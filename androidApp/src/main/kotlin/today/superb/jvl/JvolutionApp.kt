package today.superb.jvl

import android.app.Application
import today.superb.jvl.di.initKoin

class JvolutionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GameStore(SharedPreferences)가 Context를 읽기 전에 설정 — initKoin() 이전 필수.
        AppContext.application = this
        initKoin()
    }
}
