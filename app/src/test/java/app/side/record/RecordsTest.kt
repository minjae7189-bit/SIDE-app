package app.side.record

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecordsTest {

    private fun talk(at: String, title: String) = RecordEntry(LocalDateTime.parse(at), RecordKind.TALK, title)

    @Test
    fun `날짜별로 묶고 최근 날짜가 먼저, 하루 안은 시간 순`() {
        val entries = listOf(
            talk("2026-11-14T10:00", "어제 오전"),
            talk("2026-11-15T18:00", "오늘 저녁"),
            talk("2026-11-15T09:00", "오늘 아침"),
            RecordEntry(LocalDateTime.parse("2026-11-15T12:00"), RecordKind.ASSESSMENT, "진로 유형 ES"),
        )
        val days = groupByDate(entries)
        assertEquals(listOf(LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-14")), days.map { it.date })
        assertEquals(listOf("오늘 아침", "진로 유형 ES", "오늘 저녁"), days[0].entries.map { it.title })
        assertEquals(2, days[0].talks.size)
        assertEquals(1, days[0].assessments.size)
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertEquals(emptyList<DayRecord>(), groupByDate(emptyList()))
    }
}
