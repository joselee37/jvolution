package today.superb.jvl.ui.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.terminal.activeLineageEntry
import today.superb.jvl.nowMillis
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.max

/** 트리 한 줄 — dim(은퇴)/alive(현 세대 status) 색 구분, [gen]은 노드 헤더 줄만(탭 → `tree <gen>`). */
private data class TreeLine(val text: String, val dim: Boolean = false, val alive: Boolean = false, val gen: Int? = null)

/** 상대시간. 데모 `fmtTime` 1:1. */
private fun fmtTime(ms: Long, now: Long): String {
    if (ms == 0L) return "----"
    val s = max(0L, (now - ms) / 1000L)
    if (s < 60) return "${s}s ago"
    val m = s / 60
    if (m < 60) return "${m}m ago"
    return "${m / 60}h ${m % 60}m ago"
}

/**
 * 계보 화면 — 데모 `TreeScreen` 포팅. GENESIS/ 루트 아래 세대별 `Gnn_NAME/` 노드를 리눅스 tree
 * 스타일로 그린다. 현 세대는 `◀ ACTIVE` + 라이브 무드/유대, 은퇴 세대는 dim + `✟ retired` + 경과시간.
 * 노드 헤더 탭 = `tree <gen>` 매크로(세대 상세를 터미널에 출력 — App이 배선).
 */
@Composable
fun TreeScreen(state: GameState, onSelectGen: (Int) -> Unit = {}, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val now = nowMillis()
    val nodeCount = state.lineage.size + 1
    val totalCycles = state.lineage.sumOf { it.cycles } + state.cycles
    val lines = buildTreeLines(state, now)

    Column(modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoText("LINEAGE // ARCHIVE", color = palette.phos, fontSize = 10.sp)
            MonoText("NODES $nodeCount", color = palette.phosMid, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoText("ROOT GENESIS/", color = palette.phosMid, fontSize = 10.sp)
            MonoText("CYC ${totalCycles.toString().padStart(4, '0')}", color = palette.phosMid, fontSize = 10.sp)
        }
        MonoText("ANCESTRY", color = palette.phos, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current, modifier = Modifier.padding(top = 4.dp))

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            items(lines) { line ->
                MonoText(
                    text = line.text,
                    modifier = if (line.gen != null) {
                        Modifier.fillMaxWidth().clickable { onSelectGen(line.gen) }
                    } else {
                        Modifier
                    },
                    color = when {
                        line.alive -> palette.phos
                        line.dim -> palette.phosDim
                        else -> palette.phosMid
                    },
                    fontSize = 10.sp,
                    fontFamily = LocalMonoFont.current,
                )
            }
        }

        state.toast?.let { toast ->
            MonoText("▸ $toast", color = palette.phos, fontSize = 14.sp, fontFamily = LocalDisplayFont.current, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun buildTreeLines(state: GameState, now: Long): List<TreeLine> {
    val nodes = state.lineage.map { it to true } + (activeLineageEntry(state) to false)
    val totalCycles = state.lineage.sumOf { it.cycles } + state.cycles
    val out = ArrayList<TreeLine>()
    out += TreeLine("$ tree GENESIS/")
    out += TreeLine("GENESIS/")

    nodes.forEachIndexed { i, (e, retired) ->
        val last = i == nodes.lastIndex
        val branch = if (last) "└── " else "├── "
        val cont = if (last) "    " else "│   "
        val activeTag = if (retired) "" else "  ◀ ACTIVE"
        out += TreeLine("$branch" + "G${e.gen.toString().padStart(2, '0')}_${e.name}/$activeTag", dim = retired, gen = e.gen)
        out += field(cont, "stage     ", e.stage.name.lowercase(), retired)
        out += field(cont, "cycles    ", e.cycles.toString().padStart(4, '0'), retired)
        if (retired) {
            out += field(cont, "last-mood ", "${e.happiness}%", true)
            out += field(cont, "bond      ", "${e.bond}%", true)
            out += field(cont, "archived  ", fmtTime(e.archivedAt, now), true)
            out += field(cont, "status    ", "✟ retired", true, last = true)
        } else {
            out += field(cont, "happiness ", "${e.happiness}%", false)
            out += field(cont, "bond      ", "${e.bond}%", false)
            out += field(cont, "hatched   ", fmtTime(e.hatchedAt, now), false)
            out += field(cont, "status    ", "● alive", false, alive = true, last = true)
        }
        if (!last) out += TreeLine("│")
    }

    out += TreeLine("─────────────────────────────")
    out += TreeLine("${nodes.size} ${if (nodes.size == 1) "directory" else "directories"}, $totalCycles cycles total", dim = true)
    out += TreeLine("▸ tap a node for detail · type `sonar` to return", dim = true)
    return out
}

private fun field(cont: String, key: String, value: String, dim: Boolean, alive: Boolean = false, last: Boolean = false): TreeLine {
    val branch = if (last) "└── " else "├── "
    return TreeLine("$cont$branch$key$value", dim = dim, alive = alive)
}

