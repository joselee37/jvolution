# Sonar Tamagotchi — KMP 스켈레톤/템플릿 심층 비교 리서치

## TL;DR
- **공식 `Kotlin/KMP-App-Template`(shared UI)** 와 **`Kotlin/KMP-App-Template-Native`(SwiftUI native UI)** 두 가지 공식 베이스가 "출시는 폰 먼저, 워치는 후속" 시나리오에 가장 적합하다. 단일 정답은 없으며, **CRT 셰이더·게임 루프를 한 번에 짜고 싶다면 shared UI**, **iOS·Apple Watch에 SwiftUI로 완전 네이티브 정합성을 두고 싶다면 Native**가 답이다.
- watchOS에서는 **SwiftUI가 사실상 강제**된다. Compose Multiplatform은 watchOS를 공식 지원하지 않으며 JetBrains가 단기 로드맵에 없다고 명시했다(Metal 부재로 Skia 렌더 자체가 불가). Kotlin/Native는 `watchosArm64 / watchosX64 / watchosSimulatorArm64`를 지원하므로 **KMP 코어를 XCFramework로 빌드 → watchOS Watch App에서 SwiftUI로 소비**하는 패턴이 유일한 정공법이다.
- 커뮤니티 보일러플레이트인 **DevAtrii/Kmp-Starter-Template**은 RevenueCat·Mixpanel·Remote Config가 기본 포함된 "fat" 베이스이고 라이선스도 비표준 커스텀(KS-1.0)이라 본 프로젝트의 "군더더기 최소화 + 허용적 라이선스" 기준에서 권장하지 않는다. **mirego/kmp-boilerplate**의 `main`(lean) 브랜치는 BSD-3-Clause로 깔끔하지만 마지막 의미 있는 업데이트가 Kotlin 1.9.21 시점에 멈춰 있어 "활발한 유지보수" 기준을 만족하지 못한다.

---

## Key Findings

1. **JetBrains의 기존 `compose-multiplatform-template`과 `compose-multiplatform-ios-android-template`은 2023-12-20에 보관(archived)되었다.** 두 저장소 README에 명시적으로 "This template has been archived. To create Compose Multiplatform projects, use the Kotlin Multiplatform wizard."라고 적혀 있으며, 후속 경로는 **`Kotlin/KMP-App-Template`(shared UI)** 또는 **`Kotlin/KMP-App-Template-Native`(native UI)** 로 일원화됐다.
2. **두 공식 템플릿은 모두 Apache-2.0**이며 JetBrains가 직접 유지하고 있고, 두 저장소의 GitHub Actions 워크플로는 2025년에도 활발히 돌고 있다(KMP-App-Template-Native의 가장 최근 iOS 빌드 워크플로 실행이 2025-04-13에 기록됨).
3. **JetBrains 2025-08 로드맵 업데이트와 2026-05 "New Default Project Structure" 블로그**는 KMP 표준 구조가 AGP 9.0에 맞춰 "단일 `composeApp`" → "`shared` 라이브러리 모듈 + `androidApp` 등 별도 애플리케이션 모듈"로 이동했다고 밝히고, 새 구조의 레퍼런스로 정확히 **KMP-App-Template**을 지목했다. 이 변화는 워치 등 추가 클라이언트 모듈을 끼워 넣기에 오히려 유리하다.
4. **watchOS는 SwiftUI 강제.** Apple Developer 포럼에서 Apple 엔지니어는 "UIKit is not public API on watchOS"라고 명확히 답했고, 기술노트 TN3157(Updating your watchOS project for SwiftUI and WidgetKit)은 SwiftUI/SwiftUI 라이프사이클로의 이전을 공식 권장한다. WatchKit 스토리보드는 watchOS 7.0 이후 deprecated이며 Xcode 14 이후 새 워치 앱 템플릿에서는 SwiftUI만 선택 가능하다.
5. **Compose Multiplatform은 watchOS를 지원하지 않으며 단기 계획에도 없다.** JetBrains의 Compose 멤버 mazunin-v-jb는 깃허브 이슈 #4551에서 "We don't have plans to work on WatchOS support in the near future."라고 답했고, 같은 스레드의 paxbun은 "Metal is not available on watchOS; therefore, custom renderers like Skia are also not available on watchOS"가 기술적 근본 원인이라고 정리했다. 한편 Kotlin/Native는 `watchosArm64 / watchosSimulatorArm64 / watchosX64`(및 레거시 `watchosArm32`, S8+용 `watchosDeviceArm64`)를 지원해 **공유 비즈니스 로직은 KMP framework로 watchOS에 정상적으로 내려보낼 수 있다.**
6. **CRT 셰이더는 shared UI 쪽이 압도적으로 저렴하다.** Compose Multiplatform은 안드로이드에서 `RuntimeShader`(AGSL, Android 13+), iOS/Desktop/Web에서는 Skia `RuntimeEffect`/`RuntimeShaderBuilder`(SkSL)로 동일한 `Modifier.graphicsLayer { renderEffect = … }` API를 사용한다. 즉 셰이더 한 벌(SkSL ↔ AGSL은 문법이 거의 동일)을 작성하면 양 플랫폼에서 같은 코드가 돈다. Native 분기에서는 iOS는 Metal/SwiftUI(`MetalKit`, `SwiftUI.Shader`(iOS 17+) `.colorEffect/.distortionEffect/.layerEffect`) 별도 셰이더 파이프라인을 짜야 한다.
7. **상용 게임 라이선스 적합성**: 공식 두 템플릿(Apache-2.0)과 mirego(BSD-3-Clause)는 안전하다. **DevAtrii/Kmp-Starter-Template은 커스텀 KS-1.0**으로, 본 템플릿으로 만든 앱의 상업 판매는 명시적으로 허용("Sell applications built using the Software")하지만 템플릿 자체를 재판매·재배포할 수 없다. 표준 OSI 라이선스가 아니라는 점이 법무 리뷰 부담이다.

