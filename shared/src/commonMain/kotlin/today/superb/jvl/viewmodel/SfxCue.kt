package today.superb.jvl.viewmodel

import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.PeerEventKind
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.sound.Sfx

/**
 * 액션 + 상태 전이(before/after) → 재생할 SFX(없으면 null). 순수 함수 — [GameViewModel.dispatch]가 reduce
 * 전후 상태로 호출한다. 음소거 게이트(`after.sound`)는 호출부 책임(여기서 보지 않는다).
 *
 * no-op 액션(전이 없음)에는 소리를 내지 않는다 — 가드된 액션이 거부된 경우 조용해야 하므로
 * 의미 전이를 before/after로 직접 확인한다.
 */
fun sfxCueFor(action: Action, before: GameState, after: GameState): Sfx? = when (action) {
    Action.Ping -> if (after.pingNonce != before.pingNonce) Sfx.Ping else null

    Action.Feed, Action.Play, Action.Clean, Action.Heal, Action.Train -> Sfx.Care
    Action.Discipline -> Sfx.Scold
    Action.Sleep -> when {
        after.asleep && !before.asleep -> Sfx.SleepCue
        !after.asleep && before.asleep -> Sfx.WakeCue
        else -> null
    }

    Action.Evolve -> if (after.evolving && !before.evolving) Sfx.Evolve else null
    Action.EvolveComplete -> if (after.stage != before.stage) Sfx.EvolveDone else null

    is Action.PeerTick -> when {
        // pendingRequest null→nonnull은 challenge에서만 발생(single-request gate; friendly는 pendingRequest를 안 바꿈).
        before.pendingRequest == null && after.pendingRequest != null -> Sfx.Alert
        after.peerEventNonce != before.peerEventNonce &&
            after.peerEventLatest?.kind == PeerEventKind.Friendly -> Sfx.Friendly
        else -> null
    }

    // 이미 전투 중이면 무음(가드된 액션)
    is Action.BattleStart -> if (before.battle == null && after.battle != null) Sfx.Alert else null

    Action.BattleResolve -> {
        val b = after.battle
        val p = before.battle
        when {
            b == null || p == null -> null
            b.flashNonceMe == p.flashNonceMe && b.flashNonceThem == p.flashNonceThem -> null // 양측 빗나감
            b.log.firstOrNull()?.crit == true -> Sfx.Crit
            else -> Sfx.Hit
        }
    }

    Action.BattleApplyDamage -> when (after.battle?.result) {
        BattleResult.Win -> Sfx.Win
        BattleResult.Lose -> Sfx.Lose
        BattleResult.Draw -> Sfx.Disengage
        else -> null // 전투 계속(다음 턴) 또는 Flee는 별도 액션
    }

    Action.BattleFlee -> if (after.battle?.result == BattleResult.Flee) Sfx.Disengage else null

    Action.ToggleSound -> if (after.sound) Sfx.Confirm else null
    is Action.Breed -> Sfx.Confirm

    else -> null
}
