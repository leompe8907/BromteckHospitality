package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.panaccess.android.exoplayer.PanHlsExtractorFactory
import androidx.media3.ui.PlayerView
import com.networkbroadcast.hospitality.panaccess.CopyProtection
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.nowAndNext
import com.networkbroadcast.hospitality.tv.MediaRequest
import com.networkbroadcast.hospitality.brand.UiText
import kotlinx.coroutines.delay

private const val TAG = "Hospitality.Player"

/** Los PLAY_WHEN_READY_CHANGE_REASON_* de Media3, en texto, para que el log se lea solo. */
private fun playWhenReadyReason(reason: Int): String = when (reason) {
    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "pedido del usuario"
    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "pérdida de foco de audio"
    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "audio desconectado"
    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "app remota"
    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "fin del contenido"
    else -> "motivo $reason"
}
private const val MAX_RETRIES = 3
private const val SEEK_STEP_MS = 10_000L // PLAYER_SEEK_INCREMENT_MS del base

/** Alto del desvanecido del degradado por encima del contenido del player (ver PlayerScrim). */
private val SCRIM_FADE = 112.dp

/**
 * Player HLS. Reproduce la URL que entrega el DRM con el HLS estándar de Media3, sin MediaDrm:
 * así lo hace también la app iOS del base. Si un stream no anda, el siguiente paso es el
 * PanHlsExtractorFactory de Panaccess (lib-exoplayer-hls-release.aar del base).
 *
 * [resolve] se llama en cada carga y en cada reintento: la URL va atada a la sesión del DRM.
 */
@OptIn(UnstableApi::class)
@Composable
private fun HlsPlayer(
    key: Any,
    live: Boolean,
    resolve: suspend () -> String?,
    recover: suspend () -> Boolean,
    onError: (String?) -> Unit,
    /** Dónde arrancar, para retomar lo que el huésped dejó a medias (ver GuestSession). 0 = desde
     *  el principio. Se aplica también en los reintentos: si falla a mitad, vuelve a donde estaba,
     *  no al principio. */
    startPositionMs: Long = 0L,
    content: @Composable BoxScope.(ExoPlayer) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).setSeekBackIncrementMs(SEEK_STEP_MS).setSeekForwardIncrementMs(SEEK_STEP_MS).build().apply { playWhenReady = true } }
    var attempt by remember(key) { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                android.util.Log.i(TAG, "$key: video ${videoSize.width}x${videoSize.height}")
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) android.util.Log.i(TAG, "$key: listo; pistas=${player.currentTracks.groups.map { it.type }}")
            }
            // Quién arranca y quién frena la reproducción, con el motivo que da Media3. Es la única
            // forma de distinguir "lo pausó el huésped" de "lo pausó el sistema" (pérdida de foco de
            // audio, auriculares desconectados, una app remota) sin adivinar.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                android.util.Log.i(TAG, "$key: playWhenReady=$playWhenReady (${playWhenReadyReason(reason)})")
            }
            // El otro camino por el que el video se frena sin que playWhenReady cambie.
            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                android.util.Log.i(TAG, "$key: reproducción suprimida, motivo=$reason")
            }
            override fun onPlayerError(e: PlaybackException) {
                android.util.Log.w(TAG, "error ${e.errorCodeName} (intento $attempt): ${e.message}")
                if (attempt < MAX_RETRIES) attempt++ else onError(e.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release(); CopyProtection.detach() }
    }

    LaunchedEffect(key, attempt) {
        onError(null)
        if (attempt > 0) delay(1_000L * attempt)
        // Desde el segundo error se sospecha de la sesión (p. ej. 401): se recupera antes de pedir
        // la URL de nuevo, como VideoFragment del base al tercer fallo.
        if (attempt >= 2) recover()
        val url = resolve()
        android.util.Log.i(TAG, "$key: url ${if (url == null) "no resuelta" else "ok"}")
        if (url == null) { onError("url"); return@LaunchedEffect }
        val hls = url.contains("m3u8")
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null)
        // Mismo margen de vivo que el base: 10 s detrás del borde y velocidad 0,97–1,03.
        if (live) item.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build()
        )
        if (hls) {
            // Como getMediaSourceForHls de VideoFragment: extractor HLS de Panaccess
            // (PLAYER_USE_HLS_PANEXTRACTOR) y arranque sin esperar chunks (PLAYER_HLS_ENABLE_FASTER_START_UP).
            // Sin el extractor, algunos canales (p. ej. Record News) cargan para siempre sin error.
            val source = HlsMediaSource.Factory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()))
                .setExtractorFactory(PanHlsExtractorFactory())
                .setAllowChunklessPreparation(true)
                .createMediaSource(item.build())
            player.setMediaSource(source)
        } else {
            player.setMediaItem(item.build())
        }
        // Antes de prepare(): ExoPlayer toma esto como posición de arranque y no se ve el salto
        // que habría si se hiciera seekTo() una vez que ya empezó a reproducir.
        if (startPositionMs > 0L) player.seekTo(startPositionMs)
        player.prepare()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize(),
        )
        // Capa de la huella antipiratería del operador (un solo contenedor por proceso, ver CopyProtection).
        AndroidView(factory = { CopyProtection.overlay(it) }, modifier = Modifier.fillMaxSize())
        content(player)
    }
}

