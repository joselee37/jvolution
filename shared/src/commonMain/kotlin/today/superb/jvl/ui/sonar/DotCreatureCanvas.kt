package today.superb.jvl.ui.sonar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import today.superb.jvl.ui.sonar.species.ghostDensity
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val COLS = 30
private const val ROWS = 38
private const val TWO_PI = (2.0 * PI).toFloat()

// 데모 creature.jsx 상수 1:1.
private const val PULSE_DURATION = 1.6f   // 빔이 한 번 가로지르는 시간(초)
private const val PULSE_INTERVAL = 5f     // 자동 펄스 주기(초)
private const val BEAM_HALFWIDTH = 0.05f  // u-space 선단 반폭
private const val TRAIL_REACH = 0.45f     // u-space 잔상 길이
private const val DECAY_TAU = 1.0f        // phosphor 감쇠 시상수(초)

private class DotPoint(val u: Float, val v: Float, val sparsity: Float)

private fun buildGhostPoints(): List<DotPoint> {
    val rng = Random(20260530)
    val pts = ArrayList<DotPoint>(COLS * ROWS)
    for (j in 0 until ROWS) {
        for (i in 0 until COLS) {
            val u = ((i + 0.5f) / COLS) * 2f - 1f
            val v = ((j + 0.5f) / ROWS) * 2f - 1f
            pts.add(DotPoint(u, v, rng.nextFloat()))
        }
    }
    return pts
}

/**
 * 도트 ghost 생명체 — 데모 `SonarCreature` 충실 포팅.
 *
 * frame-state(브레스·phosphor 잔광·빔 위치)는 GameState 밖에서 자율 동작하고, 트리거 [pingNonce]만
 * GameState에서 읽는다. 빔(자동 펄스 ~5s, ping 시 즉시 1회)이 실루엣을 가로지르면 닿은 dot이 켜지고
 * `exp(-Δt/τ)`로 잔광 감쇠한다. happiness는 동공 시선, asleep은 전체 밝기에 반영.
 */
@Composable
fun DotCreatureCanvas(
    pingNonce: Int,
    happiness: Float,
    asleep: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val points = remember { buildGhostPoints() }
    val brightness = remember { FloatArray(points.size) }
    val litTime = remember { FloatArray(points.size) { -10f } }

    val curHappiness by rememberUpdatedState(happiness)
    val curAsleep by rememberUpdatedState(asleep)
    val curPingNonce by rememberUpdatedState(pingNonce)

    // 매 프레임 갱신되어 Canvas 재그리기를 유발하는 시간/빔 상태.
    var renderT by remember { mutableStateOf(0f) }
    var scanU by remember { mutableStateOf(Float.NaN) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        var handledNonce = curPingNonce
        var pingStartMs = -1f
        while (true) {
            val nanos = withFrameNanos { it }
            val t = (nanos - startNanos) / 1_000_000_000f

            // ping 트리거 감지 → 즉시 sweep 시작(자동 펄스보다 우선).
            if (curPingNonce != handledNonce) {
                handledNonce = curPingNonce
                pingStartMs = t
            }
            val pingActive = pingStartMs >= 0f && (t - pingStartMs) < PULSE_DURATION
            val beamU: Float = when {
                pingActive -> ((t - pingStartMs) / PULSE_DURATION) * 2f - 1f
                else -> {
                    val phase = (t % PULSE_INTERVAL) / PULSE_DURATION
                    if (phase < 1f) phase * 2f - 1f else Float.NaN
                }
            }

            val sleepFactor = if (curAsleep) 0.4f else 1f
            val breathU = sin(t * 0.6f) * 0.015f
            val breathV = cos(t * 0.4f) * 0.012f

            for (idx in points.indices) {
                val p = points[idx]
                val u = p.u + breathU
                val v = p.v + breathV
                var density = ghostDensity(u, v, t, curHappiness)

                // 가장자리 jitter — 실루엣 경계를 sonar-return처럼 거칠게.
                val jitter = p.sparsity
                if (density == 0f) {
                    val angle = jitter * TWO_PI
                    val sampled = ghostDensity(u + cos(angle) * 0.05f, v + sin(angle) * 0.05f, t, curHappiness)
                    if (sampled > 0.25f && jitter > 0.86f) density = 0.22f
                } else if (density < 0.5f && jitter > 0.7f) {
                    density *= 0.35f
                }
                val present = density > 0f && p.sparsity < density * 0.92f + 0.08f

                var beamBoost = 0f
                if (!beamU.isNaN() && present) {
                    val d = u - beamU
                    if (d > -BEAM_HALFWIDTH && d < BEAM_HALFWIDTH) {
                        litTime[idx] = t
                        beamBoost = 1f - abs(d) / BEAM_HALFWIDTH
                    } else if (d < 0f && d > -TRAIL_REACH) {
                        beamBoost = maxOf(0f, 0.5f + d / TRAIL_REACH * 0.5f)
                    }
                }
                val trail = exp(-(t - litTime[idx]) / DECAY_TAU)
                val target = if (present) minOf(1f, maxOf(trail, beamBoost) * sleepFactor) else 0f
                brightness[idx] += (target - brightness[idx]) * 0.4f
            }

            renderT = t
            scanU = beamU
        }
    }

    Canvas(modifier) {
        val t = renderT
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.46f
        val ry = size.height * 0.46f
        val spacing = (2f / COLS) * rx

        for (idx in points.indices) {
            val b0 = brightness[idx]
            if (b0 < 0.02f) continue
            val p = points[idx]
            val twinkle = 1f + sin(t * 2f + p.u * 5f + p.v * 7f) * 0.06f
            val b = (b0 * twinkle).coerceIn(0f, 1f)
            if (b < 0.02f) continue

            val center = Offset(cx + p.u * rx, cy + p.v * ry)
            drawCircle(
                color = lerpColor(palette.phosDim, palette.phos, b).copy(alpha = b),
                radius = spacing * (0.30f + b * 0.34f),
                center = center,
            )
            if (b > 0.5f) {
                drawCircle(
                    color = palette.phos.copy(alpha = (b - 0.5f) * 2f),
                    radius = spacing * 0.18f,
                    center = center,
                )
            }
        }

        // 빔 — 넓은 halo + 밝은 선단.
        if (!scanU.isNaN()) {
            val x = cx + scanU * rx
            val haloW = rx * 0.34f
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to palette.phos.copy(alpha = 0f),
                    0.85f to palette.phos.copy(alpha = 0.10f),
                    1f to palette.phos.copy(alpha = 0.32f),
                    startX = x - haloW,
                    endX = x + 4f,
                ),
                topLeft = Offset(x - haloW, 0f),
                size = Size(haloW + 4f, size.height),
            )
            drawRect(
                color = palette.phos.copy(alpha = 0.9f),
                topLeft = Offset(x - 1.5f, 0f),
                size = Size(3f, size.height),
            )
        }
    }
}

/** 두 색을 채널별 선형 보간(알파는 호출부에서 별도 지정). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * u,
        green = a.green + (b.green - a.green) * u,
        blue = a.blue + (b.blue - a.blue) * u,
        alpha = 1f,
    )
}
