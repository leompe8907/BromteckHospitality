package com.networkbroadcast.hospitality.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografía. Por defecto, las de los decks: Cormorant Garamond (títulos) y Rubik (texto), en
 * res/font/hospitality_*.ttf (licencia OFL, ver licenses/). Una marca cambia sus fuentes poniendo
 * archivos con el mismo nombre en brands/<marca>/res/font/: los recursos de la app pisan a los
 * de esta librería. Ésa es la "fuente por marca" que en PanaccessApp no existe.
 */
val HospitalityDisplayFamily = FontFamily(Font(R.font.hospitality_display, FontWeight.SemiBold))

val HospitalityBodyFamily = FontFamily(
    Font(R.font.hospitality_body_regular, FontWeight.Normal),
    Font(R.font.hospitality_body_medium, FontWeight.Medium),
    Font(R.font.hospitality_body_semibold, FontWeight.SemiBold),
)

@Immutable
data class HospitalityTypography(
    val display: TextStyle,     // saludo grande de la home
    val title: TextStyle,       // títulos de pantalla
    val section: TextStyle,     // títulos de fila ("Entretenimiento", "Acciones rápidas")
    val body: TextStyle,
    val label: TextStyle,       // texto de tarjetas y chips
) {
    companion object {
        fun from(displayFamily: FontFamily, bodyFamily: FontFamily) = HospitalityTypography(
            // Cormorant dibuja chico para su tamaño nominal: por eso títulos más grandes que con una sans.
            display = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
            title = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
            section = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            body = TextStyle(fontFamily = bodyFamily, fontSize = 18.sp, lineHeight = 27.sp),
            label = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp),
        )
    }
}

val LocalHospitalityColors = staticCompositionLocalOf { HospitalityColors.Resort }
val LocalHospitalityTypography = staticCompositionLocalOf {
    HospitalityTypography.from(HospitalityDisplayFamily, HospitalityBodyFamily)
}

object HospitalityTheme {
    val colors: HospitalityColors
        @Composable get() = LocalHospitalityColors.current
    val typography: HospitalityTypography
        @Composable get() = LocalHospitalityTypography.current
}

@Composable
fun HospitalityTheme(
    colors: HospitalityColors = HospitalityColors.Resort,
    displayFamily: FontFamily = HospitalityDisplayFamily,
    bodyFamily: FontFamily = HospitalityBodyFamily,
    content: @Composable () -> Unit,
) {
    val typography = HospitalityTypography.from(displayFamily, bodyFamily)
    // Material3 queda alineado con los tokens para los componentes que todavía se usen de ahí.
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, background = colors.background, surface = colors.surface,
            onBackground = colors.textPrimary, onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, background = colors.background, surface = colors.surface,
            onBackground = colors.textPrimary, onSurface = colors.textPrimary,
        )
    }
    CompositionLocalProvider(
        LocalHospitalityColors provides colors,
        LocalHospitalityTypography provides typography,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
