package today.superb.jvl.core

import today.superb.jvl.core.battle.BattleAction
import today.superb.jvl.core.battle.BattleLogEntry
import today.superb.jvl.core.battle.BattlePhase
import today.superb.jvl.core.battle.BattleResult
import today.superb.jvl.core.battle.BattleState
import today.superb.jvl.core.battle.battleNarration
import today.superb.jvl.core.battle.pickNpcMove
import today.superb.jvl.core.battle.resolveBattleTurn
import today.superb.jvl.core.genetics.Ancestor
import today.superb.jvl.core.genetics.Lineage
import today.superb.jvl.core.genetics.breed
import kotlin.math.abs
import kotlin.math.roundToInt

/** 스탯 드리프트 계수(초당). dt를 곱해 적용. 데모 `tick` 케이스 1:1. */
private object Drift {
    const val ASLEEP_ENERGY = 0.02f
    const val ASLEEP_HUNGER = 0.005f
    const val AWAKE_HUNGER = 0.012f
    const val AWAKE_DIRTY = 0.008f
    const val AWAKE_ENERGY = -0.01f
    const val AWAKE_HAPPINESS = -0.015f
    const val EVOLVE_PROGRESS = 0.005f

    /** awake일 때 행복이 깎이기 시작하는 hunger/dirty 임계. */
    const val DISCOMFORT_THRESHOLD = 0.7f
}

private fun Float.clamp(): Float = coerceIn(0f, 1f)

/**
 * 순수 reducer — 데모 `reduce(s, a)` 1:1 포팅.
 *
 * 결정성: wall-clock(`nowMillis`)을 읽지 않고, 부수효과(토스트 만료 타이머 등)는 ViewModel이 소유한다.
 * [rng]는 향후 talk/peer 액션에서 사용(케어 액션은 결정적이라 미사용).
 */
