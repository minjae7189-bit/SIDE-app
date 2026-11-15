package app.side.ui

import app.side.assessment.AssessmentUi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.talk.CardKind
import app.side.talk.TalkPhase
import app.side.talk.TalkUiState
import app.side.vehicle.DriveState

/**
 * 주행 중 화면: 글자를 읽지 않아도 되도록 상태만 크게 보여 준다.
 * 메뉴·질문 글자·카드는 띄우지 않고, 조작은 큰 버튼 하나뿐이다. 움직이는 효과는 없다.
 */
@Composable
fun DrivingScreen(
    ui: TalkUiState,
    driveState: DriveState,
    actions: TalkActions,
    assessment: AssessmentUi = AssessmentUi(),
    onStopAssessment: () -> Unit = {},
) {
    if (assessment.voiceOn) {
        AssessmentDriving(assessment, onStopAssessment)
        return
    }
    val failed = ui.lastError != null && !ui.isRunning
    // 내용은 지평선 위쪽에만 둔다(큰 버튼이 지평선 선이나 해와 겹치지 않게).
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(HORIZON)
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(active = ui.isRunning, size = 200.dp)
        Spacer(Modifier.height(20.dp))
        Text(drivingHeadline(ui), style = SideType.Title.copy(fontSize = 52.sp), color = SideColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                failed -> "음성을 재생하지 못했어요. 다시 시도해 주세요"
                driveState == DriveState.UNKNOWN -> "차량 상태를 확인할 수 없어 음성으로만 진행해요"
                else -> "주행 중에는 음성으로만 진행해요"
            },
            style = SideType.Body.copy(fontSize = 22.sp),
            color = if (failed) SideColors.ErrorSoft else SideColors.TextSoft,
        )
        Spacer(Modifier.height(36.dp))
        SunButton(
            text = mainButtonLabel(ui),
            onClick = if (ui.isRunning) actions.pause else actions.play,
            modifier = Modifier.size(width = 340.dp, height = 96.dp),
            icon = if (ui.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            style = SideType.Button.copy(fontSize = 28.sp),
            shape = RoundedCornerShape(48.dp),
        )
    }
}

/** 음성 진로 검사 중의 주행 화면: 진행 수와 상태, 멈추기 버튼 하나. 문항 글자는 띄우지 않는다. */
@Composable
private fun AssessmentDriving(assessment: AssessmentUi, onStop: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(HORIZON)
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(active = true, size = 200.dp)
        Spacer(Modifier.height(20.dp))
        Text("진로 검사 ${assessment.answers.size} / ${assessment.total}", style = SideType.Title.copy(fontSize = 52.sp), color = SideColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            assessment.voiceStatus.ifBlank { "음성으로 답해 주세요" },
            style = SideType.Body.copy(fontSize = 22.sp),
            color = SideColors.TextSoft,
        )
        Spacer(Modifier.height(36.dp))
        SunButton(
            text = "검사 멈추기",
            onClick = onStop,
            modifier = Modifier.size(width = 340.dp, height = 96.dp),
            icon = Icons.Rounded.Pause,
            style = SideType.Button.copy(fontSize = 28.sp),
            shape = RoundedCornerShape(48.dp),
        )
    }
}

private fun drivingHeadline(ui: TalkUiState): String = when (ui.phase) {
    TalkPhase.READY -> "오늘, 어떤 이야기를 나눠볼까요?"
    TalkPhase.ASKING -> when (ui.card.kind) {
        CardKind.TALK -> "질문"
        CardKind.BALANCE -> "밸런스 게임"
        CardKind.QUIZ -> "퀴즈"
    } + " ${ui.index + 1} / ${ui.total}"
    TalkPhase.LISTENING -> when (ui.card.kind) {
        CardKind.TALK -> "네 이야기를 들려줘"
        CardKind.BALANCE -> "어느 쪽이 좋아?"
        CardKind.QUIZ -> "정답은 뭘까?"
    }
    TalkPhase.STORY -> when (ui.card.kind) {
        CardKind.TALK -> "직업 이야기"
        CardKind.BALANCE -> "어울리는 직업"
        CardKind.QUIZ -> "정답 공개"
    }
    TalkPhase.PAUSED -> "잠깐 쉬는 중"
    TalkPhase.FINISHED -> "오늘의 이야기 완료"
}

/** 아직 만들지 않은 메뉴(성장 기록·설정·진로 미션)의 자리. */
@Composable
fun ComingSoonScreen(tab: SideTab, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(tab.label, onBack)
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("준비 중", style = SideType.Eyebrow, color = SideColors.Sun)
            Spacer(Modifier.height(16.dp))
            Text("${tab.label}은\n곧 열려요", style = SideType.Hero.copy(fontSize = 56.sp), color = SideColors.Text)
            Spacer(Modifier.height(20.dp))
            Text(
                when (tab) {
                    SideTab.MISSION -> "가는 길에 가족이 함께 해 보는 진로 미션이 열릴 거예요"
                    SideTab.RECORD -> "함께 나눈 이야기와 관심사가 여기에 쌓일 거예요"
                    else -> ""
                },
                style = SideType.Body,
                color = SideColors.TextSoft,
            )
        }
    }
}
