package pl.wolter.ostoja.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DomainsTest {
    @Test fun registrableDomains() {
        mapOf(
            "www.allegro.pl" to "allegro.pl",
            "konto.allegro.pl" to "allegro.pl",
            "login.bank.co.uk" to "bank.co.uk",
            "bank.com.pl" to "bank.com.pl",
            // Hostingi z subdomenami użytkowników: każda subdomena to inny właściciel.
            "mojprojekt.github.io" to "mojprojekt.github.io",
            "api.mojprojekt.github.io" to "mojprojekt.github.io",
            "sklep.waw.pl" to "sklep.waw.pl",
            "kubel.s3.eu-west-1.amazonaws.com" to "kubel.s3.eu-west-1.amazonaws.com",
            "github.io" to "github.io",
            "192.168.1.5" to "192.168.1.5",
            "localhost" to "localhost",
        ).forEach { (host, want) -> assertEquals(host, want, registrableDomain(host)) }
    }

    @Test fun loginInUrlDoesNotFoolHost() {
        assertEquals("zla-strona.com", hostOf("https://allegro.pl@zla-strona.com/login"))
        assertEquals("allegro.pl", hostOf("https://www.allegro.pl:443/logowanie?x=1"))
    }

    /** Rozszerzenie ma tę samą listę sufiksów (firefox/lib/protocol.js). */
    @Test fun extensionUsesSameSuffixList() {
        val js = File("../firefox/lib/protocol.js").readText()
        fun set(name: String) = Regex("'([a-z0-9.-]+)'").findAll(js.substringAfter("const $name = ").substringBefore(']')).map { it.groupValues[1] }.toSet()
        assertEquals(Domains.PUBLIC, set("PUBLIC"))
        assertEquals(Domains.WHOLE_HOST, set("WHOLE_HOST"))
    }
}
