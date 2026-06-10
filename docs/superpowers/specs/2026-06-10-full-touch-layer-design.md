# 풀 터치 레이어 + 실제 SFX — "모든 구성요소가 진짜로 동작하는 앱"

> **상태:** 설계 승인됨 (2026-06-10, 대화형 brainstorming 세션).
> **목표:** 데모 1:1 포팅 수준을 넘어, 화면에 보이는 모든 요소가 실제로 조작 가능하고
> 모든 토글/표시가 실제 기능에 연결된 앱으로 만든다. 가짜 UI 금지.

## 배경 — 현황 격차

6차 마일스톤 + phase-2(셰이더·전투 연출) + persistence까지 완료된 상태.
게임 로직(케어/피어/전투/계보/저장)과 터미널 명령 30여 종은 전부 실제 동작한다.
남은 격차는 두 종류다:

**① 가짜/장식 요소 (표시만 되고 동작 안 함)**

| 요소 | 현재 상태 |
|---|---|
| 설정 SFX 토글 | `state.sound` 플래그만 토글 — 재생 코드가 존재하지 않음 |
| 헤더 `◉ LINK` | 순수 장식 (`App.kt`) |
| HP 피격 플래시 | reducer가 `flashNonceMe/Them`을 올리지만 `BattleScreen`이 읽지 않음 |
| `SCOLDED` 무드 | `disciplineFlash`가 영원히 false — 도달 불가 (데모 버그 보존분) |
| 베젤 하우징 | 데모 설정엔 military/vintage/minimal 3종, 앱엔 1종 고정 |
| species 이원화 | `GameState.species`는 미사용, 렌더는 `Tweaks.species`만 사용 (치트성 분리) |

**② 보이지만 만질 수 없는 요소 (모바일인데 터미널 타이핑 전용)**

- 케어 액션 전부 (feed/play/clean/sleep/train/scold/heal/ping/evolve)
- 화면 전환 (sonar↔radar↔tree)
- 도전 경보 오버레이 — `accept`/`decline` 타이핑 필요, 오버레이 자체는 터치 불가
- 레이더 블립·생명체·계보 노드 — 탭 무반응
- 터미널 `↑↓` 명령 히스토리 — 소프트 키보드에 방향키가 없어 모바일에서 사용 불가

## 승인된 핵심 결정

1. **풀 터치 레이어** — 보이는 모든 요소가 터치에 반응한다.
2. **실제 SFX 구현** — expect/actual 플레이어 + 코드 합성 레트로 톤.
3. **터치 = 터미널 매크로** — 터치가 터미널 명령을 대신 타이핑한다 (단일 경로).
4. **케어 버튼 = 터미널 명령 칩 스트립** — 터미널 입력줄 위 가로 스크롤 한 줄, 컨텍스트 인식.

## 설계

### 1. 터치 = 터미널 매크로 (단일 경로 원칙)

모든 터치 조작은 `vm.submitCommand("feed")`처럼 **기존 터미널 파이프라인을 그대로 탄다**:
명령이 터미널에 에코되고(`in` 라인) 응답이 출력되며(`out` 라인), 액션이 reduce로 흐른다.

- 전투 중 명령 잠금(`allowedInBattle`), DND, sleep/wake 멱등 처리 등 **기존 가드가 자동 적용**된다.
- 터치와 타이핑의 동작이 구조적으로 영원히 일치한다.
- 터치가 명령어를 보여주므로 학습 효과가 있다 (게임 정체성인 터미널 미학 강화).

**유일한 예외 — 생명체 탭(ping):** 빈번한 조작이라 매번 에코하면 로그 스팸이 된다.
`dispatch(Action.Ping)` 직접 호출(무음 경로)로 처리한다. 그 외 모든 터치는 매크로.

### 2. 명령 칩 스트립 (`:shared` ui/chips/)

터미널 입력줄 바로 위 ~32dp 가로 스크롤 한 줄. 탭하면 해당 명령을 submitCommand.

