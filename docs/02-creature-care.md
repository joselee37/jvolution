# 02 — 생명체·케어·스탯 시스템 (Creature & Care)

## 스탯 정의 (Stats)

생명체는 다음 스탯을 가진다. 표시용 스탯(`evolveProgress` 등 일부)을
제외한 모든 케어 스탯은 0~1 범위로 관리된다.

| 스탯 | 영어 | 초기값 | 범위 | 방향성 / 의미 |
|------|------|--------|------|---------------|
| 행복도 | happiness | 0.6 | 0~1 | 높을수록 행복. 낮으면 무드 라벨 `UNHAPPY`. |
| 에너지 | energy | 0.7 | 0~1 | 높을수록 활기참. 낮으면 `DROWSY`. |
| 허기 | hunger | 0.45 | 0~1 | **0 = 배부름, 1 = 굶주림.** 높으면 `HUNGRY`. |
| 오염도 | dirty | 0.3 | 0~1 | **0 = 깨끗함, 1 = 더러움.** 높으면 `DISTRESSED`. |
| 유대 | bond | 0.4 | 0~1 | 생명체와 운영자 사이의 친밀도. |
| 훈련도 | training | 0.1 | 0~1 | 전투 공격력 배수에 반영(상세 [04](./04-battle.md)). |
| 규율 | discipline | 0.2 | 0~1 | 전투 방어력 배수에 반영. |
| 진화 진척도 | evolveProgress | 0 | 0~1 | 1 도달 시 진화 가능. |

추가 상태값:

- `asleep` — 수면 여부(boolean). 초기값 `false`. 시간 드리프트 방식을 바꾼다.
- `stage` — 진화 단계. 초기값 `egg`.
- `canEvolve` — 진화 가능 여부. `evolveProgress ≥ 1` 이고 마지막 단계가
  아닐 때 `true`.

> **표시 규칙:** 터미널 `status`와 소나 게이지는 `hunger`·`dirty`를 사용자
> 친화적으로 뒤집어 보여준다 — "fed = 1 − hunger", "clean = 1 − dirty".
> 즉 게이지가 가득 차 있으면 배부르고 깨끗한 상태다.

## 케어 액션 (Care Actions)

플레이어는 터미널 명령어로 케어 액션을 수행한다. **모든 케어 액션은 케어
사이클(care-cycle) 카운터를 1 증가시킨다.** 각 액션의 스탯 증감은 다음과
같다(데모에서 추출한 디자인 값).

| 액션 | 명령어 | 효과 | 부작용 | 피드백 토스트 |
|------|--------|------|--------|---------------|
| 먹이기 (feed) | `feed` | hunger −0.25 | dirty +0.03 | `NOM NOM` |
| 놀기 (play) | `play` | happiness +0.20, bond +0.04 | energy −0.08, hunger +0.04 | `YIPPEE` |
| 청소 (clean) | `clean` | dirty → 0 (완전 초기화), happiness +0.05 | 없음 | `TANK FLUSHED` |
| 수면 (sleep) | `sleep`, `wake` | `asleep` 토글 | 없음 | `GOOD NIGHT` / `AWAKE` |
| 훈련 (train) | `train` | training +0.15, discipline +0.05, evolveProgress +0.04 | energy −0.08, hunger +0.04 | `DRILL OK` |
| 훈육 (scold) | `scold` | discipline +0.10 | happiness −0.08, bond −0.02 | `SCOLDED` |
| 치료 (heal) | `heal` | energy +0.30, happiness +0.05 | 없음 | `PATCHED` |
| 핑 (ping) | `ping` | bond +0.03, 소나 펄스 트리거 | 없음 | 없음 |

규칙 메모:

- 모든 스탯 변화는 0~1 범위로 클램프(clamp)된다.
- `feed`의 부수 효과로 `dirty`가 소량 오르므로, 먹이기를 반복하면 청소가
  필요해진다.
- `clean`은 오염도를 감소가 아니라 **0으로 완전 초기화**한다.
- `play`와 `train`은 에너지·허기를 소모하므로, 활동 후에는 `heal`/`feed`
  로 보충해야 한다.
- `train`만이 케어 액션으로 진화 진척도를 직접 올린다(+0.04).
- `ping`은 케어 사이클을 올리고 유대를 살짝 높이며, 소나 화면의 펄스
  스윕(sweep) 연출을 발동시킨다(상세 [06](./06-ui-visual.md)).
- 피드백 토스트(toast)는 화면에 약 1.4초간 떠 있다가 사라진다.

