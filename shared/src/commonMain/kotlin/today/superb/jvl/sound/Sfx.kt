package today.superb.jvl.sound

/** 한 톤 — 주파수(Hz, 0 = 무음 rest)와 길이(ms). */
data class Tone(val freqHz: Float, val durMs: Int)

private fun t(freq: Float, ms: Int) = Tone(freq, ms)

/**
 * 레트로 SFX 카탈로그 — 사각파 톤 시퀀스(8-bit/CRT 미학). 합성은 [SfxSynth.render],
 * 재생은 플랫폼 SfxPlayer(후속), 트리거 매핑은 viewmodel의 sfxCueFor(후속).
 *
 * 주파수는 평균율 음계 근사(D3=147, G3=196, C4=262, E4=330, G4=392, A4=440, C5=523,
 * E5=659, G5=784, A5=880, B5=988, C6=1047, D6=1175, E6=1318).
 */
enum class Sfx(val tones: List<Tone>) {
    Ping(listOf(t(880f, 50), t(1318f, 80))),
    Care(listOf(t(659f, 45), t(988f, 70))),
    Scold(listOf(t(392f, 70), t(262f, 110))),
    SleepCue(listOf(t(523f, 80), t(392f, 80), t(262f, 140))),
    WakeCue(listOf(t(262f, 60), t(523f, 90))),
    Evolve(listOf(t(523f, 70), t(659f, 70), t(784f, 70), t(1047f, 120))),
    EvolveDone(listOf(t(784f, 90), t(1047f, 90), t(1318f, 160))),
    Alert(listOf(t(1175f, 90), t(0f, 40), t(1175f, 90), t(0f, 40), t(1175f, 140))),
    Friendly(listOf(t(784f, 60), t(988f, 60))),
    Hit(listOf(t(196f, 90), t(147f, 60))),
    Crit(listOf(t(98f, 60), t(196f, 60), t(98f, 110))),
    Win(listOf(t(523f, 80), t(659f, 80), t(784f, 80), t(1047f, 200))),
    Lose(listOf(t(330f, 110), t(262f, 110), t(196f, 220))),
    Disengage(listOf(t(440f, 70), t(330f, 110))),
    Confirm(listOf(t(1047f, 60))),
}
