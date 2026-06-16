# 계보·유전 시스템 재구현 설계 (Genetics & Lineage Foundation)

> **상태:** 설계 승인 대기 → 구현 예정
> **작성일:** 2026-06-16
> **근거:** [docs/RESEARCH-breeding-genetics.md](../../RESEARCH-breeding-genetics.md) §6 권장 아키텍처
> **범위:** 백엔드/앱 기반(core 도메인 + 직렬화/마이그레이션 + 상태 배선). **사용자向 신규 UI는 제외**(기존 화면은 컴파일 유지용 최소 retarget만).

---

## 1. 목표와 범위

기존 계보 시스템(`LineageEntry` 비석 목록 + 무성 `Action.Reset` 세대교체)을 **버리고**, 게놈→표현형 2단계 매핑 기반의 유전·번식·혈통 시스템으로 **완전히 재구현**한다.

### 결정된 제품 사양 (사용자 확정)
- **번식 모델:** 유성 교배 — 현재 개체 × 선택한 피어 → 자식(이배체 재조합 + 돌연변이). 혈통은 부모 2명 DAG.
- **표현형 도메인:** 게놈이 **스탯 + 외형 파라미터 + 행동 가중치** 3개 도메인 전부 구동 + 혈통 DAG + Wright 근친계수.

### In scope (이번 구현)
1. **유전 엔진**(`:core` 순수 KMP): `Genome`/`AllelePair`/`Loci` 카탈로그, 순수 `express()`→`Phenotype`(3 도메인), `breed()`/`mutate()`/`randomGenome()`/`Genome.default()`, `Kinship`/근친계수.
2. **상태 배선:** `GameState`·`Peer`에 게놈 추가, `Action.Reset`→`Action.Breed`, reducer 아카이브/교배/초기 스탯 시드.
3. **직렬화/마이그레이션:** `SaveBlob.SCHEMA_VERSION` 2→3, `Genome.version`.
4. **전체 단위테스트**(TDD).

### Out of scope (후속)
- 외형(`AppearanceTraits`)·행동(`BehaviorTraits`)은 `express()`가 **순수 데이터로 산출**하되, **Compose 렌더링·라이브 peer AI/전투 수식은 이번에 손대지 않음**. 게놈엔 이미 반영되어 언제든 소비 가능.
- 기존 `TreeScreen`·터미널 readout은 **새 모델에 맞춘 기계적 retarget**만(재설계 아님). 리치 계보/번식 UI는 별도 작업.

---

## 2. 모듈 배치와 순수성

신규 코드는 전부 `:core` 모듈 `today.superb.jvl.core.genetics` 패키지(`commonMain`). `:core`는 순수 도메인이므로 **stdlib + `kotlinx-serialization-core` 애너테이션 + 기존 `Rng`만** 사용한다(`CorePurityTest`가 androidx/compose/ktor/coil/koin import를 금지 — 위반 없음).

```
core/src/commonMain/kotlin/today/superb/jvl/core/genetics/
  Genome.kt        — Genome, AllelePair, Domain, Dominance, Locus, GENOME_VERSION
  Loci.kt          — object Loci (고정 좌위 카탈로그)
  Phenotype.kt     — Phenotype, AppearanceTraits, StatTraits, BehaviorTraits
  Expression.kt    — express(Genome): Phenotype, 좌위별 우성 규칙
  Breeding.kt      — breed(), mutate(), randomGenome(), Genome.default()
  Pedigree.kt      — Ancestor, Lineage, PedigreeNode, GameState.pedigree()
  Kinship.kt       — coefficientOfKinship(), inbreeding(), predictedInbreeding()
```

---

## 3. 게놈 모델 (이배체, Niche식)

좌위 카탈로그는 **코드 상수**(`object Loci`). 저장본에는 **대립유전자 값 + version만** 직렬화 → 저장 크기 최소·스키마 진화 분리.

