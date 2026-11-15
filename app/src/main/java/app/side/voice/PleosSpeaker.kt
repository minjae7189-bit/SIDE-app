package app.side.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.side.BuildConfig
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 기반 [Speaker].
 *
 * 초기화가 끝나기 전에 들어온 요청은 하나만 보관했다가 초기화 직후 읽는다.
 * 초기화에 실패하면 [onInitFailed]로 알려 상위에서 다른 엔진으로 바꿀 수 있게 한다.
 *
 * [credentials]가 있으면 HYBRID 모드(서버 합성 + 오프라인 시 온디바이스)로 동작하고 초기화 후 `registerApp`을 호출한다.
 * 없으면 앱 등록이 필요 없는 ON_DEVICE 모드로만 동작한다.
 */
class PleosSpeaker(
    context: Context,
    private val credentials: Credentials?,
    private val onInitFailed: (String) -> Unit,
) : Speaker {

    /** Pleos Playground 프로젝트에서 발급받은 Client ID / Client Secret. */
    data class Credentials(val clientId: String, val clientSecret: String) {
        companion object {
            /** 빌드 때 넣은 자격증명(local.properties 또는 환경변수). 없으면 null. TTS·STT가 같이 쓴다. */
            fun fromBuildConfig(): Credentials? {
                val id = BuildConfig.PLEOS_CLIENT_ID
                val secret = BuildConfig.PLEOS_CLIENT_SECRET
                return if (id.isNotBlank() && secret.isNotBlank()) Credentials(id, secret) else null
            }
        }
    }

    override val name = "Pleos TTS"

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(
        context.applicationContext,
        if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE,
    )

    private var ready = false
    private var released = false
    private var pending: Request? = null
    private var current: Request? = null

    /**
     * 현재 요청에 대해 onStart를 받았는지. Pleos 콜백에는 발화 ID가 없어서,
     * 이전 발화의 onDone이 메인 큐에 들어간 직후 새 발화를 시작하면 그 onDone이 새 요청을 끝내 버릴 수 있다.
     * 새 요청의 onStart 이후에 온 onDone만 인정해 이를 막는다(onStart는 speak 이후에만 온다).
     */
    private var currentStarted = false

    private class Request(val text: String, val onDone: () -> Unit, val onError: (String) -> Unit)

    private val events = object : EventListener {
        override fun onStart() {
            Log.d(TAG, "onStart")
            main.post { if (current != null) currentStarted = true }
        }
        override fun onReady() { Log.d(TAG, "onReady") }
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            Log.d(TAG, "onDone")
            main.post {
                if (!currentStarted) return@post // 이전(중단된) 발화의 늦은 완료
                current?.also { current = null }?.onDone?.invoke()
            }
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
        }

        override fun onError(error: ErrorType) {
            Log.w(TAG, "onError: $error")
            main.post { current?.also { current = null }?.onError?.invoke(error.toString()) }
        }
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "initialized")
                    main.post {
                        if (released) return@post
                        // addEventListener는 initialize() 호출 전에 부르면 SDK 내부 lateinit 크래시가 난다.
                        // 초기화 성공 뒤에 등록해 순서를 확실히 한다.
                        try {
                            tts.addEventListener(events)
                        } catch (e: Exception) {
                            Log.e(TAG, "addEventListener failed", e)
                            onInitFailed(e.message ?: e.javaClass.simpleName)
                            return@post
                        }
                        registerAppIfNeeded()
                        // 온디바이스 엔진은 국가 코드가 없는 "ko"를 거부한다(unsupported locale: ko-). ko_KR로 지정.
                        runCatching { tts.setLocale(Locale.KOREA) }
                            .onFailure { Log.w(TAG, "setLocale failed", it) }
                        ready = true
                        pending?.let {
                            pending = null
                            start(it)
                        }
                    }
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "initialize failed: $error")
                    main.post { if (!released) onInitFailed(error.toString()) }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "initialize threw", e)
            main.post { onInitFailed(e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun registerAppIfNeeded() {
        val c = credentials ?: return
        try {
            tts.registerApp(c.clientId, c.clientSecret, object : OnServerConnectionListener {
                override fun onConnected() {
                    Log.i(TAG, "server connected")
                }

                override fun onFailed(error: ErrorType) {
                    // SDK 문서상 HYBRID는 서버를 못 쓰면 온디바이스로 합성하므로 엔진을 바꾸지 않는다(실기 미확인).
                    Log.w(TAG, "registerApp failed: $error")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "registerApp threw", e)
        }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val request = Request(text, onDone, onError)
        if (!ready) {
            pending = request
            return
        }
        start(request)
    }

    private fun start(request: Request) {
        if (current != null) {
            current = null
            runCatching { tts.stop() }
        }
        current = request
        currentStarted = false
        try {
            tts.speak(request.text, true)
        } catch (e: Exception) {
            Log.e(TAG, "speak threw", e)
            current = null
            request.onError(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun stop() {
        pending = null
        val speaking = current != null
        current = null
        // 읽는 중일 때만 SDK stop을 보낸다(불필요한 비동기 stop이 release와 겹치지 않도록).
        if (ready && speaking) runCatching { tts.stop() }
    }

    override fun release() {
        released = true
        pending = null
        current = null
        runCatching { tts.removeEventListener(events) }
        // SDK의 stop()은 비동기로 처리된다. 직전에 보낸 stop이 release(서비스 해제) 뒤에 실행되면
        // SDK가 서비스를 다시 바인딩해 해제되지 않은 연결이 남을 수 있어, release를 잠시 늦춰 stop이 먼저 끝나게 한다.
        main.postDelayed({ runCatching { tts.release() } }, RELEASE_DELAY_MS)
    }

    private companion object {
        const val TAG = "SIDE/PleosTts"
        const val RELEASE_DELAY_MS = 500L
    }
}
