package today.superb.jvl.core.terminal

/**
 * 파싱된 터미널 명령. 데모 `runCommand`의 verb 분기 1:1(1차 마일스톤 범위).
 *
 * 레이더·전투·계보 관련 명령(scan/peers/radar/tree/sonar/bond/challenge/accept/
 * decline/dnd/flee/mute/reset)은 후속 마일스톤이라 [ModulePending]으로 묶는다 —
 * 1차에서는 SetView를 dispatch하지 않고 우아하게 응답만 한다.
 */
sealed interface TerminalCommand {
    data object Help : TerminalCommand
    data object WhoAmI : TerminalCommand
    data object Status : TerminalCommand
    data object Ping : TerminalCommand

    /** `feed [item]` — 선택적 먹이 이름. */
    data class Feed(val item: String?) : TerminalCommand
    data object Play : TerminalCommand
    data object Clean : TerminalCommand

    /** `sleep` — 멱등: 이미 자고 있으면 no-op. */
    data object Sleep : TerminalCommand

    /** `wake` — 멱등: 이미 깨어 있으면 no-op. */
    data object Wake : TerminalCommand
    data object Train : TerminalCommand

    /** `scold` / `discipline`. */
    data object Scold : TerminalCommand
    data object Heal : TerminalCommand
    data object Evolve : TerminalCommand
    data object Talk : TerminalCommand
    data object History : TerminalCommand
    data object Clear : TerminalCommand

    /** `name <str>` — 이미 대문자·12자 절단됨. 빈 문자열이면 usage. */
    data class Name(val value: String) : TerminalCommand
    data class Echo(val text: String) : TerminalCommand
    data object Ls : TerminalCommand
    data class Cat(val file: String?) : TerminalCommand

    // ── 피어 / 레이더 (2차 마일스톤) ──

    /** `scan` / `peers` / `radar` — 레이더 화면 전환 + 근처 유닛 목록. */
    data object Scan : TerminalCommand

    /** `sonar` / `back` — 소나 화면 복귀. */
    data object Sonar : TerminalCommand

    /** `bond <name>` — 특정 피어 관계 조회. 인자 없으면 usage. */
    data class Bond(val name: String?) : TerminalCommand

    /** `accept` — 대기 도전 수락 → 전투 시작(3차). */
    data object Accept : TerminalCommand

    /** `decline` — 대기 요청 거절. */
    data object Decline : TerminalCommand

    /** `dnd [on|off]` — 방해금지. 인자 없으면 토글. */
    data class Dnd(val arg: String?) : TerminalCommand

    // ── 전투 (3차 마일스톤) ──

    /** `challenge <name>` — 특정 피어에게 도전(성격별 수락 확률). 인자 없으면 usage. */
    data class Challenge(val name: String?) : TerminalCommand

    /** `flee` / `forfeit` — 진행 중인 전투 이탈. */
    data object Flee : TerminalCommand

    // ── 계보 (4차 마일스톤) ──

    /** `tree` — 계보 화면으로 전환. */
    data object Tree : TerminalCommand

    /** `reset` — 현재 개체 아카이브 + 새 알 시작(새 이름/타임스탬프는 ViewModel이 주입). */
    data object Reset : TerminalCommand

    /** 알려진 명령이지만 후속 마일스톤(모듈 미탑재). */
    data class ModulePending(val verb: String) : TerminalCommand

    /** 빈 입력 — 무시. */
    data object Empty : TerminalCommand

    /** 알 수 없는 명령. */
    data class Unknown(val verb: String) : TerminalCommand
}
