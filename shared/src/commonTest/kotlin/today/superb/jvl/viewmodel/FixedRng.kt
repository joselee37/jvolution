package today.superb.jvl.viewmodel

import today.superb.jvl.core.Rng

/** 스크립트된 RNG — peer AI 분기를 결정적으로 검증(:core commonTest의 동명 헬퍼와 동일 역할). */
class FixedRng(private val floats: List<Float>) : Rng {
    private var idx = 0
    override fun nextFloat(): Float {
        check(idx < floats.size) { "FixedRng exhausted at #${idx + 1}/${floats.size}" }
        return floats[idx++]
    }
    override fun nextLong(): Long = nextFloat().toLong()
    override fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt()
}
