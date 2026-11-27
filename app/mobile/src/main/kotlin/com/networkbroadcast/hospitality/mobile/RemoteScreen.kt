package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.companion.RemoteKey
import com.networkbroadcast.hospitality.companion.TvCommand
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/*
 * Control remoto del celular (mockup `s_remote`, docs/mockup-celular/index.html; 1 px = 1 dp): la TV de la habitación
 * arriba, atrás/inicio/guía, volumen, flechas con OK, canal, silencio/hotel y "Mandar a la TV" con lo que se está
 * viendo en el celular. Cada botón manda una orden al backend (CompanionLink.send); la TV la levanta en su próxima
 * consulta. El celular no sabe qué muestra la TV: la TV chica solo muestra la última orden enviada.
 */

/** Textos del control remoto (los del mockup) en los cinco idiomas; lo que no se conoce cae a inglés. */
data class RemoteText(
    val title: String,
    val back: String,
    val home: String,
    val guide: String,
    val hotel: String,
    val volume: String,
    val channel: String,
    val mute: String,
    /** "Orden enviada" (chip de la TV chica, seguido de la orden). */
    val lastSent: String,
    val ready: String,
    val tvOn: String,
    private val tvRoom: String,
    val watching: String,
    val sendTv: String,
    val sentTv: String,
    /** La orden no llegó al backend (sin red, o el celular ya no está vinculado). No está en el mockup. */
    val notSent: String,
) {
    fun tvRoom(room: String) = tvRoom.replace("{r}", room)

    companion object {
        fun of(language: String): RemoteText = when (language) {
            "es" -> RemoteText("Control remoto", "Atrás", "Inicio", "Guía", "El hotel", "Volumen", "Canal", "Silencio", "Orden enviada", "Listo para enviar órdenes", "TV conectada", "TV de la habitación {r}", "Lo que estás viendo", "Mandar a la TV", "Enviado a la TV", "No llegó a la TV")
            "pt" -> RemoteText("Controle", "Voltar", "Início", "Guia", "O hotel", "Volume", "Canal", "Mudo", "Comando enviado", "Pronto para enviar comandos", "TV conectada", "TV do quarto {r}", "O que você está vendo", "Enviar para a TV", "Enviado para a TV", "Não chegou à TV")
            "fr" -> RemoteText("Télécommande", "Retour", "Accueil", "Guide", "L'hôtel", "Volume", "Chaîne", "Muet", "Commande envoyée", "Prêt à envoyer des commandes", "TV connectée", "TV chambre {r}", "Ce que vous regardez", "Envoyer à la TV", "Envoyé à la TV", "La TV n'a pas reçu la commande")
            "de" -> RemoteText("Fernbedienung", "Zurück", "Start", "Guide", "Das Hotel", "Lautstärke", "Sender", "Stumm", "Befehl gesendet", "Bereit für Befehle", "TV verbunden", "TV Zimmer {r}", "Das sehen Sie gerade", "An den TV senden", "An den TV gesendet", "Der TV hat den Befehl nicht erhalten")
            else -> RemoteText("Remote", "Back", "Home", "Guide", "The hotel", "Volume", "Channel", "Mute", "Command sent", "Ready to send commands", "TV connected", "Room {r} TV", "What you're watching", "Send to TV", "Sent to the TV", "Couldn't reach the TV")
        }
    }
}

/** Lo último que se puso a ver en el celular y que se puede mandar a la TV (un canal o una película; un evento anterior no). */
data class NowPlaying(val title: String, val imageUrl: String?, val command: TvCommand)

/** Estado de la última orden, para el chip de la TV chica: null = todavía no se mandó ninguna. */
data class LastCommand(val label: String, val delivered: Boolean)

