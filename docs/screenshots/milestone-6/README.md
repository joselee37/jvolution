# 6차 마일스톤 — 설정 패널 + 사운드 Android 동작 검증 (2026-06-09)

전용 에뮬레이터 `emulator-5570`(`emu up jvolution`)에서 설정 패널·실시간 반영을 adb로 검증한 기록.
헤더 우상단 `⚙ CFG` 액션 버튼으로 패널을 연다(사용자 제안).

| 스크린샷 | 검증 내용 |
|---|---|
| `01-settings.png` | `⚙ CFG` 탭 → 설정 패널. DISPLAY(테마 GREEN/AMBER/BLUE, CRT 강도 슬라이더, 스캔라인/노이즈 토글), CREATURE(종 ghost/blob/jelly/squid/pixel), SONAR(펄스 5.0s/감쇠 1.0s 슬라이더), AUDIO(SFX), CARE(◢◤ HATCH NEW EGG). 시스템 상태바도 정상 표시(인셋 적용) |
| `02-live-amber-squid.png` | 테마 AMBER + 종 squid 선택 후 닫기 → 소나가 즉시 amber 색조 + 오징어 실루엣으로 갱신. `dnd ON` 터미널 토글도 동작 |

검증된 데이터 흐름: `Tweaks`(:shared UI-state, GameViewModel `MutableStateFlow`)를 `LocalTweaks`로
트리에 주입 → JvlTheme hue / CrtLayers(intensity·scanlines·noise) / DotCreatureCanvas(species·
pulse·decay)가 실시간 소비. SFX는 `Action.ToggleSound`(GameState.sound), Hatch egg는 `hatchNewEgg()`
(stamped Reset 재사용). `mute`/`sound` 터미널 명령 추가로 **터미널 전체 패리티 달성**(ModulePending 제거).

**버그 수정:** API 35(Android 15) edge-to-edge 강제로 헤더가 시스템 상태바 아래 그려져 `⚙ CFG`
터치가 안 먹던 문제 → `DeviceFrame`에 `statusBarsPadding()`/`navigationBarsPadding()` 인셋 적용.

미검증: 영속화(@Serializable + DataStore — 별도 후속), 베젤 하우징 변형(vintage/minimal), iOS.
