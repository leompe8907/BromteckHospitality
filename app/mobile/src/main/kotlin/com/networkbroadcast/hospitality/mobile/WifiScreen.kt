package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.qrImageBitmap
import com.networkbroadcast.hospitality.hotel.WifiNetwork
import kotlinx.coroutines.delay

/**
 * Wi-Fi del hotel (X_APP_WIFI), pantalla `s_wifi` del mockup: la tarjeta con el QR de 206 dp sobre blanco, el
 * nombre de la red y la clave con "Copiar", y debajo la indicación de escanear.
 */
@Composable
fun WifiScreen(network: WifiNetwork, text: UiText, contentText: ContentText, onBack: () -> Unit) {
    val colors = HospitalityTheme.colors
    val qr = remember(network) { qrImageBitmap(network.qrPayload) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_900); copied = false } }
    val card = RoundedCornerShape(rad(22))
    Column(Modifier.fillMaxSize()) {
        TopBar(text.wifiTitle, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp).fillMaxWidth().clip(card).background(colors.surface).border(1.dp, colors.surfaceBorder, card)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                QrBox(qr, 206.dp, 12.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text.wifiNetwork.uppercase(), color = colors.textSecondary,
                        style = HospitalityTheme.typography.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.47.sp))
                    Text(network.ssid, color = colors.textPrimary, textAlign = TextAlign.Center,
                        style = HospitalityTheme.typography.title.copy(fontSize = 24.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                }
                val key = network.password
                if (key != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(rad(14, 2))).background(colors.textPrimary.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text.wifiPassword.uppercase(), color = colors.textMuted,
                                style = HospitalityTheme.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                            Text(key, color = colors.textPrimary, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp))
                        }
                        Row(
                            Modifier.clickable { clipboard.setText(AnnotatedString(key)); copied = true }.padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                            Text(if (copied) contentText.copied else contentText.copy, color = colors.accent,
                                style = HospitalityTheme.typography.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                } else {
                    Text(text.wifiOpen, color = colors.textSecondary, style = HospitalityTheme.typography.body.copy(fontSize = 13.sp))
                }
            }
            Text(text.wifiScanHint, color = colors.textSecondary, textAlign = TextAlign.Center,
                style = HospitalityTheme.typography.body.copy(fontSize = 13.sp),
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 16.dp, bottom = 40.dp).fillMaxWidth())
        }
    }
}