@Composable
fun RemoteScreen(
    text: RemoteText,
    roomNumber: String?,
    showGuide: Boolean,
    showHotel: Boolean,
    nowPlaying: NowPlaying?,
    last: LastCommand?,
    /** Manda la orden; [label] es lo que se muestra en el chip ("Volumen +"). */
    onSend: (command: TvCommand, label: String) -> Unit,
    onSendNowPlaying: (NowPlaying) -> Unit,
    onBack: () -> Unit,
) {
    fun key(k: RemoteKey, label: String) = onSend(TvCommand.Key(k), label)
    Column(Modifier.fillMaxSize()) {
        TopBar(text.title, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) { TvBox(text, roomNumber, last) }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(text.back, R.drawable.ic_back) { key(RemoteKey.BACK, text.back) }
                    RemoteButton(text.home, R.drawable.ic_home) { key(RemoteKey.HOME, text.home) }
                    if (showGuide) RemoteButton(text.guide, R.drawable.ic_guide_grid) { onSend(TvCommand.OpenGuide, text.guide) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Rocker(
                        label = text.volume.take(3),
                        up = { RockerKey(R.drawable.ic_plus, "${text.volume} +", 22.dp) { key(RemoteKey.VOLUME_UP, "${text.volume} +") } },
                        down = { RockerKey(R.drawable.ic_minus, "${text.volume} −", 22.dp) { key(RemoteKey.VOLUME_DOWN, "${text.volume} −") } },
                    )
                    Dpad(onKey = { k, label -> key(k, label) })
                    Rocker(
                        label = text.channel.take(3),
                        up = { RockerKey(R.drawable.ic_chevron_up, "${text.channel} +", 24.dp) { key(RemoteKey.CHANNEL_UP, "${text.channel} +") } },
                        down = { RockerKey(R.drawable.ic_chevron_up, "${text.channel} −", 24.dp, rotation = 180f) { key(RemoteKey.CHANNEL_DOWN, "${text.channel} −") } },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteButton(text.mute, R.drawable.ic_mute) { key(RemoteKey.MUTE, text.mute) }
                    if (showHotel) RemoteButton(text.hotel, R.drawable.ic_building) { onSend(TvCommand.OpenHotelInfo, text.hotel) }
                }
            }
            nowPlaying?.let { now ->
                Card(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 34.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(92.dp, 52.dp).clip(RoundedCornerShape(8.dp)).background(HospitalityTheme.colors.surfaceBorder), contentAlignment = Alignment.Center) {
                                if (now.imageUrl != null) AsyncImage(imageModel(now.imageUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Icon(painterResource(R.drawable.ic_tv), contentDescription = null, tint = HospitalityTheme.colors.textMuted, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(text.watching.uppercase(), color = HospitalityTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = HospitalityTheme.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                                Text(now.title, color = HospitalityTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = HospitalityTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                        SendToTvButton(text.sendTv, Modifier.fillMaxWidth().height(46.dp), fontSize = 14.5f) { onSendNowPlaying(now) }
                    }
                }
            } ?: Box(Modifier.height(34.dp))
        }
    }
}

/** Tarjeta del mockup (`.card`): fondo y borde de las tarjetas, esquinas de 22 dp por el factor del tema. */
@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(22))
    Box(modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.surfaceBorder, shape), content = content)
}

