package today.superb.jvl.core.battle

import today.superb.jvl.core.Personality
import today.superb.jvl.core.Rng
import today.superb.jvl.core.Stage

private typealias A = BattleAction

/** 한 턴의 최종 결과(배수·crit 적용 후). 데모 `resolveBattleTurn` 반환 1:1. */
data class BattleTurnResult(val tag: String, val me: Float, val them: Float, val crit: Boolean)

/** 매트릭스 한 칸의 기본값. me=내가 받는 base, them=상대가 받는 base. */
private data class Outcome(val tag: String, val me: Float, val them: Float)

/** 16칸 결과 매트릭스(내 액션 × 상대 액션). 데모 `BATTLE_OUTCOMES` 1:1. */
private val OUTCOMES: Map<Pair<BattleAction, BattleAction>, Outcome> = mapOf(
    (A.Ping to A.Ping) to Outcome("INTERFERENCE", 0.5f, 0.5f),
    (A.Ping to A.Charge) to Outcome("INTERRUPT", 0f, 1.0f),
    (A.Ping to A.Dodge) to Outcome("BLOCKED", 0f, 0f),
    (A.Ping to A.Screech) to Outcome("COUNTERED", 0.6f, 0f),

    (A.Charge to A.Ping) to Outcome("INTERRUPT", 1.0f, 0f),
    (A.Charge to A.Charge) to Outcome("CLASH", 0.9f, 0.9f),
    (A.Charge to A.Dodge) to Outcome("BROKEN", 0f, 1.5f),
    (A.Charge to A.Screech) to Outcome("BROKEN", 0f, 1.5f),

    (A.Dodge to A.Ping) to Outcome("BLOCKED", 0f, 0f),
    (A.Dodge to A.Charge) to Outcome("BROKEN", 1.5f, 0f),
    (A.Dodge to A.Dodge) to Outcome("MISS", 0f, 0f),
    (A.Dodge to A.Screech) to Outcome("MISS", 0f, 0f),

    (A.Screech to A.Ping) to Outcome("COUNTERED", 0f, 0.6f),
    (A.Screech to A.Charge) to Outcome("BROKEN", 1.5f, 0f),
    (A.Screech to A.Dodge) to Outcome("MISS", 0f, 0f),
    (A.Screech to A.Screech) to Outcome("FEEDBACK", 0.3f, 0.3f),
)

/** 피어 단계별 파워 배수. 데모 `PEER_POWER_BY_STAGE` 1:1. */
private val PEER_POWER_BY_STAGE: Map<Stage, Float> = mapOf(
    Stage.Egg to 0.5f,
    Stage.Larva to 0.7f,
    Stage.Juvenile to 1.0f,
    Stage.Adult to 1.3f,
)

/** 성격별 고정 행동 분포(veteran 제외 — read&react). 데모 `NPC_MOVE_PROFILES` 1:1. */
private val MOVE_PROFILES: Map<Personality, Map<BattleAction, Float>> = mapOf(
    Personality.Aggressive to mapOf(A.Ping to 0.30f, A.Charge to 0.50f, A.Dodge to 0.05f, A.Screech to 0.15f),
    Personality.Gentle to mapOf(A.Ping to 0.25f, A.Charge to 0.05f, A.Dodge to 0.40f, A.Screech to 0.30f),
    Personality.Playful to mapOf(A.Ping to 0.25f, A.Charge to 0.25f, A.Dodge to 0.25f, A.Screech to 0.25f),
)
private val PLAYFUL_PROFILE = MOVE_PROFILES.getValue(Personality.Playful)

/** veteran이 플레이어 최다 액션을 카운터하는 표. 데모 `COUNTER_TABLE` 1:1. */
private val COUNTER_TABLE: Map<BattleAction, BattleAction> = mapOf(
    A.Ping to A.Dodge,
    A.Charge to A.Ping,
    A.Dodge to A.Charge,
    A.Screech to A.Charge,
)

private fun round1(v: Float): Float = (kotlin.math.round(v * 10f)) / 10f

/** 소수 첫째 자리 문자열(이미 round1된 값 가정). 데모 `toFixed(1)` 대응. */
private fun fmt1(v: Float): String {
    val r = kotlin.math.round(v * 10f).toInt()
    val frac = if (r % 10 < 0) -(r % 10) else r % 10
    return "${r / 10}.$frac"
}

private fun verb(move: BattleAction): String = when (move) {
    A.Ping -> "pings"
    A.Charge -> "charges"
    A.Dodge -> "evades"
    A.Screech -> "screeches"
}

