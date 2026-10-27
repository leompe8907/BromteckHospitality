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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import com.networkbroadcast.hospitality.tv.player.BitrateLimitter
import com.networkbroadcast.hospitality.tv.player.LimitTrackSelection
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.panaccess.android.exoplayer.PanHlsExtractorFactory
import androidx.media3.ui.AspectRatioFrameLayout
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

// Vigilancia de reproducción trabada, portada de VideoFragment#checkPlaybackStall() del base
// (Bromteckotlin). El player no avisa de nada de esto: se queda mudo y la pantalla en negro.
private const val STALL_CHECK_INTERVAL_MS = 2_000L
private const val STALL_BUFFERING_TIMEOUT_MS = 15_000L
private const val STALL_NO_FRAMES_TIMEOUT_MS = 8_000L
private const val STALL_MAX_RESTARTS_IN_ROW = 3

/** Tope de ventana para elegir perfil HLS: la TV es 1080p, bajar un 4K no mejora nada y tarda más. */
private const val VIEWPORT_WIDTH = 1920
private const val VIEWPORT_HEIGHT = 1080

/** Tope de arranque (ver OperatorConfig.playerStartWithLowestProfile); se suelta al primer frame. */
private const val START_MAX_BITRATE = 2_500_000L

/** Tope de resolución para el resto de la sesión si el decoder se trabó una vez (LIMITED_VIDEO_* del base). */
private const val LIMITED_VIDEO_MAX_WIDTH = 1280
private const val LIMITED_VIDEO_MAX_HEIGHT = 720

/** Alto del desvanecido del degradado por encima del contenido del player (ver PlayerScrim). */
private val SCRIM_FADE = 112.dp

