package pl.wolter.ostoja

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.AppRoot

class MainActivity : FragmentActivity() {
    /** Intencja do obsłużenia po odblokowaniu (skrót, widżet, link otpauth://). */
    val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            AppLock.init(this)
            pendingIntent.value = intent
        }
        setContent { AppRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.onBackground()
    }

    fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    fun applySecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        const val ACTION_SCAN = "pl.wolter.ostoja.SCAN"
        const val ACTION_NEW = "pl.wolter.ostoja.NEW"
        const val ACTION_CODES = "pl.wolter.ostoja.CODES"
        const val ACTION_SEARCH = "pl.wolter.ostoja.SEARCH"
        const val ACTION_GEN = "pl.wolter.ostoja.GEN"
        const val ACTION_ISSUES = "pl.wolter.ostoja.ISSUES"
        const val EXTRA_OTP = "otp"
        const val EXTRA_LOGIN = "login"
    }
}
