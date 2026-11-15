package app.side.talk

/** 카드 종류. 셋 다 "질문 → 답을 기다림 → 이야기" 순서로 진행한다. */
enum class CardKind {
    /** 열린 질문. */
    TALK,

    /** 둘 중 하나 고르기. 고른 쪽과 어울리는 홀랜드 유형·직업을 이야기한다. */
    BALANCE,

    /** 직업 맞히기(보기 3개). 이야기 첫머리에서 정답을 알려 준다. */
    QUIZ,
}

/** 카드 묶음. 정차 화면과 홈에서 고른다. */
enum class Deck(val label: String, val hint: String) {
    TALK("이야기 카드", "요즘 제일 재밌는 게 뭐야?"),
    BALANCE("밸런스 게임", "깊은 바닷속 탐험 vs 먼 우주 탐험"),
    QUIZ("직업 퀴즈", "하늘 길을 정해 주는 사람은?"),
    MBTI("가족 MBTI", "서로의 MBTI를 맞혀 볼까?"),
}

/**
 * 진로 토크 카드 한 장.
 * @property question 화면에 보이는 질문
 * @property story 답을 기다린 뒤 들려주는 이야기(퀴즈는 정답 설명)
 * @property topic 화면 카드에 붙는 짧은 주제
 * @property choices 밸런스 게임 2개, 퀴즈 3개
 * @property answer 퀴즈 정답 번호(0부터)
 */
data class CareerCard(
    val id: String,
    val topic: String,
    val question: String,
    val story: String,
    val kind: CardKind = CardKind.TALK,
    val choices: List<String> = emptyList(),
    val answer: Int = -1,
) {
    /** 소리 내어 읽는 질문. 보기는 화면을 보지 않아도 알 수 있게 질문 뒤에 붙인다. */
    val spoken: String
        get() = when (kind) {
            CardKind.TALK -> question
            CardKind.BALANCE -> "$question ${choices[0]}, 아니면 ${choices[1]}?"
            CardKind.QUIZ -> question + " " + choices.mapIndexed { i, c -> "${i + 1}번 $c" }.joinToString(", ") + "."
        }
}

/**
 * 1차 데모용 샘플 카드(초등학생 대상).
 * 실제 서비스에서는 검증된 진로 토크 콘텐츠를 음성 콘텐츠로 변환해 교체한다.
 */
object SampleCards {

    fun deck(deck: Deck): List<CareerCard> = when (deck) {
        Deck.TALK -> elementary
        Deck.BALANCE -> balance
        Deck.QUIZ -> quiz
        Deck.MBTI -> mbti
    }

    /** 이야기 카드: 답하기 쉬운 질문부터 시작해 함께 탄 어른의 차례, 창밖 관찰로 이어진다. */
    val elementary: List<CareerCard> = listOf(
        CareerCard(
            id = "fun",
            topic = "좋아하는 것",
            question = "요즘 제일 재밌는 게 뭐야?",
            story = "좋아하는 걸 오래 하다 보면 그게 직업이 되기도 해. 게임이 좋아서 게임을 만드는 사람도 있고, " +
                "그림이 좋아서 그림책 작가가 된 사람도 있어.",
        ),
        CareerCard(
            id = "weekend",
            topic = "노는 방법",
            question = "하루가 통째로 비어 있으면 뭐 하면서 놀고 싶어?",
            story = "노는 방식에도 힌트가 있어. 만들기를 좋아하면 무언가를 만드는 일, 친구들과 어울리는 걸 좋아하면 " +
                "사람을 돕고 이끄는 일이 잘 맞을 수 있대.",
        ),
        CareerCard(
            id = "proud",
            topic = "잘한 일",
            question = "최근에 스스로 잘했다고 느낀 일 하나만 말해 줄래?",
            story = "잘한 일을 떠올리면 내가 가진 힘이 보여. 꼼꼼함, 끈기, 친절함 같은 힘은 어떤 직업에서든 빛나.",
        ),
        CareerCard(
            id = "one-day",
            topic = "상상",
            question = "딱 하루 동안 무엇이든 될 수 있다면, 뭐가 되고 싶어?",
            story = "세상에는 우리가 아는 것보다 훨씬 많은 직업이 있어. 우리나라에만 만 개가 넘는 직업이 있대. " +
                "오늘 말한 것과 비슷한 직업도 같이 찾아보자.",
        ),
        CareerCard(
            id = "strength",
            topic = "강점",
            question = "친구들이 너한테 자주 부탁하는 건 뭐야?",
            story = "다른 사람이 나에게 부탁하는 일은 내가 잘하는 일일 때가 많아. 그런 강점이 직업의 씨앗이 돼.",
        ),
        CareerCard(
            id = "parents",
            topic = "어른의 일",
            question = "같이 탄 어른이 하는 일 중에 궁금한 게 있어? 지금 물어봐!",
            story = "이번엔 어른 차례예요. 하시는 일에서 가장 보람 있었던 순간을 아이에게 들려주세요.",
        ),
        CareerCard(
            id = "parents-young",
            topic = "어른의 어릴 적",
            question = "같이 탄 어른은 어렸을 때 뭐가 되고 싶었는지 물어볼래?",
            story = "어른 차례예요. 어릴 적 되고 싶었던 것과 지금 하는 일이 어떻게 이어졌는지, 또는 어떻게 달라졌는지 들려주세요.",
        ),
        CareerCard(
            id = "window",
            topic = "길 위의 직업",
            question = "창밖에 보이는 것 중에, 누군가의 직업과 관련된 걸 하나 찾아볼까?",
            story = "도로, 표지판, 트럭. 길 위에도 많은 사람의 일이 숨어 있어. 도로를 설계하는 토목 기술자, " +
                "물건을 나르는 화물차 기사님처럼 말이야.",
        ),
    )

