package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import com.networkbroadcast.hospitality.services.localized
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * PRUEBA de la dirección "Lobby" (rama prueba/lobby): pantallas internas sin filas que se
 * desplazan de costado, como el prototipo Papeleria/hospitality-tv/lobby.html (v5). Se usan solo
 * con "direction": "LOBBY" en brand.json; el resto de las marcas siguen con las de siempre.
 */

private fun font(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = HospitalityBodyFamily, fontSize = size, fontWeight = weight)

private val glassColor = Color(0x9E0C1714)
private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun Program.range() = "${hhmm.format(Date(startEpochSec * 1000))} – ${hhmm.format(Date(endEpochSec * 1000))}"

/** Marco común: velo sobre la foto del hotel, título grande arriba a la izquierda y la hora. */
@Composable
fun LobbyFrame(title: String, content: @Composable BoxScope.() -> Unit) {
    val c = HospitalityTheme.colors
    val now = remember { Date() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xD9080E0C), 1f to Color(0xF0080E0C)))) {
        Text("‹ Atrás", style = font(10.sp), color = c.textPrimary.copy(alpha = 0.6f), modifier = Modifier.offset(x = 48.dp, y = 24.dp))
        Text(title, style = font(32.sp, FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.offset(x = 48.dp, y = 38.dp))
        Text(hhmm.format(now), style = font(20.sp, FontWeight.Bold), color = c.textPrimary, modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 48.dp))
        Box(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 98.dp, bottom = 24.dp), content = content)
    }
}

