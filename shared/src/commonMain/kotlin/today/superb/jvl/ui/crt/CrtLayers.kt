package today.superb.jvl.ui.crt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import today.superb.jvl.ui.settings.LocalTweaks
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private const val SCANLINE_GAP = 3f
private const val SCANBAND_PERIOD = 7f   // 위→아래 이동 주기(초). 데모 06 명세.
private const val FLICKER_PERIOD = 2.6f  // 미세 명멸 주기(초).
private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * CRT 연출 오버레이 — 셰이더 없이 Compose Canvas 근사(PLAN: 셰이더는 phase 2).
 * content 위에 스캔라인(정적) + 스캔밴드(7s 이동) + 비네트 + 플리커(2.6s)를 겹친다.
 */
@Composable
fun CrtLayers(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val tweaks = LocalTweaks.current
    val intensity = tweaks.crtIntensity
    var timeMs by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos -> timeMs = (nanos - start) / 1_000_000_000f }
        }
    }

    Box(modifier) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // 스캔라인 — 정적 가로 주사선. intensity로 농도 스케일(데모 0.18*crt), 토글 가능.
            if (tweaks.scanlines) {
                val scanAlpha = (0.18f * intensity).coerceIn(0f, 0.4f)
                var y = 0f
                while (y < h) {
                    drawLine(
                        color = Color.Black.copy(alpha = scanAlpha),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                    y += SCANLINE_GAP
                }
            }

            // 스캔밴드 — 위→아래로 흐르는 넓고 옅은 밝은 띠.
            val bandH = h * 0.22f
            val travel = (timeMs % SCANBAND_PERIOD) / SCANBAND_PERIOD
            val bandTop = travel * (h + bandH) - bandH
            drawRect(
                brush = Brush.verticalGradient(
                    0f to palette.phos.copy(alpha = 0f),
                    0.5f to palette.phos.copy(alpha = 0.05f),
                    1f to palette.phos.copy(alpha = 0f),
                    startY = bandTop,
                    endY = bandTop + bandH,
                ),
                topLeft = Offset(0f, bandTop),
                size = Size(w, bandH),
            )

            // 비네트 — 가장자리 어둡게.
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.65f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                    center = Offset(w / 2f, h / 2f),
                    radius = max(w, h) * 0.72f,
                ),
                size = size,
            )

            // 플리커 — 미세 명멸(intensity로 스케일). noise 켜짐 시 grain veil 추가(셰이더 전 근사).
            val flicker = 0.5f + 0.5f * sin(timeMs / FLICKER_PERIOD * TWO_PI)
            val noiseVeil = if (tweaks.noise) 0.05f * intensity * flicker else 0f
            drawRect(color = Color.Black.copy(alpha = (0.015f + 0.025f * flicker) * intensity + noiseVeil), size = size)
        }
    }
}