/**
 * (내 무브, 상대 무브) + 스탯 → 최종 데미지. 데모 `resolveBattleTurn` 1:1.
 * training이 공격 배수를, discipline이 피해 감소를, 상대 단계가 파워를 결정. 5% RESONANCE crit은
 * 양측 비-0 데미지를 2배. RNG 호출: 데미지 산정 후 crit 주사위 1회(데모 순서 보존).
 */
fun resolveBattleTurn(
    myMove: BattleAction,
    theirMove: BattleAction,
    training: Float,
    discipline: Float,
    peerStage: Stage,
    rng: Rng,
): BattleTurnResult {
    val base = OUTCOMES[myMove to theirMove] ?: Outcome("MISS", 0f, 0f)
    val myAttackMult = 1f + training * 0.5f
    val myDefMult = 1f - discipline * 0.3f
    val peerAttackMult = PEER_POWER_BY_STAGE[peerStage] ?: 1.0f
    var me = base.me * peerAttackMult * myDefMult
    var them = base.them * myAttackMult
    var crit = false
    if (rng.nextFloat() < 0.05f && (me > 0f || them > 0f)) {
        crit = true
        me *= 2f
        them *= 2f
    }
    return BattleTurnResult(if (crit) "RESONANCE" else base.tag, round1(me), round1(them), crit)
}

/**
 * 피어의 다음 무브. 데모 `pickNpcMove(peer, battle)` 1:1.
 * veteran은 [myMoveHistory] 최근 3수의 최다 액션을 70% 확률로 카운터, 30%는 playful 분포.
 * 그 외 성격은 고정 분포. 히스토리가 없으면 veteran도 playful 분포.
 */
fun pickNpcMove(personality: Personality, myMoveHistory: List<BattleAction>, rng: Rng): BattleAction {
    if (personality == Personality.Veteran && myMoveHistory.isNotEmpty()) {
        val counts = LinkedHashMap<BattleAction, Int>()
        for (m in myMoveHistory.takeLast(3)) counts[m] = (counts[m] ?: 0) + 1
        val mostCommon = counts.entries.maxByOrNull { it.value }?.key
        if (mostCommon != null && rng.nextFloat() < 0.7f) return COUNTER_TABLE[mostCommon] ?: A.Charge
    }
    val profile = MOVE_PROFILES[personality] ?: PLAYFUL_PROFILE
    val r = rng.nextFloat()
    var acc = 0f
    for (move in BattleAction.entries) {
        acc += profile[move] ?: 0f
        if (r < acc) return move
    }
    return A.Ping
}

/** `challenge <name>` 시 피어가 수락할 확률. 데모 accept-odds 1:1. */
fun acceptOdds(personality: Personality): Float = when (personality) {
    Personality.Aggressive -> 0.85f
    Personality.Playful -> 0.65f
    Personality.Veteran -> 0.55f
    Personality.Gentle -> 0.30f
}

/** 전투 로그 한 줄 서술. 데모 `battleNarration` 1:1(태그별 분기). */
fun battleNarration(
    myMove: BattleAction,
    theirMove: BattleAction,
    result: BattleTurnResult,
    myName: String,
    theirName: String,
): String = when (result.tag) {
    "MISS" -> "$myName ${verb(myMove)}, $theirName ${verb(theirMove)} — both whiff"
    "INTERFERENCE" -> "mutual ${myMove.name.lowercase()} — waves overlap"
    "CLASH" -> "both charge — shockwave between"
    "FEEDBACK" -> "dual screech — feedback loop"
    "BLOCKED" ->
        if (result.me == 0f && result.them == 0f) {
            if (myMove == A.Dodge) "$myName evades the pulse" else "$theirName evades the pulse"
        } else {
            "pulse absorbed"
        }
    "COUNTERED" ->
        if (myMove == A.Screech) "$myName reflects — $theirName takes ${fmt1(result.them)}"
        else "$theirName reflects — $myName takes ${fmt1(result.me)}"
    "INTERRUPT" ->
        if (myMove == A.Ping) "$myName's pulse interrupts the charge — ${fmt1(result.them)}"
        else "$theirName's pulse interrupts the charge — ${fmt1(result.me)}"
    "BROKEN" ->
        if (myMove == A.Charge) "$myName smashes through — ${fmt1(result.them)} dmg"
        else "$theirName smashes through — ${fmt1(result.me)} dmg"
    else -> ""
}
