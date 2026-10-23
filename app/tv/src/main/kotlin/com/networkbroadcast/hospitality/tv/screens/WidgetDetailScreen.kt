package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.panaccess.HomeWidget

/**
 * Ficha de una instalación del hotel (bar, restaurant, gimnasio, pileta...), configurada por
 * Delancer vía Panaccess (OperatorConfig.widgets). Puramente informativa -- sin ninguna acción,
 * solo nombre, horario, piso y una galería de fotos que se navega con izquierda/derecha del control.
 */
@Composable
fun WidgetDetailScreen(widget: HomeWidget, text: UiText) {
    val colors = HospitalityTheme.colors
    var photoIndex by remember(widget) { mutableIntStateOf(0) }
    val gallery = widget.gallery.ifEmpty { listOf(widget.imageUrl) }
    val first = rememberInitialFocus()

    Box(
        Modifier.fillMaxSize()
            .focusRequester(first)
            .onKeyEvent { event ->
                if (gallery.size <= 1 || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { photoIndex = (photoIndex - 1 + gallery.size) % gallery.size; true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { photoIndex = (photoIndex + 1) % gallery.size; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AsyncImage(
            model = imageModel(gallery[photoIndex]), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        BottomScrim()

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 64.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Puntos en vez de flechas de scroll (antes FocusCard circulares, ver git log): acá no
            // hay nada para enfocar con el control -- izquierda/derecha del D-pad cambian de foto
            // directamente (onKeyEvent arriba), como el zapping de PlayerScreen.
            if (gallery.size > 1) GalleryDots(count = gallery.size, selected = photoIndex)
            Text(widget.title, style = HospitalityTheme.typography.display, color = colors.textPrimary, modifier = Modifier.width(720.dp))
            listOfNotNull(widget.hours, widget.floor?.let { "${text.floorLabel} $it" }).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = HospitalityTheme.typography.body, color = colors.textSecondary)
            }
            widget.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(720.dp))
            }
        }
    }
}

@Composable
private fun GalleryDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val colors = HospitalityTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (i == selected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.35f)),
            )
        }
    }
}
