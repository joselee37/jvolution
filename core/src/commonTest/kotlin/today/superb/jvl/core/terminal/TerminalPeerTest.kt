package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.PeerRequest
import today.superb.jvl.core.Personality
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun rng() = SeededRng(42L)

private fun peersFixture() = listOf(
    Peer("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle, bearing = 90f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0.8f, battlesWon = 2, battlesLost = 1, cooldown = 100f),
    Peer("hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive, bearing = 180f, range = 0.7f, bearingVel = 0f, rangeVel = 0f, bond = 0.1f, battlesWon = 0, battlesLost = 0, cooldown = 100f),
)

private fun state(view: View = View.Sonar, pending: PeerRequest? = null, dnd: Boolean = false) =
    GameState.initial("NAUTI", 0L, peersFixture()).copy(view = view, pendingRequest = pending, dnd = dnd)

private fun reply(input: String, s: GameState = state()) = respond(parse(input), s, rng())

class TerminalPeerTest {

    // ── scan / radar / peers ───────────────────────────────────

    @Test
    fun scan_switches_to_radar_and_lists_contacts() {
        val r = reply("scan")
        assertEquals(Action.SetView(View.Radar), r.action)
        assertTrue(r.lines.any { it.text == "$ scan --peers @ 14.2kHz" })
        assertTrue(r.lines.any { it.text == "▸ 2 contacts detected." })
        assertTrue(r.lines.any { it.text.contains("LUMEN-3") && it.text.contains("rng 25m") })
        assertTrue(r.lines.any { it.text.contains("radar scope active") })
    }

    @Test
    fun scan_aliases_peers_and_radar() {
        assertEquals(Action.SetView(View.Radar), reply("peers").action)
        assertEquals(Action.SetView(View.Radar), reply("radar").action)
    }

    @Test
    fun scan_sorts_contacts_by_range() {
        val rows = reply("scan").lines.map { it.text }.filter { it.contains("brg") }
        val lumenIdx = rows.indexOfFirst { it.contains("LUMEN-3") }  // range 0.5
        val hrrkIdx = rows.indexOfFirst { it.contains("HRRK") }      // range 0.7
        assertTrue(lumenIdx in 0 until hrrkIdx)
    }

    @Test
    fun scan_on_radar_keeps_view_and_says_continuing() {
        val r = reply("scan", state(view = View.Radar))
        assertNull(r.action)
        assertTrue(r.lines.any { it.text.contains("sweep continuing") })
    }

    // ── sonar / back ───────────────────────────────────────────

    @Test
    fun sonar_returns_from_radar() {
        assertEquals(Action.SetView(View.Sonar), reply("sonar", state(view = View.Radar)).action)
        assertEquals(Action.SetView(View.Sonar), reply("back", state(view = View.Radar)).action)
    }

    @Test
    fun sonar_when_already_on_sonar_is_noop() {
        val r = reply("sonar", state(view = View.Sonar))
        assertNull(r.action)
        assertTrue(r.lines.any { it.text == "▸ already on sonar." })
    }

    // ── bond <name> ────────────────────────────────────────────

    @Test
    fun bond_shows_relationship_and_breed_eligibility() {
        val r = reply("bond lumen")
        assertNull(r.action)
        assertTrue(r.lines.any { it.text == "LUMEN-3 — jelly · juvenile · gentle" })
        assertTrue(r.lines.any { it.text == "  record   2W / 1L" })
        assertTrue(r.lines.any { it.text.contains("◀ BREED-ELIGIBLE") })   // bond 0.8 ≥ 0.7
    }

    @Test
    fun bond_below_threshold_shows_requirement() {
        assertTrue(reply("bond hrrk").lines.any { it.text.contains("bond ≥ 70% required") })
    }

    @Test
    fun bond_unknown_peer_errors() {
        assertTrue(reply("bond zzz").lines.first().text == "no peer named zzz.")
    }

    @Test
    fun bond_without_arg_shows_usage() {
        assertTrue(reply("bond").lines.first().text == "usage: bond <name>")
    }

    // ── accept / decline ───────────────────────────────────────

    @Test
    fun accept_with_request_starts_battle() {
        val r = reply("accept", state(pending = PeerRequest("hrrk", RequestType.Challenge)))
        assertEquals(Action.BattleStart("hrrk"), r.action)
        assertTrue(r.lines.any { it.text.contains("accepted HRRK") })
    }

    @Test
    fun accept_without_request_reports_none() {
        val r = reply("accept")
        assertNull(r.action)
        assertTrue(r.lines.any { it.text == "no incoming request." })
    }

    @Test
    fun decline_with_request_dispatches_decline() {
        val r = reply("decline", state(pending = PeerRequest("hrrk", RequestType.Challenge)))
        assertEquals(Action.DeclineRequest, r.action)
    }

    @Test
    fun decline_without_request_reports_none() {
        assertNull(reply("decline").action)
    }

    // ── dnd [on|off] ───────────────────────────────────────────

    @Test
    fun dnd_no_arg_toggles_on() {
        val r = reply("dnd", state(dnd = false))
        assertEquals(Action.SetDnd(true), r.action)
        assertTrue(r.lines.any { it.text.contains("dnd ON") })
    }

    @Test
    fun dnd_explicit_off() {
        assertEquals(Action.SetDnd(false), reply("dnd off", state(dnd = true)).action)
    }

    @Test
    fun dnd_no_change_just_echoes() {
        val r = reply("dnd off", state(dnd = false))
        assertNull(r.action)
        assertTrue(r.lines.any { it.text == "▸ dnd already off." })
    }

    @Test
    fun dnd_bad_arg_shows_usage() {
        assertTrue(reply("dnd maybe").lines.first().text == "usage: dnd [on|off]")
    }

    @Test
    fun dnd_on_with_pending_challenge_notes_auto_decline() {
        val r = reply("dnd on", state(pending = PeerRequest("hrrk", RequestType.Challenge)))
        assertEquals(Action.SetDnd(true), r.action)
        assertTrue(r.lines.any { it.text.contains("pending challenge from HRRK declined") })
    }
}
