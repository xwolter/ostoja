package pl.wolter.ostoja.data

/** Polska odmiana: 1 hasło, 2 hasła, 5 haseł. */
fun plural(n: Int, one: String, few: String, many: String): String = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

fun countLabel(n: Int, one: String, few: String, many: String) = "$n ${plural(n, one, few, many)}"

/** Kolor kafelka dla serwisu: znane marki mają swój kolor, reszta - stały kolor z palety wg nazwy. */
object Brand {
    private val known = mapOf(
        "google" to 0xFF4285F4, "gmail" to 0xFFEA4335, "github" to 0xFF24292F, "gitlab" to 0xFFFC6D26,
        "facebook" to 0xFF1877F2, "instagram" to 0xFFE1306C, "messenger" to 0xFF0084FF, "whatsapp" to 0xFF25D366,
        "microsoft" to 0xFF00A4EF, "outlook" to 0xFF0078D4, "apple" to 0xFF1D1D1F, "icloud" to 0xFF3693F3,
        "amazon" to 0xFFFF9900, "allegro" to 0xFFFF5A00, "netflix" to 0xFFE50914, "spotify" to 0xFF1DB954,
        "discord" to 0xFF5865F2, "steam" to 0xFF1B2838, "twitch" to 0xFF9146FF, "x" to 0xFF111111, "twitter" to 0xFF1D9BF0,
        "linkedin" to 0xFF0A66C2, "reddit" to 0xFFFF4500, "dropbox" to 0xFF0061FF, "slack" to 0xFF4A154B,
        "samsung" to 0xFF1428A0, "revolut" to 0xFF0075EB, "mbank" to 0xFFE2001A, "pko" to 0xFF003574,
        "ing" to 0xFFFF6200, "santander" to 0xFFEC0000, "olx" to 0xFF23E5DB, "allegro.pl" to 0xFFFF5A00,
        "paypal" to 0xFF003087, "binance" to 0xFFF0B90B, "proton" to 0xFF6D4AFF, "notion" to 0xFF111111,
        "figma" to 0xFFA259FF, "adobe" to 0xFFFA0F00, "epic" to 0xFF2F2D2E, "ubisoft" to 0xFF0070FF, "usos" to 0xFF1F4E9B,
        "vinted" to 0xFF09B1BA, "uber" to 0xFF111111, "bolt" to 0xFF34D186, "booking" to 0xFF003580, "airbnb" to 0xFFFF5A5F,
    )
    private val palette = listOf(
        0xFF3E91FF, 0xFF2FA57A, 0xFF7A6CF0, 0xFFE0704A, 0xFFE0A030, 0xFF2BB3C0, 0xFFD9548F, 0xFF6C7A89,
    )

    fun color(name: String, url: String = ""): Long {
        val n = name.lowercase().trim()
        known[n]?.let { return it }
        val host = hostOf(url)
        known.entries.firstOrNull { (k, _) -> k.length > 2 && (n.startsWith(k) || host.contains(k)) }?.let { return it.value }
        return palette[(n.hashCode() and 0x7fffffff) % palette.size]
    }

    private val domains = mapOf(
        "google" to "google.com", "gmail" to "google.com", "github" to "github.com", "gitlab" to "gitlab.com",
        "discord" to "discord.com", "microsoft" to "microsoft.com", "outlook" to "outlook.com", "amazon" to "amazon.com",
        "aws" to "aws.amazon.com", "slack" to "slack.com", "proton" to "proton.me", "reddit" to "reddit.com",
        "notion" to "notion.so", "bitwarden" to "bitwarden.com", "facebook" to "facebook.com", "instagram" to "instagram.com",
        "x" to "x.com", "twitter" to "x.com", "linkedin" to "linkedin.com", "dropbox" to "dropbox.com", "apple" to "apple.com",
        "steam" to "steampowered.com", "epic games" to "epicgames.com", "twitch" to "twitch.tv", "allegro" to "allegro.pl",
        "binance" to "binance.com", "coinbase" to "coinbase.com", "revolut" to "revolut.com", "paypal" to "paypal.com",
        "mbank" to "mbank.pl", "pko" to "pkobp.pl", "ing" to "ing.pl", "netflix" to "netflix.com", "spotify" to "spotify.com",
        "samsung" to "samsung.com", "ubisoft" to "ubisoft.com", "figma" to "figma.com", "adobe" to "adobe.com",
    )

    /** Domena do ikony: z adresu strony, a gdy go brak - ze znanej marki albo z nazwy. */
    fun domain(name: String, url: String = ""): String {
        val h = registrableDomain(hostOf(url))
        if (h.contains('.')) return h
        val n = name.lowercase().trim()
        domains[n]?.let { return it }
        domains.entries.firstOrNull { (k, _) -> k.length > 2 && n.startsWith(k) }?.let { return it.value }
        if (n.contains('.') && !n.contains(' ')) return registrableDomain(n)
        val slug = n.filter { it.isLetterOrDigit() }
        return if (slug.length >= 3) "$slug.com" else ""
    }