## 시간 경과 드리프트 (Passive Drift)

생명체는 케어 틱(care tick)마다 스탯이 서서히 변한다. 케어 틱은 약 1.5초
주기로 발생한다. 드리프트는 각성/수면 상태에 따라 다르게 적용된다.

**각성(awake) 상태 — 틱당 변화:**

| 스탯 | 변화 | 비고 |
|------|------|------|
| hunger | +0.012 | 점점 배고파짐 |
| dirty | +0.008 | 점점 더러워짐 |
| energy | −0.010 | 점점 지침 |
| happiness | −0.015 | **단, `hunger > 0.7` 또는 `dirty > 0.7`일 때만** |
| evolveProgress | +0.005 | 항상 |

**수면(asleep) 상태 — 틱당 변화:**

| 스탯 | 변화 | 비고 |
|------|------|------|
| energy | +0.020 | 잠으로 회복 |
| hunger | +0.005 | 천천히 배고파짐 |
| evolveProgress | +0.005 | 항상 |

규칙 메모:

- 진화 진척도는 각성/수면과 무관하게 매 틱 +0.005씩 누적된다 — 즉
  생명체는 보살핌 없이도 천천히 성장한다(`train`은 이를 가속).
- 행복도 감소는 굶주림 또는 오염도가 임계(0.7)를 넘었을 때만 발생한다.
  잘 관리된 생명체는 시간만으로 불행해지지 않는다.
- 수면 중에는 더러워짐·지침이 멈추고 에너지가 회복된다. 수면은 회복
  수단이다.

## 진화 (Evolution)

생명체는 4개의 진화 단계(stage)를 순서대로 거친다.

```
egg ──→ larva ──→ juvenile ──→ adult
(알)    (유생)     (유체)        (성체)
```

- 진화 조건: `evolveProgress ≥ 1` 그리고 현재 단계가 마지막(adult)이 아님.
  이때 `canEvolve`가 `true`가 된다.
- 진화 실행: 조건을 만족한 상태에서 `evolve` 명령을 입력하면 진화 시퀀스가
  시작된다.
- 진화 연출: 약 **2.2초**간 `EVOLVING…` 연출이 재생된 뒤 다음 단계로
  전환된다.
- 진화 완료 시: `stage`가 다음 단계로 바뀌고, `evolveProgress`는 0으로
  초기화되며, 케어 사이클이 1 증가한다.
- adult 단계에서는 더 이상 진화할 수 없다.

## 종 (Species)

생명체는 5종의 실루엣(silhouette) 중 하나로 렌더링된다. 종은 설정
패널(tweaks)에서 선택하며, 기본값은 `ghost`다.

| 종 | 영어 | 실루엣 특징 |
|----|------|-------------|
| 고스트 | ghost | 팩맨풍 — 둥근 돔 머리, 아래쪽 4갈래 톱니, 음각(negative-space) 눈에 천천히 움직이는 밝은 동공. |
| 블롭 | blob | 반원형 덩어리 — 오른쪽으로 부푼 곡면, 평평한 앞면(스캔 전선), 음각 눈 2개. |
| 젤리 | jelly | 해파리 — 왼쪽 종(bell) 모양 머리에 능선, 오른쪽으로 흔들리는 촉수 5가닥. |
| 스퀴드 | squid | 오징어 — 위쪽 뾰족한 외투막, 음각 눈 2개, 아래로 흔들리는 다리 7가닥. |
| 픽셀 | pixel | 8비트 — 12×12 비트맵으로 그려지는 도트 생명체. |

**렌더 컨셉(구현 비종속):** 각 종은 좌표 `(x, y)`를 입력받아 밀도(density,
0~1)를 돌려주는 모양 함수(shape function)로 정의된다. 화면은 이 밀도를
도트 그리드(dot grid)로 표본화하고, 소나 펄스의 스윕이 지나간 위치만
밝게 켰다가 포스퍼 감쇠(phosphor decay)로 어둡게 식힌다. 그 결과 생명체는
"빔이 최근 지나간 자리"에서만 보인다. 에너지·기분 등 무드 값이 모양
함수에 입력되어 호흡·촉수 흔들림 같은 미세 움직임을 만든다. 수면 중에는
전체 밝기가 낮아진다.

스윕·감쇠의 속도는 설정 패널의 펄스 주기(pulse period)·포스퍼 감쇠
(phosphor decay)로 조절된다([06](./06-ui-visual.md) 참조).

## 무드 라벨 (Mood Label)

