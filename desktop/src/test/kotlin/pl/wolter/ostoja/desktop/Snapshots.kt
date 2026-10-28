package pl.wolter.ostoja.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import pl.wolter.ostoja.data.Demo
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Zrzuty ekranów aplikacji bez okna (ImageComposeScene) - do porównania z prototypem. */
class Snapshots {
    private val out = File("build/snapshots").apply { mkdirs() }

    // Efekty działają w pętli zdarzeń runBlocking (ten sam wątek co render) - inaczej opóźnienia
    // wznawiane z DefaultExecutor blokują się z renderem sceny.
    private fun shot(name: String, store: Store, ui: Ui, dark: Boolean = false, w: Int = 1280, h: Int = 880, steps: Int = 40) = runBlocking {
        val scene = ImageComposeScene(w, h, Density(1f), coroutineContext = coroutineContext) { App(store, ui, dark) }
        var t = 0L
        repeat(steps) { scene.render(t); t += 50_000_000L; delay(25) }
        val img = scene.render(t)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    private fun store(paired: Boolean, unlocked: Boolean): Store {
        val dir = File(out, "data-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "key.bin").writeBytes(ByteArray(32))
        return Store(dir).apply {
            Favicons.dir = File(out, "icons").apply { mkdirs() }
            if (paired) mutate { p ->
                val now = System.currentTimeMillis()
                p.copy(
                    phones = listOf(Phone("p1", "Galaxy S26 Ultra", "AAAA", now, now, now - 4000)),
                    vault = Demo.data(),
                    unlockedUntil = if (unlocked) now + 3600_000 else now - 1000,
                    confirmedAt = now - 8 * 86400_000L,
                    recovery = "x:y",
                )
            }
        }
    }

    @Test fun render() {
        val s = store(paired = true, unlocked = true)
        shot("vault", s, Ui())
        shot("vault-dark", s, Ui(), dark = true)
        shot("codes", s, Ui().apply { view = V_CODES })
        shot("health", s, Ui().apply { view = V_HEALTH })
        shot("devices", s, Ui().apply { view = V_DEVICES })
        shot("add-otp", s, Ui().apply { view = V_CODES; dialog = Dialog.AddOtp })
        shot("new-entry", s, Ui().apply { dialog = Dialog.EditLogin(null) }, dark = true, h = 980)
        val locked = store(paired = true, unlocked = false)
        shot("locked", locked, Ui())
        locked.startUnlock()
        shot("waiting", locked, Ui(), dark = true)
        shot("setup", store(paired = false, unlocked = false), Ui())
    }
}