    fun initial(name: String) = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}

/** Import CSV z Chrome / Google Password Manager / Samsung Pass / Bitwarden (kolumny po nazwach). */
object CsvImport {
    fun parse(text: String): List<Login> {
        val clean = text.removePrefix("﻿")
        val header = clean.substringBefore('\n')
        val delimiter = if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
        val rows = parseRows(clean, delimiter)
        if (rows.size < 2) return emptyList()
        val head = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } }
        val iTitle = col("name", "title", "nazwa")
        val iUrl = col("url", "login_uri", "website", "origin_url", "strona")
        val iUser = col("username", "login_username", "login", "user", "email", "nazwa użytkownika")
        val iPass = col("password", "login_password", "hasło", "haslo")
        val iNote = col("note", "notes", "comment", "notatka")
        if (iPass == null) return emptyList()
        return rows.drop(1).mapNotNull { r ->
            fun at(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val pw = at(iPass)
            val url = at(iUrl)
            val title = at(iTitle).ifBlank { registrableDomain(hostOf(url)).substringBefore('.').replaceFirstChar { it.uppercase() } }
            if (pw.isEmpty() && at(iUser).isEmpty()) null
            else Login(title = title.ifBlank { "Bez nazwy" }, username = at(iUser), password = pw, url = url, note = at(iNote))
        }
    }

    private fun parseRows(s: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"' && i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ }
                else if (c == '"') quoted = false
                else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                delimiter -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

/** Przykładowe dane z prototypu - do obejrzenia aplikacji bez wpisywania własnych haseł. */
object Demo {
    fun data(): VaultData {
        val day = 24 * 3600 * 1000L
        val now = System.currentTimeMillis()
        fun otp(issuer: String, label: String, fav: Boolean = false) = OtpAccount(issuer = issuer, label = label, secret = Otp.randomSecret(), favorite = fav)
        val google = otp("Google", "anna.nowak@gmail.com", true)
        val github = otp("GitHub", "annanowak-dev")
        val discord = otp("Discord", "anna_nowak")
        val microsoft = otp("Microsoft", "anna.nowak@outlook.com")
        val amazon = otp("Amazon", "anna.nowak@gmail.com")
        val slack = otp("Slack", "studio-polna")
        val proton = otp("Proton", "anna@proton.me")
        val reddit = otp("Reddit", "u/annanowak")
        val notion = otp("Notion", "anna@studiopolna.pl")
        val bitwarden = otp("Bitwarden", "anna@proton.me")
        fun l(title: String, user: String, pw: String, url: String, otp: OtpAccount? = null, ageDays: Int = 30, fav: Boolean = false, breached: Boolean = false) =
            Login(title = title, username = user, password = pw, url = url, otpId = otp?.id, favorite = fav,
                created = now - (ageDays + 200) * day, updated = now - ageDays * day, breached = breached, breachChecked = if (breached) now else 0)
        val logins = listOf(
            l("Google", "anna.nowak@gmail.com", "Vx8!rT2#pLq9wZ", "https://accounts.google.com", google, 30, fav = true),
            l("GitHub", "annanowak-dev", "kT9#vQ2!mLx7\$Rw4", "https://github.com", github, 12),
            l("mBank", "anowak84", "Qe7@Lm3\$zN8!pW", "https://online.mbank.pl", ageDays = 20, fav = true),
            l("Reddit", "u/annanowak", "Rd!2020anna", "https://reddit.com", reddit, 400, breached = true),
            l("Allegro", "anna.nowak@gmail.com", "allegro123", "https://allegro.pl", ageDays = 800),
            l("Netflix", "anna.nowak@gmail.com", "Kotek2019!", "https://netflix.com", ageDays = 450),
            l("Spotify", "annanowak", "Kotek2019!", "https://spotify.com", ageDays = 450),
            l("LinkedIn", "anna.nowak@outlook.com", "Ln\$8vW!q2Xe#", "https://linkedin.com", ageDays = 60),
            l("Booking.com", "anna.nowak@gmail.com", "wakacje", "https://booking.com", ageDays = 300),
            Login(title = "Visa · mBank", type = Login.CARD, username = "Anna Nowak", password = "4111 1111 1111 4821", note = "Ważna do 09/29 · CVV 123", created = now - 90 * day, updated = now - 90 * day),
            Login(title = "Dom", type = Login.WIFI, username = "Polna_5G", password = "zielona-laka-42", created = now - 100 * day, updated = now - 100 * day),
            Login(title = "Kod do bramy", type = Login.NOTE, note = "2580#", created = now - 10 * day, updated = now - 10 * day),
        )
        return VaultData(logins = logins, otps = listOf(google, github, discord, microsoft, amazon, slack, proton, reddit, notion, bitwarden))
    }
}
