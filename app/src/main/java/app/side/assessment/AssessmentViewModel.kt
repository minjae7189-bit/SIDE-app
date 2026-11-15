package app.side.assessment

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.side.record.RecordEntry
import app.side.record.RecordKind
import app.side.record.RecordStore
import app.side.talk.VoicePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class Stage { INTRO, PROFILE, QUESTIONS, RESULT }

data class AssessmentUi(
    val content: CareerTestContent? = null,
    val loadError: String? = null,
    val stage: Stage = Stage.INTRO,
    /** 기본 정보 화면 쪽수: 0 학교급·학년·성별·지역, 1 잘하는 과목 */
    val profilePage: Int = 0,
    val profile: Profile = Profile(),
    val answers: Map<QuestionRef, Int> = emptyMap(),
    val index: Int = 0,
    val result: CareerTestResult? = null,
    val finishedAt: String? = null,
    /** 음성으로 검사하는 중인지(문항을 읽고 대답을 듣는다. 주행 중에도 이어진다). */
    val voiceOn: Boolean = false,
    /** 음성 진행 상태 문구("질문을 읽고 있어요", "대답을 듣고 있어요 · 6초" 등). */
    val voiceStatus: String = "",
    /** 음성으로 알아들은 말(듣는 중이면 중간 결과). */
    val heard: String = "",
) {
    val total: Int get() = content?.questions?.size ?: 0
    val inProgress: Boolean get() = result == null && (answers.isNotEmpty() || profile.level != null)
}

