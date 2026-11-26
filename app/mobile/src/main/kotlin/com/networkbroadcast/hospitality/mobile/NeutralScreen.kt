package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/** Textos de la pantalla neutra, en los idiomas de la app. */
internal data class NeutralText(
    val title: String,
    val step1: String,
    val step2: String,
    val checking: String,
    val failed: String,
) {
    companion object {
        fun of(language: String): NeutralText = when (language) {
            "en" -> NeutralText(
                "Scan your room's QR code", "Look at the TV in your room", "Open your phone's camera and point it at the QR code",
                "Connecting…", "Could not connect",
            )
            "pt" -> NeutralText(
                "Escaneie o QR do seu quarto", "Olhe para a TV do seu quarto", "Abra a câmera do celular e aponte para o QR",
                "Conectando…", "Não foi possível conectar",
            )
            "fr" -> NeutralText(
                "Scannez le QR de votre chambre", "Regardez la télévision de votre chambre", "Ouvrez l'appareil photo du téléphone et visez le QR code",
                "Connexion…", "Connexion impossible",
            )
            "de" -> NeutralText(
                "Scannen Sie den QR-Code Ihres Zimmers", "Schauen Sie auf den Fernseher in Ihrem Zimmer", "Öffnen Sie die Kamera Ihres Handys und richten Sie sie auf den QR-Code",
                "Verbinde…", "Verbindung nicht möglich",
            )
            else -> NeutralText(
                "Escanea el QR de tu habitación", "Mira la TV de tu habitación", "Abre la cámara del celular y apunta al QR",
                "Conectando…", "No se pudo conectar",
            )
        }
    }
}

/** Archivo del logo de la marca (el mismo que usa la TV): `brands/<marca>/assets/logo.png`, solo si `showLogo` es true. */
private const val BRAND_LOGO_ASSET = "asset:logo.png"

/**
 * true si antes del login hay algo propio del hotel: un logo (del panel, [logoUrl], o el que cargó la marca con
 * `showLogo`) o fondos de la marca; si no, la pantalla es genérica. No mira flags: lo decide lo que hay disponible.
 * El logo de Panaccess no cuenta: solo llega con la sesión abierta.
 */
internal fun BrandConfig.hasHotelIdentity(logoUrl: String? = null): Boolean = logoUrl != null || showLogo || backgrounds.isNotEmpty()

/**
 * Primera pantalla, antes de tener sesión. Si hay identidad del hotel ([hasHotelIdentity]) se ve del hotel: la
 * primera foto de `backgrounds` de fondo (con la capa lisa de [MobileBackdrop]), el logo o el nombre y la ciudad.
 * Si no, se ve genérica: un ícono de QR.
 *
 * El logo, por orden: [logoUrl] (el del panel, que solo se conoce antes del login si la app tiene panel y es de un solo
 * hotel), el que cargó la marca (`showLogo`, archivo `logo.png` de sus assets) y, si no hay ninguno, el nombre en texto.
 * Sin panel y sin logo en la marca no hay logo hasta el login, cuando Panaccess lo manda; con panel y una app para
 * varios hoteles, recién cuando el QR dice cuál es, y ahí ya hay sesión y esta pantalla no se vuelve a ver. Hoy no hay
 * panel conectado: quien llama pasa null. En los dos casos sigue el título y una tarjeta con los dos pasos
 * (mirar la TV, abrir la cámara). Los colores y el logo reales de la cuenta llegan después de escanear el QR de la TV
 * (la config del operador solo se pide con la sesión abierta). La cámara del celular abre la app con el enlace del QR;
 * un escáner propio queda para después.
 *
 * En builds de depuración se suma un formulario para entrar con una cuenta de prueba.
 */
@Composable
internal fun NeutralScreen(
    text: NeutralText,
    brand: BrandConfig,
    logoUrl: String?,
    session: MobileSession,
    debugLogin: ((user: String, password: String, room: String?) -> Unit)?,
) {
    val colors = HospitalityTheme.colors
    val typography = HospitalityTheme.typography
    val identity = brand.hasHotelIdentity(logoUrl)
    Box(Modifier.fillMaxSize()) {
        MobileBackdrop(brand.backgrounds.firstOrNull()?.let { MobileBackground.Photo(it) })
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (identity) HotelHeader(brand, logoUrl) else QrBadge()
                Text(text.title, style = typography.title, color = colors.textPrimary, textAlign = TextAlign.Center)
                val card = RoundedCornerShape(rad(18))
                Column(
                    Modifier.fillMaxWidth().clip(card).background(colors.surface).border(1.dp, colors.surfaceBorder, card).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Step(1, text.step1)
                    Step(2, text.step2)
                }
                when (session) {
                    MobileSession.Checking -> Text(text.checking, style = typography.label, color = colors.accent)
                    is MobileSession.Failed -> Text("${text.failed}: ${session.detail}", style = typography.label, color = colors.accent, textAlign = TextAlign.Center)
                    else -> Unit
                }
                if (debugLogin != null) DebugLoginForm(checking = session == MobileSession.Checking, onLogin = debugLogin)
            }
        }
    }
}

/** Logo (del panel o de la marca; si no hay, el nombre en texto, como la TV) y, si la marca tiene ciudad, una píldora con ella. */
@Composable
private fun HotelHeader(brand: BrandConfig, logoUrl: String?) {
    val colors = HospitalityTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val logo = logoUrl ?: BRAND_LOGO_ASSET.takeIf { brand.showLogo }
        if (logo != null) {
            AsyncImage(model = imageModel(logo), contentDescription = brand.displayName, modifier = Modifier.height(72.dp))
        } else {
            Text(brand.displayName, style = HospitalityTheme.typography.display, color = colors.textPrimary, textAlign = TextAlign.Center)
        }
        brand.location?.takeIf { it.isNotBlank() }?.let { city ->
            val pill = RoundedCornerShape(50)
            Row(
                Modifier.clip(pill).background(colors.surface).border(1.dp, colors.surfaceBorder, pill).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Text(city, color = colors.accent, style = HospitalityTheme.typography.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** Ícono de QR en un círculo de color de acento, para la pantalla genérica. */
@Composable
private fun QrBadge() {
    val colors = HospitalityTheme.colors
    Box(Modifier.size(80.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_qr), contentDescription = null, tint = colors.background, modifier = Modifier.size(40.dp))
    }
}

/** Un paso numerado: círculo con el número y el texto al lado. */
@Composable
private fun Step(number: Int, label: String) {
    val colors = HospitalityTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = colors.background, style = HospitalityTheme.typography.label.copy(fontWeight = FontWeight.Bold))
        }
        Text(label, style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
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
