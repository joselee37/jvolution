package today.superb.jvl.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import today.superb.jvl.core.terminal.TerminalLine
import today.superb.jvl.core.terminal.TerminalLineKind
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette

/** 터미널 한 줄 — 종류별 색상(sys 흐릿 / in 밝음 / out 중간). */
@Composable
fun TerminalLineRow(line: TerminalLine, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val color = when (line.kind) {
        TerminalLineKind.Sys -> palette.phosDim
        TerminalLineKind.In -> palette.phos
        TerminalLineKind.Out -> palette.phosMid
    }
    // In 라인은 ViewModel이 이미 `name@nautilus:~$ cmd` 형태로 프롬프트를 포함한다.
    MonoText(text = line.text, modifier = modifier, color = color)
}
