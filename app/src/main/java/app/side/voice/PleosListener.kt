package app.side.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.side.BuildConfig
import java.util.Locale

/**
 * Pleos SpeechToText SDK 기반 [Listener]. 마이크 녹음은 Pleos 음성 서비스가 하므로 앱은 STT_SERVICE 권한만 있으면 된다.
 *
 * [credentials]가 있으면 HYBRID(서버 인식 + 오프라인 시 온디바이스), 없으면 ON_DEVICE로 동작한다.
 * 초기화 전에 들어온 요청은 하나만 보관했다가 초기화 직후 시작한다. 초기화에 실패하면 이후 요청은 바로 [Listener.listen]의 onError로 끝난다.
 */
class PleosListener(
    context: Context,
    private val credentials: PleosSpeaker.Credentials?,
) : Listener {

    private val main = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(
        context.applicationContext,
        if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE,
    )

    private var ready = false
    private var failed: String? = null
    private var released = false
    private var pending: Request? = null
    private var current: Request? = null

    /**
     * 지금 요청의 onReady를 받았는지. SDK 콜백에는 세션 구분이 없어서, 이전 세션의 늦은 결과가
     * 새 요청을 끝내지 않도록 새 요청의 onReady 이후에 온 결과만 받는다(실측상 세션마다 onReady가 먼저 온다).
     */
    private var currentReady = false

    override val available: Boolean get() = failed == null

    private class Request(
        val onPartial: (String) -> Unit,
        val onFinal: (String) -> Unit,
        val onError: (String) -> Unit,
        val onSpeech: () -> Unit,
    )

    private val results = object : ResultListener {
        override fun onReady() {
            Log.d(TAG, "onReady")
            main.post { if (current != null) currentReady = true }
        }

        // onStartedRecognition/onEndedRecognition은 세션 시작·끝이 아니라 말소리 감지(EPD) 상태 변화 신호다.
        // 말하다 잠깐 쉬어도 오므로 이것으로 세션을 끝내지 않는다. 확정 결과는 onUpdated(isFinal=true)로 따로 온다.
        override fun onStartedRecognition() {
            // 말소리 감지(EPD_ON). 중간 결과보다 먼저 와서 "말하는 중" 판단에 쓴다.
            main.post { if (currentReady) current?.onSpeech?.invoke() }
        }

        override fun onEndedRecognition() = Unit

        override fun onUpdated(text: String, isFinal: Boolean) {
            if (BuildConfig.DEBUG) Log.d(TAG, "onUpdated final=$isFinal text=$text") // 아이 발화는 디버그 빌드에서만 기록
            main.post {
                val request = current ?: return@post
                if (!currentReady) return@post // 이전 세션의 늦은 결과
                if (isFinal) {
                    current = null
                    request.onFinal(text.trim())
                } else {
                    request.onPartial(text)
                }
            }
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit

        override fun onError(error: ErrorType) {
            Log.w(TAG, "onError: $error")
            main.post {
                // 다시 시도해도 소용없는 오류는 엔진을 못 쓰는 상태로 기록한다(이후 요청은 바로 실패, 화면에 안내).
                if (error in PERMANENT_ERRORS) failed = "STT unavailable: $error"
                current?.also { current = null }?.onError?.invoke(error.toString())
            }
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "initialized")
                    main.post {
                        if (released) return@post
                        try {
                            stt.addListener(results)
                        } catch (e: Exception) {
                            fail("addListener failed: ${e.message}")
                            return@post
                        }
                        registerAppIfNeeded()
                        // TTS와 같이 국가 코드까지 지정한다(TTS 온디바이스 엔진은 "ko"만으로는 거부했다).
                        try {
                            stt.setLocale(Locale.KOREA)
                        } catch (e: Exception) {
                            // 한국어를 지정하지 못하면 인식 결과를 믿을 수 없으므로 엔진을 쓰지 않는다.
                            fail("setLocale failed: ${e.message}")
                            return@post
                        }
                        ready = true
                        pending?.let {
                            pending = null
                            start(it)
                        }
                    }
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "initialize failed: $error")
                    main.post { fail("STT init failed: $error") }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "initialize threw", e)
            main.post { fail("STT init failed: ${e.message}") }
        }
    }

    private fun fail(reason: String) {
        if (released) return
        failed = reason
        pending?.also { pending = null }?.onError?.invoke(reason)
    }

    private fun registerAppIfNeeded() {
        val c = credentials ?: return
        try {
            stt.registerApp(c.clientId, c.clientSecret, object : OnServerConnectionListener {
                override fun onConnected() {
                    Log.i(TAG, "server connected")
                }

                override fun onFailed(error: ErrorType) {
                    Log.w(TAG, "registerApp failed: $error")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "registerApp threw", e)
        }
    }

    override fun listen(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onSpeech: () -> Unit,
    ) {
        failed?.let {
            onError(it)
            return
        }
        val request = Request(onPartial, onFinal, onError, onSpeech)
        if (!ready) {
            pending = request
            return
        }
        start(request)
    }

    private fun start(request: Request) {
        if (current != null) {
            current = null
            runCatching { stt.stop() }
        }
        current = request
        currentReady = false
        try {
            stt.request()
        } catch (e: Exception) {
            Log.e(TAG, "request threw", e)
            current = null
            request.onError(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun stop() {
        pending = null
        val listening = current != null
        current = null
        if (ready && listening) runCatching { stt.stop() }
    }

    override fun release() {
        released = true
        pending = null
        current = null
        runCatching { stt.removeListener(results) }
        // TTS와 같은 이유로, 직전에 보낸 비동기 stop이 release보다 먼저 처리되도록 잠시 늦춘다.
        main.postDelayed({ runCatching { stt.release() } }, RELEASE_DELAY_MS)
    }

    private companion object {
        const val TAG = "SIDE/PleosStt"
        const val RELEASE_DELAY_MS = 500L

        /** 재시도해도 해결되지 않는 오류. UNAUTHENTICATED는 HYBRID에서 온디바이스로 넘어갈 수 있어 일시 오류로 둔다. */
        val PERMANENT_ERRORS = setOf(
            ErrorType.UNAVAILABLE_LANGUAGE,
            ErrorType.NOT_SUPPORTED_API,
            ErrorType.PERMISSION_DENIED,
        )
    }
}
