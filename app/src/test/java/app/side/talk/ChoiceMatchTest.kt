package app.side.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoiceMatchTest {

    private val energy = listOf("밖에서 사람들과 어울리기", "집에서 조용히 쉬기")
    private val sea = listOf("깊은 바닷속 탐험", "먼 우주 탐험")
    private val sky = listOf("항공 교통 관제사", "비행기 조종사", "항공기 정비사")

    @Test
    fun `순서로 답함`() {
        assertEquals(0, matchChoice("첫 번째", energy))
        assertEquals(0, matchChoice("1번", energy))
        assertEquals(0, matchChoice("앞에 거", energy))
        assertEquals(1, matchChoice("두 번째요", energy))
        assertEquals(1, matchChoice("뒤에 거", energy))
        assertEquals(1, matchChoice("B", energy))
        assertEquals(2, matchChoice("3번", sky))
        assertEquals(2, matchChoice("마지막", sky))
    }

    @Test
    fun `보기 낱말로 답함`() {
        assertEquals(0, matchChoice("밖에서 노는 거", energy))
        assertEquals(1, matchChoice("집에서 쉴래", energy))
        assertEquals(1, matchChoice("조용히 있는 게 좋아", energy))
        assertEquals(0, matchChoice("바닷속", sea))
        assertEquals(1, matchChoice("우주가 좋아", sea))
        assertEquals(0, matchChoice("관제사", sky))
        assertEquals(1, matchChoice("조종사요", sky))
        assertEquals(2, matchChoice("정비사", sky))
        assertEquals(0, matchChoice("비 오는 날", listOf("비 오는 날 집에서", "맑은 날 공원")))
        // 2차 검토: 사이시옷("바닷속" ↔ "바다"), 맞장구가 붙은 대답
        assertEquals(0, matchChoice("바다", sea))
        assertEquals(0, matchChoice("바다요", sea))
        assertEquals(0, matchChoice("난 바다", sea))
        assertEquals(0, matchChoice("그래 게임 기획자", listOf("게임 기획자", "그래픽 디자이너")))
    }

    @Test
    fun `모든 카드의 보기를 그대로 읽으면 그 보기`() {
        val failures = Deck.entries.flatMap { SampleCards.deck(it) }.filter { it.choices.isNotEmpty() }.flatMap { card ->
            card.choices.mapIndexedNotNull { i, c -> matchChoice(c, card.choices).takeIf { it != i }?.let { "${card.id}: $c → $it" } }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `애매하면 null`() {
        assertNull(matchChoice("", energy))
        assertNull(matchChoice("음", energy))
        assertNull(matchChoice("둘 다 좋아", sea)) // 탐험만 겹침 → 같은 수
        assertNull(matchChoice("탐험", sea))
        assertNull(matchChoice("1번이랑 2번", energy))
        assertNull(matchChoice("첫 번째 집에서 쉬기", energy)) // 순서와 낱말이 다름
        assertNull(matchChoice("항공", sky)) // 관제사·정비사 둘 다
        // 검토 반례: 부정, 앱이 읽는 "이번엔 어른 차례예요" 메아리, 다른 뜻의 말
        assertNull(matchChoice("밖에 나가기 싫어", energy))
        assertNull(matchChoice("이번엔 어른 차례예요", energy))
        assertNull(matchChoice("에이 몰라", energy))
        assertNull(matchChoice("처음 보는 건데", energy))
        // 2차 검토 반례: 사람을 가리키는 서수, 무서움, 한 글자 낱말이 다른 말에 섞임
        assertNull(matchChoice("첫째 동생이 그랬어", energy))
        assertNull(matchChoice("우주는 무서워 바다", sea))
        assertNull(matchChoice("비밀이야", listOf("비 오는 날 집에서", "맑은 날 공원")))
    }
}
