package today.superb.jvl.core.terminal

/**
 * 터미널의 고정 프레젠테이션 카피(도움말·운영자 정보·플레이버 텍스트).
 *
 * 도메인 로직(어떤 명령이 어떤 게임 효과를 내는가)과 분리해 둔다 — i18n·테마·워치(축약 카피)
 * 도입 시 [TerminalResponder] 로직을 건드리지 않고 이 파일만 교체/번역하면 된다.
 */

internal val HELP_LINES = listOf(
    "AVAILABLE COMMANDS:",
    "  status        — vitals readout (boxed)",
    "  scan / peers  — radar sweep + nearby unit list",
    "  ping          — sonar pulse on current view",
    "  tree [gen]    — lineage archive / generation detail",
    "  sonar / back  — return to sonar view",
    "  bond <name>   — peer-bond + battle record",
    "  challenge <n> — challenge nearby unit to battle",
    "  accept        — accept incoming request",
    "  decline       — dismiss incoming request",
    "  dnd [on|off]  — block incoming challenges (auto on in battle)",
    "  flee          — disengage from current battle",
    "  feed [item]   — feed creature",
    "  play          — happiness +",
    "  clean         — hygiene +",
    "  sleep / wake  — toggle sleep",
    "  train         — training +",
    "  scold         — discipline +",
    "  heal          — apply biopatch",
    "  evolve        — advance stage",
    "  talk          — converse",
    "  mute          — toggle audio",
    "  name <str>    — rename unit",
    "  whoami        — operator info",
    "  history       — show log",
    "  clear         — clear screen",
    "  reset         — archive + new egg",
)

internal val WHOAMI_LINES = listOf(
    "OPERATOR_ID: NAUTILUS-7",
    "CLEARANCE: TIER 3 — CARETAKER",
    "STATION: CIC // ABYSSAL OBSERVATION POST",
)

internal val NOTES_LINES = listOf(
    "— field notes —",
    "subj. responds to pings between 3-7s intervals.",
    "avoids glare from main reflector. likes warm currents.",
    "last molt produced 3.4g organic dust.",
)

internal val LS_LINES = listOf("./bin/", "./log/", "./unit.dat", "./telemetry.dat", "./notes.txt")
