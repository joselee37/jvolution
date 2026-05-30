package today.superb.jvl.ui.text

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import today.superb.jvl.ui.theme.LocalMonoFont
import today.superb.jvl.ui.theme.LocalPalette

/** 팔레트 기본 전경색 + 폰트(기본=본문 모노)를 적용한 Text 래퍼. display/tech는 [fontFamily]로 지정. */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.phos,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    fontFamily: FontFamily = LocalMonoFont.current,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        fontFamily = fontFamily,
    )
}
