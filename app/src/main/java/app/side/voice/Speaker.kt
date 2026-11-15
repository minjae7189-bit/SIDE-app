package app.side.voice

/** 질문을 소리 내어 읽는 엔진. Pleos TTS와 안드로이드 기본 TTS를 같은 방식으로 다루기 위한 추상화. */
interface Speaker {
    /** 엔진 이름(로그·디버그 표시용). */
    val name: String

    /**
     * [text]를 읽는다. 이전 발화가 진행 중이면 끊고 새로 읽는다.
     * 끝까지 읽으면 [onDone], 실패하면 [onError]가 호출된다. 중간에 [stop]되면 둘 다 호출되지 않는다.
     * 콜백은 메인 스레드에서 호출된다.
     */
    fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit)

    fun stop()
    fun release()
}
