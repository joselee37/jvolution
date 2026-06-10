package today.superb.jvl.ui.bezel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette
import today.superb.jvl.ui.theme.LocalTechFont

/** 하우징 종류 — 데모 tweaks `bezel`(military/vintage/minimal) 1:1. Tweaks에 by-name 직렬화. */
enum class BezelStyle { Military, Vintage, Minimal }

/** 하우징 하드웨어 색 — phosphor 팔레트와 독립. 데모 `Bezel` jsx inline 값 1:1. */
private data class Housing(
    val outerTop: Color,
    val outerBottom: Color,
    val bolt: Color,
    val text: Color,
    val innerBorder: Color,
)

private val MILITARY = Housing(
    outerTop = Color(0xFF2C2F26), outerBottom = Color(0xFF16180F),
    bolt = Color(0xFF0A0C08), text = Color(0xFF7A8068), innerBorder = Color(0xFF080A05),
)
private val VINTAGE = Housing(
    outerTop = Color(0xFF4A3D2C), outerBottom = Color(0xFF2E261C),
    bolt = Color(0xFF1A140D), text = Color(0xFF9D8867), innerBorder = Color(0xFF1A1208),
)

/**
 * 메인 화면을 감싸는 물리 하우징. military/vintage = 그래디언트 + 코너 볼트 4 + 라벨 스트립 +
 * indicator LED 2, minimal = 검정 패딩 박스(라벨/장식 없음). 데모 `Bezel({variant})` 포팅.
 */
@Composable
fun MainBezel(label: String, style: BezelStyle = BezelStyle.Military, content: @Composable () -> Unit) {
    if (style == BezelStyle.Minimal) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .padding(4.dp),
        ) { content() }
        return
    }

    val palette = LocalPalette.current
    val housing = if (style == BezelStyle.Vintage) VINTAGE else MILITARY

    Box(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(housing.outerTop, housing.outerBottom))),
    ) {
        // 코너 볼트 4개 — 데모 boltStyle(5px 원) 1:1.
        for ((align, x, y) in listOf(
            Triple(Alignment.TopStart, 6.dp, 6.dp),
            Triple(Alignment.TopEnd, (-6).dp, 6.dp),
            Triple(Alignment.BottomStart, 6.dp, (-6).dp),
            Triple(Alignment.BottomEnd, (-6).dp, (-6).dp),
        )) {
            Box(
                Modifier.align(align).offset(x = x, y = y).size(5.dp)
                    .background(housing.bolt, CircleShape),
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            MonoText(
                label,
                color = housing.text,
                fontFamily = LocalTechFont.current,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)
                    .border(1.dp, housing.innerBorder),
            ) { content() }
            // indicator LED 2개 — 첫째 on(phosphor glow), 둘째 off(데모 1:1).
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            ) {
                Box(Modifier.size(5.dp).background(palette.phos, CircleShape))
                Box(Modifier.size(5.dp).background(Color(0x80502828), CircleShape))
            }
        }
    }
}
