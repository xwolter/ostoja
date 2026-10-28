package pl.wolter.ostoja.data

/**
 * Operacje na sejfie wspólne dla telefonu i komputera. Czyste funkcje: dostają stan i zwracają nowy,
 * zapis i znaczniki czasu zostają po stronie wywołującego (Vault na telefonie, Store na komputerze).
 */
object VaultOps {

    /** Zapis wpisu: przy zmianie hasła stare trafia do historii (5 ostatnich), a flaga wycieku jest kasowana. */
    fun saveLogin(d: VaultData, l: Login, now: Long = System.currentTimeMillis()): VaultData {
        val old = d.login(l.id)
        val stamped = if (old == null) l.copy(updated = now)
        else {
            val pwChanged = old.password != l.password && old.password.isNotEmpty()
            l.copy(
                updated = now,
                history = if (pwChanged) (listOf(OldPassword(old.password, old.updated)) + old.history).take(5) else l.history,
                breached = if (old.password != l.password) false else l.breached,
                breachChecked = if (old.password != l.password) 0 else l.breachChecked,
            )
        }
        return d.copy(logins = if (old == null) d.logins + stamped else d.logins.map { if (it.id == l.id) stamped else it })
    }

    /** Dodaje konta 2FA, pomijając duplikaty (ten sam sekret), i łączy je z pasującymi wpisami. */
    fun addOtps(d: VaultData, list: List<OtpAccount>, linkTo: String? = null): Pair<VaultData, List<OtpAccount>> {
        val known = d.otps.map { it.secret }.toSet()
        val added = list.filter { it.secret !in known }.distinctBy { it.secret }
        var logins = d.logins
        for (a in added) {
            val target = linkTo?.let { d.login(it) } ?: autoMatch(d, a)
            if (target != null && target.otpId == null) logins = logins.map { if (it.id == target.id) it.copy(otpId = a.id) else it }
        }
        return d.copy(otps = d.otps + added, logins = logins) to added
    }

    private fun autoMatch(d: VaultData, a: OtpAccount): Login? {
        val key = a.issuer.lowercase().filter { it.isLetterOrDigit() }
        if (key.length < 3) return null
        return d.logins.firstOrNull { l ->
            l.otpId == null && (l.title.lowercase().filter { it.isLetterOrDigit() } == key || hostOf(l.url).contains(key))
        }
    }

    /**
     * Konto 2FA dla wpisu. Gdy nie są połączone (np. hasło dodane z przeglądarki, a kod wcześniej z telefonu),
     * szukamy kodu tego samego serwisu po nazwie i domenie: „Github” / github.com ↔ „GitHub”.
     */
    fun otpFor(d: VaultData, l: Login): OtpAccount? {
        d.otp(l.otpId)?.let { return it }
        if (!l.isLogin) return null
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val keys = listOf(key(l.title), key(registrableDomain(hostOf(l.url)).substringBefore('.'))).filter { it.length >= 3 }.toSet()
        if (keys.isEmpty()) return null
        val matching = d.otps.filter { a -> key(a.issuer).let { k -> k.length >= 3 && keys.any { it == k || it.startsWith(k) || k.startsWith(it) } } }
        val linked = d.logins.mapNotNull { it.otpId }.toSet()
        return matching.firstOrNull { it.id !in linked } ?: matching.firstOrNull()
    }

    /** Trwale łączy wpis z pasującym kodem 2FA (synchronizuje się też na telefon). */
    fun linkOtp(d: VaultData, loginId: String): VaultData {
        val l = d.login(loginId) ?: return d
        if (l.otpId != null) return d
        val a = otpFor(d, l) ?: return d
        return d.copy(logins = d.logins.map { if (it.id == loginId) it.copy(otpId = a.id) else it })
    }

    fun deleteOtp(d: VaultData, id: String) = d.copy(
        otps = d.otps.filterNot { it.id == id },
        logins = d.logins.map { if (it.otpId == id) it.copy(otpId = null) else it },
    )
}

