package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.LobbyText
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.nowAndNext
import java.text.DateFormat
import java.util.Date

/*
 * TV en vivo y guía del celular con el diseño "Lobby" de la TV (app/tv/.../screens/LobbyScreens.kt), llevado a una
 * pantalla vertical: lo que en la TV va a la izquierda (canales) y a la derecha (vista previa, programación) acá va
 * arriba y abajo. Filas sin tarjeta, el logo del canal sobre blanco, lo elegido con una barra del color de acento.
 * Sin degradés: los velos sobre fotos son negro liso.
 */

private fun font(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = MobileFontFamily, fontSize = size, fontWeight = weight)

@Composable
private fun Kicker(s: String, color: Color = HospitalityTheme.colors.textPrimary) =
    Text(s.uppercase(), style = font(11.sp, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp), color = color.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Logo del canal sobre blanco (`ChannelBadge` del Lobby); sin logo, el número. */
@Composable
private fun ChannelBadge(ch: Channel, w: Int = 56, h: Int = 36) {
    Box(Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(3.dp), contentAlignment = Alignment.Center) {
        if (ch.logoUrl != null) AsyncImage(model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Text("${ch.number}", style = font(13.sp, FontWeight.SemiBold), color = Color(0xFF14100A))
    }
}

/** Fila de lista del Lobby: sin fondo; la elegida con un fondo suave y la barra del color de acento a la izquierda. */
@Composable
private fun ListRow(onClick: () -> Unit, selected: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = HospitalityTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) c.textPrimary.copy(alpha = 0.08f) else Color.Transparent).clickable(onClick = onClick),
    ) {
        if (selected) Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(c.accent))
        content()
    }
}

/** Chip de categoría (bouquet): pastilla; la elegida en el color de acento. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = HospitalityTheme.colors
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(if (selected) c.accent else c.textPrimary.copy(alpha = 0.08f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = font(11.sp, FontWeight.SemiBold), color = if (selected) HospitalityColors.readableTextColor(c.accent) else c.textPrimary, maxLines = 1)
    }
}

@Composable
internal fun LobbyMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = font(13.sp), color = HospitalityTheme.colors.textSecondary)
    }
}

/* ======================== TV EN VIVO ======================== */

/**
 * Canales del Lobby: las categorías (todos y los bouquets del operador) en chips y los canales en filas con el logo, el
 * número, el nombre y lo que dan ahora. Tocar un canal lo reproduce arriba.
 */
