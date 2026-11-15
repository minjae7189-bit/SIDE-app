package app.side.assessment

/** 음성 검사에서 알아들은 말의 뜻. */
sealed interface VoiceAnswer {
    /** 1~6 중 하나로 답함. */
    data class Scale(val value: Int) : VoiceAnswer

    /** 이전 문항으로. */
    data object Previous : VoiceAnswer

    /** 음성 검사를 멈춤(나중에 이어서). */
    data object Stop : VoiceAnswer

    /** 질문을 다시 들려 달라. */
    data object Repeat : VoiceAnswer

    /** 알아듣지 못함(여러 번호가 섞였거나 맞는 말이 없음). */
    data object Unknown : VoiceAnswer
}

/**
 * 6단계 문항의 음성 대답을 해석한다. 잘못 알아들은 말이 결과를 조용히 바꾸지 않도록 애매하면 [VoiceAnswer.Unknown].
 * - 번호: "3", "3번", "삼 번", "세 번째", "셋". 말 전체가 번호일 때만 받는다("3시까지", "첫째 동생이"는 번호가 아님).
 *   "오"는 감탄사로 흔해 "오 번"일 때만 번호로 본다.
 * - 말: "전혀" → 1, "매우" → 6, "약간"은 부정어가 있으면 3 아니면 4, 그냥 부정("아니요", "않아요", "싫어요") → 2,
 *   그냥 긍정("네", "그래요", "좋아요", "싶어요") → 5.
 * - 명령: "이전"/"뒤로" → 이전, "그만"/"나중에"/"멈춰" → 멈춤, "다시"/"뭐라고" → 다시 듣기.
 * - [options](화면의 응답 문구 6개)를 그대로 말하면 그 번호("조금 해 보고 싶어요" → 4). 띄어쓰기·문장부호는 따지지 않는다.
 */
fun parseVoiceAnswer(raw: String, options: List<String> = emptyList()): VoiceAnswer {
    val text = raw.trim()
    if (text.isEmpty()) return VoiceAnswer.Unknown
    val spoken = normalize(text)
    options.indexOfFirst { normalize(it) == spoken }.takeIf { it >= 0 }?.let { return VoiceAnswer.Scale(it + 1) }
    // 보기 문구의 말("궁금해요", "재미있어요", "잘해요")을 반말로 답해도 긍정으로 알아듣게 앞 두 글자를 긍정어로 쓴다.
    val optionStems = options.getOrNull(4)?.let { normalize(it).take(2) }?.takeIf { it.length == 2 }?.let { listOf(it) }.orEmpty()
    val t = text.replace(Regex("[\\s.,!?~…]"), "")
    val words = text.split(Regex("[\\s.,!?~…]+")).filter { it.isNotEmpty() }.map { stripEnding(it) }
    // 명령은 짧은 말일 때만 받는다("그만큼 좋아", "나중에 의사 하고 싶어요"가 멈춤이 되지 않게).
    val short = words.size <= 3
    if (short && words.any { it in COMMAND_STOP }) return VoiceAnswer.Stop
    if (short && (words.any { it in COMMAND_PREVIOUS } || COMMAND_PREVIOUS_PHRASES.any { t.contains(it) })) return VoiceAnswer.Previous
    // "한 번 더", "다시"는 번호보다 먼저 본다. 아라비아 숫자가 함께 있으면 번호로 본다.
    if (short && COMMAND_REPEAT.any { t.contains(it) } && !Regex("\\d").containsMatchIn(t)) return VoiceAnswer.Repeat

    val numbers = numbersIn(text)
    when {
        numbers.size == 1 && numbers.single() > 0 -> return VoiceAnswer.Scale(numbers.single())
        numbers.isNotEmpty() -> return VoiceAnswer.Unknown
    }
    // 망설임·모름은 답이 아니다("잘 모르겠네", "그냥 그래"). "그래서?", "그렇게"처럼 이어 가는 말도 답이 아니다.
    if (UNSURE.any { t.contains(it) } || HOLD.any { t.contains(it) }) return VoiceAnswer.Unknown
    if (words.any { it in CONNECTIVES }) return VoiceAnswer.Unknown
    // 안내대로 정도어 하나만 말한 경우("조금!", "아주").
    if (words.size == 1) {
        if (words[0] in STRONG_ALONE) return VoiceAnswer.Scale(6)
        if (words[0] in MILD_ALONE) return VoiceAnswer.Scale(4)
    }

    // 번호 없이 말로 답할 때는 짧은 대답만 받는다. 차 안의 다른 대화("터미널이 스타디움 없는 날이고")가
    // 부정어 하나 때문에 답으로 들어가지 않게. "전혀·매우·약간"처럼 정도를 말하면 조금 더 길어도 받는다.
    val intensified = INTENSIFIERS.any { t.contains(it) }
    if (words.size > (if (intensified) 5 else 3)) return VoiceAnswer.Unknown
    // 부정은 낱말 단위로 센다. 단독 "안"도 부정("안 좋아요"). 부정이 둘이면("싫지 않아") 뜻이 애매해서 받지 않는다.
    // 띄어쓰기 없이 들린 "안좋아", "안그래"도 부정으로 센다("안싫어"는 부정이 둘).
    val positiveStems = POSITIVE + optionStems
    val negatives = words.sumOf { w ->
        val glued = w.length >= 2 && w[0] == '안' && positiveStems.plus(NEGATIVE).any { w.drop(1).startsWith(it) }
        (if (w == "안" || glued) 1 else 0) + (if (NEGATIVE.any { w.contains(it) }) 1 else 0)
    }
    if (negatives >= 2) return VoiceAnswer.Unknown
    val negative = negatives == 1
    // "네·예·응"은 낱말 전체일 때만 긍정("모르겠네"의 "네"가 긍정이 되지 않게).
    val positive = words.any { it in POSITIVE_WORDS } || positiveStems.any { t.contains(it) }
    // "아니 좋아"처럼 감탄사 "아니" 뒤에 긍정이 오면 어느 쪽인지 알 수 없다.
    if (words.size >= 2 && words[0] in INTERJECTION_NO && positive && words.drop(1).none { w -> NEGATIVE.any { w.contains(it) } }) return VoiceAnswer.Unknown
    return when {
        t.contains("전혀") -> VoiceAnswer.Scale(1)
        STRONG.any { t.contains(it) } -> if (negative) VoiceAnswer.Scale(1) else if (positive) VoiceAnswer.Scale(6) else VoiceAnswer.Unknown
        MILD.any { t.contains(it) } -> if (negative) VoiceAnswer.Scale(3) else if (positive) VoiceAnswer.Scale(4) else VoiceAnswer.Unknown
        negative -> VoiceAnswer.Scale(2)
        positive -> VoiceAnswer.Scale(5)
        else -> VoiceAnswer.Unknown
    }
}

