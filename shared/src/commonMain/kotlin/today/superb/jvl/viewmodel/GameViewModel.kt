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
import today.superb.jvl.core.Rng
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
class GameViewModel(private val rng: Rng) : ViewModel() {

    private val _state = MutableStateFlow(
        GameState.initial(name = NAMES[rng.nextInt(NAMES.size)], now = nowMillis()),
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _terminal = MutableStateFlow(bootBanner(_state.value))
    val terminal: StateFlow<List<TerminalLine>> = _terminal.asStateFlow()

    private var toastJob: Job? = null
    private var evolveJob: Job? = null

    init {
        viewModelScope.launch {
            var last = TimeSource.Monotonic.markNow()
            while (true) {
                delay(CARE_TICK_MS)
                val now = TimeSource.Monotonic.markNow()
                val dt = (now - last).inWholeMilliseconds / 1000f
                last = now
                dispatch(Action.Tick(dt))
            }
        }
    }

    fun dispatch(action: Action) {
        val before = _state.value
        val after = reduce(before, action, rng)
        _state.value = after

        if (after.toast != null && after.toast != before.toast) scheduleToastClear()
        if (after.evolving && !before.evolving) scheduleEvolveComplete()
    }

    /** 터미널 입력 한 줄 처리. parse → respond → lines append + action dispatch. */
    fun submitCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val response = respond(parse(text), _state.value, rng)
        if (response.clearScreen) {
            _terminal.value = response.lines
            return
        }
        val inLine = TerminalLine(TerminalLineKind.In, trimmed)
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