- 칩 목록은 **순수 함수 `chipsFor(state: GameState): List<CommandChip>`** 가 결정한다
  (commonTest로 단위 테스트). `CommandChip(label, command, emphasis)`.
- 컨텍스트 규칙:

| 상황 | 칩 (앞에서부터) |
|---|---|
| 소나 평시 | `FEED` `PLAY` `CLEAN` `TRAIN` `HEAL` `SLEEP` `SCOLD` `RADAR` `TREE` |
| 수면 중 | `WAKE`가 맨 앞, 케어 칩은 유지 (멱등 가드가 응답) |
| `canEvolve` | `★EVOLVE`가 맨 앞에 강조 색 |
| 도전 수신 (`pendingRequest != null`) | `ACCEPT` `DECLINE`이 맨 앞에 경고색, 이후 평시 칩 |
| 전투 중 | `FLEE`만 (responder의 `allowedInBattle`과 일치) |
| 레이더 | `BACK` + (블립 선택 시) `CHALLENGE <NAME>` |
| 트리 | `BACK` |

- 모바일에서 사라진 `↑↓` 명령 히스토리의 실질적 대체 수단.

### 3. 화면별 터치 매핑

| 화면 | 제스처 | 동작 |
|---|---|---|
| 소나 | 생명체 영역 탭 | `dispatch(Action.Ping)` — 무음, 스윕+bond (데모 ping과 동일 효과) |
| 소나 | 생명체 롱프레스 | `submitCommand("talk")` 매크로 |
| 레이더 | 블립 탭 | `submitCommand("bond <name>")` + 블립 선택 하이라이트 + 칩 스트립에 `CHALLENGE <NAME>` 칩 등장 |
| 레이더 | 빈 영역 탭 | 선택 해제 |
| 트리 | 세대 노드 탭 | `submitCommand("tree <gen>")` 매크로 |
| 경보 오버레이 | `[ACCEPT]` / `[DECLINE]` 버튼 | 각각 `submitCommand("accept")` / `("decline")` |
| 헤더 | `◉ LINK` 탭 | `submitCommand("scan")` 매크로 (레이더 전환) |
| 전투 | 액션 칩 탭 | 기존 동작 유지 (이미 구현됨) |

- 레이더 블립 도전은 **탭(정보) → 칩(도전)의 2단계**라 실수 도전을 방지한다.
- 블립 히트 영역은 최소 44dp (가장 가까운 블립 매칭).
- 블립 선택 상태는 RadarScreen local `remember` (프레젠테이션 상태, GameState 밖).

### 4. `tree <gen>` 명령 (`:core` 확장)

노드 탭 매크로를 위해 **타이핑으로도 동작하는 진짜 명령**으로 추가한다:

- `TerminalCommand.Tree`: `data object` → `data class Tree(val gen: Int?)`.
- `tree` (인자 없음) → 기존처럼 계보 화면 전환.
- `tree 2` → G02 세대 상세를 터미널에 출력: 이름/종/단계/도달 사이클/최종 무드/bond/아카이브 경과.
  활성 세대 번호면 현재 스탯 요약. 없는 세대면 `no such generation` 응답.
- 파서·responder·`help` 목록 갱신 + commonTest.

### 5. 실제 SFX (`:shared` sound/)

**합성 (commonMain, 순수):**

- `enum class Sfx(val tones: List<Tone>)` — `Tone(freqHz: Float, durMs: Int)` 사각파 시퀀스.
  예: `Ping`(상승 2음), `Feed`(짧은 2음), `Alert`(경고 3음 반복), `Hit`(저음 노이즈성 단음),
  `Crit`, `Evolve`(아르페지오), `Win`/`Lose`, `Scold`(하강 2음) 등 — 8-bit/CRT 미학에 맞는 톤.
