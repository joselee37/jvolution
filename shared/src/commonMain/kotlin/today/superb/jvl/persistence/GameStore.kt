package today.superb.jvl.persistence

/**
 * 단일 저장 슬롯 key-value 저장소(직렬화된 [SaveBlob] JSON 하나). 플랫폼별 actual 제공.
 *
 * 동기 API — 작은 블롭 하나라 동기 read/write로 충분하고, 시작 시 동기 load로 새 펫 플래시를 막는다.
 * [nowMillis][today.superb.jvl.nowMillis]와 같은 핸드롤 expect/actual 패턴.
 */
interface GameStore {
    /** 저장된 JSON 또는 null(없음). */
    fun load(): String?

    /** JSON 저장(덮어쓰기). */
    fun save(json: String)
}

/** 플랫폼 기본 저장소 — Android SharedPreferences / iOS NSUserDefaults / JVM in-memory. */
expect fun createGameStore(): GameStore
