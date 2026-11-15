package app.side.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.talk.FamilyMbti

/**
 * 가족 MBTI(정차 중): 어른과 아이의 MBTI를 고르면, 네 축마다 같은지 다른지와
 * "조심하세요 / 이렇게 해 보세요" 대화 팁을 보여 준다.
 */
@Composable
fun FamilyMbtiScreen(
    onBack: () -> Unit,
    parent: String = FamilyMbti.EMPTY,
    child: String = FamilyMbti.EMPTY,
    onChange: (parent: String?, child: String?) -> Unit = { _, _ -> },
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("가족 MBTI", onBack)
        Spacer(Modifier.height(12.dp))
        if (LocalPortrait.current) {
            // 세로 패널: 위에 두 사람 고르기를 나란히, 아래에 축별 안내를 전체 폭으로.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f)) { MbtiPicker("어른", parent) { onChange(it, null) } }
                    Box(Modifier.weight(1f)) { MbtiPicker("아이", child) { onChange(null, it) } }
                }
                Spacer(Modifier.height(12.dp))
                Text("이럴 땐 이렇게", style = SideType.Eyebrow, color = SideColors.Blush)
                FamilyMbti.axes.indices.forEach { i -> AxisGuide(i, parent, child) }
            }
        } else {
          Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
          ) {
            Column(
                Modifier
                    .width(380.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                MbtiPicker("어른", parent) { onChange(it, null) }
                MbtiPicker("아이", child) { onChange(null, it) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("이럴 땐 이렇게", style = SideType.Eyebrow, color = SideColors.Blush)
                FamilyMbti.axes.indices.forEach { i -> AxisGuide(i, parent, child) }
            }
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(FamilyMbti.NOTICE, style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.TextFaint)
    }
}

/** 한 사람의 MBTI 고르기: 큰 네 글자 + 축마다 두 글자 중 하나. 다시 누르면 지운다. */
@Composable
private fun MbtiPicker(who: String, code: String, onChange: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(who, style = SideType.Eyebrow, color = SideColors.Blush, modifier = Modifier.padding(bottom = 10.dp))
            Spacer(Modifier.width(16.dp))
            Text(code.replace('-', '·'), style = SideType.Title.copy(fontSize = 48.sp, letterSpacing = 0.12.sp), color = SideColors.Sun)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FamilyMbti.axes.forEachIndexed { i, axis ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(axis.first, axis.second).forEach { letter ->
                        val selected = code[i] == letter
                        Surface(
                            onClick = {
                                val next = code.toCharArray().also { it[i] = if (selected) '-' else letter }
                                onChange(String(next))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) SideColors.Sun else SideColors.Panel,
                            border = if (selected) null else BorderStroke(1.dp, SideColors.LineStrong),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$letter",
                                    style = SideType.Label.copy(fontSize = 22.sp),
                                    color = if (selected) SideColors.OnSun else SideColors.Text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisGuide(index: Int, parent: String, child: String) {
    val axis = FamilyMbti.axes[index]
    val tip = FamilyMbti.tip(index, parent, child)
    val same = tip != null && parent[index] == child[index]
    Column(
        Modifier
            .fillMaxWidth()
            .background(SideColors.Panel, RoundedCornerShape(20.dp))
            .hairline(RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${axis.first} / ${axis.second}", style = SideType.Number.copy(fontSize = 20.sp), color = SideColors.Blush)
            Spacer(Modifier.width(12.dp))
            Text(axis.name, style = SideType.Label.copy(fontSize = 19.sp), color = SideColors.Text)
            Spacer(Modifier.weight(1f))
            if (tip != null) {
                Text(
                    if (same) "같아요" else "달라요",
                    style = SideType.LabelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (same) SideColors.Blush else SideColors.Sun,
                )
            }
        }
        if (tip == null) {
            Text(
                "${axis.first} ${axis.firstMeaning}  ·  ${axis.second} ${axis.secondMeaning}",
                style = SideType.LabelSmall.copy(fontSize = 15.sp),
                color = SideColors.TextFaint,
            )
        } else {
            Text("조심하세요  ${tip.careful}", style = SideType.Body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = SideColors.TextSoft)
            Text("이렇게 해 보세요  ${tip.tryThis}", style = SideType.Body.copy(fontSize = 17.sp, lineHeight = 26.sp), color = SideColors.Text)
        }
    }
}
