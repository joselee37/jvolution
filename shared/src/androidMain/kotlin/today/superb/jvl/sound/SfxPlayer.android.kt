package today.superb.jvl.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Android — [AudioTrack] MODE_STATIC. 짧은 SFX라 트랙은 1회용(재생 끝나면 자체 release). */
actual class SfxPlayer actual constructor() : SfxSink {
    private val cache = mutableMapOf<Sfx, ShortArray>()

    actual override fun play(sfx: Sfx) {
        runCatching {
            val pcm = cache.getOrPut(sfx) { SfxSynth.toPcm16(SfxSynth.render(sfx)) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SfxSynth.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { t?.release() }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            track.play()
        } // 실패(오디오 포커스/디바이스 등)는 무해 — 연출일 뿐 게임 진행과 무관.
    }

    actual override fun dispose() {
        cache.clear()
    }
}
