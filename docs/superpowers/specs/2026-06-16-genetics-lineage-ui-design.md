# 유전·계보 UI 재설계 설계 (Genetics & Lineage UI)

> **상태:** 설계 승인 → 구현 예정
> **작성일:** 2026-06-16
> **선행:** [백엔드 spec](2026-06-16-genetics-lineage-design.md) (게놈/express/breed/pedigree/Kinship 완료, master 반영)
> **원칙:** 기존 디자인 언어(phosphor CRT · VT323/JetBrains Mono · ◢◤·▸·tree chars · MainBezel · MonoText · view-switch)를 **100% 유지**. 신규 화면은 이 언어로 그린다.

---

## 1. 목표

게놈/계보 백엔드를 사용자에게 노출하는 UI를 구축한다. 기기를 "care terminal" → **"genetic observation post"**로 리프레이밍. 4개 서피스:

1. **GENOME view** (신규) — 현재 개체의 게놈/표현형 readout (Helix 레이아웃).
2. **게놈 변조 생명체** — 소나의 도트 생명체가 게놈에 따라 개체별로 달라 보이게(종 실루엣 유지).
3. **혈통(pedigree) 재설계** — `TreeScreen`을 2부모 DAG + 게놈 시그니처 + 근친계수로.
4. **Breeding 플로우** — "PAIR-BOND ASSAY" 오버레이(상대 선택 → 예측 → 확정).

### 범위 경계 (YAGNI)
- 소나/레이더/전투/터미널/베젤/설정의 **기존 구조·미감 불변**(명령칩·터미널 명령만 추가).
- 게놈 **읽기 전용**. 외형은 **변조만**(풀 바이오모프 아님). offspring 예측은 부모 형질 비교 + F로 한정.
- 백엔드 reducer/genetics는 **변경 없음** — UI는 `express()`/`predictedInbreeding()`/`state.lineage` 소비만. (presentation state는 ViewModel/App-local에 추가.)

---

## 2. 데이터 소스 (전부 기존 백엔드)

- `express(state.genome): Phenotype` — appearance/stats/behavior 형질.
- `state.genome.alleles` + `Loci.ALL` — 16좌위×2대립유전자 매트릭스.
- `predictedInbreeding(state, peerId): Double` — 교배 전 근친계수.
- `state.lineage.ancestors` (각 `id/gen/name/genome/motherId/fatherId/species/stage/스탯`), `state.motherId/fatherId/creatureId`.
- 피어 게놈: `peer.genome` → `express(peer.genome)`.

---

## 3. GENOME view (신규 `View.Genome`, Helix 레이아웃)

`core/View.kt`에 `Genome` 추가(by-name 직렬화 + `strippedForSave`가 view를 Sonar로 리셋 → 저장 안전). `shared/ui/genome/GenomeScreen.kt` 신규 Compose 렌더:

```
        ◢◤ GENOME // G03_KAIJU ◢◤        F 6%
  locus    AP·······    ST······    BH····
  mat   ▏  5 8 4 3 6 1   7 5 4 8 5 6   6 5 4 3
  pat   ▏  5 2 4 3 1 1   3 5 9 8 5 6   2 5 4 3
  ─────────────────────────────────────────
  VIT .78   MET .41   RES .60
  AGG .30   SOC .62   BLD .55   TMP .40
  ─────────────────────────────────────────
  line  G01▸G02▸G03      ✚ G02_MORSE × LUMEN-3
```

- 헤더: `◢◤ GENOME // G{gen}_{name} ◢◤` (VT323 display), 우측 `F {self_inbreeding%}` = `inbreeding(state.motherId, state.fatherId, state.pedigree())`.
- 좌위 매트릭스: `Loci.ALL`를 domain별 그룹(AP=6/ST=6/BH=4), maternal/paternal 행. **이형접합(mat≠pat)** 좌위는 `palette.phos`(강조), 동형접합은 `palette.phosMid`. JetBrains Mono 정렬.
- 형질값: `express()`에서 VIT/MET/RES(stats), AGG/SOC/BLD/TMP(behavior) — `.NN` 소수 2자리(monochrome, 바 없음 — Helix는 데이터 중심).
- 푸터: `line G01▸G02▸…▸G{gen}`(본가 spine, `ancestors.filter{gen>=1}` + 현재), `✚ {mother} × {father}`(부모 이름).
- 진입: 명령칩 `GENOME`, 터미널 `genome`/`dna`, 베젤 라벨 `GENOME-ASSAY · G{gen}`. 읽기 전용.

---

## 4. 게놈 변조 생명체 (`DotCreatureCanvas`)

종 실루엣·애니메이션은 유지하고 `express(genome).appearance/stats`가 **변조**만. monochrome phosphor 미감 보존.

