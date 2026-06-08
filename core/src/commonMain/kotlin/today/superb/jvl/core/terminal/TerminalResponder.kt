package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.Rng
import today.superb.jvl.core.View
import today.superb.jvl.core.battle.acceptOdds
import today.superb.jvl.core.talkLine

/** 전투 중 허용되는 명령(데모 BATTLE_ALLOWED). 그 외는 "locked"로 거부. */
private fun allowedInBattle(command: TerminalCommand): Boolean = when (command) {
    TerminalCommand.Help, TerminalCommand.WhoAmI, TerminalCommand.Clear,
    TerminalCommand.Flee, TerminalCommand.Empty -> true
    is TerminalCommand.Echo, is TerminalCommand.Dnd -> true
    is TerminalCommand.ModulePending -> command.verb == "mute" || command.verb == "sound"
    else -> false
}

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
fun respond(command: TerminalCommand, state: GameState, rng: Rng): TerminalResponse {
    // 전투 중 명령 잠금 — 허용 목록 외 명령(케어 등)은 거부한다(데모 in-battle lock).
    if (state.battle != null && !allowedInBattle(command)) {
        return TerminalResponse(out("locked — engagement in progress. (`flee` to disengage)"))
    }
    return when (command) {
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

    // ── 피어 / 레이더 (2차) — 데모 runCommand의 peer 분기 1:1 ──

    TerminalCommand.Scan -> {
        val onRadar = state.view == View.Radar
        val tail = if (onRadar) "▸ sweep continuing on primary display."
        else "▸ radar scope active. type `sonar` to return."
        TerminalResponse(
            out(renderScan(state.peers) + listOf("", tail)),
            action = if (onRadar) null else Action.SetView(View.Radar),
        )
    }

    TerminalCommand.Sonar ->
        if (state.view == View.Sonar) TerminalResponse(out("▸ already on sonar."))
        else TerminalResponse(out("▸ returning to sonar."), action = Action.SetView(View.Sonar))

    is TerminalCommand.Bond -> {
        val target = command.name
        if (target.isNullOrBlank()) {
            TerminalResponse(out("usage: bond <name>"))
        } else {
            val peer = findPeer(state.peers, target)
            if (peer == null) TerminalResponse(out("no peer named $target."))
            else TerminalResponse(out(renderBond(peer)))
        }
    }

    TerminalCommand.Accept -> {
        val req = state.pendingRequest
        if (req == null) {
            TerminalResponse(out("no incoming request."))
        } else {
            val name = state.peers.find { it.id == req.from }?.name ?: req.from
            TerminalResponse(
                out("▸ accepted $name's challenge.", "  ENGAGE — switching to combat console."),
                action = Action.BattleStart(req.from),
            )
        }
    }

    TerminalCommand.Decline ->
        if (state.pendingRequest == null) TerminalResponse(out("no incoming request."))
        else TerminalResponse(emptyList(), action = Action.DeclineRequest)

    is TerminalCommand.Dnd -> {
        val forced = state.battle != null   // 전투 중 DND 강제 on
        val target: Boolean? = when (command.arg) {
            null, "", "toggle" -> !state.dnd
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            else -> null
        }
        when {
            target == null -> TerminalResponse(out("usage: dnd [on|off]"))
            forced && target == false ->
                TerminalResponse(out("▸ dnd is forced on while engaged.", "  (`flee` to disengage, then `dnd off`)"))
            target == state.dnd && !forced -> TerminalResponse(out("▸ dnd already ${if (target) "on" else "off"}."))
            else -> {
                val req = state.pendingRequest
                val extra = if (target && req != null && req.type == RequestType.Challenge) {
                    val name = state.peers.find { it.id == req.from }?.name ?: "unknown"
                    listOf("▸ pending challenge from $name declined.")
                } else {
                    emptyList()
                }
                val verb = if (target) "ON" else "OFF"
                val effect = if (target) "blocked" else "allowed"
                TerminalResponse(
                    out(listOf("▸ dnd $verb — incoming challenges $effect.") + extra),
                    action = Action.SetDnd(target),
                )
            }
        }
    }

    // ── 전투 (3차) ──

    is TerminalCommand.Challenge -> {
        val target = command.name
        if (target.isNullOrBlank()) {
            TerminalResponse(out("usage: challenge <name>"))
        } else {
            val peer = findPeer(state.peers, target)
            when {
                peer == null -> TerminalResponse(out("no peer named $target."))
                rng.nextFloat() < acceptOdds(peer.personality) -> TerminalResponse(
                    out("▸ hailing ${peer.name}...", "▸ ${peer.name} accepts. ENGAGE."),
                    action = Action.BattleStart(peer.id),
                )
                else -> TerminalResponse(out("▸ hailing ${peer.name}...", "▸ ${peer.name} drifts away. (challenge declined)"))
            }
        }
    }

    TerminalCommand.Flee ->
        if (state.battle == null) TerminalResponse(out("no active engagement to flee from."))
        else TerminalResponse(out("▸ disengaging — pulse withdrawn."), action = Action.BattleFlee)

    is TerminalCommand.ModulePending ->
        TerminalResponse(out("${command.verb}: module offline — coming in a later milestone."))

    is TerminalCommand.Unknown ->
        TerminalResponse(out("${command.verb}: command not found. try `help`."))
    }
}
