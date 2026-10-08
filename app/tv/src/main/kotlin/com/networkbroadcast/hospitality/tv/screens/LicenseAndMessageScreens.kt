package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.brand.UiText
import com.panaccess.android.streaming.shared.domain.model.License

/**
 * Elección manual de licencia (lo ve el instalador). Aparece cuando ninguna se pudo tomar sola,
 * normalmente porque todas están en uso en otras TVs. Equivale a LicenseSelectionScreen del base.
 */
@Composable
fun LicenseScreen(text: UiText, licenses: List<License>, busy: Boolean, error: String?, onChoose: (License) -> Unit) {
    val colors = HospitalityTheme.colors
    val first = rememberInitialFocus(licenses.size)
    Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text.chooseLicense, style = HospitalityTheme.typography.title, color = colors.textPrimary)
        Text(text.chooseLicenseHint, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(760.dp))
        error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = FocusListPadding) {
            itemsIndexed(licenses, key = { i, l -> l.key ?: "$i" }) { index, license ->
                FocusCard(
                    onClick = { if (!busy) onChoose(license) },
                    enabled = !busy && license.hasUsablePin,
                    modifier = Modifier.fillMaxWidth().height(72.dp).then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                ) {
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp)) {
                        Text(
                            license.licenseName?.takeIf { it.isNotBlank() } ?: "${text.license} ${index + 1}",
                            style = HospitalityTheme.typography.label, color = colors.textPrimary,
                        )
                        // Sólo el final de la clave, para distinguirlas sin mostrarla entera.
                        license.key?.takeLast(6)?.let {
                            Text("…$it" + (license.products?.let { p -> " · $p" } ?: ""), style = HospitalityTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Mensaje del operador (OSM). Se cierra con OK o atrás. */
@Composable
fun OperatorMessageDialog(text: UiText, message: String, onDismiss: () -> Unit) {
    val colors = HospitalityTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = rememberInitialFocus(message)
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(18.dp)).background(colors.background).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text.messageTitle, style = HospitalityTheme.typography.title, color = colors.textPrimary)
            Column(Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                Text(message, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            }
            FocusCard(onClick = onDismiss, modifier = Modifier.size(width = 200.dp, height = 60.dp).focusRequester(focus)) {
                Text(text.accept, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
