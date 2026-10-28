package pl.wolter.ostoja.data

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pl.wolter.ostoja.sync.PairInfo
import pl.wolter.ostoja.sync.SyncCrypto
import pl.wolter.ostoja.sync.Wire

class SyncTest {
    private fun login(id: String, title: String, t: Long, pw: String = "x") =
        Login(id = id, title = title, password = pw, created = t, updated = t)

    @Test fun mergeIsCommutativeAndNewerWins() {
        val a = VaultData(logins = listOf(login("1", "Google", 100), login("2", "GitHub", 100)))
        val b = VaultData(logins = listOf(login("1", "Google (nowe)", 200), login("3", "Steam", 150)))
        val ab = VaultSync.merge(a, b)
        val ba = VaultSync.merge(b, a)
        assertEquals(ab.logins.sortedBy { it.id }, ba.logins.sortedBy { it.id })
        assertEquals("Google (nowe)", ab.login("1")!!.title)
        assertEquals(setOf("1", "2", "3"), ab.logins.map { it.id }.toSet())
    }

    @Test fun localEditsAreStampedAndDeletesLeaveTombstones() {
        val base = VaultData(logins = listOf(login("1", "Google", 100), login("2", "Reddit", 100)))
        val edited = VaultSync.stamp(base, base.copy(logins = listOf(base.logins[0].copy(title = "Gmail"))), now = 5_000)
        assertEquals(5_000, edited.login("1")!!.modified)
        assertEquals(5_000L, edited.deleted["2"])
        // Komputer nadal ma „Reddit” - po scaleniu wpis znika po obu stronach.
        val merged = VaultSync.merge(base, edited)
        assertNull(merged.login("2"))
        assertEquals("Gmail", merged.login("1")!!.title)
    }

    @Test fun editAfterDeleteSurvives() {
        val deleted = VaultData(deleted = mapOf("1" to 1_000L))
        val edited = VaultData(logins = listOf(login("1", "Google", 100).copy(modified = 2_000)))
        assertEquals("Google", VaultSync.merge(deleted, edited).login("1")!!.title)
        assertTrue(VaultSync.merge(deleted, edited).deleted.isEmpty())
    }

    @Test fun sameOtpAddedTwiceIsDeduplicated() {
        val a1 = OtpAccount(id = "a", issuer = "GitHub", secret = "JBSWY3DPEHPK3PXP", created = 10)
        val a2 = OtpAccount(id = "b", issuer = "GitHub", secret = "JBSWY3DPEHPK3PXP", created = 20)
        val phone = VaultData(otps = listOf(a1))
        val pc = VaultData(otps = listOf(a2), logins = listOf(login("1", "GitHub", 5).copy(otpId = "b")))
        val m = VaultSync.merge(phone, pc)
        assertEquals(listOf("a"), m.otps.map { it.id })
        assertEquals("a", m.login("1")!!.otpId)
        assertEquals(m.otps, VaultSync.merge(pc, phone).otps)
    }

    @Test fun sealedMessagesRejectTamperingAndWrongKey() {
        val key = SyncCrypto.channelKey(SyncCrypto.random(32))
        val blob = Wire.sealRequest(key, "sync", "phone1", JsonPrimitive("sejf"), "n1")
        assertEquals(JsonPrimitive("sejf"), Wire.openRequest(key, "sync", "phone1", blob).body)
        // Ta sama wiadomość podsunięta pod inną ścieżkę albo urządzenie nie przejdzie.
        listOf({ Wire.openRequest(key, "answer", "phone1", blob) },
            { Wire.openRequest(key, "sync", "phone2", blob) },
            { Wire.openRequest(SyncCrypto.random(32), "sync", "phone1", blob) },
            { Wire.openRequest(key, "sync", "phone1", blob.copyOf().also { it[20] = (it[20] + 1).toByte() }) },
        ).forEach { f -> try { f(); fail("Powinno odrzucić") } catch (_: Exception) {} }
    }

    @Test fun pairUriRoundTrip() {
        val p = PairInfo("pc-1", "Laptop Ani", listOf("192.168.1.5", "10.0.0.2"), 47811, "t0k", SyncCrypto.random(32))
        val q = PairInfo.parse(p.toUri())
        assertEquals(p.computerName, q.computerName)
        assertEquals(p.hosts, q.hosts)
        assertTrue(p.secret.contentEquals(q.secret))
    }

    @Test fun loginAddedFromBrowserFindsItsTwoFactorCode() {
        val gh = OtpAccount(id = "o", issuer = "GitHub", label = "annanowak", secret = "JBSWY3DPEHPK3PXP")
        val google = OtpAccount(id = "g", issuer = "Google", secret = "JBSWY3DPEHPK3PXP")
        val d = VaultData(otps = listOf(google, gh), logins = listOf(Login(id = "l", title = "Github", url = "https://github.com")))
        assertEquals("o", VaultOps.otpFor(d, d.login("l")!!)!!.id)
        assertEquals("o", VaultOps.linkOtp(d, "l").login("l")!!.otpId)
        val other = d.copy(logins = listOf(Login(id = "x", title = "Allegro", url = "https://allegro.pl")))
        assertNull(VaultOps.otpFor(other, other.login("x")!!))
    }
}
