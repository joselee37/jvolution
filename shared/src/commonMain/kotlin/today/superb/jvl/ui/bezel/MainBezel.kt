package today.superb.jvl.ui.bezel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette

/** 메인 화면을 감싸는 군용 베젤(1종) — 상단 라벨 + 테두리. */
@Composable
fun MainBezel(label: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .border(1.dp, palette.phosDim)
            .padding(6.dp),
    ) {
        MonoText(label, color = palette.phosMid)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}
