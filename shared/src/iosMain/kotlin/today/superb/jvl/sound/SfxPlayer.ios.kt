package today.superb.jvl.sound

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode

/**
 * iOS — [AVAudioEngine] + [AVAudioPlayerNode]에 float PCM 버퍼를 스케줄.
 * 엔진 기동 실패(오디오 세션 등)는 조용히 무시(연출이라 게임 진행과 무관).
 */
@OptIn(ExperimentalForeignApi::class)
actual class SfxPlayer actual constructor() : SfxSink {
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format = AVAudioFormat(AVAudioPCMFormatFloat32, SfxSynth.SAMPLE_RATE.toDouble(), 1u, false)
    private val cache = mutableMapOf<Sfx, AVAudioPCMBuffer>()
    private var started = false

    private fun ensureStarted(): Boolean {
        if (started) return true
        runCatching {
            engine.attachNode(player)
            engine.connect(player, engine.mainMixerNode, format)
            started = engine.startAndReturnError(null)
        }
        return started
    }

    actual override fun play(sfx: Sfx) {
        runCatching {
            if (!ensureStarted()) return
            val buffer = cache.getOrPut(sfx) { renderBuffer(sfx) ?: return }
            player.scheduleBuffer(buffer, completionHandler = null)
            if (!player.playing) player.play()
        }
    }

    private fun renderBuffer(sfx: Sfx): AVAudioPCMBuffer? {
        val samples = SfxSynth.render(sfx)
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = samples.size.toUInt())
        buffer.frameLength = samples.size.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return null
        for (i in samples.indices) channel[i] = samples[i]
        return buffer
    }

    actual override fun dispose() {
        runCatching {
            player.stop()
            engine.stop()
        }
        cache.clear()
    }
}
