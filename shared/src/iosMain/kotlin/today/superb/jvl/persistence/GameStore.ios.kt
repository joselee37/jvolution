package today.superb.jvl.persistence

import platform.Foundation.NSUserDefaults

private const val KEY = "jvl_save_v1"

actual fun createGameStore(): GameStore = IosGameStore()

/** NSUserDefaults 저장소(프로세스 전역, Context 불필요). */
private class IosGameStore : GameStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? = defaults.stringForKey(KEY)

    override fun save(json: String) {
        defaults.setObject(json, forKey = KEY)
    }
}
