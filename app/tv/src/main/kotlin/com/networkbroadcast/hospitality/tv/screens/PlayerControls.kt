package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.tv.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Lo que el control remoto adelanta/retrocede con los botones, igual que SEEK_STEP_MS del player. */
private const val SEEK_SECONDS = 10

/** Lo que espera la barra sin que toquen nada antes de confirmar el salto. */
private const val SCRUB_COMMIT_MS = 500L

/** Aire adicional entre pausa/reproducir y los botones de 10 s, además del espaciado de la fila. */
private val PLAY_BUTTON_GAP = 16.dp

/**
 * Tinta del player. No se parametriza, a propósito: el player dibuja sobre un degradado siempre
 * oscuro, así que un color claro se lee igual y uno oscuro desaparecería. Parametrizarla sería
 * darle al operador una forma de romper la legibilidad sin ganar nada.
 */
internal val PlayerInk = Color(0xFFF2F6EC)

/** Riel de la barra de progreso: la misma tinta apenas insinuada, tampoco parametrizada. */
internal val PlayerTrack = PlayerInk.copy(alpha = 0.28f)

/**
 * Controles de reproducción sobre el degradado del player: barra de progreso arriba y, abajo,
 * retroceder 10 s · pausa/reproducir · avanzar 10 s a la izquierda, con audio/subtítulos separado
 * al borde opuesto. Es el patrón de los players de TV de las OTT grandes.
 *
 * Los íconos van siempre en [PlayerInk], no en un color del operador: así ningún valor que cargue
 * el hotel puede volverlos ilegibles. El único color parametrizado acá es [accentColor]
 * (X_APP_CONFIRM_COLOR), que pinta el tramo reproducido de la barra, su cursor y el aro de foco.
 *
 * Cada botón lleva su nombre debajo: a tres metros un aro de color no alcanza para saber sobre
 * qué se está parado, la palabra sí.
 */
@Composable
fun PlayerControls(
    playing: Boolean,
    accentColor: Color,
    text: UiText,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    /** false en vivo sin DVR: ahí no hay nada que adelantar, así que no se muestran esos botones. */
    seekable: Boolean = false,
    /** 0 = sin duración conocida (TV en vivo): sin barra de progreso ni tiempos. */
    position: Long = 0L,
    duration: Long = 0L,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    /** null = el stream no trae pistas para elegir, así que no se muestra ese botón. */
    onShowTracks: (() -> Unit)? = null,
    /**
     * Se llama con cada tecla que recibe el panel, la haya usado o no. La pantalla lo necesita para
     * reiniciar el temporizador que esconde el panel: buscar con la barra son teclas que no
     * disparan ninguna de las acciones de arriba, así que sin esto el panel se cerraba solo a los
     * 4 s en medio de una búsqueda -- y al reabrirse el foco volvía a pausa/reproducir, sacándolo
     * de la barra. Pasaba sólo reproduciendo, porque en pausa ese temporizador no corre.
     */
    onInteraction: () -> Unit = {},
) {
    val playFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val hasBar = duration > 0
    val seekLabel = "$SEEK_SECONDS s"
    // El panel aparece ya con el foco en pausa/reproducir, el botón principal: así el huésped
    // aprieta OK dos veces (uno abre el panel, otro pausa) en vez de buscar el foco con el D-pad.
    LaunchedEffect(Unit) { playFocus.requestRetrying() }

    Column(modifier.fillMaxWidth()) {
        if (hasBar) {
            SeekBar(
                position = position, duration = duration, accentColor = accentColor,
                label = text.seekBar, onSeekTo = onSeekTo, onInteraction = onInteraction,
                onDown = { runCatching { playFocus.requestFocus() } },
                modifier = Modifier.focusRequester(barFocus),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp)
                // Arriba/abajo no se delegan al buscador de foco: con la barra se va a ella, y sin
                // barra se descartan -- si se delegaran, el foco se iría al contenedor de la
                // pantalla, que con el panel abierto no hace nada con el OK (panel trabado).
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    // Cualquier tecla cuenta como actividad, incluidas las que sólo mueven el foco
                    // entre botones: si no, el panel se cierra encima del huésped.
                    onInteraction()
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (hasBar) runCatching { barFocus.requestFocus() }; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> true
                        else -> false
                    }
                },
            verticalAlignment = Alignment.Top,
        ) {
            // Transporte agrupado a la izquierda, bajo el título: lo que se usa siempre queda
            // donde ya está mirando el ojo.
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (seekable) {
                    ControlButton(
                        onClick = onSeekBack, label = seekLabel, description = text.rewind10,
                        accentColor = accentColor, icon = R.drawable.ic_rewind_10,
                    )
                    Spacer(Modifier.width(PLAY_BUTTON_GAP))
                }
                ControlButton(
                    onClick = onPlayPause,
                    label = if (playing) text.pause else text.play,
                    description = if (playing) text.pause else text.play,
                    accentColor = accentColor,
                    icon = if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                    modifier = Modifier.focusRequester(playFocus),
                )
                if (seekable) {
                    Spacer(Modifier.width(PLAY_BUTTON_GAP))
                    ControlButton(
                        onClick = onSeekForward, label = seekLabel, description = text.forward10,
                        accentColor = accentColor, icon = R.drawable.ic_forward_10,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Audio/subtítulos no toca la reproducción: va al borde opuesto, fuera de la tira de
            // transporte, para que no se apriete por error buscando el play.
            onShowTracks?.let {
                ControlButton(
                    onClick = it, label = text.subtitles, description = text.audioAndSubtitles,
                    accentColor = accentColor, icon = R.drawable.ic_subtitles,
                )
            }
        }
    }
}

