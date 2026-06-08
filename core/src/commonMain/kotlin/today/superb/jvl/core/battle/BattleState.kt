package today.superb.jvl.core.battle

/** 전투 진행 단계. 데모 `phase: 'choose'|'myCast'|'theirCast'|'reveal'|'damage'|'end'` 1:1. */
enum class BattlePhase { Choose, MyCast, TheirCast, Reveal, Damage, End }

/** 전투 결과. 데모 `result: 'win'|'lose'|'draw'|'flee'` 1:1. */
enum class BattleResult { Win, Lose, Draw, Flee }

/** 전투 로그 한 줄(newest-first, 최대 [BattleState.LOG_CAP]). 데모 battle.log 항목 1:1. */
data class BattleLogEntry(
    val tag: String,
    val line: String,
    val crit: Boolean,
    val dmgMe: Float,
    val dmgThem: Float,
)

/**
 * 전투 상태. 데모 `makeBattle()` 객체 1:1. GameState.battle에 nullable로 보관(전투 중에만 non-null).
 *
 * HP는 Float(데미지가 소수) — 표시에는 ceil. transient 페이즈 전이(myCast→…→end)는 reducer가
 * 켜고 ViewModel 스케줄러가 타이머로 진행시킨다(toast/evolve와 동일 패턴).
 */
data class BattleState(
    val peerId: String,
    val hpMe: Float,
    val hpMaxMe: Int,
    val hpThem: Float,
    val hpMaxThem: Int,
    val cursor: Int,
    val myMove: BattleAction?,
    val theirMove: BattleAction?,
    val phase: BattlePhase,
    val log: List<BattleLogEntry>,
    val result: BattleResult?,
    val turn: Int,
    val myMoveHistory: List<BattleAction>,
    val lastDmgMe: Float,
    val lastDmgThem: Float,
    val flashNonceMe: Int,
    val flashNonceThem: Int,
) {
    companion object {
        const val HP = 5
        const val LOG_CAP = 6

        /** 새 전투 — HP 5/5, 커서 ping, choose 단계. 데모 `makeBattle(peerId)` 1:1. */
        fun start(peerId: String): BattleState = BattleState(
            peerId = peerId,
            hpMe = HP.toFloat(),
            hpMaxMe = HP,
            hpThem = HP.toFloat(),
            hpMaxThem = HP,
            cursor = 0,
            myMove = null,
            theirMove = null,
            phase = BattlePhase.Choose,
            log = emptyList(),
            result = null,
            turn = 1,
            myMoveHistory = emptyList(),
            lastDmgMe = 0f,
            lastDmgThem = 0f,
            flashNonceMe = 0,
            flashNonceThem = 0,
        )
    }
}
