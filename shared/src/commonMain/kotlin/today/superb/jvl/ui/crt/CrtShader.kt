package today.superb.jvl.ui.crt

import androidx.compose.ui.Modifier

/**
 * 콘텐츠 위에 실 CRT 셰이더(스캔라인·비네트·플리커)를 적용하는 플랫폼별 시임.
 *
 * Android: AGSL `RuntimeShader`(API 33+) render effect. iOS/JVM: no-op(폴백 — CrtLayers의
 * Compose-Canvas 근사가 대신 그려진다). [enabled]가 false면 항상 no-op.
 *
 * @param timeProvider 프레임 시간(초) 공급자 — graphicsLayer draw 시점에 읽어 애니메이션(셰이더 uniform).
 */
expect fun Modifier.crtShader(enabled: Boolean, intensity: Float, timeProvider: () -> Float): Modifier
