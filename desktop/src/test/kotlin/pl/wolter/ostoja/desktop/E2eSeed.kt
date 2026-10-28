package pl.wolter.ostoja.desktop

import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.VaultData
import java.io.File

/**
 * Folder danych do testu rozszerzenia Firefox (uruchamiany tylko z OSTOJA_SEED_DIR):
 * odblokowany sejf z wpisem dla lokalnej strony testowej z logowaniem w dwóch krokach i 2FA.
 */
class E2eSeed {
    @Test fun seed() {
        val path = System.getenv("OSTOJA_SEED_DIR")
        assumeTrue(path != null)
        val dir = File(path!!).apply { deleteRecursively(); mkdirs() }
        File(dir, "key.bin").writeBytes(ByteArray(32) { it.toByte() })
        val now = System.currentTimeMillis()
        val otp = OtpAccount(id = "otp-test", issuer = "Testowa strona", label = "anna@test.pl", secret = "JBSWY3DPEHPK3PXP")
        val vault = VaultData(
            logins = listOf(
                Login(id = "site", title = "Testowa strona", username = "anna@test.pl", password = "Sekret-123!", url = "http://localhost:8765/login.html", otpId = otp.id),
                Login(id = "plain", title = "Prosta strona", username = "jan", password = "Haslo-456?", url = "http://localhost:8765/simple.html"),
                Login(id = "gh", title = "GitHub", username = "annanowak-dev", password = "x", url = "https://github.com/login"),
            ),
            otps = listOf(otp),
        )
        Store(dir).mutate {
            it.copy(
                phones = listOf(Phone("p1", "Galaxy S26 Ultra", pl.wolter.ostoja.sync.SyncCrypto.b64(ByteArray(32) { 7 }), now, now, now)),
                vault = vault,
                unlockedUntil = now + 24 * 3600_000L,
                confirmedAt = now,
            )
        }
    }
}
