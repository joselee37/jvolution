package today.superb.jvl.di

import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import today.superb.jvl.core.DefaultRng
import today.superb.jvl.core.Rng
import today.superb.jvl.viewmodel.GameViewModel

val appModule = module {
    single<Rng> { DefaultRng() }
    // 명시적 생성 — autoTick은 기본값(true) 사용. viewModelOf는 Boolean 기본값을 못 써 NoDefinition 발생.
    viewModel { GameViewModel(get()) }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
