package today.superb.jvl.ui.sonar.species

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val HALF_W = 0.62f
private const val TOP_Y = -0.82f
private const val BASE_BOTTOM = 0.55f
private const val TOOTH_AMP = 0.20f

/**
 * Ghost 종 실루엣 밀도 함수. 데모 `creature.jsx` `SPECIES.ghost` 1:1.
 *
 * (u, v)는 [-1, 1] 정규 좌표, 반환값은 밀도 [0, 1](0=빈 공간). 돔 + 4치형 바닥 + 눈구멍 +
 * 밝은 동공 + 가장자리 부스트. [happiness]는 동공 시선/표정에 사용.
 */
fun ghostDensity(u: Float, v: Float, t: Float, happiness: Float): Float {
    // 좌우 경계
    if (u < -HALF_W || u > HALF_W) return 0f

    // 상단 돔 — 반지름 HALF_W, 중심 (0, TOP_Y + HALF_W)
    val domeCY = TOP_Y + HALF_W
    if (v < domeCY) {
        val r = sqrt(u * u + (v - domeCY) * (v - domeCY))
        if (r > HALF_W) return 0f
    }

    // 하단 스캘럽(4치형) — toothFactor: 톱니 끝=1, 골=0
    val toothFactor = (1f - cos((4f * PI.toFloat() * u) / HALF_W)) / 2f
    val bottomY = BASE_BOTTOM + TOOTH_AMP * toothFactor
    if (v > bottomY) return 0f

    // 눈 — 좌우 대칭
    val eyeY = -0.18f
    val eyeOffset = 0.27f
    val eyeR = 0.17f
    val pupilR = 0.07f

    // 동공 드리프트 — 느린 좌우 + 행복하면 살짝 위
    val lookX = sin(t * 0.4f) * 0.05f
    val lookY = (happiness - 0.5f) * 0.05f

    val lEx = u + eyeOffset
    val lEy = v - eyeY
    val rEx = u - eyeOffset
    val rEy = v - eyeY
    val lEyeDist = sqrt(lEx * lEx + lEy * lEy)
    val rEyeDist = sqrt(rEx * rEx + rEy * rEy)
    val lPupDist = sqrt((lEx - lookX) * (lEx - lookX) + (lEy - lookY) * (lEy - lookY))
    val rPupDist = sqrt((rEx - lookX) * (rEx - lookX) + (rEy - lookY) * (rEy - lookY))

    // 눈구멍 안 밝은 동공
    if (lPupDist < pupilR || rPupDist < pupilR) return 1f
    // 눈 흰자 = 실루엣의 "구멍"(빈 공간)
    if (lEyeDist < eyeR || rEyeDist < eyeR) return 0f

    // 본체 밀도 — 가장자리 근처가 살짝 밝아 sonar-return 느낌, 중심은 흐림
    val distToEdge = minOf(
        HALF_W - abs(u),
        bottomY - v,
        if (v >= domeCY) 99f else HALF_W - sqrt(u * u + (v - domeCY) * (v - domeCY)),
    )
    val edgeBoost = maxOf(0f, 1f - distToEdge * 3f) * 0.25f
    return minOf(1f, 0.45f + edgeBoost)
}
