package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.brand.UiText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hourFormat = SimpleDateFormat("HH:mm", Locale.ROOT)
private val dayFormat = SimpleDateFormat("EEE d", Locale.getDefault())

fun Program.timeRange(): String = "${hourFormat.format(Date(startEpochSec * 1000))} – ${hourFormat.format(Date(endEpochSec * 1000))}"

/**
 * Guía en dos columnas: canales a la izquierda; a la derecha la programación del canal elegido,
 * ya posicionada en lo que se emite ahora.
 *
 * Elegir un canal (OK o toque) NO lo reproduce: muestra su programación y lleva el foco a lo que
 * se emite ahora. Se reproduce desde la columna de programas: en vivo → abre el canal; ya emitido
 * con catchup → "ver de nuevo". Con el control, mover el foco por los canales también cambia la guía.
 */
@Composable
fun GuideScreen(
    source: EntertainmentSource,
    channels: List<Channel>,
    text: UiText,
    focusChannel: String?,
    onChannelFocused: (String) -> Unit,
    onWatchLive: (channelId: String) -> Unit,
    onWatchCatchup: (Program) -> Unit,
) {
    val colors = HospitalityTheme.colors
    if (channels.isEmpty()) return CenteredMessage(text.noChannels)
    var selected by remember { mutableStateOf(focusChannel?.takeIf { id -> channels.any { it.id == id } } ?: channels.first().id) }
    var schedule by remember { mutableStateOf<List<Program>?>(null) }
    // Pedido de "llevá el foco a lo que se emite ahora" cuando cargue la programación.
    var jumpToPrograms by remember { mutableStateOf(false) }
    val nowFocus = remember { FocusRequester() }
    val first = rememberInitialFocus()
    val startIndex = channels.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val channelListState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    // Se espera un instante antes de pedir la guía: recorriendo canales con el control no tiene
    // sentido bajar la de cada uno por el que se pasa.
    LaunchedEffect(selected) {
        schedule = null
        delay(250)
        schedule = source.schedule(selected)
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text.guide, style = HospitalityTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(start = 12.dp))
            LazyColumn(state = channelListState, contentPadding = FocusListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                    FocusCard(
                        onClick = {
                            if (selected != channel.id) { selected = channel.id; onChannelFocused(channel.id) }
                            jumpToPrograms = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .then(if (index == startIndex) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.isFocused && selected != channel.id) { selected = channel.id; onChannelFocused(channel.id) } },
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${channel.number}", style = HospitalityTheme.typography.label, color = colors.accent, modifier = Modifier.width(44.dp))
                            Text(channel.name, style = HospitalityTheme.typography.label, color = if (channel.id == selected) colors.textPrimary else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        val programs = schedule
        when {
            programs == null -> CenteredMessage(text.loading)
            programs.isEmpty() -> CenteredMessage(text.noSchedule)
            else -> {
                val now = System.currentTimeMillis() / 1000
                val nowIndex = programs.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
                val state = rememberLazyListState(initialFirstVisibleItemIndex = nowIndex)
                LaunchedEffect(programs, jumpToPrograms) {
                    if (!jumpToPrograms) return@LaunchedEffect
                    withFrameNanos { }
                    runCatching { nowFocus.requestFocus() }
                    // Recién después: cambiar la clave antes cancelaba esta corrutina y el foco no se movía.
                    jumpToPrograms = false
                }
                LazyColumn(state = state, contentPadding = PaddingValues(top = 52.dp, bottom = 12.dp, start = 12.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(programs) { index, program ->
                        ProgramRow(program, now, text, if (index == nowIndex) Modifier.focusRequester(nowFocus) else Modifier) {
                            when {
                                program.isLive(now) -> onWatchLive(selected)
                                program.isPast(now) && program.catchupId != null -> onWatchCatchup(program)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(program: Program, now: Long, text: UiText, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val live = program.isLive(now)
    val canWatchAgain = program.isPast(now) && program.catchupId != null
    val badge = when {
        live -> text.nowLabel
        canWatchAgain -> text.watchAgain
        else -> null
    }
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(76.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(program.timeRange(), style = HospitalityTheme.typography.label, color = if (live) colors.accent else colors.textSecondary)
                Text(dayFormat.format(Date(program.startEpochSec * 1000)), style = HospitalityTheme.typography.body, color = colors.textMuted)
            }
            Column(Modifier.weight(1f)) {
                Text(program.title, style = HospitalityTheme.typography.label, color = if (program.isPast(now) && !canWatchAgain) colors.textMuted else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                program.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = HospitalityTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            badge?.let { Text(it, style = HospitalityTheme.typography.label, color = colors.accent, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}
