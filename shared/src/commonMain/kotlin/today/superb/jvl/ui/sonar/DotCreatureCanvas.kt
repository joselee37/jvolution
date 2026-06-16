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
import today.superb.jvl.core.Species
import today.superb.jvl.core.genetics.AppearanceTraits
import today.superb.jvl.ui.settings.LocalTweaks
import today.superb.jvl.ui.sonar.species.densityFor
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

// 정사각 그리드 — 등방(isotropic) 매핑이라 가로/세로 도트 간격이 동일하고 ghost 실루엣이 안 깨짐.
private const val COLS = 34
private const val ROWS = 34
private const val TWO_PI = (2.0 * PI).toFloat()

/** 도트 sparsity 고정 시드(데모는 빌드마다 Math.random — 우리는 프레임 안정 위해 결정적). */
private const val GHOST_SEED = 20260530L

// 데모 creature.jsx 상수 1:1.
private const val PULSE_DURATION = 1.6f   // 빔이 한 번 가로지르는 시간(초)
private const val BEAM_HALFWIDTH = 0.05f  // u-space 선단 반폭
private const val TRAIL_REACH = 0.45f     // u-space 잔상 길이
// 자동 펄스 주기·phosphor 감쇠는 Tweaks(pulsePeriod/phosphorDecay)에서 읽는다.

private class DotPoint(val i: Int, val j: Int, val u: Float, val v: Float, val sparsity: Float)

