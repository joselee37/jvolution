# 2차 마일스톤 — 피어 & 레이더 Android 동작 검증 (2026-06-08)

전용 에뮬레이터 `emulator-5570`(AVD `jvolution_01`, API 35, `emu up jvolution`로 기동)에서
`:androidApp:assembleDebug` APK를 설치·실행하고 레이더/피어/경보 흐름을 adb(스크린샷 + 입력
주입)로 검증한 기록. (devtools `emu` 정책 — 프로젝트 전용 AVD, leave-as-found.)

| 스크린샷 | 검증 내용 |
|---|---|
| `01-sonar.png` | 기동 — 소나 화면(녹색 Hue), 베젤 `SONAR-OBS · EGG`, 도트 생명체, 터미널 부팅 배너 |
| `02-radar.png` | `scan` — 레이더 뷰 전환 + 피어 목록(거리순), **ARC-9 challenge 발동** → 적색(Hue.Alert) + PeerAlertOverlay(UNIT/SQUID/ADULT/318°·30m) |
| `03-accept.png` | `accept`(Phase-1 스텁) — "accepted ARC-9's challenge" + `[BATTLE MODULE PENDING]` 에코, friendly(BLINK +5%)·새 challenge(SIFT) 에코, 오버레이 갱신 |
| `04-radar-clean.png` | `dnd on` — 대기 challenge(SIFT) 자동 거절 + challenge 억제 → 녹색 복귀, 깨끗한 레이더 스코프(4링·NESW·스윕 콘·블립 라벨·self 점) |
| `05-sonar-return.png` | `sonar` — 소나 복귀("returning to sonar"). DND 중에도 friendly(NIMBUS +5%)는 정상 발생(challenge만 억제) |

검증된 데이터 흐름: peer-tick 루프 → `Action.PeerTick` → reduce(드리프트·경계반사·6% 게이트·
성격 분기·DND 억제·단일 요청 게이트) → StateFlow → 레이더 Canvas 재구성. `pendingRequest` →
`Hue.Alert` 전체 색조 전환, peer-event nonce → 터미널 자동 에코(challenge=sys, friendly/
accept/decline=out), 터미널 `scan/sonar/bond/accept/decline/dnd` 핸들러 전부 실기 확인.

미검증: 전투(3차 — `accept`는 현재 스텁), iOS(macOS/Xcode 필요).
