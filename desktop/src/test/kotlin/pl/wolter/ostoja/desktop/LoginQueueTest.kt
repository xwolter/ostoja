package pl.wolter.ostoja.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** „Zaloguj na komputerze”, gdy Firefox jest zamknięty: aplikacja uruchamia go, a polecenie czeka na rozszerzenie. */
class LoginQueueTest {
    private fun bridge(paired: Boolean): Pair<ExtBridge, IntArray> {
        val dir = File("build/queue-test-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "key.bin").writeBytes(ByteArray(32))
        val store = Store(dir)
        if (paired) store.mutate { it.copy(extensions = listOf(ExtClient("e1", "Firefox", "AAAA", 0, 0))) }
        val launches = IntArray(1)
        return ExtBridge(store) {}.apply { launchBrowser = { launches[0]++; true } } to launches
    }

    @Test fun closedFirefoxIsStartedAndGetsTheCommand() {
        ExtBridge.CONFIRM_MS = 300
        val (b, launches) = bridge(paired = true)
        // Zapytanie z przeglądarki, którą właśnie zamknięto: odbiera polecenie, ale nikt go nie wykona.
        val lost = Thread { b.takeCommands(5_000) }.apply { start() }
        assertTrue(b.queueLogin("site", "Galaxy"))
        lost.join(2_000)
        Thread.sleep(600)
        assertEquals(1, launches[0])
        // Nowo uruchomiony Firefox od razu dostaje to samo polecenie.
        val got = b.takeCommands(1_000)
        assertEquals(listOf("site"), got.map { it.loginId })
    }

    @Test fun runningFirefoxIsNotStartedAgain() {
        ExtBridge.CONFIRM_MS = 300
        val (b, launches) = bridge(paired = true)
        val waiter = Thread {
            val c = b.takeCommands(5_000)
            // Rozszerzenie pobiera dane wpisu - tak samo jak „creds” w route().
            val m = ExtBridge::class.java.getDeclaredMethod("confirm", String::class.java).apply { isAccessible = true }
            m.invoke(b, c.single().loginId)
        }.apply { start() }
        assertTrue(b.queueLogin("site", "Galaxy"))
        waiter.join(2_000)
        Thread.sleep(600)
        assertEquals(0, launches[0])
        assertTrue(b.takeCommands(200).isEmpty())
    }

    @Test fun noExtensionMeansNoQueue() {
        val (b, launches) = bridge(paired = false)
        assertFalse(b.queueLogin("site", "Galaxy"))
        Thread.sleep(100)
        assertEquals(0, launches[0])
    }
}
