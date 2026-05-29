import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// 순수 Kotlin 도메인 라이브러리: 도메인 모델 · reducer · RNG · 터미널 파서.
// Compose / Ktor / Coil / Koin 의존 금지 (서드파티 제로, kotlin.stdlib만).
// jvm() 타깃은 reducer 단위테스트를 host에서 빠르게 돌리기 위함(:core:jvmTest).
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "today.superb.jvl.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
