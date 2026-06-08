# phase-2 — CRT 셰이더 (옵션 토글) (2026-06-09)

데모의 CRT 룩을 셰이더로 구현하되, **설정 패널 토글**(기본 OFF)로 테스터가 적용 전/후를 비교할 수
있게 했다. OFF = 기존 Compose-Canvas 근사, ON = 실 GPU 셰이더.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-shader-off.png` | CRT shader OFF — Canvas 근사(밝은 녹색, 약한 비네트) |
| `02-shader-on.png` | CRT shader ON — AGSL 셰이더 적용. **전체 감광 + 강한 비네트(코너 어둡게) + GPU 스캔라인** — CRT 튜브 느낌. 토글로 즉시 전환 |

구현: `expect fun Modifier.crtShader(enabled, intensity, timeProvider)` (commonMain) +
- **Android actual**: AGSL `RuntimeShader`(API 33+) → `RenderEffect.createRuntimeShaderEffect` →
  `graphicsLayer { renderEffect = … }`. 콘텐츠를 샘플해 스캔라인·비네트·플리커(uTime/uIntensity uniform).
- **iOS/JVM actual**: no-op 폴백(Canvas 근사 유지 — SkSL actual은 후속).

`Tweaks.crtShader`(기본 false) → 설정 패널 "CRT shader (beta)" 토글. CrtLayers는 ON이면 셰이더 적용
+ Canvas 레이어 스킵, OFF면 Canvas 근사. 전용 에뮬레이터(API 35, swiftshader)에서 토글 ON 시
크래시 없이 렌더 확인.

버그픽스: 셰이더 래퍼 도입 시 content를 `matchParentSize` Box로 감싸 CrtLayers의 모든 자식이
matchParentSize가 되어 Box가 0으로 붕괴(흰 화면) → content 래퍼를 `fillMaxSize`로 수정.

미검증: iOS SkSL actual(현재 no-op), 실 하드웨어 GPU 화질.
