package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.InfoPill
import com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors

/**
 * Bienvenida al entrar a la habitación: una vez por estadía (el check-out la vuelve a habilitar).
 * El nombre sale de la estadía (backend/PMS); sin nombre, una bienvenida al hotel.
 */
@Composable
fun WelcomeDialog(
    text: UiText,
    guestName: String?,
    hotelName: String,
    room: String?,
    message: String?,
    imageUrl: String?,
    cardColorHex: String? = null,
    textColorHex: String? = null,
    /** Color del botón Confirmar (OperatorConfig.confirmColorHex), mismo criterio que el
     *  "Confirmar" de CheckoutDialog: "Comenzar" y el pill con el nombre del hotel son la única
     *  acción de esta bienvenida -- null = el color de card de siempre de la marca. */
    confirmColorHex: String? = null,
    onDismiss: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val customCard = HospitalityColors.parseHex(cardColorHex)
    val cardBackground = customCard ?: colors.background
    val cardText = HospitalityColors.parseHex(textColorHex)
        ?: customCard?.let { HospitalityColors.readableTextColor(it) }
        ?: colors.textPrimary
    val actionColors = confirmColorHex?.let { colors.withCustomCardColor(it) } ?: colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = rememberInitialFocus()
        Column(Modifier.width(720.dp).clip(RoundedCornerShape(22.dp)).background(cardBackground)) {
            imageUrl?.let {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    AsyncImage(model = imageModel(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
                    BottomScrim()
                }
            }
            Column(Modifier.padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    guestName?.let { text.welcomeTitle.format(it) } ?: text.welcomeTitleNoName,
                    style = HospitalityTheme.typography.display, color = cardText,
                )
                CompositionLocalProvider(LocalHospitalityColors provides actionColors) {
                    InfoPill(listOfNotNull(hotelName, room?.let { "${text.room} $it" }).joinToString(" · "))
                }
                message?.let { Text(it, style = HospitalityTheme.typography.body, color = cardText.copy(alpha = 0.75f)) }
                CompositionLocalProvider(LocalHospitalityColors provides actionColors) {
                    FocusCard(onClick = onDismiss, modifier = Modifier.padding(top = 6.dp).size(width = 220.dp, height = 60.dp).focusRequester(focus)) {
                        Text(text.start, style = HospitalityTheme.typography.label, color = actionColors.textPrimary, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