/**
 * 번호로 한 대답이면 그 번호, 아니면 빈 집합. 숫자가 섞였는데 번호 대답 꼴이 아니면 {-1}(모름).
 * 번호 대답은 말 전체가 번호여야 한다("3", "3번이요", "음 세 번째요"). 앞뒤의 군소리·맞장구("응 3번", "다시 3번")와
 * "3번으로 할게요", "3번 같아"처럼 고르는 말은 떼고 본다. "3시까지", "5분만 더", "첫째 동생이", "두 번 말했잖아"처럼
 * 문장 속에 숫자·서수가 섞인 말은 번호로 보지 않는다(차 안의 다른 대화가 답으로 들어가지 않게).
 */
private fun numbersIn(text: String): Set<Int> {
    val all = text.split(Regex("[\\s.,!?~…]+")).filter { it.isNotEmpty() }.dropLastWhile { it in NUMBER_TAIL }
    // 군소리를 떼기 전에 먼저 본다("네 번"의 "네"는 맞장구가 아니라 4).
    for (tokens in listOf(all, all.dropWhile { it in NUMBER_LEAD })) {
        if (tokens.isEmpty()) continue
        // 말 전체를 붙여서 하나의 번호로 읽거나("세 번째요" → 3), 낱말마다 같은 번호를 되풀이한 경우("3번 3번").
        numberOf(tokens.joinToString(""))?.let { return setOf(it) }
        val each = tokens.map { numberOf(it) }
        if (each.all { it != null } && each.toSet().size == 1) return setOf(each.first()!!)
    }
    // 그 밖에 숫자가 들어 있으면 번호 대답이 아니다("3번이랑 5번", "10번", "3시까지").
    return if (Regex("\\d").containsMatchIn(text)) setOf(-1) else emptySet()
}

/** 낱말(또는 붙인 말) 하나가 번호면 그 번호. "3번이요", "삼 번으로", "세번째요", "다섯이에요". */
private fun numberOf(word: String): Int? {
    val core = NUMBER_SUFFIXES.firstOrNull { word.endsWith(it) && word.length > it.length }
        ?.let { word.removeSuffix(it) } ?: word
    Regex("^([1-6])번?$").find(core)?.let { return it.groupValues[1].toInt() }
    if (Regex("\\d").containsMatchIn(core)) return null
    ORDINALS.firstOrNull { (w, _) -> core == w }?.let { return it.second }
    // 한 글자 한자어 수는 "삼", "삼 번"처럼 말 전체일 때만. "오"(감탄사)·"이"(군소리)는 "오 번", "이 번"일 때만 받는다.
    SINO.firstOrNull { (w, _) -> (core == w && w !in SINO_NOT_ALONE) || core == "${w}번" }?.let { return it.second }
    // 고유어 수: "셋", "다섯", "두 번", "네 번". "한 번"은 "한 번 더"처럼 번호가 아닌 경우가 많아 받지 않는다.
    NATIVE.firstOrNull { (w, _) -> core == w }?.let { return it.second }
    NATIVE_WITH_BEON.firstOrNull { (w, _) -> core == "${w}번" }?.let { return it.second }
    return null
}

