package today.superb.jvl.core

/**
 * 고정 7유닛 NPC 로스터와 그 초기 상태 팩토리. 데모 `NPC_ROSTER` + `makePeers()` 1:1.
 *
 * 로스터(id/name/species/stage/personality)는 게임 시작 시 한 번 구성되며 고정이다.
 * 가변 상태(위치·속도·쿨다운)는 [rng]로 시드해 분산시킨다 — 테스트는 [SeededRng]로 재현.
 */
object PeerRoster {

    /** 고정 속성만 담은 로스터 항목(가변 상태 제외). */
    private data class Entry(
        val id: String,
        val name: String,
        val species: Species,
        val stage: Stage,
        val personality: Personality,
    )

    private val ROSTER = listOf(
        Entry("lumen", "LUMEN-3", Species.Jelly, Stage.Juvenile, Personality.Gentle),
        Entry("hrrk", "HRRK", Species.Squid, Stage.Adult, Personality.Aggressive),
        Entry("blink", "BLINK", Species.Pixel, Stage.Larva, Personality.Playful),
        Entry("morrow", "MORROW", Species.Ghost, Stage.Adult, Personality.Veteran),
        Entry("sift", "SIFT", Species.Blob, Stage.Juvenile, Personality.Playful),
        Entry("arc9", "ARC-9", Species.Squid, Stage.Adult, Personality.Aggressive),
        Entry("nimbus", "NIMBUS", Species.Jelly, Stage.Larva, Personality.Gentle),
    )

    /**
     * 시작 로스터를 구성한다. 데모 `makePeers()`의 초기값 분포 1:1:
     * - bearing: `i*360/n ± 15°` (균등 분산)
     * - range:   `[0.32, 0.87)`
     * - bearingVel: `[-1.1, +1.1]` 도/초
     * - rangeVel:   `[-0.002, +0.002]` /초
     * - cooldown:   `[20, 60)` 초 (시작 직후 일제 발동 방지 침묵)
     */
    fun makePeers(rng: Rng): List<Peer> {
        val n = ROSTER.size
        return ROSTER.mapIndexed { i, e ->
            Peer(
                id = e.id,
                name = e.name,
                species = e.species,
                stage = e.stage,
                personality = e.personality,
                bearing = ((i * 360f / n) + (rng.nextFloat() - 0.5f) * 30f + 360f) % 360f,
                range = 0.32f + rng.nextFloat() * 0.55f,
                bearingVel = (rng.nextFloat() - 0.5f) * 2.2f,
                rangeVel = (rng.nextFloat() - 0.5f) * 0.004f,
                bond = 0f,
                battlesWon = 0,
                battlesLost = 0,
                cooldown = 20f + rng.nextFloat() * 40f,
            )
        }
    }
}
