package today.superb.jvl.core

import today.superb.jvl.core.genetics.breed
import kotlin.test.Test
import kotlin.test.assertEquals

private const val EPS = 1e-4f

private fun peerL() = Peer("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle, bearing = 90f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0.6f, battlesWon = 1, battlesLost = 2, cooldown = 100f)

private fun lived() = GameState.initial("MORSE", now = 500L, peers = listOf(peerL())).copy(
    gen = 1, stage = Stage.Larva, cycles = 7,
    happiness = 0.9f, energy = 0.8f, bond = 0.5f, discipline = 0.3f, training = 0.2f,
    creatureId = "g1",
)

/** 유성 교배(Action.Breed) — 현재 개체를 Ancestor로 아카이브하고 피어와 교배해 다음 세대를 시작한다. */
class LineageReducerTest {

    @Test
    fun breed_archives_current_creature_as_ancestor() {
        val next = reduce(lived(), Action.Breed("lumen", "KAIJU", "g2", now = 1000L), SeededRng(1L))
        // 본가 계보(gen>=1)에는 아카이브된 MORSE 한 명.
        val spine = next.lineage.ancestors.filter { it.gen >= 1 }
        assertEquals(1, spine.size)
        val e = spine.first()
        assertEquals(1, e.gen)
        assertEquals("MORSE", e.name)
        assertEquals("g1", e.id)
        assertEquals(Stage.Larva, e.stage)
        assertEquals(7, e.cycles)
        assertEquals(90, e.happiness)        // round(0.9*100)
        assertEquals(80, e.energy)
        assertEquals(50, e.bond)
        assertEquals(30, e.discipline)
        assertEquals(20, e.training)
        assertEquals(500L, e.hatchedAt)      // 옛 개체에서 보존
        assertEquals(1000L, e.archivedAt)    // 주입된 now
    }

    @Test
    fun breed_records_peer_co_parent_as_founder_once() {
        val g2 = reduce(lived(), Action.Breed("lumen", "KAIJU", "g2", now = 1000L), SeededRng(1L))
        val founders = g2.lineage.ancestors.filter { it.id == "lumen" }
        assertEquals(1, founders.size)
        assertEquals(0, founders.first().gen)            // 피어는 founder(gen=0)
        // 다시 같은 피어와 교배해도 중복 기록하지 않는다.
        val g3 = reduce(g2, Action.Breed("lumen", "BLEEP", "g3", now = 2000L), SeededRng(1L))
        assertEquals(1, g3.lineage.ancestors.count { it.id == "lumen" })
    }

    @Test
    fun breed_starts_next_generation_with_parents() {
        val next = reduce(lived(), Action.Breed("lumen", "KAIJU", "g2", now = 1000L), SeededRng(1L))
        assertEquals(2, next.gen)
        assertEquals("KAIJU", next.name)
        assertEquals("g2", next.creatureId)
        assertEquals("g1", next.motherId)     // 현 개체가 모계
        assertEquals("lumen", next.fatherId)  // 피어가 부계
        assertEquals(Stage.Egg, next.stage)
        assertEquals(0, next.cycles)
        assertEquals(1000L, next.hatchedAt)
    }

    @Test
    fun breed_child_genome_is_recombination_and_parents_archived() {
        val s = lived()
        val peer = s.peers.first { it.id == "lumen" }
        // reducer는 breed()를 먼저 호출하므로 같은 시드의 독립 breed가 자식 게놈을 재현한다.
        val expectedChild = breed(s.genome, peer.genome, SeededRng(1L))
        val next = reduce(s, Action.Breed("lumen", "KAIJU", "g2", 1000L), SeededRng(1L))
        assertEquals(expectedChild, next.genome, "자식 게놈 = breed(부모 게놈들)")
        assertEquals(s.genome, next.lineage.ancestors.first { it.id == "g1" }.genome, "아카이브 조상은 자기 게놈 보존")
        assertEquals(peer.genome, next.lineage.ancestors.first { it.id == "lumen" }.genome, "피어 founder는 피어 게놈 보존")
    }

    @Test
    fun breed_with_unknown_peer_is_noop() {
        val s = lived()
        val next = reduce(s, Action.Breed("nobody", "X", "gx", now = 1L), SeededRng(1L))
        assertEquals(s, next)
    }

    @Test
    fun breed_preserves_peers_and_records() {
        val s = lived().copy(
            pendingRequest = PeerRequest("lumen", RequestType.Challenge), peerEventNonce = 3,
            view = View.Tree,
        )
        val next = reduce(s, Action.Breed("lumen", "BLEEP", "g2", now = 1000L), SeededRng(1L))
        // peers + 관계 + 뷰는 생명체와 독립 → 보존.
        assertEquals(s.peers, next.peers)
        assertEquals(0.6f, next.peers.first().bond, EPS)
        assertEquals(PeerRequest("lumen", RequestType.Challenge), next.pendingRequest)
        assertEquals(3, next.peerEventNonce)
        assertEquals(View.Tree, next.view, "뷰 보존(initial의 Sonar 기본값과 구분)")
    }

    @Test
    fun breed_accumulates_generations() {
        val g2 = reduce(lived(), Action.Breed("lumen", "KAIJU", "g2", 1000L), SeededRng(1L))
        val g3 = reduce(g2, Action.Breed("lumen", "BLEEP", "g3", 2000L), SeededRng(1L))
        assertEquals(3, g3.gen)
        val spine = g3.lineage.ancestors.filter { it.gen >= 1 }
        assertEquals(2, spine.size)
        assertEquals(1, spine[0].gen)
        assertEquals(2, spine[1].gen)
        assertEquals("KAIJU", spine[1].name)
    }

    @Test
    fun set_view_tree_switches_view() {
        val next = reduce(lived(), Action.SetView(View.Tree), SeededRng(1L))
        assertEquals(View.Tree, next.view)
    }

    @Test
    fun breed_does_not_mutate_input() {
        val s = lived()
        reduce(s, Action.Breed("lumen", "KAIJU", "g2", 1000L), SeededRng(1L))
        assertEquals(1, s.gen)                       // 원본 불변
        assertEquals(0, s.lineage.ancestors.size)
    }
}
