package pl.wolter.ostoja.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Cienkie, obrysowe ikony w duchu One UI (24×24, linia 1.8). */
object Ic {
    private fun icon(name: String, stroke: String, fill: String? = null, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            if (stroke.isNotEmpty()) addPath(
                pathData = addPathNodes(stroke),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            if (fill != null) addPath(pathData = addPathNodes(fill), fill = SolidColor(Color.Black))
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx + r},$cy A$r,$r 0 1 1 ${cx - r},$cy A$r,$r 0 1 1 ${cx + r},$cy Z"

    private fun gear(): String {
        val sb = StringBuilder()
        val ri = 7.3f
        val ro = 9.7f
        fun pt(r: Float, deg: Double): String {
            val a = Math.toRadians(deg - 90)
            return "${12 + r * cos(a).toFloat()},${12 + r * sin(a).toFloat()}"
        }
        for (i in 0 until 8) {
            val a = i * 45.0
            sb.append(if (i == 0) "M" else "A$ri,$ri 0 0 1 ").append(pt(ri, a - 13)).append(' ')
            sb.append("L").append(pt(ro, a - 8)).append(' ')
            sb.append("L").append(pt(ro, a + 8)).append(' ')
            sb.append("L").append(pt(ri, a + 13)).append(' ')
        }
        sb.append("A$ri,$ri 0 0 1 ").append(pt(ri, -13.0)).append(" Z ")
        sb.append(circle(12f, 12f, 3f))
        return sb.toString()
    }

    val Search = icon("search", circle(11f, 11f, 7f) + " M20.5,20.5 L16,16")
    val Plus = icon("plus", "M12,5 V19 M5,12 H19", width = 2f)
    val Copy = icon("copy", "M11,8 H19 A2,2 0 0 1 21,10 V19 A2,2 0 0 1 19,21 H11 A2,2 0 0 1 9,19 V10 A2,2 0 0 1 11,8 Z M15,8 V5 A2,2 0 0 0 13,3 H5 A2,2 0 0 0 3,5 V13 A2,2 0 0 0 5,15 H9")
    val Eye = icon("eye", "M2,12 C4,7.5 7.6,5 12,5 C16.4,5 20,7.5 22,12 C20,16.5 16.4,19 12,19 C7.6,19 4,16.5 2,12 Z " + circle(12f, 12f, 3f))
    val EyeOff = icon("eyeOff", "M2,12 C4,7.5 7.6,5 12,5 C16.4,5 20,7.5 22,12 C20,16.5 16.4,19 12,19 C7.6,19 4,16.5 2,12 Z " + circle(12f, 12f, 3f) + " M3.5,3.5 L20.5,20.5")
    val Key = icon("key", circle(8f, 15f, 4f) + " M11,12 L20,3 M17,6 L20,9 M15,8 L17,10")
    val Shield = icon("shield", "M12,3 L19,6 V11 C19,15.5 16,19.5 12,21 C8,19.5 5,15.5 5,11 V6 Z")
    val ShieldCheck = icon("shieldCheck", "M12,3 L19,6 V11 C19,15.5 16,19.5 12,21 C8,19.5 5,15.5 5,11 V6 Z M9,12 L11,14 L15,10")
    val Lock = icon("lock", "M6,11 H18 A1,1 0 0 1 19,12 V20 A1,1 0 0 1 18,21 H6 A1,1 0 0 1 5,20 V12 A1,1 0 0 1 6,11 Z M8,11 V8 A4,4 0 0 1 16,8 V11")
    val Settings = icon("settings", gear(), width = 1.7f)
    val Scan = icon("scan", "M4,8 V5 A1,1 0 0 1 5,4 H8 M16,4 H19 A1,1 0 0 1 20,5 V8 M20,16 V19 A1,1 0 0 1 19,20 H16 M8,20 H5 A1,1 0 0 1 4,19 V16 M7,12 H17")
    val Qr = icon(
        "qr",
        "M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H16 M20,14 V16 M14,18 V20 H16 M18,18 H20 V20",
        fill = "M6.5,6.5 H7.5 V7.5 H6.5 Z M16.5,6.5 H17.5 V7.5 H16.5 Z M6.5,16.5 H7.5 V17.5 H6.5 Z",
    )
    val Star = icon("star", "M12,3.6 L14.6,8.9 L20.4,9.7 L16.2,13.8 L17.2,19.6 L12,16.9 L6.8,19.6 L7.8,13.8 L3.6,9.7 L9.4,8.9 Z")
    val StarFilled = icon("starFilled", "M12,3.6 L14.6,8.9 L20.4,9.7 L16.2,13.8 L17.2,19.6 L12,16.9 L6.8,19.6 L7.8,13.8 L3.6,9.7 L9.4,8.9 Z",
        fill = "M12,3.6 L14.6,8.9 L20.4,9.7 L16.2,13.8 L17.2,19.6 L12,16.9 L6.8,19.6 L7.8,13.8 L3.6,9.7 L9.4,8.9 Z")
    val ChevronRight = icon("chevR", "M9.5,6 L15.5,12 L9.5,18")
    val ChevronDown = icon("chevD", "M6,9.5 L12,15.5 L18,9.5")
    val Back = icon("back", "M15,5 L8,12 L15,19", width = 2f)
    val Close = icon("close", "M6,6 L18,18 M18,6 L6,18", width = 2f)
    val Check = icon("check", "M5,12.5 L9.5,17 L19,7.5", width = 2f)
    val More = icon("more", "", fill = circle(12f, 5.5f, 1.6f) + " " + circle(12f, 12f, 1.6f) + " " + circle(12f, 18.5f, 1.6f))
    val Trash = icon("trash", "M4,7 H20 M10,11 V17 M14,11 V17 M6,7 L7,19 A2,2 0 0 0 9,21 H15 A2,2 0 0 0 17,19 L18,7 M9,7 V4 H15 V7")
    val Edit = icon("edit", "M4,20 H8 L19,9 A2.83,2.83 0 0 0 15,5 L4,16 Z M13.5,6.5 L17.5,10.5")
    val Globe = icon("globe", circle(12f, 12f, 9f) + " M3,12 H21 M12,3 C14.5,5.5 15.8,8.5 15.8,12 C15.8,15.5 14.5,18.5 12,21 C9.5,18.5 8.2,15.5 8.2,12 C8.2,8.5 9.5,5.5 12,3 Z")
    val User = icon("user", circle(12f, 8f, 4f) + " M4,20 C5.5,16.5 8.5,15 12,15 C15.5,15 18.5,16.5 20,20")
    val Note = icon("note", "M6,3 H15 L19,7 V20 A1,1 0 0 1 18,21 H6 A1,1 0 0 1 5,20 V4 A1,1 0 0 1 6,3 Z M9,12 H15 M9,16 H15")
    val Refresh = icon("refresh", "M20,11 A8,8 0 1 0 17.7,16.7 M20,4 V11 H13")
    val Wand = icon("wand", "M4,20 L15,9 M13.5,7.5 L16.5,10.5 M17,3 V6 M15.5,4.5 H18.5 M20,9 V11 M19,10 H21 M9,4 V6 M8,5 H10")
    val Flash = icon("flash", "M13,2.5 L5,13.5 H11 L10,21.5 L18,10.5 H12 Z")
    val Image = icon("image", "M5,4 H19 A2,2 0 0 1 21,6 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 V6 A2,2 0 0 1 5,4 Z M3,16 L8,11 L12,15 L15,12 L21,18 " + circle(15.5f, 8.5f, 1.5f))
    val Keyboard = icon("keyboard", "M4,6 H20 A1,1 0 0 1 21,7 V17 A1,1 0 0 1 20,18 H4 A1,1 0 0 1 3,17 V7 A1,1 0 0 1 4,6 Z M7,10 H7.01 M11,10 H11.01 M15,10 H15.01 M8,14 H16")
    val Bell = icon("bell", "M6,16 V11 A6,6 0 0 1 18,11 V16 L19.5,18 H4.5 Z M10,20.5 A2,2 0 0 0 14,20.5")
    val Pin = icon("pin", "M12,16 V21 M8.5,3 H15.5 L14.5,8.5 L17.5,12.5 V14 H6.5 V12.5 L9.5,8.5 Z")
    val Moon = icon("moon", "M20,14.5 A8.5,8.5 0 1 1 9.5,4 A6.5,6.5 0 0 0 20,14.5 Z")
    val Palette = icon("palette", "M12,3 A9,9 0 0 0 12,21 C13.4,21 14,20.2 14,19.3 C14,18 13,17.6 13,16.5 C13,15.6 13.7,15 14.6,15 H17 A4,4 0 0 0 21,11 C21,6.6 17,3 12,3 Z",
        fill = circle(7.5f, 11f, 1.3f) + " " + circle(10f, 7f, 1.3f) + " " + circle(14.5f, 7f, 1.3f))
    val Download = icon("download", "M12,4 V15 M7,10 L12,15 L17,10 M5,20 H19")
    val Upload = icon("upload", "M12,15 V4 M7,9 L12,4 L17,9 M5,20 H19")
    val Fingerprint = icon("fingerprint", "M12,11.5 V14 C12,16 11.4,18 10.2,19.6 M8.5,8.8 A4.5,4.5 0 0 1 16.5,11.5 V13 M7.5,12 V13.5 A9,9 0 0 1 6.6,17.4 M5.2,6.8 A8.5,8.5 0 0 1 20.5,11.5 V12.5 M15.5,16.5 A13,13 0 0 1 14.5,20")
    val Timer = icon("timer", circle(12f, 13f, 8f) + " M12,9 V13 L14.5,14.5 M10,2.5 H14")
    val Info = icon("info", circle(12f, 12f, 9f) + " M12,11 V16.5 M12,7.6 V7.7", width = 1.9f)
    val Sort = icon("sort", "M4,7 H14 M4,12 H11 M4,17 H8 M18,5 V19 M15,16 L18,19 L21,16")
    val Link = icon("link", "M10,14 A4,4 0 0 0 15.7,14 L18.7,11 A4,4 0 0 0 13,5.3 L12,6.3 M14,10 A4,4 0 0 0 8.3,10 L5.3,13 A4,4 0 0 0 11,18.7 L12,17.7")
    val External = icon("ext", "M14,4 H20 V10 M20,4 L11,13 M18,14 V19 A1,1 0 0 1 17,20 H5 A1,1 0 0 1 4,19 V7 A1,1 0 0 1 5,6 H10")
    val Widgets = icon("widgets", "M5,4 H10 A1,1 0 0 1 11,5 V10 A1,1 0 0 1 10,11 H5 A1,1 0 0 1 4,10 V5 A1,1 0 0 1 5,4 Z M14,4 H19 A1,1 0 0 1 20,5 V10 A1,1 0 0 1 19,11 H14 A1,1 0 0 1 13,10 V5 A1,1 0 0 1 14,4 Z M5,13 H10 A1,1 0 0 1 11,14 V19 A1,1 0 0 1 10,20 H5 A1,1 0 0 1 4,19 V14 A1,1 0 0 1 5,13 Z M16.5,13 V20 M13,16.5 H20")
    val Form = icon("form", "M4,6 H20 M4,12 H13 M4,18 H9 M14,20 L14.6,17.4 L19.4,12.6 A1.4,1.4 0 0 1 21.4,14.6 L16.6,19.4 Z")
    val Warning = icon("warning", "M12,4 L21,19.5 H3 Z M12,10 V14 M12,16.9 V17")
    val History = icon("history", "M4,12 A8,8 0 1 0 6.3,6.3 M4,4 V9 H9 M12,8 V12 L15,14")
    val Phone = icon("phone", "M8,3 H16 A2,2 0 0 1 18,5 V19 A2,2 0 0 1 16,21 H8 A2,2 0 0 1 6,19 V5 A2,2 0 0 1 8,3 Z M11,18 H13")
    val Clipboard = icon("clipboard", "M9,3.5 H15 V6.5 H9 Z M8,5 H6 A1,1 0 0 0 5,6 V20 A1,1 0 0 0 6,21 H18 A1,1 0 0 0 19,20 V6 A1,1 0 0 0 18,5 H16")
    val Sparkle = icon("sparkle", "M12,3 L13.8,10.2 L21,12 L13.8,13.8 L12,21 L10.2,13.8 L3,12 L10.2,10.2 Z")
    // ikony kapsuły i list 1:1 z prototypu
    val Padlock = icon("padlock", "M7,10 H17 A3,3 0 0 1 20,13 V18 A3,3 0 0 1 17,21 H7 A3,3 0 0 1 4,18 V13 A3,3 0 0 1 7,10 Z M8,10 V7.5 A4,4 0 0 1 16,7.5 V10", width = 1.7f)
    val Clock = icon("clock", circle(12f, 13f, 8f) + " M12,9 V13 L14.5,15 M10,2.5 H14", width = 1.7f)
    val ScanTab = icon("scanTab", "M4,8 V6 A2,2 0 0 1 6,4 H8 M16,4 H18 A2,2 0 0 1 20,6 V8 M20,16 V18 A2,2 0 0 1 18,20 H16 M8,20 H6 A2,2 0 0 1 4,18 V16 M7,12 H17", width = 1.7f)
    val Sliders = icon("sliders", "M4,7 H13 M19,7 H20 M4,17 H7 M13,17 H20 " + circle(16f, 7f, 2.3f) + " " + circle(10f, 17f, 2.3f), width = 1.7f)
    val Card = icon("card", "M5.5,6 H18.5 A2.5,2.5 0 0 1 21,8.5 V15.5 A2.5,2.5 0 0 1 18.5,18 H5.5 A2.5,2.5 0 0 1 3,15.5 V8.5 A2.5,2.5 0 0 1 5.5,6 Z M3,10 H21 M7,14.5 H11", width = 1.7f)
    val Wifi = icon("wifi", "M3.5,9.5 A12,12 0 0 1 20.5,9.5 M6.5,13 A7.5,7.5 0 0 1 17.5,13 M9.5,16.3 A3.2,3.2 0 0 1 14.5,16.3", fill = circle(12f, 19f, 0.9f), width = 1.7f)
    val NoteDoc = icon("noteDoc", "M7.5,3.5 H16.5 A2.5,2.5 0 0 1 19,6 V18 A2.5,2.5 0 0 1 16.5,20.5 H7.5 A2.5,2.5 0 0 1 5,18 V6 A2.5,2.5 0 0 1 7.5,3.5 Z M9,9 H15 M9,13 H15 M9,17 H12", width = 1.7f)
    val Regen = icon("regen", "M20,11 A8,8 0 0 0 5.5,6.5 L4,8 M4,4 V8 H8 M4,13 A8,8 0 0 0 18.5,17.5 L20,16 M20,20 V16 H16", width = 1.9f)
    val Share = icon("share", circle(18f, 5f, 2.5f) + " " + circle(6f, 12f, 2.5f) + " " + circle(18f, 19f, 2.5f) + " M8.2,10.8 L15.8,6.2 M8.2,13.2 L15.8,17.8", width = 1.7f)
    val Grid = icon("grid", "M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H20 V20 H14 Z")
    val Laptop = icon("laptop", "M5,5 H19 A1,1 0 0 1 20,6 V15 H4 V6 A1,1 0 0 1 5,5 Z M2,18 H22", width = 1.7f)
}
