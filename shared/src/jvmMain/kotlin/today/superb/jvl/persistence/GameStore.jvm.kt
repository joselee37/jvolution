package today.superb.jvl.persistence

actual fun createGameStore(): GameStore = JvmGameStore()

/** JVM(테스트 호스트) in-memory 저장소 — 실제 파일을 쓰지 않는다. */
private class JvmGameStore : GameStore {
    private var value: String? = null
    override fun load(): String? = value
    override fun save(json: String) {
        value = json
    }
}
