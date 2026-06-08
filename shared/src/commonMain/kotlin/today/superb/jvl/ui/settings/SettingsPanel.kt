package today.superb.jvl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.core.Species
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.Hue
import today.superb.jvl.ui.theme.LocalDisplayFont
import today.superb.jvl.ui.theme.LocalPalette
import kotlin.math.round

/**
 * 설정(Tweaks) 패널 — 데모 `TweaksPanel` 포팅. 헤더 우상단 톱니 버튼으로 열리는 풀스크린 오버레이.
 * Display(테마/CRT/스캔라인/노이즈)·Creature(종)·Sonar(펄스/감쇠)·Audio(SFX)·Care(새 알)를 실시간 반영.
 */
@Composable
fun SettingsPanel(
    tweaks: Tweaks,
    sound: Boolean,
    onTweaks: (Tweaks) -> Unit,
    onToggleSound: () -> Unit,
    onHatch: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.92f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.92f).border(1.dp, palette.phos).padding(14.dp)
                .verticalScroll(rememberScrollState())
                // 패널 내부 탭이 닫기로 새지 않도록(빈 클릭 소비).
                .clickable(enabled = false) {},
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoText("▸ TWEAKS", color = palette.phos, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current)
                MonoText("✕ CLOSE", color = palette.phosMid, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onClose))
            }

            section("DISPLAY")
            chipRow("Theme", Hue.entries.filter { it != Hue.Alert }, tweaks.theme, { it.name.uppercase() }) { onTweaks(tweaks.copy(theme = it)) }
            sliderRow("CRT intensity", tweaks.crtIntensity, 0f..1.4f) { onTweaks(tweaks.copy(crtIntensity = it)) }
            toggleRow("Scanlines", tweaks.scanlines) { onTweaks(tweaks.copy(scanlines = it)) }
            toggleRow("Noise / static", tweaks.noise) { onTweaks(tweaks.copy(noise = it)) }

            section("CREATURE")
            chipRow("Species", Species.entries, tweaks.species, { it.name.lowercase() }) { onTweaks(tweaks.copy(species = it)) }

            section("SONAR")
            sliderRow("Pulse period", tweaks.pulsePeriod, 2f..12f, "s") { onTweaks(tweaks.copy(pulsePeriod = it)) }
            sliderRow("Phosphor decay", tweaks.phosphorDecay, 0.3f..4f, "s") { onTweaks(tweaks.copy(phosphorDecay = it)) }

            section("AUDIO")
            toggleRow("SFX", sound) { onToggleSound() }

            section("CARE")
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).border(1.dp, palette.phos).clickable(onClick = onHatch).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                MonoText("◢◤ HATCH NEW EGG", color = palette.phos, fontSize = 12.sp, fontFamily = LocalDisplayFont.current)
            }
        }
    }
}

@Composable
private fun section(label: String) {
    val palette = LocalPalette.current
    Spacer(Modifier.height(10.dp))
    MonoText(label, color = palette.phosMid, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun <T> chipRow(label: String, options: List<T>, selected: T, render: (T) -> String, onSelect: (T) -> Unit) {
    val palette = LocalPalette.current
    MonoText(label, color = palette.phosDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (opt in options) {
            val active = opt == selected
            Box(
                Modifier.weight(1f).border(1.dp, if (active) palette.phos else palette.phosDim)
                    .background(if (active) palette.phosGrid else palette.bg)
                    .clickable { onSelect(opt) }.padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                MonoText(render(opt), color = if (active) palette.phos else palette.phosDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun toggleRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable { onToggle(!value) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(label, color = palette.phosDim, fontSize = 11.sp)
        MonoText(if (value) "[ ON ]" else "[ OFF ]", color = if (value) palette.phos else palette.phosDim, fontSize = 11.sp)
    }
}

@Composable
private fun sliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String = "", onChange: (Float) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoText(label, color = palette.phosDim, fontSize = 11.sp)
        MonoText("${round(value * 10) / 10}$unit", color = palette.phos, fontSize = 11.sp)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = palette.phos,
            activeTrackColor = palette.phosMid,
            inactiveTrackColor = palette.phosGrid,
        ),
    )
}
