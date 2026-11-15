package app.side.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceAnswerTest {

    private fun scale(n: Int) = VoiceAnswer.Scale(n)

    @Test
    fun `번호로 답한 말`() {
        mapOf(
            "3" to 3, "3번" to 3, "3번이요" to 3, "음 4번" to 4, "세 번째요" to 3, "5요" to 5,
            // 2차 검토: 군소리·맞장구가 붙은 번호, 고르는 말이 붙은 번호, 서수
            "응 3번" to 3, "네 3번이요" to 3, "그럼 3번" to 3, "나는 3번" to 3, "3번으로요" to 3, "3번으로 할게요" to 3,
            "3번 할래" to 3, "3번이야" to 3, "3번입니다" to 3, "4번 같아" to 4, "3번 3번" to 3, "음음 3번" to 3, "다시 3번" to 3,
            "둘째요" to 2, "셋째" to 3, "이 번" to 2, "삼 번" to 3, "삼" to 3, "오번이요" to 5, "육 번" to 6, "일번" to 1,
            "셋" to 3, "다섯" to 5, "여섯이요" to 6, "두 번" to 2, "네 번" to 4, "세 번째" to 3, "첫 번째" to 1,
        ).mapNotNull { (text, n) -> parseVoiceAnswer(text).takeIf { it != scale(n) }?.let { "$text → $it" } }
            .let { assertEquals(emptyList<String>(), it) }
    }

    @Test
    fun `말로 답한 대답`() {
        mapOf(
            "전혀 아니요" to 1, "전혀 탐구하고 싶지 않다" to 1, "매우 그렇다" to 6, "아주 좋아요" to 6, "매우 싫어요" to 1,
            "약간 그래요" to 4, "조금 싫어" to 3, "약간 그렇지 않다" to 3, "아니요" to 2, "싫어" to 2, "그렇지 않아요" to 2,
            "네" to 5, "응" to 5, "그래요" to 5, "하고 싶어요" to 5, "네요" to 5,
            // 검토 반례: 단독 "안"은 부정
            "안 좋아요" to 2, "안 그래요" to 2, "안 맞아요" to 2,
            // 2차 검토: 붙여 들린 부정, 아이 말투, 안내대로 정도어만 말함
            "안좋아" to 2, "안그래" to 2, "좀 그래요" to 4, "좀 아니에요" to 3, "아냐" to 2, "아뇨" to 2,
            "궁금해" to 5, "아주 궁금해" to 6, "조금 궁금해" to 4, "재밌어" to 5, "너무 재밌어" to 6, "즐거워" to 5,
            "잘해" to 5, "아주 잘해" to 6, "너무 좋아" to 6, "하나도 안 좋아" to 1, "절대 싫어" to 1,
            "조금" to 4, "아주" to 6, "매우" to 6, "약간" to 4, "조금이요" to 4, "아주요" to 6,
        ).mapNotNull { (text, n) -> parseVoiceAnswer(text).takeIf { it != scale(n) }?.let { "$text → $it" } }
            .let { assertEquals(emptyList<String>(), it) }
    }

    @Test
    fun `보기 문구의 말을 반말로 답함`() {
        val options = listOf("전혀 관심이 없어요", "관심이 없어요", "조금 관심이 없어요", "조금 관심이 있어요", "관심이 있어요", "아주 관심이 있어요")
        assertEquals(scale(5), parseVoiceAnswer("관심 있어", options))
        assertEquals(scale(6), parseVoiceAnswer("아주 관심 있어", options))
        assertEquals(scale(2), parseVoiceAnswer("관심 없어", options))
        val middle = listOf("전혀 흥미를 느끼지 않는다", "흥미를 느끼지 않는다", "약간 흥미를 느끼지 않는다", "약간 흥미를 느낀다", "흥미를 느낀다", "매우 흥미를 느낀다")
        assertEquals(scale(5), parseVoiceAnswer("흥미를 느껴요", middle))
        assertEquals(scale(4), parseVoiceAnswer("약간 흥미를 느껴요", middle))
        assertEquals(scale(6), parseVoiceAnswer("매우 즐겨요"))
    }

    @Test
    fun `명령은 짧은 말일 때만`() {
        assertEquals(VoiceAnswer.Scale(6), parseVoiceAnswer("그만큼 좋아 매우"))
        assertNotEquals(VoiceAnswer.Stop, parseVoiceAnswer("나중에 의사 하고 싶어요 정말로"))
        assertEquals(VoiceAnswer.Repeat, parseVoiceAnswer("한 번만 더"))
    }

    @Test
    fun `명령`() {
        assertEquals(VoiceAnswer.Previous, parseVoiceAnswer("이전 문제"))
        assertEquals(VoiceAnswer.Previous, parseVoiceAnswer("뒤로"))
        assertEquals(VoiceAnswer.Stop, parseVoiceAnswer("그만할래"))
        assertEquals(VoiceAnswer.Stop, parseVoiceAnswer("나중에 할게"))
        assertEquals(VoiceAnswer.Repeat, parseVoiceAnswer("다시 말해 줘"))
        assertEquals(VoiceAnswer.Repeat, parseVoiceAnswer("한 번 더"))
        assertEquals(VoiceAnswer.Repeat, parseVoiceAnswer("뭐라고?"))
    }

    @Test
    fun `애매하면 모른다`() {
        listOf(
            "", "3번이랑 5번", "1부터 6까지", "10번", "음", "글쎄", "이거 뭐야", "오늘 날씨", "7번",
            // 에뮬레이터에서 실제로 잡힌 주변 대화: 부정어가 섞여도 긴 말은 답이 아니다
            "터미널이 스타디움 없는 날이고", "옛날 주식이 너무 위험해 그래서 안 했어",
            // 검토 반례: 모름·망설임, 이중 부정, 번호처럼 보이는 말
            "잘 모르겠네요", "모르겠네", "정말 모르겠어", "완전 몰라", "그냥 그래", "싫지 않아", "나쁘지 않아",
            "한 번만 더 생각해 볼게", "한 번 생각해 볼게요", "이번 건 잘 모르겠어요",
            // 2차 검토 반례: 문장 속 숫자·서수, 감탄사, 정도어가 섞인 말
            "3시까지 도착해", "5분만 더 가면 돼", "2킬로미터 앞에서 우회전", "첫째 동생이 그랬어",
            "아니 잠깐만", "잠깐만", "정말?", "진짜?", "아주 조금", "오", "이번 주말에 할머니 집 가자", "이번엔 잘 모르겠어",
            "두 번 말했잖아", "네비 꺼", "예쁘다", "이전에 가봤어", "그만큼",
            // 2차 검토: 망설임·이어 가는 말·맞장구·군소리
            "안싫어", "그저 그래", "그래서", "그렇게", "아니 좋아", "아니야 좋아", "이", "이요", "저기 있어", "여기 있어",
            "응 알았어", "네 알겠습니다", "3번이랑 5번 사이",
        ).mapNotNull { text -> parseVoiceAnswer(text).takeIf { it != VoiceAnswer.Unknown }?.let { "$text → $it" } }
            .let { assertEquals(emptyList<String>(), it) }
    }
}
