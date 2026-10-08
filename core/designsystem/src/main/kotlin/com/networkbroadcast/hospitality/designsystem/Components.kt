package com.networkbroadcast.hospitality.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(14.dp)

/**
 * Tarjeta navegable con el control remoto: crece un poco y muestra el borde de foco
 * (amarillo en resort, como en el mockup de la home). Toda la app usa ésta, así el foco
 * se comporta igual en todas las pantallas.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardShape,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = HospitalityTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // El foco se lee del árbol de foco (onFocusChanged), no de los eventos de interacción: con
    // éstos, si el foco llega por FocusRequester apenas se compone la pantalla, a veces el evento
    // se pierde y la tarjeta queda enfocada pero sin borde (pasó en la ficha de VOD).
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.focus else colors.surfaceBorder,
                shape = shape,
            )
            // clickable ya es enfocable y responde al OK del control (DPAD_CENTER / Enter).
            // No sumar un focusable() aparte: el foco quedaría en otro nodo y el OK no haría nada.
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        content = { content(focused) },
    )
}

/** Píldora informativa ("29°C · Cancún · Hab. 214"). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    val colors = HospitalityTheme.colors
    Text(
        text = text,
        style = HospitalityTheme.typography.label,
        color = colors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = HospitalityTheme.typography.section,
        color = HospitalityTheme.colors.textPrimary,
        modifier = modifier,
    )
}
