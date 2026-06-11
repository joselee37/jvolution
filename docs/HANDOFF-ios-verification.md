# 핸드오프 — iOS 빌드/SFX 검증 (macOS 필요)

> **배경:** `feature/full-touch-sfx` 마일스톤(풀 터치 레이어 + 실제 SFX, master 머지됨)은 Linux에서
> 개발·검증되었다. Kotlin/Native iOS 타깃은 macOS에서만 컴파일되므로 **iosMain 신규 코드의
> 컴파일·런타임 검증이 미완**이다. 이 문서는 macOS에서 수행할 검증 절차와 예상 수정 지점을 담는다.
>
> 관련: 스펙 `docs/superpowers/specs/2026-06-10-full-touch-layer-design.md` ·
> QA 기록 `docs/screenshots/qa-touch-sfx/README.md` (Android 검증 완료분)

## 1. 검증 대상

이번 마일스톤에서 iosMain에 추가된 파일은 **1개**:

- `shared/src/iosMain/kotlin/today/superb/jvl/sound/SfxPlayer.ios.kt`
  — `AVAudioEngine` + `AVAudioPlayerNode`로 합성 PCM(FloatArray)을 재생하는 `expect SfxPlayer`의 actual.
  이 저장소에서 **cinterop 포인터를 쓰는 첫 iosMain 코드**라 K/N 바인딩 라벨이 미확인 상태다.

commonMain 변경(터치 레이어·칩·species 등)은 플랫폼 중립이며 `compileCommonMainKotlinMetadata`로
expect/actual 매칭까지 확인됨 — iOS 한정 리스크는 위 파일 + 앱 통합뿐이다.

## 2. 컴파일 검증 (1차 게이트)

```sh
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
./gradlew :core:allTests        # iOS 시뮬 포함 전 타깃 테스트
./gradlew :shared:embedAndSignAppleFrameworkForXcode   # Xcode 통합 빌드 표면
```

### 컴파일 에러가 나면 — 의심 지점 체크리스트 (개발 중 기록된 순서)

`SfxPlayer.ios.kt`에서 K/N 바인딩 시그니처가 어긋날 수 있는 곳. 전부 1줄 수정 수준:

| # | 코드 | 의심 내용 | 어긋날 때 대안 |
|---|---|---|---|
| 1 | `AVAudioPCMBuffer(pCMFormat = format, frameCapacity = n)` | 생성자 라벨 — ObjC `initWithPCMFormat:` 의 K/N decapitalize는 `pCMFormat`이 맞을 것으로 분석됨(셀렉터 첫 조각 규칙) | 라벨 제거(positional) 또는 IDE 자동완성으로 실제 라벨 확인 |
| 2 | `buffer.floatChannelData?.get(0)` + `channel[i] = v` | `CPointer<CPointerVar<FloatVar>>` 이중 포인터 인덱싱. `kotlinx.cinterop.get/set` import 필요(이미 있음) | `channel.set(i, v)` 명시 호출 |
| 3 | `engine.running` | ObjC `isRunning` getter → K/N 프로퍼티명 `running` | `isRunning()` 메서드형으로 노출될 수도 |
| 4 | `import platform.AVFAudio.setActive` | `setActive(_:error:)`가 멤버가 아닌 **패키지 확장 함수**로 노출된다는 가정으로 import를 추가함 — 멤버라면 이 import 자체가 컴파일 에러 | import 삭제 후 멤버 호출 |
| 5 | `session.setCategory(AVAudioSessionCategoryAmbient, null)` | 2-인자 오버로드 선택(3-4인자 변형 존재) | `setCategory(category, error = null)` named 또는 withOptions 변형 |
| 6 | `player.scheduleBuffer(buffer, completionHandler = null)` | 2-인자 오버로드 vs `(buffer, atTime, options, handler)` 4-인자 | named arg 유지하면 모호성 없음 — 라벨명만 확인 |
| 7 | `player.playing` | `isPlaying` getter → `playing` | #3과 동일 패턴 |
| 8 | `AVAudioFormat(AVAudioPCMFormatFloat32, 44100.0, 1u, false)` | failable init이 **필드 초기화자**에 있음(runCatching 밖) — 이 인자 조합으로 nil은 안 나오지만, nil이면 Koin 주입 시점 크래시 | 문제 시 lazy + null 가드로 이동 |

## 3. 런타임 스모크 (시뮬레이터)

Xcode에서 `iosApp` 실행 후:

1. **기본 재생** — ⚙ CFG → AUDIO → SFX `[ ON ]` → FEED 칩 탭 → 비프 발음 확인. OFF 토글 → 무음 확인.
2. **인터럽션 복구** (개발 중 Critical로 수정된 경로 — `started && engine.running` 재기동):
   홈으로 나갔다 복귀(또는 시뮬레이터에서 Siri 호출) → FEED 칩 → **비프가 다시 나는지**.
   재생이 영구 무음이면 `ensureStarted()`의 엔진 재시작 분기가 동작하지 않는 것.
3. **믹스 동작** — 배경에서 Music 앱 재생 중 SFX 발음 시 음악이 **끊기지 않아야** 함
   (AVAudioSession `Ambient` 설정 검증). 끊기면 §2 #4/#5의 세션 호출이 실효되지 않은 것.
4. **터치 레이어 일반 동작** — Android QA(README 참조)와 동일 시나리오 일부:
   칩 탭 에코 / 생명체 탭 스윕 / 블립 탭 → CHALLENGE 칩 / 설정 species·bezel 즉시 반영.

## 4. 완료 기준

- [ ] `:shared:compileKotlinIosArm64` + `iosSimulatorArm64` 그린 (수정이 있었다면 커밋)
- [ ] `:core:allTests` 그린
- [ ] 스모크 1–4 통과 — 결과를 `docs/screenshots/qa-touch-sfx/README.md`의
      "알려진 한계" 절에 반영(검증 완료로 갱신하거나 발견 이슈 기록)
- [ ] 수정 커밋은 `fix(shared): adjust AVFAudio bindings for K/N` 형식으로
