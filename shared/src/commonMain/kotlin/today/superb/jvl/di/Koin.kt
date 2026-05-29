package today.superb.jvl.di

import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import today.superb.jvl.core.DefaultRng
import today.superb.jvl.core.Rng
import today.superb.jvl.viewmodel.GameViewModel

val appModule = module {
    single<Rng> { DefaultRng() }
    viewModelOf(::GameViewModel)
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
