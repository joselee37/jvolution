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

    /** 알려진 명령이지만 후속 마일스톤(모듈 미탑재). */
    data class ModulePending(val verb: String) : TerminalCommand

    /** 빈 입력 — 무시. */
    data object Empty : TerminalCommand

    /** 알 수 없는 명령. */
    data class Unknown(val verb: String) : TerminalCommand
}
