package today.superb.jvl.core

/** 터미널/소나 로그 한 줄. newest-first로 누적, 최대 [LOG_CAP]줄. */
data class LogEntry(val cycle: Int, val msg: String)

/**
 * 게임 전역 상태. 데모 `initialState()` 1:1 포팅(1차 마일스톤 범위: 케어 + 메타).
 *
 * 불변: 모든 필드 `val`, 컬렉션은 read-only `List`. reducer는 항상 `copy`로 새 상태를 반환한다.
 * peers / battle / pendingRequest / lineage 는 2차 마일스톤(레이더·전투·계보)에서 추가.
 *
 * 스탯은 모두 [0, 1] 범위(reducer가 [clamp]). hunger 0=배부름·1=굶주림, dirty 0=청결·1=더러움.
 */
data class GameState(
    val name: String,
    val age: Int,
    val cycles: Int,
    val gen: Int,
    val stage: Stage,
    val species: Species,
    val happiness: Float,
    val energy: Float,
    val hunger: Float,
    val dirty: Float,
    val bond: Float,
    val training: Float,
    val discipline: Float,
    val asleep: Boolean,
    val evolveProgress: Float,
    val canEvolve: Boolean,
    val evolving: Boolean,
    val disciplineFlash: Boolean,
    val pingNonce: Int,
    val log: List<LogEntry>,
    val toast: String?,
    val sound: Boolean,
    val view: View,
    /** 부화 시각(epoch millis). reducer 밖(ViewModel)에서 주입 — reducer는 wall-clock을 읽지 않는다. */
    val hatchedAt: Long,
) {
    companion object {
        const val LOG_CAP = 20

        /**
         * 새 게임 초기 상태. 데모 `initialState()`의 기본 스탯과 동일.
         * @param name  생성 시점에 caller(ViewModel)가 RNG로 고른 이름.
         * @param now   부화 시각(epoch millis) — caller가 `nowMillis()`로 주입.
         */
        fun initial(name: String, now: Long): GameState = GameState(
            name = name,
            age = 0,
            cycles = 0,
            gen = 1,
            stage = Stage.Egg,
            species = Species.Ghost,
            happiness = 0.6f,
            energy = 0.7f,
            hunger = 0.45f,
            dirty = 0.3f,
            bond = 0.4f,
            training = 0.1f,
            discipline = 0.2f,
            asleep = false,
            evolveProgress = 0f,
            canEvolve = false,
            evolving = false,
            disciplineFlash = false,
            pingNonce = 0,
            log = emptyList(),
            toast = null,
            sound = false,
            view = View.Sonar,
            hatchedAt = now,
        )
    }
}
