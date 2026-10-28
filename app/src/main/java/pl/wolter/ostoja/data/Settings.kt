package pl.wolter.ostoja.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** 0 - systemowy, 1 - jasny, 2 - ciemny */
    val theme: Int = 0,
    val accent: Int = 0,
    val hideOnScroll: Boolean = true,
    val biometricLock: Boolean = true,
    /** Sekundy w tle, po których sejf się blokuje (0 = od razu). */
    val autoLockSec: Int = 30,
    /** Sekundy, po których schowek jest czyszczony (0 = nigdy). */
    val clipClearSec: Int = 30,
    val secureScreen: Boolean = true,
    val hideCodes: Boolean = false,
    val widgetCodes: Boolean = true,
    val lockscreenCode: Boolean = false,
    /** 0 - A-Z, 1 - ostatnio zmienione */
    val vaultSort: Int = 0,
    val onboarded: Boolean = false,
    /** Ikony serwisów (favicony pobierane raz i trzymane w pamięci podręcznej). */
    val icons: Boolean = true,
    /** „123 456” zamiast „123456”. */
    val groupDigits: Boolean = true,
    /** Sprawdzanie haseł w bazie wycieków (k-anonimowość - wysyłane jest 5 znaków skrótu SHA-1). */
    val leakCheck: Boolean = true,
    val lastLeakCheck: Long = 0,
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = _s.asStateFlow()
    val value get() = _s.value

    private fun read() = AppSettings(
        theme = sp.getInt("theme", 0),
        accent = sp.getInt("accent", 0),
        hideOnScroll = sp.getBoolean("hideOnScroll", true),
        biometricLock = sp.getBoolean("biometricLock", true),
        autoLockSec = sp.getInt("autoLockSec", 30),
        clipClearSec = sp.getInt("clipClearSec", 30),
        secureScreen = sp.getBoolean("secureScreen", true),
        hideCodes = sp.getBoolean("hideCodes", false),
        widgetCodes = sp.getBoolean("widgetCodes", true),
        lockscreenCode = sp.getBoolean("lockscreenCode", false),
        vaultSort = sp.getInt("vaultSort", 0),
        onboarded = sp.getBoolean("onboarded", false),
        icons = sp.getBoolean("icons", true),
        groupDigits = sp.getBoolean("groupDigits", true),
        leakCheck = sp.getBoolean("leakCheck", true),
        lastLeakCheck = sp.getLong("lastLeakCheck", 0),
    )

    fun update(fn: (AppSettings) -> AppSettings) {
        val s = fn(_s.value)
        sp.edit()
            .putInt("theme", s.theme)
            .putInt("accent", s.accent)
            .putBoolean("hideOnScroll", s.hideOnScroll)
            .putBoolean("biometricLock", s.biometricLock)
            .putInt("autoLockSec", s.autoLockSec)
            .putInt("clipClearSec", s.clipClearSec)
            .putBoolean("secureScreen", s.secureScreen)
            .putBoolean("hideCodes", s.hideCodes)
            .putBoolean("widgetCodes", s.widgetCodes)
            .putBoolean("lockscreenCode", s.lockscreenCode)
            .putInt("vaultSort", s.vaultSort)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("icons", s.icons)
            .putBoolean("groupDigits", s.groupDigits)
            .putBoolean("leakCheck", s.leakCheck)
            .putLong("lastLeakCheck", s.lastLeakCheck)
            .apply()
        _s.value = s
    }

    companion object {
        /** Kolory akcentu z prototypu. */
        val ACCENTS = listOf(0xFF3E91FF.toInt(), 0xFF2FA57A.toInt(), 0xFF7A6CF0.toInt(), 0xFFE0704A.toInt())
        val ACCENT_NAMES = listOf("Niebieski", "Zielony", "Fioletowy", "Pomarańczowy")
    }
}
