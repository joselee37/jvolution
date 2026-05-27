# jvolution

Sonar Tamagotchi — a CRT/sonar-themed virtual-pet game, rewritten as a Kotlin Multiplatform mobile app (Android + iOS phones first; Watch companions later).

## Status

KMP scaffold in place (`shared/`, `androidApp/`, `iosApp/`), based on [`Kotlin/KMP-App-Template`](https://github.com/Kotlin/KMP-App-Template) (Compose Multiplatform shared UI). The original Museum sample code is still present and will be replaced screen-by-screen as the game is ported.

## Reference

- Game spec (source of truth): `demo/docs/` — 7-file framework-agnostic spec covering creature care, peer radar, battle, terminal, UI/visual.
- Working web prototype: `demo/` — read-only reference for behaviour and look.

## Build

```sh
./gradlew :androidApp:assembleDebug      # Android
./gradlew :shared:embedAndSignAppleFrameworkForXcode   # iOS (run from Xcode)
```

Open `iosApp/iosApp.xcodeproj` in Xcode to build the iOS app.

## Identity

- Package: `today.superb.jvl`
- Android applicationId / iOS BUNDLE_ID: `today.superb.jvl`
- Display name: `Jvolution`
