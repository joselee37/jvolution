package today.superb.jvl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val EPS = 1e-4f

private fun peer(
    id: String = "p1",
    name: String = "PEER-1",
    species: Species = Species.Ghost,
    stage: Stage = Stage.Adult,
    personality: Personality = Personality.Playful,
    bearing: Float = 0f,
    range: Float = 0.5f,
    bearingVel: Float = 0f,
    rangeVel: Float = 0f,
    bond: Float = 0f,
    battlesWon: Int = 0,
    battlesLost: Int = 0,
    cooldown: Float = 100f,
) = Peer(id, name, species, stage, personality, bearing, range, bearingVel, rangeVel, bond, battlesWon, battlesLost, cooldown)

private fun stateWith(vararg ps: Peer, pending: PeerRequest? = null, dnd: Boolean = false) =
    GameState.initial("TEST", 0L, ps.toList()).copy(pendingRequest = pending, dnd = dnd)

class PeerReducerTest {

    // ── 위치 드리프트 ──────────────────────────────────────────

    @Test
    fun peer_tick_drifts_bearing() {
        val s = stateWith(peer(bearing = 10f, bearingVel = 5f, cooldown = 100f))
        val p = reduce(s, Action.PeerTick(1f), SeededRng(1L)).peers.first()
        assertEquals(15f, p.bearing, EPS)
    }

    @Test
    fun peer_tick_wraps_bearing_past_360() {
        val s = stateWith(peer(bearing = 358f, bearingVel = 5f, cooldown = 100f))
        val p = reduce(s, Action.PeerTick(1f), SeededRng(1L)).peers.first()
        assertEquals(3f, p.bearing, EPS)
    }

    @Test
    fun peer_tick_wraps_bearing_below_zero() {
        val s = stateWith(peer(bearing = 2f, bearingVel = -5f, cooldown = 100f))
        val p = reduce(s, Action.PeerTick(1f), SeededRng(1L)).peers.first()
        assertEquals(357f, p.bearing, EPS)
    }

    @Test
    fun peer_tick_reflects_range_at_lower_bound() {
        val s = stateWith(peer(range = 0.205f, rangeVel = -0.01f, cooldown = 100f))
        val p = reduce(s, Action.PeerTick(1f), SeededRng(1L)).peers.first()
        assertEquals(0.20f, p.range, EPS)        // clamped to band floor
        assertEquals(0.01f, p.rangeVel, EPS)     // sign flipped inward
    }

    @Test
    fun peer_tick_reflects_range_at_upper_bound() {
        val s = stateWith(peer(range = 0.915f, rangeVel = 0.01f, cooldown = 100f))
        val p = reduce(s, Action.PeerTick(1f), SeededRng(1L)).peers.first()
        assertEquals(0.92f, p.range, EPS)
        assertEquals(-0.01f, p.rangeVel, EPS)
    }

