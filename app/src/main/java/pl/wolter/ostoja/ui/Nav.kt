package pl.wolter.ostoja.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pl.wolter.ostoja.data.OtpAccount

sealed interface Route {
    data class Item(val id: String) : Route
    data class EditLogin(val id: String?) : Route
    data object Scan : Route
    /** Przeniesienie kodów 2FA z Google / Microsoft Authenticator. */
    data object ImportOtp : Route
    data class EditOtp(val id: String?, val prefill: OtpAccount? = null, val linkTo: String? = null) : Route
}

@Stable
class Nav {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Route>()
    val top get() = stack.lastOrNull()

    /** Generator otwarty z paska akcji sejfu (samodzielnie, tylko kopiowanie). */
    var generator by mutableStateOf(false)
    /** Kilka kont z kodu migracji Google Authenticator czeka na potwierdzenie importu. */
    var importBatch by mutableStateOf<List<OtpAccount>?>(null)
    var focusSearch by mutableIntStateOf(0)
    var highlightOtp by mutableStateOf<String?>(null)
    /** Konto, do którego ma zostać przypisany kod zeskanowany z ekranu edycji hasła. */
    var scanLinkTo: String? = null
    /** Zeskanowany kod parowania z komputera - czeka na potwierdzenie. */
    var pairUri by mutableStateOf<String?>(null)

    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun replaceTop(r: Route) { pop(); push(r) }
    fun reset() { stack.clear() }
}