```kotlin
const val GENOME_VERSION = 1

@Serializable
data class AllelePair(val maternal: Int, val paternal: Int)   // 정수 → 완전 결정론

@Serializable
data class Genome(
    val version: Int = GENOME_VERSION,
    val alleles: List<AllelePair>,   // index == Locus.id, 길이 == Loci.ALL.size
)

enum class Domain { APPEARANCE, STAT, BEHAVIOR }

/** 좌위 발현 규칙. COMPLETE=우성 발현, INCOMPLETE_BLEND=평균, CODOMINANT=평균+이형접합 노출. */
enum class Dominance { COMPLETE, INCOMPLETE_BLEND, CODOMINANT }

/** 좌위 정의(카탈로그 항목, 비직렬화 — 코드 상수). [min, max] 정수 도메인. */
data class Locus(
    val id: Int,
    val key: String,
    val domain: Domain,
    val dominance: Dominance,
    val min: Int,
    val max: Int,
)
```

### 좌위 카탈로그 (16개) — `object Loci`

| id | key | domain | dominance | range | 비고 |
|----|-----|--------|-----------|-------|------|
| 0 | bodyLength | APPEARANCE | INCOMPLETE_BLEND | 1..9 | 전체 크기/세그먼트 길이 |
| 1 | branchAngle | APPEARANCE | INCOMPLETE_BLEND | 0..12 | 분기각(×15° 매핑) |
| 2 | symmetry | APPEARANCE | COMPLETE | 1..8 | 분기 수/대칭 |
| 3 | recursionDepth | APPEARANCE | INCOMPLETE_BLEND | 1..6 | 바이오모프 g9 |
| 4 | hue | APPEARANCE | CODOMINANT | 0..7 | 팔레트 인덱스(이형접합 → 2색) |
| 5 | pattern | APPEARANCE | COMPLETE | 0..5 | 무늬 |
| 6 | vigorA | STAT | INCOMPLETE_BLEND | 0..10 | vitality 기여 |
| 7 | vigorB | STAT | INCOMPLETE_BLEND | 0..10 | vitality 기여 |
| 8 | metabolismA | STAT | INCOMPLETE_BLEND | 0..10 | metabolism 기여 |
| 9 | metabolismB | STAT | INCOMPLETE_BLEND | 0..10 | metabolism 기여 |
| 10 | resilienceA | STAT | INCOMPLETE_BLEND | 0..10 | resilience 기여 |
| 11 | resilienceB | STAT | INCOMPLETE_BLEND | 0..10 | resilience 기여 |
| 12 | aggression | BEHAVIOR | COMPLETE | 0..10 | challenge 성향 |
| 13 | sociability | BEHAVIOR | COMPLETE | 0..10 | friendly 성향 |
| 14 | boldness | BEHAVIOR | INCOMPLETE_BLEND | 0..10 | |
| 15 | tempo | BEHAVIOR | INCOMPLETE_BLEND | 0..10 | 활동/쿨다운 |

> 좌위 추가/제거는 `GENOME_VERSION`을 올리고 게놈 마이그레이션(짧은 게놈은 기본 대립유전자로 패딩)으로 처리. 카탈로그가 코드 상수이므로 `Loci.ALL.size`가 단일 소스.

---

## 4. 표현형 (순수 결정론 함수)

`express()`는 **RNG 없는 순수 함수** → "같은 게놈 → 같은 개체"가 플랫폼 무관 자동 보장. 따라서 리서치 §6b가 KMP 리스크로 지목한 **휴대용 PRNG는 불필요**(§12 참조).

```kotlin
data class AppearanceTraits(
    val bodyLength: Int, val branchAngle: Int, val symmetry: Int,
    val recursionDepth: Int, val hue: Int, val hueAlt: Int?, val pattern: Int,
)
data class StatTraits(val vitality: Float, val metabolism: Float, val resilience: Float)   // [0,1]
data class BehaviorTraits(val aggression: Float, val sociability: Float, val boldness: Float, val tempo: Float) // [0,1]

data class Phenotype(
    val appearance: AppearanceTraits,
    val stats: StatTraits,
    val behavior: BehaviorTraits,
)

fun express(genome: Genome): Phenotype
```