class AssessmentActions(
    val start: () -> Unit,
    val restart: () -> Unit,
    val showResult: () -> Unit,
    val updateProfile: (Profile) -> Unit,
    val profileNext: () -> Unit,
    val profileBack: () -> Unit,
    val answer: (Int) -> Unit,
    val previous: () -> Unit,
    val exit: () -> Unit,
    /** 음성 검사를 켜고 끈다. */
    val toggleVoice: () -> Unit = {},
    /** 음성 검사를 멈춘다(다른 화면으로 갈 때, 주행 화면의 멈추기). */
    val stopVoice: () -> Unit = {},
) {
    companion object {
        val None = AssessmentActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * 검사 진행 상태. 응답은 기기에 저장해서, 기어를 바꾸거나 앱을 다시 켜도 이어서 할 수 있다.
 * 검사는 정차 중에만 화면에 보인다(주행 중에는 화면 전체가 음성 화면으로 바뀐다).
 */
class AssessmentViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("career_test", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(AssessmentUi())
    val ui: StateFlow<AssessmentUi> = _ui

    val actions = AssessmentActions(
        start = ::start,
        restart = ::restart,
        showResult = { _ui.update { if (it.result != null) it.copy(stage = Stage.RESULT) else it } },
        // 검사 도중 학교급을 바꾸면 다른 학교급 문항에 한 답이 섞이지 않게 답을 비운다.
        updateProfile = { p -> update { if (p.level != it.profile.level) it.copy(profile = p, answers = emptyMap(), index = 0) else it.copy(profile = p) } },
        profileNext = ::profileNext,
        profileBack = ::profileBack,
        answer = ::answer,
        previous = ::previous,
        exit = {
            stopVoice()
            _ui.update { it.copy(stage = Stage.INTRO) }
        },
        toggleVoice = { if (_ui.value.voiceOn) stopVoice() else startVoice() },
        stopVoice = ::stopVoice,
    )

    /** 지금 주행 중인지(안내 문구를 고를 때 쓴다). MainActivity가 넣어 준다. */
    var isDriving: () -> Boolean = { false }

    /** 음성 엔진 창구(이야기 화면의 TTS·STT를 같이 쓴다). MainActivity가 넣어 준다. */
    var voice: VoicePort? = null
    private var voiceJob: Job? = null

    /**
     * 음성 검사: 문항을 읽고 대답을 들어 답한다. 영역(흥미·적성·가치관)이 바뀔 때마다 답하는 법을 한 번 안내하고,
     * 알아들은 답은 "3번, 약간 그렇다"처럼 되풀이해 확인한다. 두 번 못 알아들으면 화면에서 고르도록 멈춘다.
     * "이전", "다시", "그만"으로 조작한다. 탑승자가 화면을 보지 않고 할 수 있게, 주행 중에도 이어진다.
     */
    fun startVoice() {
        val port = voice ?: return
        val s = _ui.value
        if (s.content == null) return
        if (s.stage == Stage.INTRO && s.result == null && s.profile.complete) start()
        if (_ui.value.stage != Stage.QUESTIONS) {
            _ui.update { it.copy(voiceStatus = if (it.profile.complete) "" else "기본 정보를 먼저 골라 주세요") }
            return
        }
        port.claim()
        voiceJob?.cancel()
        _ui.update { it.copy(voiceOn = true, voiceStatus = "", heard = "") }
        voiceJob = viewModelScope.launch {
            try {
                runVoice(port)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "음성 검사 중단", e)
            } finally {
                _ui.update { it.copy(voiceOn = false, voiceStatus = "", heard = "") }
            }
        }
    }

    /** 음성 검사를 멈춘다(화면을 벗어날 때, 이야기를 시작할 때도 부른다). 답한 내용은 그대로 남는다. */
    fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
        if (_ui.value.voiceOn) voice?.stop()
        _ui.update { it.copy(voiceOn = false, voiceStatus = "", heard = "") }
    }

    private suspend fun runVoice(port: VoicePort) {
        var lastDomain: Domain? = null
        var misses = 0
        while (true) {
            val s = _ui.value
            val content = s.content ?: return
            if (s.stage == Stage.RESULT) {
                val type = s.result?.let { content.types[it.code] }
                port.say("검사를 모두 마쳤어요. 결과는 화면에서 볼 수 있어요." + (type?.let { " 나의 진로 유형은 ${it.name}이에요." } ?: ""))
                return
            }
            if (s.stage != Stage.QUESTIONS) return
            val q = content.questions[s.index]
            val level = s.profile.level ?: Level.MIDDLE
            val options = content.options(q, level)
            if (q.domain != lastDomain) {
                status("답하는 방법을 알려 줘요")
                port.say(domainIntro(q.domain, options, level))
                lastDomain = q.domain
            }
            status("질문을 읽고 있어요")
            port.say(content.text(q, level))
            status("대답을 듣고 있어요")
            var unavailable = false
            val heard = port.listen(
                seconds = LISTEN_SECONDS,
                onHeard = { text -> _ui.update { it.copy(heard = text) } },
                onSeconds = { left -> status(if (left > 0) "대답을 듣고 있어요 · ${left}초" else "대답을 듣고 있어요") },
                onUnavailable = { unavailable = true },
            )
            if (unavailable) {
                port.say("지금은 음성인식을 쓸 수 없어요. " + laterHint())
                return
            }
            // 듣는 사이 화면에서 답했거나 이전 문항으로 갔으면, 이 대답은 버리고 지금 문항을 다시 읽는다
            // (읽지 않은 문항에 답이 들어가거나 검사 결과가 두 번 기록되지 않게).
            val now = _ui.value
            if (now.stage != Stage.QUESTIONS || now.index != s.index) {
                _ui.update { it.copy(heard = "") }
                continue
            }
            when (val a = parseVoiceAnswer(heard.orEmpty(), options)) {
                is VoiceAnswer.Scale -> {
                    misses = 0
                    status("${a.value}번으로 할게요")
                    port.say("${a.value}번, ${options[a.value - 1]}.")
                    // 되읽는 사이 화면에서 답했으면 그쪽을 따른다(이 대답이 다음 문항에 들어가지 않게).
                    val after = _ui.value
                    if (after.stage != Stage.QUESTIONS || after.index != s.index) {
                        _ui.update { it.copy(heard = "") }
                        continue
                    }
                    answer(a.value)
                    _ui.update { it.copy(heard = "") }
                }
                VoiceAnswer.Previous -> {
                    if (_ui.value.index > 0) {
                        previous()
                        port.say("이전 문항으로 갈게요.")
                    } else {
                        port.say("첫 문항이에요.")
                    }
                }
                VoiceAnswer.Repeat -> misses = 0 // 같은 문항을 다시 읽는다
                VoiceAnswer.Stop -> {
                    port.say("음성 검사를 멈출게요. 지금까지 답한 것은 남아 있어요.")
                    return
                }
                VoiceAnswer.Unknown -> {
                    misses++
                    if (misses >= MAX_MISSES) {
                        port.say("잘 알아듣지 못했어요. " + laterHint())
                        return
                    }
                    port.say("1번부터 6번 중에 번호로 말해 주세요.")
                }
            }
        }
    }

    private fun status(text: String) = _ui.update { it.copy(voiceStatus = text) }

    /** 음성 검사를 멈출 때 안내: 주행 중이면 화면을 보라고 하지 않는다. */
    private fun laterHint(): String =
        if (isDriving()) "차가 서면 화면에서 이어서 할 수 있어요." else "화면에서 골라 주세요."

    /** 영역마다 한 번 읽는 안내. 보기 여섯 개를 매번 읽으면 너무 길어서 양 끝만 알려 준다. */
    private fun domainIntro(domain: Domain, options: List<String>, level: Level): String {
        val words = if (level == Level.ELEMENTARY_LOW || level == Level.ELEMENTARY_HIGH) "아주 그래요, 조금 아니에요" else "매우 그렇다, 약간 아니다"
        return "${domain.label} 문항이에요. 1번 ${options.first()}부터 6번 ${options.last()}까지, 번호로 말해 주세요. " +
            "${words}처럼 말해도 돼요. 다시 들으려면 다시, 그만하려면 그만이라고 해 주세요."
    }

    init {
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open(ASSET).bufferedReader().use { CareerTestJson.parse(it.readText()) }
                }
            }
            loaded.onSuccess { content -> _ui.value = restore(content) }
                .onFailure { e ->
                    Log.e(TAG, "검사 자료를 읽지 못함", e)
                    _ui.update { it.copy(loadError = "검사 자료를 불러오지 못했어요") }
                }
        }
    }

    private fun start() = update {
        when {
            it.result != null -> it.copy(stage = Stage.RESULT)
            it.profile.complete -> it.copy(stage = Stage.QUESTIONS, index = firstUnanswered(it))
            else -> it.copy(stage = Stage.PROFILE, profilePage = 0)
        }
    }

    private fun restart() = update {
        AssessmentUi(content = it.content, stage = Stage.PROFILE, profile = Profile(level = it.profile.level))
    }

    private fun profileNext() = update {
        when {
            it.profilePage == 0 -> it.copy(profilePage = 1)
            it.profile.complete -> it.copy(stage = Stage.QUESTIONS, index = firstUnanswered(it))
            else -> it
        }
    }

    private fun profileBack() = update {
        if (it.profilePage == 1) it.copy(profilePage = 0) else it.copy(stage = Stage.INTRO)
    }

    private fun answer(value: Int) {
        var finished: RecordEntry? = null
        update { s -> answered(s, value) { finished = it } }
        // 기록은 상태를 바꾼 뒤에 한 번만 남긴다(update 람다는 다시 실행될 수 있어 그 안에서 부수효과를 두지 않는다).
        finished?.let { RecordStore.get(getApplication()).add(it) }
    }

    private fun answered(s: AssessmentUi, value: Int, onFinished: (RecordEntry?) -> Unit): AssessmentUi {
        onFinished(null)
        val content = s.content ?: return s
        if (s.stage != Stage.QUESTIONS) return s // 결과 화면 등에서 늦게 온 답은 받지 않는다(중복 채점·기록 방지)
        val q = content.questions[s.index]
        val answers = s.answers + (q to value)
        if (answers.size == content.questions.size) {
            val result = CareerTestScoring.score(content, answers)
            val type = content.types[result.code]
            // 성장 기록: 검사를 마친 날과 결과 유형을 남긴다.
            onFinished(
                RecordEntry(
                    at = LocalDateTime.now(),
                    kind = RecordKind.ASSESSMENT,
                    title = "진로 유형 ${result.code}" + (type?.name?.let { " · $it" } ?: ""),
                    detail = type?.feature.orEmpty(),
                    tag = "진로 검사",
                ),
            )
            return s.copy(
                answers = answers,
                result = result,
                finishedAt = LocalDate.now().format(DATE),
                stage = Stage.RESULT,
            )
        }
        // 이전 문항으로 돌아가 고친 경우에도 아직 답하지 않은 다음 문항으로 간다.
        val next = (s.index + 1 until content.questions.size).firstOrNull { content.questions[it] !in answers }
            ?: content.questions.indices.first { content.questions[it] !in answers }
        return s.copy(answers = answers, index = next)
    }

    private fun previous() = update {
        if (it.index > 0) it.copy(index = it.index - 1) else it.copy(stage = Stage.PROFILE, profilePage = 1)
    }

    private fun firstUnanswered(s: AssessmentUi): Int =
        s.content?.questions?.indexOfFirst { it !in s.answers }?.takeIf { it >= 0 } ?: 0

    /** 상태를 바꾸고 기기에 저장한다. */
    private fun update(change: (AssessmentUi) -> AssessmentUi) {
        _ui.update(change)
        save(_ui.value)
    }

    private fun save(s: AssessmentUi) {
        val content = s.content ?: return
        val p = s.profile
        prefs.edit()
            .putString(
                KEY_PROFILE,
                JSONObject()
                    .put("level", p.level?.name)
                    .put("grade", p.grade)
                    .put("gender", p.gender)
                    .put("region", p.region)
                    .put("subjects", JSONArray(p.subjects))
                    .toString(),
            )
            .putString(KEY_ANSWERS, content.questions.joinToString("") { (s.answers[it] ?: 0).toString() })
            .putString(KEY_FINISHED, s.finishedAt)
            .apply()
    }

    private fun restore(content: CareerTestContent): AssessmentUi {
        val profile = runCatching {
            prefs.getString(KEY_PROFILE, null)?.let { JSONObject(it) }?.let { o ->
                Profile(
                    level = o.optString("level").takeIf { it.isNotEmpty() && it != "null" }?.let { Level.valueOf(it) },
                    grade = o.optInt("grade", -1),
                    gender = o.optInt("gender", -1),
                    region = o.optInt("region", -1),
                    subjects = o.optJSONArray("subjects")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
                )
            }
        }.getOrNull() ?: Profile()
        val digits = prefs.getString(KEY_ANSWERS, "").orEmpty()
        val answers = if (digits.length == content.questions.size) {
            content.questions.withIndex()
                .mapNotNull { (i, q) -> digits[i].digitToIntOrNull()?.takeIf { it in 1..6 }?.let { q to it } }
                .toMap()
        } else {
            emptyMap() // 문항 수가 바뀐 경우 예전 응답은 버린다.
        }
        val done = answers.size == content.questions.size
        val base = AssessmentUi(content = content, profile = profile, answers = answers)
        return base.copy(
            index = firstUnanswered(base),
            result = if (done) CareerTestScoring.score(content, answers) else null,
            finishedAt = if (done) prefs.getString(KEY_FINISHED, null) else null,
        )
    }

    override fun onCleared() {
        voiceJob?.cancel()
    }

    private companion object {
        const val LISTEN_SECONDS = 8
        const val MAX_MISSES = 2
        const val TAG = "CareerTest"
        const val ASSET = "career_test_demo.json"
        const val KEY_PROFILE = "profile"
        const val KEY_ANSWERS = "answers"
        const val KEY_FINISHED = "finished_at"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.")
    }
}
