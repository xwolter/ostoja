package pl.wolter.ostoja.desktop

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.sync.SyncProtocol
import java.io.File
import java.net.InetSocketAddress

/**
 * Zgodność rozszerzenia z aplikacją: prawdziwy firefox/lib/protocol.js (w Node) paruje się z ExtBridge
 * przez ECDH, porównuje kod i pobiera szyfrowane dane. Wymaga Node i wolnego portu 47811
 * (aplikacja Ostoja nie może wtedy działać); inaczej test jest pomijany.
 */
class ExtBridgeTest {
    @Test fun firefoxProtocolTalksToApp() {
        val node = runCatching { ProcessBuilder("node", "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue("brak Node", node)
        // Osobny port - działająca aplikacja Ostoja zostaje nietknięta.
        val port = 47899
        val server = runCatching { HttpServer.create(InetSocketAddress("127.0.0.1", port), 8) }.getOrNull()
        assumeTrue("port $port zajęty", server != null)

        val dir = File("build/ext-test-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "key.bin").writeBytes(ByteArray(32))
        val store = Store(dir)
        val otp = OtpAccount(id = "o1", issuer = "Testowa", secret = "JBSWY3DPEHPK3PXP")
        val now = System.currentTimeMillis()
        store.mutate {
            it.copy(
                phones = listOf(Phone("p1", "Galaxy", "AAAA", now, now, now)),
                vault = VaultData(logins = listOf(
                    Login(id = "site", title = "Testowa", username = "anna", password = "Sekret-123!", url = "http://localhost:8765/login.html", otpId = "o1"),
                    Login(id = "gh", title = "Github", username = "annanowak", password = "x", url = "https://github.com/login"),
                    Login(id = "shop", title = "Sklep", username = "jan", password = "y", url = "https://sklep.example.pl"),
                ), otps = listOf(otp)),
                unlockedUntil = now + 3600_000,
            )
        }
        val bridge = ExtBridge(store) {}
        server!!.createContext("/ext/") { bridge.route(it) }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        try {
            val script = File("../firefox/test/protocol-client.cjs").absolutePath
            val shot = File(dir, "tab.jpg").also { javax.imageio.ImageIO.write(QrTest.githubSetupPage(), "jpg", it) }
            val proc = ProcessBuilder("node", script).redirectErrorStream(true)
                .apply { environment()["OSTOJA_BASE"] = "http://127.0.0.1:$port"; environment()["QR_JPEG"] = shot.absolutePath }.start()
            val out = proc.inputStream.bufferedReader()
            val codeLine = out.readLine()
            check(codeLine.startsWith("CODE ")) { codeLine }
            // Ten sam kod po obu stronach = ta sama wymiana kluczy ECDH.
            assertEquals(bridge.pairing.value!!.code, codeLine.removePrefix("CODE "))
            bridge.approve(true)
            val result = out.readLine()
            proc.waitFor()
            check(result.startsWith("RESULT ")) { result }
            val r = Json.parseToJsonElement(result.removePrefix("RESULT ")).jsonObject
            assertEquals("false", r["state"]!!.jsonObject["locked"]!!.jsonPrimitive.content)
            assertEquals("localhost", r["list"]!!.jsonObject["items"]!!.jsonArray[0].jsonObject["domain"]!!.jsonPrimitive.content)
            assertEquals("Sekret-123!", r["creds"]!!.jsonObject["password"]!!.jsonPrimitive.content)
            // Zapisywanie z rozszerzenia: rozpoznanie nowego konta i zmienionego hasła, zapis i aktualizacja.
            assertEquals("[\"same\",\"changed\",\"new\",\"Allegro\"]", r["check"].toString())
            val v = store.value.vault
            assertEquals("Allegro-2026!", v.logins.first { it.title == "Allegro" }.password)
            assertEquals("Nowe-haslo-1", v.login("site")!!.password)
            assertEquals("Sekret-123!", v.login("site")!!.history.first().password)
            // Kod QR z karty GitHuba: konto 2FA dodane i od razu połączone z hasłem GitHuba.
            assertEquals("""[{"ok":true,"title":"GitHub","error":""},{"ok":false,"title":"GitHub","error":"exists"}]""", r["qr"].toString())
            val gh = store.value.vault.otps.single { it.issuer == "GitHub" }
            assertEquals("KZ7Q4M2XWOPL3RTB", gh.secret)
            assertEquals(gh.id, store.value.vault.login("gh")!!.otpId)
            // Strona logowania znaleziona przez rozszerzenie: zapisuje się tylko zamiast samej domeny i tylko na tej domenie.
            assertEquals("[true,false,false]", r["loginUrl"].toString())
            assertEquals("https://sklep.example.pl/logowanie", store.value.vault.login("shop")!!.url)
            assertEquals("https://github.com/login", store.value.vault.login("gh")!!.url)
            val code = r["otp"]!!.jsonObject["code"]!!.jsonPrimitive.content
            val t = System.currentTimeMillis()
            check(code == Otp.code(otp, t) || code == Otp.code(otp, t, 1) || code == Otp.code(otp, t, -1)) { code }
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }
}
