package app.side.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.side.R
import app.side.assessment.AssessmentActions
import app.side.assessment.AssessmentUi
import app.side.places.PlacesActions
import app.side.places.PlacesUi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.side.talk.Deck
import app.side.talk.SampleCards
import app.side.talk.TalkPhase
import app.side.talk.TalkUiState
import app.side.vehicle.DriveState

/** 정차 중에 오갈 수 있는 화면. [inSidebar]가 false면 홈의 버튼으로만 들어간다. */
enum class SideTab(val label: String, val inSidebar: Boolean = true, val step: Int? = null) {
    HOME("홈"),

    // 여정 네 단계: 이야기 → 진로 검사 → 직업 탐험 → 함께 가 볼 곳(마지막, 강조).
    // 한 단계만 해도 가 볼 곳을 추천하지만, 단계를 더 할수록 추천 근거가 쌓여 구체적이 된다.
    TALK("이야기 나누기", step = 1),
    TEST("진로 검사", step = 2),
    EXPLORE("직업 탐험", step = 3),
    PLACES("함께 가 볼 곳", step = 4),

    // 단계가 아닌 메뉴(번호 없음)
    RECORD("성장 기록"),
    MISSION("진로 미션", inSidebar = false),
    FAMILY("가족 MBTI", inSidebar = false),
}

/** 여정의 마지막 단계(강조해서 보여 준다). */
val SideTab.isFinalStep: Boolean get() = this == SideTab.PLACES

class TalkActions(
    val play: () -> Unit,
    val pause: () -> Unit,
    val replay: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val simulateGear: (DriveState?) -> Unit,
    /** 가족 MBTI 글자를 바꾼다(null이면 그대로). 음성으로 채운 글자와 같은 상태를 쓴다. */
    val setFamilyMbti: (String?, String?) -> Unit = { _, _ -> },
    /** 카드 묶음을 고르고 그 묶음의 n번째 카드부터 시작한다. */
    val startDeck: (Deck, Int) -> Unit = { _, _ -> },
)

/**
 * 기어 상태에 따라 주행 화면 / 정차 화면을 고른다.
 * 주행 중에는 메뉴 없이 음성 화면 하나만 보여 주고, 정차하면 보던 메뉴로 돌아온다.
 */
