package today.superb.jvl.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.PeerRequest
import today.superb.jvl.core.Personality
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.terminal.TerminalLineKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun readyPeer(id: String = "hrrk", name: String = "HRRK", cooldown: Float = 0f) =
    Peer(id, name, Species.Squid, Stage.Adult, Personality.Aggressive, bearing = 0f, range = 0.5f, bearingVel = 12f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = cooldown)

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
    fun scold_flash_expires_after_two_seconds() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("scold")
        runCurrent()
        assertTrue(vm.state.value.disciplineFlash, "scold 직후 flash on")

        advanceTimeBy(2001)
        runCurrent()
        assertFalse(vm.state.value.disciplineFlash, "2s 후 flash 해제")
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

    // ── 2차: 피어 / 레이더 ──────────────────────────────────────

    @Test
    fun peer_challenge_event_echoes_to_terminal_as_sys() = runTest(dispatcher) {
        val seed = GameState.initial("NAUTI", 0L, listOf(readyPeer(cooldown = 0f)))
        // initialState 제공 시 init에서 rng 미소비 → PeerTick이 [gate, roll, cooldown] 3개를 결정 소비.
        val vm = GameViewModel(FixedRng(listOf(0f, 0f, 0f)), autoTick = false, initialState = seed)
        vm.dispatch(Action.PeerTick(1f))
        runCurrent()
        assertNotNull(vm.state.value.pendingRequest)
        assertTrue(vm.terminal.value.any { it.kind == TerminalLineKind.Sys && it.text.contains("INCOMING") })
    }

    @Test
    fun decline_command_echoes_decline_line() = runTest(dispatcher) {
        val seed = GameState.initial("NAUTI", 0L, listOf(readyPeer(cooldown = 100f)))
            .copy(pendingRequest = PeerRequest("hrrk", RequestType.Challenge))
        val vm = GameViewModel(SeededRng(42L), autoTick = false, initialState = seed)
        vm.submitCommand("decline")
        assertNull(vm.state.value.pendingRequest)
        assertTrue(vm.terminal.value.any { it.text == "▸ declined HRRK." })
    }

    @Test
    fun fresh_game_has_full_peer_roster() {
        val vm = vm()
        assertEquals(7, vm.state.value.peers.size)
    }

    // 주: 피어 틱 *루프*(autoTick 코루틴)는 단위 테스트하지 않는다 — runTest에서 무한 while(true)
    // 루프는 정리 단계(advanceUntilIdle)가 끝나지 않아 행을 유발한다(기존 care 루프도 동일 이유로
    // autoTick=false). PeerTick 로직은 reducer 테스트 + 위 dispatch(PeerTick) 에코 테스트가 커버하고,
    // "1s마다 dispatch"는 검증된 care 루프와 동형이라 앱 실행으로 검증한다.
}
