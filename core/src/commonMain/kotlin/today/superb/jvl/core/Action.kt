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
}
