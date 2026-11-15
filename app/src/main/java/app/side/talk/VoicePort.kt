package app.side.talk

/** 음성(TTS·STT)을 빌려 쓰는 창구. 실제 엔진은 [TalkViewModel]이 하나만 가진다. */
interface VoicePort {
    /** 음성을 쓰기 시작한다(진행 중인 이야기를 멈춘다). */
    fun claim()

    /** 끝까지 읽을 때까지 기다린다. 실패하면 예외. */
    suspend fun say(text: String)

    /** [seconds] 동안 듣고 확정된 말을 돌려준다. 말이 없으면 null. 확정되지 않은 중간 결과는 답으로 쓰지 않는다. */
    suspend fun listen(seconds: Int, onHeard: (String) -> Unit = {}, onSeconds: (Int) -> Unit = {}, onUnavailable: () -> Unit = {}): String?

    /** 말하기·듣기를 바로 멈춘다. */
    fun stop()
}
