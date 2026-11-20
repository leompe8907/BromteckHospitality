package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/*
 * PRUEBA de la dirección "Lobby": bienvenida e idioma en un solo popup, sobre
 * la foto del hotel, antes de que cargue nada más (feedback de Enrique, 20-nov-2026). Aparece la
 * primera vez en la estadía y vuelve después del check-out; reemplaza a LanguageScreen +
 * WelcomeDialog en la marca de prueba. Desde la home ("Idioma") se abre solo la parte de idioma.
 */
@Composable
fun LobbyWelcome(
    brand: BrandConfig,
    /** Logo que cargó el operador (X_APP_LOGO); null = el de la marca, si lo trae. */
    logoUrl: String?,
    guestName: String?,
    room: String?,
    selected: String?,
    onlyLanguage: Boolean,
    onSelected: (String) -> Unit,
) {
    // Colores del operador: color de card para la tarjeta, de letra y de confirmación para el botón.
    val colors = lobbyGlass()
    val first = rememberInitialFocus()
    val t = UiText.of(selected ?: brand.defaultLanguage)
    val accent = lobbyConfirmColor()
    val panel = if (HospitalityColors.parseHex(LocalLobbyStyle.current.cardColorHex) != null) colors.surface.copy(alpha = 0.96f) else Color(0xF0111111)
    // Con 3 idiomas, 120 dp cada botón; con más, se achican para que entren en la tarjeta (520 dp útiles).
    val buttonWidth = minOf(120, (520 - 9 * (brand.languages.size - 1)) / brand.languages.size.coerceAtLeast(1)).dp
    Box(Modifier.fillMaxSize().background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 600.dp).clip(RoundedCornerShape(15.dp)).background(panel).padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            (logoUrl ?: if (brand.showLogo) imageModel("asset:logo.png") else null)?.let {
                AsyncImage(model = it, contentDescription = brand.displayName, contentScale = ContentScale.Fit, modifier = Modifier.height(20.dp))
            }
            if (!onlyLanguage) {
                room?.let {
                    Text("${t.room} $it".uppercase(), style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp), color = accent, modifier = Modifier.padding(top = 18.dp))
                }
                Text(
                    guestName?.takeIf { it.isNotBlank() }?.let { t.welcomeTitle.format(it.trim().split(" ").first()) + "." } ?: t.welcomeTitleNoName,
                    style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
                    color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
                )
                brand.welcome.message.let { it[selected ?: brand.defaultLanguage] ?: it[brand.defaultLanguage] ?: it["en"] }?.let {
                    Text(it, style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 13.sp, lineHeight = 19.sp), color = colors.textPrimary.copy(alpha = 0.82f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                Text(t.language, style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 30.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, modifier = Modifier.padding(top = 18.dp))
            }
            // En cada idioma ofrecido: el huésped todavía no eligió el suyo.
            Text(
                brand.languages.map { UiText.of(it).chooseLanguage }.distinct().joinToString("  ·  "),
                style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 10.sp), color = colors.textPrimary.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                brand.languages.forEachIndexed { i, code ->
                    val isFirst = if (selected != null) code == selected else i == 0
                    // Como el prototipo: en reposo, botón translúcido; con foco, relleno del color de acento.
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors provides colors.copy(surface = Color(0x1AF3EEE4), surfaceBorder = Color(0x29F3EEE4), textPrimary = accent),
                    ) {
                        FocusCard(
                            onClick = { onSelected(code) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(width = buttonWidth, height = 44.dp).then(if (isFirst) Modifier.focusRequester(first) else Modifier),
                        ) { focused ->
                            if (focused) Box(Modifier.fillMaxSize().background(accent))
                            Text(UiText.languageNames[code] ?: code.uppercase(), style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = if (focused) HospitalityColors.readableTextColor(accent) else colors.textPrimary, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}
