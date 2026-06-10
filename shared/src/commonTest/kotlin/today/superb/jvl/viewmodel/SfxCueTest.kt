package today.superb.jvl.viewmodel

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.reduce
import today.superb.jvl.sound.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
