package today.superb.jvl.core

/**
 * 스크립트된 RNG — [nextFloat]가 주어진 시퀀스를 순서대로 반환한다.
 *
 * peer AI 분기(challenge/friendly/idle/suppressed)를 결정적으로 검증하기 위함. 시퀀스를 다 쓰면
 * 예외를 던져 **RNG 호출 횟수**까지 단정할 수 있다(예: single-request gate가 두 번째 피어의
 * 주사위를 소비하지 않음을 IndexOutOfBounds 부재로 검증).
 */
class FixedRng(private val floats: List<Float>) : Rng {
    private var idx = 0
    val consumed: Int get() = idx

    override fun nextFloat(): Float {
        check(idx < floats.size) { "FixedRng exhausted: requested float #${idx + 1}, only ${floats.size} scripted" }
        return floats[idx++]
    }

    override fun nextLong(): Long = nextFloat().toLong()
    override fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt()
}
