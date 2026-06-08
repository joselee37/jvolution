package today.superb.jvl.core

/**
 * reducer가 처리하는 액션. 데모 `reduce(s, a)`의 케이스 1:1.
 *
 * 명령어 `scold` → [Discipline] 매핑(데모 `case 'discipline'`).
 * 2차 마일스톤(레이더·전투): PeerTick / Challenge / Battle* 등은 후속 추가.
 */
sealed interface Action {
    /** 케어 틱 — 스탯 드리프트. dt는 초 단위 경과시간. */
    data class Tick(val dt: Float) : Action

    /** 소나 핑 — bond 증가 + 스윕 트리거(pingNonce). */
    data object Ping : Action
    data object Feed : Action
    data object Play : Action
    data object Clean : Action

    /** 수면 토글(awake ↔ asleep). */
    data object Sleep : Action
    data object Train : Action

    /** 훈육(터미널 `scold`). */
    data object Discipline : Action
    data object Heal : Action

    /** 진화 시작 — evolving=true. ViewModel이 2.2s 후 [EvolveComplete] dispatch. */
    data object Evolve : Action

    /** 진화 완료 — 다음 단계로 전이. */
    data object EvolveComplete : Action

    data class Rename(val name: String) : Action

    /**
     * 2차 멀티뷰용 선반영. **1차에서는 어떤 경로로도 dispatch되지 않는다** — 터미널의
     * tree/radar/sonar는 [today.superb.jvl.core.terminal.TerminalCommand.ModulePending]으로
     * 처리되어 view를 바꾸지 않는다. 1차에서 배선 금지(단일 화면이라 state.view↔렌더 desync 유발).
     */
    data class SetView(val view: View) : Action

    /** 토스트 만료 — ViewModel 타이머가 1.4s 후 dispatch. */
    data object ClearToast : Action

    // ── 피어 / 레이더 (2차 마일스톤) ──

    /** 피어 틱 — 위치 드리프트 + 근접 AI 판정. dt는 초 단위(ViewModel이 ~1s 주기로 dispatch). */
    data class PeerTick(val dt: Float) : Action

    /**
     * 대기 중인 도전 수락 — 2차는 데모 Phase-1 스텁(유대 +0.04 + 이벤트 에코).
     * 실제 전투 진입은 3차에서 이 분기를 `battleStart`로 교체.
     */
    data object AcceptRequest : Action

    /** 대기 중인 요청 거절. */
    data object DeclineRequest : Action

    /**
     * 방해금지 설정. 데모 `setDnd`는 단순 set이지만, 명세(05)의 "DND를 켤 때 대기 요청 자동 거절"
     * 규칙을 순수 도메인으로 흡수해 이 reducer 분기가 처리한다(터미널 단일 액션 API 유지).
     */
    data class SetDnd(val on: Boolean) : Action
}
