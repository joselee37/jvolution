package today.superb.jvl.ui.sonar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.moodLabel
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalPalette

/** 소나 화면 — 상단 readout + 도트 생명체 + 토스트 배너. */
@Composable
fun SonarScreen(state: GameState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxSize().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoText(
                "${state.name} · ${state.stage.name.uppercase()}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = LocalDisplayFont.current,
            )
            MonoText(moodLabel(state).name, fontSize = 18.sp, fontFamily = LocalDisplayFont.current)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            DotCreatureCanvas(
                pingNonce = state.pingNonce,
                happiness = state.happiness,
                asleep = state.asleep,
                modifier = Modifier.fillMaxSize(),
                species = state.species,   // 종은 게임 상태가 단일 소스(설정 패널이 SetSpecies로 변경)
                energy = state.energy,
            )

            // 수면 표시 — 우상단 zzz(데모 `screens.jsx:213`).
            if (state.asleep) {
                MonoText(
                    "z z Z",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = palette.phosDim,
                    fontSize = 20.sp,
                    fontFamily = LocalDisplayFont.current,
                )
            }

            // 진화 오버레이 — 중앙(데모 `screens.jsx:222`).
            if (state.evolving) {
                MonoText(
                    "◢◤ EVOLVING ◢◤",
                    modifier = Modifier.align(Alignment.Center),
                    color = palette.phos,
                    fontSize = 22.sp,
                    fontFamily = LocalDisplayFont.current,
                )
            }

            state.toast?.let { toast ->
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(palette.phosDim),
                ) {
                    MonoText(
                        text = toast,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = palette.phos,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = LocalDisplayFont.current,
                    )
                }
            }
        }
    }
}
