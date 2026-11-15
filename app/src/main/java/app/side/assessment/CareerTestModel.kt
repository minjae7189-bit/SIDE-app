package app.side.assessment

/**
 * 진로 검사 데이터 모델. 흥미·적성·가치관 문항을 홀랜드 6유형(RIASEC)으로 채점한다.
 * 이 저장소의 문항(app/src/main/assets/career_test_demo.json)은 데모용 예시이며, 실제 서비스에서는 검증된 진로검사 문항으로 바꾼다.
 * 이 파일은 안드로이드에 의존하지 않아 JVM 단위 테스트에서 그대로 쓸 수 있다.
 */
/**
 * 학교급. 화면에 이 순서로 보인다. 초등 저학년(1~3학년)·고학년(4~6학년) 문항은 같은 하위요인을 아이 눈높이로 쓴 것이다.
 * 채점 규칙은 모두 같다.
 */
enum class Level { ELEMENTARY_LOW, ELEMENTARY_HIGH, MIDDLE, HIGH }

enum class Domain(val label: String) {
    INTEREST("흥미"),
    APTITUDE("적성"),
    VALUES("가치관"),
}

/** 홀랜드 6유형. 순서가 곧 육각형 위치이자 동점 처리 순서(R > I > A > S > E > C)다. */
val RIASEC: List<Char> = listOf('R', 'I', 'A', 'S', 'E', 'C')

/** 하위요인 하나. 가치관은 하위요인마다 문항이 2개다. */
data class Factor(
    val id: String,
    val name: String,
    val desc: String = "",
    val codes: List<Char>,
    val attrs: List<String>,
    val questions: Map<Level, List<String>> = emptyMap(),
    /** 흥미 문항의 응답 양 끝 문구(① 쪽, ⑥ 쪽). */
    val scale: Map<Level, Pair<String, String>> = emptyMap(),
)

/** 경향성 속성(I-1, T-2, V-3 …)의 서술어. */
data class Attribute(val group: String, val predicate: String)

data class CareerType(val code: String, val name: String, val feature: String, val jobs: List<String>)

data class RiasecInfo(
    val code: Char,
    val name: String,
    val core: String,
    val easy: String,
    val activities: String,
    val tags: List<String>,
    val jobs: List<String>,
)

data class TextBlock(val title: String, val desc: String, val condition: String = "")

data class CareerTestContent(
    val name: String = "진로 검사",
    val source: String = "",
    val introLead: String = "",
    val introSummary: String = "",
    val introNotes: List<TextBlock> = emptyList(),
    val levelLabels: Map<Level, String> = emptyMap(),
    val grades: Map<Level, List<String>> = emptyMap(),
    /** 학교급 안내(초등용 문항이 새로 쓴 것임을 알린다). 없으면 빈 문자열. */
    val levelNotes: Map<Level, String> = emptyMap(),
    val genders: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    /** 잘하는 과목 보기(중·고 공통). 학교급마다 따로 있으면 [levelSubjects]를 쓴다. */
    val subjects: List<String> = emptyList(),
    val levelSubjects: Map<Level, List<String>> = emptyMap(),
    /** 학교급별 적성·가치관 응답 6단계(없으면 [AGREE_LABELS]). */
    val agreeLabels: Map<Level, List<String>> = emptyMap(),
    /** 흥미 응답 가운데 단계에 붙이는 말(기본 "약간", 초등 "조금"). */
    val mildWords: Map<Level, String> = emptyMap(),
    val factors: Map<Domain, List<Factor>>,
    val attributes: Map<Domain, Map<String, Attribute>> = emptyMap(),
    val types: Map<String, CareerType> = emptyMap(),
    val riasec: Map<Char, RiasecInfo> = emptyMap(),
    val consistencyIntro: String = "",
    val consistencyHigh: TextBlock? = null,
    val consistencyLow: TextBlock? = null,
    val differentiationIntro: String = "",
    /** 키: high_peaked, high_flat, low_peaked, low_flat */
    val differentiation: Map<String, TextBlock> = emptyMap(),
) {
    /** 문항 순서: 흥미 → 적성 → 가치관(하위요인마다 2개). */
    val questions: List<QuestionRef> by lazy {
        Domain.entries.flatMap { d ->
            factors.getValue(d).flatMapIndexed { fi, f ->
                val count = if (d == Domain.VALUES) 2 else 1
                (0 until count).map { qi -> QuestionRef(d, fi, qi) }
            }
        }
    }

    fun factor(q: QuestionRef): Factor = factors.getValue(q.domain)[q.factorIndex]

    fun text(q: QuestionRef, level: Level): String = factor(q).questions[level]?.getOrNull(q.itemIndex).orEmpty()

    fun subjectsFor(level: Level?): List<String> = level?.let { levelSubjects[it] } ?: subjects

    /** 응답 6단계 문구. 흥미는 문항마다 다르고(①·⑥ 문구로 만든다), 적성·가치관은 학교급마다 같다. */
    fun options(q: QuestionRef, level: Level): List<String> {
        val agree = agreeLabels[level] ?: AGREE_LABELS
        if (q.domain != Domain.INTEREST) return agree
        val (low, high) = factor(q).scale[level] ?: return agree
        val mild = mildWords[level] ?: "약간"
        val lowCore = low.removePrefix("전혀").trim()
        val highCore = high.removePrefix("매우").removePrefix("아주").trim()
        return listOf(low, lowCore, "$mild $lowCore", "$mild $highCore", highCore, high)
    }

    companion object {
        val AGREE_LABELS = listOf("전혀 그렇지 않다", "그렇지 않다", "약간 그렇지 않다", "약간 그렇다", "그렇다", "매우 그렇다")
    }
}

/** 문항 위치: 영역, 하위요인 번호, 하위요인 안의 문항 번호(가치관만 0·1). */
data class QuestionRef(val domain: Domain, val factorIndex: Int, val itemIndex: Int)

/** 기본 정보(학교급·학년·성별·지역·잘하는 과목 1~3순위). 고르지 않은 값은 -1. */
data class Profile(
    val level: Level? = null,
    val grade: Int = -1,
    val gender: Int = -1,
    val region: Int = -1,
    val subjects: List<Int> = emptyList(),
) {
    val complete: Boolean get() = level != null && grade >= 0 && gender >= 0 && region >= 0 && subjects.size == 3
}
