package today.superb.jvl.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * :core 모듈 경계 가드 — commonMain 소스가 UI/네트워크/DI 프레임워크를 import하지 못하게 막는다.
 *
 * :core는 순수 Kotlin 도메인이어야 한다(서드파티 제로). 컨벤션만으로는 강제되지 않으므로
 * 소스 파일을 스캔해 금지된 import를 단정한다. Konsist 같은 외부 의존성 없이 JVM 파일 접근만 사용
 * (jvmTest 전용). 새 contributor가 실수로 Compose/Ktor/Koin/Coil을 끌어오면 빌드가 빨갛게 된다.
 */
class CorePurityTest {

    private val forbiddenPrefixes = listOf(
        "androidx.",
        "org.jetbrains.compose",
        "io.ktor",
        "io.coil",
        "coil3",
        "org.koin",
        "io.insert",
    )

    @Test
    fun commonMain_has_no_ui_or_framework_imports() {
        // Gradle test working dir = 모듈 루트(core/).
        val commonMain = File("src/commonMain/kotlin")
        assertTrue(commonMain.isDirectory, "commonMain 소스 디렉터리를 찾지 못함: ${commonMain.absolutePath}")

        val violations = commonMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter { line -> forbiddenPrefixes.any { line.contains(it) } }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        if (violations.isNotEmpty()) {
            fail(":core 순수성 위반 — 금지된 import 발견:\n" + violations.joinToString("\n"))
        }
    }
}
