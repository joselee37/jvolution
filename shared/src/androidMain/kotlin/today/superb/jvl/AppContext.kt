package today.superb.jvl

import android.content.Context

/**
 * 프로세스 전역 Application Context 홀더. `JvolutionApp.onCreate()`가 `initKoin()` 전에 설정한다.
 *
 * SharedPreferences 기반 GameStore가 Context를 필요로 하는데, 공유 `initKoin()` 시그니처를
 * koin-android 없이 유지하기 위한 최소 시임(데모 in-memory→영속화 확장).
 */
object AppContext {
    lateinit var application: Context
}
