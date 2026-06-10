# 풀 터치 레이어 + 실제 SFX 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 화면에 보이는 모든 요소가 실제로 조작 가능하고 모든 토글/표시가 실제 기능에 연결된 앱 — 가짜 UI 제로.

**Architecture:** 모든 터치는 기존 터미널 파이프라인(`submitCommand`)의 매크로로 흐른다(예외: 생명체 탭 ping만 무음 dispatch). SFX는 commonMain 순수 합성(`SfxSynth`) + `expect/actual SfxPlayer`, 트리거는 `dispatch()`의 before/after diff를 순수 함수 `sfxCueFor`가 판정. 칩 스트립 가시성도 순수 함수 `chipsFor`. reducer는 끝까지 순수.

**Tech Stack:** Kotlin Multiplatform (`:core` 순수 도메인 / `:shared` Compose MP), kotlinx.serialization, Koin, Android `AudioTrack` / iOS `AVAudioEngine` / JVM `javax.sound.sampled`.

**Spec:** `docs/superpowers/specs/2026-06-10-full-touch-layer-design.md`

**검증 환경 주의:** Linux 호스트 — iOS 타깃 컴파일 불가. iosMain actual은 작성하되 검증은 `:shared:compileCommonMainKotlinMetadata` + Android 빌드로 한다. macOS/CI에서 `:shared:compileKotlinIosArm64`로 후속 확인 필요(특히 Task 8의 AVFAudio 바인딩 시그니처).

**커밋 규칙:** `<type>: <description>` (attribution 없음 — 사용자 전역 설정). 각 Task 끝에 커밋.

---

## 파일 구조 (생성/수정 전체 맵)

```
core/src/commonMain/kotlin/today/superb/jvl/core/
├── Action.kt                      [수정] ClearDisciplineFlash, SetSpecies 추가
├── Reducer.kt                     [수정] Discipline flash on, 신규 액션 2종, Reset 종 보존
├── GameState.kt                   [수정] disciplineFlash KDoc 갱신
├── Mood.kt                        [수정] SCOLDED 주석 갱신
└── terminal/
    ├── TerminalCommand.kt         [수정] Tree → data class Tree(arg)
    ├── TerminalParser.kt          [수정] tree 인자 파싱
    ├── TerminalResponder.kt       [수정] tree <gen> 분기
    ├── TerminalCopy.kt            [수정] HELP_LINES 데모 패리티
    └── LineageReadout.kt          [신규] renderGeneration + activeLineageEntry
core/src/commonTest/kotlin/today/superb/jvl/core/
├── ReducerTest.kt                 [수정] flash/species/reset 테스트
├── MoodTest.kt                    [수정] SCOLDED 도달 테스트
└── LineageReadoutTest.kt          [신규] tree <gen> 파서/응답 테스트

shared/src/commonMain/kotlin/today/superb/jvl/
├── App.kt                         [수정] 칩/탭/오버레이/LINK 배선, SettingsPanel species
├── sound/
│   ├── Sfx.kt                     [신규] 톤 카탈로그
│   ├── SfxSynth.kt                [신규] PCM 합성 (순수)
│   └── SfxPlayer.kt               [신규] expect class : SfxSink
├── viewmodel/
│   ├── SfxCue.kt                  [신규] sfxCueFor 순수 매핑
│   └── GameViewModel.kt           [수정] flash 타이머, sfx 재생, SfxSink 주입
├── ui/chips/
│   ├── CommandChips.kt            [신규] CommandChip + chipsFor (순수)
│   └── CommandChipStrip.kt        [신규] 칩 스트립 Composable
├── ui/settings/Tweaks.kt          [수정] species 제거, bezel 추가
├── ui/settings/SettingsPanel.kt   [수정] species→상태, BEZEL 섹션
├── ui/bezel/MainBezel.kt          [수정] 하우징 3종
├── ui/frame/DeviceFrame.kt        [수정] terminal 높이 312dp
├── ui/sonar/SonarScreen.kt        [수정] 탭/롱프레스, state.species
├── ui/radar/RadarScreen.kt        [수정] 블립 탭 + 선택 링
├── ui/radar/PeerAlertOverlay.kt   [수정] ACCEPT/DECLINE 버튼
├── ui/tree/TreeScreen.kt          [수정] 노드 탭, activeLineageEntry 재사용
├── ui/battle/BattleScreen.kt      [수정] state.species, HP 플래시
├── persistence/SaveCodec.kt       [수정] SCHEMA_VERSION 2 + v1 마이그레이션
└── di/Koin.kt                     [수정] SfxPlayer 바인딩
shared/src/androidMain/.../sound/SfxPlayer.android.kt   [신규]
shared/src/iosMain/.../sound/SfxPlayer.ios.kt           [신규]
shared/src/jvmMain/.../sound/SfxPlayer.jvm.kt           [신규]
shared/src/commonTest/kotlin/today/superb/jvl/
├── sound/SfxSynthTest.kt          [신규]
├── viewmodel/SfxCueTest.kt        [신규]
├── viewmodel/GameViewModelTest.kt [수정] flash 타이머 + sfx 게이트 테스트
├── ui/chips/ChipsForTest.kt       [신규]
└── persistence/SaveCodecTest.kt   [수정] v1→v2 마이그레이션 테스트
```

빠른 테스트 루프: `./gradlew :core:jvmTest` / `./gradlew :shared:jvmTest` (둘 다 host JVM에서 commonTest 실행).

---

### Task 1: `:core` — SCOLDED transient 수리 (`ClearDisciplineFlash`)

데모 보존 버그(`disciplineFlash`가 영원히 false → `Mood.SCOLDED` 도달 불가)를 기존 transient 패턴(reducer가 켜고 ViewModel 타이머가 끔)으로 수리한다. 이 Task는 `:core`만 — ViewModel 타이머는 Task 5.

**Files:**
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/Action.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/Reducer.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/GameState.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/Mood.kt`
- Test: `core/src/commonTest/kotlin/today/superb/jvl/core/ReducerTest.kt`, `MoodTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`ReducerTest.kt`에 추가 (기존 테스트 스타일·`SeededRng(42L)` 픽스처를 따른다. 파일 상단에 이미 있는 초기 상태 헬퍼를 재사용 — 없으면 `GameState.initial("UNIT", 0L)` 사용):

```kotlin
@Test
fun discipline_turns_on_discipline_flash() {
    val s = reduce(GameState.initial("UNIT", 0L), Action.Discipline, SeededRng(42L))
    assertTrue(s.disciplineFlash, "Discipline은 disciplineFlash를 켠다")
}

@Test
fun clear_discipline_flash_turns_it_off() {
    val flashed = reduce(GameState.initial("UNIT", 0L), Action.Discipline, SeededRng(42L))
    val cleared = reduce(flashed, Action.ClearDisciplineFlash, SeededRng(42L))
    assertFalse(cleared.disciplineFlash)
    // flash 외 다른 필드는 건드리지 않는다.
    assertEquals(flashed.discipline, cleared.discipline)
}
```

`MoodTest.kt`에 추가:

```kotlin
@Test
fun scolded_mood_is_reachable_after_discipline() {
    val s = reduce(GameState.initial("UNIT", 0L), Action.Discipline, SeededRng(42L))
    assertEquals(Mood.SCOLDED, moodLabel(s))
}
```

주: `moodLabel` 우선순위상 asleep/evolving이 위에 있으나 초기 상태는 둘 다 false라 SCOLDED가 나온다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core:jvmTest --tests "*ReducerTest*" --tests "*MoodTest*"`
Expected: FAIL — `Action.ClearDisciplineFlash` 미정의(컴파일 에러).

- [ ] **Step 3: 구현**

`Action.kt` — `ClearToast` 선언 바로 아래에 추가:

```kotlin
    /** 토스트 만료 — ViewModel 타이머가 1.4s 후 dispatch. */
    data object ClearToast : Action

    /** 훈육 플래시 만료 — ViewModel 타이머가 2s 후 dispatch([Discipline]이 켠 것을 끔). */
    data object ClearDisciplineFlash : Action
```

`Reducer.kt` — `Action.Discipline` 분기에 `disciplineFlash = true` 추가:

```kotlin
        Action.Discipline -> state.copy(
            cycles = state.cycles + 1,
            discipline = (state.discipline + 0.1f).clamp(),
            happiness = (state.happiness - 0.08f).clamp(),
            bond = (state.bond - 0.02f).clamp(),
            disciplineFlash = true,
            log = log("SCOLD — reprimand"),
            toast = "SCOLDED",
        )
```

`Reducer.kt` — `Action.ClearToast` 분기 아래에 추가:

```kotlin
        Action.ClearToast -> state.copy(toast = null)

        Action.ClearDisciplineFlash -> state.copy(disciplineFlash = false)
```

`GameState.kt` — `disciplineFlash` KDoc 교체:

```kotlin
    /**
     * 훈육 직후 잠깐 켜지는 transient flash — [Mood.SCOLDED]의 트리거. toast/evolving과 같은
     * 패턴: reducer([Action.Discipline])가 켜고 ViewModel 타이머가 [Action.ClearDisciplineFlash]로
     * 끈다. (데모 as-is에서는 아무도 켜지 않는 버그였음 — 풀터치 마일스톤에서 수리.)
     */
    val disciplineFlash: Boolean,
```

`Mood.kt` — 함수 KDoc의 버그 주석 교체:

```kotlin
/**
 * 기분 라벨 — 8단계 우선순위. 데모 `screens.jsx:118-127` 1:1.
 *
 * 주: [Mood.SCOLDED]는 [Action.Discipline]이 켜는 transient `disciplineFlash`(~2s, ViewModel이
 * 끔)가 트리거. 데모 as-is에서는 도달 불가 버그였으나 풀터치 마일스톤에서 수리됨.
 */
```

(`Mood.kt`에 `today.superb.jvl.core.Action` KDoc 참조가 unresolved 경고를 내면 import 없이 `[Action.Discipline]` → `Discipline 액션` 평문으로 바꿔도 된다.)

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core:jvmTest`
Expected: BUILD SUCCESSFUL, 전체 테스트 PASS (기존 테스트 무손상 포함).

- [ ] **Step 5: 커밋**

```bash
git add core/
git commit -m "fix(core): make SCOLDED mood reachable via discipline flash transient"
```

---

### Task 2: `:core` — `Action.SetSpecies` + Reset 종 보존

설정의 종 선택이 게임 상태(`GameState.species`)를 실제로 바꾸도록 액션을 추가하고, `Reset`(새 알)이 선택한 종을 잃지 않게 한다(데모에서 종은 tweaks 소관이라 reset과 무관했음 — 동등 동작 유지).

