package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.qrImageBitmap
import com.networkbroadcast.hospitality.hotel.WifiNetwork
import com.networkbroadcast.hospitality.hotel.WifiSecurity

/**
 * Wi-Fi del hotel (X_APP_WIFI): el QR para conectar el celular, y al lado el nombre de la red y la clave
 * en texto, por si la cámara no lo lee. El QR va sobre blanco y trae su propio margen, y se dibuja grande:
 * se lee desde el sillón. Con [lobby] el fondo es negro neutro, no el color del tema (verde en la paleta de
 * Resort), igual que la bienvenida de la dirección Lobby.
 */
@Composable
fun WifiDialog(network: WifiNetwork, text: UiText, lobby: Boolean = false, onDismiss: () -> Unit) {
    val colors = HospitalityTheme.colors
    val qr = remember(network) { qrImageBitmap(network.qrPayload) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = rememberInitialFocus()
        Row(
            Modifier.width(780.dp).clip(RoundedCornerShape(18.dp)).background(if (lobby) Color(0xF0111111) else colors.background).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text.wifiTitle, style = HospitalityTheme.typography.title, color = colors.textPrimary)
                Text(text.wifiScanHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                WifiLine(text.wifiNetwork, network.ssid, mono = false, maxLines = 2)
                val key = network.password
                if (network.security == WifiSecurity.OPEN || key == null) {
                    Text(text.wifiOpen, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                } else {
                    // Monoespaciada (que no se confundan l/1 u O/0) y sin recortar: si es larga, baja de línea.
                    WifiLine(text.wifiPassword, key, mono = true, maxLines = 3)
                }
                FocusCard(onClick = onDismiss, modifier = Modifier.size(width = 200.dp, height = 56.dp).focusRequester(focus)) {
                    Text(text.accept, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
                }
            }
            Image(
                qr, contentDescription = null, filterQuality = FilterQuality.None,
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            )
        }
    }
}

@Composable
private fun WifiLine(label: String, value: String, mono: Boolean, maxLines: Int) {
    val colors = HospitalityTheme.colors
    Column {
        Text(label, style = HospitalityTheme.typography.label, color = colors.textSecondary, maxLines = 1)
        Text(
            value,
            style = TextStyle(
                fontFamily = if (mono) FontFamily.Monospace else HospitalityTheme.typography.body.fontFamily,
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary, maxLines = maxLines, modifier = Modifier.padding(top = 2.dp),
        )
    }
}
