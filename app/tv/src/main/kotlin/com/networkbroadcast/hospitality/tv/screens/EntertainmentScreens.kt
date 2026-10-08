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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.brand.UiText

internal val ScreenPadding = PaddingValues(horizontal = 52.dp, vertical = 36.dp)

/**
 * TV en vivo: una fila por bouquet, como la home del base. Si el operador no tiene bouquets, una
 * sola fila con todos los canales. OK abre el player en ese canal.
 */
@Composable
fun LiveTvScreen(channels: ChannelsResult?, text: UiText, focusOn: String?, onPlay: (channelId: String) -> Unit) {
    val colors = HospitalityTheme.colors
    val result = channels ?: return CenteredMessage(text.loading)
    if (result.channels.isEmpty()) return CenteredMessage(result.failure?.let { "${text.noChannels} ($it)" } ?: text.noChannels)
    val byId = result.channels.associateBy { it.id }
    val rows = result.groups.ifEmpty { listOf(ChannelGroup(text.liveTv, result.channels.map { it.id })) }
        .map { group -> group.name to group.channelIds.mapNotNull(byId::get) }
        .filter { it.second.isNotEmpty() }
    val target = focusOn?.takeIf { it in byId } ?: rows.first().second.first().id
    // El mismo canal puede estar en varios bouquets: el foco va a su primera aparición.
    val targetRow = rows.indexOfFirst { (_, members) -> members.any { it.id == target } }
    val first = rememberInitialFocus(target)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(text.liveTv, style = HospitalityTheme.typography.title, color = colors.textPrimary) }
        itemsIndexed(rows) { rowIndex, (name, members) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(name, Modifier.padding(start = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = FocusListPadding) {
                    items(members, key = { it.id }) { channel ->
                        val isTarget = rowIndex == targetRow && channel.id == target
                        ChannelCard(channel, if (isTarget) Modifier.focusRequester(first) else Modifier) { onPlay(channel.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, modifier = modifier.size(width = 200.dp, height = 120.dp)) {
        if (channel.logoUrl != null) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = channel.name, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 26.dp).size(width = 130.dp, height = 60.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${channel.number}", style = HospitalityTheme.typography.label, color = colors.accent, modifier = Modifier.padding(end = 8.dp))
            Text(channel.name, style = HospitalityTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Películas y series: una fila por categoría, con pósters. OK abre la ficha. */
@Composable
fun VodScreen(vod: VodResult?, text: UiText, focusOn: String?, onOpen: (VodItem) -> Unit) {
    val colors = HospitalityTheme.colors
    val result = vod ?: return CenteredMessage(text.loading)
    val shelves = result.shelves.filter { it.items.isNotEmpty() }
    if (shelves.isEmpty()) return CenteredMessage(result.failure?.let { "${text.noVod} ($it)" } ?: text.noVod)
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
                                AsyncImage(model = it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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

/** Ficha de un título: imagen, sinopsis y "Reproducir". */
@Composable
fun VodDetailScreen(item: VodItem, text: UiText, onPlay: () -> Unit) {
    val colors = HospitalityTheme.colors
    val first = rememberInitialFocus()
    Box(Modifier.fillMaxSize()) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.75f)))
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
