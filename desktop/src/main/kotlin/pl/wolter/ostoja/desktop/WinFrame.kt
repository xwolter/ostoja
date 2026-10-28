package pl.wolter.ostoja.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * Pasek tytułu Windows 11 w kolorach aplikacji (DwmSetWindowAttribute): ciemny tryb, kolor tła paska
 * i tekstu, zaokrąglone rogi. Okno zostaje systemowe - przyciąganie do krawędzi, zmiana rozmiaru
 * i cień działają jak w każdej aplikacji. Na Windows 10 część atrybutów jest po prostu ignorowana.
 */
object WinFrame {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: IntByReference, size: Int): Int
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val DWMWCP_ROUND = 2

    private val dwm: Dwm? by lazy {
        if (!System.getProperty("os.name").lowercase().contains("win")) null
        else runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }

    /** COLORREF to 0x00BBGGRR. */
    private fun colorRef(c: Color): Int {
        val argb = c.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    fun style(window: ComposeWindow, dark: Boolean, caption: Color, text: Color, border: Color) {
        val d = dwm ?: return
        runCatching {
            val hwnd = Pointer(window.windowHandle)
            fun set(attr: Int, v: Int) = d.DwmSetWindowAttribute(hwnd, attr, IntByReference(v), 4)
            set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
            set(DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND)
            set(DWMWA_CAPTION_COLOR, colorRef(caption))
            set(DWMWA_TEXT_COLOR, colorRef(text))
            set(DWMWA_BORDER_COLOR, colorRef(border))
        }
    }
}
