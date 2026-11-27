package com.networkbroadcast.hospitality.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.networkbroadcast.hospitality.brand.Features
import com.networkbroadcast.hospitality.brand.LobbyText
import com.networkbroadcast.hospitality.companion.TvCommand
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.CatchupCatalog
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Episode
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.Season
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.entertainment.nowAndNext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.text.DateFormat
import java.util.Date

/** Las pestañas del contenido de Panaccess; cada una depende de un flag de la marca (ver [contentTabs]). */
enum class ContentTab { Live, Guide, Catchup, Vod }

/**
 * Qué pestañas ofrece la marca según por dónde se entró ([entry]). Desde TV en vivo: canales y guía con liveTv (la
 * guía además con guide); "Eventos anteriores" (catch-up) es de la guía y solo aparece si Panaccess devolvió algo para
 * ver en diferido ([hasCatchup]). Películas no va ahí: tiene su propio tile en la home y se abre sola, sin pestañas.
 */
fun contentTabs(features: Features, hasCatchup: Boolean = false, entry: ContentTab = ContentTab.Live): List<ContentTab> =
    if (entry == ContentTab.Vod) listOfNotNull(ContentTab.Vod.takeIf { features.vod })
    else buildList {
        if (features.liveTv) add(ContentTab.Live)
        if (features.liveTv && features.guide) add(ContentTab.Guide)
        if (features.liveTv && features.guide && hasCatchup) add(ContentTab.Catchup)
    }

private const val REFRESH_EVERY_MS = 10 * 60 * 1000L
private const val SCHEDULE_TTL_MS = 30 * 60 * 1000L

/**
 * Catálogo de Panaccess para el celular: canales, películas, catch-up y la guía de cada canal,
 * cargados una sola vez para toda la app (volver a la pantalla no vuelve a pedir nada). Primero se
 * muestra el snapshot local y después se refresca, igual que la TV. También guarda el "seguir viendo".
 */
