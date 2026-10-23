package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.CatchupCatalog
import com.networkbroadcast.hospitality.entertainment.EntertainmentSource
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.entertainment.Program
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.entertainment.VodShelf
import com.panaccess.android.streaming.shared.domain.model.Stream
import com.panaccess.android.streaming.shared.domain.model.VodImages
import com.panaccess.android.streaming.shared.epg.EpgApiCredentials
import com.panaccess.android.streaming.shared.epg.EpgSchedule
import com.panaccess.android.streaming.shared.vod.VodShelves
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import com.networkbroadcast.hospitality.entertainment.VodItem as DomainVodItem
import com.panaccess.android.streaming.shared.domain.model.VodItem as PanaccessVodItem

/** Entretenimiento real desde Panaccess: canales, guía (con catchup), VOD y URLs para el player. */
class PanaccessEntertainmentSource(
    context: Context,
    private val session: PanaccessSession,
    epgCredentials: EpgApiCredentials,
    private val showEmptyBouquetsForPreview: Boolean = false,
) : EntertainmentSource {

    private val catchupCacheDirectory = File(context.filesDir, "catchup")
    private val catalogCache = EntertainmentCatalogCache(context) { session.license?.key }
    private val catalog = CatalogService(session.client)
    private val vod = VodService(session.client)
    private val epg = EpgService(context, epgCredentials)
    private val lock = Mutex()
    private val vodLock = Mutex()
    private val scheduleLocks = ConcurrentHashMap<String, Mutex>()
    private val catchupLock = Mutex()
    private var streams: List<Stream> = emptyList()
    private var cachedChannels: ChannelsResult? = null
    private var cachedVod: VodResult? = null
    private var cachedCatchup: CatchupCatalog? = null
    private var cachedCatchupAtMillis = 0L

    override suspend fun channels(): ChannelsResult = lock.withLock {
        cachedChannels?.let { return it }
        loadChannelsLocked()
    }

    override suspend fun cachedChannels(): ChannelsResult? = lock.withLock {
        cachedChannels?.let { return it }
        withContext(Dispatchers.IO) { catalogCache.readChannels() }.also { cachedChannels = it }
    }

    override suspend fun refreshChannels(): ChannelsResult = lock.withLock { loadChannelsLocked() }

    private suspend fun loadChannelsLocked(): ChannelsResult {
        // Lista vacía o error: el base lo trata como sesión caída (PriorityCasFunctionCaller).
        val result = catalog.channels().let { first ->
            if (first.streams.isEmpty() && session.recover()) catalog.channels() else first
        }
        if (result.failure != null) {
            return cachedChannels?.copy(failure = result.failure) ?: ChannelsResult(emptyList(), failure = result.failure)
        }
        // No reemplazar un snapshot utilizable por una respuesta vacía de sesión/licencia dudosa.
        if (result.streams.isEmpty() && cachedChannels?.channels?.isNotEmpty() == true) return cachedChannels!!
        streams = result.streams
        val ids = result.streams.map { it.streamId }.toSet()
        // Filas por bouquet, por prioridad; los que no tienen canales de esta TV no aparecen.
        val groups = result.bouquets.mapNotNull { bouquet ->
            val members = result.streams.filter { bouquet.bouquetId in it.bouquetIds && it.streamId in ids }
            val includeEmptyForPreview = showEmptyBouquetsForPreview && result.streams.isEmpty()
            if ((members.isEmpty() && !includeEmptyForPreview) || bouquet.name.isBlank()) null
            else {
                val layout = BouquetServiceLayoutConfig.parse(bouquet.customData)
                android.util.Log.i(
                    "Hospitality.Catalog",
                    "bouquet='${bouquet.name}' customData=${!bouquet.customData.isNullOrBlank()} " +
                        "layout=${layout.type}/${layout.rows}x${layout.columns}/${layout.cardDesign}",
                )
                ChannelGroup(bouquet.name, members.map { it.streamId.toString() }, layout)
            }
        }
        val mapped = ChannelsResult(
            channels = result.streams.map { Channel(it.streamId.toString(), it.channelNumber, it.name, it.imageUrl, it.hasCatchup) },
            groups = groups,
        )
        cachedChannels = mapped
        withContext(Dispatchers.IO) { catalogCache.writeChannels(mapped) }
        return mapped
    }

    override suspend fun schedule(channelId: String): List<Program> = scheduleLocks
        .getOrPut(channelId) { Mutex() }
        .withLock { loadSchedule(channelId) }

    private suspend fun loadSchedule(channelId: String): List<Program> {
        var stream = lock.withLock { streams }.firstOrNull { it.streamId.toString() == channelId }
        // Un snapshot de canales basta para Home, pero no contiene los objetos Stream necesarios
        // para resolver el EPG. Si la guía llega antes que el refresco remoto, hidratar el catálogo.
        if (stream == null) {
            refreshChannels()
            stream = lock.withLock { streams }.firstOrNull { it.streamId.toString() == channelId }
        }
        if (stream == null) {
            android.util.Log.w("Hospitality.Epg", "schedule($channelId): no se encontró el Stream ni tras refreshChannels()")
            return emptyList()
        }
        val config = session.operatorConfig
        if (config == null) {
            android.util.Log.w("Hospitality.Epg", "schedule($channelId): operatorConfig es null, no se puede pedir EPG")
            return emptyList()
        }
        // runCatching: un fallo acá (red, parseo) no debe tirar la LaunchedEffect de ChannelCard --
        // antes una excepción sin capturar podía cancelar el efecto en silencio y la tarjeta se
        // quedaba para siempre sin evento, sin ningún rastro en logcat de qué pasó.
        return runCatching {
            epg.events(config, stream.epgStreamId).mapNotNull { e ->
                val start = EpgSchedule.parse(e.start)?.epochSeconds ?: return@mapNotNull null
                val end = EpgSchedule.parse(e.end)?.epochSeconds ?: return@mapNotNull null
                Program(
                    title = e.title,
                    startEpochSec = start,
                    endEpochSec = end,
                    description = e.shortDescription ?: e.extendedDescription,
                    catchupId = e.catchupId.takeIf { e.hasCatchup && stream.hasCatchup },
                    imageUrl = e.imageUrl,
                )
            }.sortedBy { it.startEpochSec }
        }.onSuccess {
            android.util.Log.i("Hospitality.Epg", "schedule($channelId, epgStreamId=${stream.epgStreamId}): ${it.size} evento(s)")
        }.onFailure {
            android.util.Log.w("Hospitality.Epg", "schedule($channelId, epgStreamId=${stream.epgStreamId}) falló: ${it.message}")
        }.getOrDefault(emptyList())
    }

    override suspend fun cachedCatchupCatalog(): CatchupCatalog? = catchupLock.withLock {
        val file = catchupCacheFile() ?: return null
        val snapshot = runCatching { readCatchupCatalog(file) }
            .onFailure { android.util.Log.w("Hospitality.Catalog", "no se pudo leer caché de catchup: ${it.message}") }
            .getOrNull()
        if (snapshot != null) {
            cachedCatchup = snapshot
            // El snapshot sirve de inmediato, pero no renueva el TTL: el siguiente llamado consulta Panaccess.
            cachedCatchupAtMillis = 0L
            android.util.Log.i("Hospitality.Catalog", "caché local de catchup: canales=${snapshot.channels.size}")
        }
        snapshot
    }

    override suspend fun catchupCatalog(): CatchupCatalog = catchupLock.withLock {
        val now = System.currentTimeMillis()
        cachedCatchup?.takeIf { now - cachedCatchupAtMillis < CATCHUP_CACHE_MILLIS }?.let { return it }

        var channelResult = channels()
        // channels() puede devolver el snapshot local, que aún no tiene Stream/epgStreamId asociado.
        if (channelResult.channels.isNotEmpty() && lock.withLock { streams.isEmpty() }) {
            channelResult = refreshChannels()
        }
        if (channelResult.failure != null) {
            return cachedCatchup?.copy(failure = channelResult.failure)
                ?: CatchupCatalog(emptyList(), emptyMap(), channelResult.failure)
        }
        val streamSnapshot = lock.withLock { streams }
        val groupsResult = catalog.catchupGroups()
        if (groupsResult.failure != null) {
            return cachedCatchup?.copy(failure = groupsResult.failure)
                ?: CatchupCatalog(emptyList(), emptyMap(), groupsResult.failure)
        }

        val streamsByEpgId = streamSnapshot.filter { it.epgStreamId > 0 }.groupBy { it.epgStreamId }
        val groups = groupsResult.items.filter { group ->
            group.epgStreamId > 0 && streamsByEpgId[group.epgStreamId].orEmpty().isNotEmpty()
        }
        val epgStreamIds = groups.map { it.epgStreamId }.distinct()
        val gate = Semaphore(MAX_CATCHUP_REQUESTS)
        val eventsByEpgStream = coroutineScope {
            epgStreamIds.map { epgStreamId ->
                async { gate.withPermit { epgStreamId to catalog.catchupEvents(epgStreamId) } }
            }.awaitAll().toMap()
        }

        val programsByChannel = linkedMapOf<String, List<Program>>()
        val availableChannels = linkedMapOf<String, Channel>()
        groups.forEach { group ->
            val events = eventsByEpgStream[group.epgStreamId]?.items.orEmpty()
                .mapNotNull { event ->
                    val start = EpgSchedule.parse(event.start)?.epochSeconds ?: return@mapNotNull null
                    if (event.catchupId <= 0 || event.durationSeconds <= 0) return@mapNotNull null
                    Program(
                        title = event.name,
                        startEpochSec = start,
                        endEpochSec = start + event.durationSeconds,
                        description = event.shortDescription ?: event.extendedDescription,
                        catchupId = event.catchupId,
                        imageUrl = event.imageUrl,
                    )
                }
                .distinctBy { it.catchupId }
                .sortedByDescending { it.startEpochSec }
            if (events.isEmpty()) return@forEach

            streamsByEpgId[group.epgStreamId].orEmpty().forEach { stream ->
                val channelId = stream.streamId.toString()
                availableChannels[channelId] = Channel(
                    id = channelId,
                    number = stream.channelNumber,
                    name = stream.name,
                    logoUrl = stream.imageUrl,
                    hasCatchup = true,
                )
                programsByChannel[channelId] = events
            }
        }

        val partialFailure = eventsByEpgStream.values.firstNotNullOfOrNull { it.failure }
        val fetched = CatchupCatalog(
            channels = availableChannels.values.toList(),
            programsByChannel = programsByChannel,
            failure = partialFailure,
        )
        val result = if (partialFailure != null && cachedCatchup != null) {
            cachedCatchup!!.copy(failure = partialFailure)
        } else {
            fetched
        }
        result.also {
            cachedCatchup = it
            cachedCatchupAtMillis = if (it.failure == null) now else 0L
            if (it.failure == null) catchupCacheFile()?.let { file -> writeCatchupCatalog(file, it) }
            val eventCount = it.programsByChannel.values.sumOf { programs -> programs.size }
            android.util.Log.i("Hospitality.Catalog", "canales con catchup=${it.channels.size}, eventos=$eventCount")
        }
    }

    override suspend fun vodShelves(): VodResult = vodLock.withLock {
        cachedVod?.let { return it }
        loadVodLocked()
    }

    override suspend fun cachedVodShelves(): VodResult? = vodLock.withLock {
        cachedVod?.let { return it }
        withContext(Dispatchers.IO) { catalogCache.readVod() }.also { cachedVod = it }
    }

    override suspend fun refreshVodShelves(): VodResult = vodLock.withLock { loadVodLocked() }

    private suspend fun loadVodLocked(): VodResult {
        val result = vod.catalog()
        if (result.failure != null) {
            return cachedVod?.copy(failure = result.failure) ?: VodResult(emptyList(), result.failure)
        }
        if (result.items.isEmpty() && cachedVod?.shelves?.any { it.items.isNotEmpty() } == true) return cachedVod!!
        val server = session.client.responsibleServer()
        fun map(item: PanaccessVodItem) = DomainVodItem(
            id = item.vodId.toString(),
            title = item.name,
            posterUrl = VodImages.poster(server, item),
            backdropUrl = VodImages.header(server, item),
            synopsis = item.synopsis,
            durationMinutes = (item.durationSeconds / 60).takeIf { it > 0 },
        )
        val shelves = VodShelves.all(result.groups, result.items).map { s -> VodShelf(s.category.name, s.items.map(::map)) }
            // Sin categorías utilizables: una sola fila con todo, para no dejar el catálogo vacío.
            .ifEmpty { if (result.items.isEmpty()) emptyList() else listOf(VodShelf("", result.items.map(::map))) }
        val mapped = VodResult(shelves)
        cachedVod = mapped
        withContext(Dispatchers.IO) { catalogCache.writeVod(mapped) }
        return mapped
    }

    override suspend fun playbackUrl(target: Playable): String? = when (target) {
        is Playable.Live -> {
            val stream = lock.withLock { streams }.firstOrNull { it.streamId.toString() == target.channelId }
            when {
                stream == null || stream.url.isBlank() -> null
                // Externos (type >= 100): la URL del catálogo ya es la final, como Stream.getUrl() del base.
                stream.type >= 100 -> stream.url
                else -> session.client.liveStreamUrl(stream.url)
            }
        }
        is Playable.Catchup -> session.client.catchupUrl(target.catchupId)
        is Playable.Vod -> target.vodId.toIntOrNull()?.let { session.client.vodUrl(it) }
    }

    override suspend fun recover(): Boolean = session.recover()

    /** Al cerrar la sesión o cambiar de cuenta. */
    suspend fun clear() {
        catchupLock.withLock { cachedCatchup = null; cachedCatchupAtMillis = 0L }
        lock.withLock { cachedChannels = null; streams = emptyList(); epg.clear() }
        vodLock.withLock { cachedVod = null }
        scheduleLocks.clear()
    }


    /** Caché local por licencia: evita mostrar datos de otra cuenta/licencia en esta TV. */
    private fun catchupCacheFile(): File? {
        val licenseKey = session.license?.key?.takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(licenseKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(catchupCacheDirectory, "$digest.json")
    }

    private fun readCatchupCatalog(file: File): CatchupCatalog? {
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        val channelsJson = root.optJSONArray("channels") ?: return null
        val channels = buildList {
            for (i in 0 until channelsJson.length()) {
                val item = channelsJson.optJSONObject(i) ?: continue
                add(Channel(
                    id = item.optString("id"),
                    number = item.optInt("number"),
                    name = item.optString("name"),
                    logoUrl = item.optNullableString("logoUrl"),
                    hasCatchup = item.optBoolean("hasCatchup", true),
                ))
            }
        }
        val programsJson = root.optJSONObject("programsByChannel") ?: JSONObject()
        val programs = buildMap {
            val keys = programsJson.keys()
            while (keys.hasNext()) {
                val channelId = keys.next()
                val items = programsJson.optJSONArray(channelId) ?: continue
                put(channelId, buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        add(Program(
                            title = item.optString("title"),
                            startEpochSec = item.optLong("startEpochSec"),
                            endEpochSec = item.optLong("endEpochSec"),
                            description = item.optNullableString("description"),
                            catchupId = item.optInt("catchupId").takeIf { !item.isNull("catchupId") },
                            imageUrl = item.optNullableString("imageUrl"),
                        ))
                    }
                })
            }
        }
        return CatchupCatalog(channels, programs)
    }

    private fun writeCatchupCatalog(file: File, catalog: CatchupCatalog) {
        runCatching {
            val channels = JSONArray().apply {
                catalog.channels.forEach { channel ->
                    put(JSONObject().apply {
                        put("id", channel.id)
                        put("number", channel.number)
                        put("name", channel.name)
                        put("logoUrl", channel.logoUrl ?: JSONObject.NULL)
                        put("hasCatchup", channel.hasCatchup)
                    })
                }
            }
            val programs = JSONObject().apply {
                catalog.programsByChannel.forEach { (channelId, channelPrograms) ->
                    put(channelId, JSONArray().apply {
                        channelPrograms.forEach { program ->
                            put(JSONObject().apply {
                                put("title", program.title)
                                put("startEpochSec", program.startEpochSec)
                                put("endEpochSec", program.endEpochSec)
                                put("description", program.description ?: JSONObject.NULL)
                                put("catchupId", program.catchupId ?: JSONObject.NULL)
                                put("imageUrl", program.imageUrl ?: JSONObject.NULL)
                            })
                        }
                    })
                }
            }
            catchupCacheDirectory.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSONObject().put("channels", channels).put("programsByChannel", programs).toString())
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure { android.util.Log.w("Hospitality.Catalog", "no se pudo guardar caché de catchup: ${it.message}") }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_CATCHUP_REQUESTS = 5
        const val CATCHUP_CACHE_MILLIS = 10 * 60 * 1000L
    }
}
