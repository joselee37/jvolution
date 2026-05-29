# 1차 마일스톤 — Android 동작 검증 (2026-05-30)

`emulator-5554`(API 35)에서 `:androidApp:assembleDebug` APK를 설치·실행하고 PLAN.md
수동 체크리스트를 adb(스크린샷 + 입력 주입)로 검증한 기록.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-device-frame.png` | DeviceFrame 레이아웃(header/베젤/터미널) + 도트 ghost 생명체 + 부팅 배너 |
| `02-status-readout.png` | `status` — 박스형 readout(막대·퍼센트·섹션·`◀ READY` 마커) |
| `03-feed-toast.png` | `feed` — "NOM NOM" 토스트 배너(1.4s 후 ClearToast) |
| `04-ping-sweep.png` | `ping` — 생명체에 좌→우 밝기 sweep(pingNonce frame-state) |
| `05-sleep-mood.png` | `sleep` — mood `ASLEEP` 전이 + "GOOD NIGHT" + 터미널 라인 색상 구분 |

검증된 데이터 흐름: 터미널 입력 → parse → respond → {lines, action} → dispatch →
reduce → StateFlow → Compose 재구성. care-tick 드리프트, 토스트 타이머, ping sweep,
mood 우선순위(NOMINAL→HUNGRY→DISTRESSED→ASLEEP→복귀) 전부 실기 확인.

미검증: `↑↓` 명령 히스토리(하드웨어 키보드 전용), iOS(macOS/Xcode 필요).
