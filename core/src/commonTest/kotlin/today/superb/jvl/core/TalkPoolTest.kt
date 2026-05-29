package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun rng() = SeededRng(42L)
private fun state() = GameState.initial(name = "NAUTI", now = 0L)

class TalkPoolTest {

    @Test
    fun asleep_line_has_priority() {
        assertEquals("zzZZ... (do not disturb)", talkLine(state().copy(asleep = true), rng()))
    }

    @Test
    fun hunger_line_when_starving() {
        assertEquals(
            "i hear something... is that food? i hope so.",
            talkLine(state().copy(hunger = 0.8f), rng()),
        )
    }

    @Test
    fun dirty_line_when_filthy() {
        assertEquals(
            "the water is murky. could you flush the tank?",
            talkLine(state().copy(dirty = 0.8f), rng()),
        )
    }

    @Test
    fun unhappy_line_when_sad() {
        assertEquals(
            "it has been a long shift. i miss you.",
            talkLine(state().copy(happiness = 0.2f), rng()),
        )
    }

    @Test
    fun tired_line_when_low_energy() {
        assertEquals(
            "tired... maybe a quick rest?",
            talkLine(state().copy(energy = 0.2f), rng()),
        )
    }

    @Test
    fun egg_stage_line() {
        // healthy egg → muffled tapping
        assertEquals("tap... tap... tap... [muffled]", talkLine(state().copy(stage = Stage.Egg), rng()))
    }

    @Test
    fun idle_line_is_from_pool_and_deterministic_for_fixed_seed() {
        // Adult + healthy → idle pool, RNG-selected. Same seed → same line, always in-pool.
        val adult = state().copy(stage = Stage.Adult)
        val first = talkLine(adult, SeededRng(7L))
        val second = talkLine(adult, SeededRng(7L))
        assertEquals(first, second)
        assertTrue(idleLineContains(first), "idle line must come from the pool: '$first'")
    }

    @Test
    fun idle_pool_indices_stay_in_bounds_across_many_draws() {
        // nextInt(bound) 계약 — 모든 추출이 풀 안에 있어야 함(인덱스 OOB 회귀 가드).
        val adult = state().copy(stage = Stage.Adult)
        val r = SeededRng(99L)
        repeat(200) { assertTrue(idleLineContains(talkLine(adult, r))) }
    }
}

/** IDLE_LINES는 TalkPool.kt의 private이라 알려진 풀 내용으로 검증. */
private fun idleLineContains(line: String): Boolean = line in setOf(
    "do you ever wonder where the signal goes?",
    "i counted 1,440 pings today. i counted them all.",
    "the reef hums at 27 hertz. i hum back.",
    "i think i saw a shape. it had nine sides.",
    "is the operator there? i sensed you above.",
    "thank you for staying.",
)
