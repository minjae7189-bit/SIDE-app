package app.side.places

import java.time.DayOfWeek
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 가 볼 만한 곳 추천: 오늘 나눈 이야기(들은 카드, 고른 관심사)와 진로 검사 결과로 관심 주제를 모으고,
 * 현재 위치에서 가까우면서 관심 주제와 맞는 문화시설을 이유와 함께 고른다.
 * 이 파일은 안드로이드에 의존하지 않아 JVM 단위 테스트에서 그대로 쓸 수 있다.
 */
enum class Theme(val label: String, val suggestion: String) {
    SCIENCE("과학·기술", "전시물을 보며 '이건 어떤 원리로 움직일까?'를 같이 맞혀 보세요."),
    NATURE("자연·생태", "가장 신기했던 생물 하나를 골라, 그 생물을 연구하는 직업을 이야기해 보세요."),
    HISTORY("역사·문화", "옛날 사람들이 하던 일이 지금은 어떤 직업으로 바뀌었는지 찾아보세요."),
    ART("미술·디자인", "마음에 드는 작품 하나를 골라, 만든 사람이 어떤 생각을 했을지 이야기해 보세요."),
    PERFORM("음악·공연", "공연을 본 뒤 무대 위와 무대 뒤에서 일한 사람들을 함께 떠올려 보세요."),
    BOOKS("책·글", "아이가 고른 책의 주인공이 자라서 어떤 일을 할지 상상해 보세요."),
    MAKING("만들기·체험", "직접 만든 것을 들고 '이걸 직업으로 하는 사람은 누구일까?' 이야기해 보세요."),
    SPORTS("몸으로 놀기", "잘한 점을 칭찬해 주고, 운동과 관련된 직업도 함께 떠올려 보세요."),
    EXPLORE("모험·놀이", "어려운 단계를 어떻게 풀었는지 들어 보며 아이의 문제 해결 방식을 살펴보세요."),
    COMMUNITY("사람·나눔", "그곳에서 일하는 분께 어떤 일을 하시는지 직접 여쭤 보세요."),
}

/**
 * 장소 하나. [approx]가 true면 주소로 추정한 위치라 거리를 "약 ○km"로 보여 준다.
 * 운영 정보([hours], [closed], [fee], [closedDays], [intro])는 박물관·미술관 표준데이터와 공공시설 개방 자료에만 있다.
 */
