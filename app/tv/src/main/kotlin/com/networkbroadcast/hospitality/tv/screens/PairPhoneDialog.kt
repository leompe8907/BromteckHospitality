package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.companion.PairingCode
import com.networkbroadcast.hospitality.companion.TvPairing
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.qrImageBitmap
import kotlinx.coroutines.delay

/**
 * "Conectar celular": código de 6 dígitos + QR para la app del celular. El código lo da el backend
 * ([TvPairing]); vence, y se pide uno nuevo solo.
 */
@Composable
fun PairPhoneDialog(pairing: TvPairing, text: UiText, onDismiss: () -> Unit) {
    val colors = HospitalityTheme.colors
    var code by remember { mutableStateOf<PairingCode?>(null) }
    LaunchedEffect(pairing) {
        while (true) {
            code = pairing.requestCode()
            val ttl = code?.let { it.expiresAtEpochSec - System.currentTimeMillis() / 1000 } ?: 60
            delay(ttl.coerceAtLeast(30) * 1000)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = rememberInitialFocus()
        Row(
            Modifier.width(780.dp).clip(RoundedCornerShape(18.dp)).background(colors.background).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text.pairTitle, style = HospitalityTheme.typography.title, color = colors.textPrimary)
                Text(text.pairHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                Text(
                    code?.code?.chunked(3)?.joinToString(" ") ?: "··· ···",
                    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
                    color = colors.accent,
                )
                FocusCard(onClick = onDismiss, modifier = Modifier.size(width = 200.dp, height = 56.dp).focusRequester(focus)) {
                    Text(text.accept, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
                }
            }
            code?.let { c ->
                val qr = remember(c.qrPayload) { qrImageBitmap(c.qrPayload) }
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                    Image(qr, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(200.dp))
                }
            }
        }
    }
}
