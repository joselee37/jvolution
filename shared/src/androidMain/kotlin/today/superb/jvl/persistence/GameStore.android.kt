package today.superb.jvl.persistence

import android.content.Context
import today.superb.jvl.AppContext

private const val PREFS = "jvl_save"
private const val KEY = "save_v1"

actual fun createGameStore(): GameStore = AndroidGameStore()

/** SharedPreferences 저장소. [AppContext.application]에서 Context 확보(JvolutionApp이 주입). */
private class AndroidGameStore : GameStore {
    private val prefs by lazy {
        AppContext.application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(json: String) {
        prefs.edit().putString(KEY, json).apply()
    }
}
