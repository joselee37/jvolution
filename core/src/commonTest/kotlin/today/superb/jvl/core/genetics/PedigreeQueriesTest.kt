package today.superb.jvl.core.genetics

import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GameState.pedigree() 합성 + predictedInbreeding() 검증(설계 §6).
 *
 * 핵심: id 충돌 시 **조상 레코드가 peer founder를 이긴다**(부모 포함). 이 우선순위가 깨지면
 * 이전에 교배해 조상이 된 피어와 재교배할 때 근친이 0으로 잘못 보고된다.
 */
class PedigreeQueriesTest {

    private val delta = 1e-9

    private fun ancestor(id: String, gen: Int, mother: String?, father: String?) = Ancestor(
        id = id, gen = gen, name = id, species = Species.Ghost, stage = Stage.Adult,
        genome = Genome.default(), motherId = mother, fatherId = father,
        cycles = 0, happiness = 0, energy = 0, bond = 0, discipline = 0, training = 0,
        hatchedAt = 0L, archivedAt = 0L,
    )

    private fun peer(id: String) = Peer(
        id = id, name = id, species = Species.Jelly, stage = Stage.Adult, personality = Personality.Gentle,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 0f,
    )

    /** 현재 개체 self=(A,B); peer "sib"도 (A,B) → 전동기. "sib"는 ancestor + 라이브 피어로 동시에 존재. */
    private fun sibState() = GameState.initial("SELF", 0L, peers = listOf(peer("sib"))).copy(
        creatureId = "self", gen = 1, motherId = "A", fatherId = "B",
        lineage = Lineage(
            listOf(
                ancestor("A", 0, null, null),
                ancestor("B", 0, null, null),
                ancestor("sib", 1, "A", "B"),
            ),
        ),
    )

    @Test
    fun predicted_inbreeding_detects_full_sib_peer_via_ancestor_precedence() {
        // ancestor("sib", 부모 A,B)가 peer founder("sib", 부모 없음)를 이겨야 → 전동기 F=0.25.
        assertEquals(0.25, predictedInbreeding(sibState(), "sib"), delta)
    }

    @Test
    fun predicted_inbreeding_matches_kinship_of_parents() {
        val state = sibState()
        assertEquals(
            Kinship.coefficientOfKinship(state.creatureId, "sib", state.pedigree()),
            predictedInbreeding(state, "sib"),
            delta,
        )
    }

    @Test
    fun predicted_inbreeding_zero_for_unrelated_peer() {
        val state = GameState.initial("SELF", 0L, peers = listOf(peer("stranger"))).copy(
            creatureId = "self", gen = 1, motherId = "A", fatherId = "B",
            lineage = Lineage(listOf(ancestor("A", 0, null, null), ancestor("B", 0, null, null))),
        )
        assertEquals(0.0, predictedInbreeding(state, "stranger"), delta)
    }
}
