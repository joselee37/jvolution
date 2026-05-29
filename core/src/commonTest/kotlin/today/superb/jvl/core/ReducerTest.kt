package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val EPS = 1e-4f

/** 모든 reducer 검증은 시드 고정 RNG로 결정성을 보장(케어 액션은 RNG 미사용이지만 계약 유지). */
private val rng = SeededRng(42L)

/** 표준 초기 상태 — name/now 주입으로 결정성. */
private fun initial() = GameState.initial(name = "TEST", now = 0L)

class ReducerTest {

    @Test
    fun feed_lowers_hunger_and_logs() {
        val s = reduce(initial(), Action.Feed, rng)
        assertEquals(0.20f, s.hunger, EPS)        // 0.45 - 0.25
        assertEquals(0.65f, s.happiness, EPS)     // 0.6 + 0.05
        assertEquals(0.33f, s.dirty, EPS)         // 0.3 + 0.03
        assertEquals(1, s.cycles)
        assertEquals("NOM NOM", s.toast)
        assertEquals(1, s.log.size)
        assertEquals("FEED — ration dispensed", s.log.first().msg)
    }

    @Test
    fun feed_clamps_hunger_at_zero() {
        val nearlyFull = initial().copy(hunger = 0.1f)
        val s = reduce(nearlyFull, Action.Feed, rng)
        assertEquals(0f, s.hunger, EPS)           // clamp(0.1 - 0.25) = 0
    }

    @Test
    fun play_raises_happiness_and_bond() {
        val s = reduce(initial(), Action.Play, rng)
        assertEquals(0.8f, s.happiness, EPS)      // 0.6 + 0.2
        assertEquals(0.62f, s.energy, EPS)        // 0.7 - 0.08
        assertEquals(0.49f, s.hunger, EPS)        // 0.45 + 0.04
        assertEquals(0.44f, s.bond, EPS)          // 0.4 + 0.04
        assertEquals("YIPPEE", s.toast)
    }

    @Test
    fun clean_resets_dirty() {
        val s = reduce(initial(), Action.Clean, rng)
        assertEquals(0f, s.dirty, EPS)
        assertEquals(0.65f, s.happiness, EPS)
        assertEquals("TANK FLUSHED", s.toast)
    }

    @Test
    fun heal_clamps_energy_at_one() {
        val s = reduce(initial(), Action.Heal, rng)
        assertEquals(1f, s.energy, EPS)           // clamp(0.7 + 0.3) = 1
        assertEquals("PATCHED", s.toast)
    }

    @Test
    fun train_raises_training_and_evolve_progress() {
        val s = reduce(initial(), Action.Train, rng)
        assertEquals(0.25f, s.training, EPS)      // 0.1 + 0.15
        assertEquals(0.25f, s.discipline, EPS)    // 0.2 + 0.05
        assertEquals(0.04f, s.evolveProgress, EPS)
    }

    @Test
    fun discipline_lowers_happiness_and_bond() {
        val s = reduce(initial(), Action.Discipline, rng)
        assertEquals(0.3f, s.discipline, EPS)     // 0.2 + 0.1
        assertEquals(0.52f, s.happiness, EPS)     // 0.6 - 0.08
        assertEquals(0.38f, s.bond, EPS)          // 0.4 - 0.02
    }

    @Test
    fun ping_bumps_nonce_and_bond() {
        val s = reduce(initial(), Action.Ping, rng)
        assertEquals(1, s.pingNonce)
        assertEquals(0.43f, s.bond, EPS)          // 0.4 + 0.03
        assertEquals(1, s.cycles)
    }

    @Test
    fun sleep_toggles_awake_state() {
        val asleep = reduce(initial(), Action.Sleep, rng)
        assertTrue(asleep.asleep)
        assertEquals("GOOD NIGHT", asleep.toast)

        val awake = reduce(asleep, Action.Sleep, rng)
        assertFalse(awake.asleep)
        assertEquals("AWAKE", awake.toast)
    }

    @Test
    fun tick_awake_drifts_stats() {
        val s = reduce(initial(), Action.Tick(dt = 1f), rng)
        assertEquals(0.462f, s.hunger, EPS)       // 0.45 + 0.012
        assertEquals(0.308f, s.dirty, EPS)        // 0.3 + 0.008
        assertEquals(0.69f, s.energy, EPS)        // 0.7 - 0.01
        assertEquals(0.6f, s.happiness, EPS)      // unchanged: hunger/dirty below threshold
        assertEquals(0.005f, s.evolveProgress, EPS)
    }

    @Test
    fun tick_asleep_recovers_energy() {
        val s = reduce(initial().copy(asleep = true), Action.Tick(dt = 1f), rng)
        assertEquals(0.72f, s.energy, EPS)        // 0.7 + 0.02
        assertEquals(0.455f, s.hunger, EPS)       // 0.45 + 0.005
        assertEquals(0.3f, s.dirty, EPS)          // unchanged while asleep
    }

    @Test
    fun tick_awake_penalises_happiness_when_uncomfortable() {
        val hungry = initial().copy(hunger = 0.8f)
        val s = reduce(hungry, Action.Tick(dt = 1f), rng)
        assertEquals(0.585f, s.happiness, EPS)    // 0.6 - 0.015 (hunger > 0.7)
    }

    @Test
    fun tick_marks_can_evolve_when_progress_full() {
        val ready = initial().copy(evolveProgress = 0.999f)
        val s = reduce(ready, Action.Tick(dt = 1f), rng)
        assertTrue(s.canEvolve)                   // Egg can advance and progress >= 1
    }

    @Test
    fun evolve_sets_evolving_flag() {
        val s = reduce(initial(), Action.Evolve, rng)
        assertTrue(s.evolving)
        assertEquals("EVOLVING", s.toast)
    }

    @Test
    fun evolve_is_noop_at_final_stage() {
        val adult = initial().copy(stage = Stage.Adult)
        val s = reduce(adult, Action.Evolve, rng)
        assertSame(adult, s)                      // unchanged, no evolving flag
        assertFalse(s.evolving)
    }

    @Test
    fun evolve_complete_advances_stage() {
        val s = reduce(initial().copy(evolving = true), Action.EvolveComplete, rng)
        assertEquals(Stage.Larva, s.stage)
        assertEquals(0f, s.evolveProgress, EPS)
        assertFalse(s.evolving)
        assertEquals("→ LARVA", s.toast)
    }

    @Test
    fun rename_changes_name() {
        val s = reduce(initial(), Action.Rename("KRAKEN"), rng)
        assertEquals("KRAKEN", s.name)
        assertEquals("RENAME — KRAKEN", s.log.first().msg)
    }

    @Test
    fun set_view_switches_view() {
        val s = reduce(initial(), Action.SetView(View.Radar), rng)
        assertEquals(View.Radar, s.view)
    }

    @Test
    fun clear_toast_nulls_toast() {
        val withToast = reduce(initial(), Action.Feed, rng)
        val cleared = reduce(withToast, Action.ClearToast, rng)
        assertNull(cleared.toast)
    }

    @Test
    fun log_is_capped_at_twenty() {
        var s = initial()
        repeat(25) { s = reduce(s, Action.Feed, rng) }
        assertEquals(GameState.LOG_CAP, s.log.size)
    }

    @Test
    fun reduce_does_not_mutate_input() {
        val s = initial()
        val next = reduce(s, Action.Feed, rng)
        assertNotSame(s, next)
        assertEquals(0.45f, s.hunger, EPS)        // original untouched
        assertTrue(s.log.isEmpty())
    }
}
