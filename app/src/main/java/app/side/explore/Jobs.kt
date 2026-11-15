package app.side.explore

import app.side.places.Theme

/**
 * 직업 하나: 이름과 하는 일 한 줄.
 * [keywords]는 관련 장소를 찾을 때 장소 이름·종류에서 찾을 낱말, [themes]는 낱말로 못 찾을 때 쓰는 장소 주제.
 */
data class Job(val name: String, val duty: String, val keywords: List<String> = emptyList(), val themes: Set<Theme> = emptySet())

/** 직업 탐험의 분야 하나. [id]는 분야 그림(side_field_*) 이름에도 쓴다. */
data class CareerField(val id: String, val name: String, val jobs: List<Job>)

/**
 * 직업 탐험 샘플 자료. 하는 일만 쉬운 말로 적었다(연봉·전망 같은 출처가 필요한 내용은 넣지 않음).
 * 실제 서비스에서는 검증된 직업 콘텐츠로 바꾼다.
 */
object CareerFields {
    val all: List<CareerField> = listOf(
        CareerField(
            "space", "우주·항공",
            listOf(
                Job("우주비행사", "우주선을 타고 우주에 가서 실험하고 탐사해요.", listOf("우주", "천문대", "천체", "별자리", "과학관"), setOf(Theme.SCIENCE)),
                Job("항공기 조종사", "비행기를 조종해 사람과 짐을 안전하게 옮겨요.", listOf("항공", "비행", "공항"), setOf(Theme.SCIENCE)),
                Job("항공 교통 관제사", "비행기들이 부딪히지 않게 뜨고 내리는 순서와 하늘 길을 알려 줘요.", listOf("항공", "비행", "공항"), setOf(Theme.SCIENCE)),
                Job("위성 개발자", "지구 둘레를 도는 인공위성을 설계하고 만들어요.", listOf("우주", "천문대", "천체", "과학관"), setOf(Theme.SCIENCE)),
                Job("로켓 엔지니어", "로켓 엔진과 발사체를 설계하고 시험해요.", listOf("우주", "과학관", "항공"), setOf(Theme.SCIENCE)),
                Job("항공 정비사", "비행기가 안전하게 날 수 있게 꼼꼼히 점검하고 고쳐요.", listOf("항공", "비행", "공항"), setOf(Theme.SCIENCE, Theme.MAKING)),
                Job("천문학자", "망원경으로 별과 은하를 관측하고 우주의 비밀을 연구해요.", listOf("천문대", "천체", "별자리", "우주"), setOf(Theme.SCIENCE)),
            ),
        ),
        CareerField(
            "it", "IT·AI",
            listOf(
                Job("AI 개발자", "스스로 배우고 판단하는 인공지능 프로그램을 만들어요.", listOf("로봇", "과학관", "디지털", "소프트웨어"), setOf(Theme.SCIENCE)),
                Job("앱 개발자", "스마트폰과 컴퓨터에서 쓰는 앱을 만들어요.", listOf("디지털", "소프트웨어", "과학관"), setOf(Theme.SCIENCE, Theme.MAKING)),
                Job("데이터 분석가", "많은 자료 속에서 규칙을 찾아 더 나은 결정을 도와요.", listOf("과학관", "디지털", "통계"), setOf(Theme.SCIENCE)),
                Job("정보보안 전문가", "해킹을 막고 소중한 정보를 안전하게 지켜요.", listOf("디지털", "과학관", "방탈출"), setOf(Theme.SCIENCE, Theme.EXPLORE)),
                Job("게임 프로그래머", "게임 속 캐릭터와 규칙이 움직이도록 코드를 짜요.", listOf("게임", "VR", "디지털"), setOf(Theme.EXPLORE, Theme.SCIENCE)),
                Job("로봇 공학자", "스스로 움직이고 일하는 로봇을 설계하고 만들어요.", listOf("로봇", "과학관", "메이커"), setOf(Theme.SCIENCE, Theme.MAKING)),
                Job("UX 디자이너", "사람들이 앱과 기계를 쉽고 편하게 쓰도록 화면과 쓰는 순서를 설계해요.", listOf("디자인", "미술관", "디지털"), setOf(Theme.ART)),
            ),
        ),
        CareerField(
            "env", "환경·에너지",
            listOf(
                Job("환경 공학자", "물과 공기, 흙이 더러워지지 않도록 기술로 지켜요.", listOf("환경", "생태", "에너지"), setOf(Theme.NATURE)),
                Job("태양광 기술자", "햇빛으로 전기를 만드는 장치를 설치하고 관리해요.", listOf("에너지", "태양", "발전", "환경"), setOf(Theme.SCIENCE, Theme.NATURE)),
                Job("풍력 발전 기술자", "바람으로 전기를 만드는 발전기를 점검하고 고쳐요.", listOf("에너지", "발전", "풍력", "환경"), setOf(Theme.SCIENCE, Theme.NATURE)),
                Job("기후 연구원", "날씨와 기후가 어떻게 바뀌는지 연구해 미래를 예측해요.", listOf("기후", "기상", "환경", "생태"), setOf(Theme.NATURE, Theme.SCIENCE)),
                Job("재활용 전문가", "버려진 물건을 나누고 다시 쓸 수 있는 자원으로 만들어요.", listOf("자원", "재활용", "환경"), setOf(Theme.NATURE, Theme.MAKING)),
                Job("생태 조사원", "숲과 강에 사는 동물과 식물을 조사하고 보호해요.", listOf("생태", "습지", "수목원", "숲", "곤충"), setOf(Theme.NATURE)),
                Job("수질 관리원", "마시는 물과 강물이 깨끗한지 검사하고 관리해요.", listOf("수자원", "정수", "물문화", "댐", "하천", "환경"), setOf(Theme.NATURE)),
            ),
        ),
        CareerField(
            "health", "보건·의료",
            listOf(
                Job("의사", "아픈 사람을 진찰하고 병의 원인을 찾아 치료해요.", listOf("의학", "의료", "보건", "인체"), setOf(Theme.SCIENCE)),
                Job("간호사", "환자 곁에서 몸 상태를 살피고 치료와 회복을 도와요.", listOf("의학", "의료", "보건", "인체"), setOf(Theme.COMMUNITY)),
                Job("약사", "약을 정확하게 짓고 먹는 방법을 알려 줘요.", listOf("약령", "의약", "한의", "의학"), setOf(Theme.SCIENCE)),
                Job("치과의사", "이와 잇몸을 검사하고 치료해요.", listOf("치과", "의학", "인체"), setOf(Theme.SCIENCE)),
                Job("재활 치료사", "다치거나 아팠던 사람이 다시 잘 움직일 수 있게 운동과 치료를 도와요.", listOf("의학", "보건", "체육"), setOf(Theme.SPORTS, Theme.COMMUNITY)),
                Job("응급구조사", "사고 현장에서 응급 처치를 하고 환자를 병원으로 옮겨요.", listOf("안전체험", "소방", "119", "안전"), setOf(Theme.COMMUNITY)),
                Job("수의사", "아픈 동물을 진찰하고 치료해요.", listOf("동물", "곤충", "생태", "아쿠아"), setOf(Theme.NATURE)),
                Job("영양사", "건강한 식단을 짜고 급식을 관리해요.", listOf("음식", "요리", "김치", "떡", "식문화"), setOf(Theme.MAKING)),
            ),
        ),
        CareerField(
            "media", "콘텐츠·미디어",
            listOf(
                Job("영상 감독", "영화와 광고, 영상을 기획하고 촬영을 이끌어요.", listOf("영화", "영상", "방송"), setOf(Theme.ART, Theme.PERFORM)),
                Job("웹툰 작가", "이야기를 만들고 그림으로 그려 웹툰을 연재해요.", listOf("만화", "웹툰", "애니"), setOf(Theme.ART, Theme.BOOKS)),
                Job("게임 기획자", "게임의 이야기와 규칙, 재미있는 장치를 설계해요.", listOf("게임", "VR", "방탈출"), setOf(Theme.EXPLORE)),
                Job("애니메이터", "그림이 움직이도록 장면 하나하나를 만들어요.", listOf("애니", "만화", "영상"), setOf(Theme.ART)),
                Job("아나운서", "뉴스와 방송 프로그램을 정확하고 알기 쉽게 전해요.", listOf("방송", "신문", "언론"), setOf(Theme.PERFORM)),
                Job("기자", "일어난 일을 취재해 사실대로 기사를 써요.", listOf("신문", "방송", "언론"), setOf(Theme.BOOKS)),
                Job("음향 엔지니어", "소리를 녹음하고 다듬어 듣기 좋게 만들어요.", listOf("음악", "소리", "악기", "오디오"), setOf(Theme.PERFORM)),
                Job("크리에이터", "직접 만든 영상과 글로 사람들과 이야기를 나눠요.", listOf("방송", "영상", "미디어"), setOf(Theme.ART, Theme.MAKING)),
            ),
        ),
        CareerField(
            "public", "사회·공공",
            listOf(
                Job("소방관", "불을 끄고, 위험에 빠진 사람을 구조해요.", listOf("소방", "안전체험", "119"), setOf(Theme.COMMUNITY)),
                Job("경찰관", "사건을 조사하고 사람들이 안전하게 지낼 수 있게 지켜요.", listOf("경찰", "안전체험", "안전"), setOf(Theme.COMMUNITY)),
                Job("사회복지사", "도움이 필요한 사람에게 알맞은 지원을 찾아 연결해 줘요.", listOf("복지", "생활문화센터", "문화의집"), setOf(Theme.COMMUNITY)),
                Job("외교관", "다른 나라에서 우리나라를 대표하고 나라 사이의 일을 협의해요.", listOf("세계", "민속", "국제", "외교"), setOf(Theme.HISTORY)),
                Job("판사", "법에 따라 공정하게 재판하고 판결해요.", listOf("법원", "법조", "헌법"), setOf(Theme.HISTORY, Theme.COMMUNITY)),
                Job("선생님", "학생들이 배우고 자랄 수 있게 가르치고 이끌어요.", listOf("교육", "어린이", "도서관"), setOf(Theme.BOOKS)),
                Job("우편 집배원", "편지와 소포를 집집마다 전해 줘요.", listOf("우편", "우표", "체신"), setOf(Theme.HISTORY, Theme.COMMUNITY)),
            ),
        ),
    )
}
