package today.superb.jvl.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * breed 플로우(PAIR-BOND ASSAY) presentation 전이 — 터미널/칩이 즉시 교배하지 않고 breedTarget을
 * 설정(오버레이 열기), confirm에서만 dispatch한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelBreedTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun mate() = Peer(
        "hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f,
    )

    private fun vm() = GameViewModel(
        SeededRng(42L), autoTick = false,
        initialState = GameState.initial("NAUTI", 0L, listOf(mate())),
    )

    @Test
    fun terminal_breed_opens_assay_without_breeding() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("breed hrrk")
        runCurrent()
        assertEquals("hrrk", vm.breedTarget.value, "ASSAY 대상 설정")
        assertEquals(1, vm.state.value.gen, "터미널 breed는 즉시 교배하지 않는다")
        assertTrue(vm.terminal.value.any { it.text.contains("predicted inbreeding") }, "예측 근친계수 에코")
    }

    @Test
    fun confirm_breed_advances_generation_and_clears_target() = runTest(dispatcher) {
        val vm = vm()
        vm.requestBreed("hrrk")
        assertEquals("hrrk", vm.breedTarget.value)
        vm.confirmBreed()
        runCurrent()
        assertNull(vm.breedTarget.value, "확정 후 대상 해제")
        assertEquals(2, vm.state.value.gen, "교배로 다음 세대")
        assertEquals("hrrk", vm.state.value.fatherId, "피어가 부계")
    }

    @Test
    fun cancel_breed_clears_target_without_breeding() = runTest(dispatcher) {
        val vm = vm()
        vm.requestBreed("hrrk")
        vm.cancelBreed()
        assertNull(vm.breedTarget.value)
        assertEquals(1, vm.state.value.gen, "취소는 교배하지 않음")
    }

    @Test
    fun request_breed_ignores_unknown_peer() = runTest(dispatcher) {
        val vm = vm()
        vm.requestBreed("nobody")
        assertNull(vm.breedTarget.value)
    }
}
