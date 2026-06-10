package today.superb.jvl.core.terminal

import today.superb.jvl.core.GameState
import today.superb.jvl.core.LineageEntry

/**
 * `tree <gen>` 세대 상세의 순수 텍스트 렌더. [renderStatus]/[renderBond]와 같은 규약 —
 * 색/레이아웃 없이 `List<String>`만 반환. 시각은 표시하지 않는다(respond는 wall-clock이 없음 —
 * 상대시간은 트리 화면 몫).
 */

/** 현 세대를 [LineageEntry] 모양으로 어댑트(트리 렌더·상세 조회 공용). archivedAt=0 = 현역. */
fun activeLineageEntry(state: GameState): LineageEntry = LineageEntry(
    gen = state.gen,
    name = state.name,
    stage = state.stage,
    cycles = state.cycles,
    // 스탯 ×100 truncation(toInt) — TreeScreen.activeOf와 동일(후속 통합 시 함께 변경할 것).
    happiness = (state.happiness * 100).toInt(),
    energy = (state.energy * 100).toInt(),
    bond = (state.bond * 100).toInt(),
    discipline = (state.discipline * 100).toInt(),
    training = (state.training * 100).toInt(),
    hatchedAt = state.hatchedAt,
    archivedAt = 0L,
)

/** 한 세대의 상세 readout. [active]면 현역 표기. */
fun renderGeneration(entry: LineageEntry, active: Boolean): List<String> = listOf(
    "▸ G${entry.gen.toString().padStart(2, '0')}_${entry.name} — ${entry.stage.name.lowercase()}",
    "  cycles      ${entry.cycles.toString().padStart(4, '0')}",
    "  happiness   ${entry.happiness}%",
    "  energy      ${entry.energy}%",
    "  bond        ${entry.bond}%",
    "  discipline  ${entry.discipline}%",
    "  training    ${entry.training}%",
    "  status      ${if (active) "● active" else "✟ retired"}",
)
