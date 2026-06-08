# 5차 마일스톤 — 나머지 4종 렌더링 Android 동작 검증 (2026-06-08)

데모 `creature.jsx`의 5종 실루엣 밀도 함수를 모두 포팅(Ghost 외 Blob/Jelly/Squid/Pixel)하고,
`DotCreatureCanvas`를 `species`로 분기하도록 만들었다. 종 선택 UI(설정 패널)는 6차이므로, 비-Ghost
종은 **전투의 상대 생명체**(peer.species)로 화면에 나타난다.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-battle-species.png` | BLINK(pixel) 도전 수락 → 전투. 스윕이 지날 때 우측 상대가 **블록형 pixel 실루엣**으로 렌더(좌측 플레이어는 Ghost) — `densityFor` 종 분기 동작 |

검증된 데이터 흐름: `densityFor(species, u, v, t, happiness, energy)`가 종별 shape 함수로 분기
(Ghost=happiness, Blob/Jelly/Squid=energy, Pixel=고정 비트맵). 전투에서 상대 `energy`에 HP 비율을
넘겨 체력에 따라 위축. SonarScreen은 `state.species`(현재 Ghost 고정 — 종 선택은 6차 설정 패널).

미검증: 플레이어 종 변경(6차 설정 패널 필요), Blob/Jelly/Squid 개별 인게임 노출(전투 상대로 등장 시),
iOS. 종 shape 수학은 `:shared` `SpeciesShapesTest`(6 케이스) + 데모 1:1 대조로 검증.
