package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/** Textos de la pantalla neutra, en los idiomas de la app. */
internal data class NeutralText(
    val title: String,
    val hint: String,
    val checking: String,
    val failed: String,
) {
    companion object {
        fun of(language: String): NeutralText = when (language) {
            "en" -> NeutralText(
                "Scan your room's QR code", "Scan the QR code on your hotel room's TV to watch the content.",
                "Connecting…", "Could not connect",
            )
            "pt" -> NeutralText(
                "Escaneie o QR do seu quarto", "Escaneie o QR code na TV do seu quarto de hotel para ver o conteúdo.",
                "Conectando…", "Não foi possível conectar",
            )
            "fr" -> NeutralText(
                "Scannez le QR de votre chambre", "Scannez le QR code sur la télévision de votre chambre d'hôtel pour voir le contenu.",
                "Connexion…", "Connexion impossible",
            )
            "de" -> NeutralText(
                "Scannen Sie den QR-Code Ihres Zimmers", "Scannen Sie den QR-Code auf dem Fernseher Ihres Hotelzimmers, um die Inhalte zu sehen.",
                "Verbinde…", "Verbindung nicht möglich",
            )
            else -> NeutralText(
                "Escanea el QR de tu habitación", "Escanea el QR en la televisión de tu habitación de hotel para ver el contenido.",
                "Conectando…", "No se pudo conectar",
            )
        }
    }
}

/**
 * Primera pantalla, antes de tener sesión: todavía no se sabe de qué hotel es, así que es neutra
 * (la marca, los colores y el logo del hotel llegan después de escanear el QR de la TV).
 * La cámara del celular abre la app con el enlace del QR; un escáner propio queda para después.
 *
 * En builds de depuración se suma un formulario para entrar con una cuenta de prueba.
 */
@Composable
internal fun NeutralScreen(
    text: NeutralText,
    session: MobileSession,
    debugLogin: ((user: String, password: String, room: String?) -> Unit)?,
) {
    val colors = HospitalityTheme.colors
    val typography = HospitalityTheme.typography
    Box(Modifier.fillMaxSize().background(colors.background).systemBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text.title, style = typography.title, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(text.hint, style = typography.body, color = colors.textPrimary.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            when (session) {
                MobileSession.Checking -> Text(text.checking, style = typography.label, color = colors.accent)
                is MobileSession.Failed -> Text("${text.failed}: ${session.detail}", style = typography.label, color = colors.accent, textAlign = TextAlign.Center)
                else -> Unit
            }
            if (debugLogin != null) DebugLoginForm(checking = session == MobileSession.Checking, onLogin = debugLogin)
        }
    }
}

@Composable
private fun DebugLoginForm(checking: Boolean, onLogin: (String, String, String?) -> Unit) {
    val colors = HospitalityTheme.colors
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DEBUG · cuenta de prueba", style = HospitalityTheme.typography.label, color = colors.textPrimary.copy(alpha = 0.6f))
        OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(room, { room = it }, label = { Text("Licencia / habitación (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent)
                .let { if (checking || user.isBlank() || password.isBlank()) it else it.clickable { onLogin(user, password, room) } },
            contentAlignment = Alignment.Center,
        ) {
            Text("Entrar", style = HospitalityTheme.typography.label, color = colors.background)
        }
    }
}