**Files:**
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/Action.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/Reducer.kt`
- Test: `core/src/commonTest/kotlin/today/superb/jvl/core/ReducerTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`ReducerTest.kt`에 추가:

```kotlin
@Test
fun set_species_changes_species_quietly() {
    val s = reduce(GameState.initial("UNIT", 0L), Action.SetSpecies(Species.Squid), SeededRng(42L))
    assertEquals(Species.Squid, s.species)
    assertNull(s.toast, "설정 변경은 토스트를 띄우지 않는다")
    assertEquals(0, s.cycles, "케어 사이클로 세지 않는다")
}

@Test
fun reset_preserves_selected_species() {
    val squid = reduce(GameState.initial("UNIT", 0L), Action.SetSpecies(Species.Squid), SeededRng(42L))
    val next = reduce(squid, Action.Reset(newName = "NEXT", now = 99L), SeededRng(42L))
    assertEquals(Species.Squid, next.species, "새 알도 선택한 종을 유지")
    assertEquals(2, next.gen)
}
```

(파일 상단 import에 `Species`, `assertNull`이 없으면 추가.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core:jvmTest --tests "*ReducerTest*"`
Expected: FAIL — `Action.SetSpecies` 미정의.

- [ ] **Step 3: 구현**

`Action.kt` — `Rename` 아래에 추가:

```kotlin
    data class Rename(val name: String) : Action

    /** 생명체 종 변경(설정 패널). 렌더의 단일 소스인 [GameState.species]를 직접 바꾼다. */
    data class SetSpecies(val species: Species) : Action
```

`Reducer.kt` — `is Action.Rename` 분기 아래에 추가:

```kotlin
        is Action.Rename -> state.copy(name = action.name, log = log("RENAME — ${action.name}"))

        is Action.SetSpecies -> state.copy(species = action.species)
```

`Reducer.kt` — `Action.Reset` 분기의 마지막 `.copy(...)`에 `species` 보존 추가:

```kotlin
            GameState.initial(action.newName, action.now, state.peers).copy(
                gen = state.gen + 1,
                species = state.species,
                lineage = state.lineage + epitaph,
                view = state.view,
                pendingRequest = state.pendingRequest,
                peerEventNonce = state.peerEventNonce,
                peerEventLatest = state.peerEventLatest,
            )
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core:jvmTest`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add core/
git commit -m "feat(core): add SetSpecies action; preserve species across reset"
```

---

### Task 3: `:core` — `tree [gen]` 명령 + `LineageReadout` + HELP 데모 패리티

트리 노드 탭 매크로의 백엔드. `tree 2`가 G02 세대 상세를 터미널에 출력한다(타이핑으로도 동작하는 진짜 명령). 동시에 `HELP_LINES`를 데모 help와 패리티로 갱신한다(현재 scan/tree/bond/challenge/accept/decline/dnd/flee/mute/reset이 도움말에서 누락 — 문서가 실제와 어긋나는 가짜 정보).

**Files:**
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/TerminalCommand.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/TerminalParser.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/TerminalResponder.kt`
- Modify: `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/TerminalCopy.kt`
- Create: `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/LineageReadout.kt`
- Test: `core/src/commonTest/kotlin/today/superb/jvl/core/LineageReadoutTest.kt` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

Create `core/src/commonTest/kotlin/today/superb/jvl/core/LineageReadoutTest.kt`:

```kotlin
package today.superb.jvl.core

import today.superb.jvl.core.terminal.TerminalCommand
import today.superb.jvl.core.terminal.parse
import today.superb.jvl.core.terminal.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineageReadoutTest {

    private val rng = SeededRng(42L)

    /** gen 2 활성 + gen 1 은퇴 상태. */
    private fun lineageState(): GameState {
        val g1 = reduce(GameState.initial("ALPHA", 1_000L), Action.Feed, rng)
        return reduce(g1, Action.Reset(newName = "BETA", now = 2_000L), rng)
    }

    @Test
    fun tree_without_arg_switches_view() {
        val r = respond(parse("tree"), lineageState(), rng)
        assertEquals(Action.SetView(View.Tree), r.action)
    }

    @Test
    fun tree_with_retired_gen_renders_detail_without_view_change() {
        val r = respond(parse("tree 1"), lineageState(), rng)
        assertNull(r.action, "상세 조회는 화면을 바꾸지 않는다")
        assertTrue(r.lines.any { it.text.contains("G01_ALPHA") })
        assertTrue(r.lines.any { it.text.contains("✟ retired") })
    }

    @Test
    fun tree_with_active_gen_renders_live_detail() {
        val r = respond(parse("tree 2"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("G02_BETA") })
        assertTrue(r.lines.any { it.text.contains("● active") })
    }

    @Test
    fun tree_with_unknown_gen_says_no_such_generation() {
        val r = respond(parse("tree 9"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("no such generation") })
        assertNull(r.action)
    }

    @Test
    fun tree_with_non_numeric_arg_prints_usage() {
        val r = respond(parse("tree abc"), lineageState(), rng)
        assertTrue(r.lines.any { it.text.contains("usage: tree") })
        assertNull(r.action)
    }

    @Test
    fun help_lists_all_implemented_commands() {
        val help = respond(parse("help"), GameState.initial("UNIT", 0L), rng)
            .lines.joinToString("\n") { it.text }
        for (cmd in listOf("scan", "tree", "bond", "challenge", "accept", "decline", "dnd", "flee", "mute", "reset")) {
            assertTrue(help.contains(cmd), "help에 $cmd 누락")
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :core:jvmTest --tests "*LineageReadoutTest*"`
Expected: FAIL — `tree 1`이 화면 전환 응답을 반환(상세 미구현), help 누락.

- [ ] **Step 3: 구현**

`TerminalCommand.kt` — `Tree`를 data class로 교체:

```kotlin
    /** `tree [gen]` — 인자 없으면 계보 화면 전환, 세대 번호면 해당 세대 상세 출력. */
    data class Tree(val arg: String?) : TerminalCommand
```

`TerminalParser.kt` — `"tree"` 분기 교체:

```kotlin
        "tree" -> TerminalCommand.Tree(args.firstOrNull())
```

Create `core/src/commonMain/kotlin/today/superb/jvl/core/terminal/LineageReadout.kt`:

```kotlin
package today.superb.jvl.core.terminal

import today.superb.jvl.core.GameState
import today.superb.jvl.core.LineageEntry

/**
 * `tree <gen>` 세대 상세의 순수 텍스트 렌더. [renderStatus]/[renderBond]와 같은 규약 —
 * 색/레이아웃 없이 `List<String>`만 반환. 시각은 표시하지 않는다(respond는 wall-clock이 없음 —
 * 상대시간은 트리 화면 몫).
 */

/** 현 세대를 [LineageEntry] 모양으로 어댑트(트리 렌더·상세 조회 공용). archivedAt=0 = 현역. */
fun activeLineageEntry(state: GameState): LineageEntry = LineageEntry(
    gen = state.gen,
    name = state.name,
    stage = state.stage,
    cycles = state.cycles,
    happiness = (state.happiness * 100).toInt(),
    energy = (state.energy * 100).toInt(),
    bond = (state.bond * 100).toInt(),
    discipline = (state.discipline * 100).toInt(),
    training = (state.training * 100).toInt(),
    hatchedAt = state.hatchedAt,
    archivedAt = 0L,
)

/** 한 세대의 상세 readout. [active]면 현역 표기. */
fun renderGeneration(entry: LineageEntry, active: Boolean): List<String> = listOf(
    "▸ G${entry.gen.toString().padStart(2, '0')}_${entry.name} — ${entry.stage.name.lowercase()}",
    "  cycles      ${entry.cycles.toString().padStart(4, '0')}",
    "  happiness   ${entry.happiness}%",
    "  energy      ${entry.energy}%",
    "  bond        ${entry.bond}%",
    "  discipline  ${entry.discipline}%",
    "  training    ${entry.training}%",
    "  status      ${if (active) "● active" else "✟ retired"}",
)
```

`TerminalResponder.kt` — `TerminalCommand.Tree` 분기 교체 (`is` 패턴으로):

```kotlin
    is TerminalCommand.Tree -> {
        val arg = command.arg
        if (arg == null) {
            val archived = state.lineage.size
            TerminalResponse(
                out(
                    "$ tree GENESIS/",
                    "▸ archive contains $archived retired ${if (archived == 1) "generation" else "generations"} + 1 active.",
                    "▸ lineage rendered on primary display.",
                    "  (type `sonar` to return)",
                ),
                action = Action.SetView(View.Tree),
            )
        } else {
            val gen = arg.toIntOrNull()
            if (gen == null) {
                TerminalResponse(out("usage: tree [gen]"))
            } else {
                val retired = state.lineage.find { it.gen == gen }
                when {
                    retired != null -> TerminalResponse(out(renderGeneration(retired, active = false)))
                    gen == state.gen -> TerminalResponse(out(renderGeneration(activeLineageEntry(state), active = true)))
                    else -> TerminalResponse(out("no such generation: G${gen.toString().padStart(2, '0')}"))
                }
            }
        }
    }
```

`TerminalCopy.kt` — `HELP_LINES` 전체 교체 (데모 `screens.jsx` help 패리티 + `tree [gen]`):

```kotlin
internal val HELP_LINES = listOf(
    "AVAILABLE COMMANDS:",
    "  status        — vitals readout (boxed)",
    "  scan / peers  — radar sweep + nearby unit list",
    "  ping          — sonar pulse on current view",
    "  tree [gen]    — lineage archive / generation detail",
    "  sonar / back  — return to sonar view",
    "  bond <name>   — peer-bond + battle record",
    "  challenge <n> — challenge nearby unit to battle",
    "  accept        — accept incoming request",
    "  decline       — dismiss incoming request",
    "  dnd [on|off]  — block incoming challenges (auto on in battle)",
    "  flee          — disengage from current battle",
    "  feed [item]   — feed creature",
    "  play          — happiness +",
    "  clean         — hygiene +",
    "  sleep / wake  — toggle sleep",
    "  train         — training +",
    "  scold         — discipline +",
    "  heal          — apply biopatch",
    "  evolve        — advance stage",
    "  talk          — converse",
    "  mute          — toggle audio",
    "  name <str>    — rename unit",
    "  whoami        — operator info",
    "  history       — show log",
    "  clear         — clear screen",
    "  reset         — archive + new egg",
)
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :core:jvmTest`
Expected: PASS 전체 (기존 `Tree` data object를 참조하던 테스트가 있으면 `TerminalCommand.Tree(null)`로 갱신 — `grep -rn "TerminalCommand.Tree" core/src/commonTest/`로 확인).

- [ ] **Step 5: 커밋**

```bash
git add core/
git commit -m "feat(core): tree [gen] generation detail + demo-parity help"
```

---

### Task 4: `:shared` — species 단일화 (`Tweaks.species` 제거 + SaveCodec v2)

`GameState.species`를 렌더의 단일 소스로 만든다. 설정 패널 종 선택 → `Action.SetSpecies` dispatch. 옛 저장본(v1)의 `tweaks.species`는 `game.species`로 마이그레이션.

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/settings/Tweaks.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/settings/SettingsPanel.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/sonar/SonarScreen.kt:49`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/battle/BattleScreen.kt:97`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:105-113`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/persistence/SaveCodec.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/persistence/SaveCodecTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`SaveCodecTest.kt`에 추가 (기존 테스트의 GameState/Tweaks 픽스처 스타일을 따른다). v1 디스크 포맷은 현행 코덱으로 v2 블롭을 만든 뒤 문자열 치환으로 재구성한다 — 필드 전체를 손으로 나열하지 않고도 v1과 동형:

```kotlin
@Test
fun decode_migrates_v1_tweaks_species_into_game_state() {
    val codec = SaveCodec()
    val game = GameState.initial("UNIT", 0L)
    // 현행 코덱으로 v2 블롭을 만든 뒤 schemaVersion을 1로 바꾸고 tweaks에 species를 주입해
    // v1 형상을 재구성한다 — 필드 나열 없이 v1 디스크 포맷과 동형.
    val v2 = codec.encode(game, Tweaks())
    val v1 = v2
        .replaceFirst("\"schemaVersion\":2", "\"schemaVersion\":1")
        .replaceFirst("\"tweaks\":{", "\"tweaks\":{\"species\":\"Squid\",")

    val blob = codec.decode(v1)

    assertNotNull(blob, "v1 블롭은 마이그레이션되어 디코드된다")
    assertEquals(Species.Squid, blob.game.species, "tweaks.species → game.species 이관")
    assertEquals(SaveBlob.SCHEMA_VERSION, blob.schemaVersion)
}

@Test
fun decode_v1_without_species_falls_back_to_game_species() {
    val codec = SaveCodec()
    val v2 = codec.encode(GameState.initial("UNIT", 0L), Tweaks())
    val v1 = v2.replaceFirst("\"schemaVersion\":2", "\"schemaVersion\":1")
    val blob = codec.decode(v1)
    assertNotNull(blob)
    assertEquals(Species.Ghost, blob.game.species)
}
```

(import에 `Species`, `assertNotNull` 추가. 기존 테스트 중 `Tweaks(species = ...)`를 쓰는 곳이 있으면 이 Step에서 함께 제거 대상으로 표시 — Step 3에서 컴파일이 잡아준다.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*SaveCodecTest*"`
Expected: FAIL — 현행 `SCHEMA_VERSION`이 1이라 replace가 no-op → 마이그레이션 분기 부재로 assertion 실패. (또는 `"schemaVersion":2` 미존재로 v1 재구성 실패 → 그 자체가 RED 신호.)

- [ ] **Step 3: 구현**

`Tweaks.kt` — `species` 필드 제거, import 정리, KDoc 한 줄 추가:

```kotlin
import kotlinx.serialization.Serializable
import today.superb.jvl.ui.theme.Hue
```

```kotlin
/**
 * 실시간 디스플레이 설정. 데모 `TWEAK_DEFAULTS` 1:1(오디오는 GameState.sound, 종은
 * GameState.species로 통합 → 제외).
 * ...기존 KDoc 유지...
 */
@Immutable
@Serializable
data class Tweaks(
    val theme: Hue = Hue.Green,
    val crtIntensity: Float = 0.7f,   // [0, 1.4]
    val scanlines: Boolean = true,
    val noise: Boolean = true,
    /** 실 CRT 셰이더(AGSL/SkSL) on/off. 기본 off → 기존 Compose-Canvas 근사. 테스터 비교용 토글. */
    val crtShader: Boolean = false,
    val pulsePeriod: Float = 5f,      // [2, 12] s
    val phosphorDecay: Float = 1f,    // [0.3, 4] s
)
```

(`today.superb.jvl.core.Species` import 제거.)

`SettingsPanel.kt` — 시그니처에 species 추가, CREATURE 섹션 교체:

```kotlin
fun SettingsPanel(
    tweaks: Tweaks,
    species: Species,
    sound: Boolean,
    onTweaks: (Tweaks) -> Unit,
    onSelectSpecies: (Species) -> Unit,
    onToggleSound: () -> Unit,
    onHatch: () -> Unit,
    onClose: () -> Unit,
) {
```

```kotlin
            section("CREATURE")
            chipRow("Species", Species.entries, species, { it.name.lowercase() }) { onSelectSpecies(it) }
```

`SonarScreen.kt:49` — `species = LocalTweaks.current.species` → `species = state.species` (파일 내 `LocalTweaks` 사용이 이것뿐이면 import도 제거).

`BattleScreen.kt:97` — `species = LocalTweaks.current.species` → `species = state.species` (동일하게 `LocalTweaks` import 제거 — `grep -n LocalTweaks`로 다른 사용처 없음을 확인).

`App.kt` — SettingsPanel 호출부 교체:

```kotlin
                if (settingsOpen) {
                    SettingsPanel(
                        tweaks = tweaks,
                        species = state.species,
                        sound = state.sound,
                        onTweaks = vm::updateTweaks,
                        onSelectSpecies = { vm.dispatch(Action.SetSpecies(it)) },
                        onToggleSound = { vm.dispatch(Action.ToggleSound) },
                        onHatch = { vm.hatchNewEgg(); settingsOpen = false },
                        onClose = { settingsOpen = false },
                    )
                }
```

`SaveCodec.kt` — 버전 상향 + 마이그레이션. `SaveBlob`의 companion 상수를 2로:

```kotlin
    companion object {
        const val SCHEMA_VERSION = 2
    }
```

`SaveBlob` KDoc에 한 줄 추가:

```kotlin
 * v1 → v2: 종 선택이 Tweaks.species(UI 설정)에서 GameState.species(도메인)로 이동.
 * v1 블롭은 [SaveCodec.decode]가 tweaks.species를 game.species로 이관한다.
```

`SaveCodec.decode` 교체 (파일 상단에 `import kotlinx.serialization.json.jsonObject`, `import kotlinx.serialization.json.jsonPrimitive`, `import today.superb.jvl.core.Species` 추가):

```kotlin
    fun decode(raw: String?): SaveBlob? {
        if (raw == null) return null
        val blob = runCatching { json.decodeFromString(SaveBlob.serializer(), raw) }.getOrNull() ?: return null
        return when (blob.schemaVersion) {
            SaveBlob.SCHEMA_VERSION -> blob.copy(game = blob.game.strippedForSave())
            1 -> {
                // v1: 종이 tweaks.species에 있었다 — raw JSON에서 직접 끌어와 game.species로 이관.
                val legacySpecies = runCatching {
                    json.parseToJsonElement(raw).jsonObject["tweaks"]?.jsonObject
                        ?.get("species")?.jsonPrimitive?.content?.let(Species::valueOf)
                }.getOrNull()
                val game = (legacySpecies?.let { blob.game.copy(species = it) } ?: blob.game).strippedForSave()
                blob.copy(schemaVersion = SaveBlob.SCHEMA_VERSION, game = game)
            }
            else -> null // 알 수 없는 스키마 — 새 게임으로 폴백
        }
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :androidApp:assembleDebug`
Expected: 테스트 전체 PASS + 빌드 성공. `Tweaks(species=...)`를 참조하던 기존 테스트가 깨지면 species 인자 제거로 갱신(동작 변화 없음 확인).

- [ ] **Step 5: 커밋**

```bash
git add shared/
git commit -m "feat(shared): make GameState.species the single render source (save v2 migration)"
```

---

### Task 5: `:shared` — GameViewModel disciplineFlash 타이머

Task 1의 transient를 기존 toast/evolve 패턴으로 끈다.

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/viewmodel/GameViewModel.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/viewmodel/GameViewModelTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`GameViewModelTest.kt`에 추가 (기존 `runTest(dispatcher)`/`advanceTimeBy` 패턴):

```kotlin
@Test
fun scold_flash_expires_after_two_seconds() = runTest(dispatcher) {
    val vm = vm()
    vm.submitCommand("scold")
    runCurrent()
    assertTrue(vm.state.value.disciplineFlash, "scold 직후 flash on")

    advanceTimeBy(2001)
    runCurrent()
    assertFalse(vm.state.value.disciplineFlash, "2s 후 flash 해제")
}
```

(import `assertFalse` 필요 시 추가.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*GameViewModelTest*"`
Expected: FAIL — flash가 영원히 true.

- [ ] **Step 3: 구현**

`GameViewModel.kt` — 상수/Job/스케줄러 추가:

```kotlin
private const val EVOLVE_MS = 2200L
private const val DISCIPLINE_FLASH_MS = 2000L
```

```kotlin
    private var toastJob: Job? = null
    private var evolveJob: Job? = null
    private var battleJob: Job? = null
    private var disciplineJob: Job? = null
```

`dispatch()`의 transient 스케줄 구간에 한 줄 추가:

```kotlin
        if (after.toast != null && after.toast != before.toast) scheduleToastClear()
        if (after.evolving && !before.evolving) scheduleEvolveComplete()
        if (after.disciplineFlash && !before.disciplineFlash) scheduleDisciplineClear()
```

`scheduleEvolveComplete()` 아래에 추가:

```kotlin
    private fun scheduleDisciplineClear() {
        disciplineJob?.cancel()
        disciplineJob = viewModelScope.launch {
            delay(DISCIPLINE_FLASH_MS)
            dispatch(Action.ClearDisciplineFlash)
        }
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add shared/
git commit -m "feat(shared): expire discipline flash via viewmodel timer (SCOLDED mood live)"
```

---

### Task 6: `:shared` — `Sfx` 톤 카탈로그 + `SfxSynth` PCM 합성 (순수)

**Files:**
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/sound/Sfx.kt`
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/sound/SfxSynth.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/sound/SfxSynthTest.kt` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

Create `shared/src/commonTest/kotlin/today/superb/jvl/sound/SfxSynthTest.kt`:

```kotlin
package today.superb.jvl.sound

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SfxSynthTest {

    @Test
    fun render_length_matches_tone_durations() {
        for (sfx in Sfx.entries) {
            val expected = sfx.tones.sumOf { it.durMs * SfxSynth.SAMPLE_RATE / 1000 }
            assertEquals(expected, SfxSynth.render(sfx).size, "${sfx.name} 길이")
        }
    }

    @Test
    fun render_amplitude_stays_in_safe_range() {
        for (sfx in Sfx.entries) {
            val peak = SfxSynth.render(sfx).maxOf { abs(it) }
            assertTrue(peak <= 0.3f, "${sfx.name} 피크 $peak — 마스터 진폭 초과")
            assertTrue(peak > 0f, "${sfx.name} 무음이면 안 됨")
        }
    }

    @Test
    fun rest_tones_are_silent() {
        // Alert는 freq 0 rest 구간을 포함한다 — 그 구간은 전부 0.
        val alert = Sfx.Alert
        val pcm = SfxSynth.render(alert)
        var base = 0
        for (tone in alert.tones) {
            val n = tone.durMs * SfxSynth.SAMPLE_RATE / 1000
            if (tone.freqHz == 0f) {
                for (i in base until base + n) assertEquals(0f, pcm[i], "rest 샘플 $i")
            }
            base += n
        }
    }

    @Test
    fun envelope_starts_and_ends_near_zero() {
        val pcm = SfxSynth.render(Sfx.Ping)
        assertTrue(abs(pcm.first()) < 0.02f, "어택 시작은 0 근접")
        assertTrue(abs(pcm.last()) < 0.02f, "릴리즈 끝은 0 근접")
    }

    @Test
    fun render_is_deterministic() {
        assertContentEquals(SfxSynth.render(Sfx.Win), SfxSynth.render(Sfx.Win))
    }

    @Test
    fun pcm16_conversion_clamps_and_scales() {
        val pcm = SfxSynth.toPcm16(floatArrayOf(0f, 1f, -1f, 2f, -2f))
        assertContentEquals(shortArrayOf(0, 32767, -32767, 32767, -32767), pcm)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*SfxSynthTest*"`
Expected: FAIL — `Sfx`/`SfxSynth` 미정의(컴파일 에러).

- [ ] **Step 3: 구현**

Create `shared/src/commonMain/kotlin/today/superb/jvl/sound/Sfx.kt`:

```kotlin
package today.superb.jvl.sound

/** 한 톤 — 주파수(Hz, 0 = 무음 rest)와 길이(ms). */
data class Tone(val freqHz: Float, val durMs: Int)

private fun t(freq: Float, ms: Int) = Tone(freq, ms)

/**
 * 레트로 SFX 카탈로그 — 사각파 톤 시퀀스(8-bit/CRT 미학). 합성은 [SfxSynth.render],
 * 재생은 [SfxPlayer], 트리거 매핑은 `viewmodel/SfxCue.kt`의 `sfxCueFor`.
 *
 * 주파수는 평균율 음계 근사(C4=262, E4=330, G4=392, A4=440, C5=523, E5=659, G5=784,
 * A5=880, C6=1047, D6=1175, E6=1318).
 */
enum class Sfx(val tones: List<Tone>) {
    Ping(listOf(t(880f, 50), t(1318f, 80))),
    Care(listOf(t(659f, 45), t(988f, 70))),
    Scold(listOf(t(392f, 70), t(262f, 110))),
    SleepCue(listOf(t(523f, 80), t(392f, 80), t(262f, 140))),
    WakeCue(listOf(t(262f, 60), t(523f, 90))),
    Evolve(listOf(t(523f, 70), t(659f, 70), t(784f, 70), t(1047f, 120))),
    EvolveDone(listOf(t(784f, 90), t(1047f, 90), t(1318f, 160))),
    Alert(listOf(t(1175f, 90), t(0f, 40), t(1175f, 90), t(0f, 40), t(1175f, 140))),
    Friendly(listOf(t(784f, 60), t(988f, 60))),
    Hit(listOf(t(196f, 90), t(147f, 60))),
    Crit(listOf(t(98f, 60), t(196f, 60), t(98f, 110))),
    Win(listOf(t(523f, 80), t(659f, 80), t(784f, 80), t(1047f, 200))),
    Lose(listOf(t(330f, 110), t(262f, 110), t(196f, 220))),
    Disengage(listOf(t(440f, 70), t(330f, 110))),
    Confirm(listOf(t(1047f, 60))),
}
```

Create `shared/src/commonMain/kotlin/today/superb/jvl/sound/SfxSynth.kt`:

```kotlin
package today.superb.jvl.sound

import kotlin.math.PI
import kotlin.math.sin

/**
 * [Sfx] 톤 시퀀스 → mono PCM 합성. 순수 — 같은 입력이면 같은 출력(테스트 가능).
 * 모든 플랫폼 플레이어가 같은 버퍼를 캐시해 재생한다.
 */
object SfxSynth {
    const val SAMPLE_RATE = 44100

    private const val AMP = 0.22f      // 마스터 진폭 — 클리핑/청각 피로 방지
    private const val ATTACK_MS = 4    // 톤 경계 클릭 방지 엔벨로프
    private const val RELEASE_MS = 12

    /** float PCM([-1,1]) 합성. iOS(AVAudioPCMBuffer float)가 직접 쓰고, 나머지는 [toPcm16] 경유. */
    fun render(sfx: Sfx, sampleRate: Int = SAMPLE_RATE): FloatArray {
        val total = sfx.tones.sumOf { it.durMs * sampleRate / 1000 }
        val out = FloatArray(total)
        var base = 0
        for (tone in sfx.tones) {
            val n = tone.durMs * sampleRate / 1000
            if (tone.freqHz > 0f) {
                val attack = (ATTACK_MS * sampleRate / 1000).coerceAtMost(n)
                val release = (RELEASE_MS * sampleRate / 1000).coerceAtMost(n)
                for (i in 0 until n) {
                    // 사각파(8-bit 펄스파 근사) — sin 부호만 사용.
                    val square = if (sin(2.0 * PI * tone.freqHz * i / sampleRate) >= 0.0) 1f else -1f
                    val env = minOf(
                        if (attack > 0) i / attack.toFloat() else 1f,
                        if (release > 0) (n - 1 - i) / release.toFloat() else 1f,
                        1f,
                    ).coerceAtLeast(0f)
                    out[base + i] = square * AMP * env
                }
            }
            base += n
        }
        return out
    }

    /** float PCM → 16-bit PCM. Android(AudioTrack)/JVM(Clip)용. */
    fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:jvmTest --tests "*SfxSynthTest*"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add shared/src/commonMain/kotlin/today/superb/jvl/sound/ shared/src/commonTest/kotlin/today/superb/jvl/sound/
git commit -m "feat(shared): retro SFX catalog + pure square-wave PCM synth"
```

---

### Task 7: `:shared` — `sfxCueFor` 순수 트리거 매핑

**Files:**
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/viewmodel/SfxCue.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/viewmodel/SfxCueTest.kt` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

Create `shared/src/commonTest/kotlin/today/superb/jvl/viewmodel/SfxCueTest.kt`:

```kotlin
package today.superb.jvl.viewmodel

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.reduce
import today.superb.jvl.sound.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SfxCueTest {

    private val rng = SeededRng(42L)
    private val base = GameState.initial("UNIT", 0L)

    /** reduce를 실제로 돌려 before/after를 만들고 cue를 판정 — reducer와 매핑의 정합 보장. */
    private fun cue(state: GameState, action: Action): Sfx? =
        sfxCueFor(action, state, reduce(state, action, rng))

    @Test
    fun care_actions_play_care() {
        assertEquals(Sfx.Care, cue(base, Action.Feed))
        assertEquals(Sfx.Care, cue(base, Action.Play))
        assertEquals(Sfx.Care, cue(base, Action.Clean))
        assertEquals(Sfx.Care, cue(base, Action.Heal))
        assertEquals(Sfx.Care, cue(base, Action.Train))
    }

    @Test
    fun ping_scold_sleep_wake_map_to_their_cues() {
        assertEquals(Sfx.Ping, cue(base, Action.Ping))
        assertEquals(Sfx.Scold, cue(base, Action.Discipline))
        assertEquals(Sfx.SleepCue, cue(base, Action.Sleep))
        val asleep = reduce(base, Action.Sleep, rng)
        assertEquals(Sfx.WakeCue, cue(asleep, Action.Sleep))
    }

    @Test
    fun evolve_transition_and_completion() {
        val evolving = reduce(base, Action.Evolve, rng)
        assertEquals(Sfx.Evolve, sfxCueFor(Action.Evolve, base, evolving))
        // 이미 evolving이면 추가 cue 없음.
        assertNull(sfxCueFor(Action.Evolve, evolving, reduce(evolving, Action.Evolve, rng)))

        val done = reduce(evolving, Action.EvolveComplete, rng)
        assertEquals(Sfx.EvolveDone, sfxCueFor(Action.EvolveComplete, evolving, done))
    }

    @Test
    fun toggle_sound_confirms_only_when_turning_on() {
        val on = reduce(base, Action.ToggleSound, rng)
        assertEquals(Sfx.Confirm, sfxCueFor(Action.ToggleSound, base, on))
        val off = reduce(on, Action.ToggleSound, rng)
        assertNull(sfxCueFor(Action.ToggleSound, on, off), "끌 때는 무음")
    }

    @Test
    fun tick_and_view_changes_are_silent() {
        assertNull(cue(base, Action.Tick(1f)))
        assertNull(cue(base, Action.ClearToast))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*SfxCueTest*"`
Expected: FAIL — `sfxCueFor` 미정의.

- [ ] **Step 3: 구현**

Create `shared/src/commonMain/kotlin/today/superb/jvl/viewmodel/SfxCue.kt`:

```kotlin
package today.superb.jvl.viewmodel

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.PeerEventKind
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.sound.Sfx

/**
 * 액션 + 상태 전이 → 재생할 SFX(없으면 null). 순수 함수 — [GameViewModel.dispatch]가 reduce
 * 전후 상태로 호출한다. 음소거 게이트(`after.sound`)는 호출부 책임(여기서 보지 않는다).
 *
 * no-op 액션(전이 없음)에는 소리를 내지 않는다 — 가드된 액션이 거부된 경우 조용해야 하므로
 * 의미 전이를 before/after로 직접 확인한다.
 */
fun sfxCueFor(action: Action, before: GameState, after: GameState): Sfx? = when (action) {
    Action.Ping -> if (after.pingNonce != before.pingNonce) Sfx.Ping else null

    Action.Feed, Action.Play, Action.Clean, Action.Heal, Action.Train -> Sfx.Care
    Action.Discipline -> Sfx.Scold
    Action.Sleep -> if (after.asleep) Sfx.SleepCue else Sfx.WakeCue

    Action.Evolve -> if (after.evolving && !before.evolving) Sfx.Evolve else null
    Action.EvolveComplete -> if (after.stage != before.stage) Sfx.EvolveDone else null

    is Action.PeerTick -> when {
        before.pendingRequest == null && after.pendingRequest != null -> Sfx.Alert
        after.peerEventNonce != before.peerEventNonce &&
            after.peerEventLatest?.kind == PeerEventKind.Friendly -> Sfx.Friendly
        else -> null
    }

    is Action.BattleStart -> if (before.battle == null && after.battle != null) Sfx.Alert else null

    Action.BattleResolve -> {
        val b = after.battle
        val p = before.battle
        when {
            b == null || p == null -> null
            b.flashNonceMe == p.flashNonceMe && b.flashNonceThem == p.flashNonceThem -> null // 양측 빗나감
            b.log.firstOrNull()?.crit == true -> Sfx.Crit
            else -> Sfx.Hit
        }
    }

    Action.BattleApplyDamage -> when (after.battle?.result) {
        BattleResult.Win -> Sfx.Win
        BattleResult.Lose -> Sfx.Lose
        BattleResult.Draw -> Sfx.Disengage
        else -> null // 전투 계속(다음 턴) 또는 Flee는 별도 액션
    }

    Action.BattleFlee -> if (after.battle?.result == BattleResult.Flee) Sfx.Disengage else null

    Action.ToggleSound -> if (after.sound) Sfx.Confirm else null
    is Action.Reset -> Sfx.Confirm

    else -> null
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:jvmTest --tests "*SfxCueTest*"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add shared/
git commit -m "feat(shared): pure sfx cue mapping from action + state transition"
```

---

### Task 8: `:shared` — `SfxPlayer` expect/actual + DI + dispatch 통합

**Files:**
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/sound/SfxPlayer.kt`
- Create: `shared/src/androidMain/kotlin/today/superb/jvl/sound/SfxPlayer.android.kt`
- Create: `shared/src/iosMain/kotlin/today/superb/jvl/sound/SfxPlayer.ios.kt`
- Create: `shared/src/jvmMain/kotlin/today/superb/jvl/sound/SfxPlayer.jvm.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/viewmodel/GameViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/di/Koin.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/viewmodel/GameViewModelTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`GameViewModelTest.kt`에 추가:

```kotlin
private class RecordingSfx : today.superb.jvl.sound.SfxSink {
    val played = mutableListOf<today.superb.jvl.sound.Sfx>()
    override fun play(sfx: today.superb.jvl.sound.Sfx) { played.add(sfx) }
    override fun dispose() {}
}

@Test
fun sfx_plays_only_when_sound_is_on() = runTest(dispatcher) {
    val sink = RecordingSfx()
    val vm = GameViewModel(SeededRng(42L), autoTick = false, sfx = sink)

    vm.submitCommand("feed")
    runCurrent()
    assertTrue(sink.played.isEmpty(), "sound off — 무음")

    vm.submitCommand("sound")   // ToggleSound → sound=true + Confirm
    runCurrent()
    assertEquals(listOf(today.superb.jvl.sound.Sfx.Confirm), sink.played)

    vm.submitCommand("feed")
    runCurrent()
    assertEquals(today.superb.jvl.sound.Sfx.Care, sink.played.last(), "sound on — Care 재생")
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*GameViewModelTest*"`
Expected: FAIL — `SfxSink` 미정의 / `GameViewModel`에 `sfx` 파라미터 없음.

- [ ] **Step 3: commonMain 구현**

Create `shared/src/commonMain/kotlin/today/superb/jvl/sound/SfxPlayer.kt`:

```kotlin
package today.superb.jvl.sound

/** SFX 재생 표면 — [GameViewModel]이 의존(테스트는 recording fake 주입). */
interface SfxSink {
    fun play(sfx: Sfx)
    fun dispose()
}

/**
 * 플랫폼 SFX 재생기 — [SfxSynth]가 합성한 PCM을 fire-and-forget 재생.
 * 첫 재생 시 합성해 캐시한다(톤이 짧아 합성은 ~ms). 재생 실패는 게임 진행을 막지 않는다
 * (조용히 무시 — 오디오는 connectivity가 아닌 연출).
 */
expect class SfxPlayer() : SfxSink {
    override fun play(sfx: Sfx)
    override fun dispose()
}
```

`GameViewModel.kt` — 생성자에 sink 추가 + dispatch 통합 + dispose:

```kotlin
import today.superb.jvl.sound.SfxSink
```

```kotlin
class GameViewModel(
    private val rng: Rng,
    autoTick: Boolean = true,
    initialState: GameState? = null,
    initialTweaks: Tweaks? = null,
    private val store: GameStore? = null,
    private val codec: SaveCodec? = null,
    private val sfx: SfxSink? = null,
) : ViewModel() {
```

`dispatch()`에서 transient 스케줄 줄들 **앞**에 추가 (after 확정 직후):

```kotlin
    fun dispatch(action: Action) {
        val before = _state.value
        val after = reduce(before, action, rng)
        _state.value = after

        // SFX — 음소거 게이트는 after 기준(켜는 토글 자신도 들리게).
        if (after.sound) sfxCueFor(action, before, after)?.let { cue -> sfx?.play(cue) }

        if (after.toast != null && after.toast != before.toast) scheduleToastClear()
```

`onCleared()`에 추가:

```kotlin
    override fun onCleared() {
        super.onCleared()
        sfx?.dispose()
        val c = codec ?: return
        val s = store ?: return
        s.save(c.encode(_state.value, _tweaks.value))
    }
```

`Koin.kt` — 바인딩 추가:

```kotlin
import today.superb.jvl.sound.SfxPlayer
import today.superb.jvl.sound.SfxSink
```

```kotlin
val appModule = module {
    single<Rng> { DefaultRng() }
    single<GameStore> { createGameStore() }
    single { SaveCodec() }
    single<SfxSink> { SfxPlayer() }
    viewModel {
        val codec = get<SaveCodec>()
        val blob = codec.decode(get<GameStore>().load())
        GameViewModel(
            get(),
            initialState = blob?.game,
            initialTweaks = blob?.tweaks,
            store = get(),
            codec = codec,
            sfx = get(),
        )
    }
}
```

- [ ] **Step 4: 플랫폼 actual 구현**

Create `shared/src/androidMain/kotlin/today/superb/jvl/sound/SfxPlayer.android.kt`:

```kotlin
package today.superb.jvl.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Android — [AudioTrack] MODE_STATIC. 짧은 SFX라 트랙은 1회용(재생 끝나면 자체 release). */
actual class SfxPlayer actual constructor() : SfxSink {
    private val cache = mutableMapOf<Sfx, ShortArray>()

    actual override fun play(sfx: Sfx) {
        runCatching {
            val pcm = cache.getOrPut(sfx) { SfxSynth.toPcm16(SfxSynth.render(sfx)) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SfxSynth.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { t?.release() }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            track.play()
        } // 실패(오디오 포커스/디바이스 등)는 무해 — 연출일 뿐 게임 진행과 무관.
    }

    actual override fun dispose() {
        cache.clear()
    }
}
```

Create `shared/src/jvmMain/kotlin/today/superb/jvl/sound/SfxPlayer.jvm.kt`:

```kotlin
package today.superb.jvl.sound

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/** JVM(host 테스트/도구) — [javax.sound.sampled.Clip]. 오디오 장치 없는 CI에선 조용히 무시. */
actual class SfxPlayer actual constructor() : SfxSink {
    private val cache = mutableMapOf<Sfx, ByteArray>()

    actual override fun play(sfx: Sfx) {
        runCatching {
            val bytes = cache.getOrPut(sfx) {
                val pcm = SfxSynth.toPcm16(SfxSynth.render(sfx))
                ByteArray(pcm.size * 2).also { b ->
                    for (i in pcm.indices) {
                        b[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
                        b[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
                    }
                }
            }
            val clip = AudioSystem.getClip()
            clip.open(AudioFormat(SfxSynth.SAMPLE_RATE.toFloat(), 16, 1, true, false), bytes, 0, bytes.size)
            clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
            clip.start()
        }
    }

    actual override fun dispose() {
        cache.clear()
    }
}
```

Create `shared/src/iosMain/kotlin/today/superb/jvl/sound/SfxPlayer.ios.kt`:

```kotlin
package today.superb.jvl.sound

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode

/**
 * iOS — [AVAudioEngine] + [AVAudioPlayerNode]에 float PCM 버퍼를 스케줄.
 * 엔진 기동 실패(오디오 세션 등)는 조용히 무시(연출이라 게임 진행과 무관).
 */
@OptIn(ExperimentalForeignApi::class)
actual class SfxPlayer actual constructor() : SfxSink {
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format = AVAudioFormat(AVAudioPCMFormatFloat32, SfxSynth.SAMPLE_RATE.toDouble(), 1u, false)
    private val cache = mutableMapOf<Sfx, AVAudioPCMBuffer>()
    private var started = false

    private fun ensureStarted(): Boolean {
        if (started) return true
        runCatching {
            engine.attachNode(player)
            engine.connect(player, engine.mainMixerNode, format)
            started = engine.startAndReturnError(null)
        }
        return started
    }

    actual override fun play(sfx: Sfx) {
        runCatching {
            if (!ensureStarted()) return
            val buffer = cache.getOrPut(sfx) { renderBuffer(sfx) ?: return }
            player.scheduleBuffer(buffer, completionHandler = null)
            if (!player.playing) player.play()
        }
    }

    private fun renderBuffer(sfx: Sfx): AVAudioPCMBuffer? {
        val samples = SfxSynth.render(sfx)
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt())
        buffer.frameLength = samples.size.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return null
        for (i in samples.indices) channel[i] = samples[i]
        return buffer
    }

    actual override fun dispose() {
        runCatching {
            player.stop()
            engine.stop()
        }
        cache.clear()
    }
}
```

**iOS 바인딩 주의:** Linux에서 컴파일 검증 불가. K/N AVFAudio 바인딩의 생성자 라벨(`pCMFormat` vs `pcmFormat`), `playing` 프로퍼티명, `scheduleBuffer` 오버로드는 macOS에서 `./gradlew :shared:compileKotlinIosArm64`로 확인하고 어긋나면 바인딩에 맞춰 조정한다(기존 `CrtShader.ios.kt`가 K/N 바인딩 사용 예시).

- [ ] **Step 5: 통과/빌드 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :shared:compileCommonMainKotlinMetadata && ./gradlew :androidApp:assembleDebug`
Expected: 전부 SUCCESS. (metadata 컴파일이 expect/actual 누락을 잡는다 — jvm/android/ios 모두 actual 존재.)

- [ ] **Step 6: 커밋**

```bash
git add shared/
git commit -m "feat(shared): real SFX playback — expect/actual player wired into dispatch"
```

---

### Task 9: `:shared` — `CommandChip` + `chipsFor` (순수)

**Files:**
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/ui/chips/CommandChips.kt`
- Test: `shared/src/commonTest/kotlin/today/superb/jvl/ui/chips/ChipsForTest.kt` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

Create `shared/src/commonTest/kotlin/today/superb/jvl/ui/chips/ChipsForTest.kt`:

```kotlin
package today.superb.jvl.ui.chips

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.PeerRequest
import today.superb.jvl.core.Personality
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.View
import today.superb.jvl.core.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipsForTest {

    private val rng = SeededRng(42L)
    private val base = GameState.initial("UNIT", 0L)

    private fun labels(state: GameState, selected: Peer? = null) =
        chipsFor(state, selected).map { it.label }

    @Test
    fun sonar_default_offers_care_and_nav() {
        assertEquals(
            listOf("FEED", "PLAY", "CLEAN", "TRAIN", "HEAL", "SLEEP", "SCOLD", "RADAR", "TREE"),
            labels(base),
        )
    }

    @Test
    fun asleep_puts_wake_first_and_drops_sleep() {
        val asleep = reduce(base, Action.Sleep, rng)
        val l = labels(asleep)
        assertEquals("WAKE", l.first())
        assertTrue("SLEEP" !in l)
    }

    @Test
    fun can_evolve_prepends_highlighted_evolve() {
        val ready = base.copy(canEvolve = true)
        val chips = chipsFor(ready)
        assertEquals("★EVOLVE", chips.first().label)
        assertEquals(ChipEmphasis.Highlight, chips.first().emphasis)
        assertEquals("evolve", chips.first().command)
    }

    @Test
    fun pending_request_prepends_accept_decline_alerts() {
        val pending = base.copy(pendingRequest = PeerRequest("p1", RequestType.Challenge))
        val chips = chipsFor(pending)
        assertEquals(listOf("ACCEPT", "DECLINE"), chips.take(2).map { it.label })
        assertTrue(chips.take(2).all { it.emphasis == ChipEmphasis.Alert })
    }

    @Test
    fun battle_locks_everything_but_flee() {
        val inBattle = reduce(
            base.copy(peers = listOf(peer("p1", "HRRK"))),
            Action.BattleStart("p1"),
            rng,
        )
        assertEquals(listOf("FLEE"), labels(inBattle))
    }

    @Test
    fun radar_adds_back_and_challenge_for_selection() {
        val onRadar = base.copy(view = View.Radar, peers = listOf(peer("p1", "HRRK")))
        val l = labels(onRadar, selected = onRadar.peers.first())
        assertEquals("BACK", l.first())
        assertTrue(l.any { it == "CHALLENGE HRRK" })
        val cmd = chipsFor(onRadar, onRadar.peers.first()).first { it.label == "CHALLENGE HRRK" }.command
        assertEquals("challenge hrrk", cmd)
    }

    @Test
    fun tree_adds_back() {
        assertEquals("BACK", labels(base.copy(view = View.Tree)).first())
    }

    private fun peer(id: String, name: String) = Peer(
        id, name, Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f,
        bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 99f,
    )
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:jvmTest --tests "*ChipsForTest*"`
Expected: FAIL — `chipsFor` 미정의.

- [ ] **Step 3: 구현**

Create `shared/src/commonMain/kotlin/today/superb/jvl/ui/chips/CommandChips.kt`:

```kotlin
package today.superb.jvl.ui.chips

import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.View

/** 칩 강조 단계 — Normal(케어/네비) / Highlight(기회: evolve·challenge) / Alert(응답 필요: accept 등). */
enum class ChipEmphasis { Normal, Highlight, Alert }

/** 명령 칩 — 탭하면 [command]가 터미널 매크로(`submitCommand`)로 실행된다. */
data class CommandChip(
    val label: String,
    val command: String,
    val emphasis: ChipEmphasis = ChipEmphasis.Normal,
)

private fun chip(label: String, command: String = label.lowercase()) = CommandChip(label, command)

/**
 * 현재 상태에서 보여줄 명령 칩 목록. 순수 함수(commonTest 대상).
 *
 * 우선순위(앞에서부터): 응답 필요(Alert) → 기회(★EVOLVE) → 뷰 컨텍스트(BACK/CHALLENGE) →
 * 케어(수면 중엔 WAKE가 선두) → 네비(RADAR/TREE, 소나에서만).
 * 전투 중에는 FLEE만 — responder의 `allowedInBattle`과 일치(케어 칩을 눌러봤자 locked라
 * 보여주지 않는 것이 옳다).
 *
 * @param selectedPeer 레이더에서 탭으로 선택된 블립(화면 local state — GameState 밖).
 */
fun chipsFor(state: GameState, selectedPeer: Peer? = null): List<CommandChip> {
    if (state.battle != null) return listOf(CommandChip("FLEE", "flee", ChipEmphasis.Alert))

    return buildList {
        if (state.pendingRequest != null) {
            add(CommandChip("ACCEPT", "accept", ChipEmphasis.Alert))
            add(CommandChip("DECLINE", "decline", ChipEmphasis.Alert))
        }
        if (state.canEvolve && !state.evolving) {
            add(CommandChip("★EVOLVE", "evolve", ChipEmphasis.Highlight))
        }
        when (state.view) {
            View.Radar -> {
                add(chip("BACK", "back"))
                if (selectedPeer != null) {
                    add(
                        CommandChip(
                            label = "CHALLENGE ${selectedPeer.name}",
                            command = "challenge ${selectedPeer.name.lowercase()}",
                            emphasis = ChipEmphasis.Highlight,
                        ),
                    )
                }
            }
            View.Tree -> add(chip("BACK", "back"))
            else -> {}
        }
        if (state.asleep) add(chip("WAKE"))
        add(chip("FEED"))
        add(chip("PLAY"))
        add(chip("CLEAN"))
        add(chip("TRAIN"))
        add(chip("HEAL"))
        if (!state.asleep) add(chip("SLEEP"))
        add(chip("SCOLD"))
        if (state.view == View.Sonar) {
            add(chip("RADAR", "scan"))
            add(chip("TREE", "tree"))
        }
    }
}
```

주의 — 테스트 기대 순서: 평시 `FEED PLAY CLEAN TRAIN HEAL SLEEP SCOLD RADAR TREE`, 수면 시 `WAKE FEED PLAY CLEAN TRAIN HEAL SCOLD ...`. 위 구현이 정확히 그 순서를 낸다(SLEEP은 HEAL 뒤, WAKE는 선두).

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:jvmTest --tests "*ChipsForTest*"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add shared/
git commit -m "feat(shared): context-aware command chip model (pure chipsFor)"
```

---

### Task 10: `:shared` — `CommandChipStrip` Composable + App 통합

**Files:**
- Create: `shared/src/commonMain/kotlin/today/superb/jvl/ui/chips/CommandChipStrip.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/frame/DeviceFrame.kt:43`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:99-101`

- [ ] **Step 1: 구현** (Compose UI — 단위 테스트 대신 빌드 + Task 18 에뮬레이터 QA로 검증)

Create `shared/src/commonMain/kotlin/today/superb/jvl/ui/chips/CommandChipStrip.kt`:

```kotlin
package today.superb.jvl.ui.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette

/**
 * 터미널 입력줄 위의 명령 칩 한 줄 — 탭하면 해당 명령을 터미널 매크로로 실행([onCommand] →
 * `GameViewModel.submitCommand`). 모바일에서 사라진 `↑↓` 명령 히스토리의 실질적 대체 수단.
 */
@Composable
fun CommandChipStrip(
    chips: List<CommandChip>,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    LazyRow(
        modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(chips, key = { it.command }) { chipItem ->
            val border = when (chipItem.emphasis) {
                ChipEmphasis.Alert, ChipEmphasis.Highlight -> palette.phos
                ChipEmphasis.Normal -> palette.phosDim
            }
            val text = when (chipItem.emphasis) {
                ChipEmphasis.Alert, ChipEmphasis.Highlight -> palette.phos
                ChipEmphasis.Normal -> palette.phosMid
            }
            val bg = if (chipItem.emphasis == ChipEmphasis.Alert) palette.phosGrid else palette.bg
            Box(
                Modifier
                    .border(1.dp, border)
                    .background(bg)
                    .clickable { onCommand(chipItem.command) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                MonoText(chipItem.label, color = text, fontSize = 10.sp)
            }
        }
    }
}
```

`DeviceFrame.kt:43` — 터미널 슬롯을 칩 높이만큼 확장:

```kotlin
        Box(Modifier.fillMaxWidth().height(312.dp)) { terminal() }
```

(KDoc의 `280.dp` 언급도 `312.dp(칩 스트립 30 + 터미널)`로 갱신.)

`App.kt` — terminal 슬롯 교체 + import 추가:

```kotlin
import androidx.compose.foundation.layout.Column
import today.superb.jvl.ui.chips.CommandChipStrip
import today.superb.jvl.ui.chips.chipsFor
```

```kotlin
                        terminal = {
                            Column {
                                CommandChipStrip(
                                    chips = chipsFor(state),
                                    onCommand = vm::submitCommand,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                Box(Modifier.fillMaxWidth().weight(1f)) {
                                    TerminalScreen(lines = terminal, name = state.name, onSubmit = vm::submitCommand)
                                }
                            }
                        },
```

(`chipsFor(state)` — selectedPeer 인자는 Task 12에서 배선. `Box`/`fillMaxWidth`는 이미 import됨.)

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): command chip strip above terminal (touch macros)"
```

---

### Task 11: `:shared` — 생명체 탭(ping)/롱프레스(talk)

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/sonar/SonarScreen.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:90`

- [ ] **Step 1: 구현**

`SonarScreen.kt` — 시그니처 + 생명체 Box에 제스처 (import 추가: `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.ui.input.pointer.pointerInput`):

```kotlin
/**
 * 소나 화면 — 상단 readout + 도트 생명체 + 토스트 배너.
 * 생명체 영역 탭 = ping(무음 dispatch — 로그 스팸 방지), 롱프레스 = `talk` 매크로.
 */
@Composable
fun SonarScreen(
    state: GameState,
    onPing: () -> Unit = {},
    onTalk: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

```kotlin
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onPing() }, onLongPress = { onTalk() })
                },
            contentAlignment = Alignment.Center,
        ) {
```

`App.kt:90` — 호출부 교체:

```kotlin
                                        else -> SonarScreen(
                                            state = state,
                                            onPing = { vm.dispatch(Action.Ping) },
                                            onTalk = { vm.submitCommand("talk") },
                                        )
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): tap creature to ping, long-press to talk"
```

---

### Task 12: `:shared` — 레이더 블립 탭 + 선택 링 + CHALLENGE 칩

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/radar/RadarScreen.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt`

- [ ] **Step 1: 구현 — RadarScreen**

`RadarScreen.kt` import 추가:

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
```

시그니처 교체:

```kotlin
/**
 * ...기존 KDoc...
 * 블립 탭 = 선택 + `bond <name>` 매크로(App이 배선). 선택된 블립은 잔광과 무관하게 브래킷 링.
 */
@Composable
fun RadarScreen(
    state: GameState,
    selectedPeerId: String? = null,
    onSelectPeer: (Peer?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

`RadarScope` 호출부:

```kotlin
            RadarScope(state.peers, selectedPeerId, onSelectPeer, Modifier.fillMaxSize())
```

`RadarScope` — 시그니처/탭 판정/선택 링:

```kotlin
@Composable
private fun RadarScope(
    peers: List<Peer>,
    selectedPeerId: String?,
    onSelectPeer: (Peer?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val labelFont = LocalMonoFont.current
    val curPeers by rememberUpdatedState(peers)

    val litTime = remember { mutableMapOf<String, Float>() } // peerId → 마지막으로 스윕에 닿은 t
    var renderT by remember { mutableStateOf(0f) }
```

(프레임 루프 `LaunchedEffect`는 기존 그대로.)

`Canvas(modifier)`를 탭 입력이 달린 형태로 교체:

```kotlin
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = minOf(size.width, size.height) / 2f - 16f
                if (r <= 0f) return@detectTapGestures
                val hitRadius = 22.dp.toPx()   // 최소 44dp 히트 타깃의 반경
                val t = renderT
                // 보이는(잔광 살아있는) 블립 중 탭에 가장 가까운 것.
                val hit = curPeers
                    .filter { p -> exp(-(t - (litTime[p.id] ?: -10f)) / DECAY_S) > 0.04f }
                    .map { p ->
                        val a = bearingToCanvas(p.bearing)
                        val pos = Offset(cx + cos(a) * p.range * r, cy + sin(a) * p.range * r)
                        p to (pos - tap).getDistance()
                    }
                    .filter { (_, d) -> d <= hitRadius }
                    .minByOrNull { (_, d) -> d }
                    ?.first
                onSelectPeer(hit)
            }
        },
    ) {
```

블립 루프 아래(외곽 눈금 그리기 전)에 선택 링 추가:

```kotlin
        // 선택된 블립 — 잔광과 무관한 타깃 링(위치는 실시간 피어 위치 추적).
        selectedPeerId?.let { sel ->
            curPeers.find { it.id == sel }?.let { p ->
                val pos = polar(p.bearing, p.range * r)
                drawCircle(palette.phos, radius = 14f, center = pos, style = Stroke(1.6f), alpha = 0.95f)
                drawCircle(palette.phos, radius = 20f, center = pos, style = Stroke(0.8f), alpha = 0.45f)
            }
        }
```

- [ ] **Step 2: 구현 — App 배선**

`App.kt` import 추가:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

`App()` 본문 — `settingsOpen` 아래에 선택 상태 + 해제 로직:

```kotlin
    var settingsOpen by remember { mutableStateOf(false) }
    // 레이더 블립 선택 — 화면 local 프레젠테이션 상태(GameState 밖). 레이더를 벗어나면 해제.
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.view) { if (state.view != View.Radar) selectedPeerId = null }
    val selectedPeer = state.peers.find { it.id == selectedPeerId }
```

Radar 분기 교체:

```kotlin
                                        View.Radar -> RadarScreen(
                                            state = state,
                                            selectedPeerId = selectedPeerId,
                                            onSelectPeer = { peer ->
                                                selectedPeerId = peer?.id
                                                peer?.let { vm.submitCommand("bond ${it.name.lowercase()}") }
                                            },
                                        )
```

칩 스트립에 선택 전달 (Task 10에서 만든 호출부):

```kotlin
                                CommandChipStrip(
                                    chips = chipsFor(state, selectedPeer),
                                    onCommand = vm::submitCommand,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 4: 커밋**

```bash
git add shared/
git commit -m "feat(shared): tap radar blips — bond readout, target ring, challenge chip"
```

---

### Task 13: `:shared` — 트리 노드 탭 (`tree <gen>` 매크로) + readout 재사용

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/tree/TreeScreen.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:82`

- [ ] **Step 1: 구현**

`TreeScreen.kt`:

`TreeLine`에 gen 태그 추가:

```kotlin
/** 트리 한 줄 — dim(은퇴)/alive(현 세대 status) 색 구분, [gen]은 노드 헤더 줄만(탭 → `tree <gen>`). */
private data class TreeLine(val text: String, val dim: Boolean = false, val alive: Boolean = false, val gen: Int? = null)
```

시그니처:

```kotlin
@Composable
fun TreeScreen(state: GameState, onSelectGen: (Int) -> Unit = {}, modifier: Modifier = Modifier) {
```

`buildTreeLines`의 노드 헤더 줄에 gen 태깅:

```kotlin
        out += TreeLine("$branch" + "G${e.gen.toString().padStart(2, '0')}_${e.name}/$activeTag", dim = retired, gen = e.gen)
```

`LazyColumn` 아이템에 클릭 배선 (import `androidx.compose.foundation.clickable` 추가):

```kotlin
            items(lines) { line ->
                MonoText(
                    line.text,
                    color = when {
                        line.alive -> palette.phos
                        line.dim -> palette.phosDim
                        else -> palette.phosMid
                    },
                    fontSize = 10.sp,
                    fontFamily = LocalMonoFont.current,
                    modifier = if (line.gen != null) {
                        Modifier.fillMaxWidth().clickable { onSelectGen(line.gen) }
                    } else {
                        Modifier
                    },
                )
            }
```

로컬 `activeOf`를 Task 3의 core 함수로 교체 — `private fun activeOf` 삭제, import 추가:

```kotlin
import today.superb.jvl.core.terminal.activeLineageEntry
```

```kotlin
    val nodes = state.lineage.map { it to true } + (activeLineageEntry(state) to false)
```

(`LineageEntry` import는 유지 — `fmtTime`/노드 타입에 여전히 쓰임. 사용처가 사라지면 제거.)

하단 힌트도 탭 가능함을 알리도록 교체:

```kotlin
    out += TreeLine("▸ tap a node for detail · type `sonar` to return", dim = true)
```

`App.kt:82` — 호출부:

```kotlin
                                        View.Tree -> TreeScreen(
                                            state = state,
                                            onSelectGen = { vm.submitCommand("tree $it") },
                                        )
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): tap lineage nodes for generation detail"
```

---

### Task 14: `:shared` — 경보 오버레이 ACCEPT/DECLINE 버튼

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/radar/PeerAlertOverlay.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:93-95`

- [ ] **Step 1: 구현**

`PeerAlertOverlay.kt` — import 추가(`androidx.compose.foundation.clickable`), 시그니처:

```kotlin
@Composable
fun PeerAlertOverlay(
    peer: Peer,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

하단 안내 구간(`CHALLENGE INCOMING` 아래) 교체:

```kotlin
            Spacer(Modifier.height(8.dp))
            MonoText("CHALLENGE INCOMING", color = palette.phos, fontSize = 12.sp)

            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                alertButton("▸ ACCEPT", onAccept)
                alertButton("✕ DECLINE", onDecline)
            }
            MonoText("▸ terminal: accept · decline", color = palette.phosDim, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
```

파일 끝에 버튼 헬퍼 추가:

```kotlin
@Composable
private fun alertButton(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.border(1.dp, palette.phos).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        MonoText(label, color = palette.phos, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
```

(`Box`는 이미 import됨 — 누락 시 추가.)

`App.kt:93-95` — 호출부:

```kotlin
                                    state.pendingRequest?.let { req ->
                                        state.peers.find { it.id == req.from }?.let {
                                            PeerAlertOverlay(
                                                peer = it,
                                                onAccept = { vm.submitCommand("accept") },
                                                onDecline = { vm.submitCommand("decline") },
                                            )
                                        }
                                    }
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): accept/decline buttons on proximity alert overlay"
```

---

### Task 15: `:shared` — `◉ LINK` 인디케이터 실기능화

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:66-74`

- [ ] **Step 1: 구현**

`App.kt` import 추가:

```kotlin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
```

헤더 Row 교체:

```kotlin
                        header = {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                MonoText("SONAR-OBS · MK.III", fontFamily = LocalTechFont.current)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // LINK — 피어 채널 상태: 접점 수 표시, 도전 수신 시 점멸, 탭 = scan 매크로.
                                    val linkAlpha = if (state.pendingRequest != null) {
                                        val blink by rememberInfiniteTransition(label = "link").animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                                            label = "link-blink",
                                        )
                                        blink
                                    } else {
                                        1f
                                    }
                                    MonoText(
                                        "◉ LINK·${state.peers.size}",
                                        fontFamily = LocalTechFont.current,
                                        modifier = Modifier
                                            .alpha(linkAlpha)
                                            .clickable { vm.submitCommand("scan") },
                                    )
                                    MonoText(
                                        "⚙ CFG",
                                        fontFamily = LocalTechFont.current,
                                        modifier = Modifier.clickable { settingsOpen = true },
                                    )
                                }
                            }
                        },
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): LINK indicator shows contact count, blinks on challenge, taps to scan"
```

---

### Task 16: `:shared` — 전투 HP 피격 플래시 (`flashNonce` 소비)

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/battle/BattleScreen.kt:59,64,140-156`

- [ ] **Step 1: 구현**

`BattleScreen.kt` import 추가:

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
```

(`lerp`는 `androidx.compose.ui.graphics.lerp(Color, Color, Float)` — 이미 다른 import와 충돌 없는지 확인.)

호출부 (59·64줄):

```kotlin
            HpBar(state.name, battle.hpMe, battle.hpMaxMe, Alignment.Start, battle.flashNonceMe, Modifier.weight(1f))
            ...
            HpBar(peer.name, battle.hpThem, battle.hpMaxThem, Alignment.End, battle.flashNonceThem, Modifier.weight(1f))
```

`HpBar` 교체:

```kotlin
@Composable
private fun HpBar(
    name: String,
    hp: Float,
    hpMax: Int,
    align: Alignment.Horizontal,
    flashNonce: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val filled = ceil(hp.coerceAtLeast(0f)).toInt()

    // 피격 플래시 — reducer가 올리는 flashNonce를 소비(420ms 화이트아웃 후 감쇠).
    val flash = remember { Animatable(0f) }
    LaunchedEffect(flashNonce) {
        if (flashNonce > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420))
        }
    }
    val cellOn = lerp(palette.phos, Color.White, flash.value * 0.8f)
    val cellBorder = lerp(palette.phosDim, Color.White, flash.value * 0.8f)

    Column(modifier, horizontalAlignment = align) {
        MonoText(name, color = palette.phos, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = LocalDisplayFont.current)
        Row {
            repeat(hpMax) { i ->
                Box(
                    Modifier.padding(end = 2.dp).size(width = 12.dp, height = 8.dp)
                        .background(if (i < filled) cellOn else palette.phosGrid)
                        .border(1.dp, cellBorder),
                )
            }
        }
        MonoText("${filled.coerceAtLeast(0)}/$hpMax", color = palette.phosDim, fontSize = 9.sp)
    }
}
```

(`tween` import는 파일에 `animateDpAsState`용으로 이미 있는지 확인 — 없으면 `androidx.compose.animation.core.tween` 추가.)

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew :androidApp:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 3: 커밋**

```bash
git add shared/
git commit -m "feat(shared): hp bar hit flash consumes battle flash nonces"
```

---

### Task 17: `:shared` — 베젤 하우징 3종 (military/vintage/minimal)

데모 `Bezel` 컴포넌트(jsx inline 스타일) 포팅. 하우징 색은 팔레트와 독립적인 하드웨어 색(데모 값 그대로).

**Files:**
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/bezel/MainBezel.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/settings/Tweaks.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/ui/settings/SettingsPanel.kt`
- Modify: `shared/src/commonMain/kotlin/today/superb/jvl/App.kt:78`

