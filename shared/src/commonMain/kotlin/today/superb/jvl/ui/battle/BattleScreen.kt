package today.superb.jvl.ui.battle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.battle.BattleAction
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.core.battle.BattleState
import today.superb.jvl.ui.sonar.DotCreatureCanvas
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.ceil

/**
 * 전투 화면 — 데모 `BattleScreen` 포팅(명료성 우선). 상단 HP 바 + 턴, 중앙 양측 생명체 +
 * 페이즈/결과 오버레이, 하단 서술 로그 + 4액션 메뉴.
 *
 * 정교한 파형 클래시 캔버스·카메라 팬은 cosmetic이라 후속 시각 다듬기로 미룬다 — 게임 상태(무브·
 * 결과·데미지·HP)는 라벨/바로 완전히 읽힌다. 페이즈 전이는 GameViewModel 스케줄러가 구동.
 *
 * @param onSelectMove 액션 칩 탭 — 인덱스(0..3)로 커서 지정 + 커밋. choose 단계에서만 활성.
 */
@Composable
fun BattleScreen(state: GameState, onSelectMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    val battle = state.battle ?: return
    val peer = state.peers.find { it.id == battle.peerId } ?: return
    val palette = LocalPalette.current

    Column(modifier.fillMaxSize().padding(8.dp)) {
        // ── HP 헤더 ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HpBar(state.name, battle.hpMe, battle.hpMaxMe, Alignment.Start, battle.flashNonceMe, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonoText("ENGAGEMENT", color = palette.phosDim, fontSize = 8.sp)
                MonoText("R.${battle.turn.toString().padStart(2, '0')}", color = palette.phos, fontSize = 13.sp, fontFamily = LocalDisplayFont.current)
            }
            HpBar(peer.name, battle.hpThem, battle.hpMaxThem, Alignment.End, battle.flashNonceThem, Modifier.weight(1f))
        }

        // ── 아레나(측면 스크롤 + 카메라 팬) + 클래시 오버레이 ──
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val vpW = maxWidth
                val arenaW = vpW * 1.8f
                val meAnchor = arenaW * 0.22f
                val themAnchor = arenaW * 0.78f
                val focusMe = vpW * 0.5f - meAnchor
                val focusThem = vpW * 0.5f - themAnchor
                val focusCenter = (vpW - arenaW) * 0.5f
                // damage 단계: 피격 측으로 팬(둘 다/없음이면 중앙).
                val defenderFocus = when {
                    battle.lastDmgMe > 0f && battle.lastDmgThem <= 0f -> focusMe
                    battle.lastDmgThem > 0f && battle.lastDmgMe <= 0f -> focusThem
                    else -> focusCenter
                }
                val target = when (battle.phase) {
                    BattlePhase.Choose, BattlePhase.MyCast -> focusMe
                    BattlePhase.TheirCast -> focusThem
                    BattlePhase.Reveal -> focusCenter
                    BattlePhase.Damage -> defenderFocus
                    BattlePhase.End -> focusCenter
                }
                val camX by animateDpAsState(target, label = "battle-cam")
                val creatureSize = minOf(maxHeight, 150.dp)

                Box(Modifier.width(arenaW).fillMaxHeight().offset(x = camX)) {
                    Box(Modifier.size(creatureSize).align(Alignment.CenterStart).offset(x = meAnchor - creatureSize * 0.5f)) {
                        DotCreatureCanvas(
                            pingNonce = 0, happiness = state.happiness, asleep = false, modifier = Modifier.fillMaxSize(),
                            species = state.species, energy = (battle.hpMe / battle.hpMaxMe).coerceAtLeast(0.15f),
                        )
                    }
                    Box(Modifier.size(creatureSize).align(Alignment.CenterStart).offset(x = themAnchor - creatureSize * 0.5f)) {
                        DotCreatureCanvas(
                            pingNonce = 0, happiness = peer.bond, asleep = false, modifier = Modifier.fillMaxSize(),
                            species = peer.species, energy = (battle.hpThem / battle.hpMaxThem).coerceAtLeast(0.15f),
                        )
                    }
                    BattleClash(battle, Modifier.matchParentSize())
                }
            }
            PhaseOverlay(battle, state.name, peer.name, Modifier.align(Alignment.Center))

            state.toast?.let { toast ->
                Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).background(palette.phosDim)) {
                    MonoText("▸ $toast", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = palette.phos, fontSize = 16.sp, fontFamily = LocalDisplayFont.current)
                }
            }
        }

        // ── 서술 로그(데미지/선택/종료 시 마지막 결과) ──
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            val showOutcome = battle.phase == BattlePhase.Choose || battle.phase == BattlePhase.Damage || battle.phase == BattlePhase.End
            val outcome = battle.log.firstOrNull()
            val result = battle.result
            if (battle.phase == BattlePhase.End && result != null) {
                MonoText(resultBanner(result), color = palette.phos, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current)
            } else if (showOutcome && outcome != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MonoText(outcome.tag + if (outcome.crit) " · RESONANCE" else "", color = palette.phos, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (outcome.line.isNotEmpty()) MonoText(outcome.line, color = palette.phosMid, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // ── 액션 메뉴 ──
        BattleMenu(cursor = battle.cursor, enabled = battle.phase == BattlePhase.Choose, onSelect = onSelectMove)
        MonoText("← → 선택 · 탭 커밋", color = palette.phosDim, fontSize = 8.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun HpBar(
    name: String,
    hp: Float,
    hpMax: Int,
    align: Alignment.Horizontal,
    flashNonce: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val filled = ceil(hp.coerceAtLeast(0f)).toInt()

    // 피격 플래시 — reducer가 올리는 flashNonce를 소비(420ms 화이트아웃 후 감쇠).
    val flash = remember { Animatable(0f) }
    LaunchedEffect(flashNonce) {
        if (flashNonce > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420))
        }
    }
    val cellOn = lerp(palette.phos, Color.White, flash.value * 0.8f)
    val cellBorder = lerp(palette.phosDim, Color.White, flash.value * 0.8f)

    Column(modifier, horizontalAlignment = align) {
        MonoText(name, color = palette.phos, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current)
        Row {
            repeat(hpMax) { i ->
                Box(
                    Modifier.padding(end = 2.dp).size(width = 12.dp, height = 8.dp)
                        .background(if (i < filled) cellOn else palette.phosGrid)
                        .border(1.dp, cellBorder),
                )
            }
        }
        MonoText("${filled.coerceAtLeast(0)}/$hpMax", color = palette.phosDim, fontSize = 9.sp)
    }
}

@Composable
private fun BattleMenu(cursor: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BattleAction.entries.forEachIndexed { i, move ->
            val active = i == cursor
            Box(
                Modifier.weight(1f)
                    .border(1.dp, if (active) palette.phos else palette.phosDim)
                    .background(if (active) palette.phosGrid else palette.bg)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                MonoText(
                    move.name.uppercase(),
                    color = if (enabled) palette.phos else palette.phosDim,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun PhaseOverlay(battle: BattleState, myName: String, theirName: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val text = when (battle.phase) {
        BattlePhase.Choose -> if (battle.log.isEmpty()) "▸ SELECT ACTION" else null
        BattlePhase.MyCast -> battle.myMove?.let { "$myName → ${it.name.uppercase()}" }
        BattlePhase.TheirCast -> battle.theirMove?.let { "$theirName → ${it.name.uppercase()}" }
        BattlePhase.Reveal -> "▸ RESOLVING…"
        BattlePhase.Damage, BattlePhase.End -> null
    }
    if (text != null) {
        Box(modifier.background(palette.bg).border(1.dp, palette.phosDim).padding(horizontal = 12.dp, vertical = 6.dp)) {
            MonoText(text, color = palette.phos, fontSize = 14.sp, fontFamily = LocalDisplayFont.current)
        }
    }
}

private fun resultBanner(result: BattleResult): String = when (result) {
    BattleResult.Win -> "◉ ENGAGEMENT WON"
    BattleResult.Lose -> "✟ KO — UNIT DOWN"
    BattleResult.Draw -> "◯ STALEMATE"
    BattleResult.Flee -> "↩ DISENGAGED"
}
