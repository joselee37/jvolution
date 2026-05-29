package today.superb.jvl.core

import kotlin.random.Random

/** 결정성을 위해 reducer가 의존하는 난수 소스 — 테스트에서 시드 고정 가능. */
interface Rng {
    fun nextFloat(): Float
    fun nextLong(): Long
}

/** 시드 고정 RNG — 테스트 재현성(`SeededRng(42L)`). */
class SeededRng(seed: Long) : Rng {
    private val random = Random(seed)
    override fun nextFloat(): Float = random.nextFloat()
    override fun nextLong(): Long = random.nextLong()
}

/** 런타임 기본 RNG. */
class DefaultRng : Rng {
    override fun nextFloat(): Float = Random.nextFloat()
    override fun nextLong(): Long = Random.nextLong()
}
