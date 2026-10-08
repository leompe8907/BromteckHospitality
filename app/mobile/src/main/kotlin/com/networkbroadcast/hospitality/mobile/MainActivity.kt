package com.networkbroadcast.hospitality.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.themeColors
import com.networkbroadcast.hospitality.services.OrderLocation
import com.networkbroadcast.hospitality.services.parseSpotLink

class MainActivity : ComponentActivity() {

    /** Código que llega del QR de la TV (hospitality://pair?code=…). */
    private val incomingCode = mutableStateOf<String?>(null)

    /** Ubicación que llega del QR de un camastro o mesa (hospitality://spot?zone=pool&spot=12). */
    private val incomingSpot = mutableStateOf<OrderLocation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingCode.value = codeFrom(intent)
        incomingSpot.value = spotFrom(intent)
        val app = application as MobileApplication
        setContent {
            HospitalityTheme(colors = app.brand.themeColors()) {
                MobileApp(
                    app = app,
                    incomingCode = incomingCode.value,
                    onCodeConsumed = { incomingCode.value = null },
                    incomingSpot = incomingSpot.value,
                    onSpotConsumed = { incomingSpot.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        codeFrom(intent)?.let { incomingCode.value = it }
        spotFrom(intent)?.let { incomingSpot.value = it }
    }

    private fun codeFrom(intent: Intent?): String? =
        intent?.data?.takeIf { it.scheme == "hospitality" && it.host == "pair" }?.getQueryParameter("code")

    private fun spotFrom(intent: Intent?): OrderLocation? = intent?.dataString?.let(::parseSpotLink)
}