- [ ] **Step 1: 구현 — MainBezel**

`MainBezel.kt` 전체 교체:

```kotlin
package today.superb.jvl.ui.bezel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import today.superb.jvl.ui.text.MonoText
import today.superb.jvl.ui.theme.LocalPalette
import today.superb.jvl.ui.theme.LocalTechFont

/** 하우징 종류 — 데모 tweaks `bezel`(military/vintage/minimal) 1:1. Tweaks에 by-name 직렬화. */
enum class BezelStyle { Military, Vintage, Minimal }

/** 하우징 하드웨어 색 — 팔레트(phosphor)와 독립. 데모 `Bezel` jsx inline 값 1:1. */
private data class Housing(val outerTop: Color, val outerBottom: Color, val bolt: Color, val text: Color, val innerBorder: Color)

private val MILITARY = Housing(
    outerTop = Color(0xFF2C2F26), outerBottom = Color(0xFF16180F),
    bolt = Color(0xFF0A0C08), text = Color(0xFF7A8068), innerBorder = Color(0xFF080A05),
)
private val VINTAGE = Housing(
    outerTop = Color(0xFF4A3D2C), outerBottom = Color(0xFF2E261C),
    bolt = Color(0xFF1A140D), text = Color(0xFF9D8867), innerBorder = Color(0xFF1A1208),
)

/**
 * 메인 화면을 감싸는 물리 하우징. military/vintage = 그래디언트 + 볼트 4 + 라벨 스트립 + LED,
 * minimal = 검정 패딩 박스(라벨/장식 없음). 데모 `Bezel({variant})` 포팅.
 */
@Composable
fun MainBezel(label: String, style: BezelStyle = BezelStyle.Military, content: @Composable () -> Unit) {
    if (style == BezelStyle.Minimal) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black)
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .padding(4.dp),
        ) { content() }
        return
    }

    val palette = LocalPalette.current
    val housing = if (style == BezelStyle.Vintage) VINTAGE else MILITARY

    Box(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(housing.outerTop, housing.outerBottom))),
    ) {
        // 코너 볼트 4개.
        for ((align, x, y) in listOf(
            Triple(Alignment.TopStart, 6.dp, 6.dp),
            Triple(Alignment.TopEnd, (-6).dp, 6.dp),
            Triple(Alignment.BottomStart, 6.dp, (-6).dp),
            Triple(Alignment.BottomEnd, (-6).dp, (-6).dp),
        )) {
            Box(
                Modifier.align(align).offset(x = x, y = y).size(5.dp)
                    .background(housing.bolt, CircleShape),
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            MonoText(
                label,
                color = housing.text,
                fontFamily = LocalTechFont.current,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)
                    .border(1.dp, housing.innerBorder),
            ) { content() }
            // indicator LED 2개 — 첫째 on(phosphor), 둘째 off(데모 1:1).
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            ) {
                Box(Modifier.size(5.dp).background(palette.phos, CircleShape))
                Box(Modifier.size(5.dp).background(Color(0x80502828), CircleShape))
            }
        }
    }
}
```

