package com.networkbroadcast.hospitality.mobile

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import java.util.Locale
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.panaccess.CopyProtection
import com.panaccess.android.exoplayer.PanHlsExtractorFactory
import kotlinx.coroutines.delay

private const val TAG = "Hospitality.Player"
private const val MAX_RETRIES = 3

// Vigilancia de reproducción trabada (versión corta de la de la TV): un STATE_BUFFERING que no
// termina nunca, o un canal en vivo que llega a STATE_ENDED, no dan error; el player se queda mudo.
private const val STALL_CHECK_INTERVAL_MS = 2_000L
private const val STALL_BUFFERING_TIMEOUT_MS = 15_000L
private const val STALL_MAX_RESTARTS_IN_ROW = 3

/** Reintentos de la reproducción actual. Se crea de nuevo en cada contenido (ver [MobilePlayer]). */
@Stable
private class Retry {
    var attempt by mutableIntStateOf(0)
    var restart by mutableIntStateOf(0)
    var failed by mutableStateOf(false)
}

/**
 * Player del celular: video 16:9 (o a pantalla completa) y debajo una barra con un ícono chico de
 * pausa/reproducir (color de acento del tema), en películas el tiempo y la barra de avance, y a la
 * derecha los íconos chicos de audio/subtítulos y pantalla completa. Tocar el video también
 * alterna pausa/reproducir. Sin botones grandes sobre la imagen.
 *
 * Reproduce igual que la TV: la URL sale de [EntertainmentSource.playbackUrl] en cada carga y en
 * cada reintento (va atada a la sesión del DRM), HLS con el extractor de Panaccess y, desde el
 * segundo error, [EntertainmentSource.recover] antes de pedir la URL de nuevo.
 */
