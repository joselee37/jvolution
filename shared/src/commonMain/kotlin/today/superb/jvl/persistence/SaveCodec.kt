package today.superb.jvl.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Species
import today.superb.jvl.core.View
import today.superb.jvl.ui.settings.Tweaks

/**
 * 저장 블롭 — 스키마 버전 + 게임 상태 + 설정. 단일 JSON으로 저장.
 *
 * **저장 호환성 주의:** [GameState]에 기본값 없는 필드를 추가하거나, by-name 직렬화되는 enum
 * (Species/Stage/View/Personality/RequestType/PeerEventKind/Battle* / Hue)의 상수를 rename·remove하면
 * 기존 저장본의 decode가 깨진다(→ [SaveCodec.decode]가 null → 새 게임). 그런 변경 시 [SCHEMA_VERSION]을
 * 올리고 [SaveCodec.decode]의 마이그레이션 분기를 추가할 것. (enum 순서 변경은 안전.)
 *
 * v1 → v2: 종 선택이 Tweaks.species(UI 설정)에서 GameState.species(도메인)로 이동.
 * v1 블롭은 [SaveCodec.decode]가 tweaks.species를 game.species로 이관한다.
 */
@Serializable
data class SaveBlob(
    val schemaVersion: Int = SCHEMA_VERSION,
    val game: GameState,
    val tweaks: Tweaks,
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

/**
 * 게임 상태/설정 ↔ JSON 직렬화. 순수(:shared) — 저장소(GameStore)와 무관.
 *
 * 저장 전 [GameState]의 transient(전투/뷰/토스트/nonce 등)를 제거해, 재시작 시 진행 중 전투나
 * 떠 있던 토스트가 되살아나지 않게 하고 distinctUntilChanged가 순수-transient 변화를 무시하게 한다.
 * 손상/구버전 블롭은 decode에서 null → caller가 새 게임으로 폴백.
 */
class SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true   // 신버전 저장본을 구버전 코드가 읽을 때 forward-compat
        encodeDefaults = true
        coerceInputValues = true   // 기본값 필드의 알 수 없는 enum 값 → 기본값으로(엔트리 추가 forward-compat)
    }

    /** 저장용 스냅샷 — transient 제거된 [SaveBlob]. */
    fun snapshot(state: GameState, tweaks: Tweaks): SaveBlob =
        SaveBlob(game = state.strippedForSave(), tweaks = tweaks)

    /**
     * distinctUntilChanged 비교용 키 — transient + 피어 cosmetic(위치/속도/쿨다운)까지 제거.
     * 저장 자체는 full state([encode])로 한다. 피어 위치가 매 틱 드리프트해도 키는 안 바뀌므로,
     * 디바운스가 cosmetic churn에 리셋되어 굶는(starvation) 것을 막는다 — 내구 변화에만 저장 발화.
     */
    fun dedupKey(state: GameState, tweaks: Tweaks): SaveBlob {
        val stripped = state.strippedForSave()
        return SaveBlob(
            game = stripped.copy(
                peers = stripped.peers.map {
                    it.copy(bearing = 0f, range = 0f, bearingVel = 0f, rangeVel = 0f, cooldown = 0f)
                },
            ),
            tweaks = tweaks,
        )
    }

    fun encode(blob: SaveBlob): String = json.encodeToString(SaveBlob.serializer(), blob)

    fun encode(state: GameState, tweaks: Tweaks): String = encode(snapshot(state, tweaks))

    /**
     * JSON → 블롭. null/손상/미지원 스키마는 null(→ 새 게임 폴백). transient는 재리셋(잔존 전투/뷰 방지).
     * v1 → v2 마이그레이션: tweaks.species를 game.species로 이관.
     */
    fun decode(raw: String?): SaveBlob? {
        if (raw == null) return null
        val blob = runCatching { json.decodeFromString(SaveBlob.serializer(), raw) }.getOrNull() ?: return null
        return when (blob.schemaVersion) {
            SaveBlob.SCHEMA_VERSION -> blob.copy(game = blob.game.strippedForSave())
            1 -> {
                // v1: 종이 tweaks.species에 있었다 — raw JSON에서 직접 끌어와 game.species로 이관.
                val legacySpecies = runCatching {
                    json.parseToJsonElement(raw).jsonObject["tweaks"]?.jsonObject
                        ?.get("species")?.jsonPrimitive?.content?.let(Species::valueOf)
                }.getOrNull()
                val game = (legacySpecies?.let { blob.game.copy(species = it) } ?: blob.game).strippedForSave()
                blob.copy(schemaVersion = SaveBlob.SCHEMA_VERSION, game = game)
            }
            else -> null // 알 수 없는 스키마 — 새 게임으로 폴백
        }
    }
}

/**
 * 재시작 시 되살아나면 안 되는 transient 필드를 안전 기본값으로 리셋한 복제본.
 * GameState에 새 transient 필드를 추가하면 여기에도 반드시 추가할 것
 * (SaveCodecTest.stripped_for_save_resets_every_transient 가 누락을 잡는다).
 */
internal fun GameState.strippedForSave(): GameState = copy(
    view = View.Sonar,
    battle = null,
    toast = null,
    evolving = false,
    canEvolve = false,
    disciplineFlash = false,
    pingNonce = 0,
    peerEventNonce = 0,
    peerEventLatest = null,
    pendingRequest = null,
)
