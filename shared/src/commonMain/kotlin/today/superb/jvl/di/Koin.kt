package today.superb.jvl.di

import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import today.superb.jvl.core.DefaultRng
import today.superb.jvl.core.Rng
import today.superb.jvl.persistence.GameStore
import today.superb.jvl.persistence.SaveCodec
import today.superb.jvl.persistence.createGameStore
import today.superb.jvl.sound.SfxPlayer
import today.superb.jvl.sound.SfxSink
import today.superb.jvl.viewmodel.GameViewModel

val appModule = module {
    single<Rng> { DefaultRng() }
    single<GameStore> { createGameStore() }
    single { SaveCodec() }
    single<SfxSink> { SfxPlayer() }
    // 명시적 생성 — autoTick 기본값(true) + 시작 시 저장본 동기 load(없으면 새 게임).
    // 동기 load는 koinViewModel()+collectAsState가 즉시 수집해 새 펫이 깜빡이는 것을 막는다.
    viewModel {
        val codec = get<SaveCodec>()
        val blob = codec.decode(get<GameStore>().load())
        GameViewModel(
            get(),
            initialState = blob?.game,
            initialTweaks = blob?.tweaks,
            store = get(),
            codec = codec,
            sfx = get(),
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
