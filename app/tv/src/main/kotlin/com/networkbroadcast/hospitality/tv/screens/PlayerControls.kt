package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Lo que el control remoto adelanta/retrocede con los botones, igual que SEEK_STEP_MS del player. */
private const val SEEK_SECONDS = 10

/** Lo que espera la barra sin que toquen nada antes de confirmar el salto. */
private const val SCRUB_COMMIT_MS = 500L

/** Aire adicional entre pausa/reproducir y los botones de 10 s, además del espaciado de la fila. */
private val PLAY_BUTTON_GAP = 16.dp

/**
 * Controles de reproducción del panel del player: barra de progreso arriba y, abajo, retroceder
 * 10 s · pausa/reproducir · avanzar 10 s (más audio/subtítulos si el stream trae opciones).
 *
 * Todo es enfocable con el control remoto: izquierda/derecha mueven el foco entre los botones y
 * OK activa el que esté enfocado; arriba va a la barra de progreso, donde izquierda/derecha
 * buscan dentro de la película. Las teclas de medios del control (play/pause, rewind, fast
 * forward) siguen actuando directo sin pasar por el foco -- eso lo maneja la pantalla.
 *
 * [controlColor] es X_APP_PLAYER_CONTROL_COLOR: pinta los botones, la parte llena de la barra y
 * el cursor. El color del panel que los contiene es X_APP_CARD_COLOR (ver InfoPanel).
 */
