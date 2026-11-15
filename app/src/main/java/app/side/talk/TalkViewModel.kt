package app.side.talk

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.side.BuildConfig
import app.side.vehicle.DriveState
import app.side.vehicle.DriveStateSource
import app.side.vehicle.PleosGearSource
import app.side.voice.FallbackSpeaker
import app.side.voice.Listener
import app.side.voice.PleosListener
import app.side.voice.PleosSpeaker
import app.side.voice.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 진로 토크 진행 단계. */
enum class TalkPhase {
    /** 시작 전. */
    READY,

    /** 질문을 읽는 중. */
    ASKING,

    /** 아이의 답을 듣는 중. 음성인식으로 듣고, 말이 끝나면 바로 다음 단계로 넘어간다. 인식이 안 되면 일정 시간만 기다린다. */
    LISTENING,

    /** 직업 이야기를 들려주는 중. */
    STORY,

    /** 일시정지. */
    PAUSED,

    /** 모든 카드를 마침. */
    FINISHED,
}

data class TalkUiState(
    val cards: List<CareerCard>,
    val index: Int = 0,
    val phase: TalkPhase = TalkPhase.READY,
    val listenSecondsLeft: Int = 0,
    val engineName: String = "",
    val lastError: String? = null,
    val deck: Deck = Deck.TALK,
    /** 지금 카드의 이야기(퀴즈 정답 포함)를 들려줬는지. 질문을 다시 읽으면 false로 돌아간다. */
    val revealed: Boolean = false,
    /** 지금 카드에서 알아들은 아이의 말(듣는 중이면 중간 결과). 질문을 다시 읽으면 비운다. */
    val heard: String = "",
    /** 카드별로 알아들은 답(카드 id → 문장). 관심사 키워드 추출에 쓸 예정. */
    val answers: Map<String, String> = emptyMap(),
    /** 음성인식을 쓸 수 없어 시간만 기다리는 중인지(화면 안내용). */
    val listenUnavailable: Boolean = false,
    /** 이번에 이야기까지 다 들은 카드 id(장소 추천의 근거). 앱을 다시 켜면 비워진다. */
    val finishedCards: List<String> = emptyList(),
) {
    val card: CareerCard get() = cards[index]
    val total: Int get() = cards.size
    val isRunning: Boolean get() = phase in setOf(TalkPhase.ASKING, TalkPhase.LISTENING, TalkPhase.STORY)
}

class TalkViewModel(app: Application) : AndroidViewModel(app) {

    private val speaker: Speaker = FallbackSpeaker(app).also { fallback ->
        fallback.onEngineChanged = { name -> _ui.update { it.copy(engineName = name) } }
    }
    private val listener: Listener = PleosListener(app, PleosSpeaker.Credentials.fromBuildConfig())
    private val gearSource: DriveStateSource = PleosGearSource(app)

    private val _ui = MutableStateFlow(TalkUiState(cards = SampleCards.elementary, engineName = speaker.name))
    val ui: StateFlow<TalkUiState> = _ui

    /** 기어 연결이 안 되는 환경(일반 에뮬레이터 등)에서 시연용으로 쓰는 수동 기어. null이면 실제 기어를 따른다. */
    private val simulated = MutableStateFlow<DriveState?>(null)

    /** 시뮬레이션 기어는 실제 기어를 받을 수 없을 때만 쓴다. 실제 기어가 복구되면 실제 값을 따른다. */
    val driveState: StateFlow<DriveState> =
        combine(gearSource.state, gearSource.unavailable, simulated) { real, unavailable, sim ->
            if (unavailable && sim != null) sim else real
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DriveState.UNKNOWN)

    val gearUnavailable: StateFlow<Boolean> = gearSource.unavailable
    val simulatedGear: StateFlow<DriveState?> = simulated

    private var talkJob: Job? = null

    /** 카드를 다 마친 뒤 읽을 장소 추천 한 문장. 장소 추천 쪽(MainActivity)에서 정해 준다. */
    var closingSuggestion: () -> String? = { null }

    init {
        gearSource.start()
        viewModelScope.launch {
            driveState.collect { Log.i(TAG, "drive state: $it") }
        }
    }

    /** 처음부터 또는 일시정지한 카드부터 진행한다. */
    fun play() {
        val state = _ui.value
        // 마무리 인사 중(FINISHED, 작업은 아직 살아 있음)에 누른 "처음부터 다시"도 받아들인다.
        if (talkJob?.isActive == true && state.phase != TalkPhase.FINISHED) return
        val startIndex = if (state.phase == TalkPhase.FINISHED) 0 else state.index
        runFrom(startIndex)
    }

    fun pause() {
        cancelTalk()
        _ui.update {
            // 듣는 중에 멈추면 확정되지 않은 중간 결과는 지운다(이미 저장한 답은 phase가 LISTENING이 아니다).
            val heard = if (it.phase == TalkPhase.LISTENING) "" else it.heard
            it.copy(phase = TalkPhase.PAUSED, listenSecondsLeft = 0, heard = heard)
        }
    }

