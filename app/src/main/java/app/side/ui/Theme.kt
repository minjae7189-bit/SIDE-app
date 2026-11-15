package app.side.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.side.R

/**
 * "코랄 노을" 테마.
 * 남보라 하늘이 코랄을 지나 살구빛 지평선으로 이어지고, 그 위로 연노랑 해가 떠오른다.
 * 상자 대신 가는 선과 큰 글자로 화면을 나누고, 주 동작은 연노랑(해) 하나로만 표시한다.
 */
object SideColors {
    val Night = Color(0xFF352C66) // 위쪽 하늘
    val Dusk = Color(0xFF6A3D7C)
    val Violet = Color(0xFFB5547E) // 코랄 노을
    val Horizon = Color(0xFFFFB48A) // 지평선 살구빛
    val Sky = Brush.verticalGradient(0f to Night, 0.45f to Dusk, 0.68f to Violet, 0.78f to Horizon) // 0.78 = 지평선(HORIZON)
    /** 지평선 없는 화면용 하늘. 아래까지 내용이 차는 화면이라 끝을 밝은 살구빛 대신 코랄로 맺어 흰 글자가 잘 읽히게 한다. */
    val SkyPlain = Brush.verticalGradient(0f to Night, 0.6f to Dusk, 1f to Violet)

    val Sun = Color(0xFFFFE38A) // 주 버튼·진행 중 표시
    val SunGlow = Color(0x80FFB48A)
    val OnSun = Color(0xFF3A2A00)
    val Blush = Color(0xFFFFD3E0) // 번호·작은 제목

    val Text = Color(0xFFFFFFFF)
    val TextSoft = Color(0xCCFFFFFF)
    val TextFaint = Color(0x8CFFFFFF)
    val Ghost = Color(0x1AFFFFFF) // 배경에 깔리는 큰 번호

    val Line = Color(0x33FFFFFF) // 가는 구분선·테두리
    val LineStrong = Color(0x66FFFFFF)
    val Fill = Color(0x0FFFFFFF) // 아주 옅은 바탕
    /** 테두리 버튼·칸의 바탕. 배경의 지평선·해가 비쳐 글자와 겹치지 않게 어둡게 깐다. */
    val Panel = Color(0xB82A2353)
    val Ground = Color(0xFF2A2353) // 지평선 아래 땅
    val FillSelected = Color(0x2EFFE38A)

    val ErrorSoft = Color(0xFFFFC7CF)
}

/** 제목은 부드러운 바탕체(고운바탕), 나머지는 IBM Plex Sans KR. */
object SideFonts {
    val Serif = FontFamily(
        Font(R.font.gowun_batang_regular, FontWeight.Normal),
        Font(R.font.gowun_batang_bold, FontWeight.Bold),
    )
    val Sans = FontFamily(
        Font(R.font.plex_sans_kr_regular, FontWeight.Normal),
        Font(R.font.plex_sans_kr_medium, FontWeight.Medium),
        Font(R.font.plex_sans_kr_semibold, FontWeight.SemiBold),
        Font(R.font.plex_sans_kr_bold, FontWeight.Bold),
    )
}

private val tightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** 화면에서 쓰는 글자 모양. 크기는 차량 화면(1280×720 기준)에서 팔 길이 거리로 읽히도록 잡았다. */
object SideType {
    /** 홈 큰 제목 */
    val Hero = serif(60.sp, 1.18f, (-0.02).em)
    /** 주행 화면 제목, 하위 화면 큰 제목 */
    val Title = serif(48.sp, 1.22f, (-0.015).em)
    /** 이야기 나누기 질문 */
    val Question = serif(40.sp, 1.3f, (-0.01).em)
    /** 인용 문구(질문 예시) */
    val Quote = serif(26.sp, 1.38f, 0.em)
    /** 큰 번호(01, 02 …) */
    val Number = TextStyle(fontFamily = SideFonts.Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, letterSpacing = 0.02.em)
    /** 배경에 깔리는 아주 큰 번호 */
    val GhostNumber = TextStyle(fontFamily = SideFonts.Serif, fontWeight = FontWeight.Bold, fontSize = 200.sp, lineHeight = 200.sp, lineHeightStyle = tightLines)

    /** 작은 위 제목: 자간을 넓힌 굵은 글씨 */
    val Eyebrow = TextStyle(fontFamily = SideFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.18.em)
    val Label = TextStyle(fontFamily = SideFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.01).em)
    val LabelSmall = TextStyle(fontFamily = SideFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val Body = TextStyle(fontFamily = SideFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 32.sp)
    val Button = TextStyle(fontFamily = SideFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.01).em)

    private fun serif(size: TextUnit, lineHeight: Float, tracking: TextUnit) = TextStyle(
        fontFamily = SideFonts.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * lineHeight,
        letterSpacing = tracking,
        lineHeightStyle = tightLines,
    )
}

@Composable
fun SideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SideColors.Sun,
            onPrimary = SideColors.OnSun,
            secondary = SideColors.Blush,
            background = SideColors.Night,
            surface = SideColors.Dusk,
            onBackground = SideColors.Text,
            onSurface = SideColors.Text,
        ),
        // 따로 글자 모양을 주지 않은 Text도 Plex Sans KR로 나오게 한다.
        typography = Typography().let { t ->
            Typography(
                bodyLarge = t.bodyLarge.copy(fontFamily = SideFonts.Sans),
                bodyMedium = t.bodyMedium.copy(fontFamily = SideFonts.Sans),
                bodySmall = t.bodySmall.copy(fontFamily = SideFonts.Sans),
                labelLarge = t.labelLarge.copy(fontFamily = SideFonts.Sans),
            )
        },
        content = content,
    )
}