/** La TV de la habitación (`.tvbox`): la pantalla (sin imagen: el celular no sabe qué muestra) y el estado de la última orden. */
@Composable
private fun TvBox(text: RemoteText, roomNumber: String?, last: LastCommand?) {
    val colors = HospitalityTheme.colors
    Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(176.dp, 99.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black).border(1.dp, colors.surfaceBorder, RoundedCornerShape(9.dp))) {
            Icon(painterResource(R.drawable.ic_tv), contentDescription = null, tint = Color.White.copy(alpha = 0.22f), modifier = Modifier.align(Alignment.Center).size(34.dp))
            last?.let {
                Text(it.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 9.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(roomNumber?.let(text::tvRoom) ?: text.tvOn, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = HospitalityTheme.typography.label.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold))
            if (roomNumber != null) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(colors.accent)
                    Text(text.tvOn, color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 11.5.sp))
                }
            }
            val chip = when {
                last == null -> text.ready
                last.delivered -> "${text.lastSent}: ${last.label}"
                else -> text.notSent
            }
            val chipColor = if (last != null && !last.delivered) Color(0xFFE5484D) else colors.accent
            Text(chip, color = chipColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = HospitalityTheme.typography.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(chipColor.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

/** Punto de estado (`.dot`): 7 dp con un halo del mismo color. */
@Composable
internal fun StatusDot(color: Color) {
    Box(Modifier.size(13.dp).clip(CircleShape).background(color.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

/** Botón de la fila (`.rb`): 50 dp de alto, ícono de 18 dp y rótulo. */
@Composable
private fun RowScope.RemoteButton(label: String, icon: Int, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(25, 2))
    Row(
        Modifier.weight(1f).height(50.dp).clip(shape).background(colors.surface).border(1.dp, colors.surfaceBorder, shape).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
        Text(label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = HospitalityTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Volumen o canal (`.rock`): cápsula vertical de 68 x 196 con + arriba, el rótulo y − abajo. */
@Composable
private fun Rocker(label: String, up: @Composable () -> Unit, down: @Composable () -> Unit) {
    val colors = HospitalityTheme.colors
    Column(
        Modifier.size(68.dp, 196.dp).clip(RoundedCornerShape(34.dp)).background(colors.surface).border(1.dp, colors.surfaceBorder, RoundedCornerShape(34.dp)).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        up()
        Text(label.uppercase(), color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
        down()
    }
}

@Composable
private fun RockerKey(icon: Int, description: String, iconSize: Dp, rotation: Float = 0f, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = description, tint = HospitalityTheme.colors.textPrimary, modifier = Modifier.size(iconSize).rotate(rotation))
    }
}

/** Flechas y OK (`.dpad`): círculo de 212 dp, flechas de 62 dp en el borde y OK de 86 dp en el color de acento. */
@Composable
private fun Dpad(onKey: (RemoteKey, String) -> Unit) {
    val colors = HospitalityTheme.colors
    val ink = HospitalityColors.readableTextColor(colors.accent)
    Box(Modifier.size(212.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.surfaceBorder, CircleShape)) {
        listOf(
            Triple(RemoteKey.UP, Alignment.TopCenter, 0f),
            Triple(RemoteKey.RIGHT, Alignment.CenterEnd, 90f),
            Triple(RemoteKey.DOWN, Alignment.BottomCenter, 180f),
            Triple(RemoteKey.LEFT, Alignment.CenterStart, 270f),
        ).forEach { (k, align, angle) ->
            Box(Modifier.align(align).padding(6.dp).size(62.dp).clip(CircleShape).clickable { onKey(k, arrowLabel(k)) }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron_up), contentDescription = k.name, tint = colors.textPrimary, modifier = Modifier.size(28.dp).rotate(angle))
            }
        }
        Box(Modifier.align(Alignment.Center).size(86.dp).clip(CircleShape).background(colors.accent).clickable { onKey(RemoteKey.OK, "OK") }, contentAlignment = Alignment.Center) {
            Text("OK", color = ink, style = HospitalityTheme.typography.label.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private fun arrowLabel(key: RemoteKey) = when (key) {
    RemoteKey.UP -> "↑"
    RemoteKey.DOWN -> "↓"
    RemoteKey.LEFT -> "←"
    else -> "→"
}

/** Flechas y OK en chico (`.mdp`), de adorno en el tile del control: sobre el acento, el OK va en el color de texto del acento. */
@Composable
internal fun MiniDpad(size: Dp, ink: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.15f))) {
        listOf(Alignment.TopCenter to 0f, Alignment.CenterEnd to 90f, Alignment.BottomCenter to 180f, Alignment.CenterStart to 270f).forEach { (align, angle) ->
            Icon(painterResource(R.drawable.ic_chevron_up), contentDescription = null, tint = ink.copy(alpha = 0.9f),
                modifier = Modifier.align(align).padding(size * 0.04f).size(size * 0.2f).rotate(angle))
        }
        Box(Modifier.align(Alignment.Center).size(size * 0.36f).clip(CircleShape).background(ink), contentAlignment = Alignment.Center) {
            Text("OK", color = HospitalityTheme.colors.accent, style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** "Mandar a la TV" (`.sendb`): color de acento, ícono de cast y texto. */
@Composable
internal fun SendToTvButton(label: String, modifier: Modifier = Modifier.height(40.dp), fontSize: Float = 13f, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val ink = HospitalityColors.readableTextColor(colors.accent)
    Row(
        modifier.clip(RoundedCornerShape(rad(20, 2))).background(colors.accent).clickable(onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_cast), contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        Text(label, color = ink, maxLines = 1, style = HospitalityTheme.typography.label.copy(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Aviso corto abajo (`.toast`): cápsula oscura con el texto, sin tocar (no tapa botones). */
@Composable
internal fun BoxScope.TvToast(message: String?) {
    if (message == null) return
    Text(
        message, color = Color.White, maxLines = 1,
        style = HospitalityTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-48).dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xE0141618)).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