/**
 * Barra de progreso navegable. Enfocada, izquierda/derecha mueven el cursor de a un 1% de la
 * película (nunca menos de 10 s) y el salto se confirma solo cuando el huésped deja de apretar
 * [SCRUB_COMMIT_MS]: así una búsqueda larga es un solo seek y no veinte, que en HLS significa
 * veinte recargas de buffer.
 */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    accentColor: Color,
    label: String,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Posición tentativa mientras se busca; null = la barra sigue a la reproducción.
    var scrub by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(scrub) {
        val target = scrub ?: return@LaunchedEffect
        delay(SCRUB_COMMIT_MS)
        onSeekTo(target)
    }
    // Una vez que el player llegó al punto pedido, la barra vuelve a seguir la reproducción.
    LaunchedEffect(position) {
        val target = scrub ?: return@LaunchedEffect
        if (abs(position - target) < 2_000) scrub = null
    }
    // Si el foco se va de la barra, no queda una búsqueda a medio confirmar.
    LaunchedEffect(focused) { if (!focused) scrub = null }

    val shown = (scrub ?: position).coerceIn(0L, duration)
    val progress = (shown.toFloat() / duration).coerceIn(0f, 1f)
    val step = (duration / 100).coerceAtLeast(SEEK_SECONDS * 1_000L)

    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                // Buscar con la barra es la interacción más larga del panel y la que menos
                // acciones dispara: sin avisar acá, el temporizador la cerraba por el medio.
                onInteraction()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { scrub = (shown - step).coerceAtLeast(0L); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { scrub = (shown + step).coerceAtMost(duration); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { onDown(); true }
                    // Arriba no lleva a ningún lado: se consume para que el foco no se escape.
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        val knob = 18.dp
        BoxWithConstraints(Modifier.fillMaxWidth().height(knob), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.fillMaxWidth().height(if (focused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp)).background(PlayerTrack),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accentColor))
            }
            // El cursor solo con foco: sin él la barra es un indicador, no un control.
            if (focused) {
                Box(
                    Modifier.offset(x = (maxWidth - knob) * progress).size(knob)
                        .clip(CircleShape).background(accentColor)
                        .border(3.dp, PlayerInk, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                clock(shown), style = HospitalityTheme.typography.body,
                // Mientras se busca, el tiempo de la izquierda es el destino, no lo que suena.
                color = if (scrub != null) accentColor else PlayerInk.copy(alpha = 0.78f),
                fontWeight = if (scrub != null) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                clock(duration), style = HospitalityTheme.typography.body,
                color = PlayerInk.copy(alpha = 0.78f),
            )
        }
    }
}

/**
 * Botón del player: ícono monocromo con su nombre debajo, sin relleno. Enfocado, se agranda, se
 * enciende el aro de [accentColor] y la etiqueta pasa a blanco pleno.
 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    label: String,
    description: String,
    accentColor: Color,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    // Igual que FocusCard: el foco se lee del árbol de foco, no de las interacciones (si llega por
    // FocusRequester al componerse, el evento de interacción a veces se pierde).
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "playerControlScale")
    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            // clickable ya es enfocable y responde al OK del control: no sumar focusable() aparte.
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.scale(scale).size(52.dp).clip(CircleShape)
                .background(if (focused) PlayerInk.copy(alpha = 0.12f) else Color.Transparent)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) accentColor else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // contentDescription null: el nombre accesible del botón ya lo da onClickLabel, y el
            // texto de abajo lo dice en pantalla -- repetirlo acá lo haría leerse dos veces.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = PlayerInk,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            label,
            style = HospitalityTheme.typography.body,
            color = if (focused) PlayerInk else PlayerInk.copy(alpha = 0.62f),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Listas de audio y subtítulos, en lugar de los controles. "Atrás" vuelve a los controles (por eso
 * el BackHandler propio: si no, atrás cerraría el player entero).
 */
