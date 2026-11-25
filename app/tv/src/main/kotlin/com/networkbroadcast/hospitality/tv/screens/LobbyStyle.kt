package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.networkbroadcast.hospitality.brand.LobbyText
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors

/*
 * Lo que el operador de Panaccess personaliza (X_APP_*) y que la dirección "Lobby" respeta, igual
 * que la home de siempre. Se arma una vez en HospitalityApp y llega a todas las pantallas Lobby por
 * [LocalLobbyStyle], en vez de pasarlo pantalla por pantalla.
 *
 * Los velos sobre fotos son negro neutro, nunca un color de la paleta: un velo verdoso/turquesa es
 * justo el "filtro verde" que ya se sacó de la ficha de VOD, del fondo del hotel y del reproductor.
 */

@Immutable
data class LobbyStyle(
    /** X_APP_CARD_COLOR: color de las piezas y paneles. null = vidrio oscuro neutro. */
    val cardColorHex: String? = null,
    /** X_APP_TEXT_COLOR: color de letra. null = el del tema, o el que contrasta con la tarjeta. */
    val textColorHex: String? = null,
    /** X_APP_CONFIRM_COLOR: color de lo destacado y de la opción elegida (bienvenida, listas, reproductor). */
    val confirmColorHex: String? = null,
    /** X_APP_PLACEHOLDER: imagen mientras cargan los pósters. */
    val placeholderImageUrl: String? = null,
)

val LocalLobbyStyle = staticCompositionLocalOf { LobbyStyle() }
val LocalLobbyText = staticCompositionLocalOf { LobbyText.of("es") }
/** Vuelve a la pantalla anterior: lo que hace el botón "Atrás" de las pantallas Lobby. */
val LocalLobbyBack = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Pone el estilo del operador y los textos del idioma elegido para todo lo que haya adentro. Con
 * [enabled] en false (marcas que no son Lobby) no cambia nada.
 */
@Composable
fun LobbyScope(style: LobbyStyle, language: String, enabled: Boolean = true, onBack: () -> Unit = {}, content: @Composable () -> Unit) {
    if (!enabled) return content()
    CompositionLocalProvider(
        LocalLobbyStyle provides style,
        LocalLobbyText provides LobbyText.of(language),
        LocalLobbyBack provides onBack,
        LocalHospitalityColors provides HospitalityTheme.colors.withCustomTextColor(style.textColorHex),
        content = content,
    )
}

/** Piezas translúcidas sobre la foto: el color de card del operador, o vidrio negro neutro. */
@Composable
fun lobbyGlass(): HospitalityColors {
    val style = LocalLobbyStyle.current
    val base = HospitalityTheme.colors
    val card = HospitalityColors.parseHex(style.cardColorHex)
        ?: return base.copy(surface = Color(0x9E000000), surfaceBorder = Color(0x24FFFFFF))
    return base.withCustomCardColor(card)
        .copy(surface = card.copy(alpha = 0.88f), surfaceBorder = Color.Transparent)
        .withCustomTextColor(style.textColorHex)
}

/**
 * Color de lo destacado (opción elegida, "Ahora", progreso del reproductor): el mismo criterio que el
 * reproductor de siempre. X_APP_CONFIRM_COLOR si el operador lo manda; si no, el acento de la marca
 * (en mirador, el dorado de brand.json: es de donde sale ese amarillo).
 * Los colores del tema que pisan las filas y los chips no incluyen el acento, así que se puede llamar
 * desde adentro de ellos.
 */
@Composable
fun lobbyConfirmColor(): Color =
    HospitalityColors.parseHex(LocalLobbyStyle.current.confirmColorHex) ?: HospitalityTheme.colors.accent
