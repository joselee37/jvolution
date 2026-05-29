package today.superb.jvl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

/** 현재 팔레트를 트리에 노출. */
val LocalPalette: ProvidableCompositionLocal<Palette> =
    compositionLocalOf { paletteFor(Hue.Green) }

/** 1차는 시스템 모노 폰트(VT323/JetBrainsMono 등 리소스 와이어링은 후속). */
val LocalMonoFont: ProvidableCompositionLocal<FontFamily> =
    compositionLocalOf { FontFamily.Monospace }

/**
 * 앱 테마. hue에 맞는 [Palette]를 사전 계산해 [LocalPalette]로 내려보낸다.
 * palette 조회는 remember(hue)로 캐싱(불필요한 재계산 방지).
 */
@Composable
fun JvlTheme(hue: Hue, content: @Composable () -> Unit) {
    val palette = remember(hue) { paletteFor(hue) }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalMonoFont provides FontFamily.Monospace,
        content = content,
    )
}
