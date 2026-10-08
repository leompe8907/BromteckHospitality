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
        setContent {
            HospitalityTheme(colors = app.brand.themeColors()) {
                HospitalityApp(app, showLogin)
            }
        }
    }
}