fun reduce(state: GameState, action: Action, rng: Rng): GameState {
    fun log(msg: String): List<LogEntry> =
        (listOf(LogEntry(cycle = state.cycles + 1, msg = msg)) + state.log).take(GameState.LOG_CAP)

    return when (action) {
        is Action.Tick -> {
            val dt = action.dt
            var happiness = state.happiness
            var energy = state.energy
            var hunger = state.hunger
            var dirty = state.dirty
            if (state.asleep) {
                energy = (energy + Drift.ASLEEP_ENERGY * dt).clamp()
                hunger = (hunger + Drift.ASLEEP_HUNGER * dt).clamp()
            } else {
                hunger = (hunger + Drift.AWAKE_HUNGER * dt).clamp()
                dirty = (dirty + Drift.AWAKE_DIRTY * dt).clamp()
                energy = (energy + Drift.AWAKE_ENERGY * dt).clamp()
                if (hunger > Drift.DISCOMFORT_THRESHOLD || dirty > Drift.DISCOMFORT_THRESHOLD) {
                    happiness = (happiness + Drift.AWAKE_HAPPINESS * dt).clamp()
                }
            }
            val evolveProgress = (state.evolveProgress + Drift.EVOLVE_PROGRESS * dt).clamp()
            state.copy(
                happiness = happiness,
                energy = energy,
                hunger = hunger,
                dirty = dirty,
                evolveProgress = evolveProgress,
                canEvolve = state.stage.canAdvance() && evolveProgress >= 1f,
            )
        }

        Action.Ping -> state.copy(
            cycles = state.cycles + 1,
            bond = (state.bond + 0.03f).clamp(),
            pingNonce = state.pingNonce + 1,
        )

        Action.Feed -> state.copy(
            cycles = state.cycles + 1,
            hunger = (state.hunger - 0.25f).clamp(),
            happiness = (state.happiness + 0.05f).clamp(),
            dirty = (state.dirty + 0.03f).clamp(),
            log = log("FEED — ration dispensed"),
            toast = "NOM NOM",
        )

        Action.Play -> state.copy(
            cycles = state.cycles + 1,
            happiness = (state.happiness + 0.2f).clamp(),
            energy = (state.energy - 0.08f).clamp(),
            hunger = (state.hunger + 0.04f).clamp(),
            bond = (state.bond + 0.04f).clamp(),
            log = log("PLAY — bond sequence"),
            toast = "YIPPEE",
        )

        Action.Clean -> state.copy(
            cycles = state.cycles + 1,
            dirty = 0f,
            happiness = (state.happiness + 0.05f).clamp(),
            log = log("CLEAN — tank flushed"),
            toast = "TANK FLUSHED",
        )

        Action.Sleep -> state.copy(
            cycles = state.cycles + 1,
            asleep = !state.asleep,
            log = log(if (state.asleep) "WAKE" else "SLEEP — lights out"),
            toast = if (state.asleep) "AWAKE" else "GOOD NIGHT",
        )

        Action.Train -> state.copy(
            cycles = state.cycles + 1,
            training = (state.training + 0.15f).clamp(),
            discipline = (state.discipline + 0.05f).clamp(),
            energy = (state.energy - 0.08f).clamp(),
            hunger = (state.hunger + 0.04f).clamp(),
            evolveProgress = (state.evolveProgress + 0.04f).clamp(),
            log = log("TRAIN — drill complete"),
            toast = "DRILL OK",
        )

        Action.Discipline -> state.copy(
            cycles = state.cycles + 1,
            discipline = (state.discipline + 0.1f).clamp(),
            happiness = (state.happiness - 0.08f).clamp(),
            bond = (state.bond - 0.02f).clamp(),
            disciplineFlash = true,
            log = log("SCOLD — reprimand"),
            toast = "SCOLDED",
        )

        Action.Heal -> state.copy(
            cycles = state.cycles + 1,
            energy = (state.energy + 0.3f).clamp(),
            happiness = (state.happiness + 0.05f).clamp(),
            log = log("HEAL — biopatch"),
            toast = "PATCHED",
        )

        Action.Evolve ->
            if (!state.stage.canAdvance()) state
            else state.copy(evolving = true, toast = "EVOLVING")

        Action.EvolveComplete -> {
            val next = state.stage.next()
            state.copy(
                cycles = state.cycles + 1,
                stage = next,
                evolveProgress = 0f,
                canEvolve = false,
                evolving = false,
                log = log("EVOLVE — ${next.name.uppercase()} stage"),
                toast = "→ ${next.name.uppercase()}",
            )
        }

        is Action.Rename -> state.copy(name = action.name, log = log("RENAME — ${action.name}"))

        is Action.SetSpecies -> state.copy(species = action.species)

        // 2차 선반영 — 1차에서는 dispatch되지 않음(Action.SetView KDoc 참조).
        is Action.SetView -> state.copy(view = action.view)

        Action.ClearToast -> state.copy(toast = null)

        Action.ClearDisciplineFlash -> state.copy(disciplineFlash = false)

        Action.ToggleSound -> state.copy(sound = !state.sound, toast = if (state.sound) "MUTED" else "SOUND ON")

        // ── 피어 / 레이더 (2차) — 데모 peerTick/accept/decline/setDnd 1:1 ──

        is Action.PeerTick -> {
            val dt = action.dt
            // single-request gate가 한 틱 안에서도 성립하도록 pendingRequest 등 누적값을 peer 반복에
            // 걸쳐 갱신한다(데모는 .map 클로저 변수로 처리 — Kotlin map도 순차 실행이라 동형).
            var pendingRequest = state.pendingRequest
            var toast = state.toast
            var peerEventNonce = state.peerEventNonce
            var peerEventLatest = state.peerEventLatest
            // 전투 중에는 DND 강제 on으로 간주(새 도전이 끼어들지 않음).
            val dndActive = state.dnd || state.battle != null

            val peers = state.peers.map { p ->
                var bearing = ((p.bearing + p.bearingVel * dt) % 360f + 360f) % 360f
                var range = p.range + p.rangeVel * dt
                var rangeVel = p.rangeVel
                if (range <= 0.20f) { range = 0.20f; rangeVel = abs(rangeVel) }
                if (range >= 0.92f) { range = 0.92f; rangeVel = -abs(rangeVel) }
                var cooldown = maxOf(0f, p.cooldown - dt)
                var bond = p.bond

                // 발동 판정: 쿨다운 0 && 대기 요청 없음 && 6% 주사위(데모 short-circuit 순서 보존).
                if (cooldown == 0f && pendingRequest == null && rng.nextFloat() < 0.06f) {
                    val roll = rng.nextFloat()
                    val wantsChallenge = roll < p.personality.challenge
                    val wantsFriendly = !wantsChallenge && roll < p.personality.challenge + p.personality.friendly
                    when {
                        wantsChallenge && dndActive ->
                            // DND/전투로 도전 억제 — 우호로 바꾸지 않고 짧은 쿨다운으로 재판정만.
                            cooldown = 30f + rng.nextFloat() * 40f

                        wantsChallenge -> {
                            pendingRequest = PeerRequest(p.id, RequestType.Challenge)
                            toast = "${p.name} CHALLENGES"
                            peerEventNonce += 1
                            peerEventLatest = PeerEvent(
                                PeerEventKind.Challenge, p.id,
                                listOf(
                                    "",
                                    "▸▸▸ INCOMING ▸▸▸",
                                    "  ${p.name} (${p.species.name.lowercase()} · ${p.stage.name.lowercase()}) pings the channel.",
                                    "  type `accept` to engage, `decline` to dismiss.",
                                    "",
                                ),
                            )
                            cooldown = 90f + rng.nextFloat() * 60f
                        }

                        wantsFriendly -> {
                            bond = minOf(1f, bond + 0.05f)
                            toast = "${p.name} APPROACHES"
                            peerEventNonce += 1
                            peerEventLatest = PeerEvent(
                                PeerEventKind.Friendly, p.id,
                                listOf("▸ ${p.name} drifts close. peer-bond +5% → ${(bond * 100).roundToInt()}%."),
                            )
                            cooldown = 60f + rng.nextFloat() * 40f
                        }

                        else -> cooldown = 30f + rng.nextFloat() * 40f
                    }
                }

                p.copy(bearing = bearing, range = range, rangeVel = rangeVel, cooldown = cooldown, bond = bond)
            }

            state.copy(
                peers = peers,
                pendingRequest = pendingRequest,
                toast = toast,
                peerEventNonce = peerEventNonce,
                peerEventLatest = peerEventLatest,
            )
        }

        Action.DeclineRequest -> {
            val req = state.pendingRequest
            if (req == null) {
                state
            } else {
                val name = state.peers.find { it.id == req.from }?.name ?: req.from
                state.copy(
                    pendingRequest = null,
                    peerEventNonce = state.peerEventNonce + 1,
                    peerEventLatest = PeerEvent(PeerEventKind.Decline, req.from, listOf("▸ declined $name.")),
                    toast = null,
                )
            }
        }

        is Action.SetDnd -> {
            val req = state.pendingRequest
            if (action.on && req != null) {
                // 명세 05: DND를 켤 때 대기 도전이 있으면 자동 거절(도메인 규칙으로 흡수).
                val name = state.peers.find { it.id == req.from }?.name ?: req.from
                state.copy(
                    dnd = true,
                    pendingRequest = null,
                    peerEventNonce = state.peerEventNonce + 1,
                    peerEventLatest = PeerEvent(PeerEventKind.Decline, req.from, listOf("▸ declined $name.")),
                    toast = "DND ON",
                )
            } else {
                state.copy(dnd = action.on, toast = if (action.on) "DND ON" else "DND OFF")
            }
        }

        // ── 전투 (3차) — 데모 battle* reducer 케이스 1:1 ──

        is Action.BattleStart -> {
            val peer = state.peers.find { it.id == action.peerId }
            if (peer == null) {
                state
            } else {
                state.copy(
                    view = View.Battle,
                    battle = BattleState.start(action.peerId),
                    pendingRequest = null,
                    toast = "ENGAGE — ${peer.name}",
                )
            }
        }

        is Action.BattleCursor -> {
            val b = state.battle
            if (b == null || b.phase != BattlePhase.Choose) {
                state
            } else {
                val n = BattleAction.entries.size
                val cursor = action.set ?: ((b.cursor + action.delta + n) % n)
                state.copy(battle = b.copy(cursor = cursor))
            }
        }

        Action.BattleCommit -> {
            val b = state.battle
            val peer = b?.let { bb -> state.peers.find { it.id == bb.peerId } }
            if (b == null || b.phase != BattlePhase.Choose || peer == null) {
                state
            } else {
                val my = BattleAction.entries[b.cursor]
                val their = pickNpcMove(peer.personality, b.myMoveHistory, rng)
                val out = resolveBattleTurn(my, their, state.training, state.discipline, peer.stage, rng)
                val narration = battleNarration(my, their, out, state.name, peer.name)
                state.copy(
                    battle = b.copy(
                        myMove = my,
                        theirMove = their,
                        phase = BattlePhase.MyCast,
                        myMoveHistory = b.myMoveHistory + my,
                        lastDmgMe = out.me,
                        lastDmgThem = out.them,
                        log = (listOf(BattleLogEntry(out.tag, narration, out.crit, out.me, out.them)) + b.log)
                            .take(BattleState.LOG_CAP),
                    ),
                )
            }
        }

        Action.BattleAdvanceCast -> {
            val b = state.battle
            val next = when (b?.phase) {
                BattlePhase.MyCast -> BattlePhase.TheirCast
                BattlePhase.TheirCast -> BattlePhase.Reveal
                else -> null
            }
            if (b == null || next == null) state else state.copy(battle = b.copy(phase = next))
        }

        Action.BattleResolve -> {
            val b = state.battle
            if (b == null || b.phase != BattlePhase.Reveal) {
                state
            } else {
                state.copy(
                    battle = b.copy(
                        phase = BattlePhase.Damage,
                        flashNonceMe = b.flashNonceMe + if (b.lastDmgMe > 0f) 1 else 0,
                        flashNonceThem = b.flashNonceThem + if (b.lastDmgThem > 0f) 1 else 0,
                    ),
                )
            }
        }

        Action.BattleApplyDamage -> {
            val b = state.battle
            if (b == null || b.phase != BattlePhase.Damage) {
                state
            } else {
                val hpMe = maxOf(0f, b.hpMe - b.lastDmgMe)
                val hpThem = maxOf(0f, b.hpThem - b.lastDmgThem)
                val ko = hpMe <= 0f || hpThem <= 0f
                val result = when {
                    !ko -> null
                    hpMe <= 0f && hpThem <= 0f -> BattleResult.Draw
                    hpMe <= 0f -> BattleResult.Lose
                    else -> BattleResult.Win
                }
                state.copy(
                    battle = b.copy(
                        hpMe = hpMe,
                        hpThem = hpThem,
                        phase = if (ko) BattlePhase.End else BattlePhase.Choose,
                        result = result,
                        myMove = if (ko) b.myMove else null,
                        theirMove = if (ko) b.theirMove else null,
                        turn = if (ko) b.turn else b.turn + 1,
                    ),
                )
            }
        }

        Action.BattleFlee -> {
            val b = state.battle
            if (b == null || b.phase == BattlePhase.End) {
                state
            } else {
                state.copy(battle = b.copy(phase = BattlePhase.End, result = BattleResult.Flee))
            }
        }

        Action.BattleEnd -> {
            val b = state.battle
            if (b == null) {
                state
            } else {
                val peerId = b.peerId
                var happiness = state.happiness
                var discipline = state.discipline
                var evolveProgress = state.evolveProgress
                val peers = when (b.result) {
                    BattleResult.Win -> {
                        evolveProgress = (evolveProgress + 0.20f).clamp()
                        happiness = (happiness + 0.05f).clamp()
                        state.peers.map {
                            if (it.id == peerId) it.copy(battlesLost = it.battlesLost + 1, bond = minOf(1f, it.bond + 0.10f)) else it
                        }
                    }
                    BattleResult.Lose -> {
                        happiness = (happiness - 0.15f).clamp()
                        discipline = (discipline + 0.05f).clamp()
                        state.peers.map {
                            if (it.id == peerId) it.copy(battlesWon = it.battlesWon + 1, bond = minOf(1f, it.bond + 0.04f)) else it
                        }
                    }
                    BattleResult.Draw -> {
                        happiness = (happiness + 0.03f).clamp()
                        state.peers.map { if (it.id == peerId) it.copy(bond = minOf(1f, it.bond + 0.06f)) else it }
                    }
                    BattleResult.Flee -> {
                        happiness = (happiness - 0.05f).clamp()
                        state.peers.map { if (it.id == peerId) it.copy(bond = maxOf(0f, it.bond - 0.05f)) else it }
                    }
                    null -> state.peers
                }
                val toast = when (b.result) {
                    BattleResult.Win -> "VICTORY"
                    BattleResult.Lose -> "DEFEATED"
                    BattleResult.Draw -> "STALEMATE"
                    BattleResult.Flee -> "DISENGAGED"
                    null -> null
                }
                state.copy(
                    view = View.Sonar,
                    battle = null,
                    happiness = happiness,
                    discipline = discipline,
                    evolveProgress = evolveProgress,
                    peers = peers,
                    toast = toast,
                )
            }
        }

        // ── 계보 / 번식 (4차) — 설계 §8 ──

        is Action.Breed -> {
            val peer = state.peers.find { it.id == action.peerId }
            if (peer == null) {
                state   // 미상 피어 → no-op.
            } else {
                // 현재 개체를 Ancestor로 아카이브. 부모는 현 개체 자신의 부모(state.mother/fatherId).
                val archived = Ancestor(
                    id = state.creatureId,
                    gen = state.gen,
                    name = state.name,
                    species = state.species,
                    stage = state.stage,
                    genome = state.genome,
                    motherId = state.motherId,
                    fatherId = state.fatherId,
                    cycles = state.cycles,
                    happiness = (state.happiness * 100).roundToInt(),
                    energy = (state.energy * 100).roundToInt(),
                    bond = (state.bond * 100).roundToInt(),
                    discipline = (state.discipline * 100).roundToInt(),
                    training = (state.training * 100).roundToInt(),
                    hatchedAt = state.hatchedAt,
                    archivedAt = action.now,
                )
                // 피어 공동부모가 혈통에 없으면 founder(gen=0, 부모 없음)로 기록(중복 방지).
                val peerKnown = state.lineage.ancestors.any { it.id == peer.id }
                val peerFounder = if (peerKnown) null else Ancestor(
                    id = peer.id,
                    gen = 0,
                    name = peer.name,
                    species = peer.species,
                    stage = peer.stage,
                    genome = peer.genome,
                    motherId = null,
                    fatherId = null,
                    cycles = 0,
                    happiness = 0,
                    energy = 0,
                    bond = (peer.bond * 100).roundToInt(),
                    discipline = 0,
                    training = 0,
                    hatchedAt = action.now,
                    archivedAt = action.now,
                )
                val updatedLineage = Lineage(
                    state.lineage.ancestors + archived + listOfNotNull(peerFounder),
                )
                // 자식 게놈 = 이배체 재조합 + 돌연변이. 종은 50%로 한쪽 부모(enum이라 게놈 밖).
                val child = breed(state.genome, peer.genome, rng)
                val childSpecies = if (rng.nextFloat() < 0.5f) state.species else peer.species
                // 새 알 — 스탯/단계 초기화(게놈 시드). 피어/유대/전적·대기요청·뷰는 생명체와 독립이라 보존.
                GameState.initial(action.childName, action.now, state.peers, child, action.childId).copy(
                    gen = state.gen + 1,
                    species = childSpecies,
                    lineage = updatedLineage,
                    motherId = state.creatureId,
                    fatherId = peer.id,
                    view = state.view,
                    pendingRequest = state.pendingRequest,
                    peerEventNonce = state.peerEventNonce,
                    peerEventLatest = state.peerEventLatest,
                )
            }
        }
    }
}