@Composable
internal fun LobbyLiveList(model: ContentModel, text: UiText, lobby: LobbyText, now: Long, selectedId: String?, onPlay: (Channel) -> Unit) {
    val result = model.channels ?: return LobbyMessage(text.loading)
    if (result.channels.isEmpty()) return LobbyMessage(result.failure?.let { "${text.noChannels} ($it)" } ?: text.noChannels)
    val byId = result.channels.associateBy { it.id }
    val groups = listOf(lobby.all to result.channels) +
        result.groups.map { g -> g.name to g.channelIds.mapNotNull(byId::get) }.filter { it.second.isNotEmpty() }
    var group by remember { mutableIntStateOf(0) }
    val list = groups[group.coerceIn(groups.indices)].second
    val c = HospitalityTheme.colors
    Column(Modifier.fillMaxSize()) {
        if (groups.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                itemsIndexed(groups) { i, (name, _) -> CategoryChip(name, i == group) { group = i } }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.id }) { ch ->
                val schedule by produceState(model.cachedSchedule(ch.id), ch.id) { value = model.schedule(ch.id) }
                val current = schedule.orEmpty().nowAndNext(now).first
                ListRow(onClick = { onPlay(ch) }, selected = ch.id == selectedId, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChannelBadge(ch)
                        Column(Modifier.weight(1f)) {
                            Text("${ch.number} · ${ch.name}", style = font(14.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current?.title ?: " ", style = font(12.sp), color = c.textPrimary.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vista previa del Lobby (`LivePreview`) antes de poner un canal: la imagen del programa al aire (o el logo del canal
 * oscurecido), "EN VIVO · canal", el título y lo que sigue. Tocarla reproduce el canal en el mismo lugar.
 */
@Composable
internal fun LobbyLivePreview(model: ContentModel, channel: Channel, text: UiText, now: Long, onPlay: () -> Unit) {
    val c = HospitalityTheme.colors
    val schedule by produceState(model.cachedSchedule(channel.id), channel.id) { value = model.schedule(channel.id) }
    val (current, next) = schedule.orEmpty().nowAndNext(now)
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp).clickable(onClick = onPlay)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(rad(14))).background(Color(0xFF0B0F1A))) {
            val eventImage = current?.imageUrl
            when {
                eventImage != null -> AsyncImage(model = eventImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                !channel.logoUrl.isNullOrBlank() -> {
                    AsyncImage(model = channel.logoUrl, contentDescription = channel.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                }
                else -> Box(Modifier.align(Alignment.Center)) { ChannelBadge(channel, 120, 72) }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                Kicker("${text.liveNow} · ${channel.number} ${channel.name}", Color.White)
            }
        }
        Text(current?.title ?: channel.name, style = font(22.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        current?.let {
            Text("${time.format(Date(it.startEpochSec * 1000))} – ${time.format(Date(it.endEpochSec * 1000))}", style = font(12.sp), color = c.textPrimary.copy(alpha = 0.72f), modifier = Modifier.padding(top = 2.dp))
        }
        next?.let {
            Text("${text.nextLabel}: ${it.title} · ${time.format(Date(it.startEpochSec * 1000))}", style = font(12.sp), color = c.textPrimary.copy(alpha = 0.72f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/* ======================== GUÍA ======================== */

/**
 * Guía del Lobby: arriba los canales (en la TV van a la izquierda) y abajo, en un panel, la programación de hoy del
 * elegido, con lo que está al aire marcado. Tocar lo que está al aire reproduce el canal; un programa ya emitido que
 * Panaccess deja ver en diferido se puede ver de nuevo.
 */
@Composable
internal fun LobbyGuideList(
    model: ContentModel,
    text: UiText,
    lobby: LobbyText,
    now: Long,
    onWatchLive: (Channel) -> Unit,
    onWatchCatchup: (Channel, Program) -> Unit,
) {
    val result = model.channels ?: return LobbyMessage(text.loading)
    val channels = result.channels
    if (channels.isEmpty()) return LobbyMessage(result.failure?.let { "${text.noChannels} ($it)" } ?: text.noChannels)
    val c = HospitalityTheme.colors
    var selected by remember { mutableStateOf(channels.first().id) }
    val ch = channels.firstOrNull { it.id == selected } ?: channels.first()
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    fun range(p: Program) = "${time.format(Date(p.startEpochSec * 1000))} – ${time.format(Date(p.endEpochSec * 1000))}"

    Column(Modifier.fillMaxSize()) {
        // Canales: el logo sobre blanco y el número; el elegido con el borde del color de acento.
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
            items(channels, key = { it.id }) { channel ->
                val on = channel.id == selected
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) c.textPrimary.copy(alpha = 0.08f) else Color.Transparent)
                        .border(if (on) 2.dp else 0.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { selected = channel.id }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChannelBadge(channel, 56, 36)
                    Text("${channel.number}", style = font(11.sp, FontWeight.SemiBold), color = if (on) c.accent else c.textPrimary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp).fillMaxSize().clip(RoundedCornerShape(rad(14))).background(c.surface).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelBadge(ch, 64, 40)
                Column {
                    Kicker(lobby.today)
                    Text("${ch.number} · ${ch.name}", style = font(18.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val programs by produceState(model.cachedSchedule(ch.id), ch.id) { value = model.schedule(ch.id) }
            Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
                val list = programs
                when {
                    list == null -> LobbyMessage(text.loading)
                    list.isEmpty() -> LobbyMessage(text.noSchedule)
                    else -> {
                        val nowIndex = list.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
                        val state = remember(ch.id, list.size) { androidx.compose.foundation.lazy.LazyListState((nowIndex - 1).coerceAtLeast(0)) }
                        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            itemsIndexed(list, key = { i, p -> "$i/${p.startEpochSec}" }) { _, p ->
                                val live = p.isLive(now)
                                val again = p.isPast(now) && p.catchupId != null
                                ListRow(
                                    onClick = { if (live) onWatchLive(ch) else if (again) onWatchCatchup(ch, p) },
                                    selected = live,
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                ) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(range(p), style = font(11.5.sp, if (live) FontWeight.SemiBold else FontWeight.Normal),
                                            color = if (live) c.accent else c.textPrimary.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.width(98.dp))
                                        Text(
                                            p.title, style = font(13.sp, if (live) FontWeight.SemiBold else FontWeight.Normal),
                                            color = if (p.isPast(now) && !again) c.textPrimary.copy(alpha = 0.45f) else c.textPrimary,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                        )
                                        when {
                                            live -> Text(text.nowLabel.uppercase(), style = font(9.5.sp, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp), color = c.accent)
                                            again -> Text("↺ ${text.watchAgain}", style = font(10.sp, FontWeight.SemiBold), color = c.accent, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
