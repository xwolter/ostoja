package pl.wolter.ostoja.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * Odczyt kodu QR z jasności pikseli - wspólny dla aparatu w telefonie, zdjęć z galerii i zrzutów ekranu na komputerze.
 *
 * Strony takie jak GitHub rysują kod jako siatkę komórek z cienkimi liniami między modułami. Na zrzucie ekranu
 * i w aparacie trzymanym blisko monitora te linie przecinają moduły i wzorce w narożnikach przestają się
 * zgadzać. Domknięcie / otwarcie (filtr min→max albo max→min) usuwa linie cieńsze niż okno, a moduły
 * zostają tej samej wielkości. Ciemny motyw (jasny kod na ciemnym tle) obsługuje ALSO_INVERTED.
 */
object QrScan {
    private val thorough = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true, DecodeHintType.ALSO_INVERTED to true,
    )
    private val quick = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.ALSO_INVERTED to true)

    /** Pełny odczyt obrazu (zrzut, zdjęcie): najpierw wprost, potem warianty bez linii siatki. */
    fun decode(lum: ByteArray, w: Int, h: Int): String? =
        sequence {
            yield(lum)
            for (r in 1..2) for (darkFirst in listOf(true, false)) yield(morph(lum, w, h, r, darkFirst))
        }.firstNotNullOfOrNull { once(source(it, w, h, w), thorough) }

    /**
     * Jedna klatka z aparatu (płaszczyzna Y). Najpierw szybka próba całej klatki; gdy się nie uda, środkowy
     * kwadrat (tam jest ramka celownika) w jednym z czterech wariantów bez siatki - co klatkę inny,
     * żeby nie spowalniać podglądu.
     */
    fun decodeFrame(y: ByteArray, w: Int, h: Int, rowStride: Int, frame: Int): String? {
        once(source(y, w, h, rowStride), quick)?.let { return it }
        val side = minOf(w, h) * 9 / 10
        val left = (w - side) / 2
        val top = (h - side) / 2
        val crop = ByteArray(side * side)
        for (row in 0 until side) System.arraycopy(y, (top + row) * rowStride + left, crop, row * side, side)
        val v = frame and 3
        return once(source(morph(crop, side, side, 1 + v / 2, v % 2 == 0), side, side, side), thorough)
    }

    /** Filtr min→max (darkFirst - znikają jasne linie) albo max→min (znikają ciemne) w oknie (2r+1)². */
    fun morph(lum: ByteArray, w: Int, h: Int, r: Int, darkFirst: Boolean): ByteArray {
        fun pass(src: ByteArray, min: Boolean): ByteArray {
            // Rozdzielnie: najpierw w poziomie, potem w pionie.
            val tmp = ByteArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var v = src[row + x].toInt() and 255
                    for (xx in maxOf(0, x - r)..minOf(w - 1, x + r)) {
                        val s = src[row + xx].toInt() and 255
                        v = if (min) minOf(v, s) else maxOf(v, s)
                    }
                    tmp[row + x] = v.toByte()
                }
            }
            val out = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var v = tmp[y * w + x].toInt() and 255
                for (yy in maxOf(0, y - r)..minOf(h - 1, y + r)) {
                    val s = tmp[yy * w + x].toInt() and 255
                    v = if (min) minOf(v, s) else maxOf(v, s)
                }
                out[y * w + x] = v.toByte()
            }
            return out
        }
        return pass(pass(lum, darkFirst), !darkFirst)
    }

    /** Jasność z pikseli ARGB; przezroczyste tło (wycinek ze strony) liczy się jako białe. */
    fun luminance(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val c = argb[i]
        (if (c ushr 24 < 128) 255 else ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000).toByte()
    }

    private fun source(lum: ByteArray, w: Int, h: Int, stride: Int): LuminanceSource =
        PlanarYUVLuminanceSource(lum, stride, h, 0, 0, w, h, false)

    private fun once(src: LuminanceSource, hints: Map<DecodeHintType, Any>): String? =
        listOf(HybridBinarizer(src), GlobalHistogramBinarizer(src)).firstNotNullOfOrNull { b ->
            runCatching { MultiFormatReader().decode(BinaryBitmap(b), hints).text }.getOrNull()
        }
}
