package pl.wolter.ostoja.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class QrScanTest {
    private val uri = "otpauth://totp/GitHub:annanowak?secret=KZ7Q4M2XWOPL3RTB&issuer=GitHub"

    /** Strona z kodem jak tabela GitHuba: komórki `cell` px i linie 1 px między nimi (ciemne strony albo jasne). */
    private fun monitor(cell: Int, dark: Boolean): Triple<IntArray, Int, Int> {
        val m = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 0))
        val step = cell + 1
        val n = m.width + 8
        val size = n * step
        val page = if (dark) 0x11 else 0xD0
        val px = IntArray(size * size) { i ->
            val x = i % size; val y = i / size
            val mx = x / step - 4; val my = y / step - 4
            when {
                x % step >= cell || y % step >= cell -> page
                mx in 0 until m.width && my in 0 until m.height && m[mx, my] -> 0
                else -> 255
            }
        }
        return Triple(px, size, size)
    }

    /** Klatka z aparatu 1920×1080 (płaszczyzna Y): monitor powiększony `zoom` razy, lekko rozmyty, z szumem. */
    private fun cameraFrame(cell: Int, dark: Boolean, zoom: Double, stride: Int = 1920 + 64): ByteArray {
        val (src, sw, sh) = monitor(cell, dark)
        val w = 1920; val h = 1080
        val rnd = Random(7)
        val out = ByteArray(stride * h)
        val ox = (w - sw * zoom) / 2; val oy = (h - sh * zoom) / 2
        for (y in 0 until h) for (x in 0 until w) {
            val fx = (x - ox) / zoom - .5; val fy = (y - oy) / zoom - .5
            val x0 = Math.floor(fx).toInt(); val y0 = Math.floor(fy).toInt()
            fun at(xx: Int, yy: Int) = if (xx in 0 until sw && yy in 0 until sh) src[yy * sw + xx] else (if (dark) 0x11 else 0xD0)
            val tx = fx - x0; val ty = fy - y0
            val v = at(x0, y0) * (1 - tx) * (1 - ty) + at(x0 + 1, y0) * tx * (1 - ty) + at(x0, y0 + 1) * (1 - tx) * ty + at(x0 + 1, y0 + 1) * tx * ty
            out[y * stride + x] = (v * .85 + 20 + rnd.nextInt(-10, 11)).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    @Test fun phoneCameraReadsGithubGridQrFromMonitor() {
        for (cell in listOf(3, 4, 5)) for (dark in listOf(false, true)) {
            val frame = cameraFrame(cell, dark, zoom = 2.6)
            // W ciągu kilku klatek (co klatkę inny wariant bez siatki) kod musi się odczytać.
            val text = (0 until 4).firstNotNullOfOrNull { QrScan.decodeFrame(frame, 1920, 1080, 1920 + 64, it) }
            assertEquals("cell=$cell dark=$dark", uri, text)
        }
    }

    @Test fun screenshotOfGridQrIsRead() {
        for (cell in 3..6) for (dark in listOf(false, true)) {
            val (px, w, h) = monitor(cell, dark)
            val lum = ByteArray(px.size) { px[it].toByte() }
            assertEquals("cell=$cell dark=$dark", uri, QrScan.decode(lum, w, h))
        }
    }

    @Test fun noQrNoResult() {
        assertNull(QrScan.decode(ByteArray(200 * 200) { (it % 251).toByte() }, 200, 200))
    }
}
