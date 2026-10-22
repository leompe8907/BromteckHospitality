package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.OperatorCardColors
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelGroupLayout
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.brand.UiText

internal val ScreenPadding = PaddingValues(horizontal = 52.dp, vertical = 36.dp)

/**
 * TV en vivo: una fila por bouquet, como la home del base. Si el operador no tiene bouquets, una
 * sola fila con todos los canales. OK abre el player en ese canal.
 */
@Composable
fun LiveTvScreen(
    channels: ChannelsResult?,
    source: EntertainmentSource,
    text: UiText,
    focusOn: String?,
    onPlay: (channelId: String) -> Unit,
) {
    val colors = HospitalityTheme.colors
    val result = channels ?: return CenteredMessage(text.loading)
    if (result.channels.isEmpty()) return CenteredMessage(result.failure?.let { "${text.noChannels} ($it)" } ?: text.noChannels)
    val byId = result.channels.associateBy { it.id }
    val rows = result.groups.ifEmpty { listOf(ChannelGroup(text.liveTv, result.channels.map { it.id })) }
        .map { group -> Triple(group.name, group.channelIds.mapNotNull(byId::get), group.layout) }
        .filter { it.second.isNotEmpty() }
    val target = focusOn?.takeIf { it in byId } ?: rows.first().second.first().id
    // El mismo canal puede estar en varios bouquets: el foco va a su primera aparición.
    val targetRow = rows.indexOfFirst { (_, members, _) -> members.any { it.id == target } }
    val first = rememberInitialFocus(target)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(text.liveTv, style = HospitalityTheme.typography.title, color = colors.textPrimary) }
        itemsIndexed(rows) { rowIndex, (name, members, layout) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(name, Modifier.padding(start = 12.dp))
                when (layout.type) {
                    "vertical_grid" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        members.chunked(layout.columns).forEach { gridRow ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                gridRow.forEach { channel ->
                                    val isTarget = rowIndex == targetRow && channel.id == target
                                    ChannelCard(
                                        channel = channel,
                                        layout = layout,
                                        source = source,
                                        modifier = Modifier.weight(1f).then(if (isTarget) Modifier.focusRequester(first) else Modifier),
                                        fillWidth = true,
                                        onClick = { onPlay(channel.id) },
                                    )
                                }
                                repeat((layout.columns - gridRow.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = FocusListPadding) {
                        val stackedRows = layout.rows.coerceAtLeast(1)
                        val columns = if (stackedRows == 1) members.map { listOf(it) } else members.chunked(stackedRows)
                        items(columns, key = { it.first().id }) { column ->
                            if (stackedRows > 1) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    column.forEach { channel ->
                                        val isTarget = rowIndex == targetRow && channel.id == target
                                        ChannelCard(channel, layout, source, if (isTarget) Modifier.focusRequester(first) else Modifier) { onPlay(channel.id) }
                                    }
                                }
                            } else {
                                val channel = column.first()
                                val isTarget = rowIndex == targetRow && channel.id == target
                                ChannelCard(channel, layout, source, if (isTarget) Modifier.focusRequester(first) else Modifier) { onPlay(channel.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    layout: ChannelGroupLayout,
    source: EntertainmentSource,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val design = layout.cardDesign
    val needsProgram = design.startsWith("event_") || design == "event_normal" || design == "event_big" || design == "channel_full_info"
    var currentProgram by remember(channel.id, needsProgram) { mutableStateOf<Program?>(null) }
    var programRequested by remember(channel.id, needsProgram) { mutableStateOf(false) }
    val isBig = design == "logo_big" || design == "event_big" || design == "event_and_logo" ||
        design == "event_and_logo_in_top" || design == "event_and_logo_in_bottom"
    val isFullInfo = design == "channel_full_info" || design == "event_line"
    val cardWidth = when { isFullInfo -> 420.dp; isBig -> 260.dp; else -> 200.dp }
    val cardHeight = when { isFullInfo -> 88.dp; isBig -> 160.dp; else -> 120.dp }
    val cardModifier = if (fillWidth) modifier.fillMaxWidth().height(cardHeight) else modifier.size(width = cardWidth, height = cardHeight)
    val usesEventImage = needsProgram && currentProgram?.imageUrl != null
    FocusCard(onClick = onClick, modifier = cardModifier) { focused ->
        LaunchedEffect(channel.id, needsProgram, focused) {
            // Solo el canal enfocado pide EPG. En vertical_grid se componen muchas tarjetas a la
            // vez; pedir todas las guías al entrar saturaría el CDN y retrasaría el primer foco.
            if (focused && needsProgram && !programRequested) {
                val now = System.currentTimeMillis() / 1000
                currentProgram = source.schedule(channel.id).firstOrNull { it.isLive(now) }
                programRequested = true
            }
        }
        if (usesEventImage) {
            AsyncImage(model = currentProgram?.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        } else if (channel.logoUrl != null) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = channel.name, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 26.dp).size(width = if (isBig) 170.dp else 130.dp, height = if (isBig) 88.dp else 60.dp),
            )
        }
        if (design == "event_and_logo" || design == "event_and_logo_in_top" || design == "event_and_logo_in_bottom") {
            channel.logoUrl?.let {
                AsyncImage(
                    model = it, contentDescription = channel.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(if (design == "event_and_logo_in_bottom") Alignment.BottomStart else Alignment.TopStart)
                        .padding(10.dp).size(width = 74.dp, height = 38.dp),
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${channel.number} · ${channel.name}", style = HospitalityTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (needsProgram) {
                    currentProgram?.let { program ->
                        Text("${program.timeRange()} · ${program.title}", style = HospitalityTheme.typography.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Películas y series: una fila por categoría, con pósters. OK abre la ficha. */
@Composable
fun VodScreen(
    vod: VodResult?,
    text: UiText,
    focusOn: String?,
    placeholderImageUrl: String? = null,
    /** "#163F42", color de card configurado por Delancer (OperatorConfig.cardColorHex); mismo
     *  criterio que en la home (HomeScreen.cardColorHex) -- null = el de la marca. */
    cardColorHex: String? = null,
    /** Ídem para el color de letra (OperatorConfig.textColorHex); null = el que surge del
     *  contraste de [cardColorHex], o el de la marca. */
    textColorHex: String? = null,
    onOpen: (VodItem) -> Unit,
) {
    val result = vod ?: return CenteredMessage(text.loading)
    val shelves = result.shelves.filter { it.items.isNotEmpty() }
    if (shelves.isEmpty()) return CenteredMessage(result.failure?.let { "${text.noVod} ($it)" } ?: text.noVod)
    OperatorCardColors(cardColorHex, textColorHex) {
    val colors = HospitalityTheme.colors
    val target = focusOn?.takeIf { id -> shelves.any { s -> s.items.any { it.id == id } } } ?: shelves.first().items.first().id
    val targetRow = shelves.indexOfFirst { s -> s.items.any { it.id == target } }
    val first = rememberInitialFocus(target)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(text.moviesAndSeries, style = HospitalityTheme.typography.title, color = colors.textPrimary) }
        itemsIndexed(shelves) { rowIndex, shelf ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shelf.title.isNotBlank()) SectionTitle(shelf.title, Modifier.padding(start = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = FocusListPadding) {
                    items(shelf.items, key = { it.id }) { item ->
                        val isTarget = rowIndex == targetRow && item.id == target
                        FocusCard(
                            onClick = { onOpen(item) },
                            modifier = Modifier.size(width = 150.dp, height = 220.dp)
                                .then(if (isTarget) Modifier.focusRequester(first) else Modifier),
                        ) {
                            item.posterUrl?.let {
                                val neutralPlaceholder = rememberPlaceholderPainter(colors, placeholderImageUrl)
                                AsyncImage(
                                    model = it,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    placeholder = neutralPlaceholder,
                                    error = neutralPlaceholder,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            BottomScrim()
                            Text(
                                item.title, style = HospitalityTheme.typography.label, color = colors.textPrimary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** Ficha de un título: imagen, sinopsis y "Reproducir". */
@Composable
fun VodDetailScreen(item: VodItem, text: UiText, onPlay: () -> Unit) {
    val colors = HospitalityTheme.colors
    val first = rememberInitialFocus()
    Box(Modifier.fillMaxSize()) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Negro neutro, no colors.background: ese es el verde/turquesa de la paleta Resort, y
            // tapaba el backdrop entero con un tinte de color en vez de solo oscurecerlo para leer
            // el texto encima (el "filtro verde" que se veía en toda la ficha de la película).
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 64.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(item.title, style = HospitalityTheme.typography.display, color = colors.textPrimary, modifier = Modifier.width(720.dp))
            item.durationMinutes?.let { Text("$it ${text.minutes}", style = HospitalityTheme.typography.label, color = colors.textSecondary) }
            item.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = HospitalityTheme.typography.body, color = colors.textSecondary,
                    maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(720.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            FocusCard(onClick = onPlay, modifier = Modifier.size(width = 220.dp, height = 60.dp).focusRequester(first)) {
                Text(text.play, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
