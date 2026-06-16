package today.superb.jvl.core.genetics

/**
 * 게놈 → 표현형 발현 (순수 결정론 함수, RNG 없음). 같은 게놈은 어느 플랫폼에서도 같은 표현형.
 *
 * 좌위 조회는 [Loci] 카탈로그를 key/id로 찾으므로 게놈 alleles 순서·길이 변화에 견고하다.
 * 게놈이 [Loci.SIZE]보다 짧으면(스키마 마이그레이션 패딩) 해당 좌위 중간값으로 대체한다.
 */

/** [expressLocus] 결과 — 발현 정수값 + 이형접합 여부. */
internal data class LocusValue(val value: Int, val heterozygous: Boolean)

/** 좌위 발현 규칙(정수 산술). spec §4 참조. */
internal fun expressLocus(locus: Locus, pair: AllelePair): LocusValue {
    val m = pair.maternal
    val p = pair.paternal
    val value = when (locus.dominance) {
        Dominance.COMPLETE -> if (m >= p) m else p          // 높은 값 우성
        Dominance.INCOMPLETE_BLEND -> (m + p) / 2           // 정수 내림 평균
        Dominance.CODOMINANT -> (m + p) / 2
    }
    return LocusValue(value = value, heterozygous = m != p)
}

/**
 * 게놈의 한 좌위 대립유전자 쌍을 가져온다. 게놈이 짧으면(패딩 경로) 좌위 중간값 동형접합으로 대체.
 * [Loci.ALL]은 index == [Locus.id]라 id로 직접 인덱싱한다.
 */
private fun Genome.pairFor(locus: Locus): AllelePair =
    alleles.getOrNull(locus.id) ?: run {
        val mid = (locus.min + locus.max) / 2
        AllelePair(maternal = mid, paternal = mid)
    }

/** 좌위 key로 발현값을 구한다(순서 무관, 패딩 견고). */
private fun Genome.locusValue(key: String): LocusValue {
    val locus = Loci.ALL.first { it.key == key }
    return expressLocus(locus, pairFor(locus))
}

/** 게놈 → 표현형(외형/스탯/행동 3 도메인). 순수 함수. */
fun express(genome: Genome): Phenotype {
    val hueLocus = Loci.ALL.first { it.key == "hue" }
    val huePair = genome.pairFor(hueLocus)
    val hueLv = expressLocus(hueLocus, huePair)
    val appearance = AppearanceTraits(
        bodyLength = genome.locusValue("bodyLength").value,
        branchAngle = genome.locusValue("branchAngle").value,
        symmetry = genome.locusValue("symmetry").value,
        recursionDepth = genome.locusValue("recursionDepth").value,
        hue = hueLv.value,
        // CODOMINANT hue 이형접합 → 보조 색 = 두 대립유전자 중 높은 값(주 색은 평균).
        hueAlt = if (hueLv.heterozygous) maxOf(huePair.maternal, huePair.paternal) else null,
        pattern = genome.locusValue("pattern").value,
    )

    // 다유전자 스탯: 2좌위 합(각 0..10) → [0,1]. 중간 5+5=10 → 0.5.
    val stats = StatTraits(
        vitality = (genome.locusValue("vigorA").value + genome.locusValue("vigorB").value) / 20f,
        metabolism = (genome.locusValue("metabolismA").value + genome.locusValue("metabolismB").value) / 20f,
        resilience = (genome.locusValue("resilienceA").value + genome.locusValue("resilienceB").value) / 20f,
    )

    // 행동: 단일 좌위 / 10 → [0,1].
    val behavior = BehaviorTraits(
        aggression = genome.locusValue("aggression").value / 10f,
        sociability = genome.locusValue("sociability").value / 10f,
        boldness = genome.locusValue("boldness").value / 10f,
        tempo = genome.locusValue("tempo").value / 10f,
    )

    return Phenotype(appearance = appearance, stats = stats, behavior = behavior)
}

/** 새 개체 부화/교배 시 시작 스탯(스탯 도메인 라이브 연결). spec §4 시드 공식. */
data class StartingStats(
    val energy: Float,
    val happiness: Float,
    val hunger: Float,
)

/**
 * 표현형 스탯에서 시작 스탯을 시드한다. 기본 게놈(전 좌위 중간값) → 레거시 초기값 정확 재현
 * (energy 0.7 / happiness 0.6 / hunger 0.45).
 */
fun startingStats(genome: Genome): StartingStats {
    val stats = express(genome).stats
    val energy = (0.7f + (stats.vitality - 0.5f) * 0.4f).coerceIn(0f, 1f)
    val happiness = (0.6f + (stats.resilience - 0.5f) * 0.3f).coerceIn(0f, 1f)
    val hunger = (0.45f + (stats.metabolism - 0.5f) * 0.2f).coerceIn(0f, 1f)
    return StartingStats(energy = energy, happiness = happiness, hunger = hunger)
}
