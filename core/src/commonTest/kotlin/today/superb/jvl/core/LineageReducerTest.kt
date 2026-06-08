package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals

private const val EPS = 1e-4f

private fun peerL() = Peer("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle, bearing = 90f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0.6f, battlesWon = 1, battlesLost = 2, cooldown = 100f)

private fun lived() = GameState.initial("MORSE", now = 500L, peers = listOf(peerL())).copy(
    gen = 1, stage = Stage.Larva, cycles = 7,
    happiness = 0.9f, energy = 0.8f, bond = 0.5f, discipline = 0.3f, training = 0.2f,
)

class LineageReducerTest {

    @Test
    fun reset_archives_current_creature_as_epitaph() {
        val next = reduce(lived(), Action.Reset(newName = "KAIJU", now = 1000L), SeededRng(1L))
        assertEquals(1, next.lineage.size)
        val e = next.lineage.first()
        assertEquals(1, e.gen)
        assertEquals("MORSE", e.name)
        assertEquals(Stage.Larva, e.stage)
        assertEquals(7, e.cycles)
        assertEquals(90, e.happiness)        // round(0.9*100)
        assertEquals(80, e.energy)
        assertEquals(50, e.bond)
        assertEquals(30, e.discipline)
        assertEquals(20, e.training)
        assertEquals(500L, e.hatchedAt)      // preserved from old creature
        assertEquals(1000L, e.archivedAt)    // injected now
    }

    @Test
    fun reset_starts_fresh_next_generation() {
        val next = reduce(lived(), Action.Reset(newName = "KAIJU", now = 1000L), SeededRng(1L))
        assertEquals(2, next.gen)
        assertEquals("KAIJU", next.name)
        assertEquals(Stage.Egg, next.stage)
        assertEquals(0, next.cycles)
        assertEquals(0.6f, next.happiness, EPS)   // back to initial default
        assertEquals(0.7f, next.energy, EPS)
        assertEquals(1000L, next.hatchedAt)
    }

    @Test
    fun reset_preserves_peers_and_records() {
        val s = lived().copy(pendingRequest = PeerRequest("lumen", RequestType.Challenge), peerEventNonce = 3)
        val next = reduce(s, Action.Reset(newName = "BLEEP", now = 1000L), SeededRng(1L))
        // peers + relationships independent of the creature → unchanged
        assertEquals(s.peers, next.peers)
        assertEquals(0.6f, next.peers.first().bond, EPS)
        assertEquals(PeerRequest("lumen", RequestType.Challenge), next.pendingRequest)
        assertEquals(3, next.peerEventNonce)
    }

    @Test
    fun reset_accumulates_generations() {
        val g2 = reduce(lived(), Action.Reset("KAIJU", 1000L), SeededRng(1L))
        val g3 = reduce(g2, Action.Reset("BLEEP", 2000L), SeededRng(1L))
        assertEquals(3, g3.gen)
        assertEquals(2, g3.lineage.size)
        assertEquals(1, g3.lineage[0].gen)
        assertEquals(2, g3.lineage[1].gen)
        assertEquals("KAIJU", g3.lineage[1].name)
    }

    @Test
    fun set_view_tree_switches_view() {
        val next = reduce(lived(), Action.SetView(View.Tree), SeededRng(1L))
        assertEquals(View.Tree, next.view)
    }

    @Test
    fun reset_does_not_mutate_input() {
        val s = lived()
        reduce(s, Action.Reset("KAIJU", 1000L), SeededRng(1L))
        assertEquals(1, s.gen)               // original untouched
        assertEquals(0, s.lineage.size)
    }
}
