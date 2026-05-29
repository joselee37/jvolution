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
    fun scolded_reachable_in_isolation() {
        // disciplineFlash는 어떤 reducer 분기도 켜지 않아 1차에서 도달 불가(데모 버그 보존).
        // 분기 자체는 격리 시 도달함을 문서화 — 2차 재설계 시 회귀 가드.
        assertEquals(Mood.SCOLDED, moodLabel(base().copy(disciplineFlash = true)))
    }

    @Test
    fun distressed_when_dirty_above_threshold() {
        assertEquals(Mood.DISTRESSED, moodLabel(base().copy(dirty = 0.71f)))
    }

    @Test
    fun distressed_outranks_hungry() {
        // dirty>0.7 와 hunger>0.75 동시 → DISTRESSED 우선(우선순위 4 > 5).
        assertEquals(Mood.DISTRESSED, moodLabel(base().copy(dirty = 0.8f, hunger = 0.9f)))
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