/**
 * Synchronizacja bez serwera: każde urządzenie trzyma pełny sejf, a przy spotkaniu oba stany są scalane.
 * Dla każdego wpisu wygrywa nowsza wersja (`stamp`), usunięcia przenoszą nagrobki. Scalanie jest
 * przemienne, więc telefon i komputer dochodzą do identycznego wyniku niezależnie od kolejności.
 */
object VaultSync {
    private const val TOMBSTONE_TTL = 180L * 24 * 3600 * 1000

    /** Oznacza czasem `now` wpisy zmienione lokalnie i zapisuje nagrobki usuniętych. */
    fun stamp(prev: VaultData, next: VaultData, now: Long = System.currentTimeMillis()): VaultData {
        val pl = prev.logins.associateBy { it.id }
        val po = prev.otps.associateBy { it.id }
        val logins = next.logins.map { l -> if (pl[l.id] == l) l else l.copy(modified = maxOf(now, l.stamp + 1)) }
        val otps = next.otps.map { a -> if (po[a.id] == a) a else a.copy(modified = maxOf(now, a.stamp + 1)) }
        val alive = HashSet<String>().apply { logins.forEach { add(it.id) }; otps.forEach { add(it.id) } }
        val deleted = HashMap(next.deleted)
        (pl.keys + po.keys).filter { it !in alive }.forEach { deleted[it] = now }
        return next.copy(logins = logins, otps = otps, deleted = deleted.filter { (id, t) -> id !in alive && now - t < TOMBSTONE_TTL })
    }

    fun merge(a: VaultData, b: VaultData): VaultData {
        val deleted = HashMap(a.deleted)
        b.deleted.forEach { (k, v) -> deleted.merge(k, v, ::maxOf) }

        fun <T : Any> union(x: List<T>, y: List<T>, id: (T) -> String, t: (T) -> Long): List<T> {
            val out = LinkedHashMap<String, T>()
            x.forEach { out[id(it)] = it }
            y.forEach { v ->
                val cur = out[id(v)]
                // Remis czasu rozstrzyga porównanie treści - deterministycznie po obu stronach.
                if (cur == null || t(v) > t(cur) || (t(v) == t(cur) && v.toString() > cur.toString())) out[id(v)] = v
            }
            return out.values.filter { (deleted[id(it)] ?: Long.MIN_VALUE) < t(it) }
        }

        var logins = union(a.logins, b.logins, { it.id }, { it.stamp })
        var otps = union(a.otps, b.otps, { it.id }, { it.stamp })

        // To samo konto 2FA dodane osobno na obu urządzeniach: zostaje starsze, wpisy przepinamy.
        val remap = HashMap<String, String>()
        otps.groupBy { it.secret }.values.filter { it.size > 1 }.forEach { same ->
            val keep = same.minWith(compareBy<OtpAccount> { it.created }.thenBy { it.id })
            same.filter { it !== keep }.forEach { remap[it.id] = keep.id; deleted[it.id] = maxOf(deleted[it.id] ?: 0, it.stamp) }
        }
        if (remap.isNotEmpty()) {
            otps = otps.filter { it.id !in remap }
            logins = logins.map { l -> l.otpId?.let { remap[it] }?.let { l.copy(otpId = it) } ?: l }
        }
        val otpIds = otps.map { it.id }.toSet()
        logins = logins.map { if (it.otpId != null && it.otpId !in otpIds) it.copy(otpId = null) else it }

        val alive = (logins.map { it.id } + otpIds).toSet()
        return VaultData(
            version = maxOf(a.version, b.version),
            logins = logins,
            otps = otps,
            deleted = deleted.filterKeys { it !in alive },
        )
    }
}

/** „https://www.konto.google.com/login” → „konto.google.com” (bez loginu w adresie: „https://allegro.pl@zla.com” → „zla.com”). */
fun hostOf(url: String): String {
    val u = url.trim().lowercase()
    if (u.isEmpty()) return ""
    val authority = u.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfterLast('@').substringBefore(':').trimEnd('.').removePrefix("www.")
}

/** Domena rejestrowalna z uwzględnieniem sufiksów publicznych - patrz [Domains]. */
fun registrableDomain(host: String): String = Domains.registrable(host)
