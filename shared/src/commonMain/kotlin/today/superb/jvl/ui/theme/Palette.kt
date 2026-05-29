package today.superb.jvl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * hue별 인광(phosphor) 팔레트. 데모 oklch 동적 변환 대신 4종을 사전 계산(PLAN 1차 결정).
 * 연속 hue 보간(생명체 alert)은 2차에서 Canvas frame-state로 처리.
 */
@Immutable
data class Palette(
    val phos: Color,      // 밝은 인광(전경)
    val phosMid: Color,   // 중간 톤
    val phosDim: Color,   // 흐릿한 잔광
    val phosGrid: Color,  // 격자/배경 선
    val bg: Color,        // CRT 배경(near-black)
)

/** hue → 사전 계산 팔레트. 1차는 [Hue.Green]만 실사용, 나머지는 plausible 근사. */
fun paletteFor(hue: Hue): Palette = when (hue) {
    Hue.Green -> Palette(
        phos = Color(0xFF7CFFA6),
        phosMid = Color(0xFF36C46A),
        phosDim = Color(0xFF1B6B3A),
        phosGrid = Color(0xFF0E2E1C),
        bg = Color(0xFF03110A),
    )
    Hue.Amber -> Palette(
        phos = Color(0xFFFFD27C),
        phosMid = Color(0xFFC4923A),
        phosDim = Color(0xFF6B4E1B),
        phosGrid = Color(0xFF2E230E),
        bg = Color(0xFF110D03),
    )
    Hue.Blue -> Palette(
        phos = Color(0xFF7CC8FF),
        phosMid = Color(0xFF3A86C4),
        phosDim = Color(0xFF1B466B),
        phosGrid = Color(0xFF0E1F2E),
        bg = Color(0xFF030A11),
    )
    Hue.Alert -> Palette(
        phos = Color(0xFFFF8A7C),
        phosMid = Color(0xFFC4513A),
        phosDim = Color(0xFF6B271B),
        phosGrid = Color(0xFF2E120E),
        bg = Color(0xFF110503),
    )
}
