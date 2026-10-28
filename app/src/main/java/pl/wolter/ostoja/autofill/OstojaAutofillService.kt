package pl.wolter.ostoja.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import pl.wolter.ostoja.R
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.hostOf
import pl.wolter.ostoja.data.registrableDomain
import pl.wolter.ostoja.security.AppLock

/** Pola logowania znalezione na ekranie innej aplikacji lub strony. */
data class Fields(
    val user: List<AutofillId>,
    val pass: List<AutofillId>,
    val webDomain: String?,
    val packageName: String,
    val userValue: String?,
    val passValue: String?,
)

object Parser {
    private val USER_HINTS = listOf("user", "login", "email", "e-mail", "mail", "konto", "nazwa", "username", "account", "phone", "telefon", "identifier")
    private val PASS_HINTS = listOf("pass", "hasło", "haslo", "pwd", "pin")

    fun parse(structure: AssistStructure): Fields {
        val user = mutableListOf<AutofillId>()
        val pass = mutableListOf<AutofillId>()
        var domain: String? = null
        var userValue: String? = null
        var passValue: String? = null

        fun visit(n: AssistStructure.ViewNode) {
            n.webDomain?.takeIf { it.isNotBlank() }?.let { domain = it }
            val id = n.autofillId
            if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) {
                val hints = (n.autofillHints?.toList() ?: emptyList()).map { it.lowercase() }
                val html = n.htmlInfo
                val htmlType = html?.attributes?.firstOrNull { it.first == "type" }?.second?.lowercase()
                val htmlName = html?.attributes?.filter { it.first == "name" || it.first == "id" || it.first == "autocomplete" }?.joinToString(" ") { it.second ?: "" }?.lowercase().orEmpty()
                val text = listOfNotNull(n.idEntry, n.hint?.toString(), htmlName).joinToString(" ").lowercase()
                val variation = n.inputType and InputType.TYPE_MASK_VARIATION
                val cls = n.inputType and InputType.TYPE_MASK_CLASS
                val isPass = hints.any { it.contains("password") } || htmlType == "password" ||
                    (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                    (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
                    PASS_HINTS.any { text.contains(it) }
                val isUser = !isPass && (
                    hints.any { it.contains("username") || it.contains("email") || it.contains("phone") } ||
                        htmlType == "email" || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || USER_HINTS.any { text.contains(it) }
                    )
                val value = n.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                if (isPass) { pass += id; if (!value.isNullOrEmpty()) passValue = value }
                else if (isUser) { user += id; if (!value.isNullOrEmpty()) userValue = value }
            }
            for (i in 0 until n.childCount) visit(n.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        // Pole tekstowe tuż przed hasłem, którego nie rozpoznaliśmy, i tak jest zwykle loginem - pomijamy dla prostoty.
        return Fields(user.distinct(), pass.distinct(), domain, structure.activityComponent?.packageName ?: "", userValue, passValue)
    }

    /**
     * Przeglądarki, którym wierzymy w adres strony. Zwykła aplikacja może w swoim widoku podać dowolną
     * „domenę” (np. allegro.pl) i tak wyłudzić hasło - jej domenie nie ufamy.
     */
    val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta", "com.microsoft.emmx", "com.brave.browser",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "com.ecosia.android", "org.torproject.torbrowser", "app.vanadium.browser",
        "com.mi.globalbrowser", "com.huawei.browser", "com.yandex.browser",
    )

    /** Domena strony - tylko z przeglądarki. */
    fun trustedDomain(f: Fields): String? = f.webDomain?.takeIf { f.packageName in BROWSERS }?.let { registrableDomain(hostOf(it)) }?.takeIf { it.isNotBlank() }

    private val GENERIC = setOf("com", "pl", "android", "app", "apps", "mobile", "client", "www", "org", "net", "io", "main", "prod")

    /** Dopasowanie wpisów: domena strony albo słowa z nazwy pakietu aplikacji. */
    fun match(logins: List<Login>, f: Fields): List<Login> {
        val dom = trustedDomain(f)
        if (dom != null) {
            val hits = logins.filter { l -> l.url.isNotBlank() && registrableDomain(hostOf(l.url)) == dom }
            if (hits.isNotEmpty()) return hits
            // Po nazwie tylko wpisy bez adresu: inaczej allegro.xyz dostałoby hasło do allegro.pl.
            val key = dom.substringBefore('.')
            return logins.filter { it.url.isBlank() && it.title.lowercase().replace(" ", "") == key }
        }
        if (f.packageName in BROWSERS) return emptyList()
        val words = f.packageName.lowercase().split('.', '_').filter { it.length >= 3 && it !in GENERIC }
        if (words.isEmpty()) return emptyList()
        return logins.filter { l ->
            val host = registrableDomain(hostOf(l.url)).substringBefore('.')
            val title = l.title.lowercase().filter { it.isLetterOrDigit() }
            words.any { w -> w == host || w == title || (title.length >= 4 && w.contains(title)) }
        }
    }
}

class OstojaAutofillService : AutofillService() {

    @Suppress("DEPRECATION")
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val f = Parser.parse(structure)
        if (f.packageName == packageName || (f.user.isEmpty() && f.pass.isEmpty())) return callback.onSuccess(null)

        val logins = app.vault.value.logins
        val matches = Parser.match(logins, f).take(5)
        // Aplikację rozpoznajemy tylko po nazwie pakietu, którą może podrobić - wtedy zawsze pytamy, komu dać hasło.
        val fromApp = Parser.trustedDomain(f) == null
        val needAuth = AppLock.enabled(this) || fromApp
        val response = FillResponse.Builder()
        val targets = f.user + f.pass

        matches.forEachIndexed { i, l ->
            val presentation = item(this, l.title, l.username.ifBlank { "Ostoja" })
            val ds = Dataset.Builder(presentation)
            if (needAuth || l.otpId != null) {
                targets.forEach { ds.setValue(it, null, presentation) }
                ds.setAuthentication(authSender(this, f, l.id, 100 + i))
            } else {
                f.user.forEach { ds.setValue(it, AutofillValue.forText(l.username), presentation) }
                f.pass.forEach { ds.setValue(it, AutofillValue.forText(l.password), presentation) }
            }
            response.addDataset(ds.build())
        }

        // Zawsze: „Szukaj w Ostoi…” - wybór dowolnego wpisu po odblokowaniu.
        if (targets.isNotEmpty() && logins.isNotEmpty()) {
            val p = item(this, "Szukaj w Ostoi…", if (matches.isEmpty()) "Brak dopasowań dla tej aplikacji" else "Inne konto")
            val ds = Dataset.Builder(p)
            targets.forEach { ds.setValue(it, null, p) }
            ds.setAuthentication(authSender(this, f, null, 99))
            response.addDataset(ds.build())
        }

        if (f.pass.isNotEmpty()) {
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or if (f.user.isNotEmpty()) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0
            response.setSaveInfo(SaveInfo.Builder(type, targets.toTypedArray()).build())
        }
        callback.onSuccess(if (matches.isEmpty() && logins.isEmpty() && f.pass.isEmpty()) null else response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val f = Parser.parse(structure)
        val pw = f.passValue
        if (pw.isNullOrEmpty()) return callback.onSuccess()
        val user = f.userValue.orEmpty()
        val vault = app.vault
        // Z aplikacji aktualizujemy tylko wpis zapisany wcześniej z tego samego pakietu - inaczej aplikacja
        // o nazwie „com.cokolwiek.allegro” mogłaby nadpisać hasło do Allegro.
        val candidates = if (Parser.trustedDomain(f) != null) Parser.match(vault.value.logins, f)
        else vault.value.logins.filter { it.url.isBlank() && it.note == "Aplikacja: ${f.packageName}" }
        val existing = candidates.firstOrNull { it.username.equals(user, ignoreCase = true) }
        if (existing != null) {
            if (existing.password != pw) vault.saveLogin(existing.copy(password = pw))
        } else {
            val web = f.webDomain?.takeIf { f.packageName in Parser.BROWSERS }
            val url = web?.let { "https://$it" } ?: ""
            val title = web?.let { registrableDomain(hostOf(it)).substringBefore('.') }
                ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(f.packageName, 0)).toString() }.getOrNull()
                ?: f.packageName
            vault.saveLogin(Login(title = title.replaceFirstChar { it.uppercase() }, username = user, password = pw, url = url, note = if (url.isEmpty()) "Aplikacja: ${f.packageName}" else ""))
        }
        callback.onSuccess()
    }

    companion object {
        fun item(ctx: Context, title: String, subtitle: String) = RemoteViews(ctx.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, subtitle)
        }

        private fun authSender(ctx: Context, f: Fields, loginId: String?, req: Int) =
            PendingIntent.getActivity(
                ctx, req,
                Intent(ctx, AutofillAuthActivity::class.java)
                    .putExtra(AutofillAuthActivity.EXTRA_LOGIN, loginId)
                    .putParcelableArrayListExtra(AutofillAuthActivity.EXTRA_USER, ArrayList(f.user))
                    .putParcelableArrayListExtra(AutofillAuthActivity.EXTRA_PASS, ArrayList(f.pass))
                    .putExtra(AutofillAuthActivity.EXTRA_DOMAIN, Parser.trustedDomain(f) ?: f.packageName)
                    .putExtra(AutofillAuthActivity.EXTRA_APP, if (Parser.trustedDomain(f) == null) f.packageName else null),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            ).intentSender
    }
}
