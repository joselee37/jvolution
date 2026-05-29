package today.superb.jvl.ui.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 기기 프레임 — 위→아래로 header / 메인 베젤(가변) / 터미널(고정). 데모 06 명세 근사.
 * 셰이더·home indicator 등 세부는 후속.
 */
@Composable
fun DeviceFrame(
    header: @Composable () -> Unit,
    bezel: @Composable () -> Unit,
    terminal: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(8.dp),
    ) {
        header()
        Box(Modifier.fillMaxWidth().weight(1f)) { bezel() }
        Box(Modifier.fillMaxWidth().height(280.dp)) { terminal() }
    }
}
