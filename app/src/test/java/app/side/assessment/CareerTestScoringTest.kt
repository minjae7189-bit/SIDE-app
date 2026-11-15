package app.side.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 진로 검사 채점 확인(데모 문항 기준: assets/career_test_demo.json과 같은 코드·속성). */
class CareerTestScoringTest {

    private val interest = listOf(
        Factor("i01", "과학·탐구", codes = listOf('I', 'R', 'C'), attrs = listOf("I-1", "R-1")),
        Factor("i02", "기계·만들기", codes = listOf('R', 'I', 'C'), attrs = listOf("R-1", "I-1")),
        Factor("i03", "그림·디자인", codes = listOf('A', 'R', 'I'), attrs = listOf("A-1", "R-1")),
        Factor("i04", "음악·공연", codes = listOf('A', 'S', 'E'), attrs = listOf("A-2", "S-2")),
        Factor("i05", "사람 돕기", codes = listOf('S', 'I', 'E'), attrs = listOf("S-1", "I-2")),
        Factor("i06", "기획·리더십", codes = listOf('E', 'C', 'S'), attrs = listOf("E-1", "C")),
        Factor("i07", "정리·기록", codes = listOf('C', 'E', 'I'), attrs = listOf("C", "E-1")),
        Factor("i08", "몸으로 활동", codes = listOf('R', 'E', 'S'), attrs = listOf("R-2", "E-2")),
    )
    private val aptitude = listOf(
        Factor("a01", "논리·분석", codes = listOf('I', 'C', 'R'), attrs = listOf("T-1", "T-3")),
        Factor("a02", "표현·창의", codes = listOf('A', 'S', 'E'), attrs = listOf("T-2", "T-1")),
        Factor("a03", "손·몸 쓰기", codes = listOf('R', 'E', 'S'), attrs = listOf("T-4", "T-3")),
        Factor("a04", "공감·소통", codes = listOf('S', 'A', 'E'), attrs = listOf("T-2", "T-5")),
    )
    private val values = listOf(
        Factor("v01", "안정", codes = listOf('C', 'R', 'S'), attrs = listOf("V-1", "V-2")),
        Factor("v02", "성장·도전", codes = listOf('I', 'E', 'A'), attrs = listOf("V-3", "V-4")),
        Factor("v03", "함께·기여", codes = listOf('S', 'E', 'C'), attrs = listOf("V-3", "V-1")),
    )
    private val attributes = mapOf(
        Domain.INTEREST to mapOf(
            "I-1" to Attribute("탐구", "원리를 탐구하는"),
            "I-2" to Attribute("생각", "사람과 사회를 깊이 생각하는"),
            "R-1" to Attribute("만들기", "도구와 재료를 직접 다루는"),
            "R-2" to Attribute("활동", "몸을 움직여 도전하는"),
            "A-1" to Attribute("시각 표현", "생각을 그림과 모양으로 표현하는"),
            "A-2" to Attribute("무대 표현", "감정을 소리와 몸짓으로 표현하는"),
            "S-1" to Attribute("돕기", "다른 사람을 돕고 가르치는"),
            "S-2" to Attribute("어울림", "사람들과 어울리며 소통하는"),
            "E-1" to Attribute("이끌기", "계획을 세우고 이끄는"),
            "E-2" to Attribute("추진", "새로운 일을 직접 추진하는"),
            "C" to Attribute("정리", "자료를 꼼꼼하게 정리하는"),
        ),
        Domain.APTITUDE to mapOf(
            "T-1" to Attribute("논리", "이유를 따지고 규칙을 찾는 힘"),
            "T-2" to Attribute("창의", "새로운 생각을 떠올리고 표현하는 힘"),
            "T-3" to Attribute("관찰", "자세히 보고 구조를 파악하는 힘"),
            "T-4" to Attribute("몸", "손과 몸을 정확하게 쓰는 힘"),
            "T-5" to Attribute("마음", "자신과 다른 사람의 마음을 살피는 힘"),
        ),
        Domain.VALUES to mapOf(
            "V-1" to Attribute("안정", "꾸준하고 안정적인 생활"),
            "V-2" to Attribute("보상", "노력에 맞는 보상과 인정"),
            "V-3" to Attribute("성장", "도전하며 배우고 성장하는 것"),
            "V-4" to Attribute("즐거움", "일하는 과정에서 느끼는 즐거움"),
        ),
    )

    private val content = CareerTestContent(
        factors = mapOf(Domain.INTEREST to interest, Domain.APTITUDE to aptitude, Domain.VALUES to values),
        attributes = attributes,
    )

    private fun answers(i: List<Int>, a: List<Int>, v: List<Pair<Int, Int>>): Map<QuestionRef, Int> =
        content.questions.associateWith { q ->
            when (q.domain) {
                Domain.INTEREST -> i[q.factorIndex]
                Domain.APTITUDE -> a[q.factorIndex]
                Domain.VALUES -> if (q.itemIndex == 0) v[q.factorIndex].first else v[q.factorIndex].second
            }
        }

    @Test
    fun `데모 문항은 18개`() {
        assertEquals(18, content.questions.size)
    }

    @Test
    fun `탐구와 만들기에 높게 답하면 IC`() {
        val r = CareerTestScoring.score(
            content,
            answers(listOf(6, 6, 1, 1, 1, 1, 1, 1), listOf(6, 1, 1, 1), listOf(1 to 1, 6 to 6, 1 to 1)),
        )
        assertEquals(mapOf('R' to 23, 'I' to 33, 'A' to 16, 'S' to 11, 'E' to 22, 'C' to 24), r.total)
        assertEquals("IC", r.code)
    }

    @Test
    fun `모두 같은 점수면 총점 순, 같으면 RIASEC 순서`() {
        val r = CareerTestScoring.score(content, answers(List(8) { 3 }, List(4) { 3 }, List(3) { 3 to 3 }))
        val best = r.total.values.max()
        assertEquals(RIASEC.first { r.total[it] == best }, r.code[0])
        assertTrue(r.differentiation.endsWith("flat"))
    }

    @Test
    fun `경향성 문장`() {
        val names = interest.map { it.name }
        val top = listOf("과학·탐구", "기계·만들기").map { names.indexOf(it) }
        val parts = CareerTestScoring.tendency(content, Domain.INTEREST, top, emptyList())
        assertEquals(
            "전반적으로 원리를 탐구하는(과학·탐구, 기계·만들기) 활동에 대한 선호도가 높게 나타납니다.",
            CareerTestReport.tendencySentence(Domain.INTEREST, parts),
        )
    }

    @Test
    fun `육각형 거리와 일관성`() {
        assertEquals(Consistency.HIGH, CareerTestScoring.consistencyOf('R', 'I'))
        assertEquals(Consistency.HIGH, CareerTestScoring.consistencyOf('C', 'R'))
        assertEquals(Consistency.MIDDLE, CareerTestScoring.consistencyOf('R', 'A'))
        assertEquals(Consistency.LOW, CareerTestScoring.consistencyOf('R', 'S'))
    }

    @Test
    fun `조사 고르기`() {
        assertEquals("을", CareerTestReport.eulReul("행복"))
        assertEquals("를", CareerTestReport.eulReul("조화"))
        assertEquals("이", CareerTestReport.iGa("역량"))
        assertFalse(CareerTestReport.hasBatchim("변화"))
    }
}
