package pl.wolter.ostoja.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import pl.wolter.ostoja.app

/** Schowek: oznacza treść jako wrażliwą (One UI nie pokaże jej w podglądzie) i czyści po czasie. */
object Clip {
    private val handler = Handler(Looper.getMainLooper())
    private var token = 0

    fun copy(ctx: Context, label: String, text: String, sensitive: Boolean = true, toast: Boolean = false) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        // Android 13+ sam pokazuje potwierdzenie kopiowania.
        if (toast && Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Skopiowano: $label", Toast.LENGTH_SHORT).show()
        val sec = ctx.app.settings.value.clipClearSec
        if (sec > 0 && sensitive) {
            val my = ++token
            val appCtx = ctx.applicationContext
            handler.postDelayed({
                if (my == token) runCatching { appCtx.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
            }, sec * 1000L)
        }
    }
}

/** Stan blokady aplikacji (biometria / PIN telefonu). */
object AppLock {
    val locked = MutableStateFlow(true)
    private var backgroundAt = 0L
    /** Wyjście do aparatu, galerii, wyboru pliku czy okna biometrii nie powinno blokować sejfu. */
    private var external = false

    fun allowExternal() { external = true }

    fun canAuthenticate(ctx: Context) =
        BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun enabled(ctx: Context) = ctx.app.settings.value.biometricLock && canAuthenticate(ctx)

    fun init(ctx: Context) {
        if (!enabled(ctx)) locked.value = false
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(ctx: Context) {
        if (!enabled(ctx)) { locked.value = false; return }
        val limit = ctx.app.settings.value.autoLockSec * 1000L
        val away = SystemClock.elapsedRealtime() - backgroundAt
        // Krótkie wyjście do systemowego okna (aparat, pliki) nie blokuje; dłuższe - tak.
        val grace = if (external) maxOf(limit, 120_000L) else limit
        if (backgroundAt > 0 && away >= grace) locked.value = true
        backgroundAt = 0
        external = false
    }

    fun lockNow() { locked.value = true }

    fun prompt(
        activity: FragmentActivity,
        title: String = "Odblokuj Ostoję",
        subtitle: String = "Użyj odcisku palca, twarzy lub blokady ekranu",
        onResult: (Boolean) -> Unit,
    ) {
        allowExternal()
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    fun unlock(activity: FragmentActivity) {
        if (!enabled(activity)) { locked.value = false; return }
        prompt(activity) { ok -> if (ok) locked.value = false }
    }
}
