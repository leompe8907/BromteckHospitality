package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.OperatorCardColors
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.tv.R
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
    /** "Guía" o "Eventos anteriores": según cuál de las dos tarjetas de la home abrió esta misma
     *  pantalla (HomeAction.Guide vs HomeAction.CatchUp comparten Screen.Guide). */
    title: String = text.guide,
    /** Cuando no es null, muestra estos eventos de catchup en vez del EPG normal. */
    catchupProgramsByChannel: Map<String, List<Program>>? = null,
    emptyChannelsMessage: String? = null,
    focusChannel: String?,
    onChannelFocused: (String) -> Unit,
    onWatchLive: (channelId: String) -> Unit,
    onWatchCatchup: (Program) -> Unit,
    /** El evento que el huésped tiene enfocado ahora, para adelantar lo que haga falta (la URL del
     *  player en los eventos con catchup). Se llama en cada movimiento del foco: quien lo reciba
     *  debe esperar un poco antes de hacer trabajo. */
    onProgramFocused: (Program) -> Unit = {},
    /** "#163F42", color de card configurado por Delancer (OperatorConfig.cardColorHex); mismo
     *  criterio que en la home (HomeScreen.cardColorHex) -- null = el de la marca. */
    cardColorHex: String? = null,
    /** Ídem para el color de letra (OperatorConfig.textColorHex); null = el que surge del
     *  contraste de [cardColorHex], o el de la marca. */
    textColorHex: String? = null,
    /** X_APP_CONFIRM_COLOR (OperatorConfig.confirmColorHex): "Ver de nuevo", "Ahora" y el horario
     *  del programa al aire -- los tres avisos de "esto se puede ver". Null = el de la marca. */
    confirmColorHex: String? = null,
) {
    if (channels.isEmpty()) return CenteredMessage(emptyChannelsMessage ?: text.noChannels)
    val accentColor = HospitalityColors.parseHex(confirmColorHex)
    OperatorCardColors(cardColorHex, textColorHex) {
    val colors = HospitalityTheme.colors
    var selected by remember { mutableStateOf(focusChannel?.takeIf { id -> channels.any { it.id == id } } ?: channels.first().id) }
    // Mantener las guías ya visitadas en memoria mientras esta pantalla siga abierta. Antes cada
    // cambio de canal borraba la lista actual y, al volver, la pedía de nuevo aunque ya estuviera
    // precargada en disco.
    val schedulesByChannel = remember { mutableStateMapOf<String, List<Program>>() }
    // Pedido de "llevá el foco a lo que se emite ahora" cuando cargue la programación.
    var jumpToPrograms by remember { mutableStateOf(false) }
    val nowFocus = remember { FocusRequester() }
    val first = rememberInitialFocus()
    val startIndex = channels.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val channelListState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    // Priorizar el canal seleccionado y conservar los demás en la caché local de esta pantalla.
    // Catch Up ya llega como catálogo completo desde Panaccess.
    LaunchedEffect(selected, catchupProgramsByChannel) {
        if (catchupProgramsByChannel != null) {
            schedulesByChannel[selected] = catchupProgramsByChannel[selected].orEmpty()
        } else if (selected !in schedulesByChannel) {
            delay(150)
            if (selected !in schedulesByChannel) {
                schedulesByChannel[selected] = source.schedule(selected)
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = HospitalityTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(start = 12.dp))
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
                            Text("${channel.number}", style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.width(36.dp))
                            ChannelLogo(channel.logoUrl, modifier = Modifier.padding(end = 10.dp))
                            Text(channel.name, style = HospitalityTheme.typography.label, color = if (channel.id == selected) colors.textPrimary else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        val programs = schedulesByChannel[selected]
        when {
            programs == null -> CenteredMessage(text.loading)
            programs.isEmpty() -> CenteredMessage(text.noSchedule)
            else -> {
                val now = System.currentTimeMillis() / 1000
                val nowIndex = if (catchupProgramsByChannel != null) 0 else programs.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
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
                        ProgramRow(
                            program,
                            now,
                            text,
                            (if (index == nowIndex) Modifier.focusRequester(nowFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) onProgramFocused(program) },
                            accentColor = accentColor,
                        ) {
                            when {
                                catchupProgramsByChannel != null && program.catchupId != null -> onWatchCatchup(program)
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
}

/**
 * Logo del canal en la lista de la guía, con el mismo criterio que la app de streaming hermana
 * (androidpanaccessapp, CatalogComponents.kt#ChannelLogo): el respaldo (ícono genérico) se dibuja
 * siempre por debajo y sólo se oculta cuando la imagen terminó de cargar -- si se dibujara nomás
 * mientras `logoUrl == null`, un logo transparente o que no llena el recuadro dejaría ver un hueco.
 */
@Composable
private fun ChannelLogo(logoUrl: String?, modifier: Modifier = Modifier) {
    val colors = HospitalityTheme.colors
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    Box(modifier.size(width = 44.dp, height = 32.dp), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Icon(
                painterResource(R.drawable.ic_live), contentDescription = null,
                tint = colors.textMuted, modifier = Modifier.size(18.dp),
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageModel(logoUrl), contentDescription = null, contentScale = ContentScale.Fit,
                onSuccess = { loaded = true }, onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProgramRow(
    program: Program,
    now: Long,
    text: UiText,
    modifier: Modifier,
    /** X_APP_CONFIRM_COLOR: los dos avisos de acción de la fila ("Ver de nuevo", "Ahora") y el
     *  horario del programa que está al aire. Null = el accent de la marca. */
    accentColor: Color? = null,
    onClick: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val highlight = accentColor ?: colors.accent
    val live = program.isLive(now)
    // El cartel sale de lo que el programa realmente permite, no de qué pantalla lo abrió: con
    // `catchupOnly ||` (ver 3694743) "Eventos anteriores" lo ponía en todas las filas por igual,
    // así que dejaba de informar nada.
    val canWatchAgain = program.isPast(now) && program.catchupId != null
    // "Ahora" primero: lo que está al aire se avisa como tal aunque además tenga catchup.
    val badge = when {
        live -> text.nowLabel
        canWatchAgain -> text.watchAgain
        else -> null
    }
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(76.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                Text(program.timeRange(), style = HospitalityTheme.typography.label, color = if (live) highlight else colors.textSecondary)
                Text(dayFormat.format(Date(program.startEpochSec * 1000)), style = HospitalityTheme.typography.body, color = colors.textMuted)
            }
            Column(Modifier.weight(1f)) {
                Text(program.title, style = HospitalityTheme.typography.label, color = if (program.isPast(now) && !canWatchAgain) colors.textMuted else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                program.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = HospitalityTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            badge?.let { Text(it, style = HospitalityTheme.typography.label, color = highlight, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}
