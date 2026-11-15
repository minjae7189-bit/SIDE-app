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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.side.explore.CareerField
import app.side.explore.CareerFields
import app.side.explore.Job
import app.side.explore.RelatedPlace
import app.side.explore.relatedPlaces
import app.side.places.DEFAULT_POINT_NAME
import app.side.places.LocationSource
import app.side.places.Place
import app.side.places.PlacesUi
import app.side.places.Recommender
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate

/**
 * 직업 탐험: 분야 여섯 칸 → 분야의 직업 목록 → 직업 상세(하는 일 + 가까운 관련 장소).
 * 뒤로 가면 한 단계씩 돌아온다. [places]는 관련 장소를 찾을 장소 자료와 현재 위치, [onNavigate]는 장소 길 안내(내비에 넣기).
 */
@Composable
fun ExploreScreen(onBack: () -> Unit, places: PlacesUi = PlacesUi(), onNavigate: (Place) -> Unit = {}, onExplored: (Job) -> Unit = {}) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var jobIndex by rememberSaveable { mutableIntStateOf(-1) }
    val field = CareerFields.all.getOrNull(selected)
    val job = field?.jobs?.getOrNull(jobIndex)
    when {
        field != null && job != null -> {
            // 살펴본 직업은 "함께 가 볼 곳" 추천의 근거가 된다.
            LaunchedEffect(job) { onExplored(job) }
            JobDetail(field, job, places, onNavigate, onBack = { jobIndex = -1 })
        }
        field != null -> JobList(field, onBack = { selected = -1 }, onSelect = { jobIndex = it })
        else -> FieldGrid(onBack, onSelect = { selected = it; jobIndex = -1 })
    }
}

