package app.side.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.assessment.AssessmentUi
import app.side.assessment.Domain
import app.side.places.DEFAULT_POINT_NAME
import app.side.places.InterestProfile
import app.side.places.LocationSource
import app.side.places.PlacesActions
import app.side.places.PlacesUi
import app.side.places.Recommendation
import app.side.places.Recommender
import app.side.places.Theme
import app.side.places.ThemeLinks
import app.side.talk.Deck
import app.side.talk.SampleCards
import app.side.talk.TalkUiState
import java.time.LocalDate

/** 오늘 들은 카드, 아이가 말한 답, 진로 검사 결과, 고른 관심사로 관심 근거를 모은다. 화면과 음성 안내가 같은 것을 쓴다. */
fun buildProfile(talk: TalkUiState, assessment: AssessmentUi, chosen: Set<Theme>): InterestProfile {
    val cards = Deck.entries.flatMap { d -> SampleCards.deck(d).map { it.id to (it.topic to d.label) } }.toMap()
    val heard = talk.finishedCards.mapNotNull { id -> cards[id]?.let { (topic, deck) -> Triple(id, topic, deck) } }
    val content = assessment.content
    val interest = assessment.result?.domains?.get(Domain.INTEREST)
    val names = content?.factors?.get(Domain.INTEREST).orEmpty()
    return ThemeLinks.profile(
        chosen = chosen,
        heard = heard,
        testTop = interest?.top.orEmpty().mapNotNull { names.getOrNull(it)?.name },
        testSecond = interest?.second.orEmpty().mapNotNull { names.getOrNull(it)?.name },
        answers = talk.answers.values.toList(),
    )
}

/**
 * 가 볼 만한 곳(정차 중): 왼쪽에 오늘 나온 관심사 고르기와 근거, 오른쪽에 가까운 추천 장소 세 곳.
 * 장소마다 추천 이유, 거리, 가서 해 볼 대화, 길 안내 버튼을 보여 준다.
 */
@Composable
fun PlacesScreen(
    places: PlacesUi,
    profile: InterestProfile,
    actions: PlacesActions,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val recs = remember(places.places, places.here, profile) {
        // 오늘 매주 쉬는 곳은 빼고 고른다.
        if (places.places.isEmpty()) emptyList() else Recommender.recommend(places.places, places.here, profile, today = LocalDate.now().dayOfWeek)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("가 볼 만한 곳", onBack) {
            Text(
                when (places.locationSource) {
                    LocationSource.VEHICLE -> "차량 위치 기준"
                    LocationSource.CURRENT -> "현재 위치 기준"
                    LocationSource.LAST_KNOWN -> "최근 위치 기준"
                    LocationSource.DEFAULT -> "$DEFAULT_POINT_NAME 기준(위치를 받지 못함)"
                },
                style = SideType.LabelSmall,
                color = SideColors.TextSoft,
            )
            Spacer(Modifier.width(14.dp))
            LineButton(
                if (places.permissionGranted) "위치 다시 찾기" else "위치 사용 허용",
                if (places.permissionGranted) actions.refreshLocation else onRequestPermission,
                Modifier.height(52.dp),
                icon = Icons.Rounded.Refresh,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            InterestPanel(places, profile, actions, Modifier.width(330.dp).fillMaxHeight())
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                places.naviMessage?.let { Text(it, style = SideType.Label, color = SideColors.Sun) }
                when {
                    places.loading -> Text("장소를 불러오고 있어요", style = SideType.Body, color = SideColors.TextSoft)
                    recs.isEmpty() -> Text(places.message ?: "근처에서 어울리는 곳을 찾지 못했어요. 관심사를 더 골라 보세요.", style = SideType.Body, color = SideColors.TextSoft)
                    else -> recs.forEachIndexed { i, r -> RecommendationCard(i + 1, r, if (places.naviReady) "내비에 넣기" else "길 안내") { actions.openMap(r.place) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestPanel(places: PlacesUi, profile: InterestProfile, actions: PlacesActions, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("오늘 나온 관심사", style = SideType.Eyebrow, color = SideColors.Blush)
        Text("이야기하면서 아이가 반짝였던 주제를 골라 주세요.", style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.TextSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Theme.entries.forEach { t ->
                val selected = t in places.chosen
                Surface(
                    onClick = { actions.toggleTheme(t) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) SideColors.Sun else SideColors.Panel,
                    border = if (selected) null else BorderStroke(1.dp, SideColors.LineStrong),
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            t.label,
                            style = SideType.LabelSmall.copy(fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            color = if (selected) SideColors.OnSun else SideColors.Text,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("추천에 쓴 근거", style = SideType.Eyebrow.copy(fontSize = 13.sp), color = SideColors.TextFaint)
        val notes = profile.sources.filter { it.weight < Recommender.W_CHOSEN }.map { it.reason }.distinct()
        if (notes.isEmpty()) {
            Text("이야기 카드를 듣거나 진로 검사를 하면 그 내용도 함께 반영돼요.", style = SideType.LabelSmall.copy(fontSize = 15.sp), color = SideColors.TextFaint)
        } else {
            notes.take(6).forEach { Text("· $it", style = SideType.LabelSmall.copy(fontSize = 15.sp, lineHeight = 22.sp), color = SideColors.TextSoft) }
        }
    }
}

@Composable
private fun RecommendationCard(number: Int, r: Recommendation, buttonLabel: String, onNavigate: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SideColors.Panel, RoundedCornerShape(22.dp))
            .hairline(RoundedCornerShape(22.dp), if (number == 1) SideColors.Sun else SideColors.Line)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(twoDigits(number), style = SideType.Number.copy(fontSize = 20.sp), color = if (number == 1) SideColors.Sun else SideColors.Blush)
            Spacer(Modifier.width(12.dp))
            Text("${r.place.kind}  ·  ${r.theme.label}", style = SideType.Eyebrow.copy(fontSize = 13.sp), color = SideColors.Blush)
            Spacer(Modifier.weight(1f))
            Text(Recommender.formatKm(r.distanceKm, r.place.approx), style = SideType.Label.copy(fontSize = 20.sp), color = SideColors.Sun)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.place.name, style = SideType.Quote.copy(fontSize = 25.sp), color = SideColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.place.intro.ifBlank { r.place.detail }, style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.place.infoLine.isNotBlank()) {
                    Text(r.place.infoLine, style = SideType.LabelSmall.copy(fontSize = 14.sp), color = SideColors.Blush, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(16.dp))
            SunButton(buttonLabel, onNavigate, Modifier.size(width = 184.dp, height = 56.dp), icon = Icons.Rounded.Navigation, style = SideType.Button.copy(fontSize = 19.sp))
        }
        Text("왜 여기?  " + r.reasons.joinToString(" "), style = SideType.Body.copy(fontSize = 16.sp, lineHeight = 24.sp), color = SideColors.TextSoft)
        Text("가서 이렇게  ${r.theme.suggestion}", style = SideType.Body.copy(fontSize = 16.sp, lineHeight = 24.sp), color = SideColors.Text)
    }
}
