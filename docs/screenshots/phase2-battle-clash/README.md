# phase-2 — 전투 클래시 연출 + 카메라 팬 (2026-06-09)

전투를 데모 수준의 시네마틱 연출로 다듬었다. 기존 전투 UI(HP 바/메뉴/라벨)는 기능적이었지만 정적
이었는데, 측면 스크롤 아레나 + 카메라 팬 + 파형 클래시 오버레이를 추가했다(순수 Compose Canvas).

| 스크린샷 | 검증 내용 |
|---|---|
| `01-cast-camera-me.png` | myCast — 카메라가 **플레이어 생명체로 포커스**, `NAUTI → CHARGE` + cast 시그니처 |
| `02-camera-them.png` | theirCast — 카메라가 **상대(HRRK)로 팬** (두 생명체가 동시에 중앙에 오지 않음) |
| `03-clash-damage.png` | reveal — `▸ RESOLVING…`, 양측 무브 시그니처가 **중앙으로 수렴**, 카메라 중앙 |

구현: `BattleClash` 캔버스(데모 `BattleClash`+`drawSignature` 포팅) — 무브별 시그니처(ping 동심
아크 / charge 화살촉+잔상 / dodge 점선 원 / screech 톱니) + 충돌 플래시 + crit 충격파 링.
`BattleScreen`은 `BoxWithConstraints` 넓은 아레나(1.8×) + `animateDpAsState` 카메라 오프셋으로
페이즈별 포커스(choose/myCast→나, theirCast→상대, reveal→중앙, damage→피격 측). 페이즈 진행은
GameViewModel 스케줄러가 구동(reducer는 순수 유지).

주: 충돌/임팩트 플래시는 짧아(reveal 0.7s 끝 + damage 0.5s) headless 에뮬레이터 screencap으로는
정확한 순간 포착이 어렵다(빠른 버스트는 swiftshader를 불안정하게 함). 카메라 팬·시그니처·RESOLVING
흐름은 위 프레임으로 확인. 남은 phase-2: CRT 셰이더(AGSL/SkSL).
