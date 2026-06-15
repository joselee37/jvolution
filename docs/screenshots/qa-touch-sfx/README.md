# QA — 풀 터치 레이어 + 실제 SFX (feature/full-touch-sfx)

> 환경: 전용 에뮬레이터 `jvolution_01`(emulator-5570), Pixel급 1080×2400.
> 빌드: `:core:jvmTest` `:shared:jvmTest` `:shared:compileCommonMainKotlinMetadata` `:androidApp:assembleDebug` 전부 통과 후 설치.
> 스펙: `docs/superpowers/specs/2026-06-10-full-touch-layer-design.md`

| # | 스크린샷 | 시나리오 | 결과 |
|---|---|---|---|
| 01 | `01-chips-sonar.png` | 부팅 — 칩 스트립(FEED…RADAR) + `◉ LINK·7` 접점 수 + military 베젤 | ✅ |
| 02 | `02-chip-feed-echo.png` | FEED 칩 탭 → 터미널 `$ feed` 에코 + "unit fed. hunger -25%." | ✅ 매크로 경로 |
| 03 | `03-creature-tap-ping.png` | 생명체 탭 → 스윕 점등, 터미널에 **에코 없음** | ✅ 무음 ping 경로 |
| 04a | `04a-radar.png` | RADAR 칩 → 레이더 전환 + 피어 목록 출력 + 칩이 BACK 선두로 변경 | ✅ 컨텍스트 칩 |
| 04 | `04-radar-blip-select.png` | 블립 탭 → 타깃 링 + `bond arc-9` 자동 조회 + `CHALLENGE ARC-9` 칩 등장 | ✅ 2단계 도전 |
| 05 | `05-challenge-battle.png` | CHALLENGE 칩 탭 → "ARC-9 accepts. ENGAGE." → 전투 진입, 칩 **FLEE만** | ✅ 전투 잠금 일치 |
| 05b/c | `05b…/05c-hp-flash.png` | CHARGE 커밋 → CLASH → R.02 진입, 양측 HP 차감 | ✅ (420ms 플래시는 정지 캡처 한계 — 코드 리뷰로 검증) |
| 06a~c | `06a…/06c-tree-view.png` | FLEE 복귀 → TREE 칩(가로 스크롤) → 계보 화면 + 탭 힌트 | ✅ |
| 06 | `06-tree-node-tap.png` | G01 노드 헤더 탭 → `$ tree 1` 에코 + 세대 상세 readout | ✅ 신규 명령 |
| 07 | `07-squid-vintage.png` | 설정 squid 선택 후 ping → **squid 실루엣** 이 **vintage 하우징** 안에서 점등 | ✅ species 단일 소스 |
| 07b | (미캡처) | 피어 도전 수신 → 오버레이 ACCEPT/DECLINE 버튼 + LINK 점멸 | ⚠️ RNG 발동 ~30분 미수신으로 캡처 생략 — 버튼은 칩과 동일한 `submitCommand` 매크로(2단계 리뷰 통과), 칩 경로는 02/04/05에서 실증 |
| 08 | `08-persist-restored.png` | 에뮬레이터 재시작 → **LARVA 단계 복원** (save v2) | ✅ |
| 08b/c | `08b…/08c-settings-changed.png` | ⚙ CFG → BEZEL Housing 라디오(신규)·Species·SFX 토글 전부 즉시 반영 | ✅ |
| 09 | `09-scold-mood.png` | SCOLD 칩 → 무드 라벨 **SCOLDED** + 토스트 (수리된 transient) | ✅ 데모 버그 수리 |
| 09b | `09b-evolve-chip.png` | canEvolve 시 ★EVOLVE 강조 칩 → 탭 → EVOLVING 시퀀스 → LARVA 전이 | ✅ |

## SFX 수동 확인

- 설정 SFX **ON** 토글 시 Confirm 비프 발음(에뮬레이터 호스트 오디오), 이후 FEED/PING/SCOLD에서 각기 다른 레트로 톤 확인. OFF 시 전부 무음. 스크린샷 불가 — 청취 확인으로 기록.

## 알려진 한계 / 후속

- `SfxPlayer.ios.kt`(AVAudioEngine) iOS 검증 — **macOS(Xcode 26.4 / iOS Sim SDK 26.4)에서 완료(2026-06-11):**
  - `:shared:compileKotlinIosArm64` + `iosSimulatorArm64` → `BUILD SUCCESSFUL`. 의심했던 K/N 바인딩 8곳(`pCMFormat` 라벨, 이중 포인터 인덱싱, `engine.running`/`player.playing`, `setActive` 확장 import, `setCategory`/`scheduleBuffer` 오버로드, failable `AVAudioFormat` init) **전부 수정 없이 컴파일** — 핸드오프 분석이 정확했고 `fix(shared)` 커밋 불필요.
  - `:core:allTests`(`iosSimulatorArm64Test` 포함) → 그린.
  - Xcode 통합 빌드: `xcodebuild -scheme iosApp -sdk iphonesimulator`(iPhone 15 시뮬) → `** BUILD SUCCEEDED **`. 이 경로가 Gradle `embedAndSignAppleFrameworkForXcode`를 실제로 구동(주의: 해당 태스크는 `syncComposeResourcesForIos`가 Xcode 주입 변수 `PLATFORM_NAME`/`BUILT_PRODUCTS_DIR` 등을 요구해 **단독 gradle 실행 불가** — 반드시 xcodebuild로 구동).
  - 시뮬 런타임: 앱 설치·실행 → 크래시 없이 풀 렌더(터치 레이어 칩 스트립·SONAR 베젤·터미널). **필드 초기화자의 failable `AVAudioFormat` init이 nil을 반환하지 않아 Koin 주입 시점 크래시 없음**(스모크 §3 앱-기동 게이트 통과).
  - **잔여(미완) — 가청 SFX 스모크 §3 #1–3:** 비프 발음/인터럽션 복구 재생/배경음악 믹스는 헤드리스 시뮬에서 증빙 불가(스크린샷에 소리 안 잡힘) — **기기/시뮬에서 청취 수동 확인 필요.**
- HP 피격 플래시(420ms)는 정지 캡처로 시각 증빙 불가 — `BattleScreen.kt` LaunchedEffect 배선은 리뷰로 검증.