@Stable
class ContentModel(
    private val source: EntertainmentSource,
    private val vodEnabled: Boolean,
    private val catchupEnabled: Boolean = false,
    private val resumeStore: ResumeStore? = null,
) {
    var channels by mutableStateOf<ChannelsResult?>(null)
        private set
    var vod by mutableStateOf<VodResult?>(null)
        private set
    var catchup by mutableStateOf<CatchupCatalog?>(null)
        private set
    var resume by mutableStateOf(if (vodEnabled) resumeStore?.load().orEmpty() else emptyList())
        private set
    /** Lo último que se puso a ver en el celular, para "Mandar a la TV" del control remoto. */
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
    private var refreshedAt by mutableLongStateOf(0L)
    private val schedules = HashMap<String, Pair<Long, List<Program>>>()
    // La guía de decenas de canales visibles a la vez no se pide toda junta (la TV hace igual).
    private val scheduleSlots = Semaphore(4)

    /** Hay algo para ver en diferido. */
    val hasCatchup: Boolean
        get() = catchup?.programsByChannel?.values?.any { list -> list.any { it.catchupId != null } } == true

    // La home y la pantalla de contenido piden la carga a la vez: la segunda espera y usa lo que trajo la primera.
    private val loadLock = Mutex()

    suspend fun load() { loadLock.withLock { loadLocked() } }

    private suspend fun loadLocked() {
        if (channels == null) channels = attempt { source.cachedChannels() }
        if (vodEnabled && vod == null) vod = attempt { source.cachedVodShelves() }
        if (catchupEnabled && catchup == null) catchup = attempt { source.cachedCatchupCatalog() }
        if (channels != null && System.currentTimeMillis() - refreshedAt < REFRESH_EVERY_MS) return
        coroutineScope {
            val freshVod = if (vodEnabled) async { attempt { source.refreshVodShelves() } } else null
            attempt { source.refreshChannels() }?.let { channels = it }
            // El catch-up usa los canales ya frescos (como la TV: primero canales, después catch-up).
            if (catchupEnabled) attempt { source.catchupCatalog() }?.let { fresh ->
                if (fresh.channels.isNotEmpty() || fresh.failure == null || catchup == null) catchup = fresh
            }
            freshVod?.await()?.let { vod = it }
        }
        refreshedAt = System.currentTimeMillis()
    }

    /** Lo ya pedido, sin red: para pintar sin parpadeo al volver a una fila. */
    fun cachedSchedule(channelId: String): List<Program>? =
        schedules[channelId]?.takeIf { System.currentTimeMillis() - it.first < SCHEDULE_TTL_MS }?.second

    suspend fun schedule(channelId: String): List<Program> {
        cachedSchedule(channelId)?.let { return it }
        val list = scheduleSlots.withPermit { attempt { source.schedule(channelId) } }.orEmpty()
        if (list.isNotEmpty()) schedules[channelId] = System.currentTimeMillis() to list
        return list
    }

    suspend fun seasons(seriesId: String): List<Season> = attempt { source.seasons(seriesId) }.orEmpty()

    // Datos con que se guarda cada contenido en curso (título, episodio, póster), por id.
    private val meta = HashMap<String, Triple<String, String?, String?>>()

    fun track(vodId: String, title: String, subtitle: String?, posterUrl: String?) { meta[vodId] = Triple(title, subtitle, posterUrl) }

    /** Avance de [target] (solo películas/episodios): se guarda, o se borra al terminar. Ver [ResumePolicy]. */
    fun saveProgress(target: Playable, positionMs: Long, durationMs: Long) {
        val id = (target as? Playable.Vod)?.vodId ?: return
        val (title, subtitle, poster) = meta[id] ?: return
        val updated = resume.afterProgress(id, title, subtitle, poster, positionMs, durationMs, System.currentTimeMillis())
        if (updated != resume) { resume = updated; resumeStore?.save(updated) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
}

/** Lo que se está reproduciendo en la pantalla de contenido. */
private data class PlayRequest(
    val target: Playable,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val live: Boolean,
    /** Dónde arrancar (seguir viendo). */
    val startMs: Long = 0L,
)

// Tamaños pensados para el celular; el tema trae los de la TV y no se modifica.
@Composable private fun titleStyle() = HospitalityTheme.typography.title.copy(fontSize = 28.sp)
@Composable private fun rowTitleStyle() = HospitalityTheme.typography.label.copy(fontSize = 15.sp)
@Composable private fun sectionStyle() = HospitalityTheme.typography.section.copy(fontSize = 14.sp)
@Composable private fun smallStyle() = HospitalityTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp)
@Composable private fun captionStyle() = HospitalityTheme.typography.label.copy(fontSize = 12.sp)

private fun nowSec() = System.currentTimeMillis() / 1000

/** Imagen de relleno cuando falta el póster o el logo (o mientras carga): ver [rememberPlaceholder]. */
private val LocalPlaceholder = staticCompositionLocalOf<Painter> { ColorPainter(androidx.compose.ui.graphics.Color.Gray) }

/**
 * La que configura el operador (X_APP_PLACEHOLDER) si hay; si no, el gris del tema
 * ([com.networkbroadcast.hospitality.designsystem.HospitalityColors.placeholderPainter]). Igual que la TV.
 */
@Composable
private fun rememberPlaceholder(imageUrl: String?): Painter {
    val colors = HospitalityTheme.colors
    if (imageUrl != null) return rememberAsyncImagePainter(model = imageModel(imageUrl))
    return remember(colors) { colors.placeholderPainter() }
}

/** Imagen con el relleno del operador (o el gris del tema) mientras carga, si falla o si no hay url. */
@Composable
private fun PlaceholderImage(url: String?, description: String?, scale: ContentScale, modifier: Modifier = Modifier) {
    val placeholder = LocalPlaceholder.current
    AsyncImage(
        model = url?.let(::imageModel), contentDescription = description, contentScale = scale, modifier = modifier,
        placeholder = placeholder, error = placeholder, fallback = placeholder,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Pantalla completa horizontal mientras [active]: gira a horizontal y esconde las barras del sistema.
 * La Activity no se recrea al girar (configChanges en el manifest). Al salir, vuelve a vertical y
 * devuelve las barras; fuera de la pantalla completa la app es siempre vertical.
 */
@Composable
private fun FullscreenEffect(active: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(active) {
        val activity = context.findActivity()
        val window = activity?.window
        if (!active || activity == null || window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // El video llega al borde también en los celulares con muesca.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }
}

/**
 * Canales, guía, eventos anteriores y películas/series con el player arriba. Los colores son los
 * del tema (ya con los del operador).
 *
 * [onHandlesBack] avisa a quien maneja "atrás" cuando esta pantalla necesita el botón para salir de
 * la pantalla completa o de una serie, en vez de volver a la home.
 */
@Composable
fun ContentScreen(
    model: ContentModel,
    source: EntertainmentSource,
    text: UiText,
    contentText: ContentText,
    features: Features,
    initialTab: ContentTab,
    placeholderUrl: String?,
    /** Texto de "Mandar a la TV"; el botón sale con un canal o una película (un evento anterior no se puede mandar). */
    sendToTvLabel: String,
    onSendToTv: (NowPlaying) -> Unit,
    onHandlesBack: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val tabs = contentTabs(features, model.hasCatchup, entry = initialTab)
    if (tabs.isEmpty()) return
    var tab by remember { mutableStateOf(initialTab.takeIf { it in tabs } ?: tabs.first()) }
    var selected by remember { mutableStateOf<PlayRequest?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var openSeries by remember { mutableStateOf<VodItem?>(null) }
    LaunchedEffect(model) { model.load() }
    // "Ahora" se mueve: la barra de avance y el programa al aire se recalculan cada medio minuto.
    val now by produceState(nowSec()) { while (true) { delay(30_000); value = nowSec() } }
    val selectedChannelId = (selected?.target as? Playable.Live)?.channelId
    val selectedVodId = (selected?.target as? Playable.Vod)?.vodId
    val placeholder = rememberPlaceholder(placeholderUrl)
    val locale = LocalAppLocale.current
    val lobbyText = remember(locale) { LobbyText.of(locale.language) }

    FullscreenEffect(fullscreen)
    val handlesBack = fullscreen || openSeries != null
    SideEffect { onHandlesBack(handlesBack) }
    DisposableEffect(Unit) { onDispose { onHandlesBack(false) } }
    BackHandler(enabled = handlesBack) { if (fullscreen) fullscreen = false else openSeries = null }

    fun playChannel(channel: Channel) {
        selected = PlayRequest(Playable.Live(channel.id), "${channel.number}  ${channel.name}", live = true)
        model.nowPlaying = NowPlaying(channel.name, channel.logoUrl, TvCommand.PlayChannel(channel.id))
    }
    fun playVod(id: String, title: String, subtitle: String?, description: String?, poster: String?, startMs: Long = 0L) {
        model.track(id, title, subtitle, poster)
        selected = PlayRequest(Playable.Vod(id), title, subtitle = subtitle, description = description, live = false, startMs = startMs)
        model.nowPlaying = NowPlaying(title, poster, TvCommand.PlayVod(id))
    }
    fun openVod(item: VodItem) {
        if (item.isSeries) { openSeries = item; return }
        val meta = listOfNotNull(item.year?.toString(), item.durationMinutes?.let { "$it ${text.minutes}" }).joinToString(" · ")
        playVod(item.id, item.title, meta.ifEmpty { null }, item.synopsis, item.posterUrl)
    }
    fun playEpisode(series: VodItem, season: Season, episode: Episode) {
        val label = contentText.episodeLabel(season.number, episode.number)
        playVod(episode.id, series.title, listOf(label, episode.title).filter { it.isNotBlank() }.joinToString(" · "), episode.synopsis, series.posterUrl)
    }
    fun resumeVod(entry: ResumeEntry) =
        playVod(entry.vodId, entry.title, entry.subtitle, null, entry.posterUrl, ResumePolicy.startPosition(entry))
    fun playCatchup(channel: Channel, program: Program) {
        val id = program.catchupId ?: return
        selected = PlayRequest(
            Playable.Catchup(id), program.title,
            subtitle = "${channel.name} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(program.startEpochSec * 1000))}",
            description = program.description, live = false,
        )
        model.nowPlaying = null
    }

    // Programa al aire del canal que se ve (para el título, el horario y la barra del player).
    val liveTarget = selected?.target as? Playable.Live
    val liveSchedule by produceState<List<Program>?>(liveTarget?.let { model.cachedSchedule(it.channelId) }, liveTarget?.channelId) {
        value = liveTarget?.let { model.schedule(it.channelId) }
    }
    val (liveNow, liveNext) = liveSchedule.orEmpty().nowAndNext(now)
    val timeFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    fun clock(epochSec: Long) = timeFormat.format(Date(epochSec * 1000))

    CompositionLocalProvider(LocalPlaceholder provides placeholder) {
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) TopBar(tabLabel(tab, text), onBack = { if (openSeries != null) openSeries = null else onBack() })
            selected?.let { request ->
                val overlay = when {
                    request.live -> PlayerOverlay(
                        badge = contentText.liveBadge,
                        range = liveNow?.let { "${clock(it.startEpochSec)} – ${clock(it.endEpochSec)}" },
                    )
                    request.target is Playable.Catchup -> PlayerOverlay(badge = text.catchUp)
                    else -> PlayerOverlay(badge = contentText.tileMovies)
                }
                // Mismo lugar en el árbol dentro y fuera de la pantalla completa: el player no se recrea.
                Column(if (fullscreen) Modifier.fillMaxSize() else Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)) {
                    MobilePlayer(
                        source = source, target = request.target, live = request.live, text = text, contentText = contentText, overlay = overlay,
                        fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen },
                        modifier = if (fullscreen) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                        startPositionMs = request.startMs,
                        onProgress = if (request.target is Playable.Vod) model::saveProgress else null,
                        onLeave = if (request.target is Playable.Vod) model::saveProgress else null,
                    )
                    if (!fullscreen) {
                        // "Ahora": título del programa (19 sp) y debajo canal y horario (12 sp); "Siguiente" abajo.
                        // A la derecha, "Mandar a la TV" (`.nowrow` del mockup).
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (request.live) liveNow?.title ?: request.title else request.title,
                                    style = HospitalityTheme.typography.label.copy(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (request.live) {
                                    val sub = listOfNotNull(request.title, liveNow?.let { "${clock(it.startEpochSec)}–${clock(it.endEpochSec)}" }).joinToString(" · ")
                                    Text(sub, style = HospitalityTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                    liveNext?.let {
                                        Text("${text.nextLabel}: ${it.title} · ${clock(it.startEpochSec)}", style = HospitalityTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                    }
                                } else {
                                    request.subtitle?.let { Text(it, style = HospitalityTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)) }
                                    request.description?.let { Text(it, style = smallStyle(), color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                                }
                            }
                            model.nowPlaying?.takeIf { request.target !is Playable.Catchup }?.let { now -> SendToTvButton(sendToTvLabel) { onSendToTv(now) } }
                        }
                    }
                }
            }
            // Antes de elegir un canal, la vista previa del Lobby del primero; tocarla lo pone en el mismo lugar.
            if (selected == null && tab == ContentTab.Live) {
                model.channels?.channels?.firstOrNull()?.let { first -> LobbyLivePreview(model, first, text, now) { playChannel(first) } }
            }
            if (!fullscreen) {
                if (tabs.size > 1) TabBar(tabs, tab, text, contentText) { tab = it; openSeries = null }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        ContentTab.Live -> LobbyLiveList(model, text, lobbyText, now, selectedChannelId, ::playChannel)
                        ContentTab.Guide -> LobbyGuideList(model, text, lobbyText, now, ::playChannel, ::playCatchup)
                        ContentTab.Catchup -> CatchupList(model, text, ::playCatchup)
                        ContentTab.Vod -> {
                            val series = openSeries
                            if (series != null) SeriesDetail(model, series, text, contentText, selectedVodId, ::playEpisode)
                            else VodShelves(model, text, contentText, selectedVodId, ::openVod, ::resumeVod)
                        }
                    }
                }
            }
        }
    }
}

private fun tabLabel(tab: ContentTab, text: UiText) = when (tab) {
    ContentTab.Live -> text.liveTv
    ContentTab.Guide -> text.guide
    ContentTab.Catchup -> text.catchUp
    ContentTab.Vod -> text.moviesAndSeries
}

/** Pestañas del mockup (`.tabs`): cápsula con el borde del tema, 34 dp por pestaña; la elegida va en el color de acento. */
@Composable
private fun TabBar(tabs: List<ContentTab>, current: ContentTab, text: UiText, contentText: ContentText, onSelect: (ContentTab) -> Unit) {
    val colors = HospitalityTheme.colors
    val outer = RoundedCornerShape(rad(18, 2))
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(outer).background(colors.surface).border(1.dp, colors.surfaceBorder, outer).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab == current
            val label = when (tab) {
                ContentTab.Live -> contentText.tileChannels
                ContentTab.Vod -> contentText.tileMovies
                else -> tabLabel(tab, text)
            }
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(rad(15, 2))).background(if (on) colors.accent else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = HospitalityTheme.typography.label.copy(fontSize = if (tabs.size > 3) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (on) HospitalityColors.readableTextColor(colors.accent) else colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Message(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = smallStyle(), color = HospitalityTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChannelLogo(channel: Channel, width: Int, height: Int) {
    Box(Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(9.dp))) {
        PlaceholderImage(channel.logoUrl, channel.name, ContentScale.Fit, Modifier.fillMaxSize().padding(2.dp))
    }
}

/** Eventos ya emitidos que Panaccess deja ver en diferido ([ContentModel.catchup]), por canal. */
@Composable
private fun CatchupList(model: ContentModel, text: UiText, onPlay: (Channel, Program) -> Unit) {
    val colors = HospitalityTheme.colors
    val catalog = model.catchup ?: return Message(text.loading)
    val rows = catalog.channels.mapNotNull { channel ->
        val programs = catalog.programsByChannel[channel.id].orEmpty().filter { it.catchupId != null }.sortedByDescending { it.startEpochSec }
        if (programs.isEmpty()) null else channel to programs
    }
    if (rows.isEmpty()) return Message(text.noSchedule)
    val locale = LocalAppLocale.current
    val format = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(rows, key = { it.first.id }) { (channel, programs) ->
            Column {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelLogo(channel, 34, 24)
                    Text(channel.name, style = rowTitleStyle().copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(programs, key = { it.catchupId ?: it.startEpochSec.toInt() }) { program ->
                        val shape = RoundedCornerShape(rad(14, 2))
                        Column(
                            Modifier.width(150.dp).clip(shape).background(colors.surface).border(1.dp, colors.surfaceBorder, shape)
                                .clickable { onPlay(channel, program) }.padding(horizontal = 11.dp, vertical = 9.dp),
                        ) {
                            Text(format.format(Date(program.startEpochSec * 1000)), style = captionStyle().copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.42.sp), color = colors.textMuted, maxLines = 1)
                            Text(program.title, style = smallStyle().copy(fontSize = 12.5.sp, lineHeight = 15.6.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodShelves(
    model: ContentModel, text: UiText, contentText: ContentText, selectedId: String?,
    onOpen: (VodItem) -> Unit, onResume: (ResumeEntry) -> Unit,
) {
    val result = model.vod ?: return Message(text.loading)
    val shelves = result.shelves.filter { it.items.isNotEmpty() }
    val resume = model.resume
    if (shelves.isEmpty() && resume.isEmpty()) return Message(result.failure?.let { "${text.noVod} ($it)" } ?: text.noVod)
    val colors = HospitalityTheme.colors
    // Chips de estantería (.chips): "Todo" o una sola estantería.
    var shelfFilter by remember { mutableStateOf<String?>(null) }
    val shown = shelves.filter { shelfFilter == null || it.title == shelfFilter }
    val featured = shelves.firstOrNull()?.items?.firstOrNull { it.backdropUrl != null || it.posterUrl != null }
    // Estructura del mockup (.vgrid): destacada de 170 dp, chips y estanterías de pósters de 112x162.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        featured?.let { item ->
            item(key = "featured") {
                val shape = RoundedCornerShape(rad(20))
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp).clip(shape).background(colors.surface).clickable { onOpen(item) }) {
                    PlaceholderImage(item.backdropUrl ?: item.posterUrl, item.title, ContentScale.Crop, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color(0x59000000)))
                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                        Text(contentText.featured.uppercase(), color = Color.White.copy(alpha = 0.8f), style = captionStyle().copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.47.sp))
                        Text(item.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = HospitalityTheme.typography.label.copy(fontSize = 26.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold))
                        val meta = listOfNotNull(item.year?.toString(), item.durationMinutes?.let { "$it ${text.minutes}" }).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.85f), style = captionStyle().copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (shelves.size > 1) item(key = "chips") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item(key = "all") { ShelfChip(contentText.all, shelfFilter == null) { shelfFilter = null } }
                items(shelves, key = { it.title }) { shelf -> ShelfChip(shelf.title, shelfFilter == shelf.title) { shelfFilter = shelf.title } }
            }
        }
        // "Seguir viendo", arriba de todo y solo si hay algo a medias.
        if (resume.isNotEmpty()) item(key = "resume") {
            Column {
                ShelfTitle(contentText.continueWatching)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(resume, key = { it.vodId }) { entry ->
                        PosterCard(entry.title, entry.subtitle, entry.posterUrl, entry.vodId == selectedId, entry.fraction) { onResume(entry) }
                    }
                }
            }
        }
        items(shown, key = { it.title }) { shelf ->
            Column {
                ShelfTitle(shelf.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shelf.items, key = { it.id }) { item ->
                        PosterCard(item.title, item.year?.toString(), item.posterUrl ?: item.backdropUrl, item.id == selectedId, null) { onOpen(item) }
                    }
                }
            }
        }
    }
}

/** Título de estantería (`.vrow h4`): 13 sp en mayúsculas, espaciado de 0,1 em. */
@Composable
private fun ShelfTitle(title: String) {
    Text(
        title.uppercase(), color = HospitalityTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = sectionStyle().copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 9.dp),
    )
}

/** Chip de estantería (`.chips span`): el elegido va con el color de texto de fondo y el del fondo de letra. */
@Composable
private fun ShelfChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(17.dp)
    Text(
        label, maxLines = 1, color = if (on) colors.background else colors.textSecondary,
        style = HospitalityTheme.typography.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(shape).background(if (on) colors.textPrimary else colors.surface)
            .then(if (on) Modifier else Modifier.border(1.dp, colors.surfaceBorder, shape))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

/** Póster con título y una segunda línea (`.vp`); con [progress] (seguir viendo) lleva una barra fina de lo visto abajo. */
@Composable
private fun PosterCard(title: String, subtitle: String?, posterUrl: String?, selected: Boolean, progress: Float?, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(14, 2))
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(162.dp).clip(shape).background(colors.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.surfaceBorder, shape),
        ) {
            PlaceholderImage(posterUrl, title, ContentScale.Crop, Modifier.fillMaxSize())
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(colors.surfaceBorder)) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(LocalPlayerColor.current))
                }
            }
        }
        Text(title, style = captionStyle().copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        subtitle?.let { Text(it, style = captionStyle().copy(fontSize = 11.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/**
 * Temporadas y episodios de una serie ([ContentModel.seasons], que sale de getVodSeriesInfo). Una
 * fila por episodio; con más de una temporada, una fila de pastillas para elegirla.
 */
@Composable
private fun SeriesDetail(
    model: ContentModel, series: VodItem, text: UiText, contentText: ContentText, selectedId: String?,
    onPlay: (VodItem, Season, Episode) -> Unit,
) {
    val colors = HospitalityTheme.colors
    val seasons by produceState<List<Season>?>(null, series.id) { value = model.seasons(series.id) }
    var chosen by remember(series.id) { mutableStateOf<Int?>(null) }
    val list = seasons ?: return Message(text.loading)
    if (list.isEmpty()) return Message(contentText.noEpisodes)
    val season = list.firstOrNull { it.number == chosen } ?: list.first()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(series.title, style = rowTitleStyle().copy(fontSize = 18.sp), color = colors.textPrimary)
                series.synopsis?.let { Text(it, style = smallStyle(), color = colors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (list.size > 1) item(key = "seasons") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                items(list, key = { it.number }) { s ->
                    val on = s.number == season.number
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) colors.accent else colors.surface)
                            .border(1.dp, if (on) colors.accent else colors.surfaceBorder, RoundedCornerShape(16.dp))
                            .clickable { chosen = s.number }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.title?.takeIf { it.isNotBlank() } ?: contentText.seasonLabel(s.number),
                            style = captionStyle().copy(fontSize = 13.sp), color = if (on) colors.background else colors.textSecondary, maxLines = 1,
                        )
                    }
                }
            }
        }
        items(season.episodes, key = { it.id }) { episode ->
            val shape = RoundedCornerShape(14.dp)
            val selected = episode.id == selectedId
            Row(
                Modifier.fillMaxWidth().clip(shape).background(colors.surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.surfaceBorder, shape)
                    .clickable { onPlay(series, season, episode) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(8.dp))) {
                    PlaceholderImage(episode.imageUrl ?: series.backdropUrl, episode.title, ContentScale.Crop, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listOf(if (episode.number > 0) "${episode.number}." else "", episode.title).filter { it.isNotBlank() }.joinToString(" "),
                        style = rowTitleStyle(), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    episode.durationMinutes?.let { Text("$it ${text.minutes}", style = captionStyle(), color = colors.textMuted) }
                }
            }
        }
    }
}
