package today.superb.jvl.core

/** 상단 readout에 표시되는 기분 라벨. */
enum class Mood { ASLEEP, EVOLVING, SCOLDED, DISTRESSED, HUNGRY, UNHAPPY, DROWSY, NOMINAL }

/**
 * 기분 라벨 — 8단계 우선순위. 데모 `screens.jsx:118-127` 1:1.
 *
 * 주: [Mood.SCOLDED]는 [Action.Discipline]이 켜는 transient `disciplineFlash`(~2s, ViewModel이
 * 끔)가 트리거. 데모 as-is에서는 도달 불가 버그였으나 풀터치 마일스톤에서 수리됨.
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
