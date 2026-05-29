package today.superb.jvl.core

/** 상단 readout에 표시되는 기분 라벨. */
enum class Mood { ASLEEP, EVOLVING, SCOLDED, DISTRESSED, HUNGRY, UNHAPPY, DROWSY, NOMINAL }

/**
 * 기분 라벨 — 8단계 우선순위. 데모 `screens.jsx:118-127` 1:1.
 *
 * 주: [Mood.SCOLDED]는 `disciplineFlash`가 true여야 하나, 데모 as-is에서 `discipline` 액션이
 * 이를 켜지 않아 실제로는 도달하지 않는다(데모 버그를 그대로 보존). PLAN.md / `02:151` 참조.
 */
fun moodLabel(state: GameState): Mood = when {
    state.asleep -> Mood.ASLEEP
    state.evolving -> Mood.EVOLVING
    state.disciplineFlash -> Mood.SCOLDED
    state.dirty > 0.7f -> Mood.DISTRESSED
    state.hunger > 0.75f -> Mood.HUNGRY
    state.happiness < 0.3f -> Mood.UNHAPPY
    state.energy < 0.25f -> Mood.DROWSY
    else -> Mood.NOMINAL
}
