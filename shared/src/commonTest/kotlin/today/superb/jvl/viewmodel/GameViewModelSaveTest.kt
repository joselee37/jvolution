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
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.View
import today.superb.jvl.persistence.SaveCodec
import today.superb.jvl.ui.settings.Tweaks
import today.superb.jvl.ui.theme.Hue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 영속화 save 콜렉터 — autoTick=false라 tick 무한루프는 없고, saver는 store/codec 주입 시에만 동작.
 * debounce는 영속 타이머가 없어 runTest 정리 단계가 행하지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelSaveTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    private fun vm(store: FakeGameStore) =
        GameViewModel(SeededRng(42L), autoTick = false, store = store, codec = SaveCodec())

    @Test
    fun burst_of_durable_changes_debounces_to_one_save() = runTest(dispatcher) {
        val store = FakeGameStore()
        val vm = vm(store)
        vm.dispatch(Action.Feed)
        vm.dispatch(Action.Play)
        vm.dispatch(Action.Clean)
        advanceTimeBy(1200) // > debounce window
        runCurrent()
        assertEquals(1, store.saveCount)
        assertTrue(store.saved?.contains("\"gen\"") == true, "saved JSON should contain game state")
    }

    @Test
    fun pure_transient_change_does_not_save() = runTest(dispatcher) {
        val store = FakeGameStore()
        val vm = vm(store)
        vm.dispatch(Action.Feed)          // durable change → baseline save
        advanceTimeBy(1200); runCurrent()
        val baseline = store.saveCount    // 1
        assertEquals(1, baseline)

        vm.dispatch(Action.SetView(View.Radar)) // view is stripped from the snapshot
        advanceTimeBy(1200); runCurrent()
        assertEquals(baseline, store.saveCount, "stripped-transient change must not trigger a save")
    }

    @Test
    fun no_store_means_no_saver_and_no_crash() = runTest(dispatcher) {
        // store/codec 미주입 — saver 미가동(기존 동작 보존).
        val vm = GameViewModel(SeededRng(42L), autoTick = false)
        vm.dispatch(Action.Feed)
        advanceTimeBy(1200); runCurrent()
        // 크래시 없이 상태만 변함
        assertTrue(vm.state.value.cycles >= 1)
    }

    // 복원 → 변경 → 재저장 라운드트립(Koin 팩토리 시드 경로). 영속화의 핵심 계약.
    @Test
    fun restores_from_save_then_persists_mutation() = runTest(dispatcher) {
        val codec = SaveCodec()
        val prior = GameState.initial("SAVED", now = 0L).copy(training = 0.5f, cycles = 10, gen = 4)
        val store = FakeGameStore(codec.encode(prior, Tweaks(theme = Hue.Amber)))
        val blob = assertNotNull(codec.decode(store.load()))
        val vm = GameViewModel(SeededRng(42L), autoTick = false, initialState = blob.game, initialTweaks = blob.tweaks, store = store, codec = codec)

        // restored
        assertEquals("SAVED", vm.state.value.name)
        assertEquals(4, vm.state.value.gen)
        assertEquals(Hue.Amber, vm.tweaks.value.theme)

        // mutate + persist
        vm.dispatch(Action.Train)   // training +0.15
        advanceTimeBy(1200); runCurrent()
        val reread = assertNotNull(codec.decode(store.saved)).game
        assertTrue(reread.training > blob.game.training, "mutation must be re-persisted")
    }

    // #3 회귀: 피어 cosmetic 드리프트(위치/쿨다운)만으로는 저장하지 않는다(디바운스 starvation 방지).
    @Test
    fun peer_cosmetic_drift_alone_does_not_save() = runTest(dispatcher) {
        val store = FakeGameStore()
        val peer = Peer("p", "P", Species.Ghost, Stage.Adult, Personality.Playful, bearing = 0f, range = 0.5f, bearingVel = 10f, rangeVel = 0.001f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f)
        val seed = GameState.initial("X", now = 0L, peers = listOf(peer))
        val vm = GameViewModel(SeededRng(42L), autoTick = false, initialState = seed, store = store, codec = SaveCodec())

        // 초기 상태가 1회 저장됨(fresh/loaded 상태를 바로 persist — 바람직).
        advanceTimeBy(1200); runCurrent()
        val baseline = store.saveCount
        assertEquals(1, baseline)

        repeat(5) { vm.dispatch(Action.PeerTick(1f)) }  // 위치/쿨다운만 드리프트(cooldown 100 → AI 미발동)
        advanceTimeBy(1500); runCurrent()
        assertEquals(baseline, store.saveCount, "cosmetic peer drift must not churn saves")

        vm.dispatch(Action.Feed)   // 내구 변화
        advanceTimeBy(1200); runCurrent()
        assertEquals(baseline + 1, store.saveCount, "durable change must save")
    }
}
