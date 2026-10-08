package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import kotlinx.coroutines.delay

/**
 * Fondo de la home, la selección de idioma y la bienvenida: las fotos de la marca
 * (`brand.json` → "backgrounds") alternándose con un fundido lento y un acercamiento muy suave, como
 * un salvapantallas de hotel. Encima, un velo con el color del tema para que el texto se lea.
 * Sin fotos, no dibuja nada (queda el fondo liso del tema).
 */
@Composable
fun HotelBackdrop(images: List<String>, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    val colors = HospitalityTheme.colors
    var index by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        while (images.size > 1) {
            delay(9_000)
            index = (index + 1) % images.size
        }
    }
    val zoom by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = images[index], animationSpec = tween(1_800), label = "photo") { url ->
            AsyncImage(
                model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(zoom),
            )
        }
        // Velo: casi opaco abajo (donde están las tarjetas) y más liviano arriba, donde se ve la foto.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0.35f),
                    0.45f to colors.background.copy(alpha = 0.70f),
                    1f to colors.background.copy(alpha = 0.94f),
                ),
            ),
        )
    }
}
