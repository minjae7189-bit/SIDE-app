package app.side.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    private val cityHall = GeoPoint(37.5663, 126.9779)

    private fun place(name: String, kind: String, lat: Double, lng: Double, vararg themes: Theme) =
        Place(name, kind, lat, lng, approx = true, themes = themes.toSet(), detail = "")

    private val places = listOf(
        place("국립과천과학관", "과학관", 37.4364, 127.0057, Theme.SCIENCE, Theme.NATURE, Theme.EXPLORE),
        place("서울시립미술관", "미술관", 37.5640, 126.9738, Theme.ART),
        place("세종문화회관", "공연장", 37.5725, 126.9760, Theme.PERFORM),
        place("국립중앙박물관", "박물관", 37.5239, 126.9804, Theme.HISTORY),
        place("그림 클래스 A", Recommender.CLASS_KIND, 37.5660, 126.9780, Theme.ART),
        place("그림 클래스 B", Recommender.CLASS_KIND, 37.5661, 126.9781, Theme.ART),
        place("부산 과학관", "과학관", 35.2049, 129.2119, Theme.SCIENCE),
    )

    @Test
    fun `서울시청에서 과천과학관까지 약 15km`() {
        val d = Recommender.distanceKm(cityHall, GeoPoint(37.4364, 127.0057))
        assertEquals(14.9, d, 0.6)
    }

    @Test
    fun `과학 관심이면 과학관을 먼저 고르고 이유를 붙인다`() {
        val profile = ThemeLinks.profile(emptySet(), listOf(Triple("q-air", "하늘", "직업 퀴즈")), listOf("과학·탐구"), emptyList())
        val recs = Recommender.recommend(places, cityHall, profile)
        assertEquals("국립과천과학관", recs.first().place.name)
        assertTrue(recs.first().reasons.any { "과학·탐구" in it })
        assertTrue(recs.first().reasons.last().contains("km"))
        // 너무 먼 부산은 고르지 않는다.
        assertTrue(recs.none { it.place.name == "부산 과학관" })
    }

    @Test
    fun `체험 클래스는 한 곳까지만`() {
        val profile = ThemeLinks.profile(setOf(Theme.ART), emptyList(), emptyList(), emptyList())
        val recs = Recommender.recommend(places, cityHall, profile)
        assertEquals(1, recs.count { it.place.kind == Recommender.CLASS_KIND })
        assertEquals("서울시립미술관", recs.first().place.name)
    }

    @Test
    fun `관심사가 없으면 기본 이유로 가족 나들이 장소`() {
        val recs = Recommender.recommend(places, cityHall, InterestProfile(emptyList()))
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.first().reasons.first().startsWith("아직 고른 관심사가 없어"))
    }

    @Test
    fun `거리 표기와 음성 문장`() {
        assertEquals("약 1.2km", Recommender.formatKm(1.23, approx = true))
        assertEquals("300m", Recommender.formatKm(0.35, approx = false))
        val r = Recommendation(places[0], 14.7, Theme.SCIENCE, emptyList())
        assertTrue(Recommender.voiceLine(r).contains("과학관 국립과천과학관이 있어요"))
    }

    @Test
    fun `고른 관심사 문장의 조사`() {
        val p = ThemeLinks.profile(setOf(Theme.SCIENCE, Theme.NATURE), emptyList(), emptyList(), emptyList())
        assertTrue(p.reasonsFor(Theme.SCIENCE).first().contains("'과학·기술'을 골랐어요"))
        assertTrue(p.reasonsFor(Theme.NATURE).first().contains("'자연·생태'를 골랐어요"))
    }

    @Test
    fun `아이가 말한 낱말도 근거가 된다`() {
        val p = ThemeLinks.profile(emptySet(), emptyList(), emptyList(), emptyList(), answers = listOf("나는 우주에 가 보고 싶어"))
        assertEquals(Recommender.W_SPOKEN, p.weights[Theme.SCIENCE])
        assertTrue(p.reasonsFor(Theme.SCIENCE).first().contains("'우주' 이야기를 했어요"))
        val recs = Recommender.recommend(places, cityHall, p)
        assertEquals("국립과천과학관", recs.first().place.name)
    }

    @Test
    fun `매주 쉬는 날에는 빼고 운영 정보를 붙인다`() {
        val closedMonday = Place(
            "월요일 휴관 미술관", "미술관", 37.5640, 126.9738, approx = false, themes = setOf(Theme.ART), detail = "",
            hours = "10:00~18:00", closed = "매주 월요일", fee = "어린이 무료", closedDays = 1,
        )
        val profile = ThemeLinks.profile(setOf(Theme.ART), emptyList(), emptyList(), emptyList())
        val monday = Recommender.recommend(listOf(closedMonday) + places, cityHall, profile, today = java.time.DayOfWeek.MONDAY)
        assertTrue(monday.none { it.place.name == "월요일 휴관 미술관" })
        val tuesday = Recommender.recommend(listOf(closedMonday) + places, cityHall, profile, today = java.time.DayOfWeek.TUESDAY)
        assertEquals("월요일 휴관 미술관", tuesday.first().place.name)
        assertTrue(tuesday.first().reasons.last().endsWith("평일 10:00~18:00에 열어요."))
        assertEquals("운영 10:00~18:00  ·  휴관 매주 월요일  ·  어린이 무료", closedMonday.infoLine)
    }
}
