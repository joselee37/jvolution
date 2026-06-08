package today.superb.jvl.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable
import today.superb.jvl.core.Species
import today.superb.jvl.ui.theme.Hue

/**
 * 실시간 디스플레이 설정. 데모 `TWEAK_DEFAULTS` 1:1(오디오는 GameState.sound로 통합 → 제외).
 *
 * PLAN.md대로 `:core` GameState가 아닌 `:shared` UI-state로 둔다(폰 전용 프레젠테이션 관심사).
 * GameViewModel이 `MutableStateFlow<Tweaks>`로 보유하고 [LocalTweaks]로 트리에 내려보낸다.
 */
@Immutable
@Serializable
data class Tweaks(
    val theme: Hue = Hue.Green,
    val crtIntensity: Float = 0.7f,   // [0, 1.4]
    val scanlines: Boolean = true,
    val noise: Boolean = true,
    /** 실 CRT 셰이더(AGSL/SkSL) on/off. 기본 off → 기존 Compose-Canvas 근사. 테스터 비교용 토글. */
    val crtShader: Boolean = false,
    val species: Species = Species.Ghost,
    val pulsePeriod: Float = 5f,      // [2, 12] s
    val phosphorDecay: Float = 1f,    // [0.3, 4] s
)

/** 현재 Tweaks를 트리에 노출(CrtLayers/DotCreatureCanvas/screens가 소비). */
val LocalTweaks: ProvidableCompositionLocal<Tweaks> = compositionLocalOf { Tweaks() }
