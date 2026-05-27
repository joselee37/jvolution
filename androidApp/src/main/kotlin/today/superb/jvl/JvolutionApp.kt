package today.superb.jvl

import android.app.Application
import today.superb.jvl.di.initKoin

class JvolutionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