/** Zapeo: mínimo entre dos sintonizaciones reales (VideoFragment del base usa 200 ms). */
private const val ZAP_MIN_GAP_MS = 200L

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
    /**
     * Reúso del decoder entre contenidos (X_PLAYER_DECODER_REUSE_ENABLED, ver
     * OperatorConfig.playerDecoderReuse). Con esto el códec no se tira y se recrea en cada zapeo
     * -- la parte más lenta de cambiar de canal en un Firestick.
     */
    decoderReuse: Boolean = false,
    /**
     * Arrancar en el perfil HLS más bajo y soltar el tope al primer frame
     * (X_PLAYER_HLS_START_LOWEST_PROFILE, ver OperatorConfig.playerStartWithLowestProfile).
     */
    startWithLowestProfile: Boolean = false,
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
    // Tope de bitrate del arranque: se pone antes de cada prepare() y se suelta al primer frame
    // (ver el listener de abajo). Copiado del base junto con LimitTrackSelection -- ver
    // tv/player/SOURCE.md.
    val bitrateLimiter = remember { BitrateLimitter() }
    // Selector propio (en vez del que arma ExoPlayer.Builder solo) para poder fijar lo que el base
    // fija en VideoFragment al crear el player: la ventana de 1080p y, con reúso de decoder, nada
    // de adaptaciones que obliguen a recrear el códec a mitad de camino.
    val trackSelector = remember {
        val selector =
            if (startWithLowestProfile) DefaultTrackSelector(context, LimitTrackSelection.Factory(bitrateLimitter = bitrateLimiter))
            else DefaultTrackSelector(context)
        selector.apply {
            parameters = buildUponParameters()
                .setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, false)
                .setAllowVideoMixedMimeTypeAdaptiveness(!decoderReuse)
                .setAllowVideoNonSeamlessAdaptiveness(!decoderReuse)
                .setExceedVideoConstraintsIfNecessary(true)
                .build()
        }
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            // Corrección fina del retraso respecto al vivo: con el default (5 s) dos TVs del mismo
            // hotel viendo el mismo canal podían quedar hasta 5 s corridas; con 500 ms se alinean
            // solas ajustando la velocidad entre 0,97 y 1,03 (mismos valores que VideoFragment).
            .setLivePlaybackSpeedControl(
                DefaultLivePlaybackSpeedControl.Builder()
                    .setMaxLiveOffsetErrorMsForUnitSpeed(500)
                    .setFallbackMinPlaybackSpeed(0.97f)
                    .setFallbackMaxPlaybackSpeed(1.03f)
                    .build()
            )
            .build()
            .apply {
                playWhenReady = true
                // El decoder queda vivo entre un contenido y el siguiente (como
                // newPlayer.setForegroundMode(decoderReuse) del base).
                setForegroundMode(decoderReuse)
            }
    }
    val currentKey by rememberUpdatedState(key)
    // La vista real del player, para poder loguear su geometría (ver onVideoSizeChanged).
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    // Cuándo empezó la sintonización actual, para medir contra el primer frame (ver el listener).
    var tuningStartedAt by remember { mutableLongStateOf(0L) }
    // Reinicios pedidos por el watchdog de abajo. Clave aparte de [attempt] para no gastarle los
    // reintentos por error al player: un atasco no es un error y se cuenta por su lado.
    var restart by remember(key) { mutableIntStateOf(0) }

    DisposableEffect(player) {
        // El listener se registra una sola vez (la clave es el player, no el canal), así que no
        // puede capturar [key] directamente: se quedaría con el canal del primer zapeo y todas
        // las líneas de abajo nombrarían a ese, aunque midan otro.
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                // El aspecto de píxel importa tanto como la resolución: un SD de 768x484 con
                // píxeles no cuadrados puede ser 16:9 correcto, pero solo si el stream lo declara.
                // Si no lo declara (ratio 1.0), el PlayerView lo dibuja apaisado al revés y la
                // imagen se "acomoda" recién al saltar al perfil 1080p.
                val ratio = videoSize.pixelWidthHeightRatio
                val display = videoSize.width * ratio / videoSize.height.coerceAtLeast(1)
                android.util.Log.i(
                    TAG,
                    "$currentKey: video ${videoSize.width}x${videoSize.height} " +
                        "pixelRatio=$ratio aspecto=${"%.3f".format(display)}",
                )
                // Y cómo quedó medida la vista: si el PlayerView, su content frame y la superficie
                // no miden lo mismo, el video se dibuja corrido respecto de la pantalla y el
                // problema es de layout, no del stream ni del decoder. Se mira en el siguiente
                // frame (post) porque el re-layout por el tamaño nuevo todavía no ocurrió.
                playerViewRef.value?.let { view ->
                    view.post {
                        val frame = view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_content_frame)
                        val surface = (frame as? android.view.ViewGroup)?.getChildAt(0)
                        android.util.Log.i(
                            TAG,
                            "$currentKey: geometría view=${view.width}x${view.height} " +
                                "frame=${frame?.width}x${frame?.height} " +
                                "surface=${surface?.width}x${surface?.height} " +
                                "(${surface?.javaClass?.simpleName}) resizeMode=${view.resizeMode}",
                        )
                    }
                }
            }
            // Qué perfiles (variantes) trae el stream y cuál está sonando, marcado con *. Se
            // dispara en cada cambio de selección, así que es lo que dice si la calidad sube
            // después del arranque -- onVideoSizeChanged no sirve para eso: dos perfiles pueden
            // tener la misma resolución y distinto bitrate, y ahí no avisa nada.
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val perfiles = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        (0 until group.length).map { i ->
                            val format = group.getTrackFormat(i)
                            val kbps = if (format.bitrate == Format.NO_VALUE) "?" else "${format.bitrate / 1000}"
                            "${format.width}x${format.height}@${kbps}kbps${if (group.isTrackSelected(i)) "*" else ""}"
                        }
                    }
                android.util.Log.i(TAG, "$currentKey: perfiles=${perfiles.joinToString(" ")} (* = sonando)")
            }
            override fun onRenderedFirstFrame() {
                android.util.Log.i(TAG, "$currentKey: primer frame en ${SystemClock.elapsedRealtime() - tuningStartedAt} ms")
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_READY) return
                android.util.Log.i(TAG, "$currentKey: listo; pistas=${player.currentTracks.groups.map { it.type }}")
                // Ya hay imagen: se suelta el tope del arranque y la calidad sube sola.
                if (startWithLowestProfile) bitrateLimiter.limitMaxBitrate(Long.MAX_VALUE)
            }
            // Quién arranca y quién frena la reproducción, con el motivo que da Media3. Es la única
            // forma de distinguir "lo pausó el huésped" de "lo pausó el sistema" (pérdida de foco de
            // audio, auriculares desconectados, una app remota) sin adivinar.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                android.util.Log.i(TAG, "$currentKey: playWhenReady=$playWhenReady (${playWhenReadyReason(reason)})")
            }
            // El otro camino por el que el video se frena sin que playWhenReady cambie.
            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                android.util.Log.i(TAG, "$currentKey: reproducción suprimida, motivo=$reason")
            }
            override fun onPlayerError(e: PlaybackException) {
                android.util.Log.w(TAG, "error ${e.errorCodeName} (intento $attempt): ${e.message}")
                if (attempt < MAX_RETRIES) attempt++ else onError(e.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release(); CopyProtection.detach() }
    }

    LaunchedEffect(key, attempt, restart) {
        onError(null)
        // Cuánto tarda cada tramo del cambio de canal: es la única forma de saber si el negro que
        // se ve es el CAS (resolver la URL), la red (manifiesto + primer segmento) o el decoder.
        val startedAt = SystemClock.elapsedRealtime()
        tuningStartedAt = startedAt
        // Cerrar lo anterior ANTES de pedir la URL nueva. El DRM de Panaccess entrega un stream por
        // sesión: si el player sigue consumiendo el canal anterior, la URL del siguiente puede venir
        // inservible y el canal queda en negro sin dar error (se nota al volver a un canal que ya se
        // había sintonizado en esta misma sesión del player: 2 → 1 → 2). El base hace exactamente
        // esto entre videos (VideoFragment: stop() + clearMediaItems() antes del próximo
        // setMediaSource()); acá faltaba porque el ExoPlayer se reusa para todo el zapping.
        if (player.mediaItemCount > 0) {
            android.util.Log.i(TAG, "$key: cerrando el stream anterior antes de pedir la URL")
            // Con reúso de decoder no se llama stop(): es justo lo que tira el códec. El base hace
            // la misma distinción (VideoFragment: `if (!decoderReuse) exoPlayer.stop()` y siempre
            // clearMediaItems()).
            if (!decoderReuse) player.stop()
            player.clearMediaItems()
        }
        if (attempt > 0) delay(1_000L * attempt)
        // Desde el segundo error se sospecha de la sesión (p. ej. 401): se recupera antes de pedir
        // la URL de nuevo, como VideoFragment del base al tercer fallo.
        if (attempt >= 2) recover()
        val url = resolve()
        android.util.Log.i(
            TAG,
            "$key: url ${if (url == null) "no resuelta" else "ok"} en ${SystemClock.elapsedRealtime() - startedAt} ms " +
                "(reúso de decoder=$decoderReuse, arranque en perfil bajo=$startWithLowestProfile)",
        )
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
        // stop() deja el player en IDLE pero no toca playWhenReady; se reafirma por las dudas de
        // que algo (pérdida de foco de audio, una tecla de medios) lo haya dejado en false.
        player.playWhenReady = true
        if (startWithLowestProfile) bitrateLimiter.limitMaxBitrate(START_MAX_BITRATE)
        player.prepare()
    }

    /**
     * Al cambiar de contenido, la caja del video vuelve a ocupar toda la pantalla.
     *
     * El `AspectRatioFrameLayout` del PlayerView solo se entera de la relación del video cuando
     * llega `onVideoSizeChanged`, o sea 1,5-2 s después de empezar a sintonizar. Hasta entonces
     * conservaba la del canal anterior, así que los primeros frames del canal nuevo entraban en la
     * caja del viejo y recién después saltaban a la suya (se veía como una imagen corrida que "se
     * acomoda sola" al terminar de cargar). Con relación 0 el frame no recorta nada y ocupa el
     * tamaño completo, que es lo correcto mientras no se sabe qué viene.
     */
    LaunchedEffect(key) {
        val frame = playerViewRef.value
            ?.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            ?: return@LaunchedEffect
        frame.setAspectRatio(0f)
    }

    /**
     * Vigilancia de reproducción trabada: port de VideoFragment#checkPlaybackStall() del base.
     * ExoPlayer no informa nada de esto -- se queda callado y la pantalla en negro:
     * - un canal en vivo que llega a STATE_ENDED,
     * - un STATE_BUFFERING que no termina nunca (el caso de volver a un canal ya sintonizado:
     *   la URL del DRM viene inservible y no hay error),
     * - un STATE_READY que no dibuja ningún frame nuevo (decoder trabado).
     * Se corta a los [STALL_MAX_RESTARTS_IN_ROW] reinicios seguidos para no quedar en bucle.
     */
    LaunchedEffect(key, attempt, restart) {
        if (restart >= STALL_MAX_RESTARTS_IN_ROW) return@LaunchedEffect
        var bufferingSince = 0L
        var lastRendered = -1
        var lastFrameAt = 0L
        fun reset() { bufferingSince = 0L; lastRendered = -1; lastFrameAt = 0L }
        while (true) {
            delay(STALL_CHECK_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            if (!player.playWhenReady) { reset(); continue }
            val stall = when (player.playbackState) {
                Player.STATE_ENDED -> if (live) "canal en vivo en STATE_ENDED" else null
                Player.STATE_BUFFERING -> {
                    if (bufferingSince == 0L) { bufferingSince = now; null }
                    else (now - bufferingSince)
                        .takeIf { it > STALL_BUFFERING_TIMEOUT_MS }
                        ?.let { "en STATE_BUFFERING hace $it ms" }
                }
                Player.STATE_READY -> {
                    bufferingSince = 0L
                    // Sin pista de video (radio) no hay frames que contar y no hay nada que vigilar.
                    val counters = player.videoDecoderCounters
                    if (player.videoFormat == null || counters == null) { lastRendered = -1; null }
                    else {
                        counters.ensureUpdated()
                        val rendered = counters.renderedOutputBufferCount
                        if (rendered != lastRendered) { lastRendered = rendered; lastFrameAt = now; null }
                        else (now - lastFrameAt)
                            .takeIf { lastFrameAt > 0L && it > STALL_NO_FRAMES_TIMEOUT_MS }
                            ?.let { "sin frames nuevos hace $it ms (descartados: ${counters.droppedBufferCount})" }
                    }
                }
                else -> { reset(); null }
            }
            if (stall != null) {
                android.util.Log.w(TAG, "$key: reproducción trabada ($stall), reiniciando ${restart + 1}/$STALL_MAX_RESTARTS_IN_ROW")
                // Si lo que se trabó fue el decoder, se limita a 720p por el resto de la sesión:
                // en algunas TVs (decoder OMX.realtek del base) el salto adaptativo a 1080p con
                // otro perfil H.264 reinicia el decoder y descarta todos los frames sin dar error.
                // Es VideoFragment#applyVideoSizeLimit() con decoderStallDetected = true.
                if (stall.startsWith("sin frames")) {
                    android.util.Log.w(TAG, "$key: decoder trabado, limitando a ${LIMITED_VIDEO_MAX_WIDTH}x$LIMITED_VIDEO_MAX_HEIGHT por el resto de la sesión")
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setMaxVideoSize(LIMITED_VIDEO_MAX_WIDTH, LIMITED_VIDEO_MAX_HEIGHT)
                        .build()
                }
                restart++
                return@LaunchedEffect
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it)
                    .apply { useController = false; this.player = player; keepScreenOn = true }
                    .also { view -> playerViewRef.value = view }
            },
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
    /** Ver [HlsPlayer]: X_PLAYER_DECODER_REUSE_ENABLED. */
    decoderReuse: Boolean = true,
    /** Ver [HlsPlayer]: X_PLAYER_HLS_START_LOWEST_PROFILE. */
    startWithLowestProfile: Boolean = true,
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
    // Canal que el player sintoniza, distinto del que muestra la tarjeta: la tarjeta cambia con
    // cada tecla (se siente instantáneo) y al DRM se le pide una URL recién cuando el huésped deja
    // de zapear. Es la regla del base (VideoFragment#playEncryptedService: si el último cambio fue
    // hace menos de 200 ms, difiere el arranque otros 200 ms) -- así una pasada rápida por 10
    // canales no dispara 10 sintonizaciones que se cancelan entre ellas a mitad de camino.
    var tunedId by remember { mutableStateOf(channel.id) }
    var lastSwitchAt by remember { mutableLongStateOf(0L) }
    val tuned = channels.firstOrNull { it.id == tunedId } ?: channel

    LaunchedEffect(channel.id) {
        onChannelChanged(channel.id)
        if (channel.id != tunedId) {
            if (SystemClock.elapsedRealtime() - lastSwitchAt < ZAP_MIN_GAP_MS) delay(ZAP_MIN_GAP_MS)
            lastSwitchAt = SystemClock.elapsedRealtime()
            tunedId = channel.id
        }
        nowNext = null to null
        nowNext = source.schedule(channel.id).nowAndNext()
    }
    // "Hay imagen en pantalla", por evento y no sondeando cada 500 ms: el spinner tapa el video,
    // así que tiene que apagarse en el frame en que el video aparece, no hasta medio segundo
    // después. Solo lo prende el primer frame; un STATE_BUFFERING no lo apaga, si no el spinner
    // aparecería encima de la imagen en cada rebuffereo de un segundo.
    DisposableEffect(playerRef) {
        val current = playerRef ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendering = true }
        }
        current.addListener(listener)
        onDispose { current.removeListener(listener) }
    }
    LaunchedEffect(tuned.id) { rendering = false }
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
        LaunchedEffect(tuned.id) { CopyProtection.onLiveStream(tuned.id) }
        HlsPlayer(
            key = tuned.id, live = true, decoderReuse = decoderReuse,
            startWithLowestProfile = startWithLowestProfile,
            resolve = { source.playbackUrl(Playable.Live(tuned.id)) },
            recover = source::recover,
            onError = { error = it?.let { text.playbackError } },
        ) { player ->
            LaunchedEffect(player) { playerRef = player }
            if (!rendering && error == null) PlayerSpinner(confirmColorHex)
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

/**
 * Que el canal/película está cargando. Chico y centrado: el hueco negro entre un contenido y el
 * siguiente es inevitable (ver los tiempos de Hospitality.Player), pero sin nada en pantalla se
 * lee como que la TV se colgó.
 */
@Composable
private fun BoxScope.PlayerSpinner(confirmColorHex: String?) {
    CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center).size(56.dp),
        color = HospitalityColors.parseHex(confirmColorHex) ?: HospitalityTheme.colors.accent,
        strokeWidth = 4.dp,
    )
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
    /** Ver [HlsPlayer]: X_PLAYER_DECODER_REUSE_ENABLED. */
    decoderReuse: Boolean = true,
    /** Ver [HlsPlayer]: X_PLAYER_HLS_START_LOWEST_PROFILE. */
    startWithLowestProfile: Boolean = true,
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
    // Buffereando con intención de reproducir -- distinto de "pausado": el spinner no tiene que
    // aparecer sobre una película que el huésped pausó a propósito.
    var buffering by remember { mutableStateOf(true) }
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
                buffering = it.playbackState == Player.STATE_BUFFERING && it.wantsToPlay
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
            key = request.target, live = false, decoderReuse = decoderReuse,
            startWithLowestProfile = startWithLowestProfile,
            resolve = { source.playbackUrl(request.target) },
            recover = source::recover,
            onError = { error = it?.let { text.playbackError } },
            startPositionMs = resume?.startMs ?: 0L,
        ) { player ->
            LaunchedEffect(player) { playerRef = player }
            if (buffering && error == null) PlayerSpinner(confirmColorHex)
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
