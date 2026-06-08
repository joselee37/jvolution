package today.superb.jvl.core.battle

import kotlinx.serialization.Serializable

/**
 * 전투 4액션(마비노기식 RPS). 선언 순서 = 데모 `BATTLE_ACTIONS` 인덱스(메뉴 커서 0..3).
 *
 * ping=평타, charge=스매시(방어 관통), dodge=디펜스(ping 막음), screech=카운터(ping 반사).
 */
@Serializable
enum class BattleAction { Ping, Charge, Dodge, Screech }
