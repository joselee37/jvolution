package today.superb.jvl.core

/** 스탯 드리프트 계수(초당). dt를 곱해 적용. 데모 `tick` 케이스 1:1. */
private object Drift {
    const val ASLEEP_ENERGY = 0.02f
    const val ASLEEP_HUNGER = 0.005f
    const val AWAKE_HUNGER = 0.012f
    const val AWAKE_DIRTY = 0.008f
    const val AWAKE_ENERGY = -0.01f
    const val AWAKE_HAPPINESS = -0.015f
    const val EVOLVE_PROGRESS = 0.005f

    /** awake일 때 행복이 깎이기 시작하는 hunger/dirty 임계. */
    const val DISCOMFORT_THRESHOLD = 0.7f
}

private fun Float.clamp(): Float = coerceIn(0f, 1f)

/**
 * 순수 reducer — 데모 `reduce(s, a)` 1:1 포팅.
 *
 * 결정성: wall-clock(`nowMillis`)을 읽지 않고, 부수효과(토스트 만료 타이머 등)는 ViewModel이 소유한다.
 * [rng]는 향후 talk/peer 액션에서 사용(케어 액션은 결정적이라 미사용).
 */
fun reduce(state: GameState, action: Action, rng: Rng): GameState {
    fun log(msg: String): List<LogEntry> =
        (listOf(LogEntry(cycle = state.cycles + 1, msg = msg)) + state.log).take(GameState.LOG_CAP)

    return when (action) {
        is Action.Tick -> {
            val dt = action.dt
            var happiness = state.happiness
            var energy = state.energy
            var hunger = state.hunger
            var dirty = state.dirty
            if (state.asleep) {
                energy = (energy + Drift.ASLEEP_ENERGY * dt).clamp()
                hunger = (hunger + Drift.ASLEEP_HUNGER * dt).clamp()
            } else {
                hunger = (hunger + Drift.AWAKE_HUNGER * dt).clamp()
                dirty = (dirty + Drift.AWAKE_DIRTY * dt).clamp()
                energy = (energy + Drift.AWAKE_ENERGY * dt).clamp()
                if (hunger > Drift.DISCOMFORT_THRESHOLD || dirty > Drift.DISCOMFORT_THRESHOLD) {
                    happiness = (happiness + Drift.AWAKE_HAPPINESS * dt).clamp()
                }
            }
            val evolveProgress = (state.evolveProgress + Drift.EVOLVE_PROGRESS * dt).clamp()
            state.copy(
                happiness = happiness,
                energy = energy,
                hunger = hunger,
                dirty = dirty,
                evolveProgress = evolveProgress,
                canEvolve = state.stage.canAdvance() && evolveProgress >= 1f,
            )
        }

        Action.Ping -> state.copy(
            cycles = state.cycles + 1,
            bond = (state.bond + 0.03f).clamp(),
            pingNonce = state.pingNonce + 1,
        )

        Action.Feed -> state.copy(
            cycles = state.cycles + 1,
            hunger = (state.hunger - 0.25f).clamp(),
            happiness = (state.happiness + 0.05f).clamp(),
            dirty = (state.dirty + 0.03f).clamp(),
            log = log("FEED — ration dispensed"),
            toast = "NOM NOM",
        )

        Action.Play -> state.copy(
            cycles = state.cycles + 1,
            happiness = (state.happiness + 0.2f).clamp(),
            energy = (state.energy - 0.08f).clamp(),
            hunger = (state.hunger + 0.04f).clamp(),
            bond = (state.bond + 0.04f).clamp(),
            log = log("PLAY — bond sequence"),
            toast = "YIPPEE",
        )

        Action.Clean -> state.copy(
            cycles = state.cycles + 1,
            dirty = 0f,
            happiness = (state.happiness + 0.05f).clamp(),
            log = log("CLEAN — tank flushed"),
            toast = "TANK FLUSHED",
        )

        Action.Sleep -> state.copy(
            cycles = state.cycles + 1,
            asleep = !state.asleep,
            log = log(if (state.asleep) "WAKE" else "SLEEP — lights out"),
            toast = if (state.asleep) "AWAKE" else "GOOD NIGHT",
        )

        Action.Train -> state.copy(
            cycles = state.cycles + 1,
            training = (state.training + 0.15f).clamp(),
            discipline = (state.discipline + 0.05f).clamp(),
            energy = (state.energy - 0.08f).clamp(),
            hunger = (state.hunger + 0.04f).clamp(),
            evolveProgress = (state.evolveProgress + 0.04f).clamp(),
            log = log("TRAIN — drill complete"),
            toast = "DRILL OK",
        )

        Action.Discipline -> state.copy(
            cycles = state.cycles + 1,
            discipline = (state.discipline + 0.1f).clamp(),
            happiness = (state.happiness - 0.08f).clamp(),
            bond = (state.bond - 0.02f).clamp(),
            log = log("SCOLD — reprimand"),
            toast = "SCOLDED",
        )

        Action.Heal -> state.copy(
            cycles = state.cycles + 1,
            energy = (state.energy + 0.3f).clamp(),
            happiness = (state.happiness + 0.05f).clamp(),
            log = log("HEAL — biopatch"),
            toast = "PATCHED",
        )

        Action.Evolve ->
            if (!state.stage.canAdvance()) state
            else state.copy(evolving = true, toast = "EVOLVING")

        Action.EvolveComplete -> {
            val next = state.stage.next()
            state.copy(
                cycles = state.cycles + 1,
                stage = next,
                evolveProgress = 0f,
                canEvolve = false,
                evolving = false,
                log = log("EVOLVE — ${next.name.uppercase()} stage"),
                toast = "→ ${next.name.uppercase()}",
            )
        }

        is Action.Rename -> state.copy(name = action.name, log = log("RENAME — ${action.name}"))

        is Action.SetView -> state.copy(view = action.view)

        Action.ClearToast -> state.copy(toast = null)
    }
}
