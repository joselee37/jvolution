package today.superb.jvl.ui.battle

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import today.superb.jvl.core.battle.BattleAction
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleState
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.max
import kotlin.math.min

// 페이즈별 연출 시간(초) — VM 스케줄러(myCast/theirCast/reveal 0.7s, damage 0.5s)와 정합.
private const val CAST_DUR = 0.7f
private const val REVEAL_DUR = 0.7f
private const val DAMAGE_DUR = 0.5f

/**
 * 전투 파형 클래시 오버레이 — 데모 `BattleClash`+`drawSignature` 포팅.
 *
 * myCast/theirCast에서 각 측 무브 시그니처를 제자리 캐스팅, reveal에서 양측이 중앙으로 이동해 충돌
 * 플래시, damage에서 피격 측에 임팩트 플래시(+crit 시 충격파 링). frame-state는 phase/turn 변화에
 * 리셋되는 elapsed로 구동([DotCreatureCanvas]/[RadarScreen] 패턴).
 */
@Composable
fun BattleClash(battle: BattleState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var elapsed by remember(battle.phase, battle.turn) { mutableStateOf(0f) }

    LaunchedEffect(battle.phase, battle.turn) {
        val start = withFrameNanos { it }
        while (true) {
            val n = withFrameNanos { it }
            elapsed = (n - start) / 1_000_000_000f
        }
    }

    val phase = battle.phase
    val myMove = battle.myMove
    val theirMove = battle.theirMove
    val color = palette.phos

    Canvas(modifier) {
        val cy = size.height / 2f
        val meX = size.width * 0.22f
        val themX = size.width * 0.78f
        val midX = size.width / 2f

        when (phase) {
            BattlePhase.MyCast -> if (myMove != null) {
                val t = min(1f, elapsed / CAST_DUR)
                drawSignature(myMove, color, Offset(meX + 14f * t, cy), t, mirror = false)
            }
            BattlePhase.TheirCast -> if (theirMove != null) {
                val t = min(1f, elapsed / CAST_DUR)
                drawSignature(theirMove, color, Offset(themX - 14f * t, cy), t, mirror = true)
            }
            BattlePhase.Reveal -> if (myMove != null && theirMove != null) {
                val t = min(1f, elapsed / REVEAL_DUR)
                drawSignature(myMove, color, Offset(meX + (midX - meX) * t, cy), t, mirror = false)
                drawSignature(theirMove, color, Offset(themX - (themX - midX) * t, cy), t, mirror = true)
                if (t > 0.85f) {
                    val r = (t - 0.85f) * 80f + 12f
                    drawCircle(color.copy(alpha = (1f - (t - 0.85f) * 6.6f).coerceIn(0f, 1f) * 0.9f), radius = r, center = Offset(midX, cy))
                }
            }
            BattlePhase.Damage -> {
                val t = min(1f, elapsed / DAMAGE_DUR)
                val crit = battle.log.firstOrNull()?.crit == true
                val targets = buildList {
                    if (battle.lastDmgMe > 0f) add(meX)
                    if (battle.lastDmgThem > 0f) add(themX)
                    if (isEmpty()) add(midX)
                }
                val maxR = if (crit) max(size.width, size.height) * 0.7f else 80f
                for (fx in targets) {
                    drawCircle(color.copy(alpha = (1f - t) * if (crit) 0.9f else 0.6f), radius = t * maxR, center = Offset(fx, cy))
                    if (crit) {
                        for (i in 0 until 4) {
                            val rr = ((t * 1.6f - i * 0.1f) % 1f) * (maxR * 0.9f)
                            if (rr > 0f) drawCircle(color.copy(alpha = 0.6f * (1f - rr / (maxR * 0.9f))), radius = rr, center = Offset(fx, cy), style = Stroke(2f))
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

/** 무브별 시그니처 실루엣. 데모 `drawSignature` 포팅(forward=상대 방향, mirror로 좌우 반전). */
private fun DrawScope.drawSignature(move: BattleAction, color: Color, center: Offset, t: Float, mirror: Boolean) {
    val sign = if (mirror) -1f else 1f
    val alpha = min(1f, 0.4f + t)
    val c = color.copy(alpha = alpha)
    when (move) {
        BattleAction.Ping -> {
            val forward = if (mirror) 180f else 0f
            for (i in 0 until 3) {
                val r = 6f + i * 6f + (t * 8f) % 6f
                drawArc(
                    color = c, startAngle = forward - 45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(2f),
                )
            }
        }
        BattleAction.Charge -> {
            val head = Path().apply {
                moveTo(center.x + sign * 14f, center.y)
                lineTo(center.x - sign * 10f, center.y - 8f)
                lineTo(center.x - sign * 6f, center.y)
                lineTo(center.x - sign * 10f, center.y + 8f)
                close()
            }
            drawPath(head, c)
            for (i in 0 until 4) {
                val dx = -sign * (14f + i * 8f)
                drawRect(c, topLeft = Offset(center.x + min(dx, dx + sign * 5f), center.y - 1f), size = Size(5f, 2f))
            }
        }
        BattleAction.Dodge -> drawCircle(
            color = color.copy(alpha = alpha * 0.6f), radius = 10f, center = center,
            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))),
        )
        BattleAction.Screech -> {
            val path = Path()
            for (i in -10..10) {
                val px = center.x + sign * i * 2f
                val py = center.y + (if (i % 2 == 0) -1f else 1f) * 6f
                if (i == -10) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, c, style = Stroke(2f))
        }
    }
}
