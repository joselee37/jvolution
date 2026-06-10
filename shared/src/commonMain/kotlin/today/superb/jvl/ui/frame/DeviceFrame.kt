package today.superb.jvl.ui.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 기기 프레임 — 위→아래로 header / 메인 베젤(가변) / 터미널(고정). 데모 06 명세 **비율 근사**.
 *
 * 명세(06)는 1:1 정사각 베젤(362) + 터미널(315)의 고정 비율을 요구하지만, 1차는 베젤 `weight(1f)`
 * + 터미널 고정 `312.dp`(명령 칩 스트립 30 + 터미널)로 근사한다. 정사각 베젤·기준 캔버스(402x874)
 * 비율 유지는 후속(셰이더·home indicator와 함께).
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
            // API 35 edge-to-edge: 콘텐츠를 상태바/내비바 아래로 인셋(헤더가 시스템 영역에 가려져
            // 터치가 안 먹던 문제 해결). CRT 배경은 CrtLayers가 가장자리까지 그린다.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        header()
        Box(Modifier.fillMaxWidth().weight(1f)) { bezel() }
        Box(Modifier.fillMaxWidth().height(312.dp)) { terminal() }
    }
}
