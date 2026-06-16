package today.superb.jvl.core.genetics

import today.superb.jvl.core.FixedRng
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

    private val hue = Loci.byId(4)   // 0..7

    @Test
    fun mutate_clamps_at_locus_max() {
        // nextFloat #1 < MUTATION_RATE(0.08) → 발동, #2 < 0.5 → +1. 7+1 → clamp 7.
        assertEquals(7, mutateAllele(7, hue, FixedRng(listOf(0.0f, 0.0f))))
    }

    @Test
    fun mutate_clamps_at_locus_min() {
        // #1 발동, #2 >= 0.5 → -1. 0-1 → clamp 0.
        assertEquals(0, mutateAllele(0, hue, FixedRng(listOf(0.0f, 0.9f))))
    }

    @Test
    fun mutate_noop_when_below_rate() {
        // #1 >= MUTATION_RATE → 그대로(둘째 float 소비 안 함 — FixedRng 미고갈로 검증).
        assertEquals(5, mutateAllele(5, hue, FixedRng(listOf(0.5f))))
    }

    @Test
    fun child_allele_origin_is_exact_without_mutation() {
        // 좌위마다 4 float: [모계 pick=0.0→maternal, 부계 pick=0.9→paternal, 모계 rate=0.9, 부계 rate=0.9(둘 다 돌연변이 없음)].
        val mother = randomGenome(SeededRng(11L))
        val father = randomGenome(SeededRng(22L))
        val scripted = List(Loci.SIZE) { listOf(0.0f, 0.9f, 0.9f, 0.9f) }.flatten()
        val child = breed(mother, father, FixedRng(scripted))
        Loci.ALL.forEach { locus ->
            assertEquals(mother.alleles[locus.id].maternal, child.alleles[locus.id].maternal, "locus ${locus.key}: 모계 maternal에서")
            assertEquals(father.alleles[locus.id].paternal, child.alleles[locus.id].paternal, "locus ${locus.key}: 부계 paternal에서")
        }
    }

    @Test
    fun short_genome_is_padded_safely() {
        val short = Genome(alleles = Genome.default().alleles.take(4))
        // express: 누락 좌위는 중간값으로 패딩 → default 게놈과 동일 표현형(앞 4개도 중간값이라).
        assertEquals(express(Genome.default()), express(short))
        // breed: 짧은 게놈도 전체 길이로 패딩(인덱스 크래시 없음).
        assertEquals(Loci.SIZE, breed(short, Genome.default(), SeededRng(1L)).alleles.size)
    }
}
