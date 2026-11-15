package app.side.talk

/**
 * 가족 MBTI 가이드: 함께 탄 어른(부모·조부모·보호자 누구든)과 아이의 MBTI를 축마다 비교해 "이럴 땐 이렇게" 대화 팁을 준다.
 * MBTI는 서로를 이해하는 놀이로만 쓰고, 성격이나 진로를 판정하지 않는다.
 * 문구는 1차 데모용이며, 실제 서비스에서는 상담 전문가 검토를 거쳐 바꾼다.
 */
object FamilyMbti {

    /** MBTI 한 축. [first]·[second]는 두 글자, [tips]는 (어른 글자, 아이 글자) → 팁. */
    data class Axis(
        val name: String,
        val first: Char,
        val second: Char,
        val firstMeaning: String,
        val secondMeaning: String,
        val tips: Map<Pair<Char, Char>, Tip>,
    )

    data class Tip(val title: String, val careful: String, val tryThis: String)

    const val NOTICE = "MBTI는 서로를 이해하는 재미있는 도구일 뿐, 사람을 정하는 검사가 아니에요. 같은 사람도 상황에 따라 다르게 행동할 수 있어요."

    val axes: List<Axis> = listOf(
        Axis(
            name = "힘을 얻는 방법",
            first = 'E', second = 'I',
            firstMeaning = "사람들과 어울리며 힘을 얻어요",
            secondMeaning = "혼자만의 시간에 힘을 채워요",
            tips = mapOf(
                ('E' to 'I') to Tip(
                    "어른 E · 아이 I",
                    "대답이 늦다고 재촉하거나, 여러 질문을 한꺼번에 던지지 마세요.",
                    "질문 하나를 하고 기다려 주세요. 마주 보는 자리보다 차 안처럼 나란히 앉은 자리가 말 꺼내기 편할 수 있어요.",
                ),
                ('I' to 'E') to Tip(
                    "어른 I · 아이 E",
                    "이야기가 길어진다고 중간에 끊으면 아이가 서운해할 수 있어요.",
                    "아이는 말하면서 생각을 정리해요. 지칠 땐 \"조금 쉬었다가 이어서 듣자\"고 솔직하게 말해도 괜찮아요.",
                ),
                ('E' to 'E') to Tip(
                    "둘 다 E",
                    "둘 다 말하고 싶어서 말이 자주 겹칠 수 있어요.",
                    "한 사람씩 차례로 말하기로 정해 보세요. 밸런스 게임처럼 순서가 있는 놀이가 잘 맞아요.",
                ),
                ('I' to 'I') to Tip(
                    "둘 다 I",
                    "서로 조용해서 대화가 짧게 끝나기 쉬워요.",
                    "고르기 쉬운 질문부터 시작해 보세요. 말이 없어도 함께 있는 시간 자체가 좋은 대화예요.",
                ),
            ),
        ),
        Axis(
            name = "보는 눈",
            first = 'S', second = 'N',
            firstMeaning = "지금 보이는 사실을 먼저 봐요",
            secondMeaning = "그 너머의 가능성을 먼저 상상해요",
            tips = mapOf(
                ('S' to 'N') to Tip(
                    "어른 S · 아이 N",
                    "엉뚱한 상상을 \"말도 안 돼\"로 끊지 마세요.",
                    "\"그다음엔 어떻게 될까?\" 하고 이어 주세요. 상상을 키우는 질문이 아이의 흥미를 보여 줘요.",
                ),
                ('N' to 'S') to Tip(
                    "어른 N · 아이 S",
                    "큰 그림이나 먼 미래 이야기만 하면 아이가 막막해할 수 있어요.",
                    "\"내일 몇 시에, 무엇을\"처럼 구체적인 예로 이야기해 주세요.",
                ),
                ('S' to 'S') to Tip(
                    "둘 다 S",
                    "늘 하던 이야기만 반복되기 쉬워요.",
                    "가끔은 \"만약에\" 질문으로 상상 놀이를 해 보세요.",
                ),
                ('N' to 'N') to Tip(
                    "둘 다 N",
                    "상상이 끝없이 이어지다 실천으로 이어지지 않을 수 있어요.",
                    "이야기 끝에 \"그럼 이번 주에 하나만 해 볼까?\" 하고 작은 계획으로 연결해 보세요.",
                ),
            ),
        ),
        Axis(
            name = "마음을 정하는 방법",
            first = 'T', second = 'F',
            firstMeaning = "까닭과 방법을 먼저 따져요",
            secondMeaning = "사람의 마음을 먼저 살펴요",
            tips = mapOf(
                ('T' to 'F') to Tip(
                    "어른 T · 아이 F",
                    "속상한 이야기에 해결책부터 말하면 아이는 혼난다고 느낄 수 있어요.",
                    "\"그래서 속상했구나\" 하고 마음을 먼저 알아준 뒤에 방법을 이야기해 주세요.",
                ),
                ('F' to 'T') to Tip(
                    "어른 F · 아이 T",
                    "아이 말투가 차갑게 들려도 상처 주려는 뜻이 아닐 수 있어요.",
                    "\"왜 그렇게 생각했어?\" 하고 까닭을 물어보세요. 아이는 논리적으로 잘 설명해 줄 거예요.",
                ),
                ('T' to 'T') to Tip(
                    "둘 다 T",
                    "문제는 빨리 풀지만 서로의 기분을 놓치기 쉬워요.",
                    "대화 끝에 \"지금 기분은 어때?\" 하고 한 번 물어보세요.",
                ),
                ('F' to 'F') to Tip(
                    "둘 다 F",
                    "서로 마음을 잘 알아주지만, 결정을 미루기 쉬워요.",
                    "고를 일이 있을 땐 기준을 하나만 먼저 정해 보세요.",
                ),
            ),
        ),
        Axis(
            name = "생활 방식",
            first = 'J', second = 'P',
            firstMeaning = "미리 계획하고 정리하는 게 편해요",
            secondMeaning = "그때그때 유연하게 움직이는 게 편해요",
            tips = mapOf(
                ('J' to 'P') to Tip(
                    "어른 J · 아이 P",
                    "계획을 모두 정해 주면 아이가 답답해할 수 있어요.",
                    "꼭 지킬 것 하나만 함께 정하고, 나머지 순서는 아이에게 맡겨 보세요.",
                ),
                ('P' to 'J') to Tip(
                    "어른 P · 아이 J",
                    "갑자기 바뀌는 일정에 아이가 불안해할 수 있어요.",
                    "바뀌는 일이 있으면 까닭과 함께 미리 알려 주세요.",
                ),
                ('J' to 'J') to Tip(
                    "둘 다 J",
                    "계획이 틀어지면 둘 다 예민해질 수 있어요.",
                    "일정 사이에 여유 시간을 조금 남겨 두세요.",
                ),
                ('P' to 'P') to Tip(
                    "둘 다 P",
                    "자유롭지만 꼭 해야 할 일을 함께 놓칠 수 있어요.",
                    "꼭 해야 할 일만 같이 적어 두고, 나머지는 즐겁게 즉흥으로 해 보세요.",
                ),
            ),
        ),
    )

    /** "ENFP"처럼 네 글자. 고르지 않은 자리는 '-'. */
    const val EMPTY = "----"

    fun tip(axisIndex: Int, parent: String, child: String): Tip? {
        val p = parent.getOrNull(axisIndex) ?: return null
        val c = child.getOrNull(axisIndex) ?: return null
        if (p == '-' || c == '-') return null
        return axes[axisIndex].tips[p to c]
    }
}
