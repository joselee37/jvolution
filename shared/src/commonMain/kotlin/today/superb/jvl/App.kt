package today.superb.jvl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import today.superb.jvl.ui.bezel.MainBezel
import today.superb.jvl.ui.crt.CrtLayers
import today.superb.jvl.ui.frame.DeviceFrame
import today.superb.jvl.ui.sonar.SonarScreen
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

    // 1차는 항상 Green(전투/peer 없음 → alert 전환 없음). pendingRequest 기반 alert는 2차.
    JvlTheme(hue = Hue.Green) {
        CrtLayers {
            DeviceFrame(
                header = {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 헤더는 기기 아이덴티티(데모 헤더엔 mood 없음 — mood는 소나 readout 단독).
                        MonoText("SONAR-OBS · MK.III", fontFamily = LocalTechFont.current)
                        MonoText("◉ LINK", fontFamily = LocalTechFont.current)
                    }
                },
                bezel = {
                    MainBezel(label = "SONAR-OBS · ${state.stage.name.uppercase()}") {
                        SonarScreen(state)
                    }
                },
                terminal = {
                    TerminalScreen(lines = terminal, name = state.name, onSubmit = vm::submitCommand)
                },
            )
        }
    }
}
