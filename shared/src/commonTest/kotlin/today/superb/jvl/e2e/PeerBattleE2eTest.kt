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
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.PeerRequest
import today.superb.jvl.core.Personality
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.View
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.viewmodel.FixedRng
import today.superb.jvl.viewmodel.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 피어 도전 → 전투 라이프사이클 e2e. 결정론적 `accept` 경로(SeededRng)로 전투를 구동하고,
 * challenge 수락 확률 경로는 FixedRng로 강제. 페이즈 진행은 ViewModel 스케줄러를 가상시간으로 전진.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerBattleE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun foe() = Peer(
        "hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f,
    )

    /** 피어가 도전을 걸어온 상태 — accept로 결정론적 전투 시작(수락 roll 없음). */
    private fun challengedState() = GameState.initial("UNIT", 0L, listOf(foe()))
        .copy(pendingRequest = PeerRequest("hrrk", RequestType.Challenge))

    private fun vm() = GameViewModel(SeededRng(7L), autoTick = false, initialState = challengedState())

    @Test
    fun accept_starts_battle_and_switches_view() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("accept"); runCurrent()
        assertEquals(View.Battle, vm.state.value.view)
        val b = assertNotNull(vm.state.value.battle)
        assertEquals(BattlePhase.Choose, b.phase)
        assertNull(vm.state.value.pendingRequest, "수락으로 대기요청 해소")
    }

    @Test
    fun committing_a_move_resolves_a_turn_and_logs() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("accept"); runCurrent()

        vm.dispatch(Action.BattleCursor(set = 0))   // Ping
        vm.dispatch(Action.BattleCommit); runCurrent()
        val b = assertNotNull(vm.state.value.battle)
        assertEquals(BattlePhase.MyCast, b.phase)
        assertEquals(1, b.myMoveHistory.size)
        assertTrue(b.log.isNotEmpty(), "턴 서술이 로그에 남음")

        // ViewModel 스케줄러가 캐스트→데미지 페이즈를 자동 진행(MyCast→…→Damage→Choose/End).
        advanceTimeBy(3000); runCurrent()
        val after = vm.state.value.battle
        assertTrue(
            after == null || after.phase == BattlePhase.Choose || after.phase == BattlePhase.End,
            "데미지 페이즈를 지나 다음 턴(Choose) 또는 종료(End)",
        )
    }

    @Test
    fun flee_ends_battle_and_returns_to_sonar_with_penalty() = runTest(dispatcher) {
        val vm = vm()
        vm.submitCommand("accept"); runCurrent()
        val happyBefore = vm.state.value.happiness

        vm.submitCommand("flee"); runCurrent()
        assertEquals(BattlePhase.End, vm.state.value.battle?.phase)
        assertEquals(BattleResult.Flee, vm.state.value.battle?.result)

        // End + result != null → 1.8s 후 BattleEnd → 소나 복귀 + happiness 페널티.
        advanceTimeBy(2000); runCurrent()
        assertNull(vm.state.value.battle, "전투 종료")
        assertEquals(View.Sonar, vm.state.value.view, "소나 복귀")
        assertTrue(vm.state.value.happiness <= happyBefore, "flee 페널티")
    }

    @Test
    fun challenge_command_with_accepting_foe_starts_battle() = runTest(dispatcher) {
        // 수락 roll 0.0 < acceptOdds(Aggressive=0.85) → 전투 시작(challenge는 roll 1회 소비; 패딩은 무해).
        val vm = GameViewModel(
            FixedRng(listOf(0.0f, 0.5f, 0.5f)), autoTick = false,
            initialState = GameState.initial("UNIT", 0L, listOf(foe())),
        )
        vm.submitCommand("challenge hrrk"); runCurrent()
        assertEquals(View.Battle, vm.state.value.view)
        assertNotNull(vm.state.value.battle)
    }
}
