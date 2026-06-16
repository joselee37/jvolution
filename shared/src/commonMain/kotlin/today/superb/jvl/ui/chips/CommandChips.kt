package today.superb.jvl.ui.chips

import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.View

/** 칩 강조 단계 — Normal(케어/네비) / Highlight(기회: evolve·challenge) / Alert(응답 필요: accept 등). */
enum class ChipEmphasis { Normal, Highlight, Alert }

/** 명령 칩 — 탭하면 [command]가 터미널 매크로(`submitCommand`)로 실행된다. */
data class CommandChip(
    val label: String,
    val command: String,
    val emphasis: ChipEmphasis = ChipEmphasis.Normal,
)

private fun chip(label: String, command: String = label.lowercase()) = CommandChip(label, command)

/**
 * 현재 상태에서 보여줄 명령 칩 목록. 순수 함수(commonTest 대상).
 *
 * 우선순위(앞에서부터): 응답 필요(Alert) → 기회(★EVOLVE) → 뷰 컨텍스트(BACK/CHALLENGE) →
 * 케어(수면 중엔 WAKE가 선두) → 네비(RADAR/TREE, 소나에서만).
 * 전투 중에는 FLEE만 — responder의 `allowedInBattle`과 일치(케어 칩을 눌러봤자 locked라
 * 보여주지 않는 것이 옳다).
 * 전투 중 responder가 허용하는 Help/Clear/Sound 등은 인지 부하를 줄이기 위해 칩에서 의도적으로 제외.
 * CHALLENGE 칩 command는 피어 이름이 단일 토큰이라는 전제(파서가 첫 인자만 취함 — 현 로스터 전부 충족).
 *
 * @param selectedPeer 레이더에서 탭으로 선택된 블립(화면 local state — GameState 밖).
 */
fun chipsFor(state: GameState, selectedPeer: Peer? = null): List<CommandChip> {
    if (state.battle != null) return listOf(CommandChip("FLEE", "flee", ChipEmphasis.Alert))

    return buildList {
        if (state.pendingRequest != null) {
            add(CommandChip("ACCEPT", "accept", ChipEmphasis.Alert))
            add(CommandChip("DECLINE", "decline", ChipEmphasis.Alert))
        }
        if (state.canEvolve && !state.evolving) {
            add(CommandChip("★EVOLVE", "evolve", ChipEmphasis.Highlight))
        }
        when (state.view) {
            View.Radar -> {
                add(chip("BACK", "back"))
                if (selectedPeer != null) {
                    add(
                        CommandChip(
                            label = "CHALLENGE ${selectedPeer.name}",
                            command = "challenge ${selectedPeer.name.lowercase()}",
                            emphasis = ChipEmphasis.Highlight,
                        ),
                    )
                    add(
                        CommandChip(
                            label = "BREED ${selectedPeer.name}",
                            command = "breed ${selectedPeer.name.lowercase()}",
                            emphasis = ChipEmphasis.Highlight,
                        ),
                    )
                }
            }
            View.Tree, View.Genome -> add(chip("BACK", "back"))
            View.Battle -> {} // battle != null 가드가 위에서 FLEE를 반환 — 정상 경로에선 도달 불가
            else -> {}
        }
        if (state.asleep) add(chip("WAKE"))
        add(chip("FEED"))
        add(chip("PLAY"))
        add(chip("CLEAN"))
        add(chip("TRAIN"))
        add(chip("HEAL"))
        if (!state.asleep) add(chip("SLEEP"))
        add(chip("SCOLD"))
        if (state.view == View.Sonar) {
            add(chip("GENOME", "genome"))
            add(chip("RADAR", "scan"))
            add(chip("TREE", "tree"))
        }
    }
}
