package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val EPS = 1e-4f

/**
 * 테스트마다 새 RNG — 공유 가변 인스턴스는 실행 순서 의존(flaky)을 부른다.
 * 케어 액션은 RNG 미사용이지만 계약 유지 + 격리 패턴 일관성.
 */
private fun rng() = SeededRng(42L)

/** 표준 초기 상태 — name/now 주입으로 결정성. */
private fun initial() = GameState.initial(name = "TEST", now = 0L)

class ReducerTest {

    @Test
    fun feed_lowers_hunger_and_logs() {
        val s = reduce(initial(), Action.Feed, rng())
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
        val s = reduce(nearlyFull, Action.Feed, rng())
        assertEquals(0f, s.hunger, EPS)           // clamp(0.1 - 0.25) = 0
    }

    @Test
    fun play_raises_happiness_and_bond() {
        val s = reduce(initial(), Action.Play, rng())
        assertEquals(0.8f, s.happiness, EPS)      // 0.6 + 0.2
        assertEquals(0.62f, s.energy, EPS)        // 0.7 - 0.08
        assertEquals(0.49f, s.hunger, EPS)        // 0.45 + 0.04
        assertEquals(0.44f, s.bond, EPS)          // 0.4 + 0.04
        assertEquals("YIPPEE", s.toast)
    }

    @Test
    fun clean_resets_dirty() {
        val s = reduce(initial(), Action.Clean, rng())
        assertEquals(0f, s.dirty, EPS)
        assertEquals(0.65f, s.happiness, EPS)
        assertEquals("TANK FLUSHED", s.toast)
    }

    @Test
    fun heal_clamps_energy_at_one() {
        val s = reduce(initial(), Action.Heal, rng())
        assertEquals(1f, s.energy, EPS)           // clamp(0.7 + 0.3) = 1
        assertEquals("PATCHED", s.toast)
    }

    @Test
    fun train_raises_training_and_evolve_progress() {
        val s = reduce(initial(), Action.Train, rng())
        assertEquals(0.25f, s.training, EPS)      // 0.1 + 0.15
        assertEquals(0.25f, s.discipline, EPS)    // 0.2 + 0.05
        assertEquals(0.62f, s.energy, EPS)        // 0.7 - 0.08
        assertEquals(0.49f, s.hunger, EPS)        // 0.45 + 0.04
        assertEquals(0.04f, s.evolveProgress, EPS)
        assertEquals("DRILL OK", s.toast)
    }

    @Test
    fun discipline_lowers_happiness_and_bond() {
        val s = reduce(initial(), Action.Discipline, rng())
        assertEquals(0.3f, s.discipline, EPS)     // 0.2 + 0.1
        assertEquals(0.52f, s.happiness, EPS)     // 0.6 - 0.08
        assertEquals(0.38f, s.bond, EPS)          // 0.4 - 0.02
        assertTrue(s.disciplineFlash)
    }

    @Test
    fun ping_bumps_nonce_and_bond() {
        val s = reduce(initial(), Action.Ping, rng())
        assertEquals(1, s.pingNonce)
        assertEquals(0.43f, s.bond, EPS)          // 0.4 + 0.03
        assertEquals(1, s.cycles)
    }

    @Test
    fun sleep_toggles_awake_state() {
        val asleep = reduce(initial(), Action.Sleep, rng())
        assertTrue(asleep.asleep)
        assertEquals("GOOD NIGHT", asleep.toast)

        val awake = reduce(asleep, Action.Sleep, rng())
        assertFalse(awake.asleep)
        assertEquals("AWAKE", awake.toast)
    }

    @Test
    fun tick_awake_drifts_stats() {
        val s = reduce(initial(), Action.Tick(dt = 1f), rng())
        assertEquals(0.462f, s.hunger, EPS)       // 0.45 + 0.012
        assertEquals(0.308f, s.dirty, EPS)        // 0.3 + 0.008
        assertEquals(0.69f, s.energy, EPS)        // 0.7 - 0.01
        assertEquals(0.6f, s.happiness, EPS)      // unchanged: hunger/dirty below threshold
        assertEquals(0.005f, s.evolveProgress, EPS)
    }

    @Test
    fun tick_asleep_recovers_energy() {
        val s = reduce(initial().copy(asleep = true), Action.Tick(dt = 1f), rng())
        assertEquals(0.72f, s.energy, EPS)        // 0.7 + 0.02
        assertEquals(0.455f, s.hunger, EPS)       // 0.45 + 0.005
        assertEquals(0.3f, s.dirty, EPS)          // unchanged while asleep
    }

    @Test
    fun tick_awake_penalises_happiness_when_uncomfortable() {
        val hungry = initial().copy(hunger = 0.8f)
        val s = reduce(hungry, Action.Tick(dt = 1f), rng())
        assertEquals(0.585f, s.happiness, EPS)    // 0.6 - 0.015 (hunger > 0.7)
    }