`DotCreatureCanvas`에 파라미터 추가: `appearance: AppearanceTraits`, `vitality: Float`. App이 `remember(state.genome){ express(state.genome) }`로 한 번 계산해 주입(프레임마다 재계산 금지).

변조 매핑(전부 보수적 — 실루엣 유지):
- `bodyLength`(1..9) → **스케일** `scale = 0.85 + bodyLength/9 * 0.30` (0.85–1.15). 샘플링 시 `densityFor(species, u/scale, v/scale, …)`.
- `vitality`(0..1) → **기본 밝기** `target *= 0.8 + vitality*0.3` (0.8–1.1).
- `pattern`(0..5) → **도트 스티플 텍스처**: 기존 jitter 임계에 `pattern/5`를 섞어 가장자리·표면 거칠기 변조(개체 "무늬").
- `symmetry`(1..8) → jitter 분포 시드 보정(개체별 텍스처 차이).
- `hue` 유전자(0..7) → **팔레트 안 미세 톤**: dot 색을 `lerp(phosDim, phos, b)`에서 `b`에 `(hue/7-0.5)*0.12` 바이어스(phosDim↔phos 치우침). **풀컬러 아님** — CRT 단색 유지.

결과: 같은 종이라도 자식이 부모와 시각적으로 구분. 기존 종 density 함수(`SpeciesShapes`)는 그대로, 변조는 canvas 레벨에서.

---

## 5. 혈통 재설계 (`TreeScreen` → 2부모 pedigree)

기존 Linux-tree 미감 유지 + 2부모 조인 + 게놈 시그니처 + 근친계수. `state.lineage.ancestors.filter{gen>=1}`(본가 spine) + 현재 개체.

```
  ROOT GENESIS/                         NODES 4
  ANCESTRY
  $ tree GENESIS/
  GENESIS/
  ├── G01_ALPHA/        ● founder
  │   ├ genome  ▒█▓░▒█░▓     F 0%
  │
  ├── G02_MORSE/        ✟ retired
  │   ├ ✚ G01_ALPHA × HRRK
  │   ├ genome  ▓█▒░█▓░▒     F 0%
  │
  └── G03_KAIJU/        ◀ ACTIVE
      ├ ✚ G02_MORSE × LUMEN-3
      ├ genome  ▒█░▓▒█▓░     F 6%
      └ traits  VIT.78 RES.60
```

- 각 spine 노드: 헤더(`G{gen}_{name}/` + 상태태그 `● founder`/`✟ retired`/`◀ ACTIVE`), `✚ {mother} × {father}`(부모 조인 — `motherId/fatherId`를 이름으로 해석; founder는 부모줄 생략), `genome {signature}`, `F {inbreeding%}`, 현역/은퇴 스탯 한 줄.
- **게놈 시그니처**: `genomeSignature(genome): String` — 8개 좌위 값을 `▁▂▃▄▅▆▇█`(8단계 블록)로 매핑한 8자 문자열. 시각적 게놈 "지문". (presentation helper, `shared/ui/genome/`.)
- 노드 헤더 탭 → 터미널 `tree <gen>` 상세(기존 + 게놈/부모/형질 추가, `core/terminal/LineageReadout.renderGeneration`에 부모/F/시그니처 줄 추가).
- 푸터/카운트는 기존 유지(`NODES`, 총 cycles).

---

## 6. Breeding 플로우 ("PAIR-BOND ASSAY" 오버레이)

`SettingsPanel`/`PeerAlertOverlay`와 같은 오버레이(`shared/ui/breed/BreedAssayOverlay.kt`). App-local이 아닌 **ViewModel presentation state**로 구동(터미널·칩·설정 진입을 단일 경로로).

```
  ┌ PAIR-BOND ASSAY ───────────────────┐
  │   G03_KAIJU   ×   LUMEN-3          │
  │  ─────────────────────────────    │
  │   predicted inbreeding             │
  │    F ███░░░░░░░  6%    ✓ SAFE      │
  │  ─────────────────────────────    │
  │   parent traits   self │ mate      │
  │    VIT  .78 │ .52   MET .41 │ .66  │
  │    RES  .60 │ .49                  │
  │  ─────────────────────────────    │
  │    [ CONFIRM BREED ]   [ CANCEL ]  │
  └────────────────────────────────────┘
```

- F 게이지 + 안전 라벨: `<0.125 → ✓ SAFE` / `0.125–0.25 → ⚠ CLOSE` / `≥0.25 → ✕ INBRED`(half-sib/full-sib 임계). 게이지는 F를 [0, 0.25]로 정규화한 바.
- 부모 형질 비교: `express(state.genome)` vs `express(peer.genome)` — VIT/MET/RES 나란히(자식 예측 감각). behavior는 생략(공간).
- 진입 경로(단일화):
  - 레이더에서 피어 선택 → `BREED` 칩.
  - 터미널 `breed <name>`.
  - 설정 "Hatch new egg" → 무작위 가용 피어로 ASSAY.
