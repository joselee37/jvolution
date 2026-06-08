# 영속화(persistence) Android 동작 검증 (2026-06-09)

데모는 in-memory였으나(재시작 시 리셋), 생명체·계보·피어 관계·설정이 앱 재시작 후에도 유지되도록
영속화를 추가했다. 전용 에뮬레이터 `emulator-5570`에서 검증.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-restored.png` | `name PERSIST2` + feed×2 + train → **앱 강제 종료(force-stop)** → 재실행 → "PERSIST2 · EGG" 복원(NAUTI 아님), 부팅 배너 "unit PERSIST2 acquired". 뷰=Sonar(전투/레이더/토스트 transient 미복원), training 스탯 유지 확인 |

설계(설계 워크플로 산출) + 적대적 리뷰 워크플로(13건 중 5건 실이슈 반영) 거쳐 구현:

**아키텍처**
- `@Serializable`을 `:core` 도메인에 직접 부착(serialization-core만 — `CorePurityTest` 통과, `:core-data` 모듈 불필요). JSON 코덱은 `:shared`.
- `SaveCodec`(`:shared`): `SaveBlob(schemaVersion, game, tweaks)` ↔ JSON. 저장 전 transient(전투/뷰/토스트/nonce/pendingRequest) 제거 → 재시작 시 진행 중 전투/토스트가 되살아나지 않음.
- `GameStore` expect/actual(핸드롤, `nowMillis` 선례): Android SharedPreferences(`AppContext` 홀더를 `JvolutionApp.onCreate`가 주입) / iOS NSUserDefaults / JVM in-memory.
- 로드: Koin 팩토리에서 **동기** load → `GameViewModel(initialState=…, initialTweaks=…)`(새 펫 깜빡임 방지). 저장: VM의 debounce(1s) 콜렉터.

**리뷰 반영(5건)**
1. `onCleared()` 동기 flush — 소멸 직전 ~1s 변화 손실 방지.
2. `coerceInputValues` + `decode`의 `schemaVersion` 분기(향후 마이그레이션 시드) + 저장 호환성 KDoc.
3. **디바운스 starvation 방지** — 피어 cosmetic(위치/쿨다운) 드리프트를 dedup 키에서 제외(저장은 full state) → 내구 변화에만 발화.
4. 복원→변경→재저장 VM 통합 테스트.
5. `strippedForSave` 누락 가드 테스트.

검증: 189 테스트(core 164 + shared 25), 메타데이터 컴파일, APK, 실기 복원. iOS NSUserDefaults actual은 macOS 필요(미검증).
