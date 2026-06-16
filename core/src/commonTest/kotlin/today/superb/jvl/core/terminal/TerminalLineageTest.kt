package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.PeerRoster
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.View
import today.superb.jvl.core.battle.BattleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun st(battle: BattleState? = null) =
    GameState.initial("MORSE", 0L, PeerRoster.makePeers(SeededRng(7L))).copy(battle = battle)

private fun reply(input: String, s: GameState = st()) = respond(parse(input), s, SeededRng(1L))

class TerminalLineageTest {

    @Test
    fun tree_switches_view_and_reports_archive() {
        val r = reply("tree")
        assertEquals(Action.SetView(View.Tree), r.action)
        assertTrue(r.lines.any { it.text == "$ tree GENESIS/" })
        assertTrue(r.lines.any { it.text.contains("0 retired generations + 1 active") })
    }

    @Test
    fun breed_emits_breed_signal() {
        val r = reply("breed lumen")
        assertIs<Action.Breed>(r.action)
        assertEquals("lumen", (r.action as Action.Breed).peerId)
        assertTrue(r.lines.any { it.text.contains("NEW EGG INCUBATING") })
        assertTrue(r.lines.any { it.text.contains("predicted inbreeding") })
    }

    @Test
    fun breed_unknown_peer_emits_no_action() {
        val r = reply("breed ghostling")
        assertNull(r.action)
        assertTrue(r.lines.any { it.text.contains("no peer named") })
    }

    @Test
    fun genome_switches_to_genome_view() {
        val r = reply("genome")
        assertEquals(Action.SetView(View.Genome), r.action)
        assertTrue(r.lines.any { it.text.contains("GENOME ASSAY") })
    }

    @Test
    fun tree_and_breed_locked_during_battle() {
        val s = st(battle = BattleState.start("hrrk"))
        assertNull(reply("tree", s).action)
        assertTrue(reply("tree", s).lines.first().text.contains("locked"))
        assertNull(reply("breed lumen", s).action)
    }
}
