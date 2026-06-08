package today.superb.jvl.core

import kotlinx.serialization.Serializable

/**
 * 은퇴한 한 세대의 비석(epitaph). 데모 `reset`의 epitaph 객체 1:1.
 *
 * 스탯은 아카이브 시점의 백분율(0~100 정수) 스냅샷. [hatchedAt]/[archivedAt]은 epoch millis —
 * 트리 화면이 상대시간("Xs/m/h ago")으로 표시한다. reducer는 wall-clock을 읽지 않으므로
 * [archivedAt]은 [Action.Reset] payload로 주입된다.
 */
@Serializable
data class LineageEntry(
    val gen: Int,
    val name: String,
    val stage: Stage,
    val cycles: Int,
    val happiness: Int,
    val energy: Int,
    val bond: Int,
    val discipline: Int,
    val training: Int,
    val hatchedAt: Long,
    val archivedAt: Long,
)
