package app.side.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.assessment.AssessmentActions
import app.side.assessment.AssessmentUi
import app.side.assessment.CareerTestContent
import app.side.assessment.CareerTestReport
import app.side.assessment.CareerTestResult
import app.side.assessment.Consistency
import app.side.assessment.Domain
import app.side.assessment.Level
import app.side.assessment.Profile
import app.side.assessment.RIASEC
import app.side.assessment.Stage
import kotlin.math.cos
import kotlin.math.sin

/** 진로 검사: 소개 → 기본 정보 → 문항 → 결과. 정차 중에만 보인다. */
@Composable
fun AssessmentScreen(ui: AssessmentUi, actions: AssessmentActions, onBack: () -> Unit) {
    val content = ui.content
    if (content == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("진로 검사", onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(ui.loadError ?: "검사를 준비하고 있어요", style = SideType.Body, color = SideColors.TextSoft)
            }
        }
        return
    }
    when (ui.stage) {
        Stage.INTRO -> IntroStage(content, ui, actions, onBack)
        Stage.PROFILE -> ProfileStage(content, ui, actions)
        Stage.QUESTIONS -> QuestionStage(content, ui, actions)
        Stage.RESULT -> ui.result?.let { ResultStage(content, ui, it, actions) } ?: IntroStage(content, ui, actions, onBack)
    }
}

// ───────────────────────── 소개 ─────────────────────────

