package pl.wolter.ostoja.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 4226 (HOTP) i RFC 6238 (TOTP). */
object Otp {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Klucz przepisany ze strony albo skopiowany: bez odstępów (też twardych i nowych linii), myślników i „=”.
     * Cyfry 0 nie ma w Base32 - przepisując np. „…TY02…” z GitHuba, ktoś wziął literę O za zero.
     */
    fun normalizeSecret(s: String) = s.uppercase().filterNot { it.isWhitespace() || it == '-' || it == '=' }.replace('0', 'O')

    fun isValidSecret(s: String): Boolean {
        val n = normalizeSecret(s)
        return n.length >= 8 && n.all { it in B32 } && runCatching { base32Decode(n) }.getOrNull()?.isNotEmpty() == true
    }

    fun base32Decode(input: String): ByteArray {
        val s = normalizeSecret(input)
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in s) {
            val v = B32.indexOf(ch)
            require(v >= 0) { "Niepoprawny znak w kluczu: $ch" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun randomSecret(bytes: Int = 20): String =
        base32Encode(ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) })

    private fun macName(alg: String) = when (alg.uppercase()) {
        "SHA256" -> "HmacSHA256"
        "SHA512" -> "HmacSHA512"
        else -> "HmacSHA1"
    }

    fun hotp(key: ByteArray, counter: Long, digits: Int = 6, algorithm: String = "SHA1"): String {
        val mac = Mac.getInstance(macName(algorithm))
        mac.init(SecretKeySpec(key, "RAW"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val o = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
        var mod = 1L
        repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    fun counterAt(acc: OtpAccount, now: Long) = if (acc.isHotp) acc.counter else now / 1000 / acc.period.coerceAtLeast(1)

    fun code(acc: OtpAccount, now: Long = System.currentTimeMillis(), offset: Long = 0): String = runCatching {
        hotp(base32Decode(acc.secret), counterAt(acc, now) + offset, acc.digits, acc.algorithm)
    }.getOrDefault("-".repeat(acc.digits))

    /** Milisekundy do końca bieżącego okna. */
    fun remainingMs(period: Int, now: Long = System.currentTimeMillis()): Long {
        val p = period.coerceAtLeast(1) * 1000L
        return p - now % p
    }

    /** „482913” → „482 913”, „12345678” → „1234 5678”. */
    fun pretty(code: String): String = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        7 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        else -> code
    }

    fun masked(digits: Int) = pretty("•".repeat(digits))
}

/** Parser linków otpauth:// oraz eksportu z Google Authenticator (otpauth-migration://). */
object OtpUri {

    class ParseError(msg: String) : Exception(msg)

    fun parse(raw: String): List<OtpAccount> {
        val s = raw.trim()
        return when {
            s.startsWith("otpauth-migration://", ignoreCase = true) -> parseMigration(s).accounts
            s.startsWith("otpauth://", ignoreCase = true) -> listOf(parseSingle(s))
            else -> throw ParseError("To nie jest kod konta 2FA")
        }
    }

    private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun query(s: String): Map<String, String> {
        val q = s.substringAfter('?', "")
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull {
            val k = it.substringBefore('=')
            if (k.isEmpty()) null else k.lowercase() to dec(it.substringAfter('=', ""))
        }.toMap()
    }

    /**
     * Jeden kod QR z eksportu Google Authenticator („Przenieś konta”). Przy wielu kontach Google dzieli eksport
     * na kilka kodów: [index] liczony od 0, [size] - ile ich jest, [id] - wspólny dla całej serii.
     */
    data class Migration(val accounts: List<OtpAccount>, val index: Int, val size: Int, val id: Long)

    /** Kod migracji z numerem w serii albo null, gdy to nie jest otpauth-migration://. */
    fun migration(raw: String): Migration? =
        raw.trim().takeIf { it.startsWith("otpauth-migration://", ignoreCase = true) }?.let(::parseMigration)

    fun parseSingle(s: String): OtpAccount {
        val rest = s.substring("otpauth://".length)
        val type = rest.substringBefore('/').lowercase()
        if (type != OtpAccount.TOTP && type != OtpAccount.HOTP) throw ParseError("Nieobsługiwany typ: $type")
        val label = dec(rest.substringAfter('/', "").substringBefore('?')).trim()
        val q = query(rest)
        val secret = q["secret"] ?: throw ParseError("Brak klucza w kodzie")
        if (!Otp.isValidSecret(secret)) throw ParseError("Niepoprawny klucz")
        val labelIssuer = label.substringBefore(':', "").trim()
        val account = if (label.contains(':')) label.substringAfter(':').trim() else label
        val issuer = q["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer.ifBlank { account }
        return OtpAccount(
            issuer = issuer,
            label = if (account == issuer) "" else account,
            secret = Otp.normalizeSecret(secret),
            algorithm = (q["algorithm"] ?: "SHA1").uppercase().takeIf { it in setOf("SHA1", "SHA256", "SHA512") } ?: "SHA1",
            digits = q["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6,
            period = q["period"]?.toIntOrNull()?.takeIf { it in 10..300 } ?: 30,
            type = type,
            counter = q["counter"]?.toLongOrNull() ?: 0,
        )
    }

    fun build(a: OtpAccount): String {
        fun enc(x: String) = URLEncoder.encode(x, "UTF-8").replace("+", "%20")
        val label = if (a.label.isNotBlank()) "${enc(a.issuer)}:${enc(a.label)}" else enc(a.issuer)
        val sb = StringBuilder("otpauth://${a.type}/$label?secret=${a.secret}&issuer=${enc(a.issuer)}")
        if (a.algorithm != "SHA1") sb.append("&algorithm=${a.algorithm}")
        if (a.digits != 6) sb.append("&digits=${a.digits}")
        if (a.isHotp) sb.append("&counter=${a.counter}") else if (a.period != 30) sb.append("&period=${a.period}")
        return sb.toString()
    }

    // --- Google Authenticator: MigrationPayload (protobuf) ---

    private class Pb(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun more() = p < end
        fun varint(): Long {
            var shift = 0
            var r = 0L
            while (true) {
                val x = b[p++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return r
                shift += 7
            }
        }
        fun bytes(): ByteArray {
            val n = varint().toInt()
            return b.copyOfRange(p, p + n).also { p += n }
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> p += 8
                2 -> p += varint().toInt()
                5 -> p += 4
                else -> throw ParseError("Uszkodzony kod migracji")
            }
        }
    }

    private fun parseMigration(s: String): Migration {
        val data = query(s)["data"] ?: throw ParseError("Brak danych migracji")
        val bytes = runCatching {
            java.util.Base64.getDecoder().decode(data.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/'))
        }.getOrElse { throw ParseError("Uszkodzony kod migracji") }
        val out = mutableListOf<OtpAccount>()
        var batchSize = 1
        var batchIndex = 0
        var batchId = 0L
        val pb = Pb(bytes)
        while (pb.more()) {
            val tag = pb.varint().toInt()
            if (tag shr 3 == 1 && tag and 7 == 2) {
                val m = Pb(pb.bytes())
                var secret = ByteArray(0)
                var name = ""
                var issuer = ""
                var alg = 1
                var digits = 1
                var type = 2
                var counter = 0L
                while (m.more()) {
                    val t = m.varint().toInt()
                    when (t shr 3) {
                        1 -> secret = m.bytes()
                        2 -> name = String(m.bytes())
                        3 -> issuer = String(m.bytes())
                        4 -> alg = m.varint().toInt()
                        5 -> digits = m.varint().toInt()
                        6 -> type = m.varint().toInt()
                        7 -> counter = m.varint()
                        else -> m.skip(t and 7)
                    }
                }
                if (secret.isEmpty()) continue
                val acct = if (name.contains(':')) name.substringAfter(':').trim() else name
                val iss = issuer.ifBlank { name.substringBefore(':', "").trim() }.ifBlank { acct }
                out += OtpAccount(
                    issuer = iss,
                    label = if (acct == iss) "" else acct,
                    secret = Otp.base32Encode(secret),
                    algorithm = when (alg) { 2 -> "SHA256"; 3 -> "SHA512"; else -> "SHA1" },
                    digits = if (digits == 2) 8 else 6,
                    type = if (type == 1) OtpAccount.HOTP else OtpAccount.TOTP,
                    counter = counter,
                )
            } else when {
                tag == (3 shl 3) -> batchSize = pb.varint().toInt().coerceAtLeast(1)
                tag == (4 shl 3) -> batchIndex = pb.varint().toInt().coerceAtLeast(0)
                tag == (5 shl 3) -> batchId = pb.varint()
                else -> pb.skip(tag and 7)
            }
        }
        if (out.isEmpty()) throw ParseError("Kod migracji nie zawiera kont")
        return Migration(out, batchIndex.coerceAtMost(batchSize - 1), batchSize, batchId)
    }
}
