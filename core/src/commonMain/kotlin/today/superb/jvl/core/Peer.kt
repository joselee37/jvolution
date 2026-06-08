package today.superb.jvl.core

/**
 * 피어 성격 — 근접 AI 이벤트에서 challenge/friendly 분기를 고를 확률을 결정한다.
 * 데모 `PERSONALITIES` 1:1. `idle` 확률은 명시값이 아니라 나머지 질량(1 - challenge - friendly).
 *
 * 전투 행동 분포에도 재사용된다([04](demo/docs/04-battle.md)) — 3차 마일스톤.
 */
enum class Personality(val challenge: Float, val friendly: Float) {
    Aggressive(challenge = 0.65f, friendly = 0.10f),
    Gentle(challenge = 0.08f, friendly = 0.55f),
    Playful(challenge = 0.35f, friendly = 0.35f),
    Veteran(challenge = 0.25f, friendly = 0.15f),
}

/**
 * NPC 피어 한 유닛. 데모 `makePeers()`가 만드는 객체 1:1.
 *
 * 고정 속성(id/name/species/stage/personality)은 로스터에서 오고, 가변 상태(극좌표 위치·속도,
 * 유대, 전적, 쿨다운)는 peer tick마다 갱신된다. 모든 필드 `val` — reducer는 `copy`로 갱신.
 *
 * @property bearing   방위(도). 0=북(N), 시계방향 증가. [0, 360).
 * @property range     거리. 0=중심, 1=스코프 가장자리. 드리프트는 [0.20, 0.92] 띠로 경계 반사.
 * @property bearingVel 방위 드리프트 속도(도/초).
 * @property rangeVel  거리 드리프트 속도(/초). 경계에서 부호 반전.
 * @property cooldown  다음 AI 발동까지 침묵 시간(초). peer tick마다 dt만큼 감소, 0에서 멈춤.
 */
data class Peer(
    val id: String,
    val name: String,
    val species: Species,
    val stage: Stage,
    val personality: Personality,
    val bearing: Float,
    val range: Float,
    val bearingVel: Float,
    val rangeVel: Float,
    val bond: Float,
    val battlesWon: Int,
    val battlesLost: Int,
    val cooldown: Float,
)