@OptIn(UnstableApi::class)
@Composable
fun MobilePlayer(
    source: EntertainmentSource,
    target: Playable,
    live: Boolean,
    text: UiText,
    contentText: ContentText,
    overlay: PlayerOverlay,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Dónde arrancar (películas: "seguir viendo"); en los reintentos vuelve a donde estaba. */
    startPositionMs: Long = 0L,
    /** Cada ~10 s con la posición y la duración, y [onLeave] al salir: para guardar el avance. */
    onProgress: ((target: Playable, positionMs: Long, durationMs: Long) -> Unit)? = null,
    onLeave: ((target: Playable, positionMs: Long, durationMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = HospitalityTheme.colors
    val player = remember {
        ExoPlayer.Builder(context)
            // Mismo ajuste fino del retraso respecto al vivo que la TV (500 ms, velocidad 0,97–1,03).
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setMaxLiveOffsetErrorMsForUnitSpeed(500)
                    .setFallbackMinPlaybackSpeed(0.97f)
                    .setFallbackMaxPlaybackSpeed(1.03f)
                    .build(),
            )
            .build()
            .apply { playWhenReady = true }
    }
    val retry = remember(target) { Retry() }
    // El listener se registra una sola vez: lee los reintentos del contenido actual a través de esto.
    val currentRetry by rememberUpdatedState(retry)
    var rendering by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekable by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var showTracks by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendering = true }
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
            override fun onPlayerError(e: PlaybackException) {
                val r = currentRetry
                Log.w(TAG, "error ${e.errorCodeName} (intento ${r.attempt}): ${e.message}")
                if (r.attempt < MAX_RETRIES) r.attempt++ else r.failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release(); CopyProtection.detach() }
    }

    // Segundo plano: el video se pausa (la TV no tiene este caso, el celular sí).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Al salir se entrega la posición final (el player ya se liberó, por eso se usa la del sondeo).
    val leave by rememberUpdatedState(onLeave)
    // Se pasa el [target] de este contenido: al cambiar de uno a otro, el callback ya es el del nuevo.
    DisposableEffect(target) { onDispose { leave?.invoke(target, position, duration) } }
    val progress by rememberUpdatedState(onProgress)
    LaunchedEffect(target) {
        while (true) { delay(10_000); if (player.playbackState == Player.STATE_READY) progress?.invoke(target, position, duration) }
    }

    LaunchedEffect(target) {
        rendering = false
        showTracks = false
        if (live) CopyProtection.onLiveStream((target as? Playable.Live)?.channelId.orEmpty()) else CopyProtection.onNonLive()
    }

    LaunchedEffect(target, retry.attempt, retry.restart) {
        retry.failed = false
        val startedAt = SystemClock.elapsedRealtime()
        // En un reintento se vuelve a donde estaba; la primera vez, a donde dice [startPositionMs].
        val retrying = retry.attempt > 0 || retry.restart > 0
        val resumeAt = if (live) 0L
        else if (retrying && player.mediaItemCount > 0 && player.currentPosition > 0) player.currentPosition
        else startPositionMs
        // Cerrar lo anterior ANTES de pedir la URL nueva: el DRM entrega un stream por sesión y, si el
        // player sigue consumiendo el canal anterior, la URL del siguiente puede venir inservible.
        if (player.mediaItemCount > 0) { player.stop(); player.clearMediaItems() }
        if (retry.attempt > 0) delay(1_000L * retry.attempt)
        // Desde el segundo error se sospecha de la sesión (p. ej. 401): se recupera antes de reintentar.
        if (retry.attempt >= 2) source.recover()
        val url = source.playbackUrl(target)
        Log.i(TAG, "$target: url ${if (url == null) "no resuelta" else "ok"} en ${SystemClock.elapsedRealtime() - startedAt} ms")
        if (url == null) {
            if (retry.attempt < MAX_RETRIES) retry.attempt++ else retry.failed = true
            return@LaunchedEffect
        }
        val hls = url.contains("m3u8")
        val item = MediaItem.Builder().setUri(url).setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null)
        if (live) item.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build(),
        )
        if (hls) {
            // Extractor HLS de Panaccess y arranque sin esperar chunks, como la TV: sin esto algunos
            // canales cargan para siempre sin dar error.
            val mediaSource = HlsMediaSource.Factory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()))
                .setExtractorFactory(PanHlsExtractorFactory())
                .setAllowChunklessPreparation(true)
                .createMediaSource(item.build())
            player.setMediaSource(mediaSource)
        } else {
            player.setMediaItem(item.build())
        }
        // Antes de prepare(): ExoPlayer lo toma como posición de arranque, sin el salto de un seekTo() tardío.
        if (resumeAt > 0L) player.seekTo(resumeAt)
        player.playWhenReady = true
        player.prepare()
    }

    // Estado del player para la barra de abajo (sondeo cada 500 ms, como la TV).
    LaunchedEffect(player) {
        while (true) {
            playing = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            seekable = player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive
            delay(500)
        }
    }

    // Reproducción trabada: se reinicia la carga (hasta STALL_MAX_RESTARTS_IN_ROW veces seguidas).
    LaunchedEffect(target, retry.attempt, retry.restart) {
        if (retry.restart >= STALL_MAX_RESTARTS_IN_ROW) return@LaunchedEffect
        var bufferingSince = 0L
        while (true) {
            delay(STALL_CHECK_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            if (!player.playWhenReady) { bufferingSince = 0L; continue }
            val stall = when (player.playbackState) {
                Player.STATE_ENDED -> if (live) "canal en vivo en STATE_ENDED" else null
                Player.STATE_BUFFERING -> {
                    if (bufferingSince == 0L) { bufferingSince = now; null }
                    else (now - bufferingSince).takeIf { it > STALL_BUFFERING_TIMEOUT_MS }?.let { "en STATE_BUFFERING hace $it ms" }
                }
                else -> { bufferingSince = 0L; null }
            }
            if (stall != null) {
                Log.w(TAG, "$target: reproducción trabada ($stall), reiniciando ${retry.restart + 1}/$STALL_MAX_RESTARTS_IN_ROW")
                retry.restart++
                return@LaunchedEffect
            }
        }
    }

    fun togglePlayPause() {
        if (player.playWhenReady) player.pause() else player.play()
        playing = player.playWhenReady
    }

    val appLocale = LocalAppLocale.current
    val hasTracks = tracks.trackOptions(C.TRACK_TYPE_AUDIO, appLocale).size > 1 || tracks.trackOptions(C.TRACK_TYPE_TEXT, appLocale).isNotEmpty()
    val control = LocalPlayerColor.current.takeIf { it != Color.Unspecified } ?: colors.accent
    val barText = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")
    val clipShape = RoundedCornerShape(rad(20))

    // A pantalla completa el mismo Column ocupa todo y el video toma el espacio que sobra; así el
    // player (y la reproducción) no se recrea al entrar y salir: solo cambian los modificadores.
    // Pantalla del mockup (.player): video 16:9 con esquinas del tema, el badge arriba a la izquierda y la
    // barra de control (38 dp, negro al 50 %) dentro del marco, abajo.
    Column(if (fullscreen) modifier.background(colors.background) else modifier) {
        Box(
            (if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(clipShape))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player; keepScreenOn = true } },
                modifier = Modifier.fillMaxSize(),
            )
            // Huella antipiratería del operador (un solo contenedor por proceso, ver CopyProtection).
            AndroidView(factory = { CopyProtection.overlay(it) }, modifier = Modifier.fillMaxSize())
            if ((buffering || !rendering) && !retry.failed) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    color = control,
                    strokeWidth = 3.dp,
                )
            }
            // Tocar el video alterna pausa/reproducir.
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::togglePlayPause,
                ),
            )
            // Arriba (.tp): badge ("EN VIVO") a la izquierda y, en vivo, el horario del programa a la derecha.
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (live) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                    Text(overlay.badge.uppercase(), color = Color.White,
                        style = HospitalityTheme.typography.label.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.14.sp))
                }
                overlay.range?.let {
                    Text(it, color = Color.White, style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
            if (showTracks && hasTracks) {
                TrackPanel(
                    player, tracks, text,
                    Modifier.align(Alignment.CenterEnd).padding(bottom = 38.dp).fillMaxHeight().fillMaxWidth(if (fullscreen) 0.5f else 1f),
                )
            }
            // Barra de abajo (.bar3): ícono chico de pausa/reproducir, tiempo, avance, audio/subtítulos y pantalla completa.
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(38.dp).background(Color(0x80000000)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BarIcon(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) text.pause else text.play, 15.dp, control, ::togglePlayPause)
                val clock = if (live) overlay.clock.orEmpty() else "${formatClock(position)} / ${formatClock(duration)}"
                if (clock.isNotEmpty()) Text(clock, style = barText, color = Color.White, maxLines = 1)
                if (live) {
                    ProgressTrack(overlay.progress ?: 0f, control, Modifier.weight(1f))
                } else {
                    SeekBar(
                        fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        enabled = seekable && duration > 0, color = control,
                        onSeek = { f -> val to = (f.coerceIn(0f, 1f) * duration).toLong(); player.seekTo(to); position = to },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (hasTracks) BarIcon(R.drawable.ic_tracks, text.audioAndSubtitles, 17.dp, control) { showTracks = !showTracks }
                BarIcon(
                    if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
                    if (fullscreen) contentText.exitFullscreen else contentText.fullscreen,
                    16.dp, control, onToggleFullscreen,
                )
            }
        }
        if (retry.failed) {
            Text(
                text.playbackError,
                style = HospitalityTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Ícono chico de la barra, en el color del control del player (X_APP_PLAYER_CONTROL_COLOR). */
@Composable
private fun BarIcon(icon: Int, description: String, size: androidx.compose.ui.unit.Dp, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(size))
    }
}

/** Barra de avance sin interacción (`.pb`): 3 dp, blanco al 30 % y el avance en el color del control. */
@Composable
private fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = RoundedCornerShape(2.dp)
    Box(modifier.height(3.dp).clip(track).background(Color(0x4DFFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/**
 * Audio y subtítulos con la API de pistas de Media3. Un panel liso sobre el video con una fila por
 * pista; la elegida va en el color de acento. Los cambios se aplican al instante.
 */
@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(player: ExoPlayer, tracks: Tracks, text: UiText, modifier: Modifier = Modifier) {
    val colors = HospitalityTheme.colors
    var parameters by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTrackSelectionParametersChanged(newParameters: TrackSelectionParameters) { parameters = newParameters }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    fun select(option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.group.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
    }
    val appLocale = LocalAppLocale.current
    val audio = tracks.trackOptions(C.TRACK_TYPE_AUDIO, appLocale)
    val subtitles = tracks.trackOptions(C.TRACK_TYPE_TEXT, appLocale)
    val subtitlesOff = parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || subtitles.none { it.selected }
    Column(
        modifier.background(colors.surface).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (audio.size > 1) {
            TrackHeading(text.audioTrack)
            audio.forEach { TrackRowItem(it.label, it.selected) { select(it) } }
        }
        if (subtitles.isNotEmpty()) {
            TrackHeading(text.subtitles)
            TrackRowItem(text.subtitlesOff, subtitlesOff) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }
            subtitles.forEach { TrackRowItem(it.label, !subtitlesOff && it.selected) { select(it) } }
        }
    }
}

@Composable
private fun TrackHeading(title: String) {
    Text(
        title, style = HospitalityTheme.typography.section.copy(fontSize = 13.sp), color = HospitalityTheme.colors.textMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun TrackRowItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    Text(
        label,
        style = HospitalityTheme.typography.label.copy(fontSize = 15.sp),
        color = if (selected) colors.accent else colors.textPrimary,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().height(36.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

/** Una pista elegible: el grupo y el índice dentro del grupo son lo que pide Media3 para fijarla. */
private data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

/** Las pistas de [type] que el dispositivo puede reproducir; una no soportada no se ofrece. */
private fun Tracks.trackOptions(type: Int, locale: Locale): List<TrackOption> =
    groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            TrackOption(group, index, group.getTrackFormat(index).trackLabel(index, locale), group.isTrackSelected(index))
        }
    }

/** Etiqueta del stream; si no hay, el idioma en el idioma del huésped ("Español"); si no, el número. */
private fun Format.trackLabel(index: Int, locale: Locale): String {
    label?.takeIf { it.isNotBlank() }?.let { return it }
    language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }?.let { tag ->
        Locale.forLanguageTag(tag).getDisplayLanguage(locale).takeIf { it.isNotBlank() }
            ?.let { return it.replaceFirstChar { c -> c.titlecase(locale) } }
    }
    return "#${index + 1}"
}

/** Barra de avance (3 dp, como el mockup): se toca o se arrastra para saltar. Solo con contenido buscable. */
@Composable
private fun SeekBar(fraction: Float, enabled: Boolean, color: Color, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier
            .height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) { if (enabled) detectTapGestures { onSeek(it.x / width) } }
            .pointerInput(enabled) { if (enabled) detectHorizontalDragGestures { change, _ -> onSeek(change.position.x / width) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        ProgressTrack(fraction, color, Modifier.fillMaxWidth())
    }
}

/** Lo que el player muestra encima del video, calculado por la pantalla de contenido (que conoce la guía). */
data class PlayerOverlay(
    /** "EN VIVO" o, en películas, el nombre de la sección. */
    val badge: String,
    /** En vivo: horario del programa al aire ("09:00 – 10:00"). */
    val range: String? = null,
    /** En vivo: cuánto va del programa (0..1). */
    val progress: Float? = null,
    /** En vivo: la hora actual. */
    val clock: String? = null,
)
