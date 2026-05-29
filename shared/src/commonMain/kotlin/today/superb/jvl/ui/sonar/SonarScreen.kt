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
            MonoText("${state.name} · ${state.stage.name.uppercase()}", fontWeight = FontWeight.Bold)
            MonoText(moodLabel(state).name)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            DotCreatureCanvas(
                pingNonce = state.pingNonce,
                happiness = state.happiness,
                asleep = state.asleep,
                modifier = Modifier.fillMaxSize(),
            )

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
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
