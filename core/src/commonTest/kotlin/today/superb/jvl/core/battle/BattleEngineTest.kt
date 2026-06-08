package today.superb.jvl.core.battle

import today.superb.jvl.core.FixedRng
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPS = 1e-4f

// crit 주사위를 항상 실패시키는 RNG(0.9 ≥ 0.05). resolveBattleTurn은 마지막에 crit float 1개 소비.
private fun noCrit() = FixedRng(listOf(0.9f))

class BattleEngineTest {

    // ── 결과 매트릭스 + 데미지 배수 ─────────────────────────────

    @Test
    fun ping_interrupts_charge_base_damage() {
        val r = resolveBattleTurn(BattleAction.Ping, BattleAction.Charge, training = 0f, discipline = 0f, peerStage = Stage.Juvenile, rng = noCrit())
        assertEquals("INTERRUPT", r.tag)
        assertEquals(0f, r.me, EPS)
        assertEquals(1.0f, r.them, EPS)
        assertFalse(r.crit)
    }

    @Test
    fun training_scales_outgoing_damage() {
        // charge vs dodge → BROKEN them=1.5; training 0.4 → attackMult 1.2 → 1.8
        val r = resolveBattleTurn(BattleAction.Charge, BattleAction.Dodge, training = 0.4f, discipline = 0f, peerStage = Stage.Juvenile, rng = noCrit())
        assertEquals("BROKEN", r.tag)
        assertEquals(1.8f, r.them, EPS)
        assertEquals(0f, r.me, EPS)
    }

    @Test
    fun discipline_and_peer_stage_scale_incoming_damage() {
        // dodge vs charge → BROKEN me=1.5; discipline 0.5 → defMult 0.85; adult power 1.3
        // 1.5 * 1.3 * 0.85 = 1.6575 → round1 1.7
        val r = resolveBattleTurn(BattleAction.Dodge, BattleAction.Charge, training = 0f, discipline = 0.5f, peerStage = Stage.Adult, rng = noCrit())
        assertEquals(1.7f, r.me, EPS)
    }

    @Test
    fun resonance_crit_doubles_nonzero_damage() {
        // ping vs ping INTERFERENCE 0.5/0.5; crit roll 0.0 < 0.05 → double → 1.0/1.0, tag RESONANCE
        val r = resolveBattleTurn(BattleAction.Ping, BattleAction.Ping, training = 0f, discipline = 0f, peerStage = Stage.Juvenile, rng = FixedRng(listOf(0.0f)))
        assertTrue(r.crit)
        assertEquals("RESONANCE", r.tag)
        assertEquals(1.0f, r.me, EPS)
        assertEquals(1.0f, r.them, EPS)
    }

    @Test
    fun crit_skipped_when_both_damage_zero() {
        // ping vs dodge BLOCKED 0/0; even with crit roll 0.0 no crit (no damage to double)
        val r = resolveBattleTurn(BattleAction.Ping, BattleAction.Dodge, training = 0f, discipline = 0f, peerStage = Stage.Juvenile, rng = FixedRng(listOf(0.0f)))
        assertFalse(r.crit)
        assertEquals("BLOCKED", r.tag)
        assertEquals(0f, r.me, EPS)
        assertEquals(0f, r.them, EPS)
    }

    @Test
    fun every_matrix_cell_is_defined() {
        // 16칸 전부 정의돼야 함(미정의 시 엔진이 throw하거나 MISS로 떨어지지 않도록).
        for (mine in BattleAction.entries) {
            for (theirs in BattleAction.entries) {
                val r = resolveBattleTurn(mine, theirs, 0f, 0f, Stage.Juvenile, noCrit())
                assertTrue(r.tag.isNotEmpty(), "$mine vs $theirs has no tag")
            }
        }
    }

    // ── NPC 무브 AI ────────────────────────────────────────────

