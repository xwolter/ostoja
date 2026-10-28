package pl.wolter.ostoja.data

import java.security.SecureRandom
import kotlin.math.ln

data class GenOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val noAmbiguous: Boolean = true,
)

object PasswordTools {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/~"
    private const val AMBIGUOUS = "Il1O0o|`'\""
    private val rnd = SecureRandom()

    fun generate(o: GenOptions = GenOptions()): String {
        fun pool(s: String) = if (o.noAmbiguous) s.filter { it !in AMBIGUOUS } else s
        val sets = buildList {
            if (o.lower) add(pool(LOWER))
            if (o.upper) add(pool(UPPER))
            if (o.digits) add(pool(DIGITS))
            if (o.symbols) add(pool(SYMBOLS))
        }.ifEmpty { listOf(pool(LOWER)) }
        val all = sets.joinToString("")
        val len = o.length.coerceIn(4, 128)
        val chars = CharArray(len) { all[rnd.nextInt(all.length)] }
        // Gwarancja: co najmniej jeden znak z każdej wybranej grupy, w losowych miejscach.
        val slots = (0 until len).shuffled(rnd).take(sets.size)
        sets.forEachIndexed { i, set -> if (i < slots.size) chars[slots[i]] = set[rnd.nextInt(set.length)] }
        return String(chars)
    }

    private val COMMON = setOf(
        "123456", "123456789", "12345678", "password", "qwerty", "qwerty123", "1qaz2wsx", "111111", "123123",
        "abc123", "password1", "1234567", "12345", "1234567890", "000000", "iloveyou", "admin", "welcome",
        "monkey", "dragon", "letmein", "football", "polska", "haslo", "haslo123", "zaq12wsx", "qwertyuiop",
        "mateusz", "kochanie", "marcin", "michal", "bartek", "agnieszka", "misiek", "samsung", "google",
    )

    /** 0 - bardzo słabe … 4 - bardzo silne. Uproszczony estymator entropii z karami za wzorce. */
    fun strength(p: String): Int {
        if (p.isEmpty()) return 0
        val lower = p.lowercase()
        if (lower in COMMON || COMMON.any { lower.startsWith(it) && p.length <= it.length + 2 }) return 0
        var pool = 0
        if (p.any { it.isLowerCase() }) pool += 26
        if (p.any { it.isUpperCase() }) pool += 26
        if (p.any { it.isDigit() }) pool += 10
        if (p.any { !it.isLetterOrDigit() }) pool += 33
        var bits = p.length * (ln(pool.coerceAtLeast(2).toDouble()) / ln(2.0))
        val unique = p.toSet().size.toDouble() / p.length
        if (unique < .5) bits *= .6
        var seq = 0
        for (i in 2 until p.length) {
            val a = p[i - 2].code; val b = p[i - 1].code; val c = p[i].code
            if ((b - a == 1 && c - b == 1) || (a - b == 1 && b - c == 1) || (a == b && b == c)) seq++
        }
        bits -= seq * 4
        if (listOf("qwerty", "asdf", "zxcv", "1234", "haslo", "password").any { lower.contains(it) }) bits -= 12
        // „allegro2019”, „Kasia1990!” - słowo z rokiem to jeden z pierwszych wzorców w atakach słownikowych
        if (Regex("(19|20)\\d{2}").containsMatchIn(p)) bits -= 10
        if (Regex("^[A-Za-z]+\\d+[^A-Za-z0-9]?$").matches(p)) bits -= 10
        return when {
            bits < 30 -> 0
            bits < 45 -> 1
            bits < 64 -> 2
            bits < 90 -> 3
            else -> 4
        }
    }

    val STRENGTH_LABELS = listOf("Bardzo słabe", "Słabe", "Średnie", "Silne", "Bardzo silne")
}

data class SecurityReport(
    val score: Int,
    val weak: Set<String>,
    val reused: Set<String>,
    val old: Set<String>,
    val breached: Set<String>,
    val without2fa: Int,
) {
    val label get() = when {
        score >= 90 -> "Świetna"
        score >= 70 -> "Dobra"
        score >= 45 -> "Do poprawy"
        else -> "Słaba"
    }
    val issues get() = (weak + reused + breached).size
    fun flag(id: String) = when (id) {
        in breached -> Flag.LEAKED
        in weak -> Flag.WEAK
        in reused -> Flag.REUSED
        else -> null
    }
}

enum class Flag(val label: String, val note: String) {
    WEAK("Słabe", "To hasło łatwo odgadnąć. Wygeneruj nowe."),
    REUSED("Powtórzone", "To samo hasło używasz w innym serwisie."),
    LEAKED("W wycieku", "Hasło pojawiło się w publicznym wycieku danych. Zmień je jak najszybciej."),
}

fun VaultData.securityReport(now: Long = System.currentTimeMillis()): SecurityReport {
    val withPw = logins.filter { it.password.isNotEmpty() && (it.type == Login.LOGIN || it.type == Login.WIFI) }
    val weak = withPw.filter { PasswordTools.strength(it.password) <= 1 }.map { it.id }.toSet()
    val reused = withPw.groupBy { it.password }.values.filter { it.size > 1 }.flatten().map { it.id }.toSet()
    val old = withPw.filter { now - it.updated > 365L * 24 * 3600 * 1000 }.map { it.id }.toSet()
    val breached = withPw.filter { it.breached }.map { it.id }.toSet()
    val loginsOnly = logins.filter { it.isLogin }
    val no2fa = loginsOnly.count { it.otpId == null }
    val score = if (withPw.isEmpty()) 100 else {
        val n = withPw.size.toDouble()
        val penalty = 45 * weak.size / n + 25 * reused.size / n + 60 * breached.size / n + 8 * old.size / n +
            4.0 * no2fa / loginsOnly.size.coerceAtLeast(1)
        (100 - penalty).toInt().coerceIn(0, 100)
    }
    return SecurityReport(score, weak, reused, old, breached, no2fa)
}
