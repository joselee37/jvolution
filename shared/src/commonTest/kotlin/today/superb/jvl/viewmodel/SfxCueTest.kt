package today.superb.jvl.viewmodel

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleState
import today.superb.jvl.core.reduce
import today.superb.jvl.sound.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SfxCueTest {

    private val rng = SeededRng(42L)
    private val base = GameState.initial("UNIT", 0L)

    /** reduce를 실제로 돌려 before/after를 만들고 cue를 판정 — reducer와 매핑의 정합 보장. */
    private fun cue(state: GameState, action: Action): Sfx? =
        sfxCueFor(action, state, reduce(state, action, rng))

    @Test
    fun care_actions_play_care() {
        assertEquals(Sfx.Care, cue(base, Action.Feed))
        assertEquals(Sfx.Care, cue(base, Action.Play))
        assertEquals(Sfx.Care, cue(base, Action.Clean))
        assertEquals(Sfx.Care, cue(base, Action.Heal))
        assertEquals(Sfx.Care, cue(base, Action.Train))
    }

    @Test
    fun ping_scold_sleep_wake_map_to_their_cues() {
        assertEquals(Sfx.Ping, cue(base, Action.Ping))
        assertEquals(Sfx.Scold, cue(base, Action.Discipline))
        assertEquals(Sfx.SleepCue, cue(base, Action.Sleep))
        val asleep = reduce(base, Action.Sleep, rng)
        assertEquals(Sfx.WakeCue, cue(asleep, Action.Sleep))
    }

    @Test
    fun evolve_transition_and_completion() {
        val evolving = reduce(base, Action.Evolve, rng)
        assertEquals(Sfx.Evolve, sfxCueFor(Action.Evolve, base, evolving))
        // 이미 evolving이면 추가 cue 없음.
        assertNull(sfxCueFor(Action.Evolve, evolving, reduce(evolving, Action.Evolve, rng)))

        val done = reduce(evolving, Action.EvolveComplete, rng)
        assertEquals(Sfx.EvolveDone, sfxCueFor(Action.EvolveComplete, evolving, done))
    }

    @Test
    fun toggle_sound_confirms_only_when_turning_on() {
        val on = reduce(base, Action.ToggleSound, rng)
        assertEquals(Sfx.Confirm, sfxCueFor(Action.ToggleSound, base, on))
        val off = reduce(on, Action.ToggleSound, rng)
        assertNull(sfxCueFor(Action.ToggleSound, on, off), "끌 때는 무음")
    }

    @Test
    fun tick_and_view_changes_are_silent() {
        assertNull(cue(base, Action.Tick(1f)))
        assertNull(cue(base, Action.ClearToast))
    }

    @Test
    fun battle_apply_damage_terminal_results_map_to_cues() {
        val foe = Peer("hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
            bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f,
            bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f)
        val b = BattleState.start("hrrk").copy(
            phase = BattlePhase.Damage,
            hpThem = 1f, lastDmgThem = 1.5f,
            hpMe = 5f, lastDmgMe = 0f,
        )
        val before = GameState.initial("UNIT", 0L, listOf(foe)).copy(battle = b)
        val after = reduce(before, Action.BattleApplyDamage, rng)
        assertEquals(Sfx.Win, sfxCueFor(Action.BattleApplyDamage, before, after))
    }

    @Test
    fun battle_resolve_emits_hit_when_damage_landed_and_null_on_double_miss() {
        val foe = Peer("hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
            bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f,
            bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f)
        // 데미지가 실린 Reveal 상태 → Hit/Crit 중 하나.
        val hitB = BattleState.start("hrrk").copy(phase = BattlePhase.Reveal, lastDmgThem = 1.2f)
        val hitBefore = GameState.initial("UNIT", 0L, listOf(foe)).copy(battle = hitB)
        val hitAfter = reduce(hitBefore, Action.BattleResolve, rng)
        val cue = sfxCueFor(Action.BattleResolve, hitBefore, hitAfter)
        assertTrue(cue == Sfx.Hit || cue == Sfx.Crit, "데미지 발생 시 타격음")

        // 양측 빗나감 → 무음.
        val missB = BattleState.start("hrrk").copy(phase = BattlePhase.Reveal, lastDmgMe = 0f, lastDmgThem = 0f)
        val missBefore = GameState.initial("UNIT", 0L, listOf(foe)).copy(battle = missB)
        val missAfter = reduce(missBefore, Action.BattleResolve, rng)
        assertNull(sfxCueFor(Action.BattleResolve, missBefore, missAfter), "양측 빗나감은 무음")
    }
}
