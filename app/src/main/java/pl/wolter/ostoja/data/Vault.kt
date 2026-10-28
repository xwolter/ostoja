package pl.wolter.ostoja.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Jedyne źródło prawdy dla haseł i kont 2FA. Plik `vault.bin` jest w całości zaszyfrowany
 * (KeystoreCipher), zapis jest atomowy (tmp + rename).
 */
class Vault(private val ctx: Context, private val onChange: (local: Boolean) -> Unit) {
    private val file = File(ctx.filesDir, "vault.bin")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<VaultData> = _data.asStateFlow()
    val value get() = _data.value

    private fun load(): VaultData {
        if (!file.exists()) return VaultData()
        return try {
            json.decodeFromString(VaultData.serializer(), String(KeystoreCipher.decrypt(file.readBytes())))
        } catch (e: Exception) {
            // Nie nadpisujemy nieczytelnego pliku - zostaje obok do ewentualnego odzyskania.
            Log.e("Ostoja", "Nie udało się odczytać sejfu", e)
            file.renameTo(File(ctx.filesDir, "vault.broken.${System.currentTimeMillis()}"))
            VaultData()
        }
    }

    /** Zmiana lokalna: zmienione wpisy dostają znacznik czasu, usunięte - nagrobek (dla synchronizacji). */
    @Synchronized
    fun update(fn: (VaultData) -> VaultData) {
        val cur = _data.value
        val next = fn(cur)
        if (next == cur) return
        set(VaultSync.stamp(cur, next), local = true)
    }

    /** Stan scalony z komputerem. Zmiany zrobione w trakcie zapytania nie giną - scalamy z bieżącym. */
    @Synchronized
    fun applyRemote(remote: VaultData) {
        val merged = VaultSync.merge(_data.value, remote)
        if (merged == _data.value) return
        set(merged, local = false)
    }

    private fun set(d: VaultData, local: Boolean) {
        _data.value = d
        persist(d)
        onChange(local)
    }

    private fun persist(d: VaultData) {
        val tmp = File(ctx.filesDir, "vault.tmp")
        tmp.writeBytes(KeystoreCipher.encrypt(json.encodeToString(VaultData.serializer(), d).toByteArray()))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    // --- operacje ---

    fun saveLogin(l: Login) = update { d -> VaultOps.saveLogin(d, l) }

    fun deleteLogin(id: String) = update { d -> d.copy(logins = d.logins.filterNot { it.id == id }) }

    fun toggleFavorite(id: String) = update { d ->
        d.copy(logins = d.logins.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    /** Dodaje konta 2FA, pomijając duplikaty (ten sam sekret). Zwraca nowe. */
    fun addOtps(list: List<OtpAccount>, linkTo: String? = null): List<OtpAccount> {
        var added = emptyList<OtpAccount>()
        update { d -> VaultOps.addOtps(d, list, linkTo).also { added = it.second }.first }
        return added
    }

    fun saveOtp(a: OtpAccount) = update { d ->
        d.copy(otps = if (d.otp(a.id) == null) d.otps + a else d.otps.map { if (it.id == a.id) a else it })
    }

    fun deleteOtp(id: String) = update { d -> VaultOps.deleteOtp(d, id) }

    fun toggleOtpFavorite(id: String) = update { d ->
        d.copy(otps = d.otps.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    }

    fun nextHotp(id: String) = update { d ->
        d.copy(otps = d.otps.map { if (it.id == id) it.copy(counter = it.counter + 1) else it })
    }

    fun replaceAll(d: VaultData) = update { d }

    /** Scala kopię z bieżącym sejfem: wpisy o tym samym id zastępuje, nowe dodaje. */
    fun merge(other: VaultData) = update { d ->
        val logins = (d.logins.associateBy { it.id } + other.logins.associateBy { it.id }).values.toList()
        val secrets = d.otps.associateBy { it.secret }
        val otps = d.otps + other.otps.filter { it.secret !in secrets && d.otp(it.id) == null }
        d.copy(logins = logins, otps = otps)
    }

    fun exportJson(): ByteArray = json.encodeToString(VaultData.serializer(), value).toByteArray()

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(bytes: ByteArray): VaultData = json.decodeFromString(VaultData.serializer(), String(bytes))
    }
}
