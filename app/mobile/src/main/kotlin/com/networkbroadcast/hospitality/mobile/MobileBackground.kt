package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/**
 * Parámetro de Panaccess con el fondo de la app del celular.
 *
 * NOMBRE PROPUESTO: todavía NO existe en Panaccess; hay que crearlo. Es aparte del fondo de la TV
 * (`X_APP_LOGO_BACKGROUND`) porque una foto pensada para una pantalla horizontal queda mal en un
 * celular vertical. El valor es la URL de una foto o un color en hex (`#0E2E32`).
 */
const val MOBILE_BACKGROUND_PARAM = "X_APP_MOBILE_BACKGROUND"

/** Fondo de la app del celular: una foto o un color liso. */
sealed interface MobileBackground {
    data class Photo(val url: String) : MobileBackground
    data class Solid(val color: Color) : MobileBackground
}

/**
 * Lee [MOBILE_BACKGROUND_PARAM]. Un valor que empieza con `#` es un color (si el hex no sirve, no hay
 * fondo propio); cualquier otro texto es la URL de una foto. null si no hay valor: queda el fondo liso del tema.
 */
fun parseMobileBackground(raw: String?): MobileBackground? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return if (value.startsWith("#")) HospitalityColors.parseHex(value)?.let { MobileBackground.Solid(it) }
    else MobileBackground.Photo(value)
}

/**
 * Pinta el fondo de toda la app. Sin [background] (o con un color), un fondo liso. Con una foto, la foto
 * a pantalla completa con una capa lisa del color del tema encima para que el texto se lea (sin degradado).
 */
@Composable
fun MobileBackdrop(background: MobileBackground?) {
    val colors = HospitalityTheme.colors
    when (background) {
        is MobileBackground.Photo -> Box(Modifier.fillMaxSize().background(colors.background)) {
            AsyncImage(model = imageModel(background.url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = PHOTO_VEIL)))
        }
        is MobileBackground.Solid -> Box(Modifier.fillMaxSize().background(background.color))
        null -> Box(Modifier.fillMaxSize().background(colors.background))
    }
}

/** Qué tan opaca es la capa del tema sobre la foto de fondo: la foto se ve, pero los textos se leen. */
private const val PHOTO_VEIL = 0.72f
