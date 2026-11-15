package app.side.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.side.talk.Deck
import app.side.talk.TalkUiState

/** 홈: 큰 바탕체 인사, 시작 방법, 오른쪽 그림, 아래 번호 붙은 버튼 네 개. */
@Composable
fun HomeScreen(
    ui: TalkUiState,
    onStartTalk: () -> Unit,
    onOpen: (SideTab) -> Unit,
    onStartDeck: (Deck, Int) -> Unit = { _, _ -> },
    progress: JourneyProgress = JourneyProgress(),
) {
    if (LocalPortrait.current) {
        PortraitHome(ui, onStartTalk, onOpen, onStartDeck, progress)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("오늘의 진로 대화", style = SideType.Eyebrow, color = SideColors.Blush)
                Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        append("오늘, 어떤 ")
                        withStyle(SpanStyle(color = SideColors.Sun)) { append("이야기") }
                        append("를\n나눠볼까요?")
                    },
                    style = SideType.Hero.copy(fontSize = 46.sp),
                    color = SideColors.Text,
                )
                // 왼쪽 내용은 지평선(땅) 위에서 끝나야 아래 버튼 줄과 겹치지 않는다. 제목 두 줄 + 시작 방법 네 줄로 맞춘다.
                Spacer(Modifier.height(24.dp))
                StartOptions(onStartDeck)
            }
            Art(
                "side_home_hero",
                Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .hairline(RoundedCornerShape(28.dp)),
            ) {
                HorizonRoad(Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(20.dp))
        JourneyHint(progress)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HomeTile(1, if (ui.isRunning) "이어서 대화" else "이야기 나누기", onStartTalk, Modifier.weight(1f), done = progress.talk)
            HomeTile(2, "진로 검사", { onOpen(SideTab.TEST) }, Modifier.weight(1f), done = progress.test)
            HomeTile(3, "직업 탐험", { onOpen(SideTab.EXPLORE) }, Modifier.weight(1f), done = progress.explore)
            HomeTile(4, "함께 가 볼 곳", { onOpen(SideTab.PLACES) }, Modifier.weight(1.4f), primary = true, sub = progress.label)
        }
    }
}

/** 세로 패널: 그림을 위에 넓게 두고, 그 아래 제목·시작 방법, 맨 아래 2×2 버튼. */
@Composable
private fun PortraitHome(
    ui: TalkUiState,
    onStartTalk: () -> Unit,
    onOpen: (SideTab) -> Unit,
    onStartDeck: (Deck, Int) -> Unit,
    progress: JourneyProgress,
) {
    Column(Modifier.fillMaxSize()) {
        // 그림(380×560 세로형) 비율대로 칸을 잡아 좌우에 빈 테두리가 생기지 않게 한다.
        Art(
            "side_home_hero",
            Modifier
                .weight(1f)
                .aspectRatio(380f / 560f, matchHeightConstraintsFirst = true)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(28.dp))
                .hairline(RoundedCornerShape(28.dp)),
        ) {
            HorizonRoad(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Text("오늘의 진로 대화", style = SideType.Eyebrow, color = SideColors.Blush)
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                append("오늘, 어떤 ")
                withStyle(SpanStyle(color = SideColors.Sun)) { append("이야기") }
                append("를\n나눠볼까요?")
            },
            style = SideType.Hero.copy(fontSize = 42.sp),
            color = SideColors.Text,
        )
        Spacer(Modifier.height(20.dp))
        StartOptions(onStartDeck)
        Spacer(Modifier.height(20.dp))
        JourneyHint(progress)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.height(88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile(1, if (ui.isRunning) "이어서 대화" else "이야기 나누기", onStartTalk, Modifier.weight(1f), done = progress.talk)
                HomeTile(2, "진로 검사", { onOpen(SideTab.TEST) }, Modifier.weight(1f), done = progress.test)
                HomeTile(3, "직업 탐험", { onOpen(SideTab.EXPLORE) }, Modifier.weight(1f), done = progress.explore)
            }
            // 마지막 단계는 한 줄을 다 써서 강조한다.
            Row(Modifier.height(96.dp)) {
                HomeTile(4, "함께 가 볼 곳", { onOpen(SideTab.PLACES) }, Modifier.weight(1f), primary = true, sub = progress.label)
            }
        }
    }
}

/**
 * 시작 방법 세 가지. 열린 질문이 어려운 가족은 밸런스 게임이나 퀴즈로 가볍게 시작할 수 있다.
 * 누르면 그 묶음의 첫 카드부터 바로 읽는다.
 */
@Composable
private fun StartOptions(onStartDeck: (Deck, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("이렇게 시작해 보세요", style = SideType.Eyebrow.copy(fontSize = 13.sp), color = SideColors.TextFaint)
        Deck.entries.forEach { deck ->
            Surface(
                onClick = { onStartDeck(deck, 0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = SideColors.Panel,
                border = BorderStroke(1.dp, SideColors.LineStrong),
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(deck.label, style = SideType.Eyebrow.copy(fontSize = 14.sp, letterSpacing = 0.08.em), color = SideColors.Blush, modifier = Modifier.width(112.dp))
                    Text(deck.hint, style = SideType.Quote.copy(fontSize = 19.sp), color = SideColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = SideColors.TextSoft, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * 여정 진행 상황: 1~3단계를 했는지. 한 단계만 해도 "함께 가 볼 곳"을 추천하지만,
 * 단계를 더 할수록 추천 근거가 쌓여 구체적이 된다.
 */
data class JourneyProgress(val talk: Boolean = false, val test: Boolean = false, val explore: Boolean = false) {
    val done: Int get() = listOf(talk, test, explore).count { it }
    val label: String get() = if (done == 0) "하나만 해도 추천해요" else "추천 근거 $done / 3단계"
}

/** 타일 위 한 줄 안내: 단계를 더 할수록 가 볼 곳이 구체적이 된다는 것. */
@Composable
private fun JourneyHint(progress: JourneyProgress) {
    Text(
        when (progress.done) {
            0 -> "어느 단계든 하나만 해도 함께 가 볼 곳을 추천해요. 단계를 더 할수록 추천이 구체적이 돼요."
            3 -> "세 단계를 모두 했어요. 지금 추천이 가장 우리 가족에게 맞춰져 있어요."
            else -> "${progress.done}단계를 했어요. 남은 단계를 하면 함께 가 볼 곳이 더 구체적이 돼요."
        },
        style = SideType.LabelSmall.copy(fontSize = 16.sp),
        color = SideColors.TextSoft,
    )
}

@Composable
private fun HomeTile(
    number: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    done: Boolean = false,
    sub: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = if (primary) SideColors.Sun else SideColors.Panel,
        border = if (primary) null else BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        val ink = if (primary) SideColors.OnSun else SideColors.Text
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(twoDigits(number), style = SideType.Number.copy(fontSize = 18.sp), color = if (primary) SideColors.OnSun else SideColors.Blush)
                    if (done) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Check, contentDescription = "했어요", tint = SideColors.Sun, modifier = Modifier.size(20.dp))
                    }
                    if (primary) {
                        Spacer(Modifier.width(8.dp))
                        Text("마지막 단계", style = SideType.LabelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = SideColors.OnSun)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = SideType.Label, color = ink, maxLines = 1)
                if (sub != null) Text(sub, style = SideType.LabelSmall.copy(fontSize = 14.sp), color = ink, maxLines = 1)
            }
            Icon(
                if (primary) Icons.Rounded.Navigation else Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(if (primary) 34.dp else 24.dp),
            )
        }
    }
}
