package com.networkbroadcast.hospitality.tv.screens

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.networkbroadcast.hospitality.designsystem.OperatorCardColors
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
 * Recuadro inferior con la información de lo que se ve. Ocupa casi todo el ancho y va centrado
 * abajo, como el mock: así la barra de progreso es larga y los dos tiempos quedan en los extremos,
 * en vez de un recuadro angosto en una esquina.
 */
@Composable
private fun BoxScope.InfoPanel(cardColorHex: String?, content: @Composable () -> Unit) {
    val colors = HospitalityTheme.colors
    // Mismo color y mismo parámetro que el resto de las tarjetas (OperatorConfig.cardColorHex) --
    // antes esto usaba colors.background (el verde/turquesa de la paleta Resort) sin pasar por
    // ningún parámetro del operador, así que nunca podía dejar de verse verde.
    val panelColor = HospitalityColors.parseHex(cardColorHex) ?: colors.surface
    Column(
        Modifier.align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 40.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(panelColor.copy(alpha = 0.88f)).padding(24.dp),
        // Igual que las tarjetas de la home: con un color de panel claro, el texto y el riel de
        // progreso de adentro se recalculan para que sigan leyéndose (readableTextColor).
    ) { OperatorCardColors(cardColorHex, textColorHex = null) { content() } }
}

/**
 * TV en vivo a pantalla completa. Arriba/abajo (o CH+/CH-) cambian de canal; OK muestra el panel
 * y, con el panel abierto, OK activa el botón que tenga el foco; atrás vuelve.
 */
