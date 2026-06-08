package today.superb.jvl.core

import today.superb.jvl.core.battle.BattleAction
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.core.battle.BattleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val EPS = 1e-4f

private fun foe(
    id: String = "hrrk",
    name: String = "HRRK",
    personality: Personality = Personality.Aggressive,
    stage: Stage = Stage.Adult,
    bond: Float = 0f,
    won: Int = 0,
    lost: Int = 0,
) = Peer(id, name, Species.Squid, stage, personality, bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = bond, battlesWon = won, battlesLost = lost, cooldown = 100f)

private fun gs(vararg peers: Peer, battle: BattleState? = null, pending: PeerRequest? = null) =
    GameState.initial("NAUTI", 0L, peers.toList()).copy(battle = battle, pendingRequest = pending)

class BattleReducerTest {

    @Test
    fun battle_start_enters_combat_and_clears_request() {
        val s = gs(foe(), pending = PeerRequest("hrrk", RequestType.Challenge))
        val next = reduce(s, Action.BattleStart("hrrk"), SeededRng(1L))
        assertEquals(View.Battle, next.view)
        assertNotNull(next.battle)
        assertEquals("hrrk", next.battle?.peerId)
        assertEquals(BattlePhase.Choose, next.battle?.phase)
        assertNull(next.pendingRequest)
        assertEquals("ENGAGE — HRRK", next.toast)
    }

    @Test
    fun battle_start_noop_for_unknown_peer() {
        val s = gs(foe())
        assertSame(s, reduce(s, Action.BattleStart("ghost"), SeededRng(1L)))
    }

    @Test
    fun cursor_wraps_and_only_in_choose() {
        val b = BattleState.start("hrrk")
        val s = gs(foe(), battle = b)
        assertEquals(2, reduce(s, Action.BattleCursor(set = 2), SeededRng(1L)).battle?.cursor)
        assertEquals(3, reduce(s, Action.BattleCursor(delta = -1), SeededRng(1L)).battle?.cursor) // 0-1 wraps to 3
        // not in choose → ignored
        val casting = gs(foe(), battle = b.copy(phase = BattlePhase.MyCast))
        assertEquals(0, reduce(casting, Action.BattleCursor(set = 2), SeededRng(1L)).battle?.cursor)
    }

    @Test
    fun commit_picks_moves_and_enters_cast() {
        val s = gs(foe(), battle = BattleState.start("hrrk")) // cursor 0 = Ping
        val next = reduce(s, Action.BattleCommit, SeededRng(42L)).battle
        assertNotNull(next)
        assertEquals(BattlePhase.MyCast, next.phase)
        assertEquals(BattleAction.Ping, next.myMove)
        assertNotNull(next.theirMove)
        assertEquals(listOf(BattleAction.Ping), next.myMoveHistory)
        assertEquals(1, next.log.size)
    }

    @Test
    fun advance_cast_steps_through_beats() {
        val b = BattleState.start("hrrk")
        val s = gs(foe(), battle = b.copy(phase = BattlePhase.MyCast))
        val toTheirCast = reduce(s, Action.BattleAdvanceCast, SeededRng(1L)).battle
        assertEquals(BattlePhase.TheirCast, toTheirCast?.phase)
        val toReveal = reduce(gs(foe(), battle = b.copy(phase = BattlePhase.TheirCast)), Action.BattleAdvanceCast, SeededRng(1L)).battle
        assertEquals(BattlePhase.Reveal, toReveal?.phase)
        // reveal → advance is a no-op (only Resolve moves it on)
        val stay = reduce(gs(foe(), battle = b.copy(phase = BattlePhase.Reveal)), Action.BattleAdvanceCast, SeededRng(1L)).battle
        assertEquals(BattlePhase.Reveal, stay?.phase)
    }

