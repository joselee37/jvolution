package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.FixedRng
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.battle.BattleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun foe(id: String, name: String, personality: Personality) =
    Peer(id, name, Species.Squid, Stage.Adult, personality, bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f)

private fun base(battle: BattleState? = null) =
    GameState.initial("NAUTI", 0L, listOf(foe("hrrk", "HRRK", Personality.Aggressive), foe("nimbus", "NIMBUS", Personality.Gentle)))
        .copy(battle = battle)

class TerminalBattleTest {

    // ── challenge <name> (accept odds) ─────────────────────────

    @Test
    fun challenge_accepted_starts_battle() {
        // aggressive odds 0.85; roll 0.0 < 0.85 → accept
        val r = respond(parse("challenge hrrk"), base(), FixedRng(listOf(0.0f)))
        assertEquals(Action.BattleStart("hrrk"), r.action)
        assertTrue(r.lines.any { it.text.contains("accepts. ENGAGE") })
    }

    @Test
    fun challenge_declined_when_roll_exceeds_odds() {
        // gentle odds 0.30; roll 0.9 ≥ 0.30 → decline
        val r = respond(parse("challenge nimbus"), base(), FixedRng(listOf(0.9f)))
        assertNull(r.action)
        assertTrue(r.lines.any { it.text.contains("drifts away") })
    }

    @Test
    fun challenge_unknown_peer_errors() {
        assertTrue(respond(parse("challenge zzz"), base(), SeededRng(1L)).lines.first().text == "no peer named zzz.")
    }

    @Test
    fun challenge_without_arg_shows_usage() {
        assertTrue(respond(parse("challenge"), base(), SeededRng(1L)).lines.first().text == "usage: challenge <name>")
    }

    // ── flee / forfeit ─────────────────────────────────────────

    @Test
    fun flee_in_battle_dispatches_flee() {
        val r = respond(parse("flee"), base(battle = BattleState.start("hrrk")), SeededRng(1L))
        assertEquals(Action.BattleFlee, r.action)
    }

    @Test
    fun forfeit_is_alias_for_flee() {
        assertEquals(Action.BattleFlee, respond(parse("forfeit"), base(battle = BattleState.start("hrrk")), SeededRng(1L)).action)
    }

    @Test
    fun flee_without_battle_reports_none() {
        val r = respond(parse("flee"), base(), SeededRng(1L))
        assertNull(r.action)
        assertTrue(r.lines.first().text.contains("no active engagement"))
    }

    // ── in-battle command lock ─────────────────────────────────

    @Test
    fun care_commands_locked_during_battle() {
        val r = respond(parse("feed"), base(battle = BattleState.start("hrrk")), SeededRng(1L))
        assertNull(r.action)
        assertTrue(r.lines.first().text.contains("locked — engagement in progress"))
    }

    @Test
    fun help_and_flee_allowed_during_battle() {
        val s = base(battle = BattleState.start("hrrk"))
        assertTrue(respond(parse("help"), s, SeededRng(1L)).lines.first().text == "AVAILABLE COMMANDS:")
        assertEquals(Action.BattleFlee, respond(parse("flee"), s, SeededRng(1L)).action)
    }

    @Test
    fun dnd_off_forced_on_during_battle() {
        val r = respond(parse("dnd off"), base(battle = BattleState.start("hrrk")), SeededRng(1L))
        assertNull(r.action)
        assertTrue(r.lines.any { it.text.contains("forced on while engaged") })
    }
}
