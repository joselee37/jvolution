package today.superb.jvl.sound

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/** JVM(host 테스트/도구) — [javax.sound.sampled.Clip]. 오디오 장치 없는 CI에선 조용히 무시. */
actual class SfxPlayer actual constructor() : SfxSink {
    private val cache = mutableMapOf<Sfx, ByteArray>()

    actual override fun play(sfx: Sfx) {
        runCatching {
            val bytes = cache.getOrPut(sfx) {
                val pcm = SfxSynth.toPcm16(SfxSynth.render(sfx))
                ByteArray(pcm.size * 2).also { b ->
                    for (i in pcm.indices) {
                        b[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
                        b[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
                    }
                }
            }
            val clip = AudioSystem.getClip()
            clip.open(AudioFormat(SfxSynth.SAMPLE_RATE.toFloat(), 16, 1, true, false), bytes, 0, bytes.size)
            clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
            clip.start()
        }
    }

    actual override fun dispose() {
        cache.clear()
    }
}
