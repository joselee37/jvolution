package today.superb.jvl.ui.sonar.species

import today.superb.jvl.core.Species
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 종별 실루엣 밀도 디스패처. (u, v)는 [-1, 1] 정규 좌표 → 밀도 [0, 1]. 데모 `creature.jsx` `SPECIES` 1:1.
 *
 * Ghost는 [happiness](동공/표정), Blob/Jelly/Squid는 [energy](크기/촉수 흔들림), Pixel은 고정 비트맵.
 * 전투에서 상대 생명체의 [energy]에 HP 비율을 넘기면 체력에 따라 위축된다.
 */
fun densityFor(species: Species, u: Float, v: Float, t: Float, happiness: Float, energy: Float): Float = when (species) {
    Species.Ghost -> ghostDensity(u, v, t, happiness)
    Species.Blob -> blobDensity(u, v, t, energy)
    Species.Jelly -> jellyDensity(u, v, t, energy)
    Species.Squid -> squidDensity(u, v, t, energy)
    Species.Pixel -> pixelDensity(u, v)
}

/** 반원형 블롭 — 오른쪽으로 불룩, 눈 2개. 데모 `SPECIES.blob` 1:1. */
fun blobDensity(u: Float, v: Float, t: Float, energy: Float): Float {
    val cx = -0.25f
    val dx = u - cx
    val dy = v
    val wave = sin(v * 6f + t * 0.5f) * 0.04f
    val radius = 0.78f + wave + (energy - 0.5f) * 0.05f
    val r = sqrt(dx * dx + dy * dy)
    if (r > radius) return 0f
    val e1 = sqrt((dx + 0.15f) * (dx + 0.15f) + (dy + 0.20f) * (dy + 0.20f))
    val e2 = sqrt((dx + 0.15f) * (dx + 0.15f) + (dy - 0.20f) * (dy - 0.20f))
    if (e1 < 0.06f || e2 < 0.06f) return 0f
    val d = 1f - r / radius
    val frontBoost = maxOf(0f, 1f - abs(dx - radius * 0.85f) * 4f) * 0.4f
    return minOf(1f, 0.4f + d * 0.55f + frontBoost)
}

/** 해파리 — 종(bell) + 촉수. 데모 `SPECIES.jelly` 1:1(촉수 폭 jitter는 결정성 위해 고정값). */
fun jellyDensity(u: Float, v: Float, t: Float, energy: Float): Float {
    val cx = -0.15f
    val du = u - cx
    val dv = v
    if (du < 0f && du > -0.7f) {
        val ny = dv / 0.55f
        val nx = du / 0.55f
        val r = sqrt(nx * nx + ny * ny)
        if (r < 1f) {
            val ridge = sin(nx * 12f + t * 0.4f) * 0.04f
            if (r < 0.95f + ridge) return minOf(1f, 0.4f + (1f - r) * 0.7f)
        }
    }
    if (du > -0.05f && du < 0.85f) {
        val tendrilCount = 5
        for (i in 0 until tendrilCount) {
            val ty = (i - (tendrilCount - 1) / 2f) * 0.22f
            val sway = sin(du * 5f + t * 0.8f + i) * 0.07f * energy
            if (abs(dv - ty - sway) < 0.03f) return 0.7f - du * 0.6f
        }
    }
    return 0f
}

/** 오징어 — 몸통(mantle) + 촉완 7개. 데모 `SPECIES.squid` 1:1. */
fun squidDensity(u: Float, v: Float, t: Float, energy: Float): Float {
    val headCy = -0.35f
    val dh = sqrt(u * u / 0.22f + (v - headCy) * (v - headCy) / 0.18f)
    if (dh < 1f) return minOf(1f, 0.5f + (1f - dh) * 0.6f)
    val e1 = sqrt((u + 0.18f) * (u + 0.18f) + (v - headCy + 0.05f) * (v - headCy + 0.05f))
    val e2 = sqrt((u - 0.18f) * (u - 0.18f) + (v - headCy + 0.05f) * (v - headCy + 0.05f))
    if (e1 < 0.05f || e2 < 0.05f) return 0f
    if (v > headCy + 0.15f && v < 0.95f) {
        val phase = t * 0.6f
        for (i in 0 until 7) {
            val baseX = (i - 3) * 0.12f
            val sway = sin(v * 4f + phase + i * 0.5f) * 0.08f * energy
            if (abs(u - baseX - sway) < 0.025f) return 0.6f + sin(v * 8f + i) * 0.2f
        }
    }
    return 0f
}

private val PIXEL_BITMAP = listOf(
    "............",
    "....####....",
    "...######...",
    "..########..",
    ".#.######.#.",
    ".##.####.##.",
    ".##.####.##.",
    ".##########.",
    "..########..",
    "..##.##.##..",
    ".##..##..##.",
    "............",
)

/** 8-bit 픽셀 생명체 — 12×12 비트맵. 데모 `SPECIES.pixel` 1:1. */
fun pixelDensity(u: Float, v: Float): Float {
    val px = floor((u + 1f) * 6f).toInt()
    val py = floor((v + 1f) * 6f).toInt()
    if (px < 0 || px > 11 || py < 0 || py > 11) return 0f
    return if (PIXEL_BITMAP[py][px] == '#') 1f else 0f
}
