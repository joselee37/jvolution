package today.superb.jvl.sound

import kotlin.math.PI
import kotlin.math.sin

/**
 * [Sfx] 톤 시퀀스 → mono PCM 합성. 순수 — 같은 입력이면 같은 출력(테스트 가능).
 * 모든 플랫폼 플레이어가 같은 버퍼를 캐시해 재생한다.
 */
object SfxSynth {
    const val SAMPLE_RATE = 44100

    private const val AMP = 0.22f      // 마스터 진폭 — 클리핑/청각 피로 방지
    private const val ATTACK_MS = 4    // 톤 경계 클릭 방지 엔벨로프
    private const val RELEASE_MS = 12

    /**
     * float PCM([-1,1]) 합성. iOS(AVAudioPCMBuffer float)가 직접 쓰고, 나머지는 [toPcm16] 경유.
     * 사각파는 비정수 주기 버퍼에서 소량의 DC 오프셋이 생길 수 있다(청각적으로 무해 — 짧은 SFX 한정).
     */
    fun render(sfx: Sfx, sampleRate: Int = SAMPLE_RATE): FloatArray {
        val total = sfx.tones.sumOf { it.durMs * sampleRate / 1000 }
        val out = FloatArray(total)
        var base = 0
        for (tone in sfx.tones) {
            val n = tone.durMs * sampleRate / 1000
            if (tone.freqHz > 0f) {
                val attack = (ATTACK_MS * sampleRate / 1000).coerceAtMost(n)
                val release = (RELEASE_MS * sampleRate / 1000).coerceAtMost(n)
                for (i in 0 until n) {
                    // 사각파(8-bit 펄스파 근사) — sin 부호만 사용.
                    val square = if (sin(2.0 * PI * tone.freqHz * i / sampleRate) >= 0.0) 1f else -1f
                    val env = minOf(
                        if (attack > 0) i / attack.toFloat() else 1f,
                        if (release > 0) (n - 1 - i) / release.toFloat() else 1f,
                        1f,
                    ).coerceAtLeast(0f)
                    out[base + i] = square * AMP * env
                }
            }
            base += n
        }
        return out
    }

    /** float PCM → 16-bit PCM. Android(AudioTrack)/JVM(Clip)용. */
    fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
}
