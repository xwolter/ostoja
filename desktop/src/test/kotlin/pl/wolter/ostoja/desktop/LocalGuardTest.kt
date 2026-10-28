package pl.wolter.ostoja.desktop

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Strona internetowa nie dogada się z aplikacją na 127.0.0.1 - ani wprost, ani przez DNS rebinding. */
class LocalGuardTest {
    private fun status(port: Int, host: String, origin: String? = null): Int = Socket("127.0.0.1", port).use { s ->
        val req = "GET /ext/hello HTTP/1.1\r\nHost: $host\r\n" + (origin?.let { "Origin: $it\r\n" } ?: "") + "Connection: close\r\n\r\n"
        s.getOutputStream().write(req.toByteArray())
        s.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
    }

    @Test fun onlyTheExtensionGetsIn() {
        val dir = File("build/guard-test-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "key.bin").writeBytes(ByteArray(32))
        val bridge = ExtBridge(Store(dir)) {}
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/ext/") { bridge.route(it) }
        server.start()
        val port = server.address.port
        try {
            assertEquals(200, status(port, "127.0.0.1:$port", "moz-extension://abc"))
            assertEquals(403, status(port, "127.0.0.1:$port", "https://zla-strona.pl"))
            // DNS rebinding: zla-strona.pl wskazuje na 127.0.0.1, przeglądarka wysyła Host: zla-strona.pl.
            assertEquals(403, status(port, "zla-strona.pl:$port"))
        } finally {
            server.stop(0)
        }
    }
}
