package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.hotel.HotelData
import com.networkbroadcast.hospitality.hotel.HotelRepository
import com.networkbroadcast.hospitality.hotel.resolve
import com.networkbroadcast.hospitality.brand.UiText
import kotlinx.coroutines.launch

private sealed interface HotelState {
    data object Loading : HotelState
    data object Unavailable : HotelState
    data class Ready(val data: HotelData) : HotelState
}

/**
 * "Acerca del hotel" genérico: el texto sale de hotel_data.json de la marca, no de un if/else
 * por nombre de marca como en HotelInfoActionRow.
 */
@Composable
fun AboutHotelScreen(repository: HotelRepository?, language: String, fallback: String, text: UiText) {
    var state by remember { mutableStateOf<HotelState>(HotelState.Loading) }
    LaunchedEffect(repository) {
        val data = repository?.load()
        state = if (data != null) HotelState.Ready(data) else HotelState.Unavailable
    }
    when (val s = state) {
        HotelState.Loading -> CenteredMessage(text.loading)
        HotelState.Unavailable -> CenteredMessage(text.hotelUnavailable)
        is HotelState.Ready -> HotelContent(s.data, language, fallback)
    }
}

@Composable
private fun HotelContent(data: HotelData, language: String, fallback: String) {
    val colors = HospitalityTheme.colors
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val first = rememberInitialFocus()
    val interaction = remember { MutableInteractionSource() }
    val descriptionFocused by interaction.collectIsFocusedAsState()

    // Mismo orden que la app del celular: primero las fotos (grandes, con su título), después el texto.
    Column(
        // Sin margen lateral en la columna: la fila de fotos llega al borde de la pantalla (se ve que
        // sigue) en vez de cortarse en seco antes del borde.
        modifier = Modifier.fillMaxSize().padding(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(data.hotelInfo.name.resolve(language, fallback), style = HospitalityTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(start = ScreenMargin + 12.dp))

        if (data.images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = ScreenMargin + 12.dp, vertical = 12.dp)) {
                itemsIndexed(data.images, key = { _, item -> item.id }) { index, image ->
                    FocusCard(
                        onClick = {},
                        modifier = Modifier.size(width = 360.dp, height = 220.dp)
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    ) {
                        AsyncImage(
                            model = imageModel(image.url),
                            contentDescription = image.title.resolve(language, fallback),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        BottomScrim()
                        Text(
                            image.title.resolve(language, fallback),
                            style = HospitalityTheme.typography.label,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                        )
                    }
                }
            }
        }

        // El control remoto no "arrastra": la descripción toma foco y las flechas la desplazan.
        Text(
            text = data.hotelInfo.description.resolve(language, fallback),
            style = HospitalityTheme.typography.body,
            color = colors.textSecondary,
            modifier = Modifier
                .padding(horizontal = ScreenMargin)
                .fillMaxWidth()
                .weight(1f, fill = false)
                .border(1.dp, if (descriptionFocused) colors.focus else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .then(if (data.images.isEmpty()) Modifier.focusRequester(first) else Modifier)
                .onPreviewKeyEvent { event ->
                    // Mientras quede texto, las flechas desplazan; al llegar al borde, el foco sigue.
                    val native = event.nativeKeyEvent
                    val step = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (scroll.value < scroll.maxValue) 240f else 0f
                        KeyEvent.KEYCODE_DPAD_UP -> if (scroll.value > 0) -240f else 0f
                        else -> 0f
                    }
                    if (step == 0f) return@onPreviewKeyEvent false
                    if (native.action == KeyEvent.ACTION_DOWN) scope.launch { scroll.animateScrollBy(step) }
                    true
                }
                .focusable(interactionSource = interaction)
                .verticalScroll(scroll),
        )
    }
}

private val ScreenMargin = 52.dp
