package app.side.voice

import android.content.Context
import android.util.Log

/**
 * Pleos TTS를 우선 쓰고, 초기화에 실패하면 안드로이드 기본 TTS로 바꿔 끼운다.
 * 전환 시점에 대기 중이던 요청은 새 엔진으로 다시 보낸다.
 */
class FallbackSpeaker(private val context: Context) : Speaker {

    private var delegate: Speaker = PleosSpeaker(context, PleosSpeaker.Credentials.fromBuildConfig(), ::onPleosInitFailed)
    private var lastRequest: Triple<String, () -> Unit, (String) -> Unit>? = null
    private var released = false

    override val name: String get() = delegate.name

    /** 엔진이 바뀌면 호출된다(UI 표시용). */
    var onEngineChanged: ((String) -> Unit)? = null

    private fun onPleosInitFailed(reason: String) {
        if (released || delegate !is PleosSpeaker) return
        Log.w(TAG, "Pleos TTS unavailable ($reason), falling back to Android TTS")
        delegate.release()
        delegate = AndroidSpeaker(context)
        onEngineChanged?.invoke(delegate.name)
        lastRequest?.let { (text, onDone, onError) -> speak(text, onDone, onError) }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val request = Triple(text, onDone, onError)
        lastRequest = request
        delegate.speak(
            text,
            onDone = { if (lastRequest === request) lastRequest = null; onDone() },
            onError = { if (lastRequest === request) lastRequest = null; onError(it) },
        )
    }

    override fun stop() {
        lastRequest = null
        delegate.stop()
    }

    override fun release() {
        released = true
        lastRequest = null
        delegate.release()
    }

    private companion object {
        const val TAG = "SIDE/Speaker"
    }
}
