package today.superb.jvl.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.Action
import today.superb.jvl.core.Mood
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Stage
import today.superb.jvl.core.moodLabel
import today.superb.jvl.viewmodel.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 케어 루프 e2e — ViewModel을 명령으로 구동하고 스탯·mood·진화를 풀플로우로 검증.
 * autoTick=false + StandardTestDispatcher로 시간을 가상 제어(완전 결정론).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareLifecycleE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun vm() = GameViewModel(SeededRng(42L), autoTick = false)

    @Test
    fun drift_to_hungry_then_feed_recovers_and_toast_expires() = runTest(dispatcher) {
        val vm = vm()
        // 시간 압축 — 큰 tick으로 hunger를 임계(0.75) 위로 드리프트.
        vm.dispatch(Action.Tick(30f))   // hunger 0.45 + 0.012*30 ≈ 0.81
        assertEquals(Mood.HUNGRY, moodLabel(vm.state.value))
        assertTrue(vm.state.value.hunger > 0.75f)

        val hungerBefore = vm.state.value.hunger
        vm.submitCommand("feed")
        runCurrent()
        assertTrue(vm.state.value.hunger < hungerBefore, "feed가 hunger를 낮춘다")
        assertEquals("NOM NOM", vm.state.value.toast)
        assertNotEquals(Mood.HUNGRY, moodLabel(vm.state.value), "더 이상 굶주리지 않음")
        assertTrue(vm.terminal.value.any { it.text.contains("unit fed") }, "터미널 응답 줄")

        // 토스트는 ViewModel 타이머(1.4s) 후 만료.
        advanceTimeBy(1500); runCurrent()
        assertNull(vm.state.value.toast, "토스트 만료")
    }

    @Test
    fun stats_stay_clamped_under_repeated_care() = runTest(dispatcher) {
        val vm = vm()
        repeat(20) { vm.submitCommand("clean") }
        assertEquals(0f, vm.state.value.dirty, "clean은 dirty를 0으로(하한 clamp)")
        repeat(20) { vm.submitCommand("play") }
        runCurrent()
        assertTrue(vm.state.value.happiness <= 1f, "happiness 상한 clamp")
        assertTrue(vm.state.value.energy >= 0f, "energy 하한 clamp")
        assertTrue(vm.state.value.hunger <= 1f, "hunger 상한 clamp")
    }

    @Test
    fun evolve_cycle_advances_stage() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(Stage.Egg, vm.state.value.stage)
        // evolveProgress를 1로(tick +0.005/s).
        vm.dispatch(Action.Tick(250f))
        runCurrent()
        assertTrue(vm.state.value.canEvolve, "진화 가능 상태")

        vm.submitCommand("evolve")
        runCurrent()
        assertTrue(vm.state.value.evolving, "evolving 켜짐")

        // ViewModel 스케줄러가 2.2s 후 EvolveComplete를 디스패치.
        advanceTimeBy(2300); runCurrent()
        assertEquals(Stage.Larva, vm.state.value.stage, "Egg → Larva")
        assertFalse(vm.state.value.evolving, "evolving 해제")
        assertEquals(0f, vm.state.value.evolveProgress, "진화 진행도 리셋")
    }
}
