package today.superb.jvl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import jvolution.shared.generated.resources.Res
import jvolution.shared.generated.resources.jetbrains_mono_regular
import jvolution.shared.generated.resources.share_tech_mono_regular
import jvolution.shared.generated.resources.vt323_regular
import org.jetbrains.compose.resources.Font

/**
 * 번들 폰트 FontFamily 빌더. 데모 `styles.css` 폰트 체계 충실:
 * mono = JetBrains Mono(본문·터미널·status), display = VT323(픽셀 디스플레이),
 * tech = Share Tech Mono(테크 라벨 액센트). 라이선스: docs/licenses/fonts/.
 */
@Composable
fun jvlMonoFamily(): FontFamily = FontFamily(Font(Res.font.jetbrains_mono_regular))

@Composable
fun jvlDisplayFamily(): FontFamily = FontFamily(Font(Res.font.vt323_regular))

@Composable
fun jvlTechFamily(): FontFamily = FontFamily(Font(Res.font.share_tech_mono_regular))
