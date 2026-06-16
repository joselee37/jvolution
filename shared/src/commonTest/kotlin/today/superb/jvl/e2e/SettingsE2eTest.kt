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
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.persistence.SaveCodec
import today.superb.jvl.ui.settings.Tweaks
import today.superb.jvl.ui.theme.Hue
import today.superb.jvl.viewmodel.FakeGameStore
import today.superb.jvl.viewmodel.GameViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정/Tweaks e2e — 종 선택(SetSpecies)·사운드 토글(mute)·디스플레이 Tweaks 영속화·새 알 부화(hatch)를
 * ViewModel 진입점으로 풀플로우 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsE2eTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun squidPeer() = Peer(
        "hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f,
    )

    @Test
    fun set_species_changes_and_carries_into_next_generation() = runTest(dispatcher) {
        val vm = GameViewModel(
            SeededRng(7L), autoTick = false,
            initialState = GameState.initial("U", 0L, listOf(squidPeer())),
        )
        vm.dispatch(Action.SetSpecies(Species.Squid))
        assertEquals(Species.Squid, vm.state.value.species)

        // 부모(Squid) × 피어(Squid) → 자식 종도 Squid(양쪽 부모가 같으면 결정론적).
        vm.requestBreed("hrrk"); vm.confirmBreed(); runCurrent()
        assertEquals(Species.Squid, vm.state.value.species, "종이 다음 세대로 유지")
        assertEquals(2, vm.state.value.gen)
    }

    @Test
    fun sound_command_toggles_audio_with_toast() = runTest(dispatcher) {
        val vm = GameViewModel(SeededRng(42L), autoTick = false)
        assertFalse(vm.state.value.sound, "초기 음소거")

        vm.submitCommand("sound"); runCurrent()
        assertTrue(vm.state.value.sound, "sound 토글 on")
        assertEquals("SOUND ON", vm.state.value.toast)

        vm.submitCommand("mute"); runCurrent()
        assertFalse(vm.state.value.sound, "mute 토글 off")
        assertEquals("MUTED", vm.state.value.toast)
    }

    @Test
    fun display_tweaks_change_is_persisted() = runTest(dispatcher) {
        val store = FakeGameStore()
        val codec = SaveCodec()
        val vm = GameViewModel(SeededRng(42L), autoTick = false, store = store, codec = codec)

        vm.updateTweaks(Tweaks(theme = Hue.Amber, scanlines = false))
        advanceTimeBy(1200); runCurrent()   // > 저장 디바운스
        val blob = assertNotNull(codec.decode(store.saved), "Tweaks 변경이 저장됨")
        assertEquals(Hue.Amber, blob.tweaks.theme)
        assertFalse(blob.tweaks.scanlines)
    }

    @Test
    fun hatch_new_egg_opens_assay_then_confirm_advances_generation() = runTest(dispatcher) {
        // 설정 패널 "Hatch new egg" → 무작위 가용 피어로 ASSAY를 연다(즉시 교배 X).
        val vm = GameViewModel(SeededRng(42L), autoTick = false)   // 기본 7유닛 로스터
        val genBefore = vm.state.value.gen
        vm.hatchNewEgg()
        assertNotNull(vm.breedTarget.value, "부화가 PAIR-BOND ASSAY를 연다")
        assertEquals(genBefore, vm.state.value.gen, "확정 전에는 교배하지 않음")

        vm.confirmBreed(); runCurrent()
        assertNull(vm.breedTarget.value)
        assertEquals(genBefore + 1, vm.state.value.gen, "확정으로 다음 세대")
    }
}
