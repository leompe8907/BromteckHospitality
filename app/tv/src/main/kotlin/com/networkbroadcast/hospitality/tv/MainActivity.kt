package com.networkbroadcast.hospitality.tv

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.themeColors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as HospitalityApplication
        // Sólo en debug: `am start ... --ez showLogin true` muestra el login aunque la TV ya tenga la
        // sesión guardada (para revisar el diseño sin borrarla). En release no existe.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val showLogin = debuggable && intent.getBooleanExtra("showLogin", false)
        // Sólo en debug: `--ez decoderReuse false` / `--ez lowestProfile false` pisan los dos
        // parámetros de cuenta del player (X_PLAYER_DECODER_REUSE_ENABLED,
        // X_PLAYER_HLS_START_LOWEST_PROFILE) sin tener que tocar el panel de Panaccess. Sirve para
        // aislar en la TV cuál de los dos causa un problema de imagen. En release no existen.
        val overrides = PlayerOverrides(
            decoderReuse = debuggable.takeIf { it }?.let { booleanExtraOrNull("decoderReuse") },
            startWithLowestProfile = debuggable.takeIf { it }?.let { booleanExtraOrNull("lowestProfile") },
        )
        setContent {
            HospitalityTheme(colors = app.brand.themeColors()) {
                HospitalityApp(app, showLogin, overrides)
            }
        }
    }

    /** null = el intent no lo trae (vale lo que diga la cuenta). */
    private fun booleanExtraOrNull(name: String): Boolean? =
        if (intent.hasExtra(name)) intent.getBooleanExtra(name, true) else null
}

/** Pisadas de debug para los parámetros del player; null = vale lo que diga la cuenta. */
data class PlayerOverrides(
    val decoderReuse: Boolean? = null,
    val startWithLowestProfile: Boolean? = null,
)
