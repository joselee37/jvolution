# 3차 마일스톤 — 전투(RPS) Android 동작 검증 (2026-06-08)

전용 에뮬레이터 `emulator-5570`(AVD `jvolution_01`, `emu up jvolution`)에서 `:androidApp:assembleDebug`
APK를 설치·실행하고 전투 진입·턴 사이클·이탈을 adb로 검증한 기록.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-battle.png` | `challenge hrrk` — 성격별 수락 확률(aggressive .85) → 전투 진입. 베젤 `ENGAGEMENT · CH.07 · R.01`, HP 바(5/5·5/5), `▸ SELECT ACTION`, 4액션 메뉴, 터미널 "HRRK accepts. ENGAGE." |
| `02-cast.png` | CHARGE 커밋 → `MORSE → CHARGE` 캐스트 오버레이, 양측 도트 생명체, 메뉴 비활성(choose 외) |
| `03-outcome.png` | 결과 적용 — `CLASH · both charge — shockwave between`(charge×charge), HP 양측 4/5, 턴 `R.02`, 메뉴 재활성 |
| `04-flee-return.png` | `flee` — "disengaging — pulse withdrawn." → 전투 종료, 소나 화면 복귀 |

검증된 데이터 흐름: `challenge`/`accept` → `BattleStart` → BattleScreen. 액션 커밋 → reducer가
`resolveBattleTurn`(16칸 매트릭스 + 데미지 배수 + 5% RESONANCE crit) + `pickNpcMove`(성격 분포 /
veteran read&react)로 무브·결과 산정 → GameViewModel 스케줄러가 myCast(0.7s)→theirCast(0.7s)→
reveal(0.7s)→damage(0.5s)→choose|end(1.8s)로 자동 진행 → HP/턴 갱신. 전투 중 터미널 명령 잠금
(`flee`/`help`/`dnd` 등만 허용), 전투 중 DND 강제 on, 종료 시 보상/페널티 + 소나 자동 복귀.

미검증: KO까지 풀 대전(다턴), 정교한 파형 클래시 캔버스·카메라 팬(cosmetic, 후속 시각 다듬기),
iOS(macOS/Xcode 필요).