    @Test
    fun aggressive_profile_picks_by_cumulative_distribution() {
        // aggressive: ping .30, charge .50, dodge .05, screech .15 (cum .30/.80/.85/1.0)
        assertEquals(BattleAction.Ping, pickNpcMove(Personality.Aggressive, emptyList(), FixedRng(listOf(0.0f))))
        assertEquals(BattleAction.Charge, pickNpcMove(Personality.Aggressive, emptyList(), FixedRng(listOf(0.4f))))
        assertEquals(BattleAction.Dodge, pickNpcMove(Personality.Aggressive, emptyList(), FixedRng(listOf(0.82f))))
        assertEquals(BattleAction.Screech, pickNpcMove(Personality.Aggressive, emptyList(), FixedRng(listOf(0.9f))))
    }

    @Test
    fun veteran_counters_most_common_recent_move() {
        // history most-common = Ping → counter Dodge; 0.0 < 0.7 → take the counter
        val move = pickNpcMove(
            Personality.Veteran,
            listOf(BattleAction.Ping, BattleAction.Ping, BattleAction.Charge),
            FixedRng(listOf(0.0f)),
        )
        assertEquals(BattleAction.Dodge, move)
    }

    @Test
    fun veteran_mixes_in_playful_thirty_percent() {
        // 0.7 ≥ 0.7 → not the counter; fall through to playful profile, r=0.1 → Ping
        val move = pickNpcMove(
            Personality.Veteran,
            listOf(BattleAction.Charge, BattleAction.Charge),
            FixedRng(listOf(0.7f, 0.1f)),
        )
        assertEquals(BattleAction.Ping, move)
    }

    @Test
    fun veteran_with_no_history_uses_playful() {
        // empty history → no read roll consumed, straight to playful profile, r=0.1 → Ping
        val move = pickNpcMove(Personality.Veteran, emptyList(), FixedRng(listOf(0.1f)))
        assertEquals(BattleAction.Ping, move)
    }

    // ── accept odds + narration ────────────────────────────────

    @Test
    fun accept_odds_per_personality() {
        assertEquals(0.85f, acceptOdds(Personality.Aggressive), EPS)
        assertEquals(0.65f, acceptOdds(Personality.Playful), EPS)
        assertEquals(0.55f, acceptOdds(Personality.Veteran), EPS)
        assertEquals(0.30f, acceptOdds(Personality.Gentle), EPS)
    }

    @Test
    fun narration_reflects_outcome_tags() {
        val miss = battleNarration(BattleAction.Dodge, BattleAction.Dodge, resolveBattleTurn(BattleAction.Dodge, BattleAction.Dodge, 0f, 0f, Stage.Juvenile, noCrit()), "NAUTI", "FOE")
        assertTrue(miss.contains("both whiff"), "MISS narration: $miss")

        val interrupt = battleNarration(BattleAction.Ping, BattleAction.Charge, resolveBattleTurn(BattleAction.Ping, BattleAction.Charge, 0f, 0f, Stage.Juvenile, noCrit()), "NAUTI", "FOE")
        assertTrue(interrupt.contains("NAUTI") && interrupt.contains("interrupts"), "INTERRUPT narration: $interrupt")

        val broken = battleNarration(BattleAction.Charge, BattleAction.Dodge, resolveBattleTurn(BattleAction.Charge, BattleAction.Dodge, 0f, 0f, Stage.Juvenile, noCrit()), "NAUTI", "FOE")
        assertTrue(broken.contains("smashes through"), "BROKEN narration: $broken")
    }

    @Test
    fun resolve_is_deterministic_under_seed() {
        val a = resolveBattleTurn(BattleAction.Charge, BattleAction.Charge, 0.2f, 0.1f, Stage.Adult, SeededRng(42L))
        val b = resolveBattleTurn(BattleAction.Charge, BattleAction.Charge, 0.2f, 0.1f, Stage.Adult, SeededRng(42L))
        assertEquals(a, b)
    }
}
