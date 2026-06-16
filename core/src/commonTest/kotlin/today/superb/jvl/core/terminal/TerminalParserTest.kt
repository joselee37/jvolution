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
    fun peer_verbs_parse_to_real_commands() {
        // 2차에서 승격: scan/peers/radar → Scan, sonar/back → Sonar, bond/accept/decline/dnd.
        assertEquals(TerminalCommand.Scan, parse("radar"))
        assertEquals(TerminalCommand.Scan, parse("peers"))
        assertEquals(TerminalCommand.Sonar, parse("back"))
        assertEquals(TerminalCommand.Bond("hrrk"), parse("bond hrrk"))
        assertEquals(TerminalCommand.Accept, parse("accept"))
        assertEquals(TerminalCommand.Decline, parse("decline"))
        assertEquals(TerminalCommand.Dnd("on"), parse("dnd on"))
        assertEquals(TerminalCommand.Dnd(null), parse("dnd"))
    }

    @Test
    fun all_verbs_parse_to_real_commands() {
        // 6차로 전체 패리티 달성 — ModulePending 없음.
        assertEquals(TerminalCommand.Challenge("hrrk"), parse("challenge hrrk"))
        assertEquals(TerminalCommand.Flee, parse("flee"))
        assertEquals(TerminalCommand.Flee, parse("forfeit"))
        assertEquals(TerminalCommand.Tree(null), parse("tree"))
        assertEquals(TerminalCommand.Breed("lumen"), parse("breed lumen"))
        assertEquals(TerminalCommand.Sound, parse("mute"))
        assertEquals(TerminalCommand.Sound, parse("sound"))
    }

    @Test
    fun unknown_verb_falls_through() {
        val cmd = parse("frobnicate now")
        assertIs<TerminalCommand.Unknown>(cmd)
        assertEquals("frobnicate", cmd.verb)
    }
}
