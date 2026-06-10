package today.superb.jvl.persistence

import today.superb.jvl.core.GameState
import today.superb.jvl.core.LineageEntry
import today.superb.jvl.core.Peer
import today.superb.jvl.core.PeerEvent
import today.superb.jvl.core.PeerEventKind
import today.superb.jvl.core.PeerRequest
import today.superb.jvl.core.Personality
import today.superb.jvl.core.RequestType
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.View
import today.superb.jvl.core.battle.BattleState
import today.superb.jvl.ui.bezel.BezelStyle
import today.superb.jvl.ui.settings.Tweaks
import today.superb.jvl.ui.theme.Hue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val EPS = 1e-4f

private fun durableState() = GameState.initial(
    "MORSE", now = 500L,
    peers = listOf(
        Peer("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle, bearing = 90f, range = 0.5f, bearingVel = 1f, rangeVel = 0.001f, bond = 0.7f, battlesWon = 2, battlesLost = 1, cooldown = 30f),
    ),
).copy(
    gen = 3, stage = Stage.Larva, cycles = 12, species = Species.Squid,
    happiness = 0.9f, energy = 0.8f, hunger = 0.2f, dirty = 0.1f, bond = 0.5f, training = 0.3f, discipline = 0.4f,
    asleep = true, evolveProgress = 0.6f, dnd = true, sound = true,
    lineage = listOf(LineageEntry(1, "OLD", Stage.Adult, 99, 80, 70, 60, 50, 40, 100L, 200L)),
)

private fun tweaksFixture() = Tweaks(
    theme = Hue.Amber, crtIntensity = 1.2f, scanlines = false, noise = false,
    pulsePeriod = 8f, phosphorDecay = 2f, crtShader = true, bezel = BezelStyle.Vintage,
)

class SaveCodecTest {

    private val codec = SaveCodec()

    @Test
    fun encode_then_decode_preserves_durable_fields() {
        val g = assertNotNull(codec.decode(codec.encode(durableState(), tweaksFixture()))).game
        assertEquals(3, g.gen)
        assertEquals(Stage.Larva, g.stage)
        assertEquals(12, g.cycles)
        assertEquals(Species.Squid, g.species)
        assertEquals(0.9f, g.happiness, EPS)
        assertEquals(0.8f, g.energy, EPS)
        assertEquals(0.6f, g.evolveProgress, EPS)
        assertEquals(true, g.asleep)
        assertEquals(true, g.dnd)
        assertEquals(true, g.sound)
        assertEquals(500L, g.hatchedAt)
        // peers (durable relationships)
        assertEquals(1, g.peers.size)
        assertEquals(0.7f, g.peers[0].bond, EPS)
        assertEquals(2, g.peers[0].battlesWon)
        assertEquals(1, g.peers[0].battlesLost)
        // lineage
        assertEquals(1, g.lineage.size)
        assertEquals("OLD", g.lineage[0].name)
        assertEquals(99, g.lineage[0].cycles)
        assertEquals(200L, g.lineage[0].archivedAt)
    }

    @Test
    fun decode_resets_transient_fields_to_defaults() {
        val withTransients = durableState().copy(
            view = View.Battle,
            battle = BattleState.start("lumen"),
            toast = "NOM NOM",
            evolving = true,
            canEvolve = true,
            disciplineFlash = true,
            pingNonce = 5,
            peerEventNonce = 3,
            peerEventLatest = PeerEvent(PeerEventKind.Challenge, "lumen", listOf("x")),
            pendingRequest = PeerRequest("lumen", RequestType.Challenge),
        )
        val g = assertNotNull(codec.decode(codec.encode(withTransients, tweaksFixture()))).game
        assertEquals(View.Sonar, g.view)
        assertNull(g.battle)
        assertNull(g.toast)
        assertFalse(g.evolving)
        assertFalse(g.canEvolve)
        assertFalse(g.disciplineFlash)
        assertEquals(0, g.pingNonce)
        assertEquals(0, g.peerEventNonce)
        assertNull(g.peerEventLatest)
        assertNull(g.pendingRequest)
        // durable still intact through the strip
        assertEquals(3, g.gen)
        assertEquals(0.9f, g.happiness, EPS)
    }

    @Test
    fun tweaks_round_trip_preserves_all_fields() {
        val t = tweaksFixture()
        assertEquals(t, assertNotNull(codec.decode(codec.encode(durableState(), t))).tweaks)
    }

    @Test
    fun decode_of_corrupt_json_returns_null() {
        assertNull(codec.decode("not valid json {{{"))
        assertNull(codec.decode("{\"schemaVersion\":1}"))   // missing required game/tweaks
    }

    @Test
    fun decode_of_null_returns_null() {
        assertNull(codec.decode(null))
    }

    @Test
    fun decode_migrates_v1_tweaks_species_into_game_state() {
        val game = GameState.initial("UNIT", 0L)
        // 현행 코덱으로 v2 블롭을 만든 뒤 schemaVersion을 1로 바꾸고 tweaks에 species를 주입해
        // v1 형상을 재구성한다 — 필드 나열 없이 v1 디스크 포맷과 동형.
        val v2 = codec.encode(game, Tweaks())
        val v1 = v2
            .replaceFirst("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replaceFirst("\"tweaks\":{", "\"tweaks\":{\"species\":\"Squid\",")

        val blob = codec.decode(v1)

        assertNotNull(blob, "v1 블롭은 마이그레이션되어 디코드된다")
        assertEquals(Species.Squid, blob.game.species, "tweaks.species → game.species 이관")
        assertEquals(SaveBlob.SCHEMA_VERSION, blob.schemaVersion)
    }

    @Test
    fun decode_v1_without_species_falls_back_to_game_species() {
        val v2 = codec.encode(GameState.initial("UNIT", 0L).copy(species = Species.Blob), Tweaks())
        val v1 = v2.replaceFirst("\"schemaVersion\":2", "\"schemaVersion\":1")
        val blob = codec.decode(v1)
        assertNotNull(blob)
        assertEquals(Species.Blob, blob.game.species, "species 키 없는 v1은 game.species 유지")
    }

    @Test
    fun decode_v1_with_malformed_species_keeps_save_and_falls_back() {
        val game = GameState.initial("UNIT", 0L)
        val v2 = codec.encode(game.copy(gen = 3), Tweaks())
        val v1 = v2
            .replaceFirst("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replaceFirst("\"tweaks\":{", "\"tweaks\":{\"species\":\"Dragon\",")

        val blob = codec.decode(v1)

        assertNotNull(blob, "깨진 species 값이 저장본 전체를 날리지 않는다")
        assertEquals(3, blob.game.gen, "진행도 보존")
        assertEquals(Species.Ghost, blob.game.species, "이관 실패 시 game.species 폴백")
    }

    // #5 가드: 모든 transient를 세팅한 상태의 strippedForSave()가 durable-only 기준 상태와 같아야 함.
    // GameState에 새 transient가 추가됐는데 strippedForSave에서 빠뜨리면 이 단정이 깨진다.
    @Test
    fun stripped_for_save_resets_every_transient() {
        val full = durableState().copy(
            view = View.Battle,
            battle = BattleState.start("lumen"),
            toast = "X",
            evolving = true,
            canEvolve = true,
            disciplineFlash = true,
            pingNonce = 9,
            peerEventNonce = 7,
            peerEventLatest = PeerEvent(PeerEventKind.Friendly, "lumen", listOf("y")),
            pendingRequest = PeerRequest("lumen", RequestType.Challenge),
        )
        // durableState()는 transient가 모두 initial 기본값 → strip은 정확히 그 상태를 재현해야 함.
        assertEquals(durableState(), full.strippedForSave())
    }
}
