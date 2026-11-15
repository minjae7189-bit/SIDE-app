package app.side.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.talk.CardKind
import app.side.talk.Deck
import app.side.talk.TalkPhase
import app.side.talk.TalkUiState

/**
 * 이야기 나누기(정차 중): 위에 카드 묶음(이야기 카드·밸런스 게임·직업 퀴즈) 고르기,
 * 왼쪽 음성 표시, 오른쪽 카드 내용, 아래 조작 버튼.
 */
@Composable
fun TalkScreen(
    ui: TalkUiState,
    actions: TalkActions,
    onBack: () -> Unit,
    onOpenFamily: () -> Unit = {},
    onOpenPlaces: () -> Unit = {},
) {
    if (LocalPortrait.current) {
        PortraitTalk(ui, actions, onBack, onOpenFamily, onOpenPlaces)
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("이야기 나누기", onBack) { Progress(ui) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeckTabs(ui.deck) { actions.startDeck(it, 0) }
            Spacer(Modifier.weight(1f))
            // 이야기를 다 마치면 가 볼 만한 곳을 먼저 권한다.
            if (ui.phase == TalkPhase.FINISHED || ui.finishedCards.isNotEmpty()) {
                SunButton("함께 가 볼 곳", onOpenPlaces, Modifier.height(48.dp), style = SideType.Button.copy(fontSize = 18.sp))
            }
            if (ui.deck == Deck.MBTI) {
                Spacer(Modifier.width(10.dp))
                LineButton("가족 MBTI 팁", onOpenFamily, Modifier.height(48.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VoiceOrb(active = ui.isRunning, size = 220.dp)
                Spacer(Modifier.height(16.dp))
                StatusLine(ui)
            }
            Spacer(Modifier.width(40.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // 배경에 깔리는 큰 번호
                Text(
                    twoDigits(ui.index + 1),
                    style = SideType.GhostNumber,
                    color = SideColors.Ghost,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (-8).dp),
                )
                Column(Modifier.align(Alignment.CenterStart)) { CardContent(ui, questionMaxLines = 3) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineButton("다시 듣기", actions.replay, Modifier.size(width = 170.dp, height = 72.dp), icon = Icons.Rounded.Replay)
            LineButton("이전", actions.previous, Modifier.size(width = 128.dp, height = 72.dp), icon = Icons.Rounded.SkipPrevious, enabled = ui.index > 0)
            RoundSunButton(
                icon = if (ui.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label = mainButtonLabel(ui),
                onClick = if (ui.isRunning) actions.pause else actions.play,
                size = 96.dp,
            )
            LineButton("다음", actions.next, Modifier.size(width = 128.dp, height = 72.dp), icon = Icons.Rounded.SkipNext, enabled = ui.index + 1 < ui.total)
            // 왼쪽 "다시 듣기"와 같은 폭의 빈자리로 가운데 버튼을 화면 가운데에 둔다.
            Spacer(Modifier.width(170.dp))
        }
    }
}

/**
 * 세로 패널: 위에 카드 묶음(가로로 넘김), 그 아래 음성 표시와 상태, 카드 내용, 맨 아래 조작 버튼.
 * 좁은 폭에서 2단으로 나누면 질문이 몇 글자씩 줄바꿈되므로 위아래로 쌓는다.
 */
@Composable
private fun PortraitTalk(ui: TalkUiState, actions: TalkActions, onBack: () -> Unit, onOpenFamily: () -> Unit, onOpenPlaces: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("이야기 나누기", onBack) { Progress(ui) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            DeckTabs(ui.deck) { actions.startDeck(it, 0) }
        }
        if (ui.deck == Deck.MBTI || ui.phase == TalkPhase.FINISHED || ui.finishedCards.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.phase == TalkPhase.FINISHED || ui.finishedCards.isNotEmpty()) {
                    SunButton("함께 가 볼 곳", onOpenPlaces, Modifier.height(48.dp), style = SideType.Button.copy(fontSize = 18.sp))
                }
                if (ui.deck == Deck.MBTI) LineButton("가족 MBTI 넣고 팁 보기", onOpenFamily, Modifier.height(48.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceOrb(active = ui.isRunning, size = 140.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) { StatusLine(ui) }
        }
        Spacer(Modifier.height(24.dp))
        // 카드가 바뀌면 스크롤 위치를 새로 시작한다(긴 카드를 내려 둔 채 다음 카드로 가도 맨 위부터 보이게).
        key(ui.deck, ui.index) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) { CardContent(ui, questionMaxLines = Int.MAX_VALUE) }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineButton("다시 듣기", actions.replay, Modifier.weight(1.3f).height(72.dp), icon = Icons.Rounded.Replay)
            LineButton("이전", actions.previous, Modifier.weight(1f).height(72.dp), icon = Icons.Rounded.SkipPrevious, enabled = ui.index > 0)
            RoundSunButton(
                icon = if (ui.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label = mainButtonLabel(ui),
                onClick = if (ui.isRunning) actions.pause else actions.play,
                size = 96.dp,
            )
            LineButton("다음", actions.next, Modifier.weight(1f).height(72.dp), icon = Icons.Rounded.SkipNext, enabled = ui.index + 1 < ui.total)
        }
    }
}

/** 카드 내용(주제 줄 + 종류별 본문). 가로·세로 배치가 같이 쓴다. */
@Composable
private fun CardContent(ui: TalkUiState, questionMaxLines: Int) {
    Text("${kindLabel(ui.card.kind)} ${twoDigits(ui.index + 1)}  ·  ${ui.card.topic}", style = SideType.Eyebrow, color = SideColors.Blush)
    Spacer(Modifier.height(14.dp))
    when (ui.card.kind) {
        CardKind.TALK -> {
            Text(ui.card.question, style = SideType.Question, color = SideColors.Text, maxLines = questionMaxLines, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(24.dp))
            StoryLine(ui.card.story)
        }
        CardKind.BALANCE -> BalanceBody(ui)
        CardKind.QUIZ -> QuizBody(ui)
    }
}

/** 카드 묶음 고르기. 누르면 그 묶음의 첫 카드부터 바로 읽는다. */
@Composable
private fun DeckTabs(current: Deck, onSelect: (Deck) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Deck.entries.forEach { deck ->
            val selected = deck == current
            Surface(
                onClick = { onSelect(deck) },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) SideColors.Sun else SideColors.Panel,
                border = if (selected) null else BorderStroke(1.dp, SideColors.LineStrong),
            ) {
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        deck.label,
                        style = SideType.LabelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) SideColors.OnSun else SideColors.Text,
                    )
                }
            }
        }
    }
}

