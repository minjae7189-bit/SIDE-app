package app.side.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 직업 탐험의 분야 하나. 분야별 직업 이야기는 다음 버전에서 음성 콘텐츠로 채운다. */
private data class CareerField(val id: String, val name: String, val jobs: List<String>)

private val fields = listOf(
    CareerField("space", "우주·항공", listOf("우주비행사", "항공기 조종사", "위성 개발자")),
    CareerField("it", "IT·AI", listOf("AI 개발자", "앱 개발자", "데이터 분석가")),
    CareerField("env", "환경·에너지", listOf("환경 공학자", "태양광 기술자", "기후 연구원")),
    CareerField("health", "보건·의료", listOf("의사", "간호사", "재활 치료사")),
    CareerField("media", "콘텐츠·미디어", listOf("영상 감독", "웹툰 작가", "게임 기획자")),
    CareerField("public", "사회·공공", listOf("소방관", "사회복지사", "외교관")),
)

/** 직업 탐험(화면 틀): 번호 붙은 분야 여섯 칸. 누르면 해 색 테두리로 고른 칸을 표시한다. */
@Composable
fun ExploreScreen(onBack: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("직업 탐험", onBack)
        Spacer(Modifier.height(20.dp))
        Text("관심 있는 분야를 골라볼까요?", style = SideType.Title.copy(fontSize = 44.sp), color = SideColors.Text)
        Spacer(Modifier.height(10.dp))
        Text("다양한 직업과 실제 이야기를 만나보세요", style = SideType.Body, color = SideColors.TextSoft)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            fields.chunked(3).forEachIndexed { row, items ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items.forEachIndexed { col, field ->
                        val i = row * 3 + col
                        FieldCard(i + 1, field, selected = i == selected, onClick = { selected = i }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("직업별 실제 이야기는 다음 버전에서 음성으로 들을 수 있어요", style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.TextFaint)
    }
}

@Composable
private fun FieldCard(number: Int, field: CareerField, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SideColors.FillSelected else SideColors.Panel,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SideColors.Sun else SideColors.LineStrong),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(twoDigits(number), style = SideType.Number.copy(fontSize = 22.sp), color = if (selected) SideColors.Sun else SideColors.Blush)
                Spacer(Modifier.weight(1f))
                // 분야 그림(side_field_*)이 있을 때만 보인다.
                Art("side_field_${field.id}", Modifier.size(56.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(field.name, style = SideType.Label.copy(fontSize = 26.sp), color = SideColors.Text, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(field.jobs.joinToString(" · "), style = SideType.LabelSmall.copy(fontSize = 16.sp, lineHeight = 23.sp), color = SideColors.TextSoft)
        }
    }
}
