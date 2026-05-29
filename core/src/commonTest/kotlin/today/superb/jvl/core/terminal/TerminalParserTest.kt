package today.superb.jvl.core.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TerminalParserTest {

    @Test
    fun blank_input_is_empty() {
        assertEquals(TerminalCommand.Empty, parse("   "))
        assertEquals(TerminalCommand.Empty, parse(""))
    }

    @Test
    fun trims_and_lowercases_verb() {
        assertEquals(TerminalCommand.Status, parse("  STATUS  "))
        assertEquals(TerminalCommand.Help, parse("Help"))
    }

    @Test
    fun sleep_and_wake_are_distinct_commands() {
        // 멱등 처리를 위해 분리(responder가 현재 상태를 보고 no-op 판단).
        assertEquals(TerminalCommand.Sleep, parse("sleep"))
        assertEquals(TerminalCommand.Wake, parse("wake"))
    }

    @Test
    fun scold_and_discipline_map_to_scold() {
        assertEquals(TerminalCommand.Scold, parse("scold"))
        assertEquals(TerminalCommand.Scold, parse("discipline"))
    }

    @Test
    fun feed_captures_optional_item() {
        assertEquals(TerminalCommand.Feed("kelp"), parse("feed kelp"))
        assertEquals(TerminalCommand.Feed(null), parse("feed"))
    }

    @Test
    fun name_is_uppercased_and_clamped_to_twelve() {
        assertEquals(TerminalCommand.Name("KRAKEN"), parse("name kraken"))
        assertEquals(TerminalCommand.Name("ABCDEFGHIJKL"), parse("name abcdefghijklmnop"))
        assertEquals(TerminalCommand.Name(""), parse("name"))
    }

    @Test
    fun echo_joins_args() {
        assertEquals(TerminalCommand.Echo("hello world"), parse("echo hello world"))
    }

    @Test
    fun cat_captures_file() {
        assertEquals(TerminalCommand.Cat("notes.txt"), parse("cat notes.txt"))
    }

    @Test
    fun radar_family_is_module_pending() {
        assertEquals(TerminalCommand.ModulePending("radar"), parse("radar"))
        assertEquals(TerminalCommand.ModulePending("tree"), parse("tree"))
        assertEquals(TerminalCommand.ModulePending("challenge"), parse("challenge hrrk"))
    }

    @Test
    fun unknown_verb_falls_through() {
        val cmd = parse("frobnicate now")
        assertIs<TerminalCommand.Unknown>(cmd)
        assertEquals("frobnicate", cmd.verb)
    }
}
