package app.side.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 코랄 노을 배경.
 * [horizon]이 true면(홈·주행 화면) 지평선과 반쯤 떠오른 해, 땅을 그린다. 아래 버튼 줄은 땅 위에 놓인다.
 * false면(나머지 화면) 지평선 없이 하늘만 그린다. 내용이 화면 아래까지 차는 화면에서 선과 해가 내용과 겹치지 않게 하기 위해서다.
 * 움직이지 않는다.
 */
@Composable
fun SideBackground(horizon: Boolean = true) {
    Canvas(
        Modifier
            .fillMaxSize()
            .background(if (horizon) SideColors.Sky else SideColors.SkyPlain),
    ) {
        val w = size.width
        val h = size.height
        if (!horizon) {
            stars.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.45f), 1.4.dp.toPx(), Offset(x * w, y * h * 0.7f)) }
            return@Canvas
        }
        // 지평선은 화면 아래 버튼 줄보다 위에 둔다. 버튼 줄(아래쪽 약 20%)은 통째로 땅 위에 놓여 선·해와 겹치지 않는다.
        val line = h * HORIZON
        val sun = Offset(w * 0.74f, line)
        drawCircle(Brush.radialGradient(listOf(SideColors.SunGlow, Color.Transparent), center = sun, radius = h * 0.6f), radius = h * 0.6f, center = sun)
        drawCircle(SideColors.Sun.copy(alpha = 0.18f), radius = h * 0.12f, center = sun)
        drawCircle(SideColors.Sun.copy(alpha = 0.3f), radius = h * 0.12f, center = sun, style = Stroke(1.dp.toPx()))
        // 지평선 아래는 불투명하게 덮어 해가 반원으로만 보이게 한다.
        drawRect(SideColors.Ground, topLeft = Offset(0f, line), size = Size(w, h - line))
        drawLine(SideColors.LineStrong, Offset(0f, line), Offset(w, line), strokeWidth = 1.dp.toPx())
        stars.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.5f), 1.4.dp.toPx(), Offset(x * w, y * line)) }
    }
}

/** 지평선 높이(화면 높이 비율). 주행 화면도 이 위쪽에만 내용을 둔다. */
const val HORIZON = 0.78f

private val stars = listOf(
    0.08f to 0.10f, 0.21f to 0.32f, 0.36f to 0.07f, 0.52f to 0.19f, 0.64f to 0.05f,
    0.81f to 0.15f, 0.93f to 0.30f, 0.44f to 0.44f, 0.29f to 0.62f, 0.88f to 0.55f,
)

/** 가는 테두리. 상자 대신 이 선으로 영역을 나눈다. */
fun Modifier.hairline(shape: Shape, color: Color = SideColors.Line): Modifier = this.border(1.dp, color, shape)

/**
 * 그림 자리. `res/drawable-nodpi/<name>.png`(또는 .webp)가 있으면 그 그림을, 없으면 [fallback]을 그린다.
 * 그림 파일만 넣으면 코드 수정 없이 바뀌도록 이름으로 찾는다.
 */
@SuppressLint("DiscouragedApi", "LocalContextGetResourceValueCall")
@Composable
fun Art(name: String, modifier: Modifier = Modifier, fallback: @Composable BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    val id = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (id != 0) {
        Image(painterResource(id), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center, content = fallback)
    }
}

