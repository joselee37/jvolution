package today.superb.jvl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.PeerEvent
import today.superb.jvl.core.PeerEventKind
import today.superb.jvl.core.PeerRoster
import today.superb.jvl.core.Rng
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.reduce
import today.superb.jvl.core.terminal.TerminalLine
import today.superb.jvl.core.terminal.TerminalLineKind
import today.superb.jvl.core.terminal.parse
import today.superb.jvl.core.terminal.respond
import today.superb.jvl.nowMillis
import kotlin.time.TimeSource

private const val CARE_TICK_MS = 1500L
private const val TOAST_MS = 1400L
private const val EVOLVE_MS = 2200L
private const val TERMINAL_CAP = 200

/** 피어 틱 주기/스텝. 데모처럼 고정 dt — 드리프트는 cosmetic이라 monotonic 보정 불필요. */
private const val PEER_TICK_MS = 1000L
private const val PEER_TICK_DT = 1f

/** 한 틱이 표현하는 최대 경과(초). 백그라운드/도즈 복귀 시 거대한 dt로 스탯이 붕괴하는 것을 방지. */
private const val CARE_DT_MAX = 3f

private val NAMES = listOf("NAUTI", "KAIJU", "BLEEP", "MORSE", "PROBE", "KRILL")

/**
 * 게임 상태 보유자 + tick 루프 소유. 데이터 흐름: UI → dispatch/submitCommand → reduce → StateFlow.
 *
 * transient 상태는 reducer가 켜고 ViewModel이 타이머로 끈다(PLAN.md):
 * toast(1.4s 후 ClearToast), evolving(2.2s 후 EvolveComplete).
 * 터미널 history는 ViewModel-local(1차에 peer-echo 없음 — 2차에 GameState로 이동 가능).
 *
 * tick 루프·StateFlow 방출은 viewModelScope의 기본 Main.immediate에서(reduce는 순수·경량).
 */