/**
 * Capa inferior del player: un degradado sobre el video, no una tarjeta. Es lo que hacen los
 * players de TV de las OTT grandes, y acá además resuelve dos cosas concretas: la imagen nunca
 * queda tapada por un rectángulo opaco, y el contenido de adentro puede usar una tinta fija
 * ([PlayerInk]) en vez de depender de un color que mande el operador.
 *
 * Negro neutro, nunca `colors.background`: ése es el verde/turquesa de la paleta Resort y teñía
 * toda la imagen -- el "filtro verde" que ya se sacó de la ficha de VOD y del fondo del hotel.
 *
 * El degradado mide lo que mide su contenido más [SCRIM_FADE] de desvanecido arriba, en vez de una
 * fracción fija de la pantalla: con una fracción, el player en vivo (sólo texto) desperdiciaba
 * medio cuadro, y el de películas (título + barra + botones) se quedaba corto y el texto caía
 * sobre el video sin oscurecer. Así cada pantalla ocupa exactamente lo suyo.
 */
@Composable
private fun BoxScope.PlayerScrim(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color(0xD9070D0F),
                    1f to Color(0xF2070D0F),
                ),
            )
            // Abajo, 36dp: por dentro del margen seguro de TV (~5%) pero bien pegado al borde, que
            // es donde se esperan los controles. Arriba, el desvanecido.
            .padding(start = 56.dp, end = 56.dp, top = SCRIM_FADE, bottom = 36.dp),
        content = content,
    )
}

/**
 * TV en vivo a pantalla completa. Arriba/abajo (o CH+/CH-) cambian de canal; OK muestra y esconde
 * la información del canal; atrás vuelve.
 *
 * Sin controles de reproducción, a propósito: un canal en vivo no se pausa ni se adelanta, así que
 * acá sólo va qué se está viendo y qué viene después. Los controles son cosa de
 * [MediaPlayerScreen] (películas y catchup), que es donde sirven.
 */
