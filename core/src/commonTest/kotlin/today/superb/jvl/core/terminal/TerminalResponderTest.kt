package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun rng() = SeededRng(42L)
private fun state() = GameState.initial(name = "NAUTI", now = 0L)
private fun reply(input: String, s: GameState = state()) = respond(parse(input), s, rng())

class TerminalResponderTest {

    @Test
    fun feed_returns_lines_and_feed_action() {
        val r = reply("feed")
        assertEquals(Action.Feed, r.action)
        assertTrue(r.lines.any { it.text == "▸ unit fed. hunger -25%." })
    }

    @Test
    fun feed_with_item_echoes_item() {
        val r = reply("feed kelp")
        assertTrue(r.lines.any { it.text == "dispensing kelp..." })
    }

    @Test
    fun ping_dispatches_ping_action() {
        assertEquals(Action.Ping, reply("ping").action)
    }

    @Test
    fun scold_dispatches_discipline_action() {
        assertEquals(Action.Discipline, reply("scold").action)
    }

    @Test
    fun help_lists_commands_without_action() {
        val r = reply("help")
        assertNull(r.action)
        assertEquals("AVAILABLE COMMANDS:", r.lines.first().text)
    }

    @Test
    fun ping_response_reflects_awake_state() {
        val awake = reply("ping", state())
        assertTrue(awake.lines.any { it.text.contains("active") })
        val asleep = reply("ping", state().copy(asleep = true))
        assertTrue(asleep.lines.any { it.text.contains("asleep") })
    }

    @Test
    fun sleep_and_wake_are_idempotent() {
        // sleep: awake → 잠들기(액션), 이미 자고 있으면 no-op
        val sleeping = reply("sleep")
        assertEquals(Action.Sleep, sleeping.action)
        assertTrue(sleeping.lines.any { it.text == "▸ lights out. unit asleep." })

        val alreadyAsleep = reply("sleep", state().copy(asleep = true))
        assertNull(alreadyAsleep.action)
        assertTrue(alreadyAsleep.lines.any { it.text == "▸ already resting." })

        // wake: asleep → 깨우기(액션), 이미 깨어 있으면 no-op
        val waking = reply("wake", state().copy(asleep = true))
        assertEquals(Action.Sleep, waking.action)
        assertTrue(waking.lines.any { it.text == "▸ unit awakened." })

        val alreadyAwake = reply("wake")
        assertNull(alreadyAwake.action)
        assertTrue(alreadyAwake.lines.any { it.text == "▸ already awake." })
    }

    @Test
    fun evolve_unavailable_when_not_ready() {
        val r = reply("evolve")
        assertNull(r.action)
        assertTrue(r.lines.any { it.text.startsWith("evolution unavailable") })
    }

    @Test
    fun evolve_initiates_when_ready() {
        val ready = state().copy(canEvolve = true)
        assertEquals(Action.Evolve, reply("evolve", ready).action)
    }

    @Test
    fun name_with_value_renames_else_usage() {
        assertEquals(Action.Rename("KRAKEN"), reply("name kraken").action)
        val usage = reply("name")
        assertNull(usage.action)
        assertTrue(usage.lines.any { it.text == "usage: name <string>" })
    }

    @Test
    fun clear_sets_clear_screen_flag() {
        val r = reply("clear")
        assertTrue(r.clearScreen)
        assertEquals(TerminalLineKind.Sys, r.lines.first().kind)
    }

    @Test
    fun talk_prefixes_unit_name() {
        val r = reply("talk", state().copy(stage = Stage.Egg))
        assertEquals("NAUTI > tap... tap... tap... [muffled]", r.lines.first().text)
    }

    @Test
    fun module_pending_for_deferred_verbs() {
        // 설정(mute/sound)만 아직 module pending(레이더·전투·계보는 승격됨).
        assertTrue(reply("mute").lines.first().text.contains("module offline"))
        assertTrue(reply("sound").lines.first().text.contains("module offline"))
    }

    @Test
    fun unknown_command_says_not_found() {
        assertTrue(reply("frobnicate").lines.first().text.contains("command not found"))
    }

    @Test
    fun empty_input_yields_nothing() {
        val r = reply("   ")
        assertTrue(r.lines.isEmpty())
        assertNull(r.action)
    }

    @Test
    fun cat_notes_reads_file_else_errors() {
        assertTrue(reply("cat notes.txt").lines.any { it.text == "— field notes —" })
        assertTrue(reply("cat secret").lines.first().text.contains("no such file"))
    }

    @Test
    fun cat_with_no_arg_reports_empty_file() {
        // 데모는 JS의 `undefined`를 출력하나, 포팅은 빈 문자열로 정규화(의도적 개선).
        assertEquals("cat: : no such file", reply("cat").lines.first().text)
    }

    @Test
    fun help_lists_only_implemented_commands() {
        // HELP_LINES는 손유지 리스트 — 미구현/미지원 verb가 섞이면 드리프트. 모든 항목이 실제로 parse되는지 가드.
        val verbs = HELP_LINES
            .drop(1) // "AVAILABLE COMMANDS:" 헤더
            .map { it.substringBefore("—").trim() }
            .filter { it.isNotEmpty() }
            .flatMap { it.substringBefore("<").substringBefore("[").split("/", " ") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertTrue(verbs.isNotEmpty())
        for (verb in verbs) {
            val cmd = parse(verb)
            assertTrue(
                cmd !is TerminalCommand.Unknown && cmd !is TerminalCommand.ModulePending,
                "help가 '$verb'를 안내하지만 parser 결과가 $cmd",
            )
        }
    }
}
