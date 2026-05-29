package today.superb.jvl.core.terminal

import today.superb.jvl.core.GameState
import kotlin.math.roundToInt

private const val BAR_WIDTH = 12

/** 0..1 값을 채움/빈칸 막대로. 데모 `bar(v, w)` 1:1. */
private fun bar(value: Float, width: Int = BAR_WIDTH): String {
    val filled = (value.coerceIn(0f, 1f) * width).roundToInt()
    return "█".repeat(filled) + "░".repeat(width - filled)
}

/** 0..1 값을 우측정렬 3자리 퍼센트로. 데모 `pct(v)` 1:1. */
private fun pct(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100).roundToInt().toString().padStart(3, ' ')}%"

/**
 * `status` 명령의 박스형 readout — 순수 문자열만 반환(색/레이아웃은 :shared가 입힘).
 * 데모 `screens.jsx:549` status 케이스 1:1.
 */
fun renderStatus(state: GameState): List<String> = buildList {
    add("─── UNIT ──────────────────────────────")
    add("  name        ${state.name}")
    add("  stage       ${state.stage.name.uppercase()}  · gen ${state.gen.toString().padStart(2, '0')}")
    add("  cycles      ${state.cycles.toString().padStart(4, '0')}" + if (state.asleep) "   [sleeping]" else "")
    add("")
    add("─── MOOD ──────────────────────────────")
    add("  happiness   ${bar(state.happiness)}  ${pct(state.happiness)}")
    add("  energy      ${bar(state.energy)}  ${pct(state.energy)}")
    add("")
    add("─── CARE ──────────────────────────────")
    add("  fed         ${bar(1f - state.hunger)}  ${pct(1f - state.hunger)}")
    add("  clean       ${bar(1f - state.dirty)}  ${pct(1f - state.dirty)}")
    add("")
    add("─── TRAINING ──────────────────────────")
    add("  bond        ${bar(state.bond)}  ${pct(state.bond)}")
    add("  training    ${bar(state.training)}  ${pct(state.training)}")
    add("  discipline  ${bar(state.discipline)}  ${pct(state.discipline)}")
    add("")
    add("─── EVOLUTION ─────────────────────────")
    add("  progress    ${bar(state.evolveProgress)}  ${pct(state.evolveProgress)}" + if (state.canEvolve) "   ◀ READY" else "")
}