private fun kindLabel(kind: CardKind) = when (kind) {
    CardKind.TALK -> "질문"
    CardKind.BALANCE -> "밸런스 게임"
    CardKind.QUIZ -> "퀴즈"
}

/** 왼쪽 해 색 세로선이 붙은 이야기 문단. */
@Composable
private fun StoryLine(text: String, fontSize: Int = 20) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(SideColors.Sun.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.width(20.dp))
        Text(text, style = SideType.Body.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp), color = SideColors.TextSoft)
    }
}

/** 밸런스 게임: 두 갈래를 나란히 보여 주고, 이야기 차례가 되면 고른 쪽과 어울리는 직업을 보여 준다. */
@Composable
private fun BalanceBody(ui: TalkUiState) {
    val card = ui.card
    Text(card.question, style = SideType.Question.copy(fontSize = 34.sp), color = SideColors.Text)
    Spacer(Modifier.height(18.dp))
    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        BalanceChoice("A", card.choices[0], Modifier.weight(1f), selected = ui.chosen == 0)
        Text("VS", style = SideType.Title.copy(fontSize = 28.sp), color = SideColors.Sun, modifier = Modifier.padding(horizontal = 18.dp))
        BalanceChoice("B", card.choices[1], Modifier.weight(1f), selected = ui.chosen == 1)
    }
    if (ui.revealed) {
        Spacer(Modifier.height(18.dp))
        StoryLine(card.story, fontSize = 18)
    }
}

