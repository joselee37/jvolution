package today.superb.jvl.core

import kotlin.random.Random

/**
 * 결정성을 위해 reducer/도메인이 의존하는 난수 소스 — 테스트에서 시드 고정 가능.
 *
 * 결정성 범위 주의: [SeededRng]는 **같은 프로세스·같은 Kotlin/stdlib 버전** 안에서만
 * 재현을 보장한다. JVM↔Native 또는 stdlib 버전 간 동일 시퀀스는 보장하지 않으므로,
 * 시드를 영속/기기 간 공유해 같은 결과를 재현해야 한다면 commonMain에 명시적 PRNG를
 * 직접 구현해야 한다(현재 1차 범위 밖 — 테스트 결정성 용도로만 사용).
 */
interface Rng {
    fun nextFloat(): Float
    fun nextLong(): Long

    /** `[0, bound)` 범위의 균등 정수. talk 대사 선택·2차 peer AI/RPS 버킷에 사용. */
    fun nextInt(bound: Int): Int
}

/** 시드 고정 RNG — 테스트 재현성(`SeededRng(42L)`). 동일 프로세스 한정(상단 KDoc 참조). */
class SeededRng(seed: Long) : Rng {
    private val random = Random(seed)
    override fun nextFloat(): Float = random.nextFloat()
    override fun nextLong(): Long = random.nextLong()
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}

/** 런타임 기본 RNG. */
class DefaultRng : Rng {
    override fun nextFloat(): Float = Random.nextFloat()
    override fun nextLong(): Long = Random.nextLong()
    override fun nextInt(bound: Int): Int = Random.nextInt(bound)
}
