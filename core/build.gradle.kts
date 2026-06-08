import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// 순수 Kotlin 도메인 라이브러리: 도메인 모델 · reducer · RNG · 터미널 파서.
// Compose / Ktor / Coil / Koin 의존 금지 (CorePurityTest가 강제).
// 단 하나의 예외: kotlinx-serialization-core(@Serializable 애너테이션 + 생성 직렬자)만 허용 —
// 영속화용 모델 직렬화. JSON 인코더/IO는 :shared(SaveCodec)에 두어 :core는 포맷·저장소와 무관하게 유지.
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
        commonMain.dependencies {
            // 직렬화 코어만(@Serializable + 생성 직렬자). JSON 인코더는 :shared.
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
