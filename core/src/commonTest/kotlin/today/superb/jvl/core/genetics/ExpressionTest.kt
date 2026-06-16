package today.superb.jvl.core.genetics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EPS = 1e-4f

/** [Loci]에서 key의 id로 게놈 대립유전자 한 쌍을 교체한 사본. */
private fun Genome.withLocus(key: String, maternal: Int, paternal: Int): Genome {
    val id = Loci.ALL.first { it.key == key }.id
    val next = alleles.toMutableList()
    next[id] = AllelePair(maternal, paternal)
    return copy(alleles = next)
}

class ExpressionTest {

    // ---- 결정론 ----

    @Test
    fun express_is_deterministic_for_same_genome() {
        val g = Genome.default()
            .withLocus("hue", 1, 6)
            .withLocus("vigorA", 3, 7)
            .withLocus("aggression", 8, 2)
        assertEquals(express(g), express(g))
    }

    @Test
    fun default_genome_phenotype_is_stable() {
        // 두 개의 독립 default 게놈 → 동일 표현형(중간값 동형접합).
        assertEquals(express(Genome.default()), express(Genome.default()))
    }

    // ---- 우성 규칙 ----

    @Test
    fun complete_dominance_takes_max() {
        // symmetry COMPLETE: max(maternal, paternal).
        val g = Genome.default().withLocus("symmetry", 2, 6)
        assertEquals(6, express(g).appearance.symmetry)
        // 순서 무관.
        val g2 = Genome.default().withLocus("symmetry", 6, 2)
        assertEquals(6, express(g2).appearance.symmetry)
    }

    @Test
    fun incomplete_blend_takes_integer_average() {
        // bodyLength INCOMPLETE_BLEND: (m + p) / 2 정수 내림. (3 + 6)/2 = 4.
        val g = Genome.default().withLocus("bodyLength", 3, 6)
        assertEquals(4, express(g).appearance.bodyLength)
    }

    @Test
    fun codominant_value_is_integer_average() {
        // hue CODOMINANT 주 색 = (m + p)/2 정수 내림. (1 + 6)/2 = 3.
        val g = Genome.default().withLocus("hue", 1, 6)
        assertEquals(3, express(g).appearance.hue)
    }

    // ---- CODOMINANT hue 이형/동형 ----

    @Test
    fun codominant_hue_heterozygous_exposes_hueAlt() {
        val g = Genome.default().withLocus("hue", 1, 6)
        val hueAlt = express(g).appearance.hueAlt
        assertNotNull(hueAlt)
        assertEquals(6, hueAlt) // max(maternal, paternal)
    }

    @Test
    fun codominant_hue_homozygous_hueAlt_is_null() {
        val g = Genome.default().withLocus("hue", 4, 4)
        assertNull(express(g).appearance.hueAlt)
    }

    @Test
    fun default_genome_hue_is_homozygous_so_hueAlt_null() {
        // hue range 0..7 → mid 3, default 동형접합 → hueAlt null.
        val p = express(Genome.default())
        assertEquals(3, p.appearance.hue)
        assertNull(p.appearance.hueAlt)
    }

    // ---- 다유전자 스탯 정규화 ----

    @Test
    fun polygenic_vitality_sums_two_loci_normalized() {
        // vigorA + vigorB 각 동형접합 합산 / 20. (4 + 8) = 12 → 0.6.
        val g = Genome.default()
            .withLocus("vigorA", 4, 4)
            .withLocus("vigorB", 8, 8)
        assertEquals(0.6f, express(g).stats.vitality, EPS)
    }

    @Test
    fun polygenic_midpoint_normalizes_to_half() {
        // default: 모든 스탯 좌위 mid 5 → (5+5)/20 = 0.5.
        val s = express(Genome.default()).stats
        assertEquals(0.5f, s.vitality, EPS)
        assertEquals(0.5f, s.metabolism, EPS)
        assertEquals(0.5f, s.resilience, EPS)
    }

    @Test
    fun polygenic_extremes_clamp_to_unit_range() {
        val low = Genome.default()
            .withLocus("metabolismA", 0, 0)
            .withLocus("metabolismB", 0, 0)
        assertEquals(0f, express(low).stats.metabolism, EPS)
        val high = Genome.default()
            .withLocus("metabolismA", 10, 10)
            .withLocus("metabolismB", 10, 10)
        assertEquals(1f, express(high).stats.metabolism, EPS)
    }

    // ---- 행동 정규화 ----

    @Test
    fun behavior_single_locus_normalized_by_ten() {
        // boldness INCOMPLETE_BLEND: (6+6)/2 = 6 → 0.6. tempo (10+10)/2 = 10 → 1.0.
        val g = Genome.default()
            .withLocus("boldness", 6, 6)
            .withLocus("tempo", 10, 10)
        val b = express(g).behavior
        assertEquals(0.6f, b.boldness, EPS)
        assertEquals(1.0f, b.tempo, EPS)
    }

    // ---- 시작 스탯 시드 (레거시 재현, CRITICAL) ----

    @Test
    fun starting_stats_for_default_genome_match_legacy_seed() {
        val s = startingStats(Genome.default())
        assertEquals(0.7f, s.energy, EPS)
        assertEquals(0.6f, s.happiness, EPS)
        assertEquals(0.45f, s.hunger, EPS)
    }

    @Test
    fun starting_stats_scale_with_phenotype() {
        // 최대 vitality → energy 상한(0.9), 중간 대비 증가 확인.
        val g = Genome.default()
            .withLocus("vigorA", 10, 10)
            .withLocus("vigorB", 10, 10)
        val s = startingStats(g)
        // vitality=1.0 → 0.7 + (1.0-0.5)*0.4 = 0.9.
        assertEquals(0.9f, s.energy, EPS)
        assertTrue(s.energy > startingStats(Genome.default()).energy)
    }
}
