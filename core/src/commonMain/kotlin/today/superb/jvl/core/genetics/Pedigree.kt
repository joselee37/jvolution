package today.superb.jvl.core.genetics

import kotlinx.serialization.Serializable
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage

/**
 * 아카이브된 한 조상(혈통 DAG 노드). 구 `LineageEntry`의 상위집합 + 게놈/부모/식별자.
 *
 * 스탯은 아카이브 시점의 백분율(0~100 정수) 스냅샷(디스플레이용, 구 `LineageEntry` 필드와 동일).
 * [hatchedAt]/[archivedAt]은 epoch millis.
 */
@Serializable
data class Ancestor(
    val id: String,
    val gen: Int,
    val name: String,
    // species/stage는 직렬화 기본값을 둔다 — 레거시 마이그레이션에서 알 수 없는 enum 값이
    // 와도 coerceInputValues가 항목별로 폴백하게 해, 한 조상 때문에 저장본 전체가 날아가지 않게.
    val species: Species = Species.Ghost,
    val stage: Stage = Stage.Adult,
    val genome: Genome,
    val motherId: String?,
    val fatherId: String?,
    val cycles: Int,
    val happiness: Int,
    val energy: Int,
    val bond: Int,
    val discipline: Int,
    val training: Int,
    val hatchedAt: Long,
    val archivedAt: Long,
)

/**
 * 혈통 모음. 구 `List<LineageEntry>`를 대체.
 * founder(peer 공동부모, gen=0)와 본가 계보(gen≥1)가 공존한다.
 */
@Serializable
data class Lineage(val ancestors: List<Ancestor>)

/** 혈통 DAG 노드(근친계수 계산용 경량 뷰, 비직렬화). */
data class PedigreeNode(
    val id: String,
    val gen: Int,
    val motherId: String?,
    val fatherId: String?,
)