    /**
     * 진행 중일 때만 멈춘다(화면을 벗어날 때 사용). 시작 전·일시정지·완료 상태는 그대로 둔다.
     * 마무리 인사 중이면 음성만 끊고 완료 상태를 유지한다.
     */
    fun pauseIfActive() {
        if (talkJob?.isActive != true) return
        if (_ui.value.phase == TalkPhase.FINISHED) cancelTalk() else pause()
    }

    /** 현재 카드를 처음부터 다시 읽는다. */
    fun replay() = runFrom(_ui.value.index)

    fun next() {
        val state = _ui.value
        if (state.index + 1 < state.total) runFrom(state.index + 1)
    }

    fun previous() {
        val state = _ui.value
        if (state.index > 0) runFrom(state.index - 1)
    }

    /** 카드 묶음(이야기 카드·밸런스 게임·직업 퀴즈)을 바꾸고 [index]번 카드부터 바로 시작한다. */
    fun startDeck(deck: Deck, index: Int = 0) {
        cancelTalk()
        _ui.update {
            it.copy(deck = deck, cards = SampleCards.deck(deck), index = 0, phase = TalkPhase.READY, revealed = false, listenSecondsLeft = 0)
        }
        runFrom(index.coerceIn(0, _ui.value.cards.lastIndex))
    }

    fun simulateGear(state: DriveState?) {
        simulated.value = state
    }