---

## Details

### 후보별 비교표

| 후보 | 공식성 | 라이선스 | ⭐ Stars | 최근 활동 | iOS UI 방식 | 모듈 구조 | 기본 의존성(군더더기?) | Kotlin / CMP |
|---|---|---|---|---|---|---|---|---|
| **Kotlin/KMP-App-Template** | ✅ JetBrains 공식 | Apache-2.0 | 641 (포크 107) | 활발(2025년 GH Actions 다회 실행, 117 commits) | Compose Multiplatform shared UI | `composeApp/`, `iosApp/`, `gradle/`, `images/`, `.github/` (단일 `composeApp` 구조; JetBrains 신구조로 이행 중) | Compose MP, Compose Navigation, Ktor, kotlinx.serialization, Coil, Koin — 분석/IAP/광고 SDK **없음** | 현행 stable Kotlin 2.x + CMP 최신 라인 (1.8.0 iOS Stable 이후) |
| **Kotlin/KMP-App-Template-Native** | ✅ JetBrains 공식 | Apache-2.0 | 243 (포크 39) | 활발(2025-04-13 Build iOS app #44 등 97 commits) | **SwiftUI**(iOS) + Jetpack Compose(Android) | `composeApp/`(Android UI), `iosApp/`(SwiftUI), `shared/`(KMP 라이브러리) | Ktor, kotlinx.serialization, Koin, **KMP-ObservableViewModel**, **KMP-NativeCoroutines**, (Android) Jetpack Compose Navigation, Coil — 군더더기 **없음** | 현행 stable |
| JetBrains/compose-multiplatform-template, …-ios-android-template | 공식이었음 | Apache-2.0 | — | **Archived (Dec 20, 2023)** — 사용 비권장 | — | — | — | — |
| **DevAtrii/Kmp-Starter-Template** | 커뮤니티 | **커스텀 KS-1.0**(비표준; 앱 판매 허용, 템플릿 재판매 금지) | 104 (포크 19) | 활발(v0.4.0 릴리스 2026-02-25, 64 commits) | Compose Multiplatform shared UI | `androidApp/`, `composeApp/`, `iosApp/`, `features/{analytics,core,database,navigation,purchases,remoteconfig}/`, `build-logic/`, `starter/`, `publish/` | **RevenueCat IAP + Mixpanel Analytics + Remote Config + Room DB + DataStore + Koin + 알림 모듈** — 게임용으로는 **과적재** | Kotlin 2.3.10 (README 뱃지), CMP 버전 비공개 |
| **mirego/kmp-boilerplate** | 커뮤니티(에이전시 Mirego) | **BSD-3-Clause**("New BSD") | 33 (포크 4) | **저활동** — Kotlin 1.9.21 시점 이후 정체, 41 commits | **SwiftUI** | `androidApp/`, `ios/`, `shared/` + `main-full` 브랜치(full) / `main` 브랜치(lean) | (main) coroutines + kotlinx.serialization + Ktlint/SwiftLint, (main-full) Ktor, Apollo Kotlin, multiplatform-settings, okio, MockK, **Trikot.KWord(i18n + Accent)** — Trikot 종속 호불호 | 1.9.21 (구버전) |
| android/wear-os-samples — `ComposeStarter` | ✅ Google 공식 | Apache-2.0 | — | 활발(`compose-bom 2026.03.00`, `androidx.wear.compose:compose-material3 1.6.0` 등) | — (Wear OS 전용) | Wear Compose Foundation/Material3/Navigation, Horologist | KMP 코어와 무관, Android Wear 단독 — Wear 모듈 추가 시 참고용 | — |
| android/codelab-compose-for-wear-os | ✅ Google 공식 codelab | Apache-2.0 | — | 활발 | — | TransformingLazyColumn, AppScaffold/ScreenScaffold 등 Wear 전용 컴포저블 학습 | — | — |
| watchOS 측: 공식 별도 스캐폴딩 없음 | — | — | — | — | SwiftUI Watch App(Xcode 새 워치 타깃) + KMP `Shared.framework` 직접 임베드 | — | — | — |

> ※ 정확한 Kotlin/CMP/Ktor/Koin 버전 숫자는 두 공식 템플릿의 `gradle/libs.versions.toml` 파일에 명시돼 있으나, 본 리서치 도구의 `raw.githubusercontent.com` 접근이 차단되어 **현 시점 정확 버전 수치는 본문에서 단정하지 않는다.** JetBrains가 두 템플릿을 KMP 표준 레퍼런스로 갱신하고 있다는 사실(2026-05 블로그)과 GitHub Actions 빌드 상태로 미루어 **현행 stable Kotlin 2.x 및 최신 stable Compose Multiplatform**(2025-05-06 iOS Stable 도달한 1.8.0 이후 라인, 2025-09-22 1.9.0 Web Beta, 2026-01-13 1.10.0 Compose Hot Reload Stable, 그 후 1.11.0)에 맞춰져 있다고 보면 된다.

---

### 1) `KMP-App-Template`(shared UI) vs `KMP-App-Template-Native`(SwiftUI) — Trade-off

| 축 | KMP-App-Template (shared UI) | KMP-App-Template-Native |
|---|---|---|
| **iOS 코드 재사용도** | **최상**. UI까지 commonMain 작성 → JetBrains가 인용한 Respawn iOS 앱은 Compose Multiplatform 1.8.0 발표 블로그(Ekaterina Petrova, 2025-05-06)에서 "The Respawn iOS app is built with Compose Multiplatform, sharing 96% of its code with Android"로 공식 인용됨. Sonar Tamagotchi처럼 화면 수가 적고 게임 로직 비중이 큰 앱은 90% 후반 공유 가능. | **중**. 비즈니스 로직만 공유, iOS UI는 SwiftUI 별도 작성. 같은 화면을 두 번 그려야 함. |
| **iOS/watchOS 네이티브 정합성** | iOS Material 기본 UI(커스텀 Cupertino 테마 필요). 시스템 다이얼로그/네비게이션 패턴은 Compose가 흉내내지만 100% 일치는 아님. 접근성(VoiceOver)도 SwiftUI 대비 갭이 남아 있음. | **최상**. SwiftUI 네이티브이므로 iOS 26 Liquid Glass, Live Activity, Widget, App Intent 등 신규 API 접근 즉시 가능. watchOS도 SwiftUI라 코드/팀 패턴이 일치. |
| **CRT 셰이더 난이도** | **압도적 유리**. `Modifier.graphicsLayer { renderEffect = RenderEffect.createRuntimeShaderEffect(...).asComposeRenderEffect() }` 형태로 commonMain에서 한 번 작성, Android는 AGSL(API 33+)·iOS/Desktop은 Skia SkSL로 자동 분기. CRT 스캔라인/배럴 디스토션/노이즈/소나 ping 효과를 expect/actual 한 쌍으로 깔끔히 처리. 안드로이드 13 미만은 `isShaderAvailable()` 체크로 폴백. | iOS에서는 Metal 셰이더(`MetalKit` + `MTKView`) 또는 SwiftUI 5의 `Shader`/`.colorEffect`/`.distortionEffect`/`.layerEffect`(iOS 17+)로 별도 작성. Android는 별도로 AGSL 작성. **셰이더를 두 번 짜야 함.** |
| **2D 캔버스 렌더(스탯 드리프트 시각화·NPC 스프라이트·소나 그리드)** | Compose `Canvas` + `drawIntoCanvas` + `Path`. KMP 게임 로직(상태 머신, 16칸 RPS 매트릭스 결과)을 `State<T>`로 그대로 바인딩. | Android는 Compose `Canvas`, iOS는 SwiftUI `Canvas`/`TimelineView`. 같은 그림을 두 번 그림. |
| **햅틱·알림·위젯·Live Activity·App Intent** | 공유 코드에서는 안 됨 → `expect/actual` 로 iOS 측은 Swift bridging(`HapticFeedback`, `UNUserNotificationCenter`, `WidgetKit`, `ActivityKit`)을 별도 작성. | 동일하지만 **iOS 측 모든 코드가 이미 Swift/SwiftUI**라 추가 브리지 없이 자연스러움. |
| **Apple Watch 컴패니언 후속 영향** | **워치는 어차피 SwiftUI**. shared UI(Compose)는 iPhone 앱에만 적용되므로 워치 추가 시 **iPhone Compose ↔ watchOS SwiftUI** 두 가지 UI 스택을 한 프로젝트에서 다루게 됨. KMP 코어(`shared`)는 그대로 워치에도 `Shared.framework`로 임베드. | **워치도 SwiftUI, 폰도 SwiftUI**라서 iOS 측 UI 스택이 **단일**. WatchConnectivity, App Group, SharedDefaults 등 iOS↔워치 데이터 동기화 코드를 SwiftUI 패턴 그대로 재사용 가능. |
| **빌드 복잡도** | 단일 `composeApp` Gradle 모듈에서 모두 빌드. iOS는 `embedAndSignAppleFrameworkForXcode` 한 줄. **간단**. | `shared` 모듈 + Xcode 프로젝트 분리. SwiftUI 측에서 KMP-ObservableViewModel과 KMP-NativeCoroutines로 `@Published`처럼 사용. **표준적이지만 학습 곡선 있음**. |
| **디버깅 경험** | Compose Hot Reload(2026-01-13 v1.10.0 stable 승격, JetBrains 블로그 "Compose Hot Reload Gradle plugin is bundled with the Compose Gradle plugin and is enabled for Kotlin version 2.1.20 or higher.") + Android Studio 디버거. iOS도 동일 코드라 Logcat/Console 단일 시점. | iOS는 Xcode 디버거, Android는 AS 디버거로 분기. Swift 측 ViewModel 디버깅은 자연스럽지만 Kotlin↔Swift 경계는 별도. |
| **폰 출시 → 워치 후속 마이그레이션 리스크** | shared UI 코드 그 자체는 **워치에 그대로 이식 불가**(Compose가 watchOS 미지원). 단, 모듈 분리(`core-game` = 순수 Kotlin 도메인)를 처음부터 잘 해두면 워치에는 코어만 재사용하면 되므로 큰 리스크 없음. **위험은 "iPhone Compose UI를 워치용 SwiftUI로 못 옮기는 것"이 아니라 "워치 UI는 어차피 처음부터 새로 작성"이라는 점**. | shared UI가 처음부터 없으므로 워치 추가가 더 자연스러움. **iOS·watchOS 두 SwiftUI 앱에서 SwiftUI View 컴포넌트(케어 알림 패널, 빠른 액션 버튼)를 어느 정도 공유**할 수 있어 워치 후속이 가장 매끈함. |

**결론(보류 가능한 형태로)**: 본 게임의 핵심 자산은 ① 순수 Kotlin 게임 로직 ② CRT 셰이더 + 2D 캔버스다. 둘 다 **shared UI 쪽이 강력**하다. 반대로 워치 컴패니언을 사실상 케어 알림 + 빠른 액션(소형 SwiftUI 화면 2~3개)으로만 잡는다면 워치 단계에서 SwiftUI를 손대야 한다는 점은 **두 옵션 모두 동일**하므로, "워치를 위해 Native를 선택"하는 것은 과한 보험이다. 단 iOS Liquid Glass/Live Activity/위젯 등에 깊이 의존할 계획이라면 Native가 유리하다.

---

### 2) ★ watchOS의 SwiftUI 강제 여부 — 명확한 검증 결과

| 질문 | 결론 | 근거 |
|---|---|---|
| watchOS 앱에서 SwiftUI가 강제되는가? | **사실상 강제.** Xcode 14 이후 새 Watch App 템플릿은 SwiftUI 라이프사이클만 제공. WatchKit 스토리보드는 watchOS 7.0부터 deprecated되어 빌드 시 경고가 뜬다. | Apple Developer 포럼 스레드 #709843("Fixed: WatchKit storyboards are deprecated in watchOS 7.0 and later. Please migrate to SwiftUI and the SwiftUI Lifecycle."), Apple TN3157 — "Updating your watchOS project for SwiftUI and WidgetKit". |
| watchOS에서 UIKit 사용 가능? | **불가(공개 API 아님).** Apple 측 답변: "UIKit is not public API on watchOS." 즉 워치에서 UIKit을 직접 import할 수 없다. | Apple Developer 포럼 #709843. |
| Compose Multiplatform이 watchOS를 지원? | **아니오. 단기 로드맵에도 없다.** | JetBrains/compose-multiplatform 이슈 #4551, mazunin-v-jb(JetBrains): "We don't have plans to work on WatchOS support in the near future." paxbun: "Metal is not available on watchOS; therefore, custom renderers like Skia are also not available on watchOS." |
| Kotlin/Native의 watchOS 타깃 범위? | **`watchosArm64`(Apple Watch S4+, ARM64_32), `watchosSimulatorArm64`(Apple Silicon Mac 시뮬레이터), `watchosX64`(Intel Mac 시뮬레이터 — watchOS 7+), 레거시 `watchosArm32`(S3 이하), 신규 `watchosDeviceArm64`(S8/S9 64-bit, atomicfu 등 라이브러리 지원 후 사용 가능). 정식 빌드 가능.** | kotlinlang.org/docs/native-target-support.html (Tier 표), Kotlinx-coroutines #3601 "Support watchosDeviceArm64". |
| Apple 공식 권장 사항? | "Update your watchOS app project to adopt SwiftUI, WidgetKit, and other modern features." | Apple Developer Documentation TN3157. |
| KMP shared logic → watchOS 소비 패턴? | **KMP가 `Shared.framework`(또는 XCFramework)로 watchOS 타깃을 포함해 빌드 → Xcode에서 별도 Watch App 타깃 추가 → SwiftUI에서 `import Shared`로 호출**. Ktor 등 일부 라이브러리는 watchOS 변형(Darwin) 지원 버전 확인 필요. watchosDeviceArm64는 Ktor 3.0.0-rc-2 릴리스 노트("Add watchosDeviceArm64 target (KTOR-6368)")에서 최초로 추가되어 stable 3.0.0에 포함됐다. | oliverdelange.co.uk "Kotlin Multiplatform on Apple Watch" — `watchosX64() / watchosArm64() / watchosSimulatorArm64()` 추가 후 `embedAndSignAppleFrameworkForXcode`로 Watch App 타깃 Build Phase에 임베드. JetBrains YouTrack KT-53107. |

**요약 한 줄**: watchOS는 UI는 SwiftUI로만(공식적으로) 짤 수 있고, KMP 비즈니스 로직만 .framework 형태로 끌어다 쓴다. Compose 코드를 워치에 재사용하는 길은 **현재도 향후 1년 이내에도 없다**.

---

### 3) CRT 셰이더 구현 패턴 — shared UI 채택 시

```kotlin
// commonMain
expect class CrtShader {
    fun apply(scope: GraphicsLayerScope, time: Float, intensity: Float)
}

// androidMain (Android 13+ AGSL)
actual class CrtShader {
    private val shader = RuntimeShader(crtAgsl) // scanline + barrel + chromatic aberration
    actual fun apply(scope: GraphicsLayerScope, time: Float, intensity: Float) {
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uIntensity", intensity)
        scope.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

// iosMain + desktopMain + wasmJsMain (skikoCommon)
actual class CrtShader {
    private val effect = RuntimeEffect.makeForShader(crtSksl) // 동일 셰이더, 문법 호환
    actual fun apply(scope: GraphicsLayerScope, time: Float, intensity: Float) {
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("uTime", time); uniform("uIntensity", intensity)
        }
        scope.renderEffect = ImageFilter.makeRuntimeShader(builder, "content", null)
            .toComposeRenderEffect()
    }
}
```
Android 12 이하는 `isShaderAvailable()` 폴백으로 단순 비네팅+노이즈 텍스처로 우아하게 떨어진다. iOS에서는 Skia가 자체 번들이라 OS 버전 게이트 없음.

---

### 4) 멀티모듈 구조 제안 (Sonar Tamagotchi 채택안)

JetBrains가 2026-05에 발표한 "New Default Project Structure" 권고와 본 게임의 후속 워치 확장을 함께 고려한 권장 트리(상위 추천안 = **KMP-App-Template 기반 + 모듈 분할**):

```
sonar-tamagotchi/
├── core-game/                ← 순수 Kotlin (commonMain 100%), 무 의존
│   ├── stat-drift            (스탯 드리프트 규칙)
│   ├── rps-matrix            (16칸 RPS 결과 매트릭스)
│   ├── probability-tables    (확률 테이블 + Seedable RNG)
│   ├── npc-ai                (간단 FSM 기반 NPC AI)
│   └── persistence           (SQLDelight 또는 multiplatform-settings 어댑터 인터페이스)
├── core-data/                ← 저장소, 직렬화, 시간 소스
│   └── (kotlinx.serialization, kotlinx-datetime, Koin)
├── shared-ui/                ← Compose Multiplatform 공통 UI
│   ├── theme-crt             (색상, 폰트, 글로우)
│   ├── shader-crt            (expect/actual CRT 셰이더)
│   ├── canvas-sonar          (소나 ping/그리드 Canvas)
│   ├── screens               (피딩, 케어, 대전, 도감)
│   └── viewmodels            (KMP-ObservableViewModel 사용; 워치도 재사용)
├── app-android/              ← Android 폰 엔트리포인트, 햅틱·알림 expect actual
├── app-ios/                  ← iOS 폰 엔트리포인트 (ComposeUIViewController로 shared-ui 임베드)
├── wear-android/             ← Wear OS (Compose for Wear OS, Tiles, Complications)
│   └── core-game + core-data 만 의존 (shared-ui는 폰 전용, Wear는 Wear Compose로 작성)
└── wear-watchos/             ← watchOS Watch App (SwiftUI)
    └── KMP XCFramework로 core-game + core-data만 소비
```

> 핵심 원칙: **`shared-ui`는 폰 전용**(워치는 화면 크기와 디자인 패턴이 달라 어차피 재사용 가치가 낮음). **`core-game`/`core-data`는 모든 4개 클라이언트에서 동일하게 소비**. 워치 모듈은 폰 출시 이후 별도 Gradle/Xcode 타깃으로 가산만 하면 되고 아키텍처 변경은 불요.

---

## Recommendations

### ✅ 1순위 추천: `Kotlin/KMP-App-Template` (shared UI) 기반 채택

**왜?** ① CRT 셰이더가 한 벌로 끝남(같은 SkSL/AGSL을 expect/actual 두 곳에만 actual 처리), ② 2D 캔버스 + 게임 루프 코드가 안드로이드/iOS에서 동일하게 검증 가능, ③ JetBrains 공식 + Apache-2.0 + 최신 KMP 구조 표준 레퍼런스, ④ 워치 후속은 어차피 별도 SwiftUI 작성이 필요한데 이 결정과 무관.

**채택 시 즉시 수행할 수정 포인트** (체크리스트):

1. **루트의 단일 `composeApp` 모듈을 분리.** 위의 `core-game / core-data / shared-ui / app-android / app-ios` 5분할로 리팩터. (JetBrains 2026-05 New Default Structure 권고에 부합.)
2. **불필요한 의존성 제거**: 템플릿의 Met Museum API용 Ktor·Coil은 게임에서 불필요하면 제거. 단 Ktor는 점수 동기화/리더보드 도입 시 다시 추가될 수 있어 보존 권장.
3. **`shared-ui/build.gradle.kts`에 Skia·AGSL 셰이더용 source set hierarchy 추가** (`skikoCommonMain` 그룹 = ios+desktop+web을 묶음). `Modifier.graphicsLayer { renderEffect = … }` 한 줄로 양 플랫폼 동일 SkSL/AGSL을 적용할 수 있도록 `expect class CrtShader` 빌드.
4. **`KMP-ObservableViewModel` + `KMP-NativeCoroutines` 추가**(원래 Native 템플릿에만 들어 있음). 이는 폰 단계에서는 굳이 필요 없지만, 워치 후속(SwiftUI에서 `@StateObject`처럼 소비) 단계에서 KMP ViewModel을 그대로 워치 SwiftUI에 노출하려면 사실상 필수.
5. **`iosApp`에 watchOS 타깃 빌드 옵션 미리 준비**: shared 모듈 `build.gradle.kts`에 `watchosArm64()`, `watchosSimulatorArm64()`, `watchosX64()` 타깃을 **지금은 주석 처리해 두고**, Ktor·Koin·kotlinx.datetime 등 모든 의존성이 워치 타깃을 지원하는지 점검(특히 watchosDeviceArm64는 Ktor 3.0.0-rc-2 이상이 필요 — Ktor 릴리스 노트 "Add watchosDeviceArm64 target (KTOR-6368)").
6. **Compose Hot Reload(1.10.0 stable, 2026-01-13 릴리스)** 활성화 → CRT 셰이더 파라미터 튜닝 생산성 폭증.
7. **CI는 KMP-App-Template의 GitHub Actions `Build Android app` / `Build iOS app` 워크플로**를 그대로 가져와 시작. (실제로 활발히 유지보수되는 공식 워크플로.)

### ✅ 2순위(서브) 추천: `Kotlin/KMP-App-Template-Native`

다음 중 **하나라도** 해당하면 Native로 전환:
- iOS Liquid Glass(iOS 26+), Live Activity, App Intent, Widget을 1차 출시에서 깊게 사용
- iOS UI/UX 디자이너가 SwiftUI 네이티브 룩에 강한 요구
- 워치 컴패니언이 단순 알림 수준이 아니라 **상시 사용 기능**(Always-On Display, 워크아웃 형 인터랙션)으로 확장될 가능성이 큼 → iOS·watchOS 모두 SwiftUI로 일관

이 경우 위 멀티모듈 트리에서 `shared-ui` 모듈을 제거하고 `app-ios`를 SwiftUI로 완전 작성한다. CRT 셰이더는 iOS 측 `SwiftUI.Shader`(iOS 17+, `.colorEffect/.distortionEffect/.layerEffect`)로 별도 작성해야 함.

### ❌ 비추천

- **`DevAtrii/Kmp-Starter-Template`**: RevenueCat/Mixpanel/Remote Config 등 분석/IAP가 기본 포함되어 게임의 "군더더기 최소화" 기준 미달. KS-1.0 커스텀 라이선스("KMP Starter Template License (KS-1.0)")로 표준 OSI가 아님 — 앱 판매 자체는 명시적으로 허용("Sell applications built using the Software")하지만 법무 검토 부담 존재.
- **`mirego/kmp-boilerplate`**: BSD-3-Clause로 라이선스는 깔끔하나 Kotlin 1.9.21 시점 이후 의미 있는 유지보수가 없음(Mirego의 자체 Trikot 종속도 호불호). "최근 커밋 활발" 기준 미달.
- **JetBrains의 archived 템플릿 두 개**: 명시적 archived 상태 → 사용 금지.

### 단계별 진행 계획과 분기 기준

| 단계 | 조치 | 분기 기준(다음 단계로 가는 트리거) |
|---|---|---|
| 1. 베이스 채택 | `Kotlin/KMP-App-Template` fork → 위 5분할 모듈로 리팩터, Met Museum 코드 삭제 | 빌드 그린, Android·iOS 양쪽에서 CRT 셰이더 1개 + Canvas로 그린 스탯 게이지 동작 |
| 2. 폰 MVP | 게임 로직(core-game), CRT/소나 셰이더, 4~6개 화면 완성 | 양 플랫폼에서 60fps, iOS 메모리 200MB 이하 |
| 3. 폰 출시 | Android Play Store / Apple App Store | DAU 5k 또는 명시적 워치 요청 5%↑ 시 다음 단계 |
| 4. Watch 컴패니언 | `wear-android`(Compose for Wear OS, ComposeStarter 패턴) + `wear-watchos`(SwiftUI Watch App + `Shared.framework`) 추가. core-game/core-data만 의존, shared-ui는 사용 안 함. | 워치-폰 동기화는 Android는 `DataLayer` API, watchOS는 `WatchConnectivity` |

**전환을 고려해야 하는 임계치(분기 기준)**:
- iOS 측 디자이너가 SwiftUI 컴포넌트(Liquid Glass, Charts, Live Activity)에 게임 핵심 UX를 묶기 시작 → **Native 분기로 갈아탈 시점**.
- 워치가 단순 알림이 아닌 게임 플레이의 일부(예: 워치 단독 미니 RPS)가 됨 → **shared-ui 의존도 낮추고 wear 모듈 비중 확대**.

---

## Caveats

- **버전 수치의 정확도**: 본 도구의 `raw.githubusercontent.com` 직접 접근이 차단되어 두 공식 템플릿의 `gradle/libs.versions.toml`에 적힌 정확한 Kotlin/CMP/Ktor/Koin 버전 숫자는 단정하지 않았다. README와 JetBrains 블로그·GitHub Actions 활동 이력으로 "현재 stable 라인에 맞춰져 있다"는 점만 확인했다. 검증된 마일스톤: **Compose Multiplatform 1.8.0(2025-05-06, iOS Stable; JetBrains Kotlin Blog, Ekaterina Petrova: "Today marks a major milestone in the Kotlin Multiplatform journey: the release of Compose Multiplatform 1.8.0, which brings Compose for iOS to Stable."), 1.9.0(2025-09-22, Web Beta; Neowin: "JetBrains has released Compose Multiplatform 1.9, bringing improvements to the platform…as well as finally moving its WASM-powered web target into beta."), 1.10.0(2026-01-13, Compose Hot Reload Stable; JetBrains Kotlin Blog: "Compose Hot Reload Gradle plugin is bundled with the Compose Gradle plugin and is enabled for Kotlin version 2.1.20 or higher."), 1.11.0(UI Testing 갱신).** 채택 직후 `libs.versions.toml`을 직접 열어 확인 후 최신 stable로 정렬할 것.
- **DevAtrii 라이선스**: KS-1.0은 "앱 판매 허용, 템플릿 재판매 금지"라는 의미에서 상용 게임 빌드에는 무방하나, 표준 OSI 라이선스가 아니라는 점에서 법무 부서가 OSS 컴플라이언스 표를 만들 때 예외 처리 부담이 생긴다. **본 프로젝트 평가 기준 3(허용적 라이선스)을 엄격 해석할 경우 부적합**.
- **mirego/kmp-boilerplate의 활동성**: 33 stars, 41 commits, Kotlin 1.9.21 — "stable base"라는 표어지만 KMP 생태계 속도(2025년 한 해에 CMP iOS Stable 1.8.0 → 1.9.0 Web Beta → 2026-01 1.10.0 Hot Reload Stable → 1.11.0 UI Test 갱신)를 따라잡지 못한 상태로 보인다. 채택 시 라이브러리 버전 일괄 업그레이드 부담이 크다.
- **Compose Multiplatform iOS의 잔여 미숙 영역**: 2026년 시점에서도 **iOS 접근성(VoiceOver) 완전 SwiftUI 동급은 아님**, 복잡한 텍스트 입력(IME, 다국어 리치 텍스트)·구형 iOS 디바이스 대용량 리스트는 프로파일링이 권장된다(2026-03 Medium 사후 보고). 본 게임은 텍스트 입력이 거의 없고 리스트도 도감 정도라 영향 적음.
- **Compose Multiplatform watchOS는 "당분간 불가"**: paxbun의 코멘트는 JetBrains 직원 답변이 아니지만 기술적 진단은 정확하다(Metal 부재). mazunin-v-jb(JetBrains)의 "단기 계획 없음" 진술이 공식 입장으로 봐야 한다. 이 상황은 **Apple이 watchOS에 Metal을 허용하기 전까지** 변하지 않을 가능성이 높다(현재까지 변화 신호 없음).
- **Wear OS 측 Compose**: Wear Compose는 Jetpack Compose와 라이브러리가 분리되어 있고(`androidx.wear.compose.material3` 등), KMP commonMain에 직접 들어가지 않는다. 따라서 워치 안드로이드 모듈은 commonMain UI를 재사용하지 못하고 별도 화면을 작성해야 한다. 다행히 두 워치 플랫폼(Wear OS, watchOS) 모두에서 `core-game`/`core-data`만 공유하는 본 보고서의 설계는 이를 자연스럽게 처리한다.
- **Apple Watch 독립/컴패니언 모드**: Apple은 watchOS 6 이후 독립 앱을 권장하나, "한 번 독립으로 만들면 컴패니언으로 되돌릴 수 없다"는 운영 함정이 있다(Apple Developer 포럼 #130351의 ITMS-90768 사례). Sonar Tamagotchi의 워치는 "케어 알림 + 빠른 액션"이라는 컴패니언 성격이 명확하므로 **컴패니언 모드(`WKCompanionAppBundleIdentifier`)로 시작**하기를 권장.