private fun buildGhostPoints(): List<DotPoint> {
    val rng = Random(GHOST_SEED)
    val pts = ArrayList<DotPoint>(COLS * ROWS)
    for (j in 0 until ROWS) {
        for (i in 0 until COLS) {
            val u = ((i + 0.5f) / COLS) * 2f - 1f
            val v = ((j + 0.5f) / ROWS) * 2f - 1f
            pts.add(DotPoint(i, j, u, v, rng.nextFloat()))
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
    species: Species = Species.Ghost,
    energy: Float = 1f,
    // 게놈 변조(외형 형질) — 종 실루엣은 유지하고 개체별 크기/밝기/톤/텍스처만 바꾼다.
    appearance: AppearanceTraits = AppearanceTraits(
        bodyLength = 5, branchAngle = 6, symmetry = 4, recursionDepth = 3, hue = 3, hueAlt = null, pattern = 2,
    ),
    vitality: Float = 0.5f,
) {
    val palette = LocalPalette.current
    val points = remember { buildGhostPoints() }
    // hue 유전자 → phosphor 팔레트 안 미세 톤 바이어스(풀컬러 아님). draw에서 b에 가산.
    val tintBias = (appearance.hue / 7f - 0.5f) * 0.12f
    val brightness = remember { FloatArray(points.size) }
    val litTime = remember { FloatArray(points.size) { -10f } }

    val tweaks = LocalTweaks.current
    val curHappiness by rememberUpdatedState(happiness)
    val curAsleep by rememberUpdatedState(asleep)
    val curPingNonce by rememberUpdatedState(pingNonce)
    val curSpecies by rememberUpdatedState(species)
    val curEnergy by rememberUpdatedState(energy)
    val curAppearance by rememberUpdatedState(appearance)
    val curVitality by rememberUpdatedState(vitality)
    val curPulse by rememberUpdatedState(tweaks.pulsePeriod)
    val curDecay by rememberUpdatedState(tweaks.phosphorDecay)

    // 매 프레임 갱신되어 Canvas 재그리기를 유발하는 시간/빔 상태.
    var renderT by remember { mutableStateOf(0f) }
    var scanU by remember { mutableStateOf(Float.NaN) }

    // 항상 도는 프레임 루프(브레스/잔광/sweep). 매 프레임 전 도트 시뮬레이션을 돌리는 의도된 비용 —
    // CRT가 "살아있게" 보이려면 정착 후에도 미세 애니메이션이 필요. withFrameNanos는 앱이 백그라운드/
    // 화면 off면 호스트가 프레임을 안 주므로 자연히 멈춘다(올바른 primitive).
    LaunchedEffect(Unit) {
        // 첫 withFrameNanos는 기준점만 잡고 버린다 — t는 다음 프레임부터 0+.
        val startNanos = withFrameNanos { it }
        var handledNonce = curPingNonce  // 마운트 시점 nonce는 처리됨으로 — 부팅 직후 가짜 ping 방지.
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
            // ping sweep 종료 시 리셋 — 자동 펄스와 상호배타로 만들어 빔 우선순위 플리커 방지.
            if (pingStartMs >= 0f && (t - pingStartMs) >= PULSE_DURATION) pingStartMs = -1f
            val beamU: Float = when {
                pingActive -> ((t - pingStartMs) / PULSE_DURATION) * 2f - 1f
                else -> {
                    val phase = (t % curPulse) / PULSE_DURATION
                    if (phase < 1f) phase * 2f - 1f else Float.NaN
                }
            }

            val sleepFactor = if (curAsleep) 0.4f else 1f
            val breathU = sin(t * 0.6f) * 0.015f
            val breathV = cos(t * 0.4f) * 0.012f
            // 게놈 변조 인자(프레임당 1회). bodyLength→스케일, vitality→밝기, pattern→스티플 텍스처.
            val scale = 0.85f + curAppearance.bodyLength / 9f * 0.30f
            val brightnessMul = 0.8f + curVitality * 0.3f
            val stippleThresh = 0.78f - curAppearance.pattern / 5f * 0.18f

            for (idx in points.indices) {
                val p = points[idx]
                val u = p.u + breathU
                val v = p.v + breathV
                // 실루엣 샘플링만 스케일(위치/빔/draw는 원좌표). scale>1 → 더 크게 보임.
                val su = u / scale
                val sv = v / scale
                var density = densityFor(curSpecies, su, sv, t, curHappiness, curEnergy)

                // 가장자리 jitter — 실루엣 경계를 sonar-return처럼 거칠게.
                val jitter = p.sparsity
                if (density == 0f) {
                    val angle = jitter * TWO_PI
                    val sampled = densityFor(curSpecies, su + cos(angle) * 0.05f, sv + sin(angle) * 0.05f, t, curHappiness, curEnergy)
                    if (sampled > 0.25f && jitter > 0.86f) density = 0.22f
                } else if (density < 0.5f && jitter > stippleThresh) {
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
                val trail = exp(-(t - litTime[idx]) / curDecay)
                val target = if (present) minOf(1f, maxOf(trail, beamBoost) * sleepFactor * brightnessMul) else 0f
                brightness[idx] += (target - brightness[idx]) * 0.4f
            }

            renderT = t
            scanU = beamU
        }
    }

    Canvas(modifier) {
        // 재그리기는 renderT(snapshot state) 읽기로 구동된다 — brightness/litTime FloatArray는
        // 성능상 의도적으로 snapshot 밖에 있으므로, 이 t 읽기를 제거하면 배열 변이가 안 그려진다.
        val t = renderT
        val cx = size.width / 2f
        val cy = size.height / 2f
        // 등방 스케일 — u·v를 같은 r로 매핑해야 density 함수의 등방 가정(sqrt(u²+v²))이 유지된다.
        val r = minOf(size.width, size.height) * 0.46f
        val spacing = (2f / COLS) * r

        for (idx in points.indices) {
            val b0 = brightness[idx]
            if (b0 < 0.02f) continue
            val p = points[idx]
            val twinkle = 1f + sin(t * 2f + p.i * 0.7f + p.j * 1.3f) * 0.06f
            val b = (b0 * twinkle).coerceIn(0f, 1f)
            if (b < 0.02f) continue

            val center = Offset(cx + p.u * r, cy + p.v * r)
            val tb = (b + tintBias).coerceIn(0f, 1f)   // hue 유전자 톤 바이어스(팔레트 안)
            drawCircle(
                color = lerpColor(palette.phosDim, palette.phos, tb).copy(alpha = b),
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
            val x = cx + scanU * r
            val haloW = r * 0.34f
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
            // 타이트한 채도 높은 body(데모 creature.jsx의 중간층) — 선단↔halo 밝기 점프 완화.
            val bodyW = haloW * 0.22f
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to palette.phos.copy(alpha = 0f),
                    1f to palette.phos.copy(alpha = 0.7f),
                    startX = x - bodyW,
                    endX = x + 3f,
                ),
                topLeft = Offset(x - bodyW, 0f),
                size = Size(bodyW + 3f, size.height),
            )
            // 밝은 선단 스파이크.
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