@Composable
private fun IntroStage(content: CareerTestContent, ui: AssessmentUi, actions: AssessmentActions, onBack: () -> Unit) {
    val portrait = LocalPortrait.current
    // 세로 패널에서는 두 칸을 위아래로 쌓고 넘쳐도 볼 수 있게 스크롤한다.
    val cellHeight = if (portrait) Modifier else Modifier.fillMaxHeight()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("진로 검사", onBack)
        SplitOrStack(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (portrait) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = 12.dp, bottom = if (portrait) 16.dp else 0.dp),
            spacing = if (portrait) 32.dp else 48.dp,
        ) { cell ->
            Column(
                cell(1.1f).then(cellHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("진로 검사  ·  ${content.source}", style = SideType.Eyebrow, color = SideColors.Blush)
                Spacer(Modifier.height(16.dp))
                Text("흥미, 적성, 가치관을\n하나의 검사로", style = SideType.Hero.copy(fontSize = if (portrait) 44.sp else 52.sp), color = SideColors.Text)
                Spacer(Modifier.height(20.dp))
                Text(content.introSummary, style = SideType.Body, color = SideColors.TextSoft)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Fact("${content.questions.size}", "문항")
                    Fact("10", "분 안팎")
                    Fact("6", "단계로 답해요")
                }
            }
            Column(
                cell(0.9f).then(cellHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                content.introNotes.forEach { note ->
                    Text(note.title.trimEnd('.'), style = SideType.Label, color = SideColors.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(note.desc, style = SideType.Body.copy(fontSize = 18.sp, lineHeight = 29.sp), color = SideColors.TextSoft)
                    Spacer(Modifier.height(28.dp))
                }
                Text(
                    "화면으로 하는 검사는 차가 서 있을 때만 할 수 있어요. 음성으로 답하면 출발해도 이어서 할 수 있어요.",
                    style = SideType.LabelSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = SideColors.TextFaint,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                ui.result != null -> {
                    SunButton("결과 보기", actions.showResult, Modifier.size(width = 260.dp, height = 88.dp), icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    LineButton("다시 검사하기", actions.restart, Modifier.size(width = 200.dp, height = 88.dp), icon = Icons.Rounded.Refresh)
                    ui.finishedAt?.let { Text("마지막 검사 $it", style = SideType.LabelSmall, color = SideColors.TextFaint) }
                }
                ui.inProgress -> {
                    SunButton("이어서 하기", actions.start, Modifier.size(width = 260.dp, height = 88.dp), icon = Icons.AutoMirrored.Rounded.ArrowForward)
                    if (ui.profile.complete) {
                        LineButton("음성으로 이어서", actions.toggleVoice, Modifier.size(width = 220.dp, height = 88.dp), icon = Icons.Rounded.Mic)
                    }
                    LineButton("처음부터", actions.restart, Modifier.size(width = 170.dp, height = 88.dp), icon = Icons.Rounded.Refresh)
                    Text("${ui.answers.size} / ${ui.total} 문항 답함", style = SideType.LabelSmall, color = SideColors.TextFaint)
                }
                else -> SunButton("검사 시작", actions.start, Modifier.size(width = 260.dp, height = 88.dp), icon = Icons.AutoMirrored.Rounded.ArrowForward)
            }
        }
    }
}

@Composable
private fun Fact(number: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(number, style = SideType.Title.copy(fontSize = 40.sp), color = SideColors.Sun)
        Spacer(Modifier.width(6.dp))
        Text(label, style = SideType.LabelSmall, color = SideColors.TextSoft, modifier = Modifier.padding(bottom = 6.dp))
    }
}

// ───────────────────────── 기본 정보 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileStage(content: CareerTestContent, ui: AssessmentUi, actions: AssessmentActions) {
    val p = ui.profile
    val set = actions.updateProfile
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("기본 정보  ${ui.profilePage + 1} / 2", actions.profileBack)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (ui.profilePage == 0) {
                Section("학교") {
                    Level.entries.forEach { l ->
                        // 학교급이 바뀌면 학년·과목 보기가 달라지므로 다시 고르게 한다.
                        Choice(content.levelLabels[l].orEmpty(), p.level == l) {
                            if (p.level != l) set(p.copy(level = l, grade = -1, subjects = emptyList()))
                        }
                    }
                }
                p.level?.let { content.levelNotes[it] }?.let { note ->
                    Text(note, style = SideType.LabelSmall.copy(fontSize = 16.sp), color = SideColors.TextSoft)
                }
                p.level?.let { level ->
                    Section("학년") {
                        content.grades[level].orEmpty().forEachIndexed { i, g -> Choice(g, p.grade == i) { set(p.copy(grade = i)) } }
                    }
                }
                Section("성별") {
                    content.genders.forEachIndexed { i, g -> Choice(g, p.gender == i) { set(p.copy(gender = i)) } }
                }
                Section("지역") {
                    content.regions.forEachIndexed { i, r -> Choice(r, p.region == i, small = true) { set(p.copy(region = i)) } }
                }
            } else {
                Text("내가 잘한다고 생각하는 과목을 순서대로 세 개 골라 주세요", style = SideType.Question.copy(fontSize = 32.sp), color = SideColors.Text)
                Text("고른 순서가 1, 2, 3순위가 돼요. 다시 누르면 빠져요.", style = SideType.Body, color = SideColors.TextSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content.subjectsFor(p.level).forEachIndexed { i, s ->
                        val rank = p.subjects.indexOf(i)
                        Choice(if (rank >= 0) "${rank + 1}  $s" else s, rank >= 0) {
                            val next = when {
                                rank >= 0 -> p.subjects - i
                                p.subjects.size < 3 -> p.subjects + i
                                else -> p.subjects
                            }
                            set(p.copy(subjects = next))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val ready = if (ui.profilePage == 0) p.level != null && p.grade >= 0 && p.gender >= 0 && p.region >= 0 else p.complete
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunButton(
                if (ui.profilePage == 0) "다음" else "문항 시작",
                { if (ready) actions.profileNext() },
                Modifier
                    .size(width = 240.dp, height = 80.dp)
                    .alpha(if (ready) 1f else 0.4f),
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
            )
            Spacer(Modifier.width(16.dp))
            if (!ready) Text("모두 고르면 넘어갈 수 있어요", style = SideType.LabelSmall, color = SideColors.TextFaint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = SideType.Eyebrow, color = SideColors.Blush)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(if (small) 56.dp else 64.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SideColors.Sun else SideColors.Panel,
        border = if (selected) null else BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        Box(Modifier.padding(horizontal = if (small) 16.dp else 24.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = SideType.Label.copy(fontSize = if (small) 17.sp else 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) SideColors.OnSun else SideColors.Text,
            )
        }
    }
}

// ───────────────────────── 문항 ─────────────────────────

@Composable
private fun QuestionStage(content: CareerTestContent, ui: AssessmentUi, actions: AssessmentActions) {
    val level = ui.profile.level ?: Level.MIDDLE
    val q = content.questions[ui.index]
    val factor = content.factor(q)
    val inDomain = content.questions.filter { it.domain == q.domain }
    val numberInDomain = inDomain.indexOf(q) + 1
    val chosen = ui.answers[q]
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${q.domain.label}  ${twoDigits(numberInDomain)} / ${twoDigits(inDomain.size)}", actions.previous) {
            Text("전체 ${ui.answers.size} / ${ui.total}", style = SideType.Number.copy(fontSize = 20.sp), color = SideColors.Text)
        }
        Spacer(Modifier.height(10.dp))
        // 전체 진행 막대
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SideColors.Line, RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ui.answers.size / ui.total.toFloat().coerceAtLeast(1f))
                    .height(4.dp)
                    .background(SideColors.Sun, RoundedCornerShape(2.dp)),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(factor.name, style = SideType.Eyebrow, color = SideColors.Blush)
            Spacer(Modifier.height(16.dp))
            Text(content.text(q, level), style = SideType.Question.copy(fontSize = 36.sp, lineHeight = 48.sp), color = SideColors.Text)
        }
        // 세로 분할 화면에서는 3개씩 두 줄로 놓는다(초등용 응답 문구가 길어 한 줄 6칸에서는 잘린다).
        val options = content.options(q, level)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.withIndex().chunked(if (LocalPortrait.current) 3 else 6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (i, label) ->
                        AnswerButton(i + 1, label, selected = chosen == i + 1, modifier = Modifier.weight(1f)) { actions.answer(i + 1) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineButton("이전 문항", actions.previous, Modifier.size(width = 150.dp, height = 64.dp), icon = Icons.AutoMirrored.Rounded.ArrowBack)
            Spacer(Modifier.width(12.dp))
            LineButton("나중에 이어서", actions.exit, Modifier.size(width = 170.dp, height = 64.dp))
            Spacer(Modifier.width(12.dp))
            // 음성 검사: 문항을 읽어 주고 대답을 들어 답한다(화면을 보지 않고도 할 수 있게).
            if (ui.voiceOn) {
                LineButton("음성 끄기", actions.toggleVoice, Modifier.size(width = 150.dp, height = 64.dp), icon = Icons.Rounded.MicOff)
            } else {
                SunButton("음성으로 답하기", actions.toggleVoice, Modifier.size(width = 236.dp, height = 64.dp), icon = Icons.Rounded.Mic, style = SideType.Button.copy(fontSize = 19.sp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    ui.voiceStatus.ifBlank { if (ui.voiceOn) "음성으로 진행 중" else "고르면 바로 다음 문항으로 넘어가요" },
                    style = SideType.LabelSmall.copy(fontSize = 15.sp),
                    color = if (ui.voiceOn) SideColors.Sun else SideColors.TextFaint,
                )
                if (ui.heard.isNotBlank()) Text("“${ui.heard}”", style = SideType.Label.copy(fontSize = 17.sp), color = SideColors.Sun, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AnswerButton(value: Int, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SideColors.Sun else SideColors.Panel,
        border = if (selected) null else BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$value", style = SideType.Title.copy(fontSize = 36.sp), color = if (selected) SideColors.OnSun else SideColors.Sun)
            Text(
                label,
                style = SideType.LabelSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),
                color = if (selected) SideColors.OnSun else SideColors.TextSoft,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ───────────────────────── 결과 ─────────────────────────

@Composable
private fun ResultStage(content: CareerTestContent, ui: AssessmentUi, result: CareerTestResult, actions: AssessmentActions) {
    val type = content.types[result.code]
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("진로 검사 결과", actions.exit) {
            LineButton("다시 검사하기", actions.restart, Modifier.size(width = 190.dp, height = 56.dp), icon = Icons.Rounded.Refresh)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // 1. 최종 유형 + 육각형
            SplitOrStack(spacing = 40.dp, verticalAlignment = Alignment.CenterVertically) { cell ->
                Column(cell(1f)) {
                    Text("나의 진로 유형", style = SideType.Eyebrow, color = SideColors.Blush)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(result.code, style = SideType.Hero.copy(fontSize = 88.sp, lineHeight = 88.sp), color = SideColors.Sun)
                        Spacer(Modifier.width(20.dp))
                        Text(type?.name.orEmpty(), style = SideType.Title.copy(fontSize = 40.sp), color = SideColors.Text, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(type?.feature.orEmpty(), style = SideType.Quote, color = SideColors.Text)
                    Spacer(Modifier.height(16.dp))
                    Text(profileLine(content, ui), style = SideType.LabelSmall, color = SideColors.TextFaint)
                }
                Box(if (LocalPortrait.current) Modifier.fillMaxWidth() else Modifier, contentAlignment = Alignment.Center) {
                    Hexagon(result, Modifier.size(300.dp))
                }
            }

            // 2~4. 흥미·적성·가치관
            Domain.entries.forEach { d -> DomainSection(content, ui, result, d) }

            // 5. 두 유형 설명과 추천 직업
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionTitle("최종 유형", "${result.code} ${type?.name.orEmpty()}")
                SplitOrStack(spacing = if (LocalPortrait.current) 20.dp else 32.dp) { cell ->
                    result.code.forEach { c ->
                        content.riasec[c]?.let { info ->
                            Column(cell(1f)) {
                                Text("$c  ${info.name}", style = SideType.Label, color = SideColors.Sun)
                                Spacer(Modifier.height(8.dp))
                                Text(info.easy.ifEmpty { info.core }, style = SideType.Body.copy(fontSize = 18.sp, lineHeight = 29.sp), color = SideColors.TextSoft)
                                Spacer(Modifier.height(8.dp))
                                Text(info.tags.joinToString("  "), style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.Blush)
                            }
                        }
                    }
                }
                type?.let { t ->
                    Text("추천 직업", style = SideType.Eyebrow, color = SideColors.Blush, modifier = Modifier.padding(top = 8.dp))
                    JobChips(t.jobs)
                }
            }

            // 6. 일관성·변별성
            SplitOrStack(spacing = 20.dp) { cell ->
                val c = when (result.consistency) {
                    Consistency.HIGH -> content.consistencyHigh
                    Consistency.LOW -> content.consistencyLow
                    Consistency.MIDDLE -> null
                }
                InfoCard(
                    eyebrow = "일관성",
                    title = c?.title ?: "두 유형이 육각형에서 한 칸 떨어져 있어요",
                    body = c?.desc ?: content.consistencyIntro,
                    modifier = cell(1f),
                )
                val dText = content.differentiation[result.differentiation]
                InfoCard(eyebrow = "변별성", title = dText?.title.orEmpty(), body = dText?.desc.orEmpty(), modifier = cell(1f))
            }
            Text(
                "검사 결과는 지금의 나를 보여 줘요. 관심사나 중요하게 여기는 가치가 바뀌면 결과도 자연스럽게 달라질 수 있어요.",
                style = SideType.LabelSmall.copy(fontSize = 15.sp),
                color = SideColors.TextFaint,
            )
        }
    }
}

private fun profileLine(content: CareerTestContent, ui: AssessmentUi): String {
    val p = ui.profile
    val level = p.level ?: return ""
    val parts = listOfNotNull(
        content.grades[level]?.getOrNull(p.grade),
        content.regions.getOrNull(p.region),
        ui.finishedAt?.let { "검사일 $it" },
        p.subjects.takeIf { it.isNotEmpty() }?.let { s -> "잘하는 과목 " + s.mapIndexed { i, x -> "${i + 1} ${content.subjectsFor(p.level).getOrElse(x) { "" }}" }.joinToString("  ") },
    )
    return parts.joinToString("  ·  ")
}

@Composable
private fun SectionTitle(eyebrow: String, title: String) {
    Column {
        Text(eyebrow, style = SideType.Eyebrow, color = SideColors.Blush)
        Spacer(Modifier.height(8.dp))
        Text(title, style = SideType.Title.copy(fontSize = 34.sp), color = SideColors.Text)
    }
}

@Composable
private fun DomainSection(content: CareerTestContent, ui: AssessmentUi, result: CareerTestResult, d: Domain) {
    val s = result.domains.getValue(d)
    val factors = content.factors.getValue(d)
    val max = if (d == Domain.VALUES) 12 else 6
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(
            d.label,
            when (d) {
                Domain.INTEREST -> "어떤 활동에 끌리나요"
                Domain.APTITUDE -> "무엇을 잘한다고 느끼나요"
                Domain.VALUES -> "일에서 무엇을 중요하게 여기나요"
            },
        )
        SplitOrStack(spacing = if (LocalPortrait.current) 6.dp else 40.dp) { cell ->
            // 막대: 하위요인 전부, 가로 패널은 두 줄로(세로 패널은 한 줄로 이어서)
            val half = (factors.size + 1) / 2
            listOf(factors.indices.take(half), factors.indices.drop(half)).forEach { col ->
                Column(cell(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    col.forEach { i -> ScoreBar(factors[i].name, s.scores[i], max, highlight = i in s.top) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(CareerTestReport.topSentence(content, d, s), style = SideType.Body, color = SideColors.Text)
            CareerTestReport.secondSentence(content, d, s)?.let { Text(it, style = SideType.Body, color = SideColors.TextSoft) }
            CareerTestReport.tendencySentence(d, s.tendency)?.let { Text("경향성  $it", style = SideType.Body, color = SideColors.TextSoft) }
        }
        if (d == Domain.APTITUDE && ui.profile.subjects.isNotEmpty()) {
            Text(
                "내가 잘한다고 생각하는 과목  " + ui.profile.subjects.mapIndexed { i, x -> "${i + 1} ${content.subjectsFor(ui.profile.level).getOrElse(x) { "" }}" }.joinToString("   "),
                style = SideType.Label.copy(fontSize = 18.sp),
                color = SideColors.Text,
            )
        }
        // 최상위 하위요인 설명
        Column(
            Modifier
                .fillMaxWidth()
                .hairline(RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("가장 높은 ${d.label}", style = SideType.Eyebrow.copy(fontSize = 13.sp), color = SideColors.TextFaint)
            s.top.forEach { i ->
                Text(factors[i].name, style = SideType.Label.copy(fontSize = 19.sp), color = SideColors.Sun)
                Text(factors[i].desc, style = SideType.Body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = SideColors.TextSoft)
            }
        }
    }
}

@Composable
private fun ScoreBar(name: String, score: Int, max: Int, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = SideType.LabelSmall.copy(fontSize = 15.sp),
            color = if (highlight) SideColors.Text else SideColors.TextSoft,
            maxLines = 1,
            modifier = Modifier.width(170.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .background(SideColors.Line, RoundedCornerShape(5.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(score / max.toFloat())
                    .height(10.dp)
                    .background(if (highlight) SideColors.Sun else SideColors.Blush.copy(alpha = 0.6f), RoundedCornerShape(5.dp)),
            )
        }
        Text("$score", style = SideType.Number.copy(fontSize = 16.sp), color = SideColors.Text, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobChips(jobs: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        jobs.forEach { j ->
            Text(
                j,
                style = SideType.LabelSmall.copy(fontSize = 16.sp),
                color = SideColors.Text,
                modifier = Modifier
                    .hairline(RoundedCornerShape(12.dp), SideColors.LineStrong)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(eyebrow: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .hairline(RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(eyebrow, style = SideType.Eyebrow, color = SideColors.Blush)
        Text(title, style = SideType.Label, color = SideColors.Text)
        Text(body, style = SideType.Body.copy(fontSize = 17.sp, lineHeight = 27.sp), color = SideColors.TextSoft)
    }
}

/** RIASEC 육각형: 유형별 평균 응답(1~6)을 꼭짓점 거리로 그린다. 상위 두 유형은 해 색. */
@Composable
private fun Hexagon(result: CareerTestResult, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2 * 0.72f
            fun point(i: Int, k: Float) = Offset(
                c.x + r * k * cos(Math.toRadians(-90.0 + 60 * i)).toFloat(),
                c.y + r * k * sin(Math.toRadians(-90.0 + 60 * i)).toFloat(),
            )
            listOf(1f, 2 / 3f, 1 / 3f).forEach { k ->
                val ring = Path().apply {
                    RIASEC.indices.forEach { i -> if (i == 0) moveTo(point(i, k).x, point(i, k).y) else lineTo(point(i, k).x, point(i, k).y) }
                    close()
                }
                drawPath(ring, SideColors.Line, style = Stroke(1.dp.toPx()))
            }
            RIASEC.indices.forEach { i -> drawLine(SideColors.Line, c, point(i, 1f), 1.dp.toPx()) }
            val shape = Path().apply {
                RIASEC.forEachIndexed { i, code ->
                    val k = ((result.average.getValue(code) - 1f) / 5f).coerceIn(0.04f, 1f)
                    val p = point(i, k)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(shape, SideColors.Sun.copy(alpha = 0.22f))
            drawPath(shape, SideColors.Sun, style = Stroke(2.dp.toPx()))
        }
        // 꼭짓점 글자
        RIASEC.forEachIndexed { i, code ->
            val angle = Math.toRadians(-90.0 + 60 * i)
            val dist = 128f
            Text(
                "$code",
                style = SideType.Label.copy(fontSize = 20.sp),
                color = if (code in result.code) SideColors.Sun else SideColors.TextSoft,
                modifier = Modifier.padding(
                    start = (if (cos(angle) > 0) (dist * cos(angle)).toFloat() * 2 else 0f).dp,
                    end = (if (cos(angle) < 0) (-dist * cos(angle)).toFloat() * 2 else 0f).dp,
                    top = (if (sin(angle) > 0) (dist * sin(angle)).toFloat() * 2 else 0f).dp,
                    bottom = (if (sin(angle) < 0) (-dist * sin(angle)).toFloat() * 2 else 0f).dp,
                ),
            )
        }
    }
}