    @Test
    fun peer_tick_decrements_cooldown_floor_zero() {
        val s = stateWith(peer(cooldown = 0.5f, bearingVel = 0f, rangeVel = 0f))
        // cooldown hits 0 → AI gate opens; idle roll keeps it simple (gate float ≥ 0.06 = no fire).
        val p = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.5f))).peers.first()
        assertEquals(0f, p.cooldown, EPS)
    }

    // ── AI 발동 게이트 ─────────────────────────────────────────

    @Test
    fun peer_tick_does_not_roll_while_cooldown_positive() {
        // cooldown > 0 → gate short-circuits before consuming any RNG (empty FixedRng would throw).
        val s = stateWith(peer(cooldown = 10f))
        val next = reduce(s, Action.PeerTick(1f), FixedRng(emptyList()))
        assertNull(next.pendingRequest)
    }

    @Test
    fun peer_tick_does_not_roll_while_request_pending() {
        val s = stateWith(peer(cooldown = 0f), pending = PeerRequest("other", RequestType.Challenge))
        val next = reduce(s, Action.PeerTick(1f), FixedRng(emptyList()))
        // unchanged pending, no RNG consumed
        assertEquals("other", next.pendingRequest?.from)
    }

    @Test
    fun peer_tick_gate_fails_above_six_percent() {
        val s = stateWith(peer(cooldown = 0f))
        // gate float ≥ 0.06 → no fire, but cooldown idle reroll is NOT taken (gate itself failed).
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.06f)))
        assertNull(next.pendingRequest)
        assertEquals(0f, next.peers.first().cooldown, EPS) // stays 0, keeps re-rolling next tick
    }

    // ── 분기: challenge / friendly / idle / suppressed ─────────

    @Test
    fun peer_tick_challenge_branch_creates_request() {
        val s = stateWith(peer(id = "hrrk", name = "HRRK", personality = Personality.Aggressive, cooldown = 0f))
        // [gate<0.06, roll<0.65 challenge, cooldown rand=0 → 90s]
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.0f, 0.0f)))
        assertEquals(PeerRequest("hrrk", RequestType.Challenge), next.pendingRequest)
        assertEquals("HRRK CHALLENGES", next.toast)
        assertEquals(1, next.peerEventNonce)
        assertEquals(PeerEventKind.Challenge, next.peerEventLatest?.kind)
        assertTrue(next.peerEventLatest!!.lines.any { it.contains("INCOMING") })
        assertEquals(90f, next.peers.first().cooldown, EPS)
    }

    @Test
    fun peer_tick_friendly_branch_raises_bond() {
        val s = stateWith(peer(id = "lumen", name = "LUMEN-3", personality = Personality.Gentle, bond = 0f, cooldown = 0f))
        // gentle: challenge 0.08, friendly 0.55 → roll 0.5 lands in friendly band; cooldown rand=0 → 60s
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.5f, 0.0f)))
        assertNull(next.pendingRequest)
        assertEquals(0.05f, next.peers.first().bond, EPS)
        assertEquals("LUMEN-3 APPROACHES", next.toast)
        assertEquals(1, next.peerEventNonce)
        assertEquals(PeerEventKind.Friendly, next.peerEventLatest?.kind)
        assertEquals(60f, next.peers.first().cooldown, EPS)
    }

    @Test
    fun peer_tick_idle_branch_only_rerolls_cooldown() {
        val s = stateWith(peer(personality = Personality.Gentle, cooldown = 0f))
        // gentle sum=0.63 → roll 0.99 = idle; cooldown rand=0 → 30s
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.99f, 0.0f)))
        assertNull(next.pendingRequest)
        assertEquals(0, next.peerEventNonce)
        assertNull(next.peerEventLatest)
        assertEquals(30f, next.peers.first().cooldown, EPS)
    }

    @Test
    fun peer_tick_challenge_suppressed_by_dnd() {
        val s = stateWith(peer(personality = Personality.Aggressive, cooldown = 0f), dnd = true)
        // challenge roll but DND on → muted no-op with short cooldown (30 + rand*40), no pending/event
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.0f, 0.0f)))
        assertNull(next.pendingRequest)
        assertEquals(0, next.peerEventNonce)
        assertEquals(30f, next.peers.first().cooldown, EPS)
    }

    @Test
    fun peer_tick_friendly_still_fires_under_dnd() {
        val s = stateWith(peer(personality = Personality.Gentle, bond = 0f, cooldown = 0f), dnd = true)
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.5f, 0.0f)))
        assertEquals(0.05f, next.peers.first().bond, EPS) // friendly unaffected by DND
    }

    @Test
    fun peer_tick_single_request_gate_within_one_tick() {
        // Two ready aggressive peers; first creates the request, second must NOT roll
        // (pendingRequest now set) — proven by FixedRng having only 3 floats for peer #1.
        val s = stateWith(
            peer(id = "a", name = "A", personality = Personality.Aggressive, cooldown = 0f),
            peer(id = "b", name = "B", personality = Personality.Aggressive, cooldown = 0f),
        )
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.0f, 0.0f)))
        assertEquals("a", next.pendingRequest?.from)
    }

    // ── decline / dnd ──────────────────────────────────────────
    // (accept는 3차에서 BattleStart로 교체 — BattleReducerTest 참조)

    @Test
    fun decline_clears_request_and_echoes() {
        val s = stateWith(
            peer(id = "hrrk", name = "HRRK", cooldown = 100f),
            pending = PeerRequest("hrrk", RequestType.Challenge),
        )
        val next = reduce(s, Action.DeclineRequest, SeededRng(1L))
        assertNull(next.pendingRequest)
        assertEquals(1, next.peerEventNonce)
        assertEquals(PeerEventKind.Decline, next.peerEventLatest?.kind)
        assertNull(next.toast)
    }

    @Test
    fun decline_is_noop_without_request() {
        val s = stateWith(peer(cooldown = 100f))
        assertSame(s, reduce(s, Action.DeclineRequest, SeededRng(1L)))
    }

    @Test
    fun set_dnd_on_sets_flag_and_toast() {
        val s = stateWith(peer(cooldown = 100f))
        val next = reduce(s, Action.SetDnd(true), SeededRng(1L))
        assertTrue(next.dnd)
        assertEquals("DND ON", next.toast)
    }

    @Test
    fun set_dnd_off_clears_flag() {
        val s = stateWith(peer(cooldown = 100f), dnd = true)
        val next = reduce(s, Action.SetDnd(false), SeededRng(1L))
        assertTrue(!next.dnd)
        assertEquals("DND OFF", next.toast)
    }

    @Test
    fun set_dnd_on_auto_declines_pending_challenge() {
        val s = stateWith(
            peer(id = "hrrk", name = "HRRK", cooldown = 100f),
            pending = PeerRequest("hrrk", RequestType.Challenge),
        )
        val next = reduce(s, Action.SetDnd(true), SeededRng(1L))
        assertTrue(next.dnd)
        assertNull(next.pendingRequest)
        assertEquals(1, next.peerEventNonce)
        assertEquals(PeerEventKind.Decline, next.peerEventLatest?.kind)
        assertEquals("DND ON", next.toast)
    }
}
