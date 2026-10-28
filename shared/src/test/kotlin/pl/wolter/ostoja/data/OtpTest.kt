package pl.wolter.ostoja.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class OtpTest {
    private fun acc(secretAscii: String, alg: String, digits: Int = 8) =
        OtpAccount(issuer = "t", secret = Otp.base32Encode(secretAscii.toByteArray()), algorithm = alg, digits = digits)

    @Test fun rfc4226_hotp() {
        val key = "12345678901234567890".toByteArray()
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { c, code -> assertEquals(code, Otp.hotp(key, c.toLong(), 6, "SHA1")) }
    }

    @Test fun rfc6238_totp_sha1() {
        val a = acc("12345678901234567890", "SHA1")
        assertEquals("94287082", Otp.code(a, 59_000))
        assertEquals("07081804", Otp.code(a, 1_111_111_109_000))
        assertEquals("14050471", Otp.code(a, 1_111_111_111_000))
        assertEquals("89005924", Otp.code(a, 1_234_567_890_000))
        assertEquals("69279037", Otp.code(a, 2_000_000_000_000))
    }

    @Test fun rfc6238_totp_sha256_sha512() {
        assertEquals("46119246", Otp.code(acc("12345678901234567890123456789012", "SHA256"), 59_000))
        assertEquals("90693936", Otp.code(acc("1234567890123456789012345678901234567890123456789012345678901234", "SHA512"), 59_000))
    }

    @Test fun base32() {
        val b = Otp.base32Decode("JBSW Y3DP EHPK 3PXP")
        assertEquals("48656c6c6f21deadbeef", b.joinToString("") { "%02x".format(it) })
        assertEquals("JBSWY3DPEHPK3PXP", Otp.base32Encode(b))
        assertTrue(Otp.isValidSecret("jbswy3dpehpk3pxp"))
        assertFalse(Otp.isValidSecret("JBSW1890"))
        // Klucz z GitHuba przepisany z zerem zamiast litery O i skopiowany z twardymi spacjami / nową linią.
        assertTrue(Otp.isValidSecret("KZ7Q4M2XW0PL3RTB"))
        assertEquals("KZ7Q4M2XWOPL3RTB", Otp.normalizeSecret("kz7q 4m2x w0pl\n3rtb"))
        assertFalse(Otp.isValidSecret("abc"))
    }

    @Test fun pretty() {
        assertEquals("482 913", Otp.pretty("482913"))
        assertEquals("1234 5678", Otp.pretty("12345678"))
        assertEquals(10_000, Otp.remainingMs(30, 20_000))
    }

    @Test fun parse_otpauth() {
        val a = OtpUri.parse("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30").single()
        assertEquals("ACME Co", a.issuer)
        assertEquals("john.doe@email.com", a.label)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", a.secret)
        assertEquals(30, a.period)

        val b = OtpUri.parse("otpauth://totp/GitHub:octocat?secret=jbswy3dpehpk3pxp&digits=8&period=60&algorithm=sha256").single()
        assertEquals("GitHub", b.issuer)
        assertEquals("octocat", b.label)
        assertEquals("JBSWY3DPEHPK3PXP", b.secret)
        assertEquals(8, b.digits)
        assertEquals(60, b.period)
        assertEquals("SHA256", b.algorithm)

        val h = OtpUri.parse("otpauth://hotp/Bank?secret=JBSWY3DPEHPK3PXP&counter=7").single()
        assertTrue(h.isHotp)
        assertEquals(7, h.counter)
        assertEquals("Bank", h.issuer)
    }

    @Test fun build_roundtrip() {
        val a = OtpAccount(issuer = "Mój Bank", label = "jan kowalski", secret = "JBSWY3DPEHPK3PXP", digits = 8, period = 60, algorithm = "SHA512")
        val b = OtpUri.parse(OtpUri.build(a)).single()
        assertEquals(a.issuer, b.issuer)
        assertEquals(a.label, b.label)
        assertEquals(a.secret, b.secret)
        assertEquals(a.digits, b.digits)
        assertEquals(a.period, b.period)
        assertEquals(a.algorithm, b.algorithm)
    }

    @Test(expected = OtpUri.ParseError::class)
    fun reject_garbage() { OtpUri.parse("https://example.com") }

    /** Ręcznie złożony MigrationPayload z Google Authenticator (protobuf). */
    @Test fun parse_migration() {
        fun varint(v: Int): ByteArray {
            val out = mutableListOf<Byte>()
            var x = v
            while (x >= 0x80) { out += ((x and 0x7F) or 0x80).toByte(); x = x ushr 7 }
            out += x.toByte()
            return out.toByteArray()
        }
        fun field(n: Int, bytes: ByteArray) = varint(n shl 3 or 2) + varint(bytes.size) + bytes
        fun fieldInt(n: Int, v: Int) = varint(n shl 3) + varint(v)
        fun otp(secret: ByteArray, name: String, issuer: String, type: Int) =
            field(1, secret) + field(2, name.toByteArray()) + field(3, issuer.toByteArray()) +
                fieldInt(4, 1) + fieldInt(5, 1) + fieldInt(6, type)

        val s1 = "Hello!".toByteArray() + byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val payload = field(1, otp(s1, "Google:alice@gmail.com", "Google", 2)) +
            field(1, otp("12345678901234567890".toByteArray(), "bob", "GitHub", 2)) +
            fieldInt(2, 1) + fieldInt(3, 1) + fieldInt(4, 0)
        val data = URLEncoder.encode(java.util.Base64.getEncoder().encodeToString(payload), "UTF-8")
        val list = OtpUri.parse("otpauth-migration://offline?data=$data")
        assertEquals(2, list.size)
        assertEquals("Google", list[0].issuer)
        assertEquals("alice@gmail.com", list[0].label)
        assertEquals("JBSWY3DPEHPK3PXP", list[0].secret)
        assertEquals("GitHub", list[1].issuer)
        assertEquals("bob", list[1].label)

        // Eksport podzielony na kilka kodów QR: drugi z trzech, wspólny identyfikator serii.
        val second = field(1, otp("abcdefghij".toByteArray(), "carol", "Discord", 2)) +
            fieldInt(2, 1) + fieldInt(3, 3) + fieldInt(4, 1) + fieldInt(5, 777)
        val m = OtpUri.migration("otpauth-migration://offline?data=" + URLEncoder.encode(java.util.Base64.getEncoder().encodeToString(second), "UTF-8"))!!
        assertEquals(1, m.index)
        assertEquals(3, m.size)
        assertEquals(777L, m.id)
        assertEquals("Discord", m.accounts.single().issuer)
        assertEquals(null, OtpUri.migration("otpauth://totp/X?secret=JBSWY3DPEHPK3PXP"))
    }
}

