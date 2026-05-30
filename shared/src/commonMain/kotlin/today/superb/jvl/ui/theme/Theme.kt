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

/** 본문 모노(JetBrains Mono) — 터미널·status 등 정렬 중요한 텍스트. */
val LocalMonoFont: ProvidableCompositionLocal<FontFamily> =
    compositionLocalOf { FontFamily.Monospace }

/** 픽셀 디스플레이(VT323) — 헤더·readout·toast 같은 CRT 큰 글씨. */
val LocalDisplayFont: ProvidableCompositionLocal<FontFamily> =
    compositionLocalOf { FontFamily.Monospace }

/** 테크 라벨(Share Tech Mono) — 기기 라벨 액센트. */
val LocalTechFont: ProvidableCompositionLocal<FontFamily> =
    compositionLocalOf { FontFamily.Monospace }

/**
 * 앱 테마. hue에 맞는 [Palette]를 사전 계산하고, 번들 폰트 3종을 트리에 내려보낸다.
 * palette 조회는 remember(hue)로 캐싱.
 */
@Composable
fun JvlTheme(hue: Hue, content: @Composable () -> Unit) {
    val palette = remember(hue) { paletteFor(hue) }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalMonoFont provides jvlMonoFamily(),
        LocalDisplayFont provides jvlDisplayFamily(),
        LocalTechFont provides jvlTechFamily(),
        content = content,
    )
}
