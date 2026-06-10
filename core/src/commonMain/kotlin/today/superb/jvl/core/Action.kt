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

    /** 생명체 종 변경(설정 패널). 렌더의 단일 소스인 [GameState.species]를 직접 바꾼다. */
    data class SetSpecies(val species: Species) : Action

    /**
     * 2차 멀티뷰용 선반영. **1차에서는 어떤 경로로도 dispatch되지 않는다** — 터미널의
     * tree/radar/sonar는 [today.superb.jvl.core.terminal.TerminalCommand.ModulePending]으로
     * 처리되어 view를 바꾸지 않는다. 1차에서 배선 금지(단일 화면이라 state.view↔렌더 desync 유발).
     */
    data class SetView(val view: View) : Action

    /** 토스트 만료 — ViewModel 타이머가 1.4s 후 dispatch. */
    data object ClearToast : Action

    /** 훈육 플래시 만료 — ViewModel 타이머가 2s 후 dispatch([Discipline]이 켠 것을 끔). */
    data object ClearDisciplineFlash : Action

    /** 오디오 on/off 토글(터미널 `mute`/`sound`, 설정 패널 SFX). */
    data object ToggleSound : Action

    // ── 피어 / 레이더 (2차 마일스톤) ──

    /** 피어 틱 — 위치 드리프트 + 근접 AI 판정. dt는 초 단위(ViewModel이 ~1s 주기로 dispatch). */
    data class PeerTick(val dt: Float) : Action

    /** 대기 중인 요청 거절. */
    data object DeclineRequest : Action

    /**
     * 방해금지 설정. 데모 `setDnd`는 단순 set이지만, 명세(05)의 "DND를 켤 때 대기 요청 자동 거절"
     * 규칙을 순수 도메인으로 흡수해 이 reducer 분기가 처리한다(터미널 단일 액션 API 유지).
     */
    data class SetDnd(val on: Boolean) : Action

    // ── 전투 (3차 마일스톤) — 데모 battle* 액션 1:1 ──

    /** 전투 시작(accept/challenge 진입). view=Battle, pendingRequest 해소. */
    data class BattleStart(val peerId: String) : Action

    /** 액션 메뉴 커서 이동([set] 절대 지정 또는 [delta] 상대 이동). choose 단계에서만. */
    data class BattleCursor(val set: Int? = null, val delta: Int = 0) : Action

    /** 액션 확정 — 양측 무브 결정 + 결과 즉시 산정, myCast 단계로. */
    data object BattleCommit : Action

    /** 캐스트 비트 진행: myCast→theirCast→reveal. */
    data object BattleAdvanceCast : Action

    /** reveal→damage 전이 + HP 바 흔들림 nonce. */
    data object BattleResolve : Action

    /** damage 적용 + KO 판정(win/lose/draw) 또는 다음 턴. */
    data object BattleApplyDamage : Action

    /** 전투 이탈(결과: flee). */
    data object BattleFlee : Action

    /** 전투 종료 — 보상/페널티 적용 후 소나 복귀. */
    data object BattleEnd : Action

    // ── 계보 (4차 마일스톤) ──

    /**
     * 세대 리셋 — 현재 개체를 계보에 아카이브하고 새 알로 다시 시작.
     * [newName]은 ViewModel이 NAMES 풀에서 RNG로 고르고, [now]는 nowMillis()를 찍어 넣는다
     * (reducer는 wall-clock을 읽지 않음). 피어/유대/전적은 보존된다.
     */
    data class Reset(val newName: String, val now: Long) : Action
}
