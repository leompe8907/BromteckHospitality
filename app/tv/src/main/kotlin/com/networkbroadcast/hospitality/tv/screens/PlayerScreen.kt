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
import androidx.compose.foundation.layout.height
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

/** Recuadro inferior con la información de lo que se ve. */
@Composable
private fun BoxScope.InfoPanel(content: @Composable () -> Unit) {
    val colors = HospitalityTheme.colors
    Column(
        Modifier.align(Alignment.BottomStart).padding(40.dp).width(640.dp)
            .clip(RoundedCornerShape(14.dp)).background(colors.background.copy(alpha = 0.88f)).padding(20.dp),
    ) { content() }
}

/** TV en vivo a pantalla completa. Arriba/abajo (o CH+/CH-) cambian de canal; OK muestra la info; atrás vuelve. */
@Composable
fun PlayerScreen(
    source: EntertainmentSource,
    channels: List<Channel>,
    startChannelId: String?,
    text: UiText,
    onChannelChanged: (String) -> Unit,
) {
    if (channels.isEmpty()) return CenteredMessage(text.noChannels)
    var index by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var nowNext by remember { mutableStateOf<Pair<Program?, Program?>>(null to null) }
    val focus = rememberInitialFocus()
    val channel = channels[index]

    LaunchedEffect(channel.id) {
        onChannelChanged(channel.id)
        nowNext = null to null
        nowNext = source.schedule(channel.id).nowAndNext()
    }
    LaunchedEffect(index, showInfo) { if (showInfo) { delay(5_000); showInfo = false } }

    fun zap(step: Int) { index = (index + step).mod(channels.size); showInfo = true }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> { showInfo = !showInfo; true }
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
        ) {
            if (showInfo || error != null) InfoPanel {
                val colors = HospitalityTheme.colors
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.number}", style = HospitalityTheme.typography.title, color = colors.accent, modifier = Modifier.width(80.dp))
                    Text(channel.name, style = HospitalityTheme.typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                nowNext.first?.let { ProgramLine(text.nowLabel, it, highlight = true) }
                nowNext.second?.let { ProgramLine(text.nextLabel, it, highlight = false) }
                error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
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

/** Catchup y VOD: OK pausa/reanuda, izquierda/derecha retroceden/avanzan 10 s, atrás vuelve. */
@Composable
fun MediaPlayerScreen(source: EntertainmentSource, request: MediaRequest, text: UiText) {
    var error by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    val focus = rememberInitialFocus()

    LaunchedEffect(showInfo, position / 5_000) { if (showInfo && playerRef?.isPlaying == true) { delay(4_000); showInfo = false } }
    LaunchedEffect(playerRef) {
        while (true) {
            playerRef?.let { position = it.currentPosition; duration = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L }
            delay(500)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { event ->
                val player = playerRef ?: return@onKeyEvent false
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play(); showInfo = true; true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { player.seekBack(); showInfo = true; true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { player.seekForward(); showInfo = true; true }
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
            if (showInfo || error != null) InfoPanel {
                val colors = HospitalityTheme.colors
                Text(request.title, style = HospitalityTheme.typography.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (duration > 0) {
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceBorder)) {
                        Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(6.dp).background(colors.accent))
                    }
                    Text("${clock(position)} / ${clock(duration)}", style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
                error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
