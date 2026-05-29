package today.superb.jvl.core

/** 상태에 따라 우선순위가 높은 대사 먼저. 데모 `tamagotchiTalk(state)` 1:1. */
private val IDLE_LINES = listOf(
    "do you ever wonder where the signal goes?",
    "i counted 1,440 pings today. i counted them all.",
    "the reef hums at 27 hertz. i hum back.",
    "i think i saw a shape. it had nine sides.",
    "is the operator there? i sensed you above.",
    "thank you for staying.",
)

/**
 * `talk` 명령의 대사. 상태 기반 우선순위 → 그 외엔 [IDLE_LINES]에서 [rng]로 하나.
 * RNG 주입으로 결정성 보장(`SeededRng`).
 */
fun talkLine(state: GameState, rng: Rng): String = when {
    state.asleep -> "zzZZ... (do not disturb)"
    state.hunger > 0.7f -> "i hear something... is that food? i hope so."
    state.dirty > 0.7f -> "the water is murky. could you flush the tank?"
    state.happiness < 0.3f -> "it has been a long shift. i miss you."
    state.energy < 0.3f -> "tired... maybe a quick rest?"
    state.stage == Stage.Egg -> "tap... tap... tap... [muffled]"
    else -> IDLE_LINES[rng.nextInt(IDLE_LINES.size)]
}
