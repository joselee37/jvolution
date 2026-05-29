package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val rng = SeededRng(42L)
private fun state() = GameState.initial(name = "NAUTI", now = 0L)
private fun reply(input: String, s: GameState = state()) = respond(parse(input), s, rng)

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
    fun sleep_response_uses_pre_toggle_state() {
        // awake → going to sleep
        assertTrue(reply("sleep").lines.any { it.text == "▸ lights out. unit asleep." })
        // asleep → waking
        assertTrue(reply("wake", state().copy(asleep = true)).lines.any { it.text == "▸ unit awakened." })
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
    fun module_pending_for_radar_family() {
        assertTrue(reply("radar").lines.first().text.contains("module offline"))
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
}
