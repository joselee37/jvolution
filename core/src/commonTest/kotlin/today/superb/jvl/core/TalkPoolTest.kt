package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val rng = SeededRng(42L)
private fun state() = GameState.initial(name = "NAUTI", now = 0L)

class TalkPoolTest {

    @Test
    fun asleep_line_has_priority() {
        assertEquals("zzZZ... (do not disturb)", talkLine(state().copy(asleep = true), rng))
    }

    @Test
    fun hunger_line_when_starving() {
        assertEquals(
            "i hear something... is that food? i hope so.",
            talkLine(state().copy(hunger = 0.8f), rng),
        )
    }

    @Test
    fun egg_stage_line() {
        // healthy egg → muffled tapping
        assertEquals("tap... tap... tap... [muffled]", talkLine(state().copy(stage = Stage.Egg), rng))
    }

    @Test
    fun idle_line_is_deterministic_for_fixed_seed() {
        // Adult + healthy → idle pool, RNG-selected. Same seed → same line.
        val adult = state().copy(stage = Stage.Adult)
        val first = talkLine(adult, SeededRng(7L))
        val second = talkLine(adult, SeededRng(7L))
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }
}
