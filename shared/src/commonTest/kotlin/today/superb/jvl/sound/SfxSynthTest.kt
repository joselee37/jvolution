package today.superb.jvl.sound

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SfxSynthTest {

    @Test
    fun render_length_matches_tone_durations() {
        for (sfx in Sfx.entries) {
            val expected = sfx.tones.sumOf { it.durMs * SfxSynth.SAMPLE_RATE / 1000 }
            assertEquals(expected, SfxSynth.render(sfx).size, "${sfx.name} 길이")
        }
    }

    @Test
    fun render_amplitude_stays_in_safe_range() {
        for (sfx in Sfx.entries) {
            val peak = SfxSynth.render(sfx).maxOf { abs(it) }
            assertTrue(peak <= 0.3f, "${sfx.name} 피크 $peak — 마스터 진폭 초과")
            assertTrue(peak > 0f, "${sfx.name} 무음이면 안 됨")
        }
    }

    @Test
    fun rest_tones_are_silent() {
        // Alert는 freq 0 rest 구간을 포함한다 — 그 구간은 전부 0.
        val alert = Sfx.Alert
        val pcm = SfxSynth.render(alert)
        var base = 0
        for (tone in alert.tones) {
            val n = tone.durMs * SfxSynth.SAMPLE_RATE / 1000
            if (tone.freqHz == 0f) {
                for (i in base until base + n) assertEquals(0f, pcm[i], "rest 샘플 $i")
            }
            base += n
        }
    }

    @Test
    fun envelope_starts_and_ends_near_zero() {
        val pcm = SfxSynth.render(Sfx.Ping)
        assertTrue(abs(pcm.first()) < 0.02f, "어택 시작은 0 근접")
        assertTrue(abs(pcm.last()) < 0.02f, "릴리즈 끝은 0 근접")
    }

    @Test
    fun render_is_deterministic() {
        assertContentEquals(SfxSynth.render(Sfx.Win), SfxSynth.render(Sfx.Win))
    }

    @Test
    fun pcm16_conversion_clamps_and_scales() {
        val pcm = SfxSynth.toPcm16(floatArrayOf(0f, 1f, -1f, 2f, -2f))
        assertContentEquals(shortArrayOf(0, 32767, -32767, 32767, -32767), pcm)
    }
}
