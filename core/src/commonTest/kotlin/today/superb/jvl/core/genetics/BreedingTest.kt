package today.superb.jvl.core.genetics

import today.superb.jvl.core.SeededRng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 번식/돌연변이/무작위 게놈 테스트 — 설계 §5/§13.
 *
 * 각 자식 대립유전자는 같은 좌위의 부모 대립유전자에서 오거나(재조합), 돌연변이로 ±1 안에 있다.
 * 모계 자식 대립유전자는 모계 부모의 두 대립유전자에서만, 부계 자식 대립유전자는 부계 부모에서만 온다.
 */
class BreedingTest {

    /** 값 [v]가 후보 [candidates] 중 하나거나 그 ±1 이내(돌연변이)인지. */
    private fun tracesToParent(v: Int, candidates: List<Int>): Boolean =
        candidates.any { v == it || v == it - 1 || v == it + 1 }

    @Test
    fun random_genome_length_matches_loci_size() {
        val g = randomGenome(SeededRng(1L))
        assertEquals(Loci.SIZE, g.alleles.size)
    }

    @Test
    fun random_genome_alleles_within_locus_range() {
        val g = randomGenome(SeededRng(7L))
        Loci.ALL.forEach { locus ->
            val pair = g.alleles[locus.id]
            assertTrue(
                pair.maternal in locus.min..locus.max,
                "locus ${locus.key} maternal ${pair.maternal} out of [${locus.min}, ${locus.max}]",
            )
            assertTrue(
                pair.paternal in locus.min..locus.max,
                "locus ${locus.key} paternal ${pair.paternal} out of [${locus.min}, ${locus.max}]",
            )
        }
    }

    @Test
    fun default_genome_alleles_are_midpoints() {
        val g = Genome.default()
        assertEquals(Loci.SIZE, g.alleles.size)
        Loci.ALL.forEach { locus ->
            val mid = (locus.min + locus.max) / 2
            val pair = g.alleles[locus.id]
            assertEquals(mid, pair.maternal, "locus ${locus.key} maternal not midpoint")
            assertEquals(mid, pair.paternal, "locus ${locus.key} paternal not midpoint")
        }
    }

    @Test
    fun child_alleles_trace_to_a_parent_allele_or_mutation() {
        val mother = randomGenome(SeededRng(11L))
        val father = randomGenome(SeededRng(22L))
        // 여러 시드로 반복해 우연한 통과를 줄인다.
        for (seed in 100L..140L) {
            val child = breed(mother, father, SeededRng(seed))
            Loci.ALL.forEach { locus ->
                val childPair = child.alleles[locus.id]
                val momCandidates = listOf(mother.alleles[locus.id].maternal, mother.alleles[locus.id].paternal)
                val dadCandidates = listOf(father.alleles[locus.id].maternal, father.alleles[locus.id].paternal)
                assertTrue(
                    tracesToParent(childPair.maternal, momCandidates),
                    "seed $seed locus ${locus.key}: child maternal ${childPair.maternal} not from mother $momCandidates (+/-1)",
                )
                assertTrue(
                    tracesToParent(childPair.paternal, dadCandidates),
                    "seed $seed locus ${locus.key}: child paternal ${childPair.paternal} not from father $dadCandidates (+/-1)",
                )
            }
        }
    }

    @Test
    fun child_alleles_within_locus_range() {
        val mother = randomGenome(SeededRng(3L))
        val father = randomGenome(SeededRng(4L))
        for (seed in 200L..240L) {
            val child = breed(mother, father, SeededRng(seed))
            Loci.ALL.forEach { locus ->
                val pair = child.alleles[locus.id]
                assertTrue(
                    pair.maternal in locus.min..locus.max,
                    "seed $seed locus ${locus.key} maternal ${pair.maternal} out of [${locus.min}, ${locus.max}]",
                )
                assertTrue(
                    pair.paternal in locus.min..locus.max,
                    "seed $seed locus ${locus.key} paternal ${pair.paternal} out of [${locus.min}, ${locus.max}]",
                )
            }
        }
    }

    @Test
    fun breed_is_reproducible_under_same_seed() {
        val mother = randomGenome(SeededRng(5L))
        val father = randomGenome(SeededRng(6L))
        val a = breed(mother, father, SeededRng(99L))
        val b = breed(mother, father, SeededRng(99L))
        assertEquals(a, b)
    }
}
