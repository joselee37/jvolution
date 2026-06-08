package today.superb.jvl.ui.crt

import androidx.compose.ui.Modifier

/** JVM(데스크톱/테스트) 폴백 — no-op. CrtLayers의 Compose-Canvas 근사가 대신 그려진다. */
actual fun Modifier.crtShader(enabled: Boolean, intensity: Float, timeProvider: () -> Float): Modifier = this
