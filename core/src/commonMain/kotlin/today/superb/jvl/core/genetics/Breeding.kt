package today.superb.jvl.core.genetics

import today.superb.jvl.core.Rng

/**
 * 번식 + 돌연변이 — 설계 §5. `breed`/`randomGenome`은 [Rng]를 쓴다(번식/시드 시 1회,
 * 결과 게놈은 저장되므로 휴대용 PRNG 불요 — 테스트는 `SeededRng`로 재현).
 */

/** 대립유전자별 ±1 섭동 확률. 리서치 §4 최소 모델. */
private const val MUTATION_RATE = 0.08f

/**
 * Gen1/피어 founder용 — 좌위 범위 `[min, max]` 내 균등 무작위(maternal/paternal 독립).
 * 길이 == [Loci.SIZE].
 */
fun randomGenome(rng: Rng): Genome = Genome(
    alleles = Loci.ALL.map { locus ->
        AllelePair(
            maternal = rng.nextInt(locus.max - locus.min + 1) + locus.min,
            paternal = rng.nextInt(locus.max - locus.min + 1) + locus.min,
        )
    },
)

/**
 * 유성 교배 — 좌위별 모계 게놈의 두 대립유전자 중 하나(50%) → 자식 maternal,
 * 부계 게놈의 두 대립유전자 중 하나(50%) → 자식 paternal. 선택된 각 값에 돌연변이 적용.
 *
 * [Loci.SIZE]보다 짧은 게놈은 누락 좌위를 [Genome.default] 항목으로 패딩해 처리.
 */
fun breed(maternal: Genome, paternal: Genome, rng: Rng): Genome {
    val motherAlleles = maternal.padded()
    val fatherAlleles = paternal.padded()
    return Genome(
        alleles = Loci.ALL.map { locus ->
            val mom = motherAlleles[locus.id]
            val dad = fatherAlleles[locus.id]
            val fromMother = if (rng.nextFloat() < 0.5f) mom.maternal else mom.paternal
            val fromFather = if (rng.nextFloat() < 0.5f) dad.maternal else dad.paternal
            AllelePair(
                maternal = mutateAllele(fromMother, locus, rng),
                paternal = mutateAllele(fromFather, locus, rng),
            )
        },
    )
}

/** 저확률 ±1 섭동(50/50), 좌위 범위 `[min, max]`로 clamp. */
private fun mutateAllele(value: Int, locus: Locus, rng: Rng): Int {
    if (rng.nextFloat() >= MUTATION_RATE) return value
    val delta = if (rng.nextFloat() < 0.5f) 1 else -1
    return (value + delta).coerceIn(locus.min, locus.max)
}

/** 누락 좌위([Loci.SIZE]보다 짧은 게놈)를 [Genome.default] 대립유전자로 패딩. */
private fun Genome.padded(): List<AllelePair> {
    if (alleles.size >= Loci.SIZE) return alleles
    val defaults = Genome.default().alleles
    return List(Loci.SIZE) { i -> alleles.getOrElse(i) { defaults[i] } }
}
