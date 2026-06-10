package today.superb.jvl.core

import kotlinx.serialization.Serializable

/** 터미널/소나 로그 한 줄. newest-first로 누적, 최대 [LOG_CAP]줄. */
@Serializable
data class LogEntry(val cycle: Int, val msg: String)

/**
 * 게임 전역 상태. 데모 `initialState()` 1:1 포팅(1차 마일스톤 범위: 케어 + 메타).
 *
 * 불변: 모든 필드 `val`, 컬렉션은 read-only `List`. reducer는 항상 `copy`로 새 상태를 반환한다.
 * peers / battle / pendingRequest / lineage 는 2차 마일스톤(레이더·전투·계보)에서 추가.
 *
 * 스탯은 모두 [0, 1] 범위(reducer가 [clamp]). hunger 0=배부름·1=굶주림, dirty 0=청결·1=더러움.
 */
@Serializable
data class GameState(
    val name: String,
    val age: Int,
    val cycles: Int,
    val gen: Int,
    val stage: Stage,
    val species: Species,
    val happiness: Float,
    val energy: Float,
    val hunger: Float,
    val dirty: Float,
    val bond: Float,
    val training: Float,
    val discipline: Float,
    val asleep: Boolean,
    val evolveProgress: Float,
    val canEvolve: Boolean,
    val evolving: Boolean,
    /**
     * 훈육 직후 잠깐 켜지는 transient flash — [Mood.SCOLDED]의 트리거. toast/evolving과 같은
     * 패턴: reducer([Action.Discipline])가 켜고 ViewModel 타이머가 [Action.ClearDisciplineFlash]로
     * 끈다. (데모 as-is에서는 아무도 켜지 않는 버그였음 — 풀터치 마일스톤에서 수리.)
     */
    val disciplineFlash: Boolean,
    val pingNonce: Int,
    val log: List<LogEntry>,
    val toast: String?,
    val sound: Boolean,
    val view: View,
    /** 부화 시각(epoch millis). reducer 밖(ViewModel)에서 주입 — reducer는 wall-clock을 읽지 않는다. */
    val hatchedAt: Long,
    // ── 피어 / 레이더 (2차 마일스톤) — 데모 initialState()의 peer 필드 1:1 ──
    /** NPC 피어 로스터(고정 7유닛). [Action.PeerTick]이 위치·AI를 갱신. */
    val peers: List<Peer>,
    /** 처리 대기 중인 도전 요청. 한 번에 하나(single-request gate). 없으면 null. */
    val pendingRequest: PeerRequest?,
    /** 방해금지 — 들어오는 challenge 억제. 전투 중에는 강제 on으로 간주(3차). */
    val dnd: Boolean,
    /** 터미널이 자동 에코해야 할 피어 이벤트가 생길 때마다 증가하는 nonce. */
    val peerEventNonce: Int,
    /** 가장 최근 피어 이벤트의 내용. [peerEventNonce] 변화 시 TerminalScreen이 읽어 출력. */
    val peerEventLatest: PeerEvent?,
    /** 진행 중인 전투(3차 마일스톤). 전투 중에만 non-null. */
    val battle: today.superb.jvl.core.battle.BattleState?,
    /** 은퇴한 이전 세대 비석(4차 마일스톤). [Action.Reset]이 현재 개체를 여기에 아카이브. */
    val lineage: List<LineageEntry>,
) {
    companion object {
        const val LOG_CAP = 20

        /**
         * 새 게임 초기 상태. 데모 `initialState()`의 기본 스탯과 동일.
         * @param name  생성 시점에 caller(ViewModel)가 RNG로 고른 이름.
         * @param now   부화 시각(epoch millis) — caller가 `nowMillis()`로 주입.
         * @param peers 시작 피어 로스터. caller가 [PeerRoster.makePeers]로 만들어 주입(기본 빈 목록 —
         *              피어를 쓰지 않는 케어/터미널 단위테스트는 생략 가능).
         */
        fun initial(name: String, now: Long, peers: List<Peer> = emptyList()): GameState = GameState(
            name = name,
            age = 0,
            cycles = 0,
            gen = 1,
            stage = Stage.Egg,
            species = Species.Ghost,
            happiness = 0.6f,
            energy = 0.7f,
            hunger = 0.45f,
            dirty = 0.3f,
            bond = 0.4f,
            training = 0.1f,
            discipline = 0.2f,
            asleep = false,
            evolveProgress = 0f,
            canEvolve = false,
            evolving = false,
            disciplineFlash = false,
            pingNonce = 0,
            log = emptyList(),
            toast = null,
            sound = false,
            view = View.Sonar,
            hatchedAt = now,
            peers = peers,
            pendingRequest = null,
            dnd = false,
            peerEventNonce = 0,
            peerEventLatest = null,
            battle = null,
            lineage = emptyList(),
        )
    }
}