    private fun runFrom(startIndex: Int) {
        cancelTalk()
        _ui.update { it.copy(index = startIndex, lastError = null) }
        talkJob = viewModelScope.launch {
            try {
                val cards = _ui.value.cards
                for (i in startIndex until cards.size) {
                    val card = cards[i]
                    // 카드 사이 쉼은 다음 카드 쪽에 둔다. 쉼 도중 일시정지하면 방금 끝낸 카드가 아니라 다음 카드부터 이어진다.
                    _ui.update { it.copy(index = i, phase = TalkPhase.ASKING, revealed = false, heard = "", listenUnavailable = false) }
                    if (i > startIndex) delay(PAUSE_BETWEEN_CARDS_MS)
                    say(card.spoken)

                    _ui.update { it.copy(phase = TalkPhase.LISTENING) }
                    val answer = listenForAnswer()
                    if (!answer.isNullOrBlank()) {
                        if (BuildConfig.DEBUG) Log.i(TAG, "heard[${card.id}]: $answer") // 아이 발화는 디버그 빌드에서만 기록
                        _ui.update { it.copy(heard = answer, answers = it.answers + (card.id to answer), listenSecondsLeft = 0) }
                        delay(AFTER_ANSWER_PAUSE_MS) // 알아들은 말을 화면에서 확인할 틈
                    }
                    _ui.update {
                        it.copy(
                            listenSecondsLeft = 0,
                            phase = TalkPhase.STORY,
                            revealed = true,
                            finishedCards = if (card.id in it.finishedCards) it.finishedCards else it.finishedCards + card.id,
                        )
                    }
                    say(card.story)
                }
                _ui.update { it.copy(phase = TalkPhase.FINISHED) }
                // 마무리 인사가 실패해도 완료 상태는 유지한다.
                runCatching {
                    say(CLOSING)
                    // 오늘 이야기와 어울리는 가까운 장소가 있으면 이어서 알려 준다.
                    closingSuggestion()?.let { say(it) }
                }
                    .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "closing failed", it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "talk stopped by error", e)
                _ui.update { it.copy(phase = TalkPhase.PAUSED, listenSecondsLeft = 0, lastError = e.message) }
            }
        }
    }

    private fun cancelTalk() {
        talkJob?.cancel()
        talkJob = null
        speaker.stop()
        listener.stop()
    }

    /**
     * 아이의 답을 듣는다. 남은 시간을 1초마다 화면에 보여 주고, 알아들은 문장을 돌려준다.
     * - 엔진은 말이 없으면 2초쯤 만에 빈 결과로 끝나므로, 시간이 남아 있으면 다시 듣는다.
     * - [LISTEN_SECONDS] 안에 아무 말이 없으면 null.
     * - 말하는 중(중간 결과가 있음)에 시간이 다 되면 [LISTEN_EXTRA_SECONDS]만큼 더 기다리고, 그래도 확정되지 않으면 마지막 중간 결과를 쓴다.
     * - 일시적인 인식 오류면 잠깐 쉬고 다시 듣는다. 엔진 자체를 쓸 수 없으면 남은 시간만 기다리고 null.
     */
    private suspend fun listenForAnswer(): String? = coroutineScope {
        var partial = ""
        /** 말소리가 감지됐는데 아직 확정되지 않았는지. 중간 결과가 오기 전에도 연장 판단에 쓴다. */
        var speaking = false
        // TTS 끝부분이 마이크로 다시 들어오지 않도록 잠깐 쉬고 듣기 시작한다.
        delay(LISTEN_START_DELAY_MS)
        val countdown = launch {
            for (left in LISTEN_SECONDS downTo 1) {
                _ui.update { it.copy(listenSecondsLeft = left) }
                delay(1_000)
            }
            _ui.update { it.copy(listenSecondsLeft = 0) }
        }

        /** 한 번 듣고 확정 결과를 돌려준다. 오류면 null. */
        suspend fun listenOnce(): String? = suspendCancellableCoroutine { cont ->
            listener.listen(
                onPartial = { text ->
                    partial = text
                    _ui.update { it.copy(heard = text) }
                },
                onFinal = { text -> if (cont.isActive) cont.resume(text) },
                onSpeech = { speaking = true },
                onError = { reason ->
                    Log.w(TAG, "listen failed: $reason")
                    if (cont.isActive) cont.resume(null)
                },
            )
            cont.invokeOnCancellation { listener.stop() }
        }

        /** 말이 나올 때까지 되풀이해 듣는다. 엔진을 쓸 수 없으면 남은 시간만 기다린다. */
        suspend fun listenUntilSpoken(): String {
            while (true) {
                val text = listenOnce()
                if (!text.isNullOrBlank()) return text
                if (!listener.available) {
                    _ui.update { it.copy(listenUnavailable = true) }
                    awaitCancellation()
                }
                if (text != null) {
                    // 빈 확정 결과(엔진이 말소리가 아니라고 판단)면 잡음으로 보인 중간 결과도 지운다.
                    partial = ""
                    speaking = false
                    _ui.update { it.copy(heard = "") }
                    delay(RETRY_GAP_MS)
                } else {
                    // 일시적인 오류면 이미 들은 중간 결과는 남겨 둔다(시간이 다 되면 그것을 답으로 쓴다).
                    delay(RETRY_AFTER_ERROR_MS)
                }
            }
        }

        // 시간 제한은 결과를 기다리는 쪽에만 건다. 듣기 자체는 이어지므로 말하는 중에 시간이 다 돼도 끊기지 않는다.
        val hearing = async { listenUntilSpoken() }
        try {
            var answer = withTimeoutOrNull(LISTEN_SECONDS * 1_000L) { hearing.await() }
            countdown.cancel()
            if (answer == null && (speaking || partial.isNotBlank())) {
                _ui.update { it.copy(listenSecondsLeft = 0) }
                answer = withTimeoutOrNull(LISTEN_EXTRA_SECONDS * 1_000L) { hearing.await() }
                    ?: partial.trim().takeIf { it.isNotEmpty() }
            }
            answer
        } finally {
            countdown.cancel()
            hearing.cancel()
            listener.stop()
        }
    }

    /**
     * 한 문장을 끝까지 읽을 때까지 기다린다. 코루틴이 취소되면 발화도 멈춘다.
     * 엔진이 완료/오류 콜백을 끝내 보내지 않는 경우(서비스 단절 등)에 멈추지 않도록 문장 길이에 비례한 시간 제한을 둔다.
     */
    private suspend fun say(text: String) {
        try {
            withTimeout(SPEAK_TIMEOUT_BASE_MS + text.length * SPEAK_TIMEOUT_PER_CHAR_MS) { speakAndWait(text) }
        } catch (e: TimeoutCancellationException) {
            throw SpeakException("음성 응답이 없어요")
        }
    }

    private suspend fun speakAndWait(text: String) = suspendCancellableCoroutine { cont ->
        speaker.speak(
            text,
            onDone = { if (cont.isActive) cont.resume(Unit) },
            onError = { msg -> if (cont.isActive) cont.resumeWith(Result.failure(SpeakException(msg))) },
        )
        cont.invokeOnCancellation { speaker.stop() }
    }

    override fun onCleared() {
        Log.i(TAG, "cleared: releasing speaker and gear source")
        cancelTalk()
        speaker.release()
        listener.release()
        gearSource.stop()
    }

    private class SpeakException(message: String) : Exception(message)

    private companion object {
        const val TAG = "SIDE/Talk"
        const val LISTEN_SECONDS = 8
        const val LISTEN_EXTRA_SECONDS = 6
        const val AFTER_ANSWER_PAUSE_MS = 1_200L
        const val LISTEN_START_DELAY_MS = 300L
        const val RETRY_GAP_MS = 200L
        const val RETRY_AFTER_ERROR_MS = 800L
        const val SPEAK_TIMEOUT_BASE_MS = 10_000L
        const val SPEAK_TIMEOUT_PER_CHAR_MS = 300L
        const val PAUSE_BETWEEN_CARDS_MS = 1_500L
        const val CLOSING = "오늘의 진로 토크는 여기까지! 이야기 나눠 줘서 고마워."
    }
}
