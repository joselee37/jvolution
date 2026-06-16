# Jvolution — 기능 · 게임루프 · e2e 테스트 맵

> **목적:** 기능별 e2e 테스트 설계의 기준 문서. 각 기능의 현재 상태 + e2e 진입점/관측/주의를 매핑한다.
> **기준 시점:** 2026-06-16 (유전·계보 UI 반영 후). 진척도는 git 히스토리 + 코드 기준(PLAN.md는 1차 로드맵이라 현재는 초과 달성).

---

## 1. 게임 루프 (엔진)

단일 `GameState` + 순수 `reduce(state, action, rng)`. 모든 입력이 한 파이프라인으로 수렴한다:

```
입력(터미널 텍스트 / 명령칩 / 화면 탭)
  → GameViewModel.submitCommand(text) → parse → respond(cmd,state,rng) → {lines, Action?}
  → dispatch(Action) → reduce(pure) → StateFlow<GameState> → Compose 재구성
```

**시간축 4층** (e2e에서 각각 다르게 구동):

| 층 | 주기 | 소유 | 구동 | e2e 제어 |
|----|------|------|------|----------|
| Care tick | ~1.5s | ViewModel 코루틴 | `Action.Tick(dt)` 스탯 드리프트 | `autoTick=false` + `dispatch(Tick(dt))` |
| Peer tick | ~1.0s | ViewModel 코루틴 | `Action.PeerTick(dt)` 위치+AI | `dispatch(PeerTick(1f))` + `FixedRng` |
| Transient 타이머 | 0.5–2.2s | ViewModel | toast/evolving/disciplineFlash/battle phase | `StandardTestDispatcher` + `advanceTimeBy` |
| Frame | ~16ms | Canvas `withFrameNanos` | 도트 phosphor·sweep (**GameState 밖**) | 스크린샷만 |

핵심: reducer는 wall-clock 미접근(타임스탬프는 Action payload) → 도메인 e2e는 `SeededRng`/`FixedRng` + 가상시간으로 **완전 결정론**. view(Sonar/Tree/Radar/Battle/Genome) 전환은 `Action.SetView`, breed 미리보기는 ViewModel presentation state `breedTarget`.

---

## 2. 기능 맵 (성숙도 + e2e 표면)

### A. 케어 루프 + 진화 — ✅ 완성
- feed/play/clean/sleep·wake/train/scold/heal/ping → 스탯 7종([0,1] clamp). tick이 awake/asleep별 드리프트. mood 8단계(ASLEEP→EVOLVING→SCOLDED→DISTRESSED→HUNGRY→UNHAPPY→DROWSY→NOMINAL).
- 진화: evolveProgress(tick +0.005/s, train +0.04) → canEvolve(stage 전진 가능 && ≥1) → `evolve` → evolving(2.2s 타이머) → 다음 Stage.
- **e2e 진입**: 터미널/칩, 생명체 탭=ping. **관측**: 스탯 delta, toast, 로그, `moodLabel(state)`, stage 전이.

### B. 생명체 렌더 (+유전 변조) — ✅ 완성 / 시각
- `DotCreatureCanvas` 종 실루엣 + ping sweep + 게놈 변조(크기/밝기/텍스처/미세톤). **e2e: 스크린샷만**(트리거 pingNonce는 domain 확인 가능).

### C. 피어 / 레이더 / DND — ✅ 완성
- 고정 7유닛, peer tick 드리프트 + 6% AI(challenge/friendly/idle), single-request gate, 근접 경보, bond, DND, LINK.
- **e2e 진입**: `scan`/블립 탭/`bond <name>`/`dnd`/`accept`/`decline`, `PeerTick`. **관측**: pendingRequest, peerEventNonce+에코, peer.bond, dnd.

### D. 전투 — ✅ 완성
- `challenge <name>`→`acceptOdds`(0.30–0.85)→`BattleStart`. RPS 4액션×16칸 매트릭스, NPC AI(성격분포 + veteran read&react), 페이즈(Choose→MyCast→TheirCast→Reveal→Damage→End, ViewModel 타이머), HP 5, crit 5%, training/discipline/stage 배수, 결과 보상.
- **e2e 진입**: `challenge`/`accept`, `BattleCursor`+`BattleCommit`, `flee`. **관측**: phase/HP/result/log, 전투 후 보상.