@Composable
fun SideApp(
    ui: TalkUiState,
    driveState: DriveState,
    gearUnavailable: Boolean,
    simulatedGear: DriveState?,
    showDebugGear: Boolean,
    actions: TalkActions,
    startTab: SideTab = SideTab.HOME,
    assessment: AssessmentUi = AssessmentUi(),
    assessmentActions: AssessmentActions = AssessmentActions.None,
    places: PlacesUi = PlacesUi(),
    placesActions: PlacesActions = PlacesActions.None,
    onRequestLocation: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 2단 배치가 망가지는 원인은 폭이므로 폭만으로 판정한다(세로 분할 패널 약 841dp, 가로 약 1682dp).
        val portrait = maxWidth < PortraitMaxWidth
        CompositionLocalProvider(LocalPortrait provides portrait) {
            // 지평선은 홈과 주행 화면에만. 다른 화면은 내용이 아래까지 차서 선이 겹친다.
            // 세로 패널 홈도 내용이 아래까지 차서 지평선이 버튼을 가로지르므로 뺀다.
            SideBackground(horizon = !driveState.allowsCardUi || (tab == SideTab.HOME && !portrait))
            // 전환 애니메이션 없이 즉시 바꾼다. 페이드아웃 동안 카드가 주행 중에 보이거나 눌리는 일을 막기 위해서다.
            if (driveState.allowsCardUi) {
                ParkedShell(
                    tab = tab,
                    onTab = { tab = it },
                    ui = ui,
                    actions = actions,
                    assessment = assessment,
                    assessmentActions = assessmentActions,
                    places = places,
                    placesActions = placesActions,
                    onRequestLocation = onRequestLocation,
                )
            } else {
                DrivingScreen(ui, driveState, actions, assessment, onStopAssessment = assessmentActions.stopVoice)
            }
            if (showDebugGear && (gearUnavailable || simulatedGear != null)) {
                DebugGearBar(
                    simulated = simulatedGear,
                    onSelect = actions.simulateGear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ParkedShell(
    tab: SideTab,
    onTab: (SideTab) -> Unit,
    ui: TalkUiState,
    actions: TalkActions,
    assessment: AssessmentUi,
    assessmentActions: AssessmentActions,
    places: PlacesUi,
    placesActions: PlacesActions,
    onRequestLocation: () -> Unit,
) {
    val goHome = { onTab(SideTab.HOME) }
    // 진로 검사 화면을 떠나면 음성 검사도 멈춘다(다른 화면에는 진행 표시와 멈추기 버튼이 없다).
    LaunchedEffect(tab) {
        if (tab != SideTab.TEST) assessmentActions.stopVoice()
    }
    val body: @Composable () -> Unit = {
        when (tab) {
            SideTab.HOME -> HomeScreen(
                ui = ui,
                onStartTalk = {
                    if (!ui.isRunning) actions.play()
                    onTab(SideTab.TALK)
                },
                onOpen = onTab,
                onStartDeck = { deck, index ->
                    actions.startDeck(deck, index)
                    onTab(SideTab.TALK)
                },
                progress = JourneyProgress(
                    talk = ui.finishedCards.isNotEmpty(),
                    test = assessment.result != null,
                    explore = places.explored.isNotEmpty(),
                ),
            )
            SideTab.TALK -> TalkScreen(
                ui,
                actions,
                onBack = goHome,
                onOpenFamily = { onTab(SideTab.FAMILY) },
                onOpenPlaces = { onTab(SideTab.PLACES) },
            )
            SideTab.PLACES -> PlacesScreen(
                places = places,
                profile = buildProfile(ui, assessment, places.chosen, places.explored),
                actions = placesActions,
                onRequestPermission = onRequestLocation,
                onBack = goHome,
            )
            SideTab.FAMILY -> FamilyMbtiScreen(
                onBack = { onTab(SideTab.TALK) },
                parent = ui.familyParent,
                child = ui.familyChild,
                onChange = actions.setFamilyMbti,
            )
            SideTab.EXPLORE -> ExploreScreen(onBack = goHome, places = places, onNavigate = placesActions.openMap, onExplored = placesActions.markExplored)
            SideTab.TEST -> AssessmentScreen(assessment, assessmentActions, onBack = goHome)
            SideTab.RECORD -> RecordScreen(onBack = goHome)
            SideTab.MISSION -> ComingSoonScreen(tab, onBack = goHome)
        }
    }
    // 세로 패널은 왼쪽 메뉴 대신 위쪽에 이름과 가로 탭을 둔다. 메뉴만 바꾸고 본문(body)은 두 배치에서
    // 트리의 같은 자리에 둬야, 패널 크기가 바뀌어 배치가 전환돼도 화면 안의 상태(선택·스크롤)가 유지된다.
    val portrait = LocalPortrait.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                if (portrait) PaddingValues(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 28.dp)
                else PaddingValues(start = 36.dp, top = 32.dp, end = 40.dp, bottom = 32.dp),
            ),
    ) {
        if (portrait) {
            TopTabs(current = tab, onSelect = onTab)
            Spacer(Modifier.height(20.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (!portrait) {
                Sidebar(
                    current = tab,
                    onSelect = onTab,
                    modifier = Modifier
                        .width(196.dp)
                        .fillMaxHeight(),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SideColors.Line),
                )
                Spacer(Modifier.width(40.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { body() }
        }
    }
}

/** 세로 패널용 위쪽 메뉴: 이름 한 줄, 그 아래 가로로 넘기는 탭. 고른 탭은 아래 해 색 막대로 표시한다. */
@Composable
private fun TopTabs(current: SideTab, onSelect: (SideTab) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.side_logo), contentDescription = "SIDE", modifier = Modifier.height(36.dp))
            Spacer(Modifier.width(14.dp))
            Text("나란히, 더 멀리", style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.TextFaint)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SideTab.entries.filter { it.inSidebar }.forEach { item ->
                val selected = item == current
                Surface(
                    onClick = { onSelect(item) },
                    modifier = Modifier.height(64.dp), // 가로 메뉴 항목과 같은 터치 높이
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (item.step?.let { "$it " } ?: "") + item.label,
                            style = SideType.Label.copy(fontSize = 19.sp, fontWeight = if (selected || item.isFinalStep) FontWeight.Bold else FontWeight.Medium),
                            color = when {
                                selected -> SideColors.Text
                                item.isFinalStep -> SideColors.Sun
                                else -> SideColors.TextSoft
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .background(if (selected) SideColors.Sun else Color.Transparent, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SideColors.Line),
        )
    }
}

/** 왼쪽 메뉴: 위에 이름, 아래로 번호 붙은 글자 메뉴. 고른 항목은 왼쪽 해 색 막대로 표시한다. */
@Composable
private fun Sidebar(current: SideTab, onSelect: (SideTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 20.dp)) {
        Image(painterResource(R.drawable.side_logo), contentDescription = "SIDE", modifier = Modifier.height(44.dp))
        Spacer(Modifier.height(10.dp))
        Text("나란히, 더 멀리", style = SideType.LabelSmall.copy(fontSize = 15.sp, lineHeight = 21.sp), color = SideColors.TextFaint)
        Spacer(Modifier.height(44.dp))
        SideTab.entries.filter { it.inSidebar }.forEach { item ->
            val selected = item == current
            // 단계가 아닌 메뉴(성장 기록) 앞에 구분선
            if (item == SideTab.RECORD) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SideColors.Line),
                )
                Spacer(Modifier.height(10.dp))
            }
            Surface(
                onClick = { onSelect(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(if (selected) SideColors.Sun else Color.Transparent, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    // 단계 번호(01~04). 홈·성장 기록은 번호 없이 같은 자리를 비운다.
                    Text(
                        item.step?.let { twoDigits(it) } ?: "",
                        style = SideType.Number.copy(fontSize = 17.sp),
                        color = if (selected || item.isFinalStep) SideColors.Sun else SideColors.TextFaint,
                        modifier = Modifier.width(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.label,
                        style = SideType.Label.copy(fontSize = 20.sp, fontWeight = if (selected || item.isFinalStep) FontWeight.Bold else FontWeight.Medium),
                        color = when {
                            selected -> SideColors.Text
                            item.isFinalStep -> SideColors.Sun
                            else -> SideColors.TextSoft
                        },
                    )
                }
            }
        }
    }
}

/** 기어 신호를 받을 수 없는 환경에서 시연용으로 기어를 바꾸는 막대(디버그 빌드 전용). */
@Composable
private fun DebugGearBar(simulated: DriveState?, onSelect: (DriveState?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(0x80000000), RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("기어 시뮬", style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.TextSoft, modifier = Modifier.padding(horizontal = 6.dp))
        listOf(DriveState.PARKED to "P", DriveState.DRIVING to "D", null to "실제").forEach { (state, label) ->
            val selected = simulated == state
            Surface(
                onClick = { onSelect(state) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) SideColors.Sun else Color.Transparent,
            ) {
                Text(
                    label,
                    style = SideType.LabelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (selected) SideColors.OnSun else SideColors.Text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ───────────────────────── 미리보기 ─────────────────────────

private val previewActions = TalkActions({}, {}, {}, {}, {}, {})

@Composable
private fun PreviewApp(tab: SideTab, ui: TalkUiState, drive: DriveState = DriveState.PARKED) = SideTheme {
    SideApp(ui, drive, gearUnavailable = false, simulatedGear = null, showDebugGear = false, actions = previewActions, startTab = tab)
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePreview() = PreviewApp(SideTab.HOME, TalkUiState(SampleCards.elementary, engineName = "Pleos TTS"))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun TalkPreview() = PreviewApp(
    SideTab.TALK,
    TalkUiState(SampleCards.elementary, index = 1, phase = TalkPhase.LISTENING, listenSecondsLeft = 5, engineName = "Pleos TTS"),
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ExplorePreview() = PreviewApp(SideTab.EXPLORE, TalkUiState(SampleCards.elementary))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun DrivingPreview() = PreviewApp(
    SideTab.HOME,
    TalkUiState(SampleCards.elementary, index = 2, phase = TalkPhase.ASKING, engineName = "Pleos TTS"),
    drive = DriveState.DRIVING,
)
