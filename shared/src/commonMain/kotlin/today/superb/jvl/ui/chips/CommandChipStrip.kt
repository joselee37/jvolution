package today.superb.jvl.ui.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 터미널 입력줄 위의 명령 칩 한 줄 — 탭하면 해당 명령을 터미널 매크로로 실행([onCommand] →
 * `GameViewModel.submitCommand`). 모바일에서 사라진 `↑↓` 명령 히스토리의 실질적 대체 수단.
 */
@Composable
fun CommandChipStrip(
    chips: List<CommandChip>,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    LazyRow(
        modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(chips, key = { it.command }) { chipItem ->
            val border = when (chipItem.emphasis) {
                ChipEmphasis.Alert, ChipEmphasis.Highlight -> palette.phos
                ChipEmphasis.Normal -> palette.phosDim
            }
            val text = when (chipItem.emphasis) {
                ChipEmphasis.Alert, ChipEmphasis.Highlight -> palette.phos
                ChipEmphasis.Normal -> palette.phosMid
            }
            val bg = if (chipItem.emphasis == ChipEmphasis.Alert) palette.phosGrid else palette.bg
            Box(
                Modifier
                    .border(1.dp, border)
                    .background(bg)
                    .clickable { onCommand(chipItem.command) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                MonoText(chipItem.label, color = text, fontSize = 10.sp)
            }
        }
    }
}