    /** 밸런스 게임: 취미와 적성을 두 갈래로 나눠 고르게 하고, 고른 쪽과 어울리는 유형·직업을 들려준다. */
    val balance: List<CareerCard> = listOf(
        CareerCard(
            id = "b-explore",
            topic = "탐험",
            question = "둘 중 하나만 할 수 있다면?",
            choices = listOf("깊은 바닷속 탐험", "먼 우주 탐험"),
            story = "바다를 고른 친구는 해양생물학자나 잠수 기술자, 우주를 고른 친구는 천문학자나 우주비행사와 닮았어. " +
                "둘 다 궁금한 걸 끝까지 알아내는 탐구형 친구들이 좋아하는 일이야.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "b-weekend",
            topic = "주말",
            question = "주말 하루, 뭘 할래?",
            choices = listOf("레고로 멋진 걸 직접 만들기", "친구들을 모아서 놀이 진행하기"),
            story = "직접 만드는 걸 고른 친구는 손과 도구를 쓰는 현실형, 놀이를 이끄는 걸 고른 친구는 사람들을 이끄는 진취형에 가까워. " +
                "로봇 기술자와 행사 기획자처럼 말이야.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "b-homework",
            topic = "숙제",
            question = "숙제를 하나만 고를 수 있다면?",
            choices = listOf("그림 그리기 숙제", "수학 퍼즐 숙제"),
            story = "그림을 고르면 디자이너나 웹툰 작가처럼 표현하는 일, 퍼즐을 고르면 데이터 분석가나 프로그래머처럼 " +
                "규칙을 찾아내는 일과 닮았어.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "b-team",
            topic = "모둠 활동",
            question = "모둠 활동에서 맡고 싶은 역할은?",
            choices = listOf("모르는 친구에게 알려 주기", "계획 세우고 정리하기"),
            story = "알려 주는 역할은 선생님이나 상담사처럼 사람을 돕는 사회형, 정리하는 역할은 회계사나 사서처럼 " +
                "꼼꼼하게 관리하는 관습형과 닮았어.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "b-care",
            topic = "돌보기",
            question = "하나만 키울 수 있다면?",
            choices = listOf("강아지나 고양이", "작은 로봇"),
            story = "동물을 고른 친구는 수의사나 동물 조련사, 로봇을 고른 친구는 로봇 공학자와 잘 어울려. " +
                "돌보는 마음도, 고치는 손재주도 모두 소중한 적성이야.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "b-stage",
            topic = "학예회",
            question = "학예회에서 하나만 맡는다면?",
            choices = listOf("무대에서 노래나 연기하기", "무대 뒤에서 조명과 음향 맡기"),
            story = "무대 위는 가수나 배우, 무대 뒤는 음향 감독이나 무대 감독이 하는 일이야. 공연은 앞과 뒤가 함께 있어야 완성돼.",
            kind = CardKind.BALANCE,
        ),
    )