- `SfxSynth.render(sfx, sampleRate): ShortArray` — PCM 16-bit mono 합성, **순수 함수**
  (어택/릴리즈 짧은 엔벨로프 포함, 클릭 노이즈 방지). commonTest로 샘플 수·진폭 범위 검증.

**재생 (expect/actual):**

```kotlin
expect class SfxPlayer() {
    fun play(sfx: Sfx)   // 사전 합성 버퍼 캐시, fire-and-forget
    fun dispose()
}
```

- Android: `AudioTrack` (static mode, 버퍼 캐시). iOS: `AVAudioEngine` + `AVAudioPlayerNode`.
  JVM: `javax.sound.sampled.Clip`. 재생 실패는 무해하게 무시(게임 진행 차단 금지), 단 디버그 로그.

**트리거 (GameViewModel):**

- **순수 함수 `sfxCueFor(before: GameState, after: GameState): Sfx?`** 가 상태 전이를 판정
  (commonTest 대상). `dispatch()`의 기존 before/after 지점(`maybeScheduleBattlePhase` 패턴)에 삽입.
- 판정 규칙: `pingNonce` 증가→Ping, `toast` 신규 등장→토스트 종류별 매핑, `pendingRequest`
  null→nonnull→Alert, `flashNonce*` 증가→Hit/Crit, `evolving` false→true→Evolve,
  battle result 등장→Win/Lose/Flee 등.
- `after.sound == false`면 재생하지 않는다 — **SFX 토글이 진짜 기능이 된다.**
- reducer는 끝까지 순수 (사운드는 ViewModel 부수효과 계층).

### 6. 가짜 요소 수리

| 요소 | 수리 |
|---|---|
| `◉ LINK` | `◉ LINK·{peers.size}` 접점 수 표시. `pendingRequest` 활성 시 점멸(경고색). 탭 → `scan` 매크로 |
| `SCOLDED` 무드 | `Action.Discipline`이 `disciplineFlash=true` 설정 + `Action.ClearDisciplineFlash` 신설. ViewModel이 기존 transient 패턴(toast/evolve와 동일)으로 ~2s 후 끔. moodLabel 우선순위는 기존 구현 그대로 동작하게 됨 |
| HP 플래시 | `BattleScreen`이 `flashNonceMe/Them`을 `LaunchedEffect`로 소비 — HP바/생명체 짧은 플래시 |
| species 통합 | `Tweaks.species` 제거. `Action.SetSpecies(species)` 신설 → 설정의 종 선택이 `GameState.species`를 변경. `DotCreatureCanvas`·battle 렌더는 `state.species` 단일 소스. 저장 호환: Tweaks 디코딩 시 미지 필드 무시 정책 확인(SaveCodec) |
| 베젤 하우징 | 데모 `styles.css`의 military/vintage/minimal 3종을 `MainBezel(style)` 분기로 포팅. `Tweaks.bezel` 추가 + 설정 패널 라디오 |

### 7. 테스트 전략

- `:core` commonTest — `SetSpecies`/`ClearDisciplineFlash`/`disciplineFlash` 전이 reducer 테스트,
  `tree <gen>` 파서·responder 테스트.
- `:shared` commonTest — `chipsFor` 컨텍스트 규칙 전체, `sfxCueFor` 전이 판정 전체,
  `SfxSynth.render` PCM 속성(길이·클램프·엔벨로프 경계).
- 기존 테스트 무손상: `:core:jvmTest` + `:shared` 테스트 전체 통과 유지.
- 수동 검증 — 전용 에뮬레이터(emulator-5570)에서 터치 시나리오별 스크린샷
  (칩 탭→에코, 블립 탭→정보+도전 칩, 오버레이 버튼, 노드 탭, LINK 탭, SFX 토글 on/off).

### 스코프 아웃

- 스와이프 화면 전환 (칩 + LINK 탭으로 충분)
- 신규 게임 메커닉 (번식, 다중 진화 경로 등)
- 워치 컴패니언, 점수 동기화
- 음악(BGM) — SFX만
