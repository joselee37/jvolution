package today.superb.jvl.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import today.superb.jvl.core.Action
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.genetics.randomGenome
import today.superb.jvl.core.reduce
import today.superb.jvl.ui.breed.BreedAssayOverlay
import today.superb.jvl.ui.genome.GenomeScreen
import today.superb.jvl.ui.theme.Hue
import today.superb.jvl.ui.theme.JvlTheme
import today.superb.jvl.ui.tree.TreeScreen
import org.junit.Rule
import org.junit.Test

/**
 * Compose 시맨틱스 테스트(jvm/desktop, 에뮬레이터 불필요) — genetics UI 3화면이 결정론 상태에서
 * 올바른 정보를 렌더하는지 검증(픽셀이 아닌 텍스트/노드). 화면이 순수 (state)->@Composable이라
 * SeededRng 게놈을 주입해 격리 렌더한다.
 */
class GeneticsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun foe() = Peer(
        "hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f,
    )

    @Test
    fun genome_screen_renders_header_matrix_traits_lineage() {
        val state = GameState.initial("KAIJU", now = 0L, genome = randomGenome(SeededRng(7L)))
        rule.setContent { JvlTheme(Hue.Green) { GenomeScreen(state) } }

        rule.onNodeWithText("GENOME // G01_KAIJU", substring = true).assertExists()
        rule.onNodeWithText("mat", substring = true).assertExists()    // 대립유전자 매트릭스(모계 행)
        rule.onNodeWithText("pat", substring = true).assertExists()    // 부계 행
        rule.onNodeWithText("VIT", substring = true).assertIsDisplayed() // 스탯 형질
        rule.onNodeWithText("AGG", substring = true).assertExists()    // 행동 형질
        rule.onNodeWithText("line", substring = true).assertExists()   // 혈통 푸터
    }

    @Test
    fun breed_assay_renders_pair_inbreeding_and_actions() {
        val peer = foe()
        val state = GameState.initial("KAIJU", now = 0L, peers = listOf(peer), genome = randomGenome(SeededRng(7L)))
        rule.setContent { JvlTheme(Hue.Green) { BreedAssayOverlay(state = state, peer = peer) } }

        rule.onNodeWithText("PAIR-BOND ASSAY", substring = true).assertExists()
        rule.onNodeWithText("KAIJU", substring = true).assertExists()   // G01_KAIJU × HRRK
        rule.onNodeWithText("HRRK", substring = true).assertExists()
        rule.onNodeWithText("predicted inbreeding", substring = true).assertExists()
        rule.onNodeWithText("SAFE", substring = true).assertExists()    // 무관 founder × 피어 → F 0% SAFE
        rule.onNodeWithText("CONFIRM BREED", substring = true).assertExists()
        rule.onNodeWithText("CANCEL", substring = true).assertExists()
    }

    @Test
    fun tree_screen_renders_two_parent_pedigree() {
        // gen1(ALPHA) × 피어(HRRK) → gen2(BETA): 2부모 혈통 + 게놈 시그니처.
        val g1 = GameState.initial("ALPHA", now = 0L, peers = listOf(foe()), genome = randomGenome(SeededRng(1L)))
        val g2 = reduce(g1, Action.Breed(peerId = "hrrk", childName = "BETA", childId = "g2", now = 1_000L), SeededRng(2L))
        rule.setContent { JvlTheme(Hue.Green) { TreeScreen(g2) } }

        rule.onNodeWithText("ANCESTRY", substring = true).assertExists()
        rule.onNodeWithText("G02_BETA", substring = true).assertExists()   // 현역
        rule.onNodeWithText("G01_ALPHA", substring = true).assertExists()  // 아카이브된 조상
        // bred 자식에만 있는 2부모 조인(부모 id가 이름으로 해석됨: ALPHA × HRRK) — 유일.
        rule.onNodeWithText("× HRRK", substring = true).assertExists()
    }
}