@Composable
fun PlayerControls(
    playing: Boolean,
    controlColor: Color,
    /** Aro del elemento enfocado (X_APP_CONFIRM_COLOR; ver PlayerScreen). */
    focusColor: Color,
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
     * true: arriba/abajo nunca salen del panel (van a la barra de progreso o se descartan). false
     * en TV en vivo, donde arriba/abajo zapean y tienen que llegar a la pantalla.
     */
    confineVertical: Boolean = true,
) {
    val colors = HospitalityTheme.colors
    val playFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val hasBar = duration > 0
    // El panel aparece ya con el foco en pausa/reproducir, el botón del medio: así el huésped
    // aprieta OK dos veces (uno abre el panel, otro pausa) en vez de buscar el foco con el D-pad.
    LaunchedEffect(Unit) { playFocus.requestRetrying() }

    Column(modifier.fillMaxWidth()) {
        if (hasBar) {
            SeekBar(
                position = position, duration = duration, controlColor = controlColor,
                focusColor = focusColor, label = text.seekBar,
                onSeekTo = onSeekTo,
                onDown = { runCatching { playFocus.requestFocus() } },
                modifier = Modifier.focusRequester(barFocus),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp)
                // Arriba/abajo no se delegan al buscador de foco: con la barra se va a ella, y sin
                // barra se descartan -- si se delegaran, el foco se iría al contenedor de la
                // pantalla, que con el panel abierto no hace nada con el OK (panel trabado).
                .onKeyEvent { event ->
                    if (!confineVertical || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (hasBar) runCatching { barFocus.requestFocus() }; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> true
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seekable) {
                ControlButton(onClick = onSeekBack, color = controlColor, focusColor = focusColor, diameter = 46.dp, label = text.rewind10) { tint, size ->
                    SeekIcon(back = true, tint = tint, diameter = size)
                }
                // Aire extra alrededor del botón principal: pegado a los de 10 s, los tres se leen
                // como un bloque y cuesta distinguir cuál está enfocado de un vistazo.
                Spacer(Modifier.width(PLAY_BUTTON_GAP))
            }
            ControlButton(
                onClick = onPlayPause, color = controlColor, focusColor = focusColor, diameter = 58.dp,
                label = if (playing) text.pause else text.play,
                modifier = Modifier.focusRequester(playFocus),
            ) { tint, size ->
                PlayPauseIcon(playing = playing, tint = tint, diameter = size)
            }
            if (seekable) {
                Spacer(Modifier.width(PLAY_BUTTON_GAP))
                ControlButton(onClick = onSeekForward, color = controlColor, focusColor = focusColor, diameter = 46.dp, label = text.forward10) { tint, size ->
                    SeekIcon(back = false, tint = tint, diameter = size)
                }
            }
            onShowTracks?.let {
                // Misma separación que alrededor de pausa/reproducir: es otro tipo de control
                // (no toca la reproducción), no tiene que leerse pegado a los de 10 s.
                Spacer(Modifier.width(PLAY_BUTTON_GAP))
                ControlButton(onClick = it, color = controlColor, focusColor = focusColor, diameter = 46.dp, label = text.audioAndSubtitles) { tint, size ->
                    SubtitlesIcon(tint = tint, diameter = size)
                }
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
    controlColor: Color,
    focusColor: Color,
    label: String,
    onSeekTo: (Long) -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HospitalityTheme.colors
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
            .padding(top = 12.dp)
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { scrub = (shown - step).coerceAtLeast(0L); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { scrub = (shown + step).coerceAtMost(duration); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { onDown(); true }
                    // Arriba no lleva a ningún lado: se consume para que el foco no se escape del panel.
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        val knob = 18.dp
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (focused) knob else 6.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.fillMaxWidth().height(if (focused) 8.dp else 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    // Riel apenas insinuado sobre el panel; el color del operador es el tramo ya
                    // reproducido, que es lo que se mira.
                    .background(colors.textPrimary.copy(alpha = 0.22f)),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(controlColor))
            }
            if (focused) {
                Box(
                    Modifier.offset(x = (maxWidth - knob) * progress).size(knob)
                        .clip(CircleShape).background(controlColor)
                        .border(3.dp, focusColor, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                clock(shown), style = HospitalityTheme.typography.body,
                // Mientras se busca, el tiempo de la izquierda es el destino, no lo que suena: se
                // pinta con el color del operador para que se note que todavía no saltó.
                color = if (scrub != null) controlColor else colors.textSecondary,
            )
            Text(clock(duration), style = HospitalityTheme.typography.body, color = colors.textSecondary)
        }
    }
}

/**
 * Listas de audio y subtítulos del stream, en el mismo panel y en lugar de los controles. "Atrás"
 * vuelve a los controles (por eso el BackHandler propio: si no, atrás cerraría el player entero).
 */
@Composable
fun TrackSelector(player: ExoPlayer, text: UiText, controlColor: Color, onClose: () -> Unit) {
    val colors = HospitalityTheme.colors
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
        Modifier.fillMaxWidth().padding(top = 12.dp)
            // Arriba/abajo se mueven entre las dos listas y nunca sueltan el foco fuera del panel
            // (si se delegaran al buscador de foco, podrían irse al contenedor de la pantalla, que
            // con el panel abierto no hace nada con el OK).
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
            Text(text.audioTrack, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            TrackRow(Modifier.onFocusChanged { audioFocused = it.hasFocus }) {
                audio.forEachIndexed { i, option ->
                    TrackChip(
                        label = option.label, selected = option.selected, controlColor = controlColor,
                        modifier = if (i == 0) Modifier.focusRequester(audioFocus) else Modifier,
                        onClick = { select(option.group, option.index) },
                    )
                }
            }
        }
        if (hasSubtitlesRow) {
            Text(text.subtitles, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            TrackRow {
                TrackChip(
                    label = text.subtitlesOff, selected = subtitlesOff, controlColor = controlColor,
                    modifier = Modifier.focusRequester(subtitlesFocus),
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    },
                )
                subtitles.forEach { option ->
                    TrackChip(
                        label = option.label, selected = !subtitlesOff && option.selected, controlColor = controlColor,
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

/** Pastilla de una pista. La elegida va rellena con el color del operador, como los botones. */
@Composable
private fun TrackChip(
    label: String,
    selected: Boolean,
    controlColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HospitalityTheme.colors
    val textColor = if (selected) HospitalityColors.readableTextColor(controlColor) else colors.textPrimary
    FocusCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(100.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(100.dp))
                .background(if (selected) controlColor else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(label, style = HospitalityTheme.typography.body, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Botón circular del player. Relleno con el color del operador y con el ícono en blanco o negro
 * según contraste (igual criterio que las tarjetas, ver HospitalityColors.readableTextColor), así
 * cualquier color que cargue Delancer en X_APP_PLAYER_CONTROL_COLOR deja el ícono visible.
 *
 * El aro de [focusColor] es lo único que distingue al botón enfocado, porque todos comparten
 * color de relleno (a pedido: todos del color del parámetro).
 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    color: Color,
    focusColor: Color,
    diameter: Dp,
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable (tint: Color, diameter: Dp) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // Igual que FocusCard: el foco se lee del árbol de foco, no de las interacciones (si llega por
    // FocusRequester al componerse, el evento de interacción a veces se pierde).
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "playerControlScale")
    val tint = remember(color) { HospitalityColors.readableTextColor(color) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .size(diameter)
            .clip(CircleShape)
            .background(color)
            .border(width = if (focused) 3.dp else 0.dp, color = if (focused) focusColor else Color.Transparent, shape = CircleShape)
            // clickable ya es enfocable y responde al OK del control: no sumar focusable() aparte.
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint, diameter)
    }
}

/** Triángulo de "reproducir" o las dos barras de "pausa", dibujados a medida del botón. */
@Composable
private fun PlayPauseIcon(playing: Boolean, tint: Color, diameter: Dp) {
    Canvas(Modifier.size(diameter * 0.42f)) {
        if (playing) {
            val barWidth = size.width * 0.3f
            val radius = CornerRadius(barWidth * 0.3f)
            drawRoundRect(tint, Offset(0f, 0f), Size(barWidth, size.height), radius)
            drawRoundRect(tint, Offset(size.width - barWidth, 0f), Size(barWidth, size.height), radius)
        } else {
            // Un pelo corrido a la derecha: un triángulo centrado por su caja se ve desbalanceado
            // dentro de un círculo.
            val path = Path().apply {
                moveTo(size.width * 0.08f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.08f, size.height)
                close()
            }
            drawPath(path, tint)
        }
    }
}

/**
 * Flecha circular con el "10" adentro (retroceder/avanzar 10 segundos), al estilo de los íconos
 * de trazo del resto de la app (ver LockIcon/EyeIcon en LoginScreen.kt). Se dibuja en vez de
 * usar un drawable para que escale con el tamaño del botón y tome el color del operador.
 */
@Composable
private fun SeekIcon(back: Boolean, tint: Color, diameter: Dp) {
    val iconSize = diameter * 0.52f
    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawSeekArrow(back = back, tint = tint) }
        Text(
            "$SEEK_SECONDS",
            color = tint,
            fontSize = (iconSize.value * 0.34f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Arco de casi una vuelta con el hueco arriba y la punta de flecha en el extremo superior: hacia
 * la izquierda (antihorario) si [back], hacia la derecha si no.
 */
private fun DrawScope.drawSeekArrow(back: Boolean, tint: Color) {
    val stroke = size.minDimension * 0.1f
    val radius = (size.minDimension - stroke * 3f) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val gapHalf = 22f
    // Se dibuja siempre el mismo arco (hueco arriba); lo que cambia es de qué punta sale la flecha.
    drawArc(
        color = tint,
        startAngle = -90f + gapHalf,
        sweepAngle = 360f - gapHalf * 2f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // Punta: en el extremo izquierdo del hueco si va para atrás, en el derecho si va para adelante.
    val angle = Math.toRadians(((if (back) -90f - gapHalf else -90f + gapHalf)).toDouble())
    val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    // Dirección tangente del giro en ese punto (antihorario para atrás, horario para adelante).
    val direction = if (back) {
        Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    } else {
        Offset(-sin(angle).toFloat(), cos(angle).toFloat())
    }
    val normal = Offset(-direction.y, direction.x)
    val length = stroke * 2.4f
    val halfWidth = stroke * 1.5f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - direction.x * length + normal.x * halfWidth, tip.y - direction.y * length + normal.y * halfWidth)
        lineTo(tip.x - direction.x * length - normal.x * halfWidth, tip.y - direction.y * length - normal.y * halfWidth)
        close()
    }
    drawPath(head, tint)
}

/** Recuadro con dos renglones adentro: el ícono clásico de subtítulos. */
@Composable
private fun SubtitlesIcon(tint: Color, diameter: Dp) {
    Canvas(Modifier.size(diameter * 0.5f)) {
        val stroke = size.minDimension * 0.1f
        val boxHeight = size.height * 0.78f
        val top = (size.height - boxHeight) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2f, top),
            size = Size(size.width - stroke, boxHeight),
            cornerRadius = CornerRadius(size.minDimension * 0.18f),
            style = Stroke(width = stroke),
        )
        val inset = size.width * 0.22f
        val firstLine = top + boxHeight * 0.36f
        val secondLine = top + boxHeight * 0.66f
        drawLine(tint, Offset(inset, firstLine), Offset(size.width - inset, firstLine), stroke, StrokeCap.Round)
        drawLine(tint, Offset(inset, secondLine), Offset(size.width * 0.62f, secondLine), stroke, StrokeCap.Round)
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