### 좌위별 발현 규칙 (정수 산술)
`expressLocus(locus, pair) -> LocusValue(value: Int, heterozygous: Boolean)`:
- **COMPLETE** → `value = max(maternal, paternal)`(높은 값 우성), `het = maternal != paternal`
- **INCOMPLETE_BLEND** → `value = (maternal + paternal) / 2`(정수 내림), `het = maternal != paternal`
- **CODOMINANT** → `value = (maternal + paternal) / 2`, `het = maternal != paternal`. `hue`는 het일 때 `hueAlt = max(maternal,paternal)`(다른 대립유전자) 노출, 동형접합이면 `hueAlt = null`.

### 다유전자 스탯(polygenic) 정규화 → [0,1]
- `vitality   = (v(vigorA) + v(vigorB)) / 20f`
- `metabolism = (v(metabolismA) + v(metabolismB)) / 20f`
- `resilience = (v(resilienceA) + v(resilienceB)) / 20f`

(각 좌위 0..10, 2좌위 합 → 종형 분포. 중간값 5+5=10 → 0.5)

### 행동 → [0,1]
`aggression/sociability/boldness/tempo = v(해당 좌위) / 10f`.

### 초기 스탯 시드 (스탯 도메인 라이브 연결)
새 개체 부화/교배 시 `GameState`의 시작 스탯을 `StatTraits`에서 산출. **기본 게놈(전 좌위 중간값) → 레거시 초기값을 정확히 재현**(기존 케어/리듀서 테스트 보존):

```
energy0    = (0.7f + (vitality   - 0.5f) * 0.4f).coerceIn(0f,1f)   // 중간 0.5 → 0.7 ✓ (범위 0.5–0.9)
happiness0 = (0.6f + (resilience - 0.5f) * 0.3f).coerceIn(0f,1f)   // 중간 0.5 → 0.6 ✓
hunger0    = (0.45f + (metabolism - 0.5f) * 0.2f).coerceIn(0f,1f)  // 중간 0.5 → 0.45 ✓
dirty0=0.3f, bond0=0.4f, training0=0.1f, discipline0=0.2f          // 레거시 고정
```

---

## 5. 번식 + 돌연변이

`breed`/`mutate`/`randomGenome`은 `Rng`를 쓴다(번식/시드 시 1회, 결과 게놈은 저장됨 → 휴대용 PRNG 불요, 테스트는 `SeededRng`로 재현).

```kotlin
/** 좌위별 각 부모에서 대립유전자 1개씩(이배체 재조합) + 돌연변이. */
fun breed(maternal: Genome, paternal: Genome, rng: Rng): Genome

/** 저확률 ±1 섭동(좌위 범위 clamp). 리서치 §4 최소 모델. */
private fun mutateAllele(value: Int, locus: Locus, rng: Rng): Int

/** Gen1/피어 founder용 — 좌위 범위 내 균등 무작위. */
fun randomGenome(rng: Rng): Genome

/** 전 좌위 중간값 — 결정론적 기본 게놈(레거시 저장본 마이그레이션·테스트·시드 재현 기준). */
fun Genome.Companion.default(): Genome
```

- 재조합: 좌위마다 모계 게놈의 두 대립유전자 중 하나(50%) → 자식 maternal, 부계 게놈에서 하나 → 자식 paternal.
- 돌연변이: 대립유전자별 `MUTATION_RATE`(예: 0.08) 확률로 ±1, `[min,max]` clamp.
- **종(Species) 상속:** 자식 종 = 50%로 부모 한쪽(reducer에서 처리 — 종은 enum이라 게놈 밖). 렌더 호환 유지.

---

## 6. 혈통 + 근친계수 (리서치 "출처 없음 — 직접 설계")

