package today.superb.jvl.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.SeededRng
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GameViewModel 단위 테스트 — autoTick=false로 무한 tick 루프를 끄고 명령 파이프라인/스케줄러만 검증.
 * viewModelScope가 Main.immediate를 쓰므로 setMain(공유 TestDispatcher)으로 가상 시간 제어.
 * resetMain은 @AfterTest에서 — runTest의 코루틴 정리 후에 해제해야 함.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = GameViewModel(SeededRng(42L), autoTick = false)

    @Test
    fun feed_echoes_dynamic_prompt_and_dispatches_action() = runTest(dispatcher) {
        val vm = vm()
        val name = vm.state.value.name.lowercase()
        val hungerBefore = vm.state.value.hunger

        vm.submitCommand("feed")

        assertTrue(vm.terminal.value.any { it.text == "$name@nautilus:~$ feed" }, "동적 프롬프트 에코")
        assertTrue(vm.terminal.value.any { it.text.contains("unit fed") }, "응답 줄")
        assertTrue(vm.state.value.hunger < hungerBefore, "Feed 액션 디스패치됨")
        assertEquals("NOM NOM", vm.state.value.toast)
    }

    @Test
    fun toast_clears_after_timer() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("feed")
        assertEquals("NOM NOM", vm.state.value.toast)

        advanceTimeBy(1500)   // > TOAST_MS(1400)
        runCurrent()
        assertNull(vm.state.value.toast, "ViewModel 타이머가 ClearToast 디스패치")
    }

    @Test
    fun clear_resets_terminal_history() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("feed")
        vm.submitCommand("clear")
        assertEquals(1, vm.terminal.value.size)
        assertTrue(vm.terminal.value.first().text.contains("CLEARED"))
    }

    @Test
    fun blank_input_is_noop() = runTest(dispatcher) {
        val vm = vm()
        val sizeBefore = vm.terminal.value.size
        vm.submitCommand("   ")
        assertEquals(sizeBefore, vm.terminal.value.size)
        assertNull(vm.state.value.toast)
    }

    @Test
    fun unknown_command_echoes_but_no_action() = runTest(dispatcher) {
        val vm = vm()
        val cyclesBefore = vm.state.value.cycles
        vm.submitCommand("frobnicate")
        assertTrue(vm.terminal.value.any { it.text.contains("command not found") })
        assertEquals(cyclesBefore, vm.state.value.cycles, "미지 명령은 상태 변화 없음")
    }
}
