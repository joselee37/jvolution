package today.superb.jvl.ui.crt

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// SkSL == AGSL(이 셰이더). 소스는 commonMain CRT_SHADER_SRC 공유.
// 컴파일은 한 번만(makeForShader는 비싸다); uniform/렌더이펙트는 draw마다 갱신.
private val crtEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(CRT_SHADER_SRC) }

/**
 * iOS(Skia) 실 CRT 셰이더 — SkSL RuntimeEffect를 RenderEffect로 콘텐츠에 적용.
 * Android AGSL actual과 동형(콘텐츠 child shader = uContent). NOTE: Kotlin/Native라 macOS에서만 컴파일.
 */
actual fun Modifier.crtShader(enabled: Boolean, intensity: Float, timeProvider: () -> Float): Modifier {
    if (!enabled) return this
    return this.graphicsLayer {
        val builder = RuntimeShaderBuilder(crtEffect).apply {
            uniform("uTime", timeProvider())
            uniform("uIntensity", intensity)
            uniform("uResolution", size.width, size.height)
        }
        renderEffect = ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = builder,
            shaderName = "uContent",
            input = null,
        ).asComposeRenderEffect()
    }
}
