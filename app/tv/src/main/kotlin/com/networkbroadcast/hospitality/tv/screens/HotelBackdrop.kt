package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import kotlinx.coroutines.delay

/**
 * Fondo de la home, la selección de idioma y la bienvenida: las fotos de la marca
 * (`brand.json` → "backgrounds") alternándose con un fundido lento, como un salvapantallas de
 * hotel (sin el acercamiento/zoom que tenía antes — se sacó a pedido). Encima, un velo con el
 * color del tema para que el texto se lea. Sin fotos, no dibuja nada (queda el fondo liso del tema).
 */
@Composable
fun HotelBackdrop(images: List<String>, modifier: Modifier = Modifier, placeholderColorHex: String? = null) {
    if (images.isEmpty()) return
    val colors = HospitalityTheme.colors
    var index by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        while (images.size > 1) {
            delay(9_000)
            index = (index + 1) % images.size
        }
    }
    // Mientras la imagen carga (o si la URL falla, p. ej. un fondo configurado mal desde
    // Panaccess), un color fijo en vez de dejar la pantalla vacía — mismo helper que usa
    // VodScreen en EntertainmentScreens.kt para fotos de canal/VOD rotas.
    val placeholder = remember(colors, placeholderColorHex) { colors.placeholderPainter(placeholderColorHex) }
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = images[index], animationSpec = tween(1_800), label = "photo") { url ->
            AsyncImage(
                model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop,
                placeholder = placeholder, error = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Velo: casi opaco abajo (donde están las tarjetas) y más liviano arriba, donde se ve la
        // foto. Negro neutro, no el color de fondo del tema -- con un fondo real (sobre todo uno
        // con verdes, como el de Delancer) el teal/verde del tema sumado al velo se veía como un
        // filtro de color encima de la foto, no un simple sombreado. Mismo criterio ya usado en
        // BottomScrim (Common.kt) para el mismo problema con los pósters de VOD.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.45f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.85f),
                ),
            ),
        )
    }
}
