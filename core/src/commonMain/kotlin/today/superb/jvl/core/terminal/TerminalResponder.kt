package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Rng
import today.superb.jvl.core.talkLine

private val HELP_LINES = listOf(
    "AVAILABLE COMMANDS:",
    "  status        — vitals readout (boxed)",
    "  ping          — sonar pulse on current view",
    "  feed [item]   — feed creature",
    "  play          — happiness +",
    "  clean         — hygiene +",
    "  sleep / wake  — toggle sleep",
    "  train         — training +",
    "  scold         — discipline +",
    "  heal          — apply biopatch",
    "  evolve        — advance stage",
    "  talk          — converse",
    "  name <str>    — rename unit",
    "  whoami        — operator info",
    "  history       — show log",
    "  clear         — clear screen",
)

private val WHOAMI_LINES = listOf(
    "OPERATOR_ID: NAUTILUS-7",
    "CLEARANCE: TIER 3 — CARETAKER",
    "STATION: CIC // ABYSSAL OBSERVATION POST",
)

private val NOTES_LINES = listOf(
    "— field notes —",
    "subj. responds to pings between 3-7s intervals.",
    "avoids glare from main reflector. likes warm currents.",
    "last molt produced 3.4g organic dust.",
)

private val LS_LINES = listOf("./bin/", "./log/", "./unit.dat", "./telemetry.dat", "./notes.txt")

private fun out(vararg text: String): List<TerminalLine> =
    text.map { TerminalLine(TerminalLineKind.Out, it) }

private fun out(text: List<String>): List<TerminalLine> =
    text.map { TerminalLine(TerminalLineKind.Out, it) }

/**
 * 파싱된 명령을 출력 줄 + 선택적 게임 [Action]으로 변환한다. 순수 함수.
 * 데모 `runCommand`의 verb 분기 1:1(1차 마일스톤 범위).
 *
 * @param rng `talk` 대사 선택에 사용(결정성 위해 주입).
 */
fun respond(command: TerminalCommand, state: GameState, rng: Rng): TerminalResponse = when (command) {
    TerminalCommand.Empty -> TerminalResponse(emptyList())

    TerminalCommand.Help -> TerminalResponse(out(HELP_LINES))
    TerminalCommand.WhoAmI -> TerminalResponse(out(WHOAMI_LINES))
    TerminalCommand.Status -> TerminalResponse(out(renderStatus(state)))

    TerminalCommand.Ping -> TerminalResponse(
        out(
            "transmitting sonar pulse @ 14.2kHz...",
            "  ▸ contact at bearing 270°, range 24.7m",
            "  ▸ biomass signature: ORGANIC // CONFINED",
            "  ▸ unit responsive — ${if (state.asleep) "asleep" else "active"}",
            "PING_OK.",
        ),
        action = Action.Ping,
    )

    is TerminalCommand.Feed -> TerminalResponse(
        out("dispensing ${command.item ?: "standard ration"}...", "▸ unit fed. hunger -25%."),
        action = Action.Feed,
    )

    TerminalCommand.Play -> TerminalResponse(
        out("initiating play sequence... ✓", "▸ happiness +20%."),
        action = Action.Play,
    )

    TerminalCommand.Clean -> TerminalResponse(
        out("flushing tank... ✓", "▸ hygiene restored."),
        action = Action.Clean,
    )

    TerminalCommand.Sleep -> TerminalResponse(
        // 응답 텍스트는 토글 전 상태 기준(데모와 동일).
        out(if (state.asleep) "▸ unit awakened." else "▸ lights out. unit asleep."),
        action = Action.Sleep,
    )

    TerminalCommand.Train -> TerminalResponse(
        out("running drill protocol...", "▸ training +15%. discipline +5%."),
        action = Action.Train,
    )

    TerminalCommand.Scold -> TerminalResponse(
        out("issuing reprimand...", "▸ discipline +10%. happiness -8%."),
        action = Action.Discipline,
    )

    TerminalCommand.Heal -> TerminalResponse(
        out("applying biopatch...", "▸ energy +30%. happiness +5%."),
        action = Action.Heal,
    )

    TerminalCommand.Evolve ->
        if (state.canEvolve) {
            TerminalResponse(
                out("◢◤ EVOLUTION SEQUENCE INITIATED ◢◤", "morphological flux detected.", "standby..."),
                action = Action.Evolve,
            )
        } else {
            val progress = (state.evolveProgress * 100).toInt()
            TerminalResponse(out("evolution unavailable. progress $progress%."))
        }

    TerminalCommand.Talk -> TerminalResponse(out("${state.name} > ${talkLine(state, rng)}"))

    is TerminalCommand.Name ->
        if (command.value.isBlank()) {
            TerminalResponse(out("usage: name <string>"))
        } else {
            TerminalResponse(out("▸ unit renamed: ${command.value}"), action = Action.Rename(command.value))
        }

    TerminalCommand.History -> TerminalResponse(
        out(state.log.take(12).map { "[${it.cycle.toString().padStart(4, '0')}] ${it.msg}" }),
    )

    TerminalCommand.Clear -> TerminalResponse(
        listOf(TerminalLine(TerminalLineKind.Sys, "◢◤ TERMINAL CLEARED")),
        clearScreen = true,
    )

    is TerminalCommand.Echo -> TerminalResponse(out(command.text))

    TerminalCommand.Ls -> TerminalResponse(out(LS_LINES))

    is TerminalCommand.Cat ->
        if (command.file == "notes.txt") TerminalResponse(out(NOTES_LINES))
        else TerminalResponse(out("cat: ${command.file ?: ""}: no such file"))

    is TerminalCommand.ModulePending ->
        TerminalResponse(out("${command.verb}: module offline — coming in a later milestone."))

    is TerminalCommand.Unknown ->
        TerminalResponse(out("${command.verb}: command not found. try `help`."))
}
