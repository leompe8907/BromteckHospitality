package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.panaccess.HomeWidget

/**
 * Ficha de una instalación del hotel (bar, restaurant, gimnasio, pileta...), configurada por
 * Delancer vía Panaccess (OperatorConfig.widgets). Puramente informativa -- sin ninguna acción,
 * solo nombre, horario, piso y una galería de fotos que se navega con las flechas.
 */
@Composable
fun WidgetDetailScreen(widget: HomeWidget) {
    val colors = HospitalityTheme.colors
    var photoIndex by remember(widget) { mutableIntStateOf(0) }
    val gallery = widget.gallery.ifEmpty { listOf(widget.imageUrl) }
    val first = rememberInitialFocus()

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageModel(gallery[photoIndex]), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        BottomScrim()

        if (gallery.size > 1) {
            GalleryArrow(
                "‹", Modifier.align(Alignment.CenterStart).padding(start = 24.dp).focusRequester(first),
            ) { photoIndex = (photoIndex - 1 + gallery.size) % gallery.size }
            GalleryArrow("›", Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) {
                photoIndex = (photoIndex + 1) % gallery.size
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 64.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(widget.title, style = HospitalityTheme.typography.display, color = colors.textPrimary, modifier = Modifier.width(720.dp))
            listOfNotNull(widget.hours, widget.floor?.let { "Piso $it" }).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = HospitalityTheme.typography.body, color = colors.textSecondary)
            }
            widget.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(720.dp))
            }
        }
    }
}

@Composable
private fun GalleryArrow(symbol: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, shape = CircleShape, modifier = modifier.size(56.dp)) {
        Text(symbol, style = HospitalityTheme.typography.title, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}