@Composable
private fun BalanceChoice(letter: String, text: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    // 음성으로 고른 쪽은 해 색으로 표시한다.
    Column(
        modifier
            .fillMaxHeight()
            .background(if (selected) SideColors.FillSelected else SideColors.Panel, RoundedCornerShape(20.dp))
            .hairline(RoundedCornerShape(20.dp), if (selected) SideColors.Sun else SideColors.LineStrong)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text(letter, style = SideType.Number.copy(fontSize = 20.sp), color = SideColors.Blush)
        Spacer(Modifier.height(6.dp))
        Text(text, style = SideType.Quote.copy(fontSize = 24.sp), color = SideColors.Text)
    }
}

/** 직업 퀴즈: 보기 셋. 이야기 차례가 되면 정답 칸을 해 색으로 표시하고 설명을 보여 준다. */
@Composable
private fun QuizBody(ui: TalkUiState) {
    val card = ui.card
    Text(card.question, style = SideType.Question.copy(fontSize = 32.sp), color = SideColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        card.choices.forEachIndexed { i, choice ->
            val correct = ui.revealed && i == card.answer
            val picked = i == ui.chosen // 음성으로 고른 답
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (correct || picked) SideColors.FillSelected else SideColors.Panel, RoundedCornerShape(16.dp))
                    .hairline(RoundedCornerShape(16.dp), if (correct) SideColors.Sun else SideColors.LineStrong)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = SideType.Number.copy(fontSize = 22.sp), color = if (correct) SideColors.Sun else SideColors.Blush, modifier = Modifier.width(32.dp))
                Text(choice, style = SideType.Label.copy(fontSize = 20.sp), color = SideColors.Text)
                if (correct || picked) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        listOfNotNull("고른 답".takeIf { picked }, "정답".takeIf { correct }).joinToString(" · "),
                        style = SideType.LabelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SideColors.Sun,
                    )
                }
            }
        }
    }
    if (ui.revealed) {
        Spacer(Modifier.height(14.dp))
        StoryLine(card.story, fontSize = 17)
    }
}

/** 진행 막대(카드 수만큼 짧은 선) + "02 / 05". */
@Composable
private fun Progress(ui: TalkUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(ui.total) { i ->
            Box(
                Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(if (i <= ui.index) SideColors.Sun else SideColors.Line, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("${twoDigits(ui.index + 1)} / ${twoDigits(ui.total)}", style = SideType.Number.copy(fontSize = 20.sp), color = SideColors.Text)
    }
}

@Composable
private fun StatusLine(ui: TalkUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (ui.isRunning) SideColors.Sun else SideColors.TextFaint, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            when (ui.phase) {
                TalkPhase.ASKING -> "질문을 읽고 있어요"
                TalkPhase.LISTENING -> when {
                    ui.listenUnavailable -> "음성인식을 쓸 수 없어 기다려요"
                    ui.card.kind == CardKind.TALK -> "아이의 답을 듣고 있어요"
                    ui.card.kind == CardKind.BALANCE -> "어느 쪽인지 말해 보세요"
                    else -> "정답을 말해 보세요"
                } + if (ui.listenSecondsLeft > 0) " · ${ui.listenSecondsLeft}초" else ""
                TalkPhase.STORY -> when (ui.card.kind) {
                    CardKind.TALK -> "직업 이야기를 들려줘요"
                    CardKind.BALANCE -> "어울리는 직업을 들려줘요"
                    CardKind.QUIZ -> "정답을 알려 줘요"
                }
                TalkPhase.PAUSED -> "잠시 멈췄어요"
                TalkPhase.FINISHED -> "모든 질문을 마쳤어요"
                TalkPhase.READY -> "가운데 버튼을 누르면 시작해요"
            },
            style = SideType.LabelSmall,
            color = SideColors.Text,
        )
    }
    if (ui.heard.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "“${ui.heard}”",
            style = SideType.Label.copy(fontSize = 20.sp),
            color = SideColors.Sun,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ui.lastError?.let {
        Spacer(Modifier.height(6.dp))
        Text("음성을 재생하지 못했어요 ($it)", style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.ErrorSoft)
    }
}

internal fun mainButtonLabel(ui: TalkUiState): String = when {
    ui.isRunning -> "잠깐 멈추기"
    ui.phase == TalkPhase.PAUSED -> "이어서 듣기"
    ui.phase == TalkPhase.FINISHED -> "처음부터 다시"
    else -> "진로 토크 시작"
}
