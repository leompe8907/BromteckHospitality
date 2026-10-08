package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.brand.UiText

/**
 * Check-out local, como en la app vieja: borra la sesión y vuelve a la selección de idioma.
 * El "check-out express" del deck (avisar a recepción) necesita el backend propio.
 */
@Composable
fun CheckoutDialog(text: UiText, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = HospitalityTheme.colors
    // usePlatformDefaultWidth = false: el ancho por defecto de Android aplastaba los botones.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // El foco arranca en "Cancelar": un OK accidental del control no debe borrar la sesión.
        val cancelFocus = rememberInitialFocus()
        Column(
            modifier = Modifier.width(560.dp).clip(RoundedCornerShape(18.dp)).background(colors.background).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text.checkoutQuestion, style = HospitalityTheme.typography.title, color = colors.textPrimary)
            Text(text.checkoutDetail, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                FocusCard(onClick = onDismiss, modifier = Modifier.size(width = 200.dp, height = 64.dp).focusRequester(cancelFocus)) {
                    Text(text.cancel, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
                }
                FocusCard(onClick = onConfirm, modifier = Modifier.size(width = 200.dp, height = 64.dp)) {
                    Text(text.confirm, style = HospitalityTheme.typography.label, color = colors.accent, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