data class Place(
    val name: String,
    val kind: String,
    val lat: Double,
    val lng: Double,
    val approx: Boolean,
    val themes: Set<Theme>,
    val detail: String,
    /** 평일 운영 시간(예: "09:00~18:00") */
    val hours: String = "",
    /** 휴관 안내 원문(예: "매주 월요일, 1월 1일") */
    val closed: String = "",
    /** 요금 안내(예: "어린이 무료") */
    val fee: String = "",
    /** 매주 쉬는 요일 비트(월=1, 화=2, … 일=64) */
    val closedDays: Int = 0,
    /** 짧은 소개 */
    val intro: String = "",
) {
    /** 그 요일에 매주 쉬는 곳인지. */
    fun closedOn(day: DayOfWeek): Boolean = closedDays shr (day.value - 1) and 1 == 1

    /** 카드에 한 줄로 보여 줄 운영 정보. */
    val infoLine: String
        get() = listOfNotNull(
            hours.takeIf { it.isNotBlank() }?.let { "운영 $it" },
            closed.takeIf { it.isNotBlank() }?.let { "휴관 $it" },
            fee.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
}

data class GeoPoint(val lat: Double, val lng: Double)

/** 관심 주제 하나가 어디서 왔는지(추천 이유 문장의 재료). */
data class InterestSource(val theme: Theme, val weight: Int, val reason: String)

/** 관심 주제와 그 근거들. */
data class InterestProfile(val sources: List<InterestSource>) {
    val weights: Map<Theme, Int> = sources.groupBy { it.theme }.mapValues { (_, v) -> v.sumOf { it.weight } }
    val isEmpty: Boolean get() = sources.isEmpty()
    fun reasonsFor(theme: Theme): List<String> = sources.filter { it.theme == theme }.sortedByDescending { it.weight }.map { it.reason }.distinct()
}

data class Recommendation(
    val place: Place,
    val distanceKm: Double,
    /** 이 장소를 고르게 한 주제 */
    val theme: Theme,
    /** "왜 여기?" 문장들 */
    val reasons: List<String>,
)

object Recommender {

    /** 기본 반경. 이 안에 맞는 곳이 3곳보다 적으면 [WIDE_KM]까지 넓힌다. */
    const val NEAR_KM = 30.0
    const val WIDE_KM = 80.0

    /** 거리 감쇠 기준(km). 이 거리만큼 멀어지면 점수가 절반이 된다. */
    const val HALF_KM = 8.0

    /** 관심 근거 가중치: 직접 고른 관심사 > 아이가 말한 낱말 = 진로 검사 최고 흥미 > 검사 두 번째 흥미 = 오늘 들은 카드 */
    const val W_CHOSEN = 3
    const val W_TEST_TOP = 2
    const val W_TEST_SECOND = 1
    const val W_CARD = 1
    const val W_SPOKEN = 2

    /**
     * @param today 오늘 요일. 주면 그 요일에 매주 쉬는 곳은 뺀다(공휴일 휴관은 알 수 없어 따지지 않는다).
     */
    fun recommend(
        places: List<Place>,
        here: GeoPoint,
        profile: InterestProfile,
        count: Int = 3,
        today: DayOfWeek? = null,
    ): List<Recommendation> {
        val weights = if (profile.isEmpty) DEFAULT_WEIGHTS else profile.weights
        fun candidates(radius: Double) = places.asSequence()
            .filter { today == null || !it.closedOn(today) }
            .map { it to distanceKm(here, GeoPoint(it.lat, it.lng)) }
            .filter { (_, d) -> d <= radius }
            .mapNotNull { (p, d) ->
                val best = p.themes.maxByOrNull { weights[it] ?: 0 } ?: return@mapNotNull null
                val relevance = p.themes.sumOf { weights[it] ?: 0 }
                if (relevance == 0) null else Scored(p, d, best, kindFactor(p.kind) * relevance / (1.0 + d / HALF_KM))
            }
            .sortedByDescending { it.score }
            .toList()

        var scored = candidates(NEAR_KM)
        if (scored.size < count) scored = candidates(WIDE_KM)

        // 같은 종류·같은 주제만 몰리지 않게 고른다(먼저 다른 주제, 그다음 남은 자리 채우기).
        // 체험 클래스는 성인 대상 유료 강좌가 많아 한 곳까지만 넣는다.
        val picked = mutableListOf<Scored>()
        fun fits(s: Scored) = picked.none { it.place.name == s.place.name } &&
            !(s.place.kind == CLASS_KIND && picked.any { it.place.kind == CLASS_KIND })
        for (s in scored) {
            if (picked.size == count) break
            if (fits(s) && picked.none { it.theme == s.theme || it.place.kind == s.place.kind }) picked += s
        }
        for (s in scored) {
            if (picked.size == count) break
            if (fits(s) && s !in picked) picked += s
        }
        return picked.map { s ->
            val reasons = if (profile.isEmpty) {
                listOf("아직 고른 관심사가 없어 가족이 함께 가기 좋은 곳을 골랐어요.")
            } else {
                profile.reasonsFor(s.theme).take(2)
            }
            Recommendation(s.place, s.distance, s.theme, reasons + distanceSentence(s.place, s.distance))
        }
    }

    fun distanceSentence(place: Place, km: Double): String =
        "${place.kind} '${place.name}'까지 ${formatKm(km, place.approx)}예요." +
            if (place.hours.isNotBlank()) " 평일 ${place.hours}에 열어요." else ""

    /** 1km 미만은 m로, 나머지는 소수 한 자리. 주소로 추정한 위치면 "약"을 붙인다. */
    fun formatKm(km: Double, approx: Boolean): String {
        val text = if (km < 1) "${(km * 1000).toInt() / 100 * 100}m" else "${"%.1f".format(km)}km"
        return if (approx) "약 $text" else text
    }

    /** 음성 안내용 한 문장(주행 중에도 읽는다). */
    fun voiceLine(r: Recommendation): String {
        val km = if (r.distanceKm < 1) "1킬로미터 안" else "${"%.0f".format(r.distanceKm)}킬로미터쯤"
        return "오늘 이야기와 어울리는 곳으로 ${r.place.kind} ${r.place.name}${iGa(r.place.name)} 있어요. 여기서 $km 떨어져 있어요. " +
            "차를 세우면 화면에서 추천하는 이유와 길 안내를 볼 수 있어요."
    }

    const val CLASS_KIND = "체험 클래스"

    /**
     * 장소 종류별 가중치. 가족이 함께 가기 좋은 공공 문화시설(박물관·과학관·미술관·공연장 등)을 먼저 권하고,
     * 여가·체육 시설과 도서관은 조금, 성인 대상 유료 강좌가 많은 체험 클래스는 많이 낮춘다.
     */
    fun kindFactor(kind: String): Double = when (kind) {
        CLASS_KIND -> 0.4
        "도서관", "문화원", "생활문화센터", "문화의집" -> 0.8
        "박물관", "과학관", "미술관", "문학관", "공연장" -> 1.0
        else -> 0.8 // 클라이밍·볼링장·방탈출 같은 여가·체육 시설
    }

    /** 두 지점 사이 거리(km, 하버사인). */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** 관심사가 하나도 없을 때: 가족이 함께 가기 좋은 박물관·과학관·미술관 쪽으로 기운다. */
    private val DEFAULT_WEIGHTS = mapOf(Theme.SCIENCE to 2, Theme.NATURE to 2, Theme.HISTORY to 1, Theme.ART to 1)

    private data class Scored(val place: Place, val distance: Double, val theme: Theme, val score: Double)
}

/** 이야기 카드와 진로 검사 하위요인이 어떤 관심 주제와 이어지는지. */
object ThemeLinks {

    /** 카드 id → 주제. 열린 질문(좋아하는 것 등)은 답을 알 수 없어 연결하지 않는다. */
    val cards: Map<String, List<Theme>> = mapOf(
        "window" to listOf(Theme.EXPLORE),
        "b-explore" to listOf(Theme.SCIENCE, Theme.NATURE),
        "b-weekend" to listOf(Theme.MAKING, Theme.EXPLORE),
        "b-homework" to listOf(Theme.ART, Theme.SCIENCE),
        "b-team" to listOf(Theme.COMMUNITY, Theme.BOOKS),
        "b-care" to listOf(Theme.NATURE, Theme.SCIENCE),
        "b-stage" to listOf(Theme.PERFORM),
        "q-air" to listOf(Theme.SCIENCE),
        "q-medicine" to listOf(Theme.SCIENCE),
        "q-game" to listOf(Theme.ART, Theme.EXPLORE),
        "q-road" to listOf(Theme.SCIENCE, Theme.EXPLORE),
        "q-museum" to listOf(Theme.HISTORY),
        "m-ei" to listOf(Theme.COMMUNITY),
        "m-jp" to listOf(Theme.EXPLORE),
    )

    /** 진로 검사 흥미 하위요인 이름 → 주제(데모 문항 기준). */
    val interests: Map<String, List<Theme>> = mapOf(
        "과학·탐구" to listOf(Theme.SCIENCE, Theme.NATURE),
        "기계·만들기" to listOf(Theme.SCIENCE, Theme.MAKING),
        "그림·디자인" to listOf(Theme.ART),
        "음악·공연" to listOf(Theme.PERFORM),
        "사람 돕기" to listOf(Theme.COMMUNITY),
        "기획·리더십" to listOf(Theme.COMMUNITY, Theme.EXPLORE),
        "정리·기록" to listOf(Theme.BOOKS),
        "몸으로 활동" to listOf(Theme.SPORTS),
    )

    /** 아이가 대답에서 말한 낱말 → 주제(음성인식으로 받은 답에서 찾는다). 앞의 낱말이 먼저 걸린다. */
    val keywords: List<Pair<String, Theme>> = listOf(
        "우주" to Theme.SCIENCE, "로켓" to Theme.SCIENCE, "과학" to Theme.SCIENCE, "실험" to Theme.SCIENCE, "로봇" to Theme.SCIENCE,
        "컴퓨터" to Theme.SCIENCE, "코딩" to Theme.SCIENCE, "발명" to Theme.SCIENCE, "비행기" to Theme.SCIENCE, "기차" to Theme.SCIENCE,
        "자동차" to Theme.SCIENCE,
        "공룡" to Theme.NATURE, "동물" to Theme.NATURE, "강아지" to Theme.NATURE, "고양이" to Theme.NATURE, "곤충" to Theme.NATURE,
        "벌레" to Theme.NATURE, "식물" to Theme.NATURE, "꽃" to Theme.NATURE, "바다" to Theme.NATURE, "물고기" to Theme.NATURE, "숲" to Theme.NATURE,
        "역사" to Theme.HISTORY, "옛날" to Theme.HISTORY, "박물관" to Theme.HISTORY, "한복" to Theme.HISTORY, "문화재" to Theme.HISTORY,
        "그림" to Theme.ART, "그리기" to Theme.ART, "만화" to Theme.ART, "웹툰" to Theme.ART, "디자인" to Theme.ART, "사진" to Theme.ART, "미술" to Theme.ART,
        "노래" to Theme.PERFORM, "춤" to Theme.PERFORM, "피아노" to Theme.PERFORM, "악기" to Theme.PERFORM, "음악" to Theme.PERFORM,
        "아이돌" to Theme.PERFORM, "연기" to Theme.PERFORM, "배우" to Theme.PERFORM, "공연" to Theme.PERFORM,
        "책" to Theme.BOOKS, "소설" to Theme.BOOKS, "글쓰기" to Theme.BOOKS, "동화" to Theme.BOOKS, "도서관" to Theme.BOOKS,
        "레고" to Theme.MAKING, "만들기" to Theme.MAKING, "요리" to Theme.MAKING, "빵" to Theme.MAKING, "쿠키" to Theme.MAKING,
        "종이접기" to Theme.MAKING, "블록" to Theme.MAKING, "슬라임" to Theme.MAKING,
        "축구" to Theme.SPORTS, "야구" to Theme.SPORTS, "농구" to Theme.SPORTS, "수영" to Theme.SPORTS, "달리기" to Theme.SPORTS,
        "줄넘기" to Theme.SPORTS, "태권도" to Theme.SPORTS, "자전거" to Theme.SPORTS, "운동" to Theme.SPORTS, "배드민턴" to Theme.SPORTS,
        "여행" to Theme.EXPLORE, "캠핑" to Theme.EXPLORE, "탐험" to Theme.EXPLORE, "모험" to Theme.EXPLORE, "방탈출" to Theme.EXPLORE, "놀이공원" to Theme.EXPLORE,
        "친구" to Theme.COMMUNITY, "선생님" to Theme.COMMUNITY, "도와" to Theme.COMMUNITY, "봉사" to Theme.COMMUNITY,
    )

    /**
     * 관심 근거 모으기.
     * @param chosen 정차 화면에서 고른 "오늘 나온 관심사"
     * @param heard 오늘 들은 카드 (id, 주제 이름, 묶음 이름)
     * @param testTop 진로 검사 흥미 최고점 하위요인 이름들
     * @param testSecond 진로 검사 흥미 두 번째 점수 하위요인 이름들
     * @param answers 음성인식으로 알아들은 아이의 답들(기기 안에서만 쓴다)
     */
    fun profile(
        chosen: Set<Theme>,
        heard: List<Triple<String, String, String>>,
        testTop: List<String>,
        testSecond: List<String>,
        answers: List<String> = emptyList(),
    ): InterestProfile {
        val sources = mutableListOf<InterestSource>()
        // 아이가 직접 말한 낱말은 카드보다 강한 근거로 본다. 한 낱말은 한 번만 센다.
        answers.flatMap { a -> keywords.filter { (w, _) -> w in a } }.distinctBy { it.first }.forEach { (w, t) ->
            sources += InterestSource(t, Recommender.W_SPOKEN, "오늘 아이가 '$w' 이야기를 했어요.")
        }
        chosen.forEach { sources += InterestSource(it, Recommender.W_CHOSEN, "오늘 관심사로 '${it.label}'${eulReul(it.label)} 골랐어요.") }
        testTop.forEach { f ->
            interests[f].orEmpty().forEach { sources += InterestSource(it, Recommender.W_TEST_TOP, "진로 검사에서 '$f'에 가장 높은 흥미를 보였어요.") }
        }
        testSecond.forEach { f ->
            interests[f].orEmpty().forEach { sources += InterestSource(it, Recommender.W_TEST_SECOND, "진로 검사에서 '$f'에도 흥미를 보였어요.") }
        }
        heard.forEach { (id, topic, deck) ->
            cards[id].orEmpty().forEach { sources += InterestSource(it, Recommender.W_CARD, "오늘 $deck '$topic' 이야기를 나눴어요.") }
        }
        return InterestProfile(sources)
    }
}

/** 마지막 글자에 받침이 있으면 true(한글·숫자). */
internal fun hasBatchim(word: String): Boolean {
    val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return false
    return when (c) {
        in '가'..'힣' -> (c - '가') % 28 != 0
        in '0'..'9' -> c in "013678"
        else -> false
    }
}

internal fun eulReul(word: String) = if (hasBatchim(word)) "을" else "를"

internal fun iGa(word: String) = if (hasBatchim(word)) "이" else "가"
