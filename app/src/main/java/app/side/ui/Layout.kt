package app.side.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 앱 화면이 좁은(세로 분할) 배치인지. 현대차 Pleos 디스플레이는 화면을 세로·가로로 나눠 쓸 수 있어서,
 * 세로로 나뉜 패널(약 840×1210dp)에서는 2단 배치 대신 위아래로 쌓는다. [SideApp]이 실제 창 크기로 정한다.
 */
val LocalPortrait = staticCompositionLocalOf { false }

/**
 * 세로 배치로 바꾸는 기준 폭. 가로 패널은 약 1680dp, 세로 패널은 약 840dp.
 * 가로 배치는 왼쪽 메뉴(약 313dp)와 이야기 화면 버튼 줄(약 756dp) 때문에 약 1070dp 이상이 필요하다.
 */
val PortraitMaxWidth: Dp = 1100.dp

/**
 * 가로 배치면 [Row]로 나란히, 세로 배치면 [Column]으로 위아래로 놓는다.
 * [content]는 각 칸에 붙일 Modifier를 만드는 함수를 받는다: 가로면 비율(weight), 세로면 전체 폭.
 * 세로 배치에서는 weight와 [verticalAlignment]가 쓰이지 않는다. cell()은 직계 자식에만 붙여야 weight가 적용된다.
 */
@Composable
fun SplitOrStack(
    modifier: Modifier = Modifier,
    spacing: Dp,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable (cell: (weight: Float) -> Modifier) -> Unit,
) {
    if (LocalPortrait.current) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            content { Modifier.fillMaxWidth() }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = verticalAlignment) {
            content { w -> Modifier.weight(w) }
        }
    }
}
