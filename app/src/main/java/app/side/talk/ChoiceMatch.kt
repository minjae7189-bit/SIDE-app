package app.side.talk

/**
 * 음성 대답을 보기 중 하나로 맞춘다(밸런스 게임 두 개, 퀴즈 세 개). 애매하면 null.
 * - 순서로 답함: "1번", "첫 번째", "앞에 거", "A" / "2번", "두 번째", "뒤에 거", "B" / "3번", "세 번째", "마지막".
 * - 보기 속 낱말로 답함: 보기마다 낱말이 몇 개 맞는지 세서 가장 많이 맞은 보기 하나(같으면 null).
 * 순서와 낱말이 서로 다른 보기를 가리키면 null. 서로 다른 순서가 둘 이상 나와도 null.
 */
fun matchChoice(answer: String, choices: List<String>): Int? {
    if (choices.isEmpty()) return null
    // 맞장구("그래", "응")는 떼고 본다("그래 게임 기획자"의 "그래"가 "그래픽"과 맞지 않게).
    val words = answer.split(Regex("[\\s.,!?~…]+")).filter { it.isNotEmpty() && it !in ACKS }
    val compact = words.joinToString("")
    if (compact.isEmpty()) return null
    // "밖에 나가기 싫어"처럼 부정이 섞이면 어느 쪽을 고른 건지 알 수 없어 받지 않는다.
    // 보기 문장에 든 말("모르는 친구에게 알려 주기"의 "모르")은 부정으로 보지 않는다.
    val choiceText = choices.joinToString(" ")
    if (words.any { it == "안" } || NEGATIONS.any { compact.contains(it) && !choiceText.contains(it) }) return null

    val byOrder = orderCues(compact, choices.size)
    if (byOrder.size > 1) return null

    // 한 글자 낱말("비" 등)은 다른 말("비밀", "비슷해")에 흔히 섞여 낱말 전체가 같을 때만 맞춘다.
    val answerStems = words.map { w -> stripParticle(w.removeSuffix("요").ifEmpty { w }) }
    val hits = choices.map { c -> stems(c).count { if (it.length >= 2) compact.contains(it) else it in answerStems } }
    val best = hits.maxOrNull() ?: 0
    val byWords = if (best > 0 && hits.count { it == best } == 1) hits.indexOf(best) else null

    val order = byOrder.singleOrNull()
    return when {
        order != null && byWords != null && order != byWords -> null
        order != null -> order
        else -> byWords
    }
}

/** 순서로 가리킨 보기 번호(0부터). */
private fun orderCues(compact: String, size: Int): Set<Int> {
    val found = mutableSetOf<Int>()
    val numbers = listOf(
        // "이번"(이번엔…), "에이"(에이 몰라), "처음"(처음 보는…)은 다른 뜻으로 흔해 순서 단서로 쓰지 않는다.
        // "첫째·둘째"는 "첫째 동생이…"처럼 사람을 가리키는 말로 흔해 쓰지 않는다.
        listOf("1번", "일번", "첫번째", "앞에", "앞의", "앞엣"),
        listOf("2번", "두번째"),
        listOf("3번", "삼번", "세번째"),
    )
    numbers.forEachIndexed { i, cues -> if (i < size && cues.any { compact.contains(it) }) found += i }
    // "뒤에", "마지막", "B"는 마지막 보기
    if (listOf("뒤에", "뒤의", "뒤엣", "마지막", "나중거", "비번").any { compact.contains(it) }) found += size - 1
    if (Regex("(^|[^A-Za-z])[Aa]($|[^A-Za-z])").containsMatchIn(compact)) found += 0
    if (Regex("(^|[^A-Za-z])[Bb]($|[^A-Za-z])").containsMatchIn(compact)) found += size - 1
    return found
}

/** 보기 문장의 낱말에서 조사·어미를 떼어 앞 두 글자(한 글자 낱말은 그대로)를 맞춰 볼 말로 쓴다. */
internal fun stems(choice: String): List<String> =
    choice.split(Regex("[\\s,.?!·]+")).filter { it.isNotEmpty() }.flatMap { word ->
        val stem = stripParticle(word)
        when {
            stem.isEmpty() -> emptyList()
            stem in STOP_WORDS -> emptyList()
            else -> listOf(stem.take(2), withoutSaisiot(stem.take(2)))
        }
    }.distinct()

/** "바닷속"의 "바닷"처럼 사이시옷이 붙은 끝 글자는 받침을 떼서 "바다"로도 맞게 한다. */
private fun withoutSaisiot(stem: String): String {
    val last = stem.lastOrNull() ?: return stem
    val code = last.code - 0xAC00
    return if (stem.length == 2 && code in 0 until 11172 && code % 28 == 19) stem.dropLast(1) + (last.code - 19).toChar() else stem
}

private fun stripParticle(word: String): String =
    PARTICLES.firstOrNull { word.length > it.length && word.endsWith(it) }?.let { word.removeSuffix(it) } ?: word

/** 부정·망설임이 섞이면 어느 쪽을 고른 건지 알 수 없다("우주는 무서워 바다", "아니 잠깐만"). */
private val NEGATIONS = listOf("싫", "아니", "않", "별로", "몰라", "모르", "무서", "잠깐", "잠시")
private val PARTICLES = listOf("에서", "에게", "으로", "까지", "부터", "하기", "할까", "일까", "을까", "과", "와", "을", "를", "이", "가", "은", "는", "에", "로", "도", "의", "기")
private val ACKS = setOf("그래", "응", "웅", "네", "예", "음", "어")
private val STOP_WORDS = setOf("저", "그", "이", "더", "좀", "때", "것", "수", "먼저", "오늘")
