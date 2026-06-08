package today.superb.jvl.core.terminal

import today.superb.jvl.core.Peer
import kotlin.math.roundToInt

/**
 * 피어 관련 터미널 출력의 순수 텍스트 렌더. 데모 `scan`/`bond` 핸들러의 라인 조립 1:1.
 *
 * [StatusReadout]과 같은 규약: 색/레이아웃 없이 `List<String>`만 반환 — i18n·테마는 이 파일만 교체.
 * 거리는 표시용으로 `range × 50` m로 환산한다(명세 03).
 */

/** `bond` 교배 자격 임계(유대 ≥ 0.7). 데모 as-is — 표시 전용. */
private const val BREED_BOND = 0.7f

private fun brg(bearing: Float) = bearing.roundToInt().toString().padStart(3, '0')
private fun rngM(range: Float) = (range * 50).roundToInt().toString().padStart(2, '0')

/** `scan` — 거리 오름차순 근접 유닛 목록. 헤더 + 카운트 + 각 피어 1줄. */
fun renderScan(peers: List<Peer>): List<String> = buildList {
    add("$ scan --peers @ 14.2kHz")
    add("▸ ${peers.size} contacts detected.")
    for (p in peers.sortedBy { it.range }) {
        val tag = p.id.uppercase().padEnd(7)
        val nm = p.name.padEnd(8)
        val sp = p.species.name.lowercase().padEnd(5)
        val st = p.stage.name.lowercase().take(4).padEnd(4)
        add("  [$tag] $nm · $sp · $st · brg ${brg(p.bearing)}° · rng ${rngM(p.range)}m")
    }
}

/** `bond <name>` — 특정 피어의 유대 게이지·전적·방위/거리·교배 자격. */
fun renderBond(peer: Peer): List<String> {
    val barW = 16
    val filled = (peer.bond * barW).roundToInt()
    val bar = "█".repeat(filled) + "░".repeat(barW - filled)
    val bondPct = (peer.bond * 100).roundToInt()
    return listOf(
        "${peer.name} — ${peer.species.name.lowercase()} · ${peer.stage.name.lowercase()} · ${peer.personality.name.lowercase()}",
        "  bond     [$bar]  $bondPct%",
        "  record   ${peer.battlesWon}W / ${peer.battlesLost}L",
        "  bearing  ${brg(peer.bearing)}°",
        "  range    ${rngM(peer.range)}m",
        if (peer.bond >= BREED_BOND) "  status   ◀ BREED-ELIGIBLE" else "  status   bond ≥ 70% required to breed",
    )
}

/** 이름(대소문자 무시) 또는 id로 피어 조회. 데모 `challenge`/`bond` 룩업 1:1. */
fun findPeer(peers: List<Peer>, target: String): Peer? =
    peers.find { it.name.lowercase() == target || it.id == target }
