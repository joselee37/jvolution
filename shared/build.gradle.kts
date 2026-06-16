import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // host JVM 타깃 — commonTest(GameViewModel 등)를 :shared:jvmTest로 빠르게 실행하기 위함.
    // UI 렌더가 아닌 ViewModel/로직 단위테스트 전용(desktop 앱 진입점 없음).
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    androidLibrary {
        namespace = "today.superb.jvl.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(projects.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compose UI 시맨틱스 테스트는 jvm-전용(데스크톱 Skiko 런타임) — iOS/Android에 UI 테스트
        // 런타임을 강제하지 않으려고 jvmTest에만 둔다. 화면이 순수 (state)->@Composable이라 격리 렌더가 쉽다.
        // uiTestJUnit4가 createComposeRule + 데스크톱 런타임을 전이로 제공(별도 실험 API opt-in 불필요).
        jvmTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            // OS별 Skiko 네이티브 런타임(linux-x64 등) — 헤드리스 렌더에 필요(uiTestJUnit4가 안 끌어옴).
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
