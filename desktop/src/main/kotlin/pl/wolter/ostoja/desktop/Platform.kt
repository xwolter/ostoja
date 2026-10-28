package pl.wolter.ostoja.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import pl.wolter.ostoja.qr.QrScan
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * Schowek Windows; skopiowane hasło znika po 30 s, jeśli w międzyczasie nie skopiowano niczego innego.
 * Hasła i kody idą z formatami, po których Windows nie zapisuje ich w historii schowka (Win+V)
 * ani nie wysyła do schowka w chmurze na inne urządzenia.
 */
object Clipboard {
    private val timer = Timer("clip", true)

    private fun native(name: String): DataFlavor {
        val f = DataFlavor("application/x-ostoja-${name.lowercase()};class=java.io.InputStream", name)
        (SystemFlavorMap.getDefaultFlavorMap() as SystemFlavorMap).apply {
            addUnencodedNativeForFlavor(f, name)
            addFlavorForUnencodedNative(name, f)
        }
        return f
    }

    private val private by lazy {
        // Windows czyta te formaty jako liczbę DWORD: 0 = nie zapisuj; sama obecność pierwszego wyklucza monitorowanie schowka.
        listOf("ExcludeClipboardContentFromMonitorProcessing", "CanIncludeInClipboardHistory", "CanUploadToCloudClipboard").map(::native)
    }

    private class Secret(private val text: String, private val extra: List<DataFlavor>) : Transferable {
        override fun getTransferDataFlavors() = (listOf(DataFlavor.stringFlavor) + extra).toTypedArray()
        override fun isDataFlavorSupported(f: DataFlavor) = f == DataFlavor.stringFlavor || f in extra
        override fun getTransferData(f: DataFlavor): Any = when {
            f == DataFlavor.stringFlavor -> text
            f in extra -> ByteArrayInputStream(ByteArray(4))
            else -> throw UnsupportedFlavorException(f)
        }
    }

    fun copy(text: String, clearAfterSec: Int = 30) {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val content: Transferable = if (clearAfterSec > 0) runCatching { Secret(text, private) }.getOrElse { StringSelection(text) } else StringSelection(text)
        runCatching { cb.setContents(content, null) }
        if (clearAfterSec > 0) timer.schedule(clearAfterSec * 1000L) {
            runCatching {
                if (cb.getData(DataFlavor.stringFlavor) == text) cb.setContents(StringSelection(""), null)
            }
        }
    }

    fun image(): BufferedImage? = runCatching {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        if (!cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
        (cb.getData(DataFlavor.imageFlavor) as Image).toBuffered()
    }.getOrNull()

    fun text(): String? = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String }.getOrNull()
}

private fun Image.toBuffered(): BufferedImage {
    if (this is BufferedImage) return this
    val b = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
    b.createGraphics().apply { drawImage(this@toBuffered, 0, 0, null); dispose() }
    return b
}

object Qr {
    fun matrix(text: String) = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
    )

    /**
     * Odczyt kodu QR ze zrzutu ekranu (schowek, plik albo cały ekran) - wspólnym QrScan (ten sam co w telefonie).
     * Na końcu pomniejszenie i rozmycie, które też zacierają linie siatki.
     */
    fun decode(img: BufferedImage): String? =
        sequenceOf<() -> BufferedImage>({ img }, { scaled(img, 0.5) }, { blurred(img) }).firstNotNullOfOrNull { f ->
            val i = f()
            val px = IntArray(i.width * i.height).also { i.getRGB(0, 0, i.width, i.height, it, 0, i.width) }
            QrScan.decode(QrScan.luminance(px), i.width, i.height)
        }

    private fun scaled(img: BufferedImage, f: Double): BufferedImage {
        val w = maxOf(1, (img.width * f).toInt()); val h = maxOf(1, (img.height * f).toInt())
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { drawImage(img.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), 0, 0, null); dispose() }
        }
    }

    private fun blurred(img: BufferedImage): BufferedImage {
        val rgb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply { color = java.awt.Color.WHITE; fillRect(0, 0, width, height); drawImage(img, 0, 0, null); dispose() }
        }
        return java.awt.image.ConvolveOp(java.awt.image.Kernel(3, 3, FloatArray(9) { 1f / 9 }), java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(rgb, null)
    }

    fun decode(file: File): String? = runCatching { javax.imageio.ImageIO.read(file)?.let(::decode) }.getOrNull()

    /** Szuka kodu QR na wszystkich monitorach - np. strona GitHuba otwarta obok Ostoi. */
    fun scanScreens(): String? = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.firstNotNullOfOrNull { dev ->
            val shot = Robot(dev).createMultiResolutionScreenCapture(dev.defaultConfiguration.bounds)
            // Przy skalowaniu ekranu (125%, 150%) bierzemy obraz w pełnej rozdzielczości.
            val img = shot.resolutionVariants.maxBy { it.getWidth(null) }.toBuffered()
            decode(img)
        }
    }.getOrNull()
}

@Composable
fun QrCode(text: String, size: Dp, color: Color) {
    val m = remember(text) { Qr.matrix(text) }
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(color, Offset(x * cell, y * cell), Size(cell + .6f, cell + .6f))
        }
    }
}

/** Uruchamianie razem z Windows (HKCU\…\Run) - tylko gdy aplikacja jest zainstalowana jako .exe. */
object Autostart {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Ostoja"
    val exe: String? = System.getProperty("jpackage.app-path")

    val available get() = exe != null

    fun enabled(): Boolean = runCatching {
        val p = ProcessBuilder("reg", "query", KEY, "/v", NAME).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        p.waitFor() == 0
    }.getOrDefault(false)

    fun set(on: Boolean) {
        val exe = exe ?: return
        val cmd = if (on) listOf("reg", "add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$exe\" --tray", "/f")
        else listOf("reg", "delete", KEY, "/v", NAME, "/f")
        runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start().apply { inputStream.readAllBytes() }.waitFor() }
    }
}

/** „przed chwilą”, „12 s temu”, „5 min temu”, „3 dni temu”. */
fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - t) / 1000
    return when {
        t <= 0 -> "nigdy"
        s < 5 -> "przed chwilą"
        s < 60 -> "$s s temu"
        s < 3600 -> "${s / 60} min temu"
        s < 86400 -> "${s / 3600} godz. temu"
        else -> pl.wolter.ostoja.data.countLabel((s / 86400).toInt(), "dzień", "dni", "dni") + " temu"
    }
}

private val MONTHS = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")

fun dayMonth(t: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    return "${c.get(java.util.Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(java.util.Calendar.MONTH)]}"
}
