package pl.wolter.ostoja.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Wpis w sejfie haseł. */
@Serializable
data class Login(
    val id: String = newId(),
    val title: String,
    /** login | card | note | wifi */
    val type: String = LOGIN,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    /** Powiązane konto 2FA (OtpAccount.id). */
    val otpId: String? = null,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val history: List<OldPassword> = emptyList(),
    /** Hasło znalezione w publicznej bazie wycieków (Have I Been Pwned, k-anonimowość). */
    val breached: Boolean = false,
    val breachChecked: Long = 0,
    /** Ostatnia zmiana dowolnego pola - do synchronizacji z komputerem (0 w starszych sejfach). */
    val modified: Long = 0,
) {
    val isLogin get() = type == LOGIN
    /** Czas, według którego wygrywa nowsza wersja przy scalaniu. */
    val stamp get() = maxOf(modified, updated, created)

    companion object {
        const val LOGIN = "login"
        const val CARD = "card"
        const val NOTE = "note"
        const val WIFI = "wifi"
    }
}

@Serializable
data class OldPassword(val password: String, val changed: Long)

/** Konto 2FA (TOTP lub HOTP). Sekret zapisany w base32 bez spacji, wielkimi literami. */
@Serializable
data class OtpAccount(
    val id: String = newId(),
    val issuer: String,
    val label: String = "",
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val type: String = TOTP,
    val counter: Long = 0,
    val favorite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = 0,
) {
    val isHotp get() = type == HOTP
    val stamp get() = maxOf(modified, created)
    val title get() = issuer.ifBlank { label }

    companion object {
        const val TOTP = "totp"
        const val HOTP = "hotp"
    }
}

@Serializable
data class VaultData(
    val version: Int = 1,
    val logins: List<Login> = emptyList(),
    val otps: List<OtpAccount> = emptyList(),
    /** Nagrobki usuniętych wpisów i kont (id → czas usunięcia), żeby usunięcie dotarło na drugie urządzenie. */
    val deleted: Map<String, Long> = emptyMap(),
) {
    fun login(id: String) = logins.firstOrNull { it.id == id }
    fun otp(id: String?) = id?.let { i -> otps.firstOrNull { it.id == i } }
}
