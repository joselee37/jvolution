package today.superb.jvl.ui.sonar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.abs
import kotlin.math.sin

private const val SWEEP_MS = 1600f
private const val BREATH_PERIOD_MS = 2600f

/**
 * 도트 ghost 생명체. frame-state(브레스 위상·sweep 진행)는 GameState 밖에서 자율 동작하고,
 * 트리거 [pingNonce]만 GameState에서 읽는다(PLAN: frame 층 분리).
 *
 * frame 루프는 LaunchedEffect 없이 단일 withFrameNanos 루프로 시간을 흘려보낸다.
 * sweep은 pingNonce 변화에 맞춰 0→1 진행하며 좌→우로 밝은 띠가 지나간다.
 */
@Composable
fun DotCreatureCanvas(pingNonce: Int, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current

    var timeMs by remember { mutableStateOf(0f) }
    var sweepStartMs by remember { mutableStateOf(-1f) }
    var lastNonce by remember { mutableStateOf(pingNonce) }

    // 단일 프레임 루프 — 시간 누적 + ping 트리거 감지.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos -> timeMs = (nanos - startNanos) / 1_000_000f }
        }
    }
    // pingNonce가 바뀌면 sweep 시작 시각을 현재로 찍는다.
    if (pingNonce != lastNonce) {
        lastNonce = pingNonce
        sweepStartMs = timeMs
    }

    val breath = 0.5f + 0.5f * sin(timeMs / BREATH_PERIOD_MS * 2f * 3.14159f)
    val sweepProgress = if (sweepStartMs < 0f) 1f else ((timeMs - sweepStartMs) / SWEEP_MS).coerceIn(0f, 1f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val bodyRx = w * 0.22f * (0.94f + 0.06f * breath)
        val bodyRy = h * 0.30f * (0.94f + 0.06f * breath)
        val step = (w * 0.028f).coerceIn(6f, 18f)

        val sweepX = if (sweepProgress < 1f) sweepProgress * w else -1f

        var gx = cx - bodyRx
        while (gx <= cx + bodyRx) {
            var gy = cy - bodyRy
            while (gy <= cy + bodyRy) {
                val nx = (gx - cx) / bodyRx
                val ny = (gy - cy) / bodyRy
                if (nx * nx + ny * ny <= 1f) {
                    // 기본 밝기: 브레스에 따라 은은하게. sweep 띠 근처면 강조.
                    var brightness = 0.35f + 0.25f * breath
                    if (sweepX >= 0f) {
                        val d = abs(gx - sweepX)
                        if (d < step * 2.5f) brightness = (brightness + (1f - d / (step * 2.5f)) * 0.8f).coerceAtMost(1f)
                    }
                    val color = lerpColor(palette.phosDim, palette.phos, brightness)
                    drawCircle(color = color, radius = step * 0.28f, center = Offset(gx, gy))
                }
                gy += step
            }
            gx += step
        }

        // 눈 2개(상단).
        val eyeOffsetX = bodyRx * 0.42f
        val eyeY = cy - bodyRy * 0.25f
        drawCircle(palette.bg, radius = step * 0.45f, center = Offset(cx - eyeOffsetX, eyeY))
        drawCircle(palette.bg, radius = step * 0.45f, center = Offset(cx + eyeOffsetX, eyeY))
    }
}

/** 두 색을 선형 보간(Compose lerp 의존 없이 채널별 보간). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * u,
        green = a.green + (b.green - a.green) * u,
        blue = a.blue + (b.blue - a.blue) * u,
        alpha = 1f,
    )
}