class GameViewModel(
    private val rng: Rng,
    autoTick: Boolean = true,
    initialState: GameState? = null,
) : ViewModel() {

    // initialState는 테스트 시드 + (후속) 영속화 복원용 seam. null이면 새 게임을 생성한다.
    private val _state = MutableStateFlow(
        initialState ?: GameState.initial(
            name = NAMES[rng.nextInt(NAMES.size)],
            now = nowMillis(),
            peers = PeerRoster.makePeers(rng),
        ),
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _terminal = MutableStateFlow(bootBanner(_state.value))
    val terminal: StateFlow<List<TerminalLine>> = _terminal.asStateFlow()

    private var toastJob: Job? = null
    private var evolveJob: Job? = null
    private var battleJob: Job? = null

    init {
        // autoTick=false는 테스트용 — 무한 tick 루프를 끄고 dispatch/submitCommand만 검증.
        if (autoTick) {
            // 케어 틱 — monotonic dt로 백그라운드/도즈 복귀 시 스탯 붕괴 방지.
            viewModelScope.launch {
                var last = TimeSource.Monotonic.markNow()
                while (true) {
                    delay(CARE_TICK_MS)
                    val now = TimeSource.Monotonic.markNow()
                    val dt = ((now - last).inWholeMilliseconds / 1000f).coerceAtMost(CARE_DT_MAX)
                    last = now
                    dispatch(Action.Tick(dt))
                }
            }
            // 피어 틱 — 위치 드리프트 + 근접 AI 판정(고정 dt, 데모 1s 케이던스).
            viewModelScope.launch {
                while (true) {
                    delay(PEER_TICK_MS)
                    dispatch(Action.PeerTick(PEER_TICK_DT))
                }
            }
        }
    }

    fun dispatch(action: Action) {
        val before = _state.value
        val after = reduce(before, action, rng)
        _state.value = after

        if (after.toast != null && after.toast != before.toast) scheduleToastClear()
        if (after.evolving && !before.evolving) scheduleEvolveComplete()
        // 피어 이벤트(challenge/friendly/decline)는 어느 경로로 발생하든 터미널에 자동 에코.
        if (after.peerEventNonce != before.peerEventNonce) after.peerEventLatest?.let(::echoPeerEvent)
        maybeScheduleBattlePhase(before, after)
    }

    /**
     * 전투 페이즈 자동 진행 — reducer가 켠 transient 페이즈를 타이머로 다음 단계로 넘긴다(데모 phase
     * 스케줄러 1:1). 플레이어는 choose에서만 [Action.BattleCommit]로 개입. (phase/turn/result 변화 시 재스케줄.)
     */
    private fun maybeScheduleBattlePhase(before: GameState, after: GameState) {
        val b = after.battle
        val prev = before.battle
        val changed = prev?.phase != b?.phase || prev?.turn != b?.turn || prev?.result != b?.result
        if (!changed) return
        battleJob?.cancel()
        if (b == null) return
        val next: Pair<Long, Action>? = when (b.phase) {
            BattlePhase.MyCast, BattlePhase.TheirCast -> 700L to Action.BattleAdvanceCast
            BattlePhase.Reveal -> 700L to Action.BattleResolve
            BattlePhase.Damage -> 500L to Action.BattleApplyDamage
            BattlePhase.End -> if (b.result != null) 1800L to Action.BattleEnd else null
            BattlePhase.Choose -> null
        }
        if (next != null) {
            battleJob = viewModelScope.launch {
                delay(next.first)
                dispatch(next.second)
            }
        }
    }

    /** 피어 이벤트를 터미널 history에 append. challenge=sys, 나머지=out(데모 `tagByKind`). */
    private fun echoPeerEvent(event: PeerEvent) {
        val kind = if (event.kind == PeerEventKind.Challenge) TerminalLineKind.Sys else TerminalLineKind.Out
        val newLines = event.lines.map { TerminalLine(kind, it) }
        _terminal.value = (_terminal.value + newLines).takeLast(TERMINAL_CAP)
    }

    /** 터미널 입력 한 줄 처리. parse → respond → lines append + action dispatch. */
    fun submitCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // trimmed로 한 번만 parse — 에코와 실행이 같은 입력을 보게.
        val response = respond(parse(trimmed), _state.value, rng)
        if (response.clearScreen) {
            _terminal.value = response.lines
            return
        }
        // 입력 에코에 유닛명 셸 프롬프트를 붙인다(데모 `name@nautilus:~$ cmd`).
        val prompt = "${_state.value.name.lowercase()}@nautilus:~$"
        val inLine = TerminalLine(TerminalLineKind.In, "$prompt $trimmed")
        _terminal.value = (_terminal.value + inLine + response.lines).takeLast(TERMINAL_CAP)
        response.action?.let { dispatch(it) }
    }

    private fun scheduleToastClear() {
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MS)
            dispatch(Action.ClearToast)
        }
    }

    private fun scheduleEvolveComplete() {
        evolveJob?.cancel()
        evolveJob = viewModelScope.launch {
            delay(EVOLVE_MS)
            dispatch(Action.EvolveComplete)
        }
    }
}

/** 부팅 배너 — 데모 TerminalScreen 초기 history 1:1. */
private fun bootBanner(state: GameState): List<TerminalLine> = listOf(
    sys("◢◤ NAUTILUS // ABYSSAL OBS POST v3.2.1"),
    sys("◢◤ CARE TERMINAL / SECURE LINK ESTABLISHED"),
    sys(""),
    outLine("unit ${state.name} acquired — ${state.stage.name.lowercase()} stage"),
    outLine("type `help` to list commands."),
    outLine(""),
)

private fun sys(text: String) = TerminalLine(TerminalLineKind.Sys, text)
private fun outLine(text: String) = TerminalLine(TerminalLineKind.Out, text)
