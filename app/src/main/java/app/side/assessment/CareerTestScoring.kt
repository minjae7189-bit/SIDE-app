package app.side.assessment

import kotlin.math.abs
import kotlin.math.min

/**
 * 진로 검사 채점(홀랜드 RIASEC 방식).
 *
 * 1. 모든 문항 점수(1~6)를 그 하위요인에 연결된 홀랜드 코드에 각각 더한다.
 * 2. 총점이 높은 두 유형을 순서대로 골라 최종 코드(예: EI)를 만든다.
 *    동점이면 ① 총점 → ② 5점 이상 문항 수 → ③ R > I > A > S > E > C 순서로 정한다.
 * 3. 영역별로 최고점·두 번째 점수 하위요인과 경향성 문장을 만든다.
 */
object CareerTestScoring {

    fun score(content: CareerTestContent, answers: Map<QuestionRef, Int>): CareerTestResult {
        val factorScores: Map<Domain, List<Int>> = Domain.entries.associateWith { d ->
            content.factors.getValue(d).indices.map { fi ->
                content.questions.filter { it.domain == d && it.factorIndex == fi }.sumOf { answers[it] ?: 0 }
            }
        }

        val subtotals = Domain.entries.associateWith { mutableMapOf<Char, Int>().withDefault { 0 } }
        val total = mutableMapOf<Char, Int>().withDefault { 0 }
        val highCount = mutableMapOf<Char, Int>().withDefault { 0 }
        for (q in content.questions) {
            val a = answers[q] ?: continue
            for (c in content.factor(q).codes) {
                total[c] = total.getValue(c) + a
                subtotals.getValue(q.domain)[c] = subtotals.getValue(q.domain).getValue(c) + a
                if (a >= HIGH_ANSWER) highCount[c] = highCount.getValue(c) + 1
            }
        }
        val ranked = RIASEC.sortedWith(
            compareByDescending<Char> { total.getValue(it) }
                .thenByDescending { highCount.getValue(it) }
                .thenBy { RIASEC.indexOf(it) },
        )
        val code = "${ranked[0]}${ranked[1]}"

        // 유형별 평균 응답(1~6). 가치관 하위요인 하나는 문항 2개로 센다.
        val itemCount = RIASEC.associateWith { c -> content.questions.count { c in content.factor(it).codes } }
        val average = RIASEC.associateWith { c -> total.getValue(c).toFloat() / itemCount.getValue(c).coerceAtLeast(1) }

        return CareerTestResult(
            code = code,
            ranked = ranked,
            total = RIASEC.associateWith { total.getValue(it) },
            subtotals = Domain.entries.associateWith { d -> RIASEC.associateWith { subtotals.getValue(d).getValue(it) } },
            average = average,
            domains = Domain.entries.associateWith { d -> summarize(content, d, factorScores.getValue(d)) },
            consistency = consistencyOf(ranked[0], ranked[1]),
            differentiation = differentiationOf(average),
        )
    }

    /** 5점 이상을 '고득점 문항'으로 센다(동점 처리 2단계). */
    const val HIGH_ANSWER = 5

    /** 육각형에서 두 유형 사이 거리: 1 이웃, 2 한 칸 건너, 3 마주 봄. */
    fun hexDistance(a: Char, b: Char): Int {
        val d = abs(RIASEC.indexOf(a) - RIASEC.indexOf(b))
        return min(d, 6 - d)
    }

    fun consistencyOf(first: Char, second: Char): Consistency = when (hexDistance(first, second)) {
        1 -> Consistency.HIGH
        3 -> Consistency.LOW
        else -> Consistency.MIDDLE
    }

    /**
     * 변별성(데모 기준).
     * - 뾰족함: 가장 높은 유형과 가장 낮은 유형의 평균 응답 차이가 [PEAK_GAP]점 이상
     * - 높은 점수: 가장 높은 유형의 평균 응답이 [HIGH_LEVEL]점(약간 그렇다) 이상
     */
    const val PEAK_GAP = 1.0f
    const val HIGH_LEVEL = 4.0f

    fun differentiationOf(average: Map<Char, Float>): String {
        val max = average.values.max()
        val minValue = average.values.min()
        val level = if (max >= HIGH_LEVEL) "high" else "low"
        val shape = if (max - minValue >= PEAK_GAP) "peaked" else "flat"
        return "${level}_$shape"
    }

    private fun summarize(content: CareerTestContent, domain: Domain, scores: List<Int>): DomainSummary {
        // toSortedSet().reversed()는 안드로이드 15 미만에서 없는 메서드로 연결될 수 있어 쓰지 않는다.
        val levels = scores.distinct().sortedDescending()
        val topScore = levels.firstOrNull() ?: 0
        val secondScore = levels.drop(1).firstOrNull()
        val top = scores.indices.filter { scores[it] == topScore }
        val second = if (secondScore == null) emptyList() else scores.indices.filter { scores[it] == secondScore }
        return DomainSummary(
            scores = scores,
            top = top,
            topScore = topScore,
            second = second,
            secondScore = secondScore,
            tendency = tendency(content, domain, top, second),
        )
    }

    /**
     * 경향성(데모용 단순 규칙): 최고점 하위요인들의 속성을 세어 많이 나온 두 속성을 고르고,
     * 속성마다 그 속성을 가진 하위요인을 최대 2개 붙인다(이미 붙은 하위요인은 다시 쓰지 않는다).
     */
    fun tendency(content: CareerTestContent, domain: Domain, top: List<Int>, second: List<Int>): List<TendencyPart> {
        val factors = content.factors.getValue(domain)
        val count = linkedMapOf<String, Int>()
        for (i in top) factors[i].attrs.forEach { count[it] = (count[it] ?: 0) + 1 }
        val picked = count.keys.sortedByDescending { count.getValue(it) }.take(2)
        val used = mutableSetOf<Int>()
        return picked.mapNotNull { a ->
            val names = (top + second).filter { it !in used && a in factors[it].attrs }.take(2)
            used += names
            val predicate = content.attributes[domain]?.get(a)?.predicate ?: return@mapNotNull null
            if (names.isEmpty()) null else TendencyPart(a, predicate, names.map { factors[it].name })
        }
    }
}

enum class Consistency { HIGH, MIDDLE, LOW }

data class TendencyPart(val attribute: String, val predicate: String, val factors: List<String>) {
    override fun toString(): String = "$predicate(${factors.joinToString(", ")})"
}

data class DomainSummary(
    /** 하위요인별 점수(흥미·적성 1~6, 가치관 2~12). */
    val scores: List<Int>,
    val top: List<Int>,
    val topScore: Int,
    val second: List<Int>,
    val secondScore: Int?,
    val tendency: List<TendencyPart>,
)

data class CareerTestResult(
    val code: String,
    val ranked: List<Char>,
    val total: Map<Char, Int>,
    val subtotals: Map<Domain, Map<Char, Int>>,
    /** 유형별 평균 응답(1~6). 육각형 그래프와 변별성에 쓴다. */
    val average: Map<Char, Float>,
    val domains: Map<Domain, DomainSummary>,
    val consistency: Consistency,
    /** high_peaked, high_flat, low_peaked, low_flat */
    val differentiation: String,
)
