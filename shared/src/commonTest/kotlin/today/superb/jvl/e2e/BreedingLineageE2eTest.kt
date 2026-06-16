package today.superb.jvl.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.genetics.predictedInbreeding
import today.superb.jvl.viewmodel.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 번식 → 다세대 계보 e2e. 터미널 breed가 PAIR-BOND ASSAY를 열고(즉시 교배 X), confirm이 세대를 진행하며
 * 2부모 혈통 DAG를 누적. 조상이 된 피어와 재교배 시 Wright 근친계수가 올라가는지 풀플로우 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BreedingLineageE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun peers() = listOf(
        Peer("hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive, bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f),
        Peer("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle, bearing = 90f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f),
    )

    private fun vm() = GameViewModel(SeededRng(11L), autoTick = false, initialState = GameState.initial("UNIT", 0L, peers()))

    @Test
    fun terminal_breed_opens_assay_then_confirm_advances_generation() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("breed hrrk"); runCurrent()
        assertEquals("hrrk", vm.breedTarget.value, "ASSAY 열림(즉시 교배하지 않음)")
        assertEquals(1, vm.state.value.gen, "터미널 breed는 즉시 교배 X")
        assertTrue(vm.terminal.value.any { it.text.contains("predicted inbreeding") }, "예측 근친계수 에코")

        vm.confirmBreed(); runCurrent()
        assertNull(vm.breedTarget.value, "확정 후 ASSAY 닫힘")
        assertEquals(2, vm.state.value.gen, "확정으로 다음 세대")
    }

    @Test
    fun confirm_breed_builds_two_parent_pedigree() = runTest(dispatcher) {
        val vm = vm()
        val gen1Id = vm.state.value.creatureId
        vm.requestBreed("hrrk"); vm.confirmBreed(); runCurrent()

        val s2 = vm.state.value
        assertEquals(2, s2.gen)
        assertEquals(gen1Id, s2.motherId, "이전 개체가 모계")
        assertEquals("hrrk", s2.fatherId, "피어가 부계")
        assertTrue(s2.lineage.ancestors.any { it.gen == 1 }, "gen1 본가 아카이브")
        assertTrue(s2.lineage.ancestors.any { it.id == "hrrk" && it.gen == 0 }, "피어 공동부모 founder 1회 기록")
    }

    @Test
    fun re_breeding_with_ancestor_peer_predicts_parent_offspring_inbreeding() = runTest(dispatcher) {
        val vm = vm()
        vm.requestBreed("hrrk"); vm.confirmBreed(); runCurrent()
        // 현재 개체(gen2)의 부계가 hrrk → hrrk와 재교배는 부모-자식 근친 F=0.25.
        assertEquals(0.25, predictedInbreeding(vm.state.value, "hrrk"), 1e-9)
        // 무관한 다른 피어와는 0.
        assertEquals(0.0, predictedInbreeding(vm.state.value, "lumen"), 1e-9)
    }

    @Test
    fun cancel_breed_leaves_generation_unchanged() = runTest(dispatcher) {
        val vm = vm()
        vm.requestBreed("hrrk")
        assertEquals("hrrk", vm.breedTarget.value)
        vm.cancelBreed()
        assertNull(vm.breedTarget.value)
        assertEquals(1, vm.state.value.gen, "취소는 교배하지 않음")
    }
}
