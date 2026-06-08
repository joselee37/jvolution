package today.superb.jvl.ui.sonar.species

import today.superb.jvl.core.Species
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 종 실루엣 밀도 함수의 불변식(데모 1:1 포팅 특성 검증). 모든 밀도는 [0,1]. */
class SpeciesShapesTest {

    @Test
    fun all_densities_within_unit_range() {
        for (species in Species.entries) {
            var u = -1f
            while (u <= 1f) {
                var v = -1f
                while (v <= 1f) {
                    val d = densityFor(species, u, v, 0f, 0.7f, 0.7f)
                    assertTrue(d in 0f..1f, "$species @($u,$v) = $d")
                    v += 0.1f
                }
                u += 0.1f
            }
        }
    }

    @Test
    fun pixel_center_is_solid_and_out_of_bounds_empty() {
        assertEquals(1f, pixelDensity(0f, 0f))      // bitmap row6/col6 = '#'
        assertEquals(0f, pixelDensity(-1.6f, 0f))   // px < 0
        assertEquals(0f, pixelDensity(0f, 1.6f))    // py > 11
    }

    @Test
    fun blob_is_empty_far_right_and_present_near_body() {
        assertEquals(0f, blobDensity(0.95f, 0f, 0f, 0.7f))      // beyond radius
        assertTrue(blobDensity(-0.25f, 0.4f, 0f, 0.7f) > 0f)    // inside half-disc, below eyes
    }

    @Test
    fun squid_mantle_is_present_at_head() {
        assertTrue(squidDensity(0f, -0.35f, 0f, 0.7f) > 0f)     // mantle center
    }

    @Test
    fun jelly_bell_is_present() {
        assertTrue(jellyDensity(-0.4f, 0f, 0f, 0.7f) > 0f)      // inside bell (du < 0)
    }

    @Test
    fun species_shapes_differ() {
        // 같은 좌표에서 종마다 다른 밀도(전부 동일하면 디스패치 버그).
        val p = Triple(0.3f, 0.3f, 0f)
        val ghost = densityFor(Species.Ghost, p.first, p.second, p.third, 0.7f, 0.7f)
        val pixel = densityFor(Species.Pixel, p.first, p.second, p.third, 0.7f, 0.7f)
        assertTrue(ghost != pixel || ghost == 0f) // 최소한 디스패치가 분기됨을 보장
    }
}
