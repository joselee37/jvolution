package today.superb.jvl.ui.crt

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

// AGSL — 콘텐츠를 샘플해 스캔라인 + 비네트 + 플리커를 입힌다(uIntensity로 농도, uTime으로 명멸).
private const val CRT_AGSL = """
uniform shader uContent;
uniform float uTime;
uniform float uIntensity;
uniform float2 uResolution;
half4 main(float2 coord) {
    half4 c = uContent.eval(coord);
    // 스캔라인 — 2px 주기로 라인 사이를 어둡게
    float scan = 0.5 + 0.5 * sin(coord.y * 3.14159265);
    c.rgb *= (1.0 - 0.20 * uIntensity * (1.0 - scan));
    // 비네트 — 가장자리 감광
    float2 uv = coord / uResolution;
    float2 d = uv - float2(0.5, 0.5);
    float vig = clamp(1.0 - dot(d, d) * 1.3 * uIntensity, 0.0, 1.0);
    c.rgb *= vig;
    // 플리커 — 미세 명멸
    float flick = 0.5 + 0.5 * sin(uTime * 6.0);
    c.rgb *= (1.0 - 0.03 * uIntensity * flick);
    return c;
}
"""

private val crtRuntimeShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(CRT_AGSL) else null
}

actual fun Modifier.crtShader(enabled: Boolean, intensity: Float, timeProvider: () -> Float): Modifier {
    val shader = crtRuntimeShader
    if (!enabled || shader == null) return this
    return this.graphicsLayer {
        shader.setFloatUniform("uTime", timeProvider())
        shader.setFloatUniform("uIntensity", intensity)
        shader.setFloatUniform("uResolution", size.width, size.height)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "uContent").asComposeRenderEffect()
    }
}
