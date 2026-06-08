package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 방위 a와 b의 최단 원형 차이(도), [0, 180]. */
private fun circDiff(a: Float, b: Float): Float {
    val d = ((a - b) % 360f + 540f) % 360f - 180f
    return if (d < 0) -d else d
}

class PeerRosterTest {

    @Test
    fun makes_seven_fixed_units_in_order() {
        val peers = PeerRoster.makePeers(SeededRng(42L))
        assertEquals(7, peers.size)
        assertEquals(
            listOf("lumen", "hrrk", "blink", "morrow", "sift", "arc9", "nimbus"),
            peers.map { it.id },
        )
        assertEquals("LUMEN-3", peers[0].name)
        assertEquals(Species.Jelly, peers[0].species)
        assertEquals(Stage.Juvenile, peers[0].stage)
        assertEquals(Personality.Gentle, peers[0].personality)
        assertEquals(Personality.Veteran, peers[3].personality) // MORROW
        assertEquals(Species.Squid, peers[5].species)           // ARC-9
    }

    @Test
    fun fresh_units_have_zero_relationship() {
        for (p in PeerRoster.makePeers(SeededRng(42L))) {
            assertEquals(0f, p.bond)
            assertEquals(0, p.battlesWon)
            assertEquals(0, p.battlesLost)
        }
    }

    @Test
    fun positions_and_velocities_within_demo_bounds() {
        for (p in PeerRoster.makePeers(SeededRng(7L))) {
            assertTrue(p.range in 0.32f..0.87f, "range ${p.range}")
            assertTrue(p.bearing in 0f..360f, "bearing ${p.bearing}")
            assertTrue(p.bearingVel in -1.1f..1.1f, "bearingVel ${p.bearingVel}")
            assertTrue(p.rangeVel in -0.002f..0.002f, "rangeVel ${p.rangeVel}")
            assertTrue(p.cooldown in 20f..60f, "cooldown ${p.cooldown}")
        }
    }

    @Test
    fun bearings_spread_around_evenly_within_15_degrees() {
        val peers = PeerRoster.makePeers(SeededRng(99L))
        val n = peers.size
        peers.forEachIndexed { i, p ->
            val base = i * 360f / n
            assertTrue(circDiff(p.bearing, base) <= 15f, "peer $i bearing ${p.bearing} vs base $base")
        }
    }

    @Test
    fun seeded_construction_is_deterministic() {
        assertEquals(PeerRoster.makePeers(SeededRng(42L)), PeerRoster.makePeers(SeededRng(42L)))
    }
}
