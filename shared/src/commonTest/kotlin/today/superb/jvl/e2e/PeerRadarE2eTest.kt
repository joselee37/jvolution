package today.superb.jvl.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * 피어 근접 AI / DND e2e — PeerTick의 6% AI 주사위를 FixedRng로 결정론 강제.
 * 단일 피어로 RNG 소비를 정확히 스크립트: [gate, roll, cooldown].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerRadarE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun peer(id: String, name: String, personality: Personality, cooldown: Float, bond: Float = 0f) = Peer(
        id, name, Species.Squid, Stage.Adult, personality,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = bond, battlesWon = 0, battlesLost = 0, cooldown = cooldown,
    )

    @Test
    fun peer_tick_can_raise_a_challenge_then_decline_clears_it() = runTest(dispatcher) {
        // 단일 Aggressive 피어(cooldown 0): gate 0.0<0.06 발동, roll 0.0<0.65 → challenge, cooldown 주사위.
        val vm = GameViewModel(
            FixedRng(listOf(0.0f, 0.0f, 0.5f, 0.5f, 0.5f)), autoTick = false,
            initialState = GameState.initial("U", 0L, listOf(peer("hrrk", "HRRK", Personality.Aggressive, cooldown = 0f))),
        )
        vm.dispatch(Action.PeerTick(1f)); runCurrent()
        val req = assertNotNull(vm.state.value.pendingRequest, "challenge가 대기요청을 만든다")
        assertEquals("hrrk", req.from)
        assertEquals(RequestType.Challenge, req.type)
        assertTrue(vm.terminal.value.any { it.text.contains("INCOMING") }, "challenge가 터미널에 에코")

        vm.submitCommand("decline"); runCurrent()
        assertNull(vm.state.value.pendingRequest, "decline이 대기요청 해소")
    }

    @Test
    fun dnd_on_auto_declines_pending_challenge() = runTest(dispatcher) {
        val vm = GameViewModel(
            SeededRng(1L), autoTick = false,
            initialState = GameState.initial("U", 0L, listOf(peer("hrrk", "HRRK", Personality.Aggressive, cooldown = 100f)))
                .copy(pendingRequest = PeerRequest("hrrk", RequestType.Challenge)),
        )
        vm.submitCommand("dnd on"); runCurrent()
        assertNull(vm.state.value.pendingRequest, "DND on이 대기 도전을 자동 거절")
        assertTrue(vm.state.value.dnd, "DND 활성")
    }

    @Test
    fun friendly_drift_raises_peer_bond() = runTest(dispatcher) {
        // Gentle 피어: gate 0.0, roll 0.1 ∈ [challenge 0.08, challenge+friendly 0.63) → friendly, cooldown 주사위.
        val vm = GameViewModel(
            FixedRng(listOf(0.0f, 0.1f, 0.5f, 0.5f, 0.5f)), autoTick = false,
            initialState = GameState.initial("U", 0L, listOf(peer("lumen", "LUMEN-3", Personality.Gentle, cooldown = 0f, bond = 0.2f))),
        )
        val bondBefore = vm.state.value.peers.first().bond
        vm.dispatch(Action.PeerTick(1f)); runCurrent()
        assertNull(vm.state.value.pendingRequest, "friendly는 도전이 아니다")
        assertTrue(vm.state.value.peers.first().bond > bondBefore, "friendly가 peer bond를 올린다")
    }
}