@Composable
fun PlayerScreen(
    source: EntertainmentSource,
    channels: List<Channel>,
    startChannelId: String?,
    text: UiText,
    // Mismo color que las tarjetas (OperatorConfig.cardColorHex); null = el de la marca.
    cardColorHex: String? = null,
    // Color de los botones del panel (OperatorConfig.playerControlColorHex); null = el de la marca.
    playerControlColorHex: String? = null,
    // Aro del botón enfocado: el mismo color que "Confirmar" (OperatorConfig.confirmColorHex), que
    // ya es el color de "la acción" en el resto de la app. null = el color de foco de la marca.
    confirmColorHex: String? = null,
    onChannelChanged: (String) -> Unit,
) {
    if (channels.isEmpty()) return CenteredMessage(text.noChannels)
    var index by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var nowNext by remember { mutableStateOf<Pair<Program?, Program?>>(null to null) }
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(true) }
    // Distinto de [playing] (que es la intención): esto es si realmente está saliendo video. El
    // panel no se auto-esconde mientras no lo esté -- ni pausado ni buffereando, así el huésped
    // ve el nombre del canal mientras carga en vez de quedarse mirando un negro sin datos.
    var rendering by remember { mutableStateOf(false) }
    // Un canal en vivo sin ventana de DVR no se puede adelantar ni atrasar: ahí el panel muestra
    // solo pausa/reproducir, para no ofrecer botones que no harían nada.
    var seekable by remember { mutableStateOf(false) }
    // Ídem con las pistas: el botón de audio/subtítulos solo aparece si el canal trae algo que elegir.
    var hasTracks by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    // Cada acción sobre el panel reinicia el temporizador que lo esconde.
    var activity by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val channel = channels[index]

    LaunchedEffect(channel.id) {
        onChannelChanged(channel.id)
        nowNext = null to null
        nowNext = source.schedule(channel.id).nowAndNext()
    }
    LaunchedEffect(playerRef) {
        while (true) {
            playerRef?.let {
                playing = it.wantsToPlay
                rendering = it.isPlaying
                seekable = it.isCurrentMediaItemSeekable
                hasTracks = it.currentTracks.hasSelectableTracks()
            }
            delay(500)
        }
    }
    // El selector de pistas se cierra solo al cambiar de canal: las del canal anterior ya no existen.
    LaunchedEffect(channel.id) { showTracks = false }
    // Mientras está pausado o cargando, o con el selector de pistas abierto, el panel se queda.
    LaunchedEffect(index, showInfo, activity, rendering, showTracks) {
        if (showInfo && rendering && !showTracks) { delay(5_000); showInfo = false }
    }
    // Al cerrarse el panel, el foco vuelve al contenedor: si no, queda en un botón que ya no está
    // y el control remoto deja de zapear. Se mira el panel real (que también se abre solo cuando
    // hay error), no showInfo: si no, al desaparecer un error el foco se quedaba en la nada.
    val panelVisible = showInfo || error != null
    LaunchedEffect(panelVisible) { if (!panelVisible) root.requestRetrying() }

    fun zap(step: Int) { index = (index + step).mod(channels.size); showInfo = true; activity++ }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(root)
            .onKeyEvent { event ->
                val player = playerRef
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    // Con el selector de pistas abierto, arriba/abajo son para moverse entre las
                    // listas de audio y subtítulos: ahí no se zapea (CH+/CH- siguen andando).
                    KeyEvent.KEYCODE_DPAD_UP -> if (showTracks) false else { zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (showTracks) false else { zap(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    // Las teclas de medios del control no pasan por el foco: andan siempre.
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player == null) false else { player.togglePlayPause(); playing = player.wantsToPlay; showInfo = true; activity++; true }
                    }
                    // Con el panel abierto, OK le llega al botón enfocado (no se consume acá);
                    // cerrado, lo abre.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                        if (panelVisible) false else { showInfo = true; activity++; true }
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
            if (panelVisible) InfoPanel(cardColorHex) {
                val colors = HospitalityTheme.colors
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.number}", style = HospitalityTheme.typography.title, color = colors.accent, modifier = Modifier.width(80.dp))
                    Text(channel.name, style = HospitalityTheme.typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                nowNext.first?.let { ProgramLine(text.nowLabel, it, highlight = true) }
                nowNext.second?.let { ProgramLine(text.nextLabel, it, highlight = false) }
                error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
                val controlColor = HospitalityColors.parseHex(playerControlColorHex) ?: colors.accent
                val focusColor = HospitalityColors.parseHex(confirmColorHex) ?: colors.focus
                if (showTracks) {
                    TrackSelector(player, text, controlColor) { showTracks = false; activity++ }
                } else {
                    PlayerControls(
                        playing = playing,
                        controlColor = controlColor,
                        focusColor = focusColor,
                        text = text,
                        seekable = seekable,
                        onPlayPause = { player.togglePlayPause(); playing = player.wantsToPlay; activity++ },
                        onSeekBack = { player.seekBack(); activity++ },
                        onSeekForward = { player.seekForward(); activity++ },
                        onShowTracks = if (hasTracks) ({ showTracks = true; activity++ }) else null,
                        // Acá arriba/abajo zapean: tienen que llegar al contenedor de la pantalla.
                        confineVertical = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramLine(label: String, program: Program, highlight: Boolean) {
    val colors = HospitalityTheme.colors
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HospitalityTheme.typography.label, color = if (highlight) colors.accent else colors.textMuted, modifier = Modifier.width(110.dp))
        Text(program.timeRange(), style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(150.dp))
        Text(program.title, style = HospitalityTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    playerControlColorHex: String? = null,
    // Mismo color que las tarjetas (OperatorConfig.cardColorHex); null = el de la marca.
    cardColorHex: String? = null,
    // Aro del elemento enfocado: el mismo color que "Confirmar" (OperatorConfig.confirmColorHex).
    confirmColorHex: String? = null,
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
        ) { player ->
            LaunchedEffect(player) { playerRef = player }
            if (panelVisible) InfoPanel(cardColorHex) {
                val colors = HospitalityTheme.colors
                Text(request.title, style = HospitalityTheme.typography.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val controlColor = HospitalityColors.parseHex(playerControlColorHex) ?: colors.accent
                val focusColor = HospitalityColors.parseHex(confirmColorHex) ?: colors.focus
                if (showTracks) {
                    TrackSelector(player, text, controlColor) { showTracks = false; activity++ }
                } else {
                    PlayerControls(
                        playing = playing,
                        controlColor = controlColor,
                        focusColor = focusColor,
                        text = text,
                        seekable = seekable,
                        position = position,
                        duration = duration,
                        onPlayPause = { player.togglePlayPause(); playing = player.wantsToPlay; activity++ },
                        onSeekBack = { player.seekBack(); activity++ },
                        onSeekForward = { player.seekForward(); activity++ },
                        onSeekTo = { player.seekTo(it); position = it; activity++ },
                        onShowTracks = if (hasTracks) ({ showTracks = true; activity++ }) else null,
                    )
                }
                error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
            }
        }
    }
}
