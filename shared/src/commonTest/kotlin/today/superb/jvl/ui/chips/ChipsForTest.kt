package today.superb.jvl.ui.chips

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
import today.superb.jvl.core.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipsForTest {

    private val rng = SeededRng(42L)
    private val base = GameState.initial("UNIT", 0L)

    private fun labels(state: GameState, selected: Peer? = null) =
        chipsFor(state, selected).map { it.label }

    @Test
    fun sonar_default_offers_care_and_nav() {
        assertEquals(
            listOf("FEED", "PLAY", "CLEAN", "TRAIN", "HEAL", "SLEEP", "SCOLD", "RADAR", "TREE"),
            labels(base),
        )
    }

    @Test
    fun asleep_puts_wake_first_and_drops_sleep() {
        val asleep = reduce(base, Action.Sleep, rng)
        val l = labels(asleep)
        assertEquals("WAKE", l.first())
        assertTrue("SLEEP" !in l)
    }

    @Test
    fun can_evolve_prepends_highlighted_evolve() {
        val ready = base.copy(canEvolve = true)
        val chips = chipsFor(ready)
        assertEquals("★EVOLVE", chips.first().label)
        assertEquals(ChipEmphasis.Highlight, chips.first().emphasis)
        assertEquals("evolve", chips.first().command)
    }

    @Test
    fun pending_request_prepends_accept_decline_alerts() {
        val pending = base.copy(pendingRequest = PeerRequest("p1", RequestType.Challenge))
        val chips = chipsFor(pending)
        assertEquals(listOf("ACCEPT", "DECLINE"), chips.take(2).map { it.label })
        assertTrue(chips.take(2).all { it.emphasis == ChipEmphasis.Alert })
    }

    @Test
    fun battle_locks_everything_but_flee() {
        val inBattle = reduce(
            base.copy(peers = listOf(peer("p1", "HRRK"))),
            Action.BattleStart("p1"),
            rng,
        )
        assertEquals(listOf("FLEE"), labels(inBattle))
    }

    @Test
    fun radar_adds_back_and_challenge_for_selection() {
        val onRadar = base.copy(view = View.Radar, peers = listOf(peer("p1", "HRRK")))
        val l = labels(onRadar, selected = onRadar.peers.first())
        assertEquals("BACK", l.first())
        assertTrue(l.any { it == "CHALLENGE HRRK" })
        val cmd = chipsFor(onRadar, onRadar.peers.first()).first { it.label == "CHALLENGE HRRK" }.command
        assertEquals("challenge hrrk", cmd)
    }

    @Test
    fun tree_adds_back() {
        assertEquals("BACK", labels(base.copy(view = View.Tree)).first())
    }

    private fun peer(id: String, name: String) = Peer(
        id, name, Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f,
        bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 99f,
    )
}
