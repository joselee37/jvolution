package today.superb.jvl.ui.breed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.genetics.InbreedingRisk
import today.superb.jvl.core.genetics.classifyInbreeding
import today.superb.jvl.core.genetics.express
import today.superb.jvl.core.genetics.predictedInbreeding
import today.superb.jvl.ui.genome.fmtPercent
import today.superb.jvl.ui.genome.fmtTrait
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.roundToInt

/** F[0,0.25] → 10칸 게이지. */
private fun gauge(f: Double): String {
    val filled = (f / 0.25 * 10).roundToInt().coerceIn(0, 10)
    return "█".repeat(filled) + "░".repeat(10 - filled)
}

private fun riskLabel(r: InbreedingRisk): String = when (r) {
    InbreedingRisk.SAFE -> "✓ SAFE"
    InbreedingRisk.CLOSE -> "⚠ CLOSE"
    InbreedingRisk.INBRED -> "✕ INBRED"
}

/**
 * PAIR-BOND ASSAY 오버레이 — 현재 개체 × [peer] 교배 미리보기. 예측 근친계수 게이지/등급 +
 * 부모 형질 비교 + 확정/취소. PeerAlertOverlay와 같은 오버레이 규약(메인 베젤 위).
 */
@Composable
fun BreedAssayOverlay(
    state: GameState,
    peer: Peer,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val f = remember(state.genome, peer.genome, state.lineage, state.creatureId) { predictedInbreeding(state, peer.id) }
    val risk = classifyInbreeding(f)
    val selfStats = remember(state.genome) { express(state.genome).stats }
    val mateStats = remember(peer.genome) { express(peer.genome).stats }

    Box(
        modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.86f).border(2.dp, palette.phos).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonoText("◢◤ PAIR-BOND ASSAY ◢◤", color = palette.phos, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            MonoText(
                "G${state.gen.toString().padStart(2, '0')}_${state.name}  ×  ${peer.name}",
                fontSize = 20.sp, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            MonoText("predicted inbreeding", color = palette.phosDim, fontSize = 10.sp)
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonoText("F ${gauge(f)}", color = palette.phos, fontSize = 13.sp, fontFamily = LocalMonoFont.current)
                MonoText(fmtPercent(f), color = palette.phos, fontSize = 13.sp)
                MonoText(
                    riskLabel(risk),
                    color = if (risk == InbreedingRisk.SAFE) palette.phosMid else palette.phos,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            MonoText("parent traits         self │ mate", color = palette.phosDim, fontSize = 10.sp)
            traitRow("VIT", selfStats.vitality, mateStats.vitality)
            traitRow("MET", selfStats.metabolism, mateStats.metabolism)
            traitRow("RES", selfStats.resilience, mateStats.resilience)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                assayButton("▸ CONFIRM BREED", onConfirm, palette.phos)
                assayButton("✕ CANCEL", onCancel, palette.phosDim)
            }
        }
    }
}

@Composable
private fun traitRow(label: String, self: Float, mate: Float) {
    val palette = LocalPalette.current
    MonoText(
        "$label   ${fmtTrait(self)} │ ${fmtTrait(mate)}",
        color = palette.phos, fontSize = 12.sp, fontFamily = LocalMonoFont.current,
    )
}

@Composable
private fun assayButton(label: String, onClick: () -> Unit, color: Color) {
    Box(
        Modifier.border(1.dp, color).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        MonoText(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
