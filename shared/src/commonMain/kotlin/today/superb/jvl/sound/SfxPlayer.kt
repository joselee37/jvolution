package today.superb.jvl.sound

/** SFX 재생 표면 — GameViewModel이 의존(테스트는 recording fake 주입). */
interface SfxSink {
    fun play(sfx: Sfx)
    fun dispose()
}

/**
 * 플랫폼 SFX 재생기 — [SfxSynth]가 합성한 PCM을 fire-and-forget 재생.
 * 첫 재생 시 합성해 캐시한다(톤이 짧아 합성은 ~ms). 재생 실패는 게임 진행을 막지 않는다
 * (조용히 무시 — 오디오는 연출이지 기능 차단 요소가 아님).
 */
expect class SfxPlayer() : SfxSink {
    override fun play(sfx: Sfx)
    override fun dispose()
}
