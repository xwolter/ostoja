package pl.wolter.ostoja.autofill

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wolter.ostoja.data.Login

/** Komu autouzupełnianie podsuwa hasło - ochrona przed fałszywymi stronami i aplikacjami. */
class AutofillMatchTest {
    private val allegro = Login(id = "a", title = "Allegro", username = "anna", password = "x", url = "https://allegro.pl")
    private val gh = Login(id = "g", title = "Pages", username = "anna", password = "y", url = "https://mojprojekt.github.io")
    private val logins = listOf(allegro, gh)

    private fun f(pkg: String, web: String?) = Fields(emptyList(), emptyList(), web, pkg, null, null)
    private fun ids(pkg: String, web: String?) = Parser.match(logins, f(pkg, web)).map { it.id }

    @Test fun browserGetsOnlyTheSameSite() {
        assertEquals(listOf("a"), ids("com.android.chrome", "www.allegro.pl"))
        // Podobna nazwa w innej domenie nie dostaje hasła.
        assertEquals(emptyList<String>(), ids("com.android.chrome", "allegro.xyz"))
        assertEquals(emptyList<String>(), ids("org.mozilla.firefox", "obcy.github.io"))
        assertEquals(listOf("g"), ids("org.mozilla.firefox", "mojprojekt.github.io"))
    }

    @Test fun appCannotClaimAWebsite() {
        // Zwykła aplikacja podaje w widoku „allegro.pl” - tej domenie nie wierzymy.
        assertEquals(null, Parser.trustedDomain(f("com.evil.app", "allegro.pl")))
        assertEquals("allegro.pl", Parser.trustedDomain(f("com.sec.android.app.sbrowser", "allegro.pl")))
    }
}
