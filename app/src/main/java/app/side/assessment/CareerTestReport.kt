package app.side.assessment

/**
 * 결과 화면 문장. 화면 코드와 떼어 두어 문장만 따로 확인할 수 있게 했다.
 */
object CareerTestReport {

    private val INTEREST_LABELS = listOf("전혀 하고 싶지 않다", "하고 싶지 않다", "약간 하고 싶지 않다", "약간 하고 싶다", "하고 싶다", "매우 하고 싶다")

    fun scoreLabel(domain: Domain, score: Int): String = when (domain) {
        Domain.INTEREST -> INTEREST_LABELS.getOrElse(score - 1) { "" }
        Domain.APTITUDE -> CareerTestContent.AGREE_LABELS.getOrElse(score - 1) { "" }
        Domain.VALUES -> ""
    }

    /** "가장 높은 …" 문장. */
    fun topSentence(content: CareerTestContent, domain: Domain, s: DomainSummary): String {
        val names = quoted(content, domain, s.top)
        val score = s.topScore
        return when (domain) {
            Domain.INTEREST -> "$names 분야에서 ${score}점(${scoreLabel(domain, score)})으로 가장 높은 흥미를 보였습니다."
            Domain.APTITUDE -> "${names}에서 ${score}점(${scoreLabel(domain, score)})으로 가장 높은 자신감을 보였습니다."
            Domain.VALUES -> "$names 항목이 ${score}점으로 가장 중요한 가치로 나타났습니다."
        }
    }

    /** 두 번째 점수 문장. 두 번째 점수가 없으면(모두 같은 점수) null. */
    fun secondSentence(content: CareerTestContent, domain: Domain, s: DomainSummary): String? {
        val score = s.secondScore ?: return null
        val names = quoted(content, domain, s.second)
        val last = content.factors.getValue(domain)[s.second.last()].name
        return when (domain) {
            Domain.INTEREST ->
                if (score >= 4) "그 외에도 $names 분야에 ${score}점(${scoreLabel(domain, score)})의 높은 흥미를 나타냈습니다."
                else "그다음으로 $names 분야에 ${score}점(${scoreLabel(domain, score)})으로 응답했습니다."
            Domain.APTITUDE ->
                if (score >= 4) "그 외에도 ${names}에 ${score}점(${scoreLabel(domain, score)})의 높은 자신감을 나타냈습니다."
                else "그다음으로 ${names}에 ${score}점(${scoreLabel(domain, score)})으로 응답했습니다."
            Domain.VALUES -> "그다음으로는 $names${iGa(last)} ${score}점으로 중요하게 평가되었습니다."
        }
    }

    /** 경향성 문장. 고른 속성이 없으면 null. */
    fun tendencySentence(domain: Domain, parts: List<TendencyPart>): String? {
        if (parts.isEmpty()) return null
        val all = parts.joinToString(", ")
        return when (domain) {
            Domain.INTEREST -> "전반적으로 $all 활동에 대한 선호도가 높게 나타납니다."
            Domain.APTITUDE -> "전반적으로 $all${iGa(parts.last().predicate)} 높게 나타납니다."
            Domain.VALUES -> {
                val first = parts.first()
                val rest = parts.drop(1)
                if (rest.isEmpty()) {
                    "직업을 통해 $first${eulReul(first.predicate)} 중요하게 여기는 가치관을 가지고 있습니다."
                } else {
                    "직업을 통해 $first${eulReul(first.predicate)} 중요하게 여기고, " +
                        "${rest.joinToString(", ")}${eulReul(rest.last().predicate)} 원하는 가치관을 가지고 있습니다."
                }
            }
        }
    }

    private fun quoted(content: CareerTestContent, domain: Domain, indices: List<Int>): String =
        indices.joinToString(", ") { "'${content.factors.getValue(domain)[it].name}'" }

    /** 마지막 글자에 받침이 있으면 true(한글·숫자). */
    fun hasBatchim(word: String): Boolean {
        val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return false
        return when (c) {
            in '가'..'힣' -> (c - '가') % 28 != 0
            in '0'..'9' -> c in "013678"
            else -> false
        }
    }

    fun eulReul(word: String) = if (hasBatchim(word)) "을" else "를"

    fun iGa(word: String) = if (hasBatchim(word)) "이" else "가"
}