```kotlin
/** 아카이브된 한 조상(혈통 DAG 노드). 구 LineageEntry의 상위집합 + 게놈/부모/식별자. */
@Serializable
data class Ancestor(
    val id: String, val gen: Int, val name: String,
    val species: Species, val stage: Stage,
    val genome: Genome, val motherId: String?, val fatherId: String?,
    val cycles: Int,
    // 아카이브 시점 스탯 스냅샷(0~100 정수) — 디스플레이용(구 LineageEntry 필드와 동일).
    val happiness: Int, val energy: Int, val bond: Int, val discipline: Int, val training: Int,
    val hatchedAt: Long, val archivedAt: Long,
)

/** 혈통 모음. 구 List<LineageEntry>를 대체. founder(peer 공동부모, gen=0)와 본가 계보(gen≥1) 공존. */
@Serializable
data class Lineage(val ancestors: List<Ancestor>)
```

### 혈통 노드 조회
```kotlin
data class PedigreeNode(val id: String, val gen: Int, val motherId: String?, val fatherId: String?)

/** ancestors + 현재 개체 + 피어(founder, gen=0)를 합쳐 id→노드 맵. */
fun GameState.pedigree(): Map<String, PedigreeNode>
```

### Wright 근친계수 (표준 재귀 kinship)
```kotlin
object Kinship {
    /** 친족계수 f(a,b). 메모이즈, DAG라 종료 보장. */
    fun coefficientOfKinship(a: String, b: String, nodes: Map<String, PedigreeNode>): Double
    /** 개체 x의 근친계수 F = f(x의 부모). 부모 미상이면 0. */
    fun inbreeding(motherId: String?, fatherId: String?, nodes: Map<String, PedigreeNode>): Double
}

/** 현재 개체 × 피어 교배 시 자식의 예측 F = f(현재개체, 피어). 교배 전 경고/표시용. */
fun predictedInbreeding(state: GameState, peerId: String): Double
```

재귀 정의(자기 자신 / 더 최근(높은 gen) 노드로 하강):
```
f(a,a) = 0.5 * (1 + F_self(a))                 // F_self(a)=f(a의 부모) 또는 0
f(a,b), a≠b: 두 노드 중 gen이 더 높은 쪽 x를 하강(동일 gen이면 부모 보유한 쪽; 둘 다 보유면 a).
             x가 부모(p,q) 보유 → 0.5*(f(p, y) + f(q, y))   // y=다른 쪽
             x가 founder(부모 없음) → 0                       // 서로 다른 founder는 무관
노드 부재 id → founder(gen=-1, 부모 없음)로 간주
메모이즈 키는 정렬된 (min(a,b), max(a,b)) 쌍 → DAG라 종료 보장.
```
검증 케이스: 무관 founder f=0 · 부모-자식 F=0.25 · 전동기(full-sib) F=0.25 · 반동기 F=0.125.

---

## 7. 상태 통합 (`GameState` / `Peer`)

```kotlin
// GameState 추가 필드 (기본값으로 기존 호출부/테스트 보존):
val genome: Genome = Genome.default(),
val creatureId: String = "founder",
val motherId: String? = null,
val fatherId: String? = null,
// 타입 교체:
val lineage: Lineage = Lineage(emptyList()),   // 구: List<LineageEntry>

// Peer 추가:
val genome: Genome = Genome.default(),
```

`GameState.initial`은 게놈을 받아 **express→시드된 시작 스탯**을 계산:
```kotlin
fun initial(
    name: String, now: Long,
    peers: List<Peer> = emptyList(),
    genome: Genome = Genome.default(),
    creatureId: String = "founder",
): GameState   // energy/happiness/hunger를 §4 시드 공식으로 산출, 나머지 레거시 고정
```

`PeerRoster.makePeers(rng)`는 각 피어에 `randomGenome(rng)` 할당.
`GameViewModel`은 새 게임 시 `randomGenome(rng)` + 유니크 `creatureId` 생성해 `initial`에 주입.

---

## 8. 액션 + 리듀서

`Action.Reset` **제거**, `Action.Breed` **신설**:
```kotlin
/**
 * 유성 교배 — 현재 개체 × [peerId] 피어 → 자식(새 알, gen+1). 현재 개체는 Ancestor로 아카이브,
 * 피어 공동부모도 (없으면) founder Ancestor로 기록. childName/childId/now는 ViewModel이 스탬프.
 */
data class Breed(val peerId: String, val childName: String, val childId: String, val now: Long) : Action
```

