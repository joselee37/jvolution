package today.superb.jvl.ui.crt

import androidx.compose.ui.Modifier

/**
 * 콘텐츠 위에 실 CRT 셰이더(스캔라인·비네트·플리커)를 적용하는 플랫폼별 시임.
 *
 * Android: AGSL `RuntimeShader`(API 33+). iOS: SkSL `RuntimeEffect`(Skia). JVM: no-op(테스트 호스트,
 * CrtLayers의 Compose-Canvas 근사가 대신 그려짐). [enabled]가 false면 항상 no-op.
 *
 * @param timeProvider 프레임 시간(초) 공급자 — graphicsLayer draw 시점에 읽어 애니메이션(셰이더 uniform).
 */
expect fun Modifier.crtShader(enabled: Boolean, intensity: Float, timeProvider: () -> Float): Modifier

/**
 * CRT 셰이더 소스 — AGSL(Android)과 SkSL(iOS/Skia)이 이 셰이더에선 동일 문법이라 공유한다.
 * 콘텐츠(uContent)를 샘플해 스캔라인 + 비네트 + 플리커(uIntensity 농도, uTime 명멸)를 입힌다.
 */
internal const val CRT_SHADER_SRC = """
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
