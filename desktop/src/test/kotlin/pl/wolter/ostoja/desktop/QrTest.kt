package pl.wolter.ostoja.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wolter.ostoja.data.OtpUri
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class QrTest {
    private val uri = "otpauth://totp/GitHub:annanowak?secret=KZ7Q4M2XWOPL3RTB&issuer=GitHub"

    /** Kod QR wklejony w „zrzut ekranu” 1920×1080, przeskalowany z wygładzaniem jak w przeglądarce. */
    private fun screenshot(fg: Color, bg: Color, page: Color, transparent: Boolean = false): BufferedImage {
        val m = Qr.matrix(uri)
        val qr = BufferedImage(m.width + 8, m.height + 8, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val on = x in 4 until m.width + 4 && y in 4 until m.height + 4 && m[x - 4, y - 4]
                setRGB(x, y, if (on) fg.rgb else if (transparent) 0 else bg.rgb)
            }
        }
        return BufferedImage(1920, 1080, if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                if (!transparent) { color = page; fillRect(0, 0, 1920, 1080) }
                color = Color.GRAY; drawString("Scan the QR code", 1100, 380)
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(qr, 1100, 400, 190, 190, null)
                dispose()
            }
        }
    }

    companion object {
        /**
         * Strona włączania 2FA w ciemnym motywie z kodem narysowanym jak siatka komórek tabeli HTML:
         * między modułami zostają linie 1 px w kolorze strony.
         */
        fun githubSetupPage(cell: Int = 4, light: Boolean = false): BufferedImage {
            val m = Qr.matrix("otpauth://totp/GitHub:annanowak?secret=KZ7Q4M2XWOPL3RTB&issuer=GitHub")
            val page = if (light) Color.WHITE else Color(0x0D1117)
            val step = cell + 1
            return BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply {
                    color = page; fillRect(0, 0, width, height)
                    color = Color(0xE6EDF3); drawString("Scan the QR code", 520, 260)
                    val x0 = 520; val y0 = 300
                    color = Color.WHITE; fillRect(x0 - 3 * step, y0 - 3 * step, (m.width + 6) * step, (m.height + 6) * step)
                    for (y in -3 until m.height + 3) for (x in -3 until m.width + 3) {
                        val on = x in 0 until m.width && y in 0 until m.height && m[x, y]
                        color = if (on) Color.BLACK else Color.WHITE
                        fillRect(x0 + x * step, y0 + y * step, cell, cell)
                        color = if (light) Color(0xD0D7DE) else page
                        fillRect(x0 + x * step + cell, y0 + y * step, 1, step); fillRect(x0 + x * step, y0 + y * step + cell, step, 1)
                    }
                    dispose()
                }
            }
        }
    }

    @Test fun readsQrDrawnAsGridOfCells() {
        for (cell in 3..8) for (light in listOf(true, false)) {
            val a = OtpUri.parse(Qr.decode(githubSetupPage(cell, light)) ?: error("nie odczytano: cell=$cell light=$light")).single()
            assertEquals("KZ7Q4M2XWOPL3RTB", a.secret)
        }
    }

    @Test fun readsGithubQrInLightAndDarkTheme() {
        val dark = Color(0x0D, 0x11, 0x17)
        listOf(
            screenshot(Color.BLACK, Color.WHITE, Color.WHITE),
            screenshot(Color(0xE6, 0xED, 0xF3), dark, dark), // ciemny motyw: jasny kod na ciemnym tle
            screenshot(Color.BLACK, Color.WHITE, Color.WHITE, transparent = true),
        ).forEach { img ->
            val a = OtpUri.parse(Qr.decode(img)!!).single()
            assertEquals("GitHub", a.issuer)
            assertEquals("KZ7Q4M2XWOPL3RTB", a.secret)
        }
    }
}
