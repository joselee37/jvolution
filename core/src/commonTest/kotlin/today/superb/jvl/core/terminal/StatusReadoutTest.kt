package today.superb.jvl.core.terminal

import today.superb.jvl.core.GameState
import today.superb.jvl.core.Stage
import kotlin.test.Test
import kotlin.test.assertTrue

private fun state() = GameState.initial(name = "NAUTI", now = 0L)

class StatusReadoutTest {

    @Test
    fun renders_unit_name_and_stage() {
        val lines = renderStatus(state())
        assertTrue(lines.any { it == "  name        NAUTI" })
        assertTrue(lines.any { it.contains("EGG") && it.contains("gen 01") })
    }

    @Test
    fun fed_is_inverse_of_hunger() {
        // hunger 0.45 → fed 55% = "█" * round(0.55*12)=7 filled
        val lines = renderStatus(state())
        val fed = lines.first { it.trimStart().startsWith("fed") }
        assertTrue(fed.contains(" 55%"))
    }

    @Test
    fun sleeping_marker_appears_when_asleep() {
        val awake = renderStatus(state())
        assertTrue(awake.none { it.contains("[sleeping]") })
        val asleep = renderStatus(state().copy(asleep = true))
        assertTrue(asleep.any { it.contains("[sleeping]") })
    }

    @Test
    fun ready_marker_appears_when_can_evolve() {
        val ready = renderStatus(state().copy(canEvolve = true))
        assertTrue(ready.any { it.contains("◀ READY") })
    }

    @Test
    fun full_bar_for_max_value() {
        val maxed = renderStatus(state().copy(happiness = 1f, stage = Stage.Adult))
        val happiness = maxed.first { it.trimStart().startsWith("happiness") }
        assertTrue(happiness.contains("████████████") && happiness.contains("100%"))
    }
}
