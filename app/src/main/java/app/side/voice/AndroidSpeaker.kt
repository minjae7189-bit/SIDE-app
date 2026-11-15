package app.side.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/** 안드로이드 기본 TTS 기반 [Speaker]. Pleos TTS를 쓸 수 없는 환경의 대체 엔진. */
class AndroidSpeaker(context: Context) : Speaker {

    override val name = "Android TTS"

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed: String? = null
    private var pending: Request? = null
    private var current: Request? = null
    private var seq = 0

    private class Request(val id: String, val text: String, val onDone: () -> Unit, val onError: (String) -> Unit)

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status) }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "init failed: $status")
            failed = "Android TTS init failed: $status"
            pending?.also { pending = null }?.onError?.invoke("Android TTS init failed: $status")
            return
        }
        val lang = tts.setLanguage(Locale.KOREA)
        if (lang < TextToSpeech.LANG_AVAILABLE) Log.w(TAG, "Korean not supported: $lang")
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                main.post { finish(utteranceId)?.onDone?.invoke() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { finish(utteranceId)?.onError?.invoke("Android TTS error") }
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                main.post { finish(utteranceId)?.onError?.invoke("Android TTS error $errorCode") }
            }
        })
        ready = true
        pending?.let {
            pending = null
            start(it)
        }
    }

    /** 현재 요청의 발화가 끝났을 때만 그 요청을 돌려준다(중단된 이전 발화의 늦은 콜백 무시). */
    private fun finish(utteranceId: String): Request? =
        current?.takeIf { it.id == utteranceId }?.also { current = null }

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val request = Request("side-${++seq}", text, onDone, onError)
        failed?.let {
            onError(it)
            return
        }
        if (!ready) {
            pending = request
            return
        }
        start(request)
    }

    private fun start(request: Request) {
        current = request
        val result = tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, request.id)
        if (result != TextToSpeech.SUCCESS) {
            current = null
            request.onError("Android TTS speak failed: $result")
        }
    }

    override fun stop() {
        pending = null
        current = null
        if (ready) tts.stop()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "SIDE/AndroidTts"
    }
}
