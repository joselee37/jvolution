package today.superb.jvl.core.genetics

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 손으로 검증한 혈통으로 Wright 근친계수 재귀를 고정(설계 §6).
 *
 * 검증 케이스: 무관 founder=0 · 부모-자식 0.25 · full-sib 0.25 · half-sib 0.125.
 */
class KinshipTest {

    private val delta = 1e-9

    private fun nodesOf(vararg nodes: PedigreeNode): Map<String, PedigreeNode> =
        nodes.associateBy { it.id }

    private fun founder(id: String, gen: Int = 0) =
        PedigreeNode(id = id, gen = gen, motherId = null, fatherId = null)

    private fun child(id: String, gen: Int, mother: String, father: String) =
        PedigreeNode(id = id, gen = gen, motherId = mother, fatherId = father)

    @Test
    fun unrelated_founders_have_zero_kinship() {
        val nodes = nodesOf(founder("X"), founder("Y"))
        assertEquals(0.0, Kinship.coefficientOfKinship("X", "Y", nodes), delta)
    }

    @Test
    fun unrelated_founders_have_zero_inbreeding() {
        val nodes = nodesOf(founder("X"), founder("Y"))
        assertEquals(0.0, Kinship.inbreeding("X", "Y", nodes), delta)
    }

    @Test
    fun kinship_of_self_founder_is_half() {
        val nodes = nodesOf(founder("X"))
        // f(a,a) = 0.5*(1 + F_self), founder F_self=0 → 0.5.
        assertEquals(0.5, Kinship.coefficientOfKinship("X", "X", nodes), delta)
    }

    @Test
    fun parent_and_child_kinship_is_quarter() {
        // A,B founders; C 부모 = (A,B).
        val nodes = nodesOf(
            founder("A"),
            founder("B"),
            child("C", gen = 1, mother = "A", father = "B"),
        )
        // f(A,C) = 0.25.
        assertEquals(0.25, Kinship.coefficientOfKinship("A", "C", nodes), delta)
    }

    @Test
    fun inbreeding_with_parent_as_one_lineage_is_quarter() {
        // f(A,C)를 inbreeding(A,C) 진입점으로도 확인(부모로 A,C를 넘김).
        val nodes = nodesOf(
            founder("A"),
            founder("B"),
            child("C", gen = 1, mother = "A", father = "B"),
        )
        assertEquals(0.25, Kinship.inbreeding("A", "C", nodes), delta)
    }

    @Test
    fun full_siblings_kinship_is_quarter() {
        // C,D 둘 다 부모 = (A,B).
        val nodes = nodesOf(
            founder("A"),
            founder("B"),
            child("C", gen = 1, mother = "A", father = "B"),
            child("D", gen = 1, mother = "A", father = "B"),
        )
        // f(C,D) = 0.25.
        assertEquals(0.25, Kinship.coefficientOfKinship("C", "D", nodes), delta)
    }

    @Test
    fun half_siblings_kinship_is_eighth() {
        // C 부모=(A,B), E 부모=(A,G) — 한 부모(A)만 공유.
        val nodes = nodesOf(
            founder("A"),
            founder("B"),
            founder("G"),
            child("C", gen = 1, mother = "A", father = "B"),
            child("E", gen = 1, mother = "A", father = "G"),
        )
        // f(C,E) = 0.125.
        assertEquals(0.125, Kinship.coefficientOfKinship("C", "E", nodes), delta)
    }

    @Test
    fun inbreeding_returns_zero_when_a_parent_is_null() {
        val nodes = nodesOf(founder("A"), founder("B"))
        assertEquals(0.0, Kinship.inbreeding(null, "B", nodes), delta)
        assertEquals(0.0, Kinship.inbreeding("A", null, nodes), delta)
        assertEquals(0.0, Kinship.inbreeding(null, null, nodes), delta)
    }

    @Test
    fun missing_id_is_treated_as_unrelated_founder() {
        // ghostParent는 nodes에 없음 → founder(gen=-1) 취급. 알려진 founder와 무관.
        val nodes = nodesOf(founder("A"))
        assertEquals(0.0, Kinship.coefficientOfKinship("A", "ghostParent", nodes), delta)
    }

    /** 전동기 C,D의 자식 Z, 손으로 검증한 2세대 근친 혈통. */
    private fun inbredZNodes() = nodesOf(
        founder("A"),
        founder("B"),
        child("C", gen = 1, mother = "A", father = "B"),
        child("D", gen = 1, mother = "A", father = "B"),
        child("Z", gen = 2, mother = "C", father = "D"),
    )

    @Test
    fun child_of_full_sibs_is_inbred() {
        // Z의 부모 포인터(C,D)로 F를 구한다 → F_Z = f(C,D) = 0.25(2세대 하강 경로 실제 검증).
        val nodes = inbredZNodes()
        val z = nodes.getValue("Z")
        assertEquals(0.25, Kinship.inbreeding(z.motherId, z.fatherId, nodes), delta)
    }

    @Test
    fun inbred_self_kinship_includes_inbreeding_term() {
        // f(Z,Z) = 0.5*(1 + F_Z) = 0.5*(1 + 0.25) = 0.625 — 자기 친족계수의 (1+F) 항을 검증.
        val nodes = inbredZNodes()
        assertEquals(0.625, Kinship.coefficientOfKinship("Z", "Z", nodes), delta)
    }
}