    /** 직업 퀴즈: 비슷해 보이는 직업 셋 중 하나를 맞히고, 정답 설명에서 나머지 둘의 일도 알려 준다. */
    val quiz: List<CareerCard> = listOf(
        CareerCard(
            id = "q-air",
            topic = "하늘",
            question = "비행기가 안전하게 뜨고 내리도록 하늘 길을 정해 주는 사람은 누구일까?",
            choices = listOf("항공 교통 관제사", "비행기 조종사", "항공기 정비사"),
            answer = 0,
            story = "정답은 1번, 항공 교통 관제사야! 관제탑에서 여러 비행기에 무전으로 순서를 알려 줘. " +
                "조종사는 비행기를 몰고, 정비사는 비행기를 고치지.",
            kind = CardKind.QUIZ,
        ),
        CareerCard(
            id = "q-medicine",
            topic = "약",
            question = "새로운 약을 만들기 위해 실험실에서 연구하는 사람은 누구일까?",
            choices = listOf("약사", "신약 개발 연구원", "간호사"),
            answer = 1,
            story = "정답은 2번, 신약 개발 연구원이야! 약사는 만들어진 약을 알맞게 지어 주고, 간호사는 환자를 돌봐. " +
                "약 하나가 나오기까지 십 년 넘게 걸리기도 한대.",
            kind = CardKind.QUIZ,
        ),
        CareerCard(
            id = "q-game",
            topic = "게임",
            question = "게임 속 이야기와 규칙을 처음 생각해 내는 사람은 누구일까?",
            choices = listOf("게임 기획자", "프로게이머", "게임 그래픽 디자이너"),
            answer = 0,
            story = "정답은 1번, 게임 기획자야! 기획자가 규칙을 짜면 디자이너가 그림을 그리고, 프로그래머가 움직이게 만들어. " +
                "게임 하나에도 여러 직업이 함께해.",
            kind = CardKind.QUIZ,
        ),
        CareerCard(
            id = "q-road",
            topic = "길",
            question = "도로나 다리를 튼튼하게 설계하는 사람은 누구일까?",
            choices = listOf("건축가", "토목 기술자", "측량사"),
            answer = 1,
            story = "정답은 2번, 토목 기술자야! 건축가는 건물을, 토목 기술자는 도로와 다리 같은 시설을 설계해. " +
                "측량사는 땅의 높이와 거리를 재 주지. 지금 달리는 이 길도 누군가 설계한 거야.",
            kind = CardKind.QUIZ,
        ),
        CareerCard(
            id = "q-museum",
            topic = "박물관",
            question = "박물관에서 전시를 기획하고 유물을 연구하는 사람은 누구일까?",
            choices = listOf("학예사", "도슨트", "문화재 수리 기술자"),
            answer = 0,
            story = "정답은 1번, 학예사야! 큐레이터라고도 불러. 도슨트는 관람객에게 전시를 설명해 주고, " +
                "문화재 수리 기술자는 낡은 유물을 고쳐. 다음에 박물관에 가면 누가 어떤 일을 하는지 찾아보자.",
            kind = CardKind.QUIZ,
        ),
    )

    /**
     * 가족 MBTI: 네 가지 축을 가족 모두가 하나씩 고르는 밸런스 게임으로 묻고, 서로 다를 때 조심할 점을 들려준다.
     * 마지막에 서로의 네 글자를 맞혀 본다. 정차하면 '가족 MBTI' 화면에서 어른·아이 조합별 가이드를 볼 수 있다.
     * MBTI는 서로를 이해하는 놀이로만 쓰고, 진로나 성격을 판정하는 데 쓰지 않는다.
     */
    val mbti: List<CareerCard> = listOf(
        CareerCard(
            id = "m-ei",
            topic = "에너지",
            question = "주말에 힘을 채우는 방법은? 가족 모두 하나씩 골라 봐.",
            choices = listOf("밖에서 사람들과 어울리기", "집에서 조용히 쉬기"),
            story = "밖을 고른 사람은 E, 집을 고른 사람은 I에 가까워요. E는 사람들과 어울리며 힘을 얻고, I는 혼자만의 시간에 힘을 채워요. " +
                "서로 다르다면, I인 가족이 쉬는 시간을 꼭 지켜 주세요.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "m-sn",
            topic = "보는 눈",
            question = "창밖의 구름을 보면 먼저 떠오르는 건?",
            choices = listOf("오늘 비가 올까?", "저 구름은 무슨 모양일까?"),
            story = "비를 떠올린 사람은 S, 모양을 상상한 사람은 N에 가까워요. S는 지금 보이는 사실을, N은 그 너머의 가능성을 먼저 봐요. " +
                "서로 다르다면, 상상은 끝까지 들어 주고 설명은 예를 들어 해 주세요.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "m-tf",
            topic = "위로",
            question = "친구가 시험을 망쳐서 속상해할 때, 먼저 하고 싶은 말은?",
            choices = listOf("다음엔 이렇게 공부해 봐", "많이 속상했겠다"),
            story = "방법을 알려 주고 싶은 사람은 T, 마음을 먼저 알아주고 싶은 사람은 F에 가까워요. " +
                "서로 다르다면, 지금 위로가 필요한지 방법이 필요한지 먼저 물어보세요.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "m-jp",
            topic = "여행",
            question = "가족 여행을 간다면?",
            choices = listOf("시간표까지 미리 짜기", "가서 끌리는 대로 다니기"),
            story = "미리 짜는 사람은 J, 끌리는 대로 다니는 사람은 P에 가까워요. " +
                "서로 다르다면, 꼭 갈 곳 한 곳만 함께 정하고 나머지는 그때그때 정해 보세요.",
            kind = CardKind.BALANCE,
        ),
        CareerCard(
            id = "m-guess",
            topic = "맞히기",
            question = "이제 서로의 MBTI를 맞혀 볼까? 아이가 먼저 함께 탄 어른의 네 글자를 맞혀 봐!",
            story = "맞힌 글자도, 틀린 글자도 좋은 이야깃거리예요. 차를 세우면 화면에서 가족의 MBTI를 넣고, " +
                "서로 다른 점에서 무엇을 조심하면 좋을지 함께 볼 수 있어요.",
        ),
    )
}