- 확정/취소: CONFIRM → `Action.Breed`(childName/childId/now ViewModel 스탬프) dispatch + 닫기. CANCEL → 닫기.

### ViewModel presentation state
```kotlin
private val _breedTarget = MutableStateFlow<String?>(null)   // peerId
val breedTarget: StateFlow<String?>
fun requestBreed(peerId: String) { if (peers has it) _breedTarget.value = peerId }
fun confirmBreed() { _breedTarget.value?.let { dispatch(stampBreed(Action.Breed(it, "", "", 0L))); _breedTarget.value = null } }
fun cancelBreed() { _breedTarget.value = null }
```
- `submitCommand`: 응답 action이 `Action.Breed`면 **dispatch 대신** `requestBreed(action.peerId)`(터미널 `breed <name>`이 ASSAY를 연다). 응답 lines(예측 F readout)는 그대로 에코.
- `hatchNewEgg()`: 무작위 가용 피어로 `requestBreed`(즉시 교배 → ASSAY 경유로 변경).
- App: `breedTarget`을 관찰해 non-null이면 `BreedAssayOverlay` 표시(`settingsOpen` 패턴).

---

## 7. 네비게이션/배선 요약

- **`core/View.kt`**: `Genome` 추가.
- **`core/terminal/TerminalCommand.kt`**: `data object Genome`.
- **`core/terminal/TerminalParser.kt`**: `"genome","dna" -> TerminalCommand.Genome`.
- **`core/terminal/TerminalResponder.kt`**: `Genome -> SetView(View.Genome)` + 짧은 readout. `breed`는 기존(F readout + Action.Breed 시그널) 유지 — re-route는 ViewModel.
- **`core/terminal/LineageReadout.kt`**: `renderGeneration`에 부모/F/시그니처 줄 추가(터미널 `tree <gen>` 상세 강화).
- **`shared/App.kt`**: `View.Genome -> GenomeScreen`, `breedTarget` 관찰 → `BreedAssayOverlay`, 베젤 라벨 `GENOME-ASSAY`, 명령칩 GENOME/BREED 배선.
- **`shared/ui/chips/CommandChips.kt`**: 컨텍스트 칩 — 소나→`GENOME`, 레이더+피어선택→`BREED`, genome/tree→`SONAR`.
- **`shared/viewmodel/GameViewModel.kt`**: breedTarget state + requestBreed/confirmBreed/cancelBreed; submitCommand breed re-route; hatchNewEgg → requestBreed.
- **`shared/viewmodel/SfxCue.kt`**: (선택) genome 진입/confirm 큐 — 기존 `Action.Breed -> Confirm` 유지.

### 신규/수정 파일
**신규:** `shared/ui/genome/GenomeScreen.kt`, `shared/ui/genome/GenomeSignature.kt`(시그니처+형질 포맷 helper), `shared/ui/breed/BreedAssayOverlay.kt`
**수정(core):** View.kt, terminal/{TerminalCommand,TerminalParser,TerminalResponder,LineageReadout}.kt
**수정(shared):** App.kt, viewmodel/GameViewModel.kt, ui/tree/TreeScreen.kt, ui/sonar/DotCreatureCanvas.kt, ui/chips/CommandChips.kt(+CommandChipStrip if needed)

---

## 8. 테스트 전략

UI 위주라 단위테스트는 **순수 로직**에 집중(Compose 렌더는 빌드/스냅샷으로):
- `core`: View.Genome 직렬화 round-trip; 파서 `genome`/`dna` → Genome; 응답 `genome` → SetView(Genome); `tree <gen>` 상세에 부모/F 포함.
- `shared`(jvmTest): ViewModel `requestBreed`/`confirmBreed`/`cancelBreed` 상태 전이 + breed re-route(터미널 `breed`가 즉시 dispatch 안 하고 breedTarget 설정); `genomeSignature` 결정성·길이; 근친 안전 라벨 임계(0.125/0.25) 분류 helper.
- 빌드 검증: `:core:jvmTest` + `:shared:jvmTest` green + `:shared:compileKotlinJvm`(Compose 컴파일). iOS는 macOS 별도(순수 commonMain Compose라 JVM 컴파일로 충분 검증).

---

## 9. 미해결/후속

- behavior 형질의 라이브 peer AI/전투 연결(이번엔 readout만).
- offspring 형질 범위 시뮬(현재 부모 비교 + F).
- 게놈 시그니처를 소나 헤더에도 미니 노출(선택).
- 다양성 지표(평균 친족계수) 대시보드.
