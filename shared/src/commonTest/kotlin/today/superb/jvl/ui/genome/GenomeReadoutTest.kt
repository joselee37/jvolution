package today.superb.jvl.ui.genome

import today.superb.jvl.core.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GENOME/breed 순수 포맷 helper 테스트(KMP 포터블 포맷·혈통 spine). */
class GenomeReadoutTest {

    @Test
    fun fmt_trait_two_decimals_no_leading_zero() {
        assertEquals(".78", fmtTrait(0.78f))
        assertEquals(".60", fmtTrait(0.6f))
        assertEquals(".05", fmtTrait(0.05f))
        assertEquals(".00", fmtTrait(0f))
        assertEquals("1.0", fmtTrait(1.0f))
        assertEquals("1.0", fmtTrait(1.5f))   // clamp
    }

    @Test
    fun fmt_percent_rounds_to_integer() {
        assertEquals("6%", fmtPercent(0.06))
        assertEquals("25%", fmtPercent(0.25))
        assertEquals("0%", fmtPercent(0.0))
        assertEquals("13%", fmtPercent(0.125))
    }

    @Test
    fun lineage_spine_includes_current_generation() {
        val s = GameState.initial("X", 0L).copy(gen = 3)
        // 빈 lineage라도 현재 gen은 포함.
        assertEquals("G03", lineageSpine(s))
    }

    @Test
    fun display_name_falls_back_to_id_then_dash() {
        val s = GameState.initial("X", 0L)
        assertEquals("—", displayName(s, null))
        assertEquals("unknown-id", displayName(s, "unknown-id"))
    }
}
