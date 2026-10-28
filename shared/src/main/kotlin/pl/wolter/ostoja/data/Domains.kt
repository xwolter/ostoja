package pl.wolter.ostoja.data

/**
 * Domena rejestrowalna, czyli granica, w której wolno podać hasło wpisu.
 *
 * Proste „dwa ostatnie człony” jest groźne: przy wpisie dla mojprojekt.github.io dawałoby github.io,
 * a wtedy dowolna strona *.github.io dostałaby hasło. Dlatego znane sufiksy publiczne (hostingi, na których
 * każdy może założyć subdomenę, oraz polskie domeny regionalne i funkcjonalne) liczą się jak końcówka domeny.
 * Ta sama lista jest w firefox/lib/protocol.js (DomainsTest pilnuje, żeby się nie rozjechały).
 */
object Domains {
    /** Sufiksy, pod którymi subdomeny należą do różnych właścicieli. */
    val PUBLIC = setOf(
        // hostingi i usługi z subdomenami użytkowników
        "github.io", "gitlab.io", "bitbucket.io", "codeberg.page", "vercel.app", "netlify.app", "herokuapp.com",
        "pages.dev", "workers.dev", "r2.dev", "trycloudflare.com", "web.app", "firebaseapp.com", "appspot.com",
        "blogspot.com", "wordpress.com", "azurewebsites.net", "azurestaticapps.net", "cloudapp.net", "cloudfront.net",
        "amazonaws.com", "elasticbeanstalk.com", "onrender.com", "fly.dev", "glitch.me", "repl.co", "replit.app",
        "ngrok.io", "ngrok.app", "ngrok-free.app", "duckdns.org", "no-ip.org", "ddns.net", "myshopify.com",
        "wixsite.com", "squarespace.com", "weebly.com", "tumblr.com", "substack.com", "notion.site", "translate.goog",
        "sharepoint.com", "webflow.io", "carrd.co", "neocities.org", "surge.sh", "readthedocs.io",
        "pythonanywhere.com", "eu.org", "us.org", "uk.net", "gb.net", "eu.com", "us.com", "br.com", "cn.com",
        "de.com", "ru.com", "sa.com", "za.com", "se.net",
        // .pl: domeny funkcjonalne i regionalne NASK
        "com.pl", "net.pl", "org.pl", "info.pl", "biz.pl", "edu.pl", "gov.pl", "mil.pl", "priv.pl", "nom.pl",
        "art.pl", "atm.pl", "auto.pl", "gmina.pl", "gsm.pl", "mail.pl", "media.pl", "miasta.pl", "pc.pl",
        "powiat.pl", "realestate.pl", "rel.pl", "sex.pl", "shop.pl", "sklep.pl", "sos.pl", "tm.pl", "tourism.pl",
        "travel.pl", "turystyka.pl", "agro.pl", "waw.pl", "warszawa.pl", "krakow.pl", "wroc.pl", "wroclaw.pl",
        "poznan.pl", "lodz.pl", "gda.pl", "gdansk.pl", "gdynia.pl", "sopot.pl", "szczecin.pl", "katowice.pl",
        "lublin.pl", "bialystok.pl", "olsztyn.pl", "torun.pl", "bydgoszcz.pl", "rzeszow.pl", "kielce.pl",
        "opole.pl", "gliwice.pl", "zgora.pl", "radom.pl", "czest.pl", "slask.pl", "malopolska.pl", "mazowsze.pl",
        "pomorskie.pl", "wielkopolska.pl", "warmia.pl", "podlasie.pl", "bielsko.pl", "tychy.pl", "elk.pl",
        "nysa.pl", "pila.pl", "lubin.pl",
    )

    /** Krajowe domeny drugiego poziomu: co.uk, com.au, gov.pl, ne.jp… */
    private val CC_SECOND = setOf(
        "com", "co", "org", "net", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil", "nic", "ltd", "plc",
        "sch", "nhs", "gen", "biz", "info", "nom", "int",
    )

    /** Tu każdy poziom subdomeny ma innego właściciela (kubeł.s3.region.amazonaws.com) - liczy się cały adres. */
    val WHOLE_HOST = setOf("amazonaws.com")

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun registrable(host: String): String {
        val h = host.lowercase().trim().trimEnd('.').removePrefix("www.")
        if (h.isEmpty() || IPV4.matches(h) || h.startsWith('[') || '.' !in h) return h
        if (WHOLE_HOST.any { h.endsWith(".$it") }) return h
        val parts = h.split('.').filter { it.isNotEmpty() }
        val suffix = PUBLIC.filter { h == it || h.endsWith(".$it") }.maxByOrNull { it.length }
        val keep = when {
            suffix != null -> suffix.count { it == '.' } + 2
            parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2] in CC_SECOND -> 3
            else -> 2
        }
        return parts.takeLast(minOf(keep, parts.size)).joinToString(".")
    }
}
