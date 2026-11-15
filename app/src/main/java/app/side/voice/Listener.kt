package app.side.voice

/** 아이의 답을 듣는 음성인식 엔진. */
interface Listener {
    /**
     * 한 번 듣는다. 말소리가 감지되면 [onSpeech], 듣는 중간 결과는 [onPartial], 말이 끝나 확정된 문장은 [onFinal]로 온다.
     * 아무 말 없이 인식이 끝나면 [onFinal]에 빈 문자열이 온다. 실패하면 [onError].
     * [stop]하면 어떤 콜백도 오지 않는다. 콜백은 메인 스레드에서 호출된다.
     */
    fun listen(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onSpeech: () -> Unit = {},
    )

    /** 엔진을 쓸 수 있는지. 초기화에 실패했으면 false(일시적인 인식 오류와 구분). */
    val available: Boolean

    fun stop()
    fun release()
}