리듀서 `is Action.Breed` 처리:
1. `peer = peers.find { it.id == peerId }` — 없으면 `state` 반환(no-op).
2. 현재 개체 → `Ancestor`(게놈·부모ID·스탯 스냅샷·`archivedAt=now`)로 `lineage.ancestors`에 추가.
3. 피어가 `ancestors`에 없으면 founder `Ancestor`(gen=0, 부모 null, 피어 게놈/종/단계) 추가.
4. `child = breed(state.genome, peer.genome, rng)` + 종 = 50%로 한쪽 부모.
5. 새 알 = `GameState.initial(childName, now, peers, child, childId)`.copy(gen+1, species=childSpecies, lineage=갱신, motherId=state.creatureId, fatherId=peerId, view/pendingRequest/peerEvent* 보존).

> 피어/유대/전적은 생명체와 독립이라 보존(구 Reset 규약 유지).

---

## 9. 터미널 + ViewModel 배선

- `TerminalCommand.Reset` → `TerminalCommand.Breed(name: String?)`. 파서: `breed <name>`(구 `reset` 자리). 인자 없음/미상 피어 → usage/error. 성공 → `Action.Breed(peerId, "", "", 0)` 시그널(ViewModel 스탬프).
- 응답에 **예측 근친계수 F**를 텍스트 한 줄로 노출(순수 core, 시스템 시연용): `"▸ predicted inbreeding F=0.12"`.
- `GameViewModel`:
  - `hatchNewEgg()` → 무작위 가용 피어를 mate로 `Action.Breed` 디스패치(기존 설정 버튼 호환 — UI 미변경). childId = `"g${gen+1}_${name}_${now}"`.
  - `submitCommand`의 `Action.Reset` 스탬프 분기 → `Action.Breed` 스탬프(childName=NAMES, childId, now).
- `SfxCue`: `Action.Reset -> Sfx.Confirm` → `Action.Breed -> Sfx.Confirm`.

---

## 10. 직렬화 + 마이그레이션

- 신규 모델(`Genome`/`AllelePair`/`Ancestor`/`Lineage`) 전부 `@Serializable`. `Phenotype`/`*Traits`는 **비직렬화**(파생 — 항상 `express`로 재계산).
- `SaveBlob.SCHEMA_VERSION` **2 → 3**. `GameState`의 신규 필드는 기본값이 있어 forward-compat이지만, 의미 있는 마이그레이션을 위해 분기 추가.
- **v2 → v3 마이그레이션**(`SaveCodec.decode`): v2 블롭은 게놈/식별자 없고 `lineage`가 구 `List<LineageEntry>` 형태(JSON). 처리:
  - 현재 개체/피어에 `Genome.default()` 할당, `creatureId = "g${gen}_${name}"`, 부모 null(founder).
  - 구 `lineage` 각 엔트리 → `Ancestor`(default 게놈, 부모 null, `species=Species.Ghost` 폴백, 스탯/시각 1:1 매핑, id=`"g${gen}_${name}"`).
  - kotlinx 역직렬화는 타입 불일치(구 `List<LineageEntry>` → 신 `Lineage`)로 실패하므로, **v2는 raw JSON에서 필드를 수동 파싱**해 재구성(기존 v1→v2 분기가 `parseToJsonElement`로 하는 방식과 동일 패턴).
- `Genome.version`: 역직렬화된 게놈 길이 < `Loci.ALL.size`면 기본 대립유전자로 패딩(`express`/`breed` 진입 시 정규화). 이번엔 v1뿐이라 패딩 경로만 마련.
- `strippedForSave()`: 게놈/계보는 durable → 변경 없음.

---

## 11. 컴파일 유지용 어댑터 (UI 재설계 아님)

