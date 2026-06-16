package today.superb.jvl

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import today.superb.jvl.core.Action
import today.superb.jvl.core.View
import today.superb.jvl.ui.battle.BattleScreen
import today.superb.jvl.ui.bezel.MainBezel
import today.superb.jvl.ui.crt.CrtLayers
import today.superb.jvl.ui.frame.DeviceFrame
import today.superb.jvl.ui.radar.PeerAlertOverlay
import today.superb.jvl.ui.radar.RadarScreen
import today.superb.jvl.ui.settings.LocalTweaks
import today.superb.jvl.ui.settings.SettingsPanel
import today.superb.jvl.ui.sonar.SonarScreen
import today.superb.jvl.ui.tree.TreeScreen
import today.superb.jvl.ui.genome.GenomeScreen
import today.superb.jvl.ui.breed.BreedAssayOverlay
import today.superb.jvl.ui.chips.CommandChipStrip
import today.superb.jvl.ui.chips.chipsFor
import today.superb.jvl.ui.terminal.TerminalScreen
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.Hue
import today.superb.jvl.ui.theme.JvlTheme
import today.superb.jvl.ui.theme.LocalTechFont
import today.superb.jvl.viewmodel.GameViewModel

@Composable
fun App() {
    val vm: GameViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val terminal by vm.terminal.collectAsStateWithLifecycle()
    val tweaks by vm.tweaks.collectAsStateWithLifecycle()
    val breedTarget by vm.breedTarget.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }

    // 레이더 블립 선택 — 화면 local 프레젠테이션 상태(GameState 밖). 레이더를 벗어나면 해제.
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.view) { if (state.view != View.Radar) selectedPeerId = null }
    val selectedPeer = state.peers.find { it.id == selectedPeerId }

    // 도전 요청이 활성인 동안 전체 색조를 Alert(red)로 강제, 아니면 설정 테마. 데모 `--hue` 1:1.
    val hue = if (state.pendingRequest != null) Hue.Alert else tweaks.theme
    val bezelLabel = when (state.view) {
        View.Radar -> "LRRS-RADAR · ${state.peers.size} CONTACTS"
        View.Battle -> "ENGAGEMENT · CH.07 · R.${(state.battle?.turn ?: 1).toString().padStart(2, '0')}"
        View.Tree -> "LINEAGE-ARCHIVE · G${state.gen.toString().padStart(2, '0')}"
        View.Genome -> "GENOME-ASSAY · G${state.gen.toString().padStart(2, '0')}"
        else -> "SONAR-OBS · ${state.stage.name.uppercase()}"
    }

    JvlTheme(hue = hue) {
        CompositionLocalProvider(LocalTweaks provides tweaks) {
            Box(Modifier.fillMaxSize()) {
                CrtLayers {
                    DeviceFrame(
                        header = {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                MonoText("SONAR-OBS · MK.III", fontFamily = LocalTechFont.current)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // LINK — 피어 채널 상태: 접점 수 표시, 도전 수신 시 점멸, 탭 = scan 매크로.
                                    val linkAlpha = if (state.pendingRequest != null) {
                                        val blink by rememberInfiniteTransition(label = "link").animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                                            label = "link-blink",
                                        )
                                        blink
                                    } else {
                                        1f
                                    }
                                    MonoText(
                                        "◉ LINK·${state.peers.size}",
                                        fontFamily = LocalTechFont.current,
                                        modifier = Modifier
                                            .alpha(linkAlpha)
                                            .clickable { vm.submitCommand("scan") },
                                    )
                                    MonoText(
                                        "⚙ CFG",
                                        fontFamily = LocalTechFont.current,
                                        modifier = Modifier.clickable { settingsOpen = true },
                                    )
                                }
                            }
                        },
                        bezel = {
                            MainBezel(label = bezelLabel, style = tweaks.bezel) {
                                Box(Modifier.fillMaxSize()) {
                                    when (state.view) {
                                        View.Radar -> RadarScreen(
                                            state = state,
                                            selectedPeerId = selectedPeerId,
                                            onSelectPeer = { peer ->
                                                selectedPeerId = peer?.id
                                                peer?.let { vm.submitCommand("bond ${it.name.lowercase()}") }
                                            },
                                        )
                                        View.Tree -> TreeScreen(
                                            state = state,
                                            onSelectGen = { vm.submitCommand("tree $it") },
                                        )
                                        View.Genome -> GenomeScreen(state = state)
                                        View.Battle -> BattleScreen(
                                            state = state,
                                            onSelectMove = { i ->
                                                vm.dispatch(Action.BattleCursor(set = i))
                                                vm.dispatch(Action.BattleCommit)
                                            },
                                        )
                                        else -> SonarScreen(
                                            state = state,
                                            onPing = { vm.dispatch(Action.Ping) },
                                            onTalk = { vm.submitCommand("talk") },
                                        )
                                    }
                                    // 근접 경보 — 어느 화면이든 베젤 위에 오버레이.
                                    state.pendingRequest?.let { req ->
                                        state.peers.find { it.id == req.from }?.let {
                                            PeerAlertOverlay(
                                                peer = it,
                                                onAccept = { vm.submitCommand("accept") },
                                                onDecline = { vm.submitCommand("decline") },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        terminal = {
                            Column {
                                CommandChipStrip(
                                    chips = chipsFor(state, selectedPeer),
                                    onCommand = vm::submitCommand,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                Box(Modifier.fillMaxWidth().weight(1f)) {
                                    TerminalScreen(lines = terminal, name = state.name, onSubmit = vm::submitCommand)
                                }
                            }
                        },
                    )
                }

                if (settingsOpen) {
                    SettingsPanel(
                        tweaks = tweaks,
                        species = state.species,
                        sound = state.sound,
                        onTweaks = vm::updateTweaks,
                        onSelectSpecies = { vm.dispatch(Action.SetSpecies(it)) },
                        onToggleSound = { vm.dispatch(Action.ToggleSound) },
                        onHatch = { vm.hatchNewEgg(); settingsOpen = false },
                        onClose = { settingsOpen = false },
                    )
                }

                // 교배 미리보기(PAIR-BOND ASSAY) — breedTarget이 가리키는 피어가 있으면 프레임 위에 모달.
                state.peers.find { it.id == breedTarget }?.let { peer ->
                    BreedAssayOverlay(
                        state = state,
                        peer = peer,
                        onConfirm = { vm.confirmBreed() },
                        onCancel = { vm.cancelBreed() },
                    )
                }
            }
        }
    }
}
