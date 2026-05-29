package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Rng
import today.superb.jvl.core.talkLine

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

    // sleep/wake는 멱등 — 이미 원하는 상태면 no-op 메시지만, 아니면 토글 액션을 흘린다(PLAN.md:93).
    TerminalCommand.Sleep ->
        if (state.asleep) TerminalResponse(out("▸ already resting."))
        else TerminalResponse(out("▸ lights out. unit asleep."), action = Action.Sleep)

    TerminalCommand.Wake ->
        if (!state.asleep) TerminalResponse(out("▸ already awake."))
        else TerminalResponse(out("▸ unit awakened."), action = Action.Sleep)

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