- `core/terminal/LineageReadout.kt`: `activeLineageEntry(state)` → 현재 개체의 `Ancestor` 뷰(`archivedAt=0`) 반환으로 변경. `renderGeneration(ancestor, active)`는 동일 디스플레이 필드 사용(시그니처 타입만 `Ancestor`).
- `core/terminal/TerminalResponder.kt`: `treeResponse`가 `state.lineage.ancestors.filter { it.gen >= 1 }`(본가 계보) 기준으로 카운트/조회.
- `shared/ui/tree/TreeScreen.kt`: `state.lineage` → `state.lineage.ancestors.filter { it.gen >= 1 }`로 retarget. 필드 접근(`e.happiness` 등)은 `Ancestor`가 동일 필드를 가져 그대로. **레이아웃/시각 변경 없음.**

---

## 12. 결정론 노트

- **표현형:** `express()`는 순수 → 같은 게놈은 어느 플랫폼에서도 같은 표현형. RNG 불개입.
- **번식:** `breed`/`mutate`/`randomGenome`만 `Rng` 사용. 결과 게놈은 즉시 저장되므로 재현 불필요. 테스트는 `SeededRng`(동일 프로세스 한정으로 충분).
- **결론:** 리서치 §6b·§8.1의 휴대용 PRNG는 **이번 범위에서 불필요**. 향후 크로스기기 "같은 시드로 같은 자식" 동기화가 필요해지면 그때 commonMain 휴대용 PRNG 도입(후속).

---

## 13. 테스트 전략 (TDD)

`:core:jvmTest`(host 빠른 실행) + `:core:allTests`(KMP) + `:shared:testDebugUnitTest`.

- **Expression**: 결정론(같은 게놈→같은 Phenotype) · 우성 규칙별(COMPLETE/BLEND/CODOMINANT) · CODOMINANT hue het→hueAlt · polygenic 합산/정규화 · 기본 게놈→레거시 시드 스탯(0.7/0.6/0.45).
- **Breeding**: 자식 대립유전자가 부모에서 옴 · 돌연변이 범위 clamp · `SeededRng` 재현 · `randomGenome` 범위 · `default` 중간값.
- **Kinship**: 무관 founder=0 · 부모-자식 0.25 · full-sib 0.25 · half-sib 0.125 · 메모이즈 종료.
- **Reducer(Breed)**: 현재 개체 아카이브 · 피어 founder 기록(중복 방지) · gen+1·부모ID 세팅 · 초기 스탯 시드 · 피어/유대 보존 · 입력 불변 · 미상 피어 no-op.
- **Serialization**: 신 스키마 라운드트립 · v2→v3 마이그레이션(구 epitaph→Ancestor) · 짧은 게놈 패딩.
- **기존 테스트 재작성**: `LineageReducerTest`/`LineageReadoutTest`/`TerminalLineageTest`/`ReducerTest`(Reset 케이스)/`SaveCodecTest`(lineage 픽스처).

---

## 14. 블래스트 반경 (변경 파일)

**신규(core/genetics):** Genome.kt, Loci.kt, Phenotype.kt, Expression.kt, Breeding.kt, Pedigree.kt, Kinship.kt (+ 각 테스트)
**수정(core):** GameState.kt, Peer.kt, PeerRoster.kt, Action.kt, Reducer.kt, terminal/{LineageReadout,TerminalResponder,TerminalCommand,TerminalParser}.kt
**삭제(core):** LineageEntry.kt (→ Ancestor로 대체)
**수정(shared):** persistence/SaveCodec.kt, viewmodel/{GameViewModel,SfxCue}.kt, ui/tree/TreeScreen.kt
**테스트 재작성:** core/{LineageReducerTest,LineageReadoutTest,terminal/TerminalLineageTest,ReducerTest}, shared/persistence/SaveCodecTest

---

## 15. 미해결/후속

- 외형 2D 벡터 렌더(`AppearanceTraits` 소비) — 신규 UI.
- 행동 가중치를 라이브 peer AI/전투 수식에 연결(`BehaviorTraits` → `Personality` 확률·`resolveBattleTurn` 가중).
- 돌연변이율·우성 분포 튜닝(표현형 편향, 리서치 §4 함정).
- 다양성 지표(평균 친족계수·founder 수) 메타게임.
- 크로스기기 결정론용 휴대용 PRNG(필요해질 때).
