package today.superb.jvl.core.genetics

/**
 * 표현형 — `express(genome)`로 산출되는 파생 데이터(비직렬화, 항상 재계산).
 *
 * 같은 게놈은 어느 플랫폼에서도 같은 표현형(express는 RNG 없는 순수 함수). 직렬화하지 않는 이유.
 */

/**
 * 외형 형질. [hueAlt]는 hue 좌위가 이형접합(CODOMINANT)일 때 노출되는 보조 색, 동형접합이면 null.
 */
data class AppearanceTraits(
    val bodyLength: Int,
    val branchAngle: Int,
    val symmetry: Int,
    val recursionDepth: Int,
    val hue: Int,
    val hueAlt: Int?,
    val pattern: Int,
)

/** 스탯 형질. 각 값 [0, 1]로 정규화된 다유전자 합산. */
data class StatTraits(
    val vitality: Float,
    val metabolism: Float,
    val resilience: Float,
)

/** 행동 형질. 각 값 [0, 1]. peer AI/전투 가중에 소비(라이브 연결은 후속). */
data class BehaviorTraits(
    val aggression: Float,
    val sociability: Float,
    val boldness: Float,
    val tempo: Float,
)

/** 게놈 발현 결과 3개 도메인 묶음. */
data class Phenotype(
    val appearance: AppearanceTraits,
    val stats: StatTraits,
    val behavior: BehaviorTraits,
)
