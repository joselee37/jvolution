# QA — 풀 터치 레이어 + 실제 SFX (feature/full-touch-sfx)

> 환경: 전용 에뮬레이터 `jvolution_01`(emulator-5570), Pixel급 1080×2400.
> 빌드: `:core:jvmTest` `:shared:jvmTest` `:shared:compileCommonMainKotlinMetadata` `:androidApp:assembleDebug` 전부 통과 후 설치.
> 스펙: `docs/superpowers/specs/2026-06-10-full-touch-layer-design.md`

| # | 스크린샷 | 시나리오 | 결과 |
|---|---|---|---|
| 01 | `01-chips-sonar.png` | 부팅 — 칩 스트립(FEED…RADAR) + `◉ LINK·7` 접점 수 + military 베젤 | ✅ |
| 02 | `02-chip-feed-echo.png` | FEED 칩 탭 → 터미널 `$ feed` 에코 + "unit fed. hunger -25%." | ✅ 매크로 경로 |
| 03 | `03-creature-tap-ping.png` | 생명체 탭 → 스윕 점등, 터미널에 **에코 없음** | ✅ 무음 ping 경로 |
| 04a | `04a-radar.png` | RADAR 칩 → 레이더 전환 + 피어 목록 출력 + 칩이 BACK 선두로 변경 | ✅ 컨텍스트 칩 |
| 04 | `04-radar-blip-select.png` | 블립 탭 → 타깃 링 + `bond arc-9` 자동 조회 + `CHALLENGE ARC-9` 칩 등장 | ✅ 2단계 도전 |
| 05 | `05-challenge-battle.png` | CHALLENGE 칩 탭 → "ARC-9 accepts. ENGAGE." → 전투 진입, 칩 **FLEE만** | ✅ 전투 잠금 일치 |
| 05b/c | `05b…/05c-hp-flash.png` | CHARGE 커밋 → CLASH → R.02 진입, 양측 HP 차감 | ✅ (420ms 플래시는 정지 캡처 한계 — 코드 리뷰로 검증) |
| 06a~c | `06a…/06c-tree-view.png` | FLEE 복귀 → TREE 칩(가로 스크롤) → 계보 화면 + 탭 힌트 | ✅ |
| 06 | `06-tree-node-tap.png` | G01 노드 헤더 탭 → `$ tree 1` 에코 + 세대 상세 readout | ✅ 신규 명령 |
| 07 | `07-squid-vintage.png` | 설정 squid 선택 후 ping → **squid 실루엣** 이 **vintage 하우징** 안에서 점등 | ✅ species 단일 소스 |
| 07b | (미캡처) | 피어 도전 수신 → 오버레이 ACCEPT/DECLINE 버튼 + LINK 점멸 | ⚠️ RNG 발동 ~30분 미수신으로 캡처 생략 — 버튼은 칩과 동일한 `submitCommand` 매크로(2단계 리뷰 통과), 칩 경로는 02/04/05에서 실증 |
| 08 | `08-persist-restored.png` | 에뮬레이터 재시작 → **LARVA 단계 복원** (save v2) | ✅ |
| 08b/c | `08b…/08c-settings-changed.png` | ⚙ CFG → BEZEL Housing 라디오(신규)·Species·SFX 토글 전부 즉시 반영 | ✅ |
| 09 | `09-scold-mood.png` | SCOLD 칩 → 무드 라벨 **SCOLDED** + 토스트 (수리된 transient) | ✅ 데모 버그 수리 |
| 09b | `09b-evolve-chip.png` | canEvolve 시 ★EVOLVE 강조 칩 → 탭 → EVOLVING 시퀀스 → LARVA 전이 | ✅ |

## SFX 수동 확인

- 설정 SFX **ON** 토글 시 Confirm 비프 발음(에뮬레이터 호스트 오디오), 이후 FEED/PING/SCOLD에서 각기 다른 레트로 톤 확인. OFF 시 전부 무음. 스크린샷 불가 — 청취 확인으로 기록.

## 알려진 한계 / 후속

- `SfxPlayer.ios.kt`(AVAudioEngine)는 Linux에서 K/N 컴파일 불가 — **macOS에서 `:shared:compileKotlinIosArm64` + 시뮬레이터 스모크 필요** (인터럽션 후 SFX 재생 포함; 바인딩 라벨 의심 지점은 커밋 메시지/리뷰 기록 참조).
- HP 피격 플래시(420ms)는 정지 캡처로 시각 증빙 불가 — `BattleScreen.kt` LaunchedEffect 배선은 리뷰로 검증.