@Composable
fun PlayerScreen(
    source: EntertainmentSource,
    channels: List<Channel>,
    startChannelId: String?,
    text: UiText,
    /**
     * Único color parametrizado del player: X_APP_CONFIRM_COLOR (OperatorConfig.confirmColorHex).
     * Acá pinta el número de canal y la etiqueta "Ahora". null = el color de foco de la marca.
     */
    confirmColorHex: String? = null,
    onChannelChanged: (String) -> Unit,
) {
    if (channels.isEmpty()) return CenteredMessage(text.noChannels)
    var index by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var nowNext by remember { mutableStateOf<Pair<Program?, Program?>>(null to null) }
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    // Si realmente está saliendo video: el panel no se auto-esconde mientras no lo esté, así el
    // huésped ve el nombre del canal mientras carga en vez de quedarse mirando un negro sin datos.
    var rendering by remember { mutableStateOf(false) }
    val focus = rememberInitialFocus()
    val channel = channels[index]

    LaunchedEffect(channel.id) {
        onChannelChanged(channel.id)
        nowNext = null to null
        nowNext = source.schedule(channel.id).nowAndNext()
    }
    LaunchedEffect(playerRef) {
        while (true) {
            playerRef?.let { rendering = it.isPlaying }
            delay(500)
        }
    }
    LaunchedEffect(index, showInfo, rendering) {
        if (showInfo && rendering) { delay(5_000); showInfo = false }
    }

    fun zap(step: Int) { index = (index + step).mod(channels.size); showInfo = true }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                        showInfo = !showInfo; true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        LaunchedEffect(channel.id) { CopyProtection.onLiveStream(channel.id) }
        HlsPlayer(
            key = channel.id, live = true,
            resolve = { source.playbackUrl(Playable.Live(channel.id)) },
            recover = source::recover,
            onError = { error = it?.let { text.playbackError } },
        ) { player ->
            LaunchedEffect(player) { playerRef = player }
            if (showInfo || error != null) PlayerScrim {
                // Sin X_APP_CONFIRM_COLOR, el accent de la marca -- el mismo criterio que la guía.
                // Antes caía a colors.focus, que es el amarillo de los campos de texto y no tiene
                // nada que ver con el player: se veía como un color elegido a propósito cuando en
                // realidad era "el operador no configuró esto".
                val accentColor = HospitalityColors.parseHex(confirmColorHex) ?: HospitalityTheme.colors.accent
                // Número y nombre del canal: lo primero que se lee al zapear, así que va al tamaño
                // de título y con el número destacado en el color del operador.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${channel.number}", style = HospitalityTheme.typography.title, color = accentColor,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                    Text(
                        channel.name, style = HospitalityTheme.typography.title, color = PlayerInk,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                nowNext.first?.let { ProgramLine(text.nowLabel, it, accentColor, highlight = true) }
                nowNext.second?.let { ProgramLine(text.nextLabel, it, accentColor, highlight = false) }
                error?.let {
                    Text(
                        it, style = HospitalityTheme.typography.body, color = accentColor,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramLine(label: String, program: Program, accentColor: Color, highlight: Boolean) {
    // Tinta fija del player, no los tokens del tema: esto va sobre el degradado, no sobre una
    // tarjeta, así que el contraste no depende de ningún color que mande el operador.
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = HospitalityTheme.typography.label,
            color = if (highlight) accentColor else PlayerInk.copy(alpha = 0.55f),
            modifier = Modifier.width(120.dp),
        )
        Text(
            program.timeRange(), style = HospitalityTheme.typography.body,
            color = PlayerInk.copy(alpha = 0.78f), modifier = Modifier.width(160.dp),
        )
        Text(
            program.title, style = HospitalityTheme.typography.body,
            color = if (highlight) PlayerInk else PlayerInk.copy(alpha = 0.78f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Seguir viendo" para una película, serie o evento de catchup: de dónde arranca y dónde se guarda
 * al salir. La política de qué vale la pena guardar está en [GuestSession.saveResumePosition]; acá
 * sólo se transporta.
 */
data class ResumePoint(val startMs: Long, val onLeave: (positionMs: Long, durationMs: Long) -> Unit)

/**
 * Catchup y VOD: OK abre el panel de controles; con el panel abierto, izquierda/derecha mueven el
 * foco entre retroceder 10 s · pausa/reproducir · avanzar 10 s y OK activa el que esté enfocado.
 * Las teclas de medios del control (play/pause, rewind, fast forward) actúan directo. Atrás vuelve.
 */
@Composable
fun MediaPlayerScreen(
    source: EntertainmentSource,
    request: MediaRequest,
    text: UiText,
    /** Único color parametrizado del player: X_APP_CONFIRM_COLOR. Ver [PlayerScreen]. */
    confirmColorHex: String? = null,
    /**
     * Dónde quedó este contenido la última vez, y dónde guardarlo al salir (ver GuestSession).
     * Null = sin "seguir viendo": arranca de cero y no guarda nada.
     */
    resume: ResumePoint? = null,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(true) }
    // Distinto de [playing] (que es la intención): esto es si realmente está saliendo video. El
    // panel no se auto-esconde mientras no lo esté -- ni pausado ni buffereando, así el título
    // sigue a la vista mientras la película carga.
    var rendering by remember { mutableStateOf(false) }
    var seekable by remember { mutableStateOf(true) }
    // El botón de audio/subtítulos solo aparece si el stream trae algo que elegir.
    var hasTracks by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    // Cada acción sobre el panel reinicia el temporizador que lo esconde: si no, se cierra a los
    // 4 s mientras el huésped todavía está moviendo el foco entre los botones.
    var activity by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }

    // Pausado o cargando, o con el selector de pistas abierto, el panel se queda.
    LaunchedEffect(showInfo, activity, rendering, showTracks, position / 5_000) {
        if (showInfo && rendering && !showTracks) { delay(4_000); showInfo = false }
    }
    LaunchedEffect(playerRef) {
        while (true) {
            playerRef?.let {
                position = it.currentPosition
                duration = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L
                playing = it.wantsToPlay
                rendering = it.isPlaying
                seekable = it.isCurrentMediaItemSeekable
                hasTracks = it.currentTracks.hasSelectableTracks()
            }
            delay(500)
        }
    }
    // Al cerrarse el panel el foco vuelve al contenedor, que es el que escucha las teclas. Se mira
    // el panel real (que también se abre solo cuando hay error), no showInfo.
    val panelVisible = showInfo || error != null
    LaunchedEffect(panelVisible) { if (!panelVisible) root.requestRetrying() }

    // Guardar dónde quedó, al salir de la pantalla. Se usan los valores del sondeo (hasta 500 ms de
    // atraso, irrelevante para retomar) y no el player: para cuando esto corre, HlsPlayer ya lo
    // liberó en su propio onDispose.
    if (resume != null) {
        DisposableEffect(request.target) {
            onDispose { resume.onLeave(position, duration) }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(root)
            .onKeyEvent { event ->
                val player = playerRef ?: return@onKeyEvent false
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    // Teclas de medios dedicadas: actúan siempre, tenga el foco quien lo tenga.
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        player.togglePlayPause(); playing = player.wantsToPlay; showInfo = true; activity++; true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekBack(); showInfo = true; activity++; true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player.seekForward(); showInfo = true; activity++; true }
                    // Con el panel abierto estas teclas son del panel (mover el foco / activar el
                    // botón enfocado), así que no se consumen acá; cerrado, lo abren.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    -> if (panelVisible) false else { showInfo = true; activity++; true }
                    // Con el panel abierto, arriba/abajo van de la barra de progreso a los botones
                    // (lo resuelve PlayerControls) o entre las listas de pistas; cerrado, lo abren.
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
                        if (panelVisible) false else { showInfo = true; activity++; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        LaunchedEffect(request.target) { CopyProtection.onNonLive() }
        HlsPlayer(
            key = request.target, live = false,
            resolve = { source.playbackUrl(request.target) },
            recover = source::recover,
            onError = { error = it?.let { text.playbackError } },
            startPositionMs = resume?.startMs ?: 0L,
        ) { player ->
            LaunchedEffect(player) { playerRef = player }
            if (panelVisible) PlayerScrim {
                // Sin X_APP_CONFIRM_COLOR, el accent de la marca -- el mismo criterio que la guía.
                // Antes caía a colors.focus, que es el amarillo de los campos de texto y no tiene
                // nada que ver con el player: se veía como un color elegido a propósito cuando en
                // realidad era "el operador no configuró esto".
                val accentColor = HospitalityColors.parseHex(confirmColorHex) ?: HospitalityTheme.colors.accent
                Text(
                    request.title, style = HospitalityTheme.typography.title, color = PlayerInk,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                if (showTracks) {
                    TrackSelector(player, text, accentColor) { showTracks = false; activity++ }
                } else {
                    PlayerControls(
                        playing = playing,
                        accentColor = accentColor,
                        text = text,
                        seekable = seekable,
                        position = position,
                        duration = duration,
                        onPlayPause = { player.togglePlayPause(); playing = player.wantsToPlay; activity++ },
                        onSeekBack = { player.seekBack(); activity++ },
                        onSeekForward = { player.seekForward(); activity++ },
                        onSeekTo = { player.seekTo(it); position = it; activity++ },
                        onShowTracks = if (hasTracks) ({ showTracks = true; activity++ }) else null,
                        onInteraction = { activity++ },
                    )
                }
                error?.let {
                    Text(
                        it, style = HospitalityTheme.typography.body, color = accentColor,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