### E. 유전 / 계보 / 번식 — ✅ 완성 (신규)
- 16좌위 이배체 게놈 → 순수 `express()` → 외형/스탯/행동. `breed`(재조합+돌연변이), 2부모 혈통 DAG, Wright 근친계수. GENOME view, PAIR-BOND ASSAY, genome 시그니처.
- **e2e 진입**: `genome`/`dna`, `breed <name>`/BREED 칩→ASSAY(breedTarget)→`confirmBreed`/`cancelBreed`. **관측**: predictedInbreeding, breedTarget 전이, gen+1·motherId/fatherId·자식 게놈=breed(부모)·조상 아카이브.
- ⚠️ **caveat**: 행동 형질(aggression 등)은 산출되지만 **라이브 peer AI/전투에 미연결**(설계 §15) → 해당 e2e 작성 금지. 외형 변조는 시각만.

### F. 터미널 — ✅ 완성 (전체 명령 패리티)
- `parse`→`respond` 순수, 명령 ~32종, 전투 중 명령 잠금, 입력 에코, 히스토리 ViewModel-local.
- **`submitCommand(text)`가 모든 기능의 단일 통합 게이트** → e2e 하네스의 1순위 드라이버.

### G. 인프라 — ✅ 완성
- **영속화**: SaveBlob v3, transient 스트리핑, 1s 디바운스 저장, v1→v2→v3 마이그레이션(손상→null 폴백).
- **설정**: Tweaks(theme/crtIntensity/scanlines/noise/pulsePeriod/phosphorDecay/crtShader/bezel) + species + sound + hatch(→breed).
- **사운드**: SFX 합성 + expect/actual 플레이어 + 액션→cue(음소거 게이트).
- **CRT/베젤/프레임**: DeviceFrame, MainBezel 3종, CrtLayers(Compose 근사 — AGSL 셰이더 미도입).

---

## 3. 진척도 요약

| 기능 | 성숙도 | e2e 상태 |
|------|--------|----------|
| 케어 + 진화 | ✅ 완성 | 풀사이클 e2e 대상 |
| 생명체 렌더(+변조) | ✅ 완성 | 스크린샷만 |
| 피어/레이더/DND | ✅ 완성 | 흐름 e2e 대상 |
| 전투 | ✅ 완성 | 한 판 e2e 대상 |
| 유전/계보/번식 | ✅ 완성(신규) | ✅ 에뮬 검증 + 도메인 e2e 대상 |
| 터미널 | ✅ 완성 | 통합 게이트 |
| 영속화/설정/사운드 | ✅ 완성 | round-trip e2e 대상 |
| CRT 셰이더(AGSL) | ⏸ 미도입(근사) | — |
| 행동형질→라이브 AI | ⏸ 미연결 | e2e 불가(caveat) |
| iOS | ✅ 빌드(macOS 별도) | Linux 불가 |
| 워치 컴패니언 | ⛔ 미착수 | — |

---

## 4. e2e 테스트 전략

**2계층**:

1. **도메인 e2e (headless, 결정론, `:shared:jvmTest`)** — `GameViewModel(autoTick=false)` 위에서 명령 시퀀스를 `submitCommand`/`dispatch`로 흘리고 GameState+터미널을 단언. `SeededRng`/`FixedRng` + `StandardTestDispatcher`. **이번 작업의 1차 대상.**
2. **UI e2e (instrumented, emulator-5570)** — adb로 실제 Compose 구동 + 스크린샷. 시각/통합 동선. iOS는 macOS 별도.

**도메인 e2e 풀플로우 (기능당 1, `shared/.../viewmodel/e2e/`)**:

| 테스트 | 흐름 | 핵심 단언 |
|--------|------|-----------|
| `CareLifecycleE2eTest` | drift→HUNGRY→feed→NOMINAL; train/tick→canEvolve→evolve→stage++ | mood 전이, 스탯 clamp, stage 전이, toast/로그 |
| `PeerBattleE2eTest` | challenge(강제 수락)→battle start→commit→phase 진행→flee→end→sonar | view 전이, battle 라이프사이클, log, 보상 |
| `BreedingLineageE2eTest` | breed→ASSAY→confirm→gen++; 조상 피어와 재교배→근친 F>0 | breedTarget 전이, 혈통 DAG, predictedInbreeding 누적 |
| `PersistenceE2eTest` | 진행→디바운스 저장→새 VM 복원→이어가기 | durable 보존, transient 리셋 |

**caveat (테스트 작성 시 준수)**:
- 행동 형질은 전투/AI에 미반영 → 해당 e2e 금지.
- Frame-state(도트/sweep)는 GameState 밖 → 스크린샷으로만.
- view/breedTarget은 presentation → ViewModel 레벨 단언.
- iOS e2e는 Linux 불가(macOS 별도).