/** 번호 앞에 붙는 군소리·맞장구. */
private val NUMBER_LEAD = setOf("음", "음음", "어", "어어", "흠", "아", "그", "저", "응", "네", "예", "그럼", "나는", "난", "저는", "다시", "음…")

/** 번호 뒤에 붙는 고르는 말. */
private val NUMBER_TAIL = setOf("할게요", "할게", "할래요", "할래", "같아요", "같아", "해요", "해", "주세요", "줘", "골라", "고를래", "고를게요")

/** 번호에 붙는 끝말(긴 것부터). */
private val NUMBER_SUFFIXES = listOf("으로요", "이에요", "이예요", "입니다", "으로", "이요", "이야", "이지", "에요", "요", "야", "지", "거", "꺼")
/** 혼자서는 번호로 보지 않는 한 글자: "오"(감탄사), "이"(군소리·"이요"). "오 번", "이 번"은 번호. */
private val SINO_NOT_ALONE = setOf("오", "이")
private val SINO = listOf("일" to 1, "이" to 2, "삼" to 3, "사" to 4, "오" to 5, "육" to 6, "륙" to 6)
private val NATIVE = listOf("하나" to 1, "둘" to 2, "셋" to 3, "넷" to 4, "다섯" to 5, "여섯" to 6)
private val NATIVE_WITH_BEON = listOf("두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6)
private val ORDINALS = listOf(
    "첫번째" to 1, "첫째" to 1, "두번째" to 2, "둘째" to 2, "세번째" to 3, "셋째" to 3,
    "네번째" to 4, "넷째" to 4, "다섯번째" to 5, "다섯째" to 5, "여섯번째" to 6, "여섯째" to 6,
)

private val COMMAND_STOP = setOf("그만", "그만해", "그만할래", "그만하자", "그만할게", "나중에", "멈춰", "멈춰줘", "종료")
private val COMMAND_PREVIOUS = setOf("이전", "뒤로")
private val COMMAND_PREVIOUS_PHRASES = listOf("이전문제", "앞문제", "앞에문제", "전문제")
private val COMMAND_REPEAT = listOf("다시", "뭐라고", "한번더", "한번만더", "못들었")
private val UNSURE = listOf("모르", "몰라", "글쎄", "그냥", "그저", "애매", "알았", "알겠")

/** 말을 이어 가는 말(부모가 "그래서?"라고만 해도 답이 되지 않게). */
private val CONNECTIVES = setOf("그래서", "그래도", "그런데", "그렇게", "그러니까", "그러면", "그럼")
private val INTERJECTION_NO = setOf("아니", "아니야", "아냐")
private val STRONG_ALONE = setOf("아주", "매우")
private val MILD_ALONE = setOf("조금", "약간", "살짝", "좀")

/** 말을 끊거나 기다려 달라는 말("아니 잠깐만"). 앞의 "아니"를 부정 대답으로 받지 않는다. */
private val HOLD = listOf("잠깐", "잠시", "잠만", "기다려")
private val STRONG = listOf("매우", "아주", "정말", "완전", "엄청", "진짜", "너무", "되게", "제일", "절대", "하나도")
private val MILD = listOf("약간", "조금", "살짝", "좀", "쪼금")
private val INTENSIFIERS = listOf("전혀") + STRONG + MILD
private val NEGATIVE = listOf("아니", "아냐", "아뇨", "않", "싫", "없", "별로", "안해", "안하", "못", "나쁘", "나빠")
private val POSITIVE_WORDS = setOf("네", "예", "응", "웅", "넵")
/** 긍정어. "있어"는 "저기 있어"처럼 대답이 아닌 말에 흔해 넣지 않는다("관심이 있어요"는 보기 문구 어간으로 알아듣는다). */
private val POSITIVE = listOf("그래", "그렇", "좋", "싶", "맞아", "할래", "궁금", "재밌", "재미있", "즐거", "즐겨", "즐기", "잘해", "잘하", "최고", "당연")

private fun normalize(s: String): String = s.replace(Regex("[\\s.,!?~…]"), "")

/** "여섯이요", "셋이에요", "둘요"처럼 붙은 끝말을 뗀다. */
private fun stripEnding(word: String): String =
    listOf("이에요", "이예요", "에요", "이요", "요", "이다").firstOrNull { word.endsWith(it) && word.length > it.length }
        ?.let { word.removeSuffix(it) } ?: word
