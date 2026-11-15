package app.side.explore

import app.side.places.GeoPoint
import app.side.places.Place
import app.side.places.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RelatedPlacesTest {

    private val here = GeoPoint(37.4127, 127.0947)
    private fun place(name: String, kind: String, lat: Double, lng: Double, vararg themes: Theme, closedDays: Int = 0) =
        Place(name, kind, lat, lng, approx = false, themes = themes.toSet(), detail = "", closedDays = closedDays)

    private val astronomer = CareerFields.all.first { it.id == "space" }.jobs.first { it.name == "천문학자" }

    @Test
    fun `낱말이 맞는 곳을 가까운 순으로 먼저`() {
        val far = place("송암 천문대", "과학관", 37.70, 126.98, Theme.SCIENCE)
        val near = place("별빛 천문대", "과학관", 37.42, 127.10, Theme.SCIENCE)
        val sameTheme = place("동네 과학 체험관", "체험 클래스", 37.413, 127.095, Theme.SCIENCE)
        val result = relatedPlaces(astronomer, listOf(far, sameTheme, near), here, limit = 2)
        assertEquals(listOf("별빛 천문대", "송암 천문대"), result.map { it.place.name })
        assertTrue(result.all { it.direct })
    }

    @Test
    fun `모자라면 같은 주제 장소로 가까운 순 채움`() {
        val direct = place("별빛 천문대", "과학관", 37.42, 127.10, Theme.SCIENCE)
        val theme1 = place("과학 놀이터", "체험 클래스", 37.413, 127.095, Theme.SCIENCE)
        val theme2 = place("발명 교실", "체험 클래스", 37.45, 127.12, Theme.SCIENCE)
        val unrelated = place("볼링장", "볼링장", 37.4128, 127.0948, Theme.SPORTS)
        val result = relatedPlaces(astronomer, listOf(theme2, unrelated, direct, theme1), here, limit = 3)
        assertEquals(listOf("별빛 천문대", "과학 놀이터", "발명 교실"), result.map { it.place.name })
        assertTrue(result[0].direct)
        assertFalse(result[1].direct)
    }

    @Test
    fun `너무 먼 곳과 오늘 쉬는 곳은 뺀다`() {
        val busan = place("부산 천문대", "과학관", 35.18, 129.07, Theme.SCIENCE)
        val closedMonday = place("월요일 쉬는 천문대", "과학관", 37.42, 127.10, Theme.SCIENCE, closedDays = 1)
        val result = relatedPlaces(astronomer, listOf(busan, closedMonday), here, today = DayOfWeek.MONDAY)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `모든 직업에 낱말과 주제가 있다`() {
        CareerFields.all.flatMap { it.jobs }.forEach { job ->
            assertTrue(job.name, job.keywords.isNotEmpty() && job.themes.isNotEmpty())
        }
    }
}