@Composable
private fun FieldGrid(onBack: () -> Unit, onSelect: (Int) -> Unit) {
    val portrait = LocalPortrait.current
    val perRow = if (portrait) 2 else 3
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("직업 탐험", onBack)
        Spacer(Modifier.height(20.dp))
        Text("관심 있는 분야를 골라볼까요?", style = SideType.Title.copy(fontSize = if (portrait) 36.sp else 44.sp), color = SideColors.Text)
        Spacer(Modifier.height(10.dp))
        Text("분야를 누르면 그 분야의 직업과 하는 일을 볼 수 있어요", style = SideType.Body, color = SideColors.TextSoft)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CareerFields.all.chunked(perRow).forEachIndexed { row, items ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items.forEachIndexed { col, f ->
                        val i = row * perRow + col
                        FieldCard(i + 1, f, onClick = { onSelect(i) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldCard(number: Int, field: CareerField, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = SideColors.Panel,
        border = BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(twoDigits(number), style = SideType.Number.copy(fontSize = 22.sp), color = SideColors.Blush)
            // 분야 그림(side_field_*)을 칸의 남는 공간을 채울 만큼 크게 둔다.
            Art(
                "side_field_${field.id}",
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
            Text(field.name, style = SideType.Label.copy(fontSize = 26.sp), color = SideColors.Text, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(
                field.jobs.take(3).joinToString(" · ") { it.name } + " 외 ${field.jobs.size - 3}개",
                style = SideType.LabelSmall.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = SideColors.TextSoft,
            )
        }
    }
}

/** 분야 하나의 직업 목록. 가로 패널은 두 줄, 세로 패널은 한 줄로 쌓는다. 누르면 직업 상세로. */
@Composable
private fun JobList(field: CareerField, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    val perRow = if (LocalPortrait.current) 1 else 2
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("직업 탐험  ·  ${field.name}", onBack)
        Spacer(Modifier.height(16.dp))
        Text("${field.name} 분야의 직업 ${field.jobs.size}가지", style = SideType.Title.copy(fontSize = 34.sp), color = SideColors.Text)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            field.jobs.withIndex().chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (i, job) -> JobCard(job, onClick = { onSelect(i) }, modifier = Modifier.weight(1f)) }
                    // 마지막 줄이 한 칸이면 빈 칸으로 폭을 맞춘다.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SideColors.Panel,
        border = BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(job.name, style = SideType.Label.copy(fontSize = 22.sp), color = SideColors.Sun)
                Spacer(Modifier.height(6.dp))
                Text(job.duty, style = SideType.Body.copy(fontSize = 18.sp, lineHeight = 27.sp), color = SideColors.Text)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = SideColors.TextSoft, modifier = Modifier.size(22.dp))
        }
    }
}

/** 직업 상세: 하는 일, 그 아래 "가까운 관련 장소" 탭에 현재 위치에서 가까운 순으로 장소. */
@Composable
private fun JobDetail(field: CareerField, job: Job, places: PlacesUi, onNavigate: (Place) -> Unit, onBack: () -> Unit) {
    val related = remember(job, places.places, places.here) {
        relatedPlaces(job, places.places, places.here, limit = 5, today = LocalDate.now().dayOfWeek)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("직업 탐험  ·  ${field.name}", onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(field.name, style = SideType.Eyebrow, color = SideColors.Blush)
            Spacer(Modifier.height(10.dp))
            Text(job.name, style = SideType.Hero.copy(fontSize = 48.sp), color = SideColors.Text)
            Spacer(Modifier.height(14.dp))
            Text("하는 일", style = SideType.Eyebrow.copy(fontSize = 13.sp), color = SideColors.TextFaint)
            Spacer(Modifier.height(6.dp))
            Text(job.duty, style = SideType.Quote.copy(fontSize = 26.sp, lineHeight = 38.sp), color = SideColors.Text)
            Spacer(Modifier.height(28.dp))
            // 아래 탭: 가까운 관련 장소
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("가까운 관련 장소", style = SideType.Label.copy(fontSize = 21.sp), color = SideColors.Text)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 3.dp)
                            .background(SideColors.Sun, RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(locationLabel(places.locationSource), style = SideType.LabelSmall, color = SideColors.TextSoft)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SideColors.Line),
            )
            Spacer(Modifier.height(14.dp))
            places.naviMessage?.let {
                Text(it, style = SideType.Label, color = SideColors.Sun)
                Spacer(Modifier.height(10.dp))
            }
            when {
                places.loading -> Text("장소를 불러오고 있어요", style = SideType.Body, color = SideColors.TextSoft)
                related.isEmpty() -> Text("근처에서 관련된 장소를 찾지 못했어요.", style = SideType.Body, color = SideColors.TextSoft)
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    related.forEachIndexed { i, r ->
                        RelatedPlaceRow(i + 1, r, if (places.naviReady) "내비에 넣기" else "길 안내") { onNavigate(r.place) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RelatedPlaceRow(number: Int, r: RelatedPlace, buttonLabel: String, onNavigate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairline(RoundedCornerShape(18.dp), SideColors.LineStrong)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(twoDigits(number), style = SideType.Number.copy(fontSize = 20.sp), color = SideColors.Blush, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.place.name, style = SideType.Label.copy(fontSize = 21.sp), color = SideColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Text(Recommender.formatKm(r.km, r.place.approx), style = SideType.Label.copy(fontSize = 18.sp), color = SideColors.Sun)
            }
            Text(
                listOfNotNull(r.place.kind, if (r.direct) null else "같은 분야", r.place.infoLine.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                style = SideType.LabelSmall.copy(fontSize = 14.sp),
                color = SideColors.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        SunButton(buttonLabel, onNavigate, Modifier.size(width = 172.dp, height = 52.dp), icon = Icons.Rounded.Navigation, style = SideType.Button.copy(fontSize = 18.sp))
    }
}

private fun locationLabel(source: LocationSource): String = when (source) {
    LocationSource.VEHICLE -> "차량 위치 기준"
    LocationSource.CURRENT -> "현재 위치 기준"
    LocationSource.LAST_KNOWN -> "최근 위치 기준"
    LocationSource.DEFAULT -> "$DEFAULT_POINT_NAME 기준(위치를 받지 못함)"
}
