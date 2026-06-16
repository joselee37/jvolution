package today.superb.jvl.ui.genome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.genetics.AllelePair
import today.superb.jvl.core.genetics.Loci
import today.superb.jvl.core.genetics.express
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette

private const val DIVIDER = "────────────────────────────────────────"

/**
 * GENOME ASSAY 화면(Helix 레이아웃) — 16좌위×2대립유전자 매트릭스 + express() 형질값 + 혈통 푸터.
 * 읽기 전용. 이형접합(mat≠pat) 좌위는 [palette.phos]로 강조, 동형접합은 [phosMid].
 * 도메인 그룹(AP=외형 6 / ST=스탯 6 / BH=행동 4)은 여백으로 구분.
 */
@Composable
fun GenomeScreen(state: GameState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val genome = state.genome
    val pheno = remember(genome) { express(genome) }
    val f = remember(genome, state.motherId, state.fatherId, state.lineage) { selfInbreeding(state) }

    fun alleleRow(pick: (AllelePair) -> Int): AnnotatedString = buildAnnotatedString {
        Loci.ALL.forEachIndexed { i, locus ->
            if (i == 6 || i == 12) append("  ")   // 도메인 그룹 구분 여백
            val pair = genome.alleles.getOrNull(i)
            val value = if (pair != null) pick(pair) else (locus.min + locus.max) / 2
            val het = pair != null && pair.maternal != pair.paternal
            withStyle(SpanStyle(color = if (het) palette.phos else palette.phosMid)) {
                append(value.toString().padStart(2))
            }
            append(" ")
        }
    }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoText(
                "◢◤ GENOME // G${state.gen.toString().padStart(2, '0')}_${state.name} ◢◤",
                fontSize = 15.sp, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.Bold,
            )
            MonoText(
                "F ${fmtPercent(f)}",
                fontSize = 15.sp, fontFamily = LocalDisplayFont.current,
                color = if (f >= 0.125) palette.phos else palette.phosMid,
            )
        }
        Spacer(Modifier.height(8.dp))
        MonoText("locus    AP·appearance·6   ST·stats·6   BH·behavior·4", color = palette.phosDim, fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            MonoText("mat ▏ ", color = palette.phosDim, fontSize = 12.sp, fontFamily = LocalMonoFont.current)
            Text(alleleRow { it.maternal }, color = palette.phosDim, fontSize = 12.sp, fontFamily = LocalMonoFont.current)
        }
        Row {
            MonoText("pat ▏ ", color = palette.phosDim, fontSize = 12.sp, fontFamily = LocalMonoFont.current)
            Text(alleleRow { it.paternal }, color = palette.phosDim, fontSize = 12.sp, fontFamily = LocalMonoFont.current)
        }
        Spacer(Modifier.height(6.dp))
        MonoText(DIVIDER, color = palette.phosDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        MonoText(
            "VIT ${fmtTrait(pheno.stats.vitality)}   MET ${fmtTrait(pheno.stats.metabolism)}   RES ${fmtTrait(pheno.stats.resilience)}",
            fontSize = 13.sp, fontFamily = LocalMonoFont.current,
        )
        MonoText(
            "AGG ${fmtTrait(pheno.behavior.aggression)}   SOC ${fmtTrait(pheno.behavior.sociability)}   " +
                "BLD ${fmtTrait(pheno.behavior.boldness)}   TMP ${fmtTrait(pheno.behavior.tempo)}",
            fontSize = 13.sp, fontFamily = LocalMonoFont.current,
        )
        Spacer(Modifier.height(6.dp))
        MonoText(DIVIDER, color = palette.phosDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        MonoText("line  ${lineageSpine(state)}", color = palette.phosMid, fontSize = 11.sp)
        if (state.motherId != null && state.fatherId != null) {
            MonoText(
                "✚ ${displayName(state, state.motherId)} × ${displayName(state, state.fatherId)}",
                color = palette.phosMid, fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        MonoText("▸ type `sonar` to return", color = palette.phosDim, fontSize = 9.sp)
    }
}