소나 화면 상단에는 생명체의 현재 무드를 한 단어로 표시한다. 여러 조건이
동시에 참일 수 있으므로 **우선순위가 높은 것 하나만** 표시한다.

| 우선순위 | 라벨 | 조건 |
|---------|------|------|
| 1 | `ASLEEP` | 수면 중 |
| 2 | `EVOLVING` | 진화 시퀀스 진행 중 |
| 3 | `SCOLDED` | 훈육 직후 플래시 상태 |
| 4 | `DISTRESSED` | `dirty > 0.7` |
| 5 | `HUNGRY` | `hunger > 0.75` |
| 6 | `UNHAPPY` | `happiness < 0.3` |
| 7 | `DROWSY` | `energy < 0.25` |
| 8 | `NOMINAL` | 위 어느 것에도 해당하지 않음(기본) |

> **데모 현황 메모:** 현재 데모에서 우선순위 3 `SCOLDED` 라벨을 띄우는
> 플래시 상태(`disciplineFlash`)는 `scold` 액션에서 켜지지 않는다 — 즉
> 무드 라벨로서의 `SCOLDED`는 사실상 표시되지 않는다(`scold`의 토스트
> 피드백 `SCOLDED`와는 별개). As-is 기준으로 기록한다.

## 생명체 대사 (Talk Pool)

`talk` 명령을 입력하면 생명체가 현재 상태에 맞는 대사를 한 줄 말한다.
상태별 트리거 대사가 일반 대사보다 우선한다.

| 우선순위 | 트리거 조건 | 대사 |
|---------|-------------|------|
| 1 | 수면 중 | `zzZZ... (do not disturb)` |
| 2 | `hunger > 0.7` | `i hear something... is that food? i hope so.` |
| 3 | `dirty > 0.7` | `the water is murky. could you flush the tank?` |
| 4 | `happiness < 0.3` | `it has been a long shift. i miss you.` |
| 5 | `energy < 0.3` | `tired... maybe a quick rest?` |
| 6 | `stage == egg` | `tap... tap... tap... [muffled]` |

위 트리거가 모두 거짓이면 다음 일반 대사 풀(pool)에서 무작위로 한 줄을
고른다.

- `do you ever wonder where the signal goes?`
- `i counted 1,440 pings today. i counted them all.`
- `the reef hums at 27 hertz. i hum back.`
- `i think i saw a shape. it had nine sides.`
- `is the operator there? i sensed you above.`
- `thank you for staying.`

## 계보 (Lineage)

세대가 리셋되면 현재 생명체는 비석(epitaph) 한 건으로 계보 아카이브에
보존되고, 새 알(egg)로 다음 세대가 시작된다.

**비석에 보존되는 데이터:**

| 항목 | 내용 |
|------|------|
| gen | 세대 번호 |
| name | 개체 이름 |
| stage | 마지막 진화 단계 |
| cycles | 누적 케어 사이클 |
| happiness | 마지막 행복도(%) |
| energy | 마지막 에너지(%) |
| bond | 마지막 유대(%) |
| discipline | 마지막 규율(%) |
| training | 마지막 훈련도(%) |
| hatchedAt | 부화 시각 |
| archivedAt | 아카이브(리셋) 시각 |

**리셋 시 동작:**

- 새 세대의 세대 번호 = 직전 세대 + 1.
- 생명체 스탯·단계·이름은 모두 초기값으로 재설정된다(새 이름은 이름
  풀에서 무작위 선택: `NAUTI`, `KAIJU`, `BLEEP`, `MORSE`, `PROBE`, `KRILL`).
- 피어(NPC) 로스터·유대·전적·진행 중인 요청은 생명체와 독립적이므로
  리셋의 영향을 받지 않고 그대로 유지된다.

**계보 화면 표시 규칙(`tree` 명령):**

- 계보는 Linux `tree` 명령 스타일의 디렉터리 트리로 그려진다. 루트는
  `GENESIS/`이며, 각 세대는 그 자식 노드(`Gnn_NAME/`)다.
- 현재 활동 중인 세대(active)는 밝게 강조되고 `◀ ACTIVE` 표시와 함께
  실시간 무드·유대·사이클을 보여주며 상태는 `● alive`.
- 은퇴한(retired) 세대는 흐린 색으로 렌더되며 상태는 `✟ retired`, 마지막
  무드·유대·아카이브 경과 시간을 보여준다.
- 하단에 총 디렉터리 수와 누적 사이클 합계를 표시한다.
