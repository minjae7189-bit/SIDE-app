package app.side.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.side.record.DayRecord
import app.side.record.RecordEntry
import app.side.record.RecordStore
import app.side.record.groupByDate
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 성장 기록: 이야기를 나눈 날, 진로 검사를 한 날, 내비에 넣은 장소를 날짜별로 묶어 보여 준다(최근 날짜가 위).
 * 새 기록이 생기면 바로 그날 묶음에 더해진다. 기록은 이 기기 안에만 저장된다.
 */
@Composable
fun RecordScreen(onBack: () -> Unit) {
    val store = RecordStore.get(LocalContext.current)
    val entries by store.entries.collectAsStateWithLifecycle()
    val days = remember(entries) { groupByDate(entries) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(5_000)
            confirmClear = false // 5초 안에 다시 누르지 않으면 취소
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("성장 기록", onBack) {
            if (entries.isNotEmpty()) {
                LineButton(
                    if (confirmClear) "한 번 더 누르면 모두 지워요" else "기록 지우기",
                    {
                        if (confirmClear) {
                            store.clear()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    },
                    Modifier.height(52.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (days.isEmpty()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("아직 기록이 없어요", style = SideType.Hero.copy(fontSize = 44.sp), color = SideColors.Text)
                Spacer(Modifier.height(16.dp))
                Text("이야기를 나누거나 진로 검사를 하거나 함께 가 볼 곳을 내비에 넣으면, 그날의 기록이 여기에 날짜별로 쌓여요.", style = SideType.Body, color = SideColors.TextSoft)
            }
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            days.forEach { DayCard(it) }
            Text("기록은 이 기기 안에만 저장돼요.", style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DayCard(day: DayRecord) {
    Column(
        Modifier
            .fillMaxWidth()
            .hairline(RoundedCornerShape(22.dp), SideColors.LineStrong)
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dateLabel(day.date), style = SideType.Title.copy(fontSize = 28.sp), color = SideColors.Text)
            if (day.date == LocalDate.now()) {
                Spacer(Modifier.width(10.dp))
                Text("오늘", style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.Sun)
            }
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(
                    day.talks.size.takeIf { it > 0 }?.let { "이야기 $it" },
                    day.assessments.size.takeIf { it > 0 }?.let { "진로 검사 $it" },
                    day.places.size.takeIf { it > 0 }?.let { "함께 가 볼 곳 $it" },
                ).joinToString("  ·  "),
                style = SideType.LabelSmall,
                color = SideColors.TextSoft,
            )
        }
        if (day.talks.isNotEmpty()) {
            Section("나눈 이야기") { day.talks.forEach { TalkLine(it) } }
        }
        if (day.assessments.isNotEmpty()) {
            Section("진로 검사") {
                day.assessments.forEach {
                    Text(it.title, style = SideType.Label.copy(fontSize = 20.sp), color = SideColors.Sun)
                    if (it.detail.isNotBlank()) Text(it.detail, style = SideType.Body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = SideColors.TextSoft)
                }
            }
        }
        if (day.places.isNotEmpty()) {
            Section("내비에 넣은 곳") {
                Text(day.places.joinToString("  ·  ") { it.title }, style = SideType.Body.copy(fontSize = 18.sp), color = SideColors.Text)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = SideType.Eyebrow, color = SideColors.Blush)
        content()
    }
}

@Composable
private fun TalkLine(e: RecordEntry) {
    Row {
        Text(e.at.format(TIME), style = SideType.Number.copy(fontSize = 16.sp), color = SideColors.TextFaint, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (e.tag.isNotBlank()) "[${e.tag}] " else "") + e.title,
                style = SideType.Body.copy(fontSize = 18.sp, lineHeight = 26.sp),
                color = SideColors.Text,
            )
            if (e.detail.isNotBlank()) {
                Text("“${e.detail}”", style = SideType.Label.copy(fontSize = 18.sp), color = SideColors.Sun)
            }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "11월 15일 일요일" */
private fun dateLabel(date: LocalDate): String =
    "${date.monthValue}월 ${date.dayOfMonth}일 ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}" +
        if (date.year != LocalDate.now().year) " (${date.year})" else ""