/** 홈 그림이 없을 때 쓰는 선 그림: 지평선으로 뻗은 길과 떠오르는 해. */
@Composable
fun HorizonRoad(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val vy = h * 0.46f // 지평선 높이
        val vx = w * 0.5f
        val line = SideColors.LineStrong
        val stroke = 1.5.dp.toPx()
        // 해: 동심원 세 겹
        listOf(0.30f, 0.22f, 0.14f).forEachIndexed { i, k ->
            drawCircle(
                if (i == 2) SideColors.Sun else SideColors.Sun.copy(alpha = 0.4f - i * 0.1f),
                radius = h * k,
                center = Offset(vx, vy),
                style = if (i == 2) androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke),
            )
        }
        // 지평선 아래를 가려 해가 반쯤 떠오른 모양으로
        drawRect(SideColors.Ground, topLeft = Offset(0f, vy), size = Size(w, h - vy))
        drawLine(line, Offset(0f, vy), Offset(w, vy), stroke)
        // 길 가장자리 두 줄과 가운데 점선
        drawLine(Color.White.copy(alpha = 0.7f), Offset(vx - w * 0.02f, vy), Offset(w * 0.12f, h), stroke * 1.6f)
        drawLine(Color.White.copy(alpha = 0.7f), Offset(vx + w * 0.02f, vy), Offset(w * 0.88f, h), stroke * 1.6f)
        val dashes = 7
        for (i in 0 until dashes) {
            val t0 = (i + 0.2f) / dashes
            val t1 = (i + 0.65f) / dashes
            // 멀수록 짧아 보이도록 제곱으로 간격을 준다.
            val y0 = vy + (h - vy) * t0 * t0
            val y1 = vy + (h - vy) * t1 * t1
            drawLine(SideColors.Sun.copy(alpha = 0.9f), Offset(vx, y0), Offset(vx, y1), stroke * (1f + 2.5f * t1))
        }
        // 지평선 위 먼 산 윤곽
        val hills = Path().apply {
            moveTo(0f, vy)
            cubicTo(w * 0.12f, vy - h * 0.10f, w * 0.22f, vy - h * 0.04f, w * 0.32f, vy - h * 0.07f)
            cubicTo(w * 0.38f, vy - h * 0.09f, w * 0.42f, vy - h * 0.02f, w * 0.45f, vy)
            moveTo(w * 0.58f, vy)
            cubicTo(w * 0.66f, vy - h * 0.08f, w * 0.78f, vy - h * 0.13f, w * 0.88f, vy - h * 0.06f)
            cubicTo(w * 0.94f, vy - h * 0.03f, w * 0.97f, vy - h * 0.05f, w, vy - h * 0.04f)
        }
        drawPath(hills, line, style = Stroke(stroke))
    }
}

/**
 * 음성 표시: 가는 동심원 + 가운데 원 + 고정된 음파 막대.
 * 주행 중에도 쓰기 때문에 움직이지 않고, 진행 중이면 해 색, 아니면 옅은 보라로만 구분한다.
 */
@Composable
fun VoiceOrb(active: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        val core = r * 0.5f
        val ring = if (active) SideColors.Sun else SideColors.Blush
        if (active) {
            drawCircle(Brush.radialGradient(listOf(SideColors.SunGlow, Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
        listOf(1.0f, 0.82f, 0.66f).forEachIndexed { i, k ->
            drawCircle(ring.copy(alpha = 0.18f + i * 0.12f), radius = r * k - 1.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        }
        drawCircle(if (active) SideColors.Sun else SideColors.Blush.copy(alpha = 0.28f), radius = core, center = c)
        val bar = if (active) SideColors.OnSun else Color.White
        val heights = listOf(0.3f, 0.6f, 0.9f, 0.55f, 1f, 0.62f, 0.32f)
        val barW = core * 0.09f
        val gap = core * 0.07f
        val total = heights.size * barW + (heights.size - 1) * gap
        heights.forEachIndexed { i, k ->
            val bh = core * 0.85f * k
            drawRoundRect(bar, Offset(c.x - total / 2 + i * (barW + gap), c.y - bh / 2), Size(barW, bh), CornerRadius(barW / 2))
        }
    }
}

/** 해 색으로 채운 주 버튼. 한 화면에 하나만 둔다. */
@Composable
fun SunButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: TextStyle = SideType.Button,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Surface(onClick = onClick, modifier = modifier, shape = shape, color = SideColors.Sun) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = SideColors.OnSun, modifier = Modifier.size((style.fontSize.value * 1.2f).dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = style, color = SideColors.OnSun)
        }
    }
}

/** 가는 선 테두리만 있는 보조 버튼. */
@Composable
fun LineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.35f),
        shape = RoundedCornerShape(20.dp),
        color = SideColors.Panel,
        border = BorderStroke(1.dp, SideColors.LineStrong),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = SideColors.Text, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = SideType.Label.copy(fontSize = SideType.Label.fontSize * 0.9f), color = SideColors.Text)
        }
    }
}

/** 해 색 동그라미 아이콘 버튼(재생/일시정지, 마이크). */
@Composable
fun RoundSunButton(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = SideColors.Sun,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = SideColors.OnSun, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** 하위 화면 위쪽의 "← 제목" 줄. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "홈으로" },
            shape = CircleShape,
            color = SideColors.Panel,
            border = BorderStroke(1.dp, SideColors.Line),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = SideColors.Text, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = SideType.Eyebrow, color = SideColors.Blush)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 두 자리 번호(01, 02 …). */
fun twoDigits(n: Int): String = n.toString().padStart(2, '0')
