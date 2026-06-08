package today.superb.jvl.core.terminal

private const val NAME_MAX = 12

private val WHITESPACE = Regex("\\s+")

/**
 * 터미널 입력 한 줄을 [TerminalCommand]로 파싱한다. 데모 `runCommand`의 토큰화 1:1:
 * trim → 빈 입력 무시 → 전체 lowercase → 공백 분리 → verb + args.
 */
fun parse(input: String): TerminalCommand {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return TerminalCommand.Empty

    val tokens = trimmed.lowercase().split(WHITESPACE)
    val verb = tokens.first()
    val args = tokens.drop(1)

    return when (verb) {
        "help" -> TerminalCommand.Help
        "whoami" -> TerminalCommand.WhoAmI
        "status" -> TerminalCommand.Status
        "ping" -> TerminalCommand.Ping
        "feed" -> TerminalCommand.Feed(args.firstOrNull())
        "play" -> TerminalCommand.Play
        "clean" -> TerminalCommand.Clean
        "sleep" -> TerminalCommand.Sleep
        "wake" -> TerminalCommand.Wake
        "train" -> TerminalCommand.Train
        "scold", "discipline" -> TerminalCommand.Scold
        "heal" -> TerminalCommand.Heal
        "evolve" -> TerminalCommand.Evolve
        "talk" -> TerminalCommand.Talk
        "name" -> TerminalCommand.Name(args.joinToString(" ").uppercase().take(NAME_MAX))
        "history" -> TerminalCommand.History
        "clear" -> TerminalCommand.Clear
        "echo" -> TerminalCommand.Echo(args.joinToString(" "))
        "ls", "dir" -> TerminalCommand.Ls
        "cat" -> TerminalCommand.Cat(args.firstOrNull())
        "scan", "peers", "radar" -> TerminalCommand.Scan
        "sonar", "back" -> TerminalCommand.Sonar
        "bond" -> TerminalCommand.Bond(args.firstOrNull())
        "accept" -> TerminalCommand.Accept
        "decline" -> TerminalCommand.Decline
        "dnd" -> TerminalCommand.Dnd(args.firstOrNull())
        "challenge" -> TerminalCommand.Challenge(args.firstOrNull())
        "flee", "forfeit" -> TerminalCommand.Flee
        "tree" -> TerminalCommand.Tree
        "reset" -> TerminalCommand.Reset
        "mute", "sound" -> TerminalCommand.Sound
        else -> TerminalCommand.Unknown(verb)
    }
}
