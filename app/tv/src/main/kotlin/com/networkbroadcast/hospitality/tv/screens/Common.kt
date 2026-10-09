package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/**
 * En TV no hay toque: si nada tiene foco al abrir una pantalla, el control remoto no hace nada.
 * Cada pantalla pide el foco para su elemento inicial con esto.
 *
 * Al volver con "atrás", en el primer frame la tarjeta todavía no está adjunta y el pedido se
 * pierde (la home quedaba sin foco); por eso se reintenta unos frames.
 */
@Composable
fun rememberInitialFocus(key: Any? = Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        repeat(10) {
            withFrameNanos { }
            // requestFocus(Enter) devuelve false si la tarjeta todavía no puede recibir el foco.
            if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    return requester
}

/**
 * Margen para listas desplazables: la tarjeta con foco crece un 5% y, sin este margen, la lista
 * le recorta el borde de foco.
 */
val FocusListPadding = PaddingValues(12.dp)

@Composable
fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = HospitalityTheme.typography.body, color = HospitalityTheme.colors.textSecondary)
    }
}

/**
 * Degradado oscuro abajo para que el texto sobre una foto se lea siempre. Negro neutro, no el color
 * de fondo del tema: antes usaba el verde de la paleta Resort y se veía como un filtro de color
 * sobre cualquier póster, en vez de un simple sombreado para legibilidad.
 */
@Composable
fun BoxScope.BottomScrim() {
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
    )
}