    @Test
    fun resolve_enters_damage_and_flashes_on_hit() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.Reveal, lastDmgThem = 1.0f, lastDmgMe = 0f)
        val next = reduce(gs(foe(), battle = b), Action.BattleResolve, SeededRng(1L)).battle
        assertEquals(BattlePhase.Damage, next?.phase)
        assertEquals(1, next?.flashNonceThem)   // they were hit
        assertEquals(0, next?.flashNonceMe)
    }

    @Test
    fun apply_damage_subtracts_hp_and_continues() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.Damage, lastDmgMe = 1f, lastDmgThem = 1f)
        val next = reduce(gs(foe(), battle = b), Action.BattleApplyDamage, SeededRng(1L)).battle
        assertNotNull(next)
        assertEquals(4f, next.hpMe, EPS)
        assertEquals(4f, next.hpThem, EPS)
        assertEquals(BattlePhase.Choose, next.phase)
        assertEquals(2, next.turn)
        assertNull(next.result)
        assertNull(next.myMove)
    }

    @Test
    fun apply_damage_resolves_win_on_ko() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.Damage, hpThem = 1f, lastDmgThem = 1.5f, hpMe = 5f, lastDmgMe = 0f)
        val next = reduce(gs(foe(), battle = b), Action.BattleApplyDamage, SeededRng(1L)).battle
        assertNotNull(next)
        assertEquals(0f, next.hpThem, EPS)
        assertEquals(BattlePhase.End, next.phase)
        assertEquals(BattleResult.Win, next.result)
    }

    @Test
    fun apply_damage_double_ko_is_draw() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.Damage, hpMe = 1f, lastDmgMe = 1.5f, hpThem = 1f, lastDmgThem = 1.5f)
        val next = reduce(gs(foe(), battle = b), Action.BattleApplyDamage, SeededRng(1L)).battle
        assertEquals(BattleResult.Draw, next?.result)
    }

    @Test
    fun flee_ends_battle_as_flee() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.Choose)
        val next = reduce(gs(foe(), battle = b), Action.BattleFlee, SeededRng(1L)).battle
        assertEquals(BattlePhase.End, next?.phase)
        assertEquals(BattleResult.Flee, next?.result)
    }

    @Test
    fun end_win_applies_rewards_and_returns_to_sonar() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.End, result = BattleResult.Win)
        val s = gs(foe(bond = 0.2f), battle = b).copy(happiness = 0.6f, evolveProgress = 0f)
        val next = reduce(s, Action.BattleEnd, SeededRng(1L))
        assertNull(next.battle)
        assertEquals(View.Sonar, next.view)
        assertEquals(0.20f, next.evolveProgress, EPS)
        assertEquals(0.65f, next.happiness, EPS)
        val peer = next.peers.first { it.id == "hrrk" }
        assertEquals(1, peer.battlesLost)
        assertEquals(0.30f, peer.bond, EPS)
        assertEquals("VICTORY", next.toast)
    }

    @Test
    fun end_lose_records_peer_win_and_penalises() {
        val b = BattleState.start("hrrk").copy(phase = BattlePhase.End, result = BattleResult.Lose)
        val s = gs(foe(bond = 0.2f), battle = b).copy(happiness = 0.6f, discipline = 0.2f)
        val next = reduce(s, Action.BattleEnd, SeededRng(1L))
        assertEquals(0.45f, next.happiness, EPS)     // 0.6 - 0.15
        assertEquals(0.25f, next.discipline, EPS)    // 0.2 + 0.05
        assertEquals(1, next.peers.first().battlesWon)
        assertEquals("DEFEATED", next.toast)
    }

    @Test
    fun active_battle_suppresses_peer_challenges() {
        // peer ready to challenge, but battle in progress → DND forced → no pending created
        val ready = foe(personality = Personality.Aggressive).copy(cooldown = 0f)
        val s = gs(ready, battle = BattleState.start("other"))
        val next = reduce(s, Action.PeerTick(1f), FixedRng(listOf(0.0f, 0.0f, 0.0f)))
        assertNull(next.pendingRequest)
    }
}