@Composable
fun TrackSelector(player: ExoPlayer, text: UiText, accentColor: Color, onClose: () -> Unit) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var parameters by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    var audioFocused by remember { mutableStateOf(false) }

    BackHandler { onClose() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
            override fun onTrackSelectionParametersChanged(newParameters: TrackSelectionParameters) {
                parameters = newParameters
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun select(group: Tracks.Group, index: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    val audio = tracks.trackOptions(C.TRACK_TYPE_AUDIO)
    val subtitles = tracks.trackOptions(C.TRACK_TYPE_TEXT)
    val subtitlesOff = parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || subtitles.none { it.selected }
    val hasAudioRow = audio.size > 1
    val hasSubtitlesRow = subtitles.isNotEmpty()

    LaunchedEffect(hasAudioRow) { (if (hasAudioRow) audioFocus else subtitlesFocus).requestRetrying() }

    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp)
            // Arriba/abajo se mueven entre las dos listas y nunca sueltan el foco fuera del panel.
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { if (hasAudioRow) runCatching { audioFocus.requestFocus() }; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (hasSubtitlesRow && audioFocused) runCatching { subtitlesFocus.requestFocus() }
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasAudioRow) {
            Text(text.audioTrack, style = HospitalityTheme.typography.body, color = PlayerInk.copy(alpha = 0.78f))
            TrackRow(Modifier.onFocusChanged { audioFocused = it.hasFocus }) {
                audio.forEachIndexed { i, option ->
                    TrackChip(
                        label = option.label, selected = option.selected, accentColor = accentColor,
                        modifier = if (i == 0) Modifier.focusRequester(audioFocus) else Modifier,
                        onClick = { select(option.group, option.index) },
                    )
                }
            }
        }
        if (hasSubtitlesRow) {
            Text(text.subtitles, style = HospitalityTheme.typography.body, color = PlayerInk.copy(alpha = 0.78f))
            TrackRow {
                TrackChip(
                    label = text.subtitlesOff, selected = subtitlesOff, accentColor = accentColor,
                    modifier = Modifier.focusRequester(subtitlesFocus),
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    },
                )
                subtitles.forEach { option ->
                    TrackChip(
                        label = option.label, selected = !subtitlesOff && option.selected, accentColor = accentColor,
                        onClick = { select(option.group, option.index) },
                    )
                }
            }
        }
    }
}

/**
 * true si hay algo que elegir: más de un audio, o algún subtítulo. Si no, el botón de pistas no
 * se muestra -- abrir una lista con una sola opción no le sirve a nadie.
 */
internal fun Tracks.hasSelectableTracks(): Boolean =
    trackOptions(C.TRACK_TYPE_AUDIO).size > 1 || trackOptions(C.TRACK_TYPE_TEXT).isNotEmpty()

/** Una pista elegible: el grupo y el índice dentro del grupo son lo que pide Media3 para fijarla. */
private data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

/** Las pistas de [type] que el dispositivo puede reproducir -- una no soportada no se ofrece. */
private fun Tracks.trackOptions(type: Int): List<TrackOption> =
    groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            TrackOption(group, index, group.getTrackFormat(index).trackLabel(index), group.isTrackSelected(index))
        }
    }

/**
 * Nombre de la pista: la etiqueta que manda el stream, si no el idioma en el idioma del propio
 * huésped ("Español", "English"), y como último recurso el número de pista.
 */
private fun Format.trackLabel(index: Int): String {
    label?.takeIf { it.isNotBlank() }?.let { return it }
    language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { tag ->
        Locale.forLanguageTag(tag).displayLanguage.takeIf { it.isNotBlank() }
            ?.let { return it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
    }
    return "#${index + 1}"
}

@Composable
private fun TrackRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Pastilla de una pista. La elegida va rellena con el color del operador. */
@Composable
private fun TrackChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(100.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(100.dp))
                .background(if (selected) accentColor else PlayerInk.copy(alpha = 0.12f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(label, style = HospitalityTheme.typography.body, color = PlayerInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Si el huésped quiere que esto esté reproduciendo -- la intención, no el estado del buffer.
 *
 * No usar `isPlaying` para dibujar el botón: ése es `STATE_READY && playWhenReady`, así que da
 * false mientras el stream todavía está armando el buffer. Al entrar a una película el botón
 * mostraba el triángulo de "reproducir" durante los primeros segundos y parecía que el player se
 * había pausado solo. `playWhenReady` arranca en true y solo cambia si el huésped aprieta pausa.
 *
 * Terminada la película, playWhenReady sigue en true pero no tiene sentido ofrecer "pausar":
 * ahí el botón vuelve a ser "reproducir" (y [togglePlayPause] la reinicia desde el principio).
 */
internal val Player.wantsToPlay: Boolean
    get() = playWhenReady && playbackState != Player.STATE_ENDED

/** Pausa, reanuda, o vuelve a empezar si la película ya terminó. */
internal fun Player.togglePlayPause() {
    if (wantsToPlay) {
        pause()
    } else {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    }
}

/** mm:ss, o h:mm:ss cuando pasa la hora (películas largas). */
internal fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * Pide el foco reintentando unos frames, igual que [rememberInitialFocus]: el nodo puede no estar
 * adjunto todavía en el frame en que se muestra el panel.
 */
internal suspend fun FocusRequester.requestRetrying() {
    repeat(10) {
        withFrameNanos { }
        if (runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return
    }
}
