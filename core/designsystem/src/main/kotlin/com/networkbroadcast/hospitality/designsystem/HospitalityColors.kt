package com.networkbroadcast.hospitality.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter

/**
 * Las tres direcciones visuales del canvas "Hospitality Android TV". Mismo contenido y layout;
 * sólo cambian los tokens. Cada marca elige una en su brand.json.
 */
enum class VisualDirection { RESORT, CINEMATIC, MINIMAL }

/** Tokens de color. Los nombres describen el uso, no el tono, para que las 3 direcciones encajen. */
@Immutable
data class HospitalityColors(
    val background: Color,      // fondo de pantalla
    val surface: Color,         // tarjetas en reposo
    val surfaceBorder: Color,   // borde de tarjetas en reposo
    val rail: Color,            // barra lateral de navegación
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,          // íconos activos, cifras, acentos de marca
    val focus: Color,           // foco de campos de texto (TextField); FocusCard usa textPrimary
    val isDark: Boolean,
) {
    companion object {
        /** Resort cálido: verde profundo, terracota y turquesa (paleta de los decks). */
        val Resort = HospitalityColors(
            background = Color(0xFF0E2E32),
            surface = Color(0xFF163F42),
            surfaceBorder = Color(0xFF2A5457),
            rail = Color(0xFF0A2427),
            textPrimary = Color(0xFFF2F6EC),
            textSecondary = Color(0xFFB9D4CE),
            textMuted = Color(0xFF628179),
            accent = Color(0xFFF08055),
            focus = Color(0xFFF5C24C),
            isDark = true,
        )

        /** Cinematográfico: negro casi puro y acento dorado, lenguaje de streaming. */
        val Cinematic = HospitalityColors(
            background = Color(0xFF0B0B0C),
            surface = Color(0xFF17171A),
            surfaceBorder = Color(0xFF2A2A2E),
            rail = Color(0xFF050506),
            textPrimary = Color(0xFFF5F2EA),
            textSecondary = Color(0xFFB8B3A7),
            textMuted = Color(0xFF6E6A62),
            accent = Color(0xFFD4AF6A),
            focus = Color(0xFFE8C47E),
            isDark = true,
        )

        /** Minimalista: fondo casi blanco y un solo acento sutil. */
        val Minimal = HospitalityColors(
            background = Color(0xFFF7F7F5),
            surface = Color(0xFFFFFFFF),
            surfaceBorder = Color(0xFFE3E3DF),
            rail = Color(0xFFEDEDEA),
            textPrimary = Color(0xFF1A1C1E),
            textSecondary = Color(0xFF4A4F55),
            textMuted = Color(0xFF8A9096),
            accent = Color(0xFF2F6FDE),
            focus = Color(0xFF2F6FDE),
            isDark = false,
        )

        fun of(direction: VisualDirection): HospitalityColors = when (direction) {
            VisualDirection.RESORT -> Resort
            VisualDirection.CINEMATIC -> Cinematic
            VisualDirection.MINIMAL -> Minimal
        }

        /** "#163F42" o "#FF163F42" -> Color. null si no es un hex válido (ej. el operador no lo configuró). */
        fun parseHex(hex: String?): Color? {
            val clean = hex?.removePrefix("#") ?: return null
            return runCatching {
                when (clean.length) {
                    6 -> Color(clean.toLong(16) or 0xFF000000)
                    8 -> Color(clean.toLong(16))
                    else -> null
                }
            }.getOrNull()
        }

        /**
         * Blanco o negro, lo que más contraste tenga contra ese color de fondo -- para que un
         * color de card configurable (X_APP_CARD_COLOR) nunca deje el texto invisible encima sin
         * necesidad de un parámetro de color de texto aparte (fórmula de luminancia percibida).
         */
        fun readableTextColor(background: Color): Color {
            val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
            return if (luminance > 0.5f) Color.Black else Color.White
        }
    }

    /** Variante con el color de card que mandó el operador (o esta misma instancia, si no hay). */
    fun withCustomCardColor(hex: String?): HospitalityColors {
        val custom = parseHex(hex) ?: return this
        val text = readableTextColor(custom)
        return copy(surface = custom, surfaceBorder = custom, textPrimary = text, textSecondary = text.copy(alpha = 0.75f))
    }

    /**
     * Gris neutro para fotos que están cargando o fallaron (fondo del hotel, pósters de VOD):
     * mismo criterio en los dos lugares que lo usan, acá para no duplicarlo. El operador puede
     * pisarlo con X_APP_PLACEHOLDER_COLOR (ver OperatorConfig); sin eso, el gris fijo de siempre
     * según el tema sea claro u oscuro.
     */
    fun placeholderPainter(customHex: String? = null): ColorPainter =
        ColorPainter(parseHex(customHex) ?: if (isDark) Color(0xFF2A2A2A) else Color(0xFFE2E2DE))
}
