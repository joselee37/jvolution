# 4차 마일스톤 — 계보/트리 + reset Android 동작 검증 (2026-06-08)

전용 에뮬레이터 `emulator-5570`(`emu up jvolution`)에서 계보 화면·세대 리셋을 adb로 검증한 기록.

| 스크린샷 | 검증 내용 |
|---|---|
| `01-tree.png` | `tree` — 계보 화면 전환. 베젤 `LINEAGE-ARCHIVE · G01`, GENESIS/ 루트 + `└── G01_KAIJU/ ◀ ACTIVE`(stage/cycles/happiness/bond/hatched/● alive), 푸터 "1 directory, 0 cycles total" |
| `02-reset-lineage.png` | `reset` — 현 개체 아카이브 + 새 알. 베젤 `G02`, `├── G01_KAIJU/`(dim, ✟ retired, 경과시간) + `└── G02_*/ ◀ ACTIVE`, NODES 2. 터미널 "◢◤ NEW EGG INCUBATING ◢◤" (동시에 BLINK 도전이 떠 적색 경보 오버레이가 겹침) |

검증된 데이터 흐름: `tree` → `SetView(Tree)` → TreeScreen(리눅스 tree 스타일, 현 세대 라이브/은퇴 dim).
`reset` → ViewModel이 NAMES 풀·nowMillis 스탬프 → `Action.Reset` → 현 개체를 `LineageEntry` 비석으로
아카이브(스탯% 스냅샷 + hatchedAt/archivedAt), gen+1, 새 알(피어/유대/전적 보존). 상대시간은
`nowMillis()` 기반("Xs/m/h ago").

미검증: 다수 세대 누적 트리 스크롤, iOS.
