package today.superb.jvl.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.terminal.TerminalLine
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 터미널 — 히스토리 LazyColumn + 입력 필드. ↑/↓로 이전 명령 탐색.
 * 명령 히스토리(cmdHistory)는 비도메인이라 화면 local state.
 */
@Composable
fun TerminalScreen(
    lines: List<TerminalLine>,
    name: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var cmdHistory by remember { mutableStateOf(listOf<String>()) }
    var cmdIdx by remember { mutableStateOf(-1) }

    // lines 자체를 키로 — cap(200) 도달 후 size가 고정돼도 새 내용에 스크롤 따라가게.
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun submit() {
        val text = input
        if (text.isNotBlank()) {
            cmdHistory = (listOf(text.trim()) + cmdHistory).take(30)
        }
        cmdIdx = -1
        input = ""
        onSubmit(text)
    }

    Column(
        modifier
            .fillMaxSize()
            .border(1.dp, palette.phosGrid)
            .padding(8.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(lines) { line -> TerminalLineRow(line) }
        }

        BasicTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            textStyle = TextStyle(color = palette.phos, fontFamily = LocalMonoFont.current, fontSize = 13.sp),
            cursorBrush = SolidColor(palette.phos),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .onPreviewKeyEvent { event ->
                    // Enter 제출은 KeyboardActions(onGo)가 단독 담당 — 여기선 ↑↓ 히스토리만.
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (cmdHistory.isNotEmpty()) {
                                cmdIdx = (cmdIdx + 1).coerceAtMost(cmdHistory.lastIndex)
                                input = cmdHistory[cmdIdx]
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            cmdIdx = (cmdIdx - 1).coerceAtLeast(-1)
                            input = if (cmdIdx >= 0) cmdHistory[cmdIdx] else ""
                            true
                        }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Column {
                    MonoText("${name.lowercase()}@nautilus:~$", color = palette.phosDim)
                    inner()
                }
            },
        )
    }
}
