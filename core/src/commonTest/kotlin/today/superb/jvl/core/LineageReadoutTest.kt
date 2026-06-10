package today.superb.jvl.core

import today.superb.jvl.core.terminal.parse
import today.superb.jvl.core.terminal.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineageReadoutTest {

    private val rng = SeededRng(42L)

    /** gen 2 활성 + gen 1 은퇴 상태. */
    private fun lineageState(): GameState {
        val g1 = reduce(GameState.initial("ALPHA", 1_000L), Action.Feed, rng)
        return reduce(g1, Action.Reset(newName = "BETA", now = 2_000L), rng)
    }

    @Test
    fun tree_without_arg_switches_view() {
        val r = respond(parse("tree"), lineageState(), rng)
        assertEquals(Action.SetView(View.Tree), r.action)
    }

    @Test
    fun tree_with_retired_gen_renders_detail_without_view_change() {
        val r = respond(parse("tree 1"), lineageState(), rng)
        assertNull(r.action, "상세 조회는 화면을 바꾸지 않는다")
        assertTrue(r.lines.any { it.text.contains("G01_ALPHA") })
        assertTrue(r.lines.any { it.text.contains("✟ retired") })
    }

    @Test
    fun tree_with_active_gen_renders_live_detail() {
        val r = respond(parse("tree 2"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("G02_BETA") })
        assertTrue(r.lines.any { it.text.contains("● active") })
    }

    @Test
    fun tree_with_unknown_gen_says_no_such_generation() {
        val r = respond(parse("tree 9"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("no such generation") })
        assertNull(r.action)
    }

    @Test
    fun tree_with_non_numeric_arg_prints_usage() {
        val r = respond(parse("tree abc"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("usage: tree") })
        assertNull(r.action)
    }

    @Test
    fun help_lists_all_implemented_commands() {
        val help = respond(parse("help"), GameState.initial("UNIT", 0L), rng)
            .lines.joinToString("\n") { it.text }
        for (cmd in listOf("scan", "tree", "bond", "challenge", "accept", "decline", "dnd", "flee", "mute", "reset")) {
            assertTrue(help.contains(cmd), "help에 $cmd 누락")
        }
    }
}
