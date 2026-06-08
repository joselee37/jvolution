package today.superb.jvl.ui.crt

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

// AGSL == SkSL(이 셰이더). 소스는 commonMain CRT_SHADER_SRC 공유.
private val crtRuntimeShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(CRT_SHADER_SRC) else null
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
