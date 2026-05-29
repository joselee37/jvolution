package today.superb.jvl.core.terminal

import today.superb.jvl.core.Action

/** 터미널 한 줄의 종류 — 렌더 시 색상이 다름(데모 `{t: 'sys'|'in'|'out'}`). */
enum class TerminalLineKind { Sys, In, Out }

data class TerminalLine(val kind: TerminalLineKind, val text: String)

/** [TerminalResponder]의 명령 처리 결과. */
data class TerminalResponse(
    /** 화면에 추가할 출력 줄(입력 에코 `in` 줄은 ViewModel이 별도로 붙임). */
    val lines: List<TerminalLine>,
    /** 게임 상태를 바꾸려면 ViewModel이 reduce로 흘릴 액션. 없으면 null. */
    val action: Action? = null,
    /** `clear` 명령 — 히스토리 비우기. ViewModel이 처리. */
    val clearScreen: Boolean = false,
)