class ToolsTest {
    @Test fun generator() {
        repeat(50) {
            val p = PasswordTools.generate(GenOptions(length = 24))
            assertEquals(24, p.length)
            assertTrue(p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
            assertTrue(p.none { it in "Il1O0o" })
        }
        val digits = PasswordTools.generate(GenOptions(length = 10, upper = false, lower = false, symbols = false))
        assertTrue(digits.all { it.isDigit() })
    }

    @Test fun strength() {
        assertEquals(0, PasswordTools.strength("qwerty123"))
        assertTrue(PasswordTools.strength("allegro2019") <= 1)
        assertTrue(PasswordTools.strength("Tq7#vLm2!pZx9wRb") >= 3)
        assertTrue(PasswordTools.strength(PasswordTools.generate(GenOptions(length = 32))) == 4)
    }

    @Test fun report() {
        val d = VaultData(
            logins = listOf(
                Login(title = "A", password = "qwerty123"),
                Login(title = "B", password = "qwerty123"),
                Login(title = "C", password = "Tq7#vLm2!pZx9wRb"),
            )
        )
        val r = d.securityReport()
        assertEquals(2, r.weak.size)
        assertEquals(2, r.reused.size)
        assertTrue(r.score < 65)
    }

    @Test fun domains() {
        assertEquals("accounts.google.com", hostOf("https://accounts.google.com/signin?x=1"))
        assertEquals("google.com", registrableDomain("accounts.google.com"))
        assertEquals("allegro.pl", registrableDomain(hostOf("https://www.allegro.pl/")))
        assertEquals("bbc.co.uk", registrableDomain("news.bbc.co.uk"))
        assertEquals("example.com.pl", registrableDomain("a.example.com.pl"))
    }

    @Test fun plural() {
        assertEquals("1 hasło", countLabel(1, "hasło", "hasła", "haseł"))
        assertEquals("3 hasła", countLabel(3, "hasło", "hasła", "haseł"))
        assertEquals("5 haseł", countLabel(5, "hasło", "hasła", "haseł"))
        assertEquals("12 haseł", countLabel(12, "hasło", "hasła", "haseł"))
        assertEquals("22 hasła", countLabel(22, "hasło", "hasła", "haseł"))
    }

    @Test fun csv_chrome_and_bitwarden() {
        val chrome = "name,url,username,password,note\r\n" +
            "accounts.google.com,https://accounts.google.com/,jan@gmail.com,\"pa,ss\"\"1\",\r\n" +
            ",https://www.allegro.pl/login,jan_k,secret,\"wiele\nlinii\"\n"
        val a = CsvImport.parse(chrome)
        assertEquals(2, a.size)
        assertEquals("pa,ss\"1", a[0].password)
        assertEquals("Allegro", a[1].title)
        assertEquals("wiele\nlinii", a[1].note)

        val bw = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            ",,login,GitHub,,,0,https://github.com,octo,hunter2,\n"
        val b = CsvImport.parse(bw).single()
        assertEquals("GitHub", b.title)
        assertEquals("octo", b.username)
        assertEquals("hunter2", b.password)
    }
}