- [ ] **Step 2: 구현 — Tweaks/설정/App**

`Tweaks.kt` — 필드 추가 (import `today.superb.jvl.ui.bezel.BezelStyle`):

```kotlin
    val phosphorDecay: Float = 1f,    // [0.3, 4] s
    /** 메인 베젤 하우징(데모 Housing 라디오 1:1). */
    val bezel: BezelStyle = BezelStyle.Military,
```

`SettingsPanel.kt` — DISPLAY 섹션 끝(CRT shader 토글 아래)에 추가 (import `today.superb.jvl.ui.bezel.BezelStyle`):

```kotlin
            section("BEZEL")
            chipRow("Housing", BezelStyle.entries, tweaks.bezel, { it.name.lowercase() }) { onTweaks(tweaks.copy(bezel = it)) }
```

`App.kt:78` — 스타일 전달:

```kotlin
                            MainBezel(label = bezelLabel, style = tweaks.bezel) {
```

저장 호환: v2 블롭에 `bezel` 키가 없어도 기본값(Military)으로 디코드된다(`ignoreUnknownKeys`/기본값 인코딩 — 추가 마이그레이션 불필요).

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew :shared:jvmTest && ./gradlew :androidApp:assembleDebug`
Expected: SUCCESS (SaveCodecTest의 Tweaks 라운드트립도 무손상).

- [ ] **Step 4: 커밋**

```bash
git add shared/
git commit -m "feat(shared): port military/vintage/minimal bezel housings with settings radio"
```

---

### Task 18: 최종 검증 — 전체 테스트/빌드 + 에뮬레이터 QA

**Files:**
- Create: `docs/screenshots/qa-touch-sfx/` (스크린샷 + README.md)

- [ ] **Step 1: 전체 테스트 + 빌드**

```bash
./gradlew :core:jvmTest :shared:jvmTest :shared:compileCommonMainKotlinMetadata :androidApp:assembleDebug
```

Expected: 전부 SUCCESS, 테스트 실패 0.

- [ ] **Step 2: 전용 에뮬레이터 기동 + 설치**

jvolution 전용 에뮬레이터 사용 (`emu up jvolution` → emulator-5570 — 공유 AVD 금지):

```bash
emu up jvolution
adb -s emulator-5570 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5570 shell am start -n today.superb.jvl/.MainActivity
```

(`MainActivity` FQCN이 다르면 `aapt dump badging` 또는 `adb shell cmd package resolve-activity --brief today.superb.jvl`로 확인.)

- [ ] **Step 3: 터치 시나리오 QA — 각 단계 스크린샷**

`adb -s emulator-5570 exec-out screencap -p > docs/screenshots/qa-touch-sfx/NN-name.png` 로 캡처:

1. `01-chips-sonar.png` — 부팅 직후: 칩 스트립(FEED…TREE)이 터미널 위에 보임.
2. `02-chip-feed-echo.png` — FEED 칩 탭 → 터미널에 `unit@nautilus:~$ feed` 에코 + `NOM NOM` 토스트.
3. `03-creature-tap-ping.png` — 생명체 탭 → 스윕 빔(터미널에는 에코 없음 — 무음 경로 확인).
4. `04-radar-blip-select.png` — RADAR 칩 → 레이더 → 블립 탭 → bond readout 출력 + 타깃 링 + `CHALLENGE <NAME>` 칩 등장.
5. `05-challenge-battle.png` — CHALLENGE 칩 탭 → (수락 시) 전투 진입, 칩이 FLEE만으로 축소.
6. `06-tree-node-tap.png` — TREE 칩 → 노드 탭 → `tree N` 에코 + 세대 상세 출력.
7. `07-alert-overlay-buttons.png` — 도전 수신 대기(피어 AI) 또는 `dnd off` 상태로 대기 → 오버레이의 ACCEPT/DECLINE 버튼 + LINK 점멸.
8. `08-settings-species-bezel.png` — ⚙ CFG → Species 변경(생명체 즉시 변형 = state.species 단일 소스 확인) + Housing vintage/minimal 전환.
9. `09-scold-mood.png` — SCOLD 칩 → mood 라벨 `SCOLDED` 표시(2s 후 복귀).
10. SFX 수동 확인 — 설정 SFX ON 후 FEED/PING/SCOLD에서 비프 발음, OFF 시 무음 (스크린샷 불가 — README에 확인 기록).

- [ ] **Step 4: QA README 작성 + 커밋**

`docs/screenshots/qa-touch-sfx/README.md`에 시나리오별 결과 표 기록 (기존 `qa-v40/README.md` 형식 참조).

```bash
git add docs/screenshots/qa-touch-sfx/
git commit -m "docs: full-touch + sfx QA screenshots"
```

- [ ] **Step 5: 잔여 확인**

- `grep -rn "LocalTweaks" shared/src/commonMain/` — species 용도 잔존 사용이 없는지 (CrtLayers/DotCreatureCanvas의 crt/pulse/decay 용도는 정상).
- `git status` 클린 확인.

---

## Self-Review 결과 (스펙 대비)

| 스펙 요구 | Task |
|---|---|
| 터치=터미널 매크로 (ping만 무음) | 10–15 (11에서 ping 무음) |
| 칩 스트립 + chipsFor 순수 함수 | 9, 10 |
| 생명체 탭/롱프레스 | 11 |
| 블립 탭→bond+CHALLENGE 칩 2단계 | 12 |
| 트리 노드 탭 + `tree <gen>` 진짜 명령 | 3, 13 |
| 오버레이 ACCEPT/DECLINE | 14 |
| LINK 실기능 (접점 수·점멸·scan) | 15 |
| SFX 합성/재생/cue/게이트 | 6, 7, 8 |
| SCOLDED transient 수리 | 1, 5 |
| HP 플래시 (flashNonce 소비) | 16 |
| species 단일화 + 저장 마이그레이션 | 2, 4 |
| 베젤 하우징 3종 | 17 |
| help 데모 패리티 (보너스 — 가짜 문서 수리) | 3 |
| 에뮬레이터 QA | 18 |
