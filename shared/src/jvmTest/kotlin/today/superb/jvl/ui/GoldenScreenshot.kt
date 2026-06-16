package today.superb.jvl.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * 의존성 없는 골든 스크린샷 비교 — 캡처는 Compose `captureToImage()`, 인코드는 Skia, 비교는 JDK ImageIO.
 * (jvmTest라 java API 사용 가능.) Roborazzi 버전 호환 리스크 회피.
 *
 * 동작: 골든 파일이 없으면 기록(record-on-missing), 있으면 픽셀 허용오차로 비교. 재기록은 해당 골든을
 * 지우고 재실행. 골든은 폰트/Skia 렌더에 민감하므로 CI는 동일 환경에서 돌려야 한다(이 한계는 모든
 * 골든 스크린샷 공통).
 */
private const val GOLDEN_DIR = "src/jvmTest/golden"
private const val MAX_DIFF_RATIO = 0.01   // 허용 픽셀 비율(1%)
private const val CHANNEL_TOL = 12        // per-channel 허용 차이(AA 노이즈 흡수)

/** desktop ImageBitmap → PNG 바이트(Skia 인코드). */
fun ImageBitmap.toPngBytes(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes

/** 골든이 없으면 기록, 있으면 허용오차 비교. */
fun assertGolden(name: String, png: ByteArray) {
    val file = File("$GOLDEN_DIR/$name.png")
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.writeBytes(png)
        println("golden recorded: ${file.path}")
        return
    }
    val expected = ImageIO.read(ByteArrayInputStream(file.readBytes())) ?: error("golden '$name' 디코드 실패")
    val actual = ImageIO.read(ByteArrayInputStream(png)) ?: error("캡처 디코드 실패")
    assertTrue(
        expected.width == actual.width && expected.height == actual.height,
        "golden '$name' 크기 불일치: ${expected.width}x${expected.height} vs ${actual.width}x${actual.height}",
    )
    var diff = 0
    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            val e = expected.getRGB(x, y)
            val a = actual.getRGB(x, y)
            if (abs(((e shr 16) and 0xFF) - ((a shr 16) and 0xFF)) > CHANNEL_TOL ||
                abs(((e shr 8) and 0xFF) - ((a shr 8) and 0xFF)) > CHANNEL_TOL ||
                abs((e and 0xFF) - (a and 0xFF)) > CHANNEL_TOL
            ) {
                diff++
            }
        }
    }
    val ratio = diff.toDouble() / (expected.width * expected.height)
    assertTrue(ratio <= MAX_DIFF_RATIO, "golden '$name' 픽셀 차이 ${ratio * 100}% (> ${MAX_DIFF_RATIO * 100}%)")
}
