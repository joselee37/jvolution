package today.superb.jvl.sound

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive

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
        // 인터럽션(전화/Siri)·백그라운드 복귀로 엔진이 멈출 수 있다 — started만 믿지 말고 running 확인.
        if (started && engine.running) return true
        runCatching {
            // Ambient: 배경 음악과 믹스, silent 스위치 존중 — 짧은 게임 SFX의 관례.
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            engine.attachNode(player)   // 중복 호출 무해
            engine.connect(player, engine.mainMixerNode, format)
            started = engine.startAndReturnError(null)
        }
        return started && engine.running
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
        started = false
        cache.clear()
    }
}