@Composable
private fun Kicker(s: String, color: Color = HospitalityTheme.colors.textPrimary) =
    Text(s.uppercase(), style = font(8.5.sp, FontWeight.Bold).copy(letterSpacing = 1.8.sp), color = color.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun ChannelBadge(ch: Channel, w: Int = 56, h: Int = 36) {
    Box(Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(3.dp), contentAlignment = Alignment.Center) {
        if (ch.logoUrl != null) AsyncImage(model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Text("${ch.number}", style = font(13.sp, FontWeight.Bold), color = Color(0xFF14100A))
    }
}

/** Filas de lista: sin fondo en reposo; con foco, fondo suave y una barra del color de acento. */
@Composable
private fun ListRow(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, onFocus: () -> Unit = {}, content: @Composable BoxScope.(Boolean) -> Unit) {
    val base = HospitalityTheme.colors
    CompositionLocalProvider(LocalHospitalityColors provides base.copy(surface = if (selected) Color(0x14F3EEE4) else Color.Transparent, surfaceBorder = Color.Transparent, textPrimary = Color.Transparent)) {
        FocusCard(onClick = onClick, shape = RoundedCornerShape(8.dp), modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() }) { f ->
            if (f) Box(Modifier.fillMaxSize().background(Color(0x1FF3EEE4)))
            if (f) Box(Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(base.accent))
            content(f)
        }
    }
}

/* ======================== TV EN VIVO ======================== */

@Composable
fun LobbyLiveTv(channels: ChannelsResult?, source: EntertainmentSource, text: UiText, focusOn: String?, onPlay: (String) -> Unit) {
    val result = channels ?: return CenteredMessage(text.loading)
    if (result.channels.isEmpty()) return CenteredMessage(text.noChannels)
    val byId = result.channels.associateBy { it.id }
    val groups = listOf(text.liveTv to result.channels) +
        result.groups.map { g -> g.name to g.channelIds.mapNotNull(byId::get) }.filter { it.second.isNotEmpty() }
    var group by remember { mutableStateOf(0) }
    val list = groups[group.coerceIn(groups.indices)].second
    var focused by remember { mutableStateOf(byId[focusOn] ?: list.first()) }
    val first = rememberInitialFocus(group)
    val accent = HospitalityTheme.colors.accent
    val c = HospitalityTheme.colors

    LobbyFrame(text.liveTv) {
        // Categorías (los bouquets del operador): elegir una cambia la lista.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.take(9).forEachIndexed { i, (name, _) ->
                val sel = i == group
                CompositionLocalProvider(LocalHospitalityColors provides c.copy(surface = if (sel) Color(0x29F3EEE4) else Color(0x14F3EEE4), surfaceBorder = Color.Transparent)) {
                    FocusCard(onClick = { group = i }, shape = RoundedCornerShape(50), modifier = Modifier.height(30.dp)) { f ->
                        if (f) Box(Modifier.fillMaxSize().background(accent))
                        Text(if (i == 0) "Todos" else name, style = font(11.sp, FontWeight.SemiBold), color = if (f) Color(0xFF14100A) else c.textPrimary,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(top = 44.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(list, key = { it.id }) { ch ->
                    val target = ch.id == (focusOn?.takeIf { id -> list.any { it.id == id } } ?: list.first().id)
                    ListRow(onClick = { onPlay(ch.id) }, onFocus = { focused = ch },
                        modifier = Modifier.fillMaxWidth().height(52.dp).then(if (target) Modifier.focusRequester(first) else Modifier)) {
                        val now by produceState<Program?>(null, ch.id) { value = runCatching { source.schedule(ch.id).firstOrNull { it.isLive(System.currentTimeMillis() / 1000) } }.getOrNull() }
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChannelBadge(ch)
                            Column(Modifier.weight(1f)) {
                                Text("${ch.number} · ${ch.name}", style = font(13.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(now?.title ?: " ", style = font(10.5.sp), color = c.textPrimary.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            LivePreview(focused, source, text, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LivePreview(ch: Channel, source: EntertainmentSource, text: UiText, modifier: Modifier) {
    val c = HospitalityTheme.colors
    val schedule by produceState<List<Program>>(emptyList(), ch.id) { value = runCatching { source.schedule(ch.id) }.getOrDefault(emptyList()) }
    val nowSec = System.currentTimeMillis() / 1000
    val i = schedule.indexOfFirst { it.isLive(nowSec) }
    val now = schedule.getOrNull(i)
    val next = if (i >= 0) schedule.getOrNull(i + 1) else null
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(254.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B3A5C), Color(0xFF0B0F1A), Color(0xFF5A3020))))) {
            now?.imageUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xB3050807), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xCC050807))))
            if (now?.imageUrl == null) Box(Modifier.align(Alignment.Center)) { ChannelBadge(ch, 160, 96) }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                Spacer(Modifier.width(7.dp))
                Kicker("${text.liveNow} · ${ch.number} ${ch.name}")
            }
        }
        Kicker(now?.let { "Ahora · ${it.range()}" } ?: " ", c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(now?.title ?: ch.name, style = font(24.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        now?.let {
            val p = ((nowSec - it.startEpochSec).toFloat() / (it.endEpochSec - it.startEpochSec).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33F3EEE4))) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(c.accent))
            }
        }
        next?.let { Text("Después: ${it.title} · ${hhmm.format(Date(it.startEpochSec * 1000))}", style = font(12.sp), color = c.textPrimary.copy(alpha = 0.72f), modifier = Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/* ======================== PELÍCULAS Y SERIES ======================== */

@Composable
fun LobbyVod(vod: VodResult?, text: UiText, focusOn: String?, onOpen: (VodItem) -> Unit) {
    val result = vod ?: return CenteredMessage(text.loading)
    val shelves = result.shelves.filter { it.items.isNotEmpty() }
    if (shelves.isEmpty()) return CenteredMessage(text.noVod)
    val all = shelves.flatMap { it.items }.distinctBy { it.id }
    var hero by remember { mutableStateOf(all.firstOrNull { it.id == focusOn } ?: all.first()) }
    val first = rememberInitialFocus(Unit)
    val c = HospitalityTheme.colors
    LobbyFrame(text.moviesAndSeries) {
        Column(Modifier.fillMaxWidth().height(104.dp)) {
            Kicker(listOfNotNull(hero.year?.toString(), hero.durationMinutes?.let { "$it ${text.minutes}" }).joinToString(" · ").ifEmpty { " " })
            Text(hero.title, style = font(26.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            hero.synopsis?.let { Text(it, style = font(11.sp), color = c.textPrimary.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp)) }
        }
        LazyVerticalGrid(GridCells.Fixed(9), Modifier.fillMaxSize().padding(top = 112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
            items(all, key = { it.id }) { item ->
                val target = item.id == (focusOn ?: all.first().id)
                FocusCard(onClick = { onOpen(item) }, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(140.dp).onFocusChanged { if (it.isFocused) hero = item }.then(if (target) Modifier.focusRequester(first) else Modifier)) {
                    item.posterUrl?.let { AsyncImage(model = it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    BottomScrim()
                    Text(item.title, style = font(10.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }
    }
}

/* ======================== EL HOTEL ======================== */

/** "El hotel" como mosaico: el primer servicio grande y el resto en una grilla de dos columnas. */
@Composable
fun LobbyHotel(widgets: List<com.networkbroadcast.hospitality.panaccess.HomeWidget>, text: UiText, onOpen: (com.networkbroadcast.hospitality.panaccess.HomeWidget) -> Unit) {
    if (widgets.isEmpty()) return CenteredMessage(text.aboutHotel)
    val first = rememberInitialFocus(Unit)
    val c = HospitalityTheme.colors
    @Composable
    fun Tile(w: com.networkbroadcast.hospitality.panaccess.HomeWidget, big: Boolean, modifier: Modifier) {
        FocusCard(onClick = { onOpen(w) }, shape = RoundedCornerShape(12.dp), modifier = modifier) {
            AsyncImage(model = com.networkbroadcast.hospitality.brand.imageModel(w.imageUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xEB050807))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Kicker(listOfNotNull(w.hours, w.floor?.let { "Piso $it" }).joinToString(" · ").ifEmpty { " " }, c.accent)
                Text(w.title, style = font(if (big) 40.sp else 22.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (big) w.description?.let { Text(it, style = font(12.sp), color = c.textPrimary.copy(alpha = 0.8f), maxLines = 2) }
            }
        }
    }
    LobbyFrame(text.aboutHotel) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(widgets.first(), true, Modifier.weight(1f).fillMaxHeight().focusRequester(first))
            if (widgets.size > 1) {
                val rest = widgets.drop(1).take(4)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rest.chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { Tile(it, false, Modifier.weight(1f).fillMaxHeight()) }
                            if (pair.size == 1 && rest.size > 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ======================== GUÍA ======================== */

/**
 * Guía con la misma lógica que GuideScreen (canales a la izquierda; al moverse por los canales
 * cambia la programación; OK lleva el foco a lo que se emite ahora), con el lenguaje del Lobby:
 * filas sin tarjeta y la programación en un panel translúcido.
 */
@Composable
fun LobbyGuide(
    source: EntertainmentSource,
    channels: List<Channel>,
    text: UiText,
    title: String,
    catchupProgramsByChannel: Map<String, List<Program>>?,
    focusChannel: String?,
    onChannelFocused: (String) -> Unit,
    onWatchLive: (String) -> Unit,
    onWatchCatchup: (Program) -> Unit,
    onProgramFocused: (Program) -> Unit,
) {
    if (channels.isEmpty()) return CenteredMessage(text.noChannels)
    val c = HospitalityTheme.colors
    var selected by remember { mutableStateOf(focusChannel?.takeIf { id -> channels.any { it.id == id } } ?: channels.first().id) }
    val schedules = remember { androidx.compose.runtime.mutableStateMapOf<String, List<Program>>() }
    var jump by remember { mutableStateOf(false) }
    val nowFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val first = rememberInitialFocus()
    val startIndex = channels.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    androidx.compose.runtime.LaunchedEffect(selected, catchupProgramsByChannel) {
        if (catchupProgramsByChannel != null) schedules[selected] = catchupProgramsByChannel[selected].orEmpty()
        else if (selected !in schedules) {
            kotlinx.coroutines.delay(150)
            if (selected !in schedules) schedules[selected] = source.schedule(selected)
        }
    }
    val ch = channels.first { it.id == selected }
    LobbyFrame(title) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            LazyColumn(Modifier.width(270.dp).fillMaxHeight(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(channels.size) { index ->
                    val channel = channels[index]
                    ListRow(
                        onClick = {
                            if (selected != channel.id) { selected = channel.id; onChannelFocused(channel.id) }
                            jump = true
                        },
                        selected = channel.id == selected,
                        onFocus = { if (selected != channel.id) { selected = channel.id; onChannelFocused(channel.id) } },
                        modifier = Modifier.fillMaxWidth().height(46.dp).then(if (index == startIndex) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelBadge(channel, 46, 30)
                            Text("${channel.number} · ${channel.name}", style = font(12.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(glassColor).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelBadge(ch, 64, 40)
                    Column {
                        Kicker("Hoy")
                        Text("${ch.number} · ${ch.name}", style = font(20.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val programs = schedules[selected]
                Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
                    when {
                        programs == null -> CenteredMessage(text.loading)
                        programs.isEmpty() -> CenteredMessage(text.noSchedule)
                        else -> {
                            val now = System.currentTimeMillis() / 1000
                            val nowIndex = if (catchupProgramsByChannel != null) 0 else programs.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
                            val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (nowIndex - 1).coerceAtLeast(0))
                            androidx.compose.runtime.LaunchedEffect(programs, jump) {
                                if (!jump) return@LaunchedEffect
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { nowFocus.requestFocus() }
                                jump = false
                            }
                            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(programs.size) { index ->
                                    val p = programs[index]
                                    val live = p.isLive(now)
                                    val again = p.isPast(now) && p.catchupId != null
                                    ListRow(
                                        onClick = {
                                            when {
                                                catchupProgramsByChannel != null && p.catchupId != null -> onWatchCatchup(p)
                                                live -> onWatchLive(selected)
                                                again -> onWatchCatchup(p)
                                            }
                                        },
                                        onFocus = { onProgramFocused(p) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp).then(if (index == nowIndex) Modifier.focusRequester(nowFocus) else Modifier),
                                    ) {
                                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(p.range(), style = font(11.5.sp, if (live) FontWeight.Bold else FontWeight.Normal), color = if (live) c.accent else c.textPrimary.copy(alpha = 0.75f), modifier = Modifier.width(110.dp))
                                            Text(
                                                p.title, style = font(13.sp, if (live) FontWeight.SemiBold else FontWeight.Normal),
                                                color = if (p.isPast(now) && !again) c.textPrimary.copy(alpha = 0.45f) else c.textPrimary,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                            )
                                            when {
                                                live -> Text(text.nowLabel.uppercase(), style = font(9.5.sp, FontWeight.Bold).copy(letterSpacing = 1.5.sp), color = c.accent)
                                                again -> Text("↺ ${text.watchAgain}", style = font(10.sp, FontWeight.SemiBold), color = c.accent)
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
}

/* ======================== PEDIR A LA HABITACIÓN ======================== */

/** Categorías a la izquierda (y "Mis pedidos"); a la derecha, los productos de la categoría enfocada. */
@Composable
fun LobbyServices(
    services: com.networkbroadcast.hospitality.services.GuestServices,
    text: com.networkbroadcast.hospitality.services.ServicesText,
    language: String,
    focusOn: String?,
    onOpenItem: (com.networkbroadcast.hospitality.services.ServiceItem) -> Unit,
    onMyOrders: () -> Unit,
) {
    val c = HospitalityTheme.colors
    val catalog by produceState<com.networkbroadcast.hospitality.services.ServiceCatalog?>(null) { value = services.catalog() }
    val orders by services.orders.collectAsState()
    val current = catalog ?: return CenteredMessage("…")
    val room = com.networkbroadcast.hospitality.services.OrderLocation.room("")
    val cats = current.categories.map { k -> k to k.items.filter { it.availableIn(room) } }.filter { it.second.isNotEmpty() }
    if (cats.isEmpty()) return CenteredMessage(text.title)
    var cat by remember { mutableStateOf(cats.indexOfFirst { (_, items) -> items.any { it.id == focusOn } }.coerceAtLeast(0)) }
    val first = rememberInitialFocus(Unit)
    LobbyFrame(text.title) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cats.forEachIndexed { i, (k, _) ->
                    ListRow(
                        onClick = { cat = i }, selected = i == cat, onFocus = { cat = i },
                        modifier = Modifier.fillMaxWidth().height(44.dp).then(if (i == cat) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Text(
                            k.name.localized(language), style = font(13.sp, FontWeight.SemiBold), color = c.textPrimary,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                val open = orders.count { it.status.isOpen }
                ListRow(onClick = onMyOrders, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text.myOrders, style = font(13.sp, FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1f))
                        if (open > 0) Text("$open", style = font(12.sp, FontWeight.Bold), color = c.accent)
                    }
                }
                Text(text.subtitleTv, style = font(10.sp), color = c.textPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(top = 14.dp, start = 16.dp))
            }
            val items = cats[cat.coerceIn(cats.indices)].second
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    FocusCard(onClick = { onOpenItem(item) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(176.dp)) {
                        val url = item.imageUrl
                        if (url != null) AsyncImage(model = com.networkbroadcast.hospitality.brand.imageModel(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(Color(0x14F3EEE4)))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6050807))))
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Text(item.name.localized(language), style = font(14.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(com.networkbroadcast.hospitality.services.formatPrice(item.price, current.currency, language, text.free), style = font(11.sp), color = c.accent)
                        }
                    }
                }
            }
        }
    }
}

/* ======================== FICHA DE UN SERVICIO ======================== */

/** Foto a pantalla completa con un velo desde la izquierda; izquierda/derecha cambian de foto. */
@Composable
fun LobbyWidgetDetail(widget: com.networkbroadcast.hospitality.panaccess.HomeWidget, text: UiText) {
    val c = HospitalityTheme.colors
    var photo by remember(widget) { mutableStateOf(0) }
    val gallery = widget.gallery.ifEmpty { listOf(widget.imageUrl) }
    val first = rememberInitialFocus()
    Box(
        Modifier.fillMaxSize().focusRequester(first)
            .onKeyEvent { e ->
                if (gallery.size <= 1 || e.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { photo = (photo - 1 + gallery.size) % gallery.size; true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { photo = (photo + 1) % gallery.size; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AsyncImage(model = com.networkbroadcast.hospitality.brand.imageModel(gallery[photo]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF0080E0C), 0.48f to Color(0x99080E0C), 1f to Color(0x0D080E0C))))
        Text("‹ Atrás", style = font(10.sp), color = c.textPrimary.copy(alpha = 0.6f), modifier = Modifier.offset(x = 48.dp, y = 24.dp))
        Column(Modifier.offset(x = 48.dp, y = 90.dp).width(430.dp)) {
            Kicker(listOfNotNull(widget.hours, widget.floor?.let { "${text.floorLabel} $it" }).joinToString(" · ").ifEmpty { " " }, c.accent)
            Text(widget.title, style = font(56.sp, FontWeight.SemiBold), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            widget.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = font(15.sp).copy(lineHeight = 22.sp), color = c.textPrimary.copy(alpha = 0.82f), modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (gallery.size > 1) {
            Row(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(gallery.size) { i -> Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == photo) c.textPrimary else c.textPrimary.copy(alpha = 0.35f))) }
                Text("◀ ▶ fotos", style = font(10.sp), color = c.textPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/* ======================== CARTEL DEL REPRODUCTOR ======================== */

/** Lo que se ve al zapear: logo del canal, el programa en grande, progreso y lo que sigue. */
@Composable
fun LobbyPlayerInfo(channel: Channel, now: Program?, next: Program?, accent: Color) {
    val ink = Color(0xFFF3EEE4)
    val nowSec = System.currentTimeMillis() / 1000
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChannelBadge(channel, 84, 52)
        Column(Modifier.weight(1f)) {
            Text("${channel.number} · ${channel.name}".uppercase() + (now?.let { "  ·  ${it.range()}" } ?: ""), style = font(10.sp, FontWeight.Bold).copy(letterSpacing = 1.8.sp), color = ink.copy(alpha = 0.75f), maxLines = 1)
            Text(now?.title ?: channel.name, style = font(30.sp, FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    now?.let {
        val p = ((nowSec - it.startEpochSec).toFloat() / (it.endEpochSec - it.startEpochSec).coerceAtLeast(1)).coerceIn(0f, 1f)
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33F3EEE4))) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(accent))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(next?.let { "Después: ${it.title} · ${hhmm.format(Date(it.startEpochSec * 1000))}" } ?: " ", style = font(12.sp), color = ink.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("▲ ▼ cambiar canal · OK información · Atrás para salir", style = font(10.sp), color = ink.copy(alpha = 0.55f))
    }
}
