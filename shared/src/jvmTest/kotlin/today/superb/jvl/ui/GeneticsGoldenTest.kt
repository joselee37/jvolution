package today.superb.jvl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import today.superb.jvl.core.GameState
import today.superb.jvl.core.Peer
import today.superb.jvl.core.Personality
import today.superb.jvl.core.SeededRng
import today.superb.jvl.core.Species
import today.superb.jvl.core.Stage
import today.superb.jvl.core.genetics.randomGenome
import today.superb.jvl.ui.breed.BreedAssayOverlay
import today.superb.jvl.ui.genome.GenomeScreen
import today.superb.jvl.ui.theme.Hue
import today.superb.jvl.ui.theme.JvlTheme
import today.superb.jvl.ui.theme.LocalPalette
import org.junit.Rule
import org.junit.Test

/**
 * 골든 스크린샷(jvm/desktop, 에뮬레이터 불필요) — 결정론적·시간 비의존 화면의 픽셀 회귀를 잡는다.
 * GenomeScreen/BreedAssayOverlay는 nowMillis를 안 읽어 골든이 안정적. (TreeScreen은 상대시간 텍스트,
 * 생명체 캔버스는 withFrameNanos 애니메이션이라 골든 비대상 — 시맨틱스/하네스가 커버.)
 */
class GeneticsGoldenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun peer() = Peer(
        "hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive,
        bearing = 0f, range = 0.5f, bearingVel = 0f, rangeVel = 0f, bond = 0f, battlesWon = 0, battlesLost = 0, cooldown = 100f,
    )

    /** 고정 크기 phosphor 프레임에 화면을 렌더하고 골든과 비교. */
    private fun golden(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            JvlTheme(Hue.Green) {
                Box(Modifier.size(380.dp, 760.dp).background(LocalPalette.current.bg)) { content() }
            }
        }
        assertGolden(name, rule.onRoot().captureToImage().toPngBytes())
    }

    @Test
    fun genome_screen_golden() {
        val state = GameState.initial("KAIJU", now = 0L, genome = randomGenome(SeededRng(7L)))
        golden("genome-screen") { GenomeScreen(state) }
    }

    @Test
    fun breed_assay_golden() {
        val p = peer()
        val state = GameState.initial("KAIJU", now = 0L, peers = listOf(p), genome = randomGenome(SeededRng(7L)))
        golden("breed-assay") { BreedAssayOverlay(state = state, peer = p) }
    }
}
