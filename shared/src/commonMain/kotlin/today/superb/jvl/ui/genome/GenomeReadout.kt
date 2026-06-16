package today.superb.jvl.ui.genome

import today.superb.jvl.core.GameState
import today.superb.jvl.core.genetics.Kinship
import today.superb.jvl.core.genetics.pedigree
import kotlin.math.roundToInt

/**
 * GENOME/breed 화면용 순수 포맷 helper(Compose 무관 — :shared UI 로직, jvmTest 대상).
 */

/** [0,1] 형질값 → ".NN"(소수 2자리, 선행 0 생략). 1.0은 "1.0". KMP commonMain 포터블(String.format 없음). */
fun fmtTrait(f: Float): String {
    val n = (f.coerceIn(0f, 1f) * 100).roundToInt()
    return if (n >= 100) "1.0" else "." + n.toString().padStart(2, '0')
}

/** 근친계수 [0,1] → 정수 백분율 문자열("6%"). */
fun fmtPercent(f: Double): String = "${(f.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

/** 현재 개체의 자기 근친계수 F = f(부모). founder(부모 미상)는 0. */
fun selfInbreeding(state: GameState): Double =
    Kinship.inbreeding(state.motherId, state.fatherId, state.pedigree())

/** id → 표시 이름(조상 → 이름, 피어 → 이름, 그 외 raw id). null이면 "—". */
fun displayName(state: GameState, id: String?): String {
    if (id == null) return "—"
    state.lineage.ancestors.find { it.id == id }?.let { return it.name }
    state.peers.find { it.id == id }?.let { return it.name }
    return id
}

/** 본가 spine "G01▸G02▸G03"(현재 개체 포함). */
fun lineageSpine(state: GameState): String {
    val gens = (state.lineage.ancestors.filter { it.gen >= 1 }.map { it.gen } + state.gen)
        .distinct().sorted()
    return gens.joinToString("▸") { "G${it.toString().padStart(2, '0')}" }
}
