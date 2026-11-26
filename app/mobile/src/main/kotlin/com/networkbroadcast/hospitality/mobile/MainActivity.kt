package com.networkbroadcast.hospitality.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.themeColors
import com.networkbroadcast.hospitality.services.OrderLocation
import com.networkbroadcast.hospitality.services.parseSpotLink
import java.util.Locale

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
        app.restoreSession()
        setContent {
            val session by app.session.collectAsState()
            // Idioma de la cuenta de la TV (X_DEFAULT_LANGUAGE de Panaccess; si no hay, el de la marca). Sin
            // sesión todavía no hay config del operador: la pantalla neutra usa el de la marca. El idioma
            // que elija el huésped en la TV llegará por el QR: se pasa como guestChoice.
            val language = accountLanguage(
                operatorDefault = if (session == MobileSession.Ready) app.panaccess.operatorConfig?.defaultLanguage else null,
                brandDefault = app.brand.defaultLanguage,
                brandLanguages = app.brand.languages,
                guestChoice = null,
            )
            val locale = remember(language) { Locale.forLanguageTag(language) }
            // Sin sesión todavía no se sabe de qué hotel es: pantalla neutra con los colores de la marca
            // genérica. Con sesión, los colores del operador (Panaccess) pisan los de la marca.
            val colors = app.brand.themeColors().let {
                if (session == MobileSession.Ready) it.withOperator(app.panaccess.operatorConfig) else it
            }
            // Una sola familia, la de texto de la TV, también para los títulos (ver Typography.kt).
            HospitalityTheme(colors = colors, displayFamily = MobileFontFamily, bodyFamily = MobileFontFamily) {
                MobileFonts {
                    val playerColor = HospitalityColors.parseHex(app.panaccess.operatorConfig?.playerControlColorHex).takeIf { session == MobileSession.Ready } ?: colors.accent
                    CompositionLocalProvider(
                        LocalAppLocale provides locale,
                        LocalShapeFactor provides shapeFactorFor(app.brand.direction),
                        LocalPlayerColor provides playerColor,
                    ) {
                        if (session == MobileSession.Ready) {
                            MobileApp(
                                app = app,
                                language = language,
                                incomingCode = incomingCode.value,
                                onCodeConsumed = { incomingCode.value = null },
                                incomingSpot = incomingSpot.value,
                                onSpotConsumed = { incomingSpot.value = null },
                            )
                        } else {
                            NeutralScreen(
                                text = NeutralText.of(language),
                                brand = app.brand,
                                // Todavía no hay panel conectado: sin él el logo solo llega con la sesión (ver NeutralScreen).
                                logoUrl = null,
                                session = session,
                                debugLogin = if (app.isDebuggable) app::debugLogin else null,
                            )
                        }
                    }
                }
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
