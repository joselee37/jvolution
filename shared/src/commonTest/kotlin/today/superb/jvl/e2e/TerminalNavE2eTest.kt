package today.superb.jvl.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.View
import today.superb.jvl.viewmodel.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 터미널 파이프라인 + 뷰 네비게이션 e2e — submitCommand가 모든 기능의 단일 입력 게이트임을 검증.
 * 입력 에코·응답·clear·미지 명령·readout·뷰 전환을 풀플로우로 확인.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TerminalNavE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun vm() = GameViewModel(SeededRng(42L), autoTick = false)

    @Test
    fun command_echoes_shell_prompt_and_response() = runTest(dispatcher) {
        val vm = vm()
        val name = vm.state.value.name.lowercase()
        vm.submitCommand("feed"); runCurrent()
        assertTrue(vm.terminal.value.any { it.text == "$name@nautilus:~$ feed" }, "입력 에코(셸 프롬프트)")
        assertTrue(vm.terminal.value.any { it.text.contains("unit fed") }, "응답 줄")
    }

    @Test
    fun clear_resets_terminal_to_banner() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("feed"); vm.submitCommand("play"); runCurrent()
        assertTrue(vm.terminal.value.size > 2)
        vm.submitCommand("clear")
        assertEquals(1, vm.terminal.value.size, "clear는 화면을 비움")
        assertTrue(vm.terminal.value.first().text.contains("CLEARED"))
    }

    @Test
    fun unknown_command_echoes_error_without_state_change() = runTest(dispatcher) {
        val vm = vm()
        val cyclesBefore = vm.state.value.cycles
        vm.submitCommand("frobnicate now"); runCurrent()
        assertTrue(vm.terminal.value.any { it.text.contains("command not found") })
        assertEquals(cyclesBefore, vm.state.value.cycles, "미지 명령은 상태 불변")
    }

    @Test
    fun readouts_render_status_whoami_help() = runTest(dispatcher) {
        val vm = vm()
        val name = vm.state.value.name
        vm.submitCommand("status"); runCurrent()
        assertTrue(vm.terminal.value.any { it.text.contains("UNIT") }, "status 박스")
        assertTrue(vm.terminal.value.any { it.text.contains(name) }, "status에 유닛명")
        assertTrue(vm.terminal.value.any { it.text.contains("EVOLUTION") }, "status 진화 섹션")

        vm.submitCommand("whoami"); runCurrent()
        assertTrue(vm.terminal.value.any { it.text.contains("OPERATOR_ID") }, "whoami readout")

        vm.submitCommand("help"); runCurrent()
        assertTrue(vm.terminal.value.any { it.text.contains("feed") }, "help 명령 목록")
    }

    @Test
    fun view_navigation_round_trip_across_all_views() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(View.Sonar, vm.state.value.view)
        vm.submitCommand("scan"); runCurrent()
        assertEquals(View.Radar, vm.state.value.view)
        vm.submitCommand("tree"); runCurrent()
        assertEquals(View.Tree, vm.state.value.view)
        vm.submitCommand("genome"); runCurrent()
        assertEquals(View.Genome, vm.state.value.view)
        vm.submitCommand("sonar"); runCurrent()
        assertEquals(View.Sonar, vm.state.value.view, "소나 복귀")
    }

    @Test
    fun sleep_and_wake_are_idempotent() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.state.value.asleep)
        vm.submitCommand("wake"); runCurrent()        // 이미 깨어 있음 → no-op
        assertFalse(vm.state.value.asleep)
        vm.submitCommand("sleep"); runCurrent()
        assertTrue(vm.state.value.asleep)
        vm.submitCommand("sleep"); runCurrent()        // 이미 자는 중 → no-op
        assertTrue(vm.state.value.asleep)
        vm.submitCommand("wake"); runCurrent()
        assertFalse(vm.state.value.asleep)
    }
}
