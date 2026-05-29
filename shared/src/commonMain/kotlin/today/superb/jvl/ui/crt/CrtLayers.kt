package today.superb.jvl.ui.crt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * CRT 연출 오버레이 — 1차는 셰이더 없이 Compose Canvas로 스캔라인을 근사(PLAN: 셰이더는 phase 2).
 * content 위에 얇은 어두운 가로선을 일정 간격으로 겹쳐 주사선 느낌을 낸다.
 */
@Composable
fun CrtLayers(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val gap = 3f
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.18f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += gap
            }
        }
    }
}
