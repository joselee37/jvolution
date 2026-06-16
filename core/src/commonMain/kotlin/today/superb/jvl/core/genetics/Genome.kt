package today.superb.jvl.core.genetics

import kotlinx.serialization.Serializable

/**
 * 게놈 스키마 버전. 좌위 추가/제거 시 올린다 → 짧은 저장본은 기본 대립유전자로 패딩.
 * 카탈로그([Loci.ALL])가 코드 상수이므로 `Loci.ALL.size`가 길이의 단일 소스.
 */
const val GENOME_VERSION = 1

/** 한 좌위의 이배체 대립유전자 쌍. 정수 → 완전 결정론(같은 게놈 → 같은 개체). */
@Serializable
data class AllelePair(val maternal: Int, val paternal: Int)

/**
 * 이배체 게놈. 저장본에는 **대립유전자 값 + version만** 직렬화(좌위 카탈로그는 코드 상수).
 *
 * @property alleles index == [Locus.id], 길이 == [Loci.SIZE]. 짧으면 진입 시 기본값으로 패딩.
 */
@Serializable
data class Genome(
    val version: Int = GENOME_VERSION,
    val alleles: List<AllelePair>,
) {
    companion object
}

/** 표현형 도메인. 게놈이 외형/스탯/행동 3개 도메인을 전부 구동. */
enum class Domain { APPEARANCE, STAT, BEHAVIOR }

/** 좌위 발현 규칙. COMPLETE=우성 발현, INCOMPLETE_BLEND=평균, CODOMINANT=평균+이형접합 노출. */
enum class Dominance { COMPLETE, INCOMPLETE_BLEND, CODOMINANT }

/** 좌위 정의(카탈로그 항목, 비직렬화 — 코드 상수). [min, max] 정수 도메인. */
data class Locus(
    val id: Int,
    val key: String,
    val domain: Domain,
    val dominance: Dominance,
    val min: Int,
    val max: Int,
)

/**
 * 전 좌위 중간값 — 결정론적 기본 게놈. 레거시 저장본 마이그레이션·테스트·시드 재현의 기준.
 * 각 좌위의 maternal/paternal 모두 중간값 `(min + max) / 2`(정수 내림)으로 동형접합.
 */
fun Genome.Companion.default(): Genome = Genome(
    alleles = Loci.ALL.map { locus ->
        val mid = (locus.min + locus.max) / 2
        AllelePair(maternal = mid, paternal = mid)
    },
)