    @Test
    fun tick_marks_can_evolve_when_progress_full() {
        val ready = initial().copy(evolveProgress = 0.999f)
        val s = reduce(ready, Action.Tick(dt = 1f), rng())
        assertTrue(s.canEvolve)                   // Egg can advance and progress >= 1
    }

    @Test
    fun evolve_sets_evolving_flag() {
        val s = reduce(initial(), Action.Evolve, rng())
        assertTrue(s.evolving)
        assertEquals("EVOLVING", s.toast)
    }

    @Test
    fun evolve_is_noop_at_final_stage() {
        val adult = initial().copy(stage = Stage.Adult)
        val s = reduce(adult, Action.Evolve, rng())
        assertSame(adult, s)                      // unchanged, no evolving flag
        assertFalse(s.evolving)
    }

    @Test
    fun evolve_complete_advances_stage() {
        val s = reduce(initial().copy(evolving = true, evolveProgress = 1f, canEvolve = true), Action.EvolveComplete, rng())
        assertEquals(Stage.Larva, s.stage)
        assertEquals(0f, s.evolveProgress, EPS)
        assertFalse(s.evolving)
        assertFalse(s.canEvolve)
        assertEquals(1, s.cycles)                 // cycles incremented
        assertEquals("→ LARVA", s.toast)
        assertEquals("EVOLVE — LARVA stage", s.log.first().msg)
    }

    @Test
    fun rename_changes_name() {
        val s = reduce(initial(), Action.Rename("KRAKEN"), rng())
        assertEquals("KRAKEN", s.name)
        assertEquals("RENAME — KRAKEN", s.log.first().msg)
    }

    @Test
    fun set_view_switches_view_and_preserves_other_fields() {
        // NOTE: SetView는 2차 선반영 — 1차에서 dispatch되지 않음(Action.SetView KDoc).
        // reducer 분기의 정확성(view만 바꾸고 나머지 보존)만 확인한다.
        val before = initial().copy(hunger = 0.42f, cycles = 7)
        val s = reduce(before, Action.SetView(View.Radar), rng())
        assertEquals(View.Radar, s.view)
        assertEquals(before.copy(view = View.Radar), s)   // 그 외 모든 필드 보존
    }

    @Test
    fun clear_toast_nulls_toast_and_preserves_other_fields() {
        val withToast = reduce(initial(), Action.Feed, rng())
        val cleared = reduce(withToast, Action.ClearToast, rng())
        assertNull(cleared.toast)
        assertEquals(withToast.copy(toast = null), cleared)  // toast 외 보존(log/스탯 그대로)
    }

    @Test
    fun toggle_sound_flips_flag_and_toasts() {
        val on = reduce(initial(), Action.ToggleSound, rng())
        assertTrue(on.sound)
        assertEquals("SOUND ON", on.toast)
        val off = reduce(on, Action.ToggleSound, rng())
        assertFalse(off.sound)
        assertEquals("MUTED", off.toast)
    }

    @Test
    fun log_is_capped_at_twenty() {
        var s = initial()
        repeat(25) { s = reduce(s, Action.Feed, rng()) }
        assertEquals(GameState.LOG_CAP, s.log.size)
    }

    @Test
    fun reduce_does_not_mutate_input() {
        val s = initial()
        val next = reduce(s, Action.Feed, rng())
        assertNotSame(s, next)
        assertEquals(0.45f, s.hunger, EPS)        // original untouched
        assertTrue(s.log.isEmpty())
    }

    @Test
    fun discipline_turns_on_discipline_flash() {
        val s = reduce(initial(), Action.Discipline, rng())
        assertTrue(s.disciplineFlash, "Discipline은 disciplineFlash를 켠다")
    }

    @Test
    fun clear_discipline_flash_turns_it_off() {
        val flashed = reduce(initial(), Action.Discipline, rng())
        val cleared = reduce(flashed, Action.ClearDisciplineFlash, rng())
        assertFalse(cleared.disciplineFlash)
        // flash 외 다른 필드는 건드리지 않는다.
        assertEquals(flashed.copy(disciplineFlash = false), cleared)
    }

    @Test
    fun set_species_changes_species_quietly() {
        val before = initial()
        val s = reduce(before, Action.SetSpecies(Species.Squid), rng())
        assertEquals(before.copy(species = Species.Squid), s)  // species만 변경, 나머지 전체 보존(토스트/cycles 포함)
    }

    @Test
    fun breed_with_same_species_peer_keeps_species() {
        // 양쪽 부모가 같은 종이면 자식 종은 동전던지기와 무관하게 그 종으로 결정된다.
        val peers = PeerRoster.makePeers(SeededRng(1L))  // "hrrk"/"arc9" = Squid
        val squid = GameState.initial("UNIT", 0L, peers).copy(species = Species.Squid)
        val next = reduce(squid, Action.Breed(peerId = "hrrk", childName = "NEXT", childId = "c2", now = 99L), rng())
        assertEquals(Species.Squid, next.species, "양쪽 부모가 Squid면 새 알도 Squid")
        assertEquals(2, next.gen)
        assertEquals("NEXT", next.name)
    }
}
