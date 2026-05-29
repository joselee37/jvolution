package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals

private fun base() = GameState.initial(name = "TEST", now = 0L)

class MoodTest {

    @Test
    fun nominal_for_healthy_creature() {
        assertEquals(Mood.NOMINAL, moodLabel(base()))
    }

    @Test
    fun asleep_has_highest_priority() {
        // asleep wins even when other distress conditions also hold
        val s = base().copy(asleep = true, hunger = 0.9f, dirty = 0.9f)
        assertEquals(Mood.ASLEEP, moodLabel(s))
    }

    @Test
    fun evolving_outranks_distress() {
        val s = base().copy(evolving = true, hunger = 0.9f)
        assertEquals(Mood.EVOLVING, moodLabel(s))
    }

    @Test
    fun distressed_when_dirty_above_threshold() {
        assertEquals(Mood.DISTRESSED, moodLabel(base().copy(dirty = 0.71f)))
    }

    @Test
    fun hungry_above_threshold() {
        assertEquals(Mood.HUNGRY, moodLabel(base().copy(hunger = 0.76f)))
    }

    @Test
    fun hunger_just_below_threshold_is_not_hungry() {
        // 0.75 boundary is exclusive — guards the PLAN checklist value
        assertEquals(Mood.NOMINAL, moodLabel(base().copy(hunger = 0.75f)))
    }

    @Test
    fun unhappy_below_threshold() {
        assertEquals(Mood.UNHAPPY, moodLabel(base().copy(happiness = 0.29f)))
    }

    @Test
    fun drowsy_when_low_energy() {
        assertEquals(Mood.DROWSY, moodLabel(base().copy(energy = 0.24f)))
    }
}
