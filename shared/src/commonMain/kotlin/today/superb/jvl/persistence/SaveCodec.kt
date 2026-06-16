package today.superb.jvl.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Species
import today.superb.jvl.core.View
import today.superb.jvl.core.genetics.Genome
import today.superb.jvl.core.genetics.default
import today.superb.jvl.ui.settings.Tweaks

/**
 * 저장 블롭 — 스키마 버전 + 게임 상태 + 설정. 단일 JSON으로 저장.
 *
 * **저장 호환성 주의:** [GameState]에 기본값 없는 필드를 추가하거나, by-name 직렬화되는 enum
 * (Species/Stage/View/Personality/RequestType/PeerEventKind/Battle* / Hue / BezelStyle)의 상수를 rename·remove하면
 * 기존 저장본의 decode가 깨진다(→ [SaveCodec.decode]가 null → 새 게임). 그런 변경 시 [SCHEMA_VERSION]을
 * 올리고 [SaveCodec.decode]의 마이그레이션 분기를 추가할 것. (enum 순서 변경은 안전.)
 *
 * v1 → v2: 종 선택이 Tweaks.species(UI 설정)에서 GameState.species(도메인)로 이동.
 * v1 블롭은 [SaveCodec.decode]가 tweaks.species를 game.species로 이관한다.
 *
 * v2 → v3: 유전 계보 도입. GameState에 genome/creatureId/motherId/fatherId 추가, lineage가
 * `List<LineageEntry>`(배열) → `Lineage`(객체 {ancestors:[...]})로 교체. v2 블롭은 lineage 배열을
 * Ancestor 배열로 surgery한 뒤 디코드한다(없는 게놈/식별자 필드는 기본값으로 채워짐).
 */
@Serializable
data class SaveBlob(
    val schemaVersion: Int = SCHEMA_VERSION,
    val game: GameState,
    val tweaks: Tweaks,
) {
    companion object {
        const val SCHEMA_VERSION = 3
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
     *
     * 스키마 버전을 먼저 raw에서 읽는다 — v2 lineage는 배열이라 v3 직렬자로 바로 디코드하면 깨지기 때문.
     * 레거시(v1/v2)는 [migrateLegacy]로 형상 변환 후 디코드.
     */
    fun decode(raw: String?): SaveBlob? {
        if (raw == null) return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return null
        return when {
            version == SaveBlob.SCHEMA_VERSION ->
                runCatching { json.decodeFromString(SaveBlob.serializer(), raw) }
                    .getOrNull()?.let { it.copy(game = it.game.strippedForSave()) }
            version in 1..2 -> migrateLegacy(root, version)
            else -> null // 알 수 없는 스키마 — 새 게임으로 폴백
        }
    }

    /**
     * v1/v2 디스크 블롭을 현행 스키마로 마이그레이션한다(raw JSON surgery 후 v3 직렬자로 디코드).
     *
     * - lineage: 구 `LineageEntry` 배열 → `{ancestors:[Ancestor...]}`(기본 게놈·null 부모·Ghost 종 폴백).
     *   이미 v3 객체면 통과. 그 외/누락이면 빈 ancestors.
     * - v1 종 이관: tweaks.species → game.species(v2+는 이미 game.species).
     * - 없는 genome/creatureId/motherId/fatherId(게임)·genome(피어)은 디코드 시 기본값으로 채워진다.
     */
    private fun migrateLegacy(root: JsonObject, version: Int): SaveBlob? = runCatching {
        // surgery 전체를 runCatching으로 감싼다 — 잘못된 형상("game":123, lineage 항목의 gen이 객체 등)에서
        // .jsonObject/.jsonPrimitive가 던지는 예외가 decode()의 null 폴백 계약을 깨고 앱 시작을 크래시시키지 않게.
        val game = root["game"]?.jsonObject ?: return@runCatching null
        val tweaks = root["tweaks"]?.jsonObject ?: return@runCatching null

        val defaultGenome = json.encodeToJsonElement(Genome.serializer(), Genome.default())
        val newLineage: JsonElement = when (val l = game["lineage"]) {
            is JsonArray -> JsonObject(
                mapOf(
                    "ancestors" to JsonArray(
                        l.mapNotNull { it as? JsonObject }.map { e ->
                            val gen = e["gen"]?.jsonPrimitive?.content ?: "0"
                            val name = e["name"]?.jsonPrimitive?.content ?: "UNIT"
                            JsonObject(
                                mapOf(
                                    "id" to JsonPrimitive("g${gen}_$name"),
                                    "gen" to (e["gen"] ?: JsonPrimitive(0)),
                                    "name" to JsonPrimitive(name),
                                    "species" to JsonPrimitive(Species.Ghost.name),
                                    "stage" to (e["stage"] ?: JsonPrimitive("Adult")),
                                    "genome" to defaultGenome,
                                    "motherId" to JsonNull,
                                    "fatherId" to JsonNull,
                                    "cycles" to (e["cycles"] ?: JsonPrimitive(0)),
                                    "happiness" to (e["happiness"] ?: JsonPrimitive(0)),
                                    "energy" to (e["energy"] ?: JsonPrimitive(0)),
                                    "bond" to (e["bond"] ?: JsonPrimitive(0)),
                                    "discipline" to (e["discipline"] ?: JsonPrimitive(0)),
                                    "training" to (e["training"] ?: JsonPrimitive(0)),
                                    "hatchedAt" to (e["hatchedAt"] ?: JsonPrimitive(0)),
                                    "archivedAt" to (e["archivedAt"] ?: JsonPrimitive(0)),
                                ),
                            )
                        },
                    ),
                ),
            )
            is JsonObject -> l   // 이미 v3 형상
            else -> JsonObject(mapOf("ancestors" to JsonArray(emptyList())))
        }

        // v1: 종이 tweaks.species에 있었다 → game.species로 이관(잘못된 enum 값은 디코드 시 폴백).
        val legacySpecies = if (version == 1) tweaks["species"]?.jsonPrimitive?.contentOrNull else null

        val newGameMap = LinkedHashMap<String, JsonElement>(game)
        newGameMap["lineage"] = newLineage
        if (legacySpecies != null) newGameMap["species"] = JsonPrimitive(legacySpecies)

        val newRoot = JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(SaveBlob.SCHEMA_VERSION),
                "game" to JsonObject(newGameMap),
                "tweaks" to tweaks,
            ),
        )
        json.decodeFromJsonElement(SaveBlob.serializer(), newRoot)
    }.getOrNull()?.let { it.copy(game = it.game.strippedForSave()) }
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
