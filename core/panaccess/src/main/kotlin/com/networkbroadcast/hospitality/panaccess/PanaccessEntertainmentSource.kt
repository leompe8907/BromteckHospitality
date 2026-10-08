package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.networkbroadcast.hospitality.entertainment.VodItem as DomainVodItem
import com.panaccess.android.streaming.shared.domain.model.VodItem as PanaccessVodItem

/** Entretenimiento real desde Panaccess: canales, guía (con catchup), VOD y URLs para el player. */
class PanaccessEntertainmentSource(
    context: Context,
    private val session: PanaccessSession,
    epgCredentials: EpgApiCredentials,
) : EntertainmentSource {

    private val catalog = CatalogService(session.client)
    private val vod = VodService(session.client)
    private val epg = EpgService(context, epgCredentials)
    private val lock = Mutex()
    private var streams: List<Stream> = emptyList()
    private var cachedChannels: ChannelsResult? = null
    private var cachedVod: VodResult? = null

    override suspend fun channels(): ChannelsResult = lock.withLock {
        cachedChannels?.let { return it }
        // Lista vacía o error: el base lo trata como sesión caída (PriorityCasFunctionCaller).
        val result = catalog.channels().let { first ->
            if (first.streams.isEmpty() && session.recover()) catalog.channels() else first
        }
        streams = result.streams
        val ids = result.streams.map { it.streamId }.toSet()
        // Filas por bouquet, por prioridad; los que no tienen canales de esta TV no aparecen.
        val groups = result.bouquets.mapNotNull { bouquet ->
            val members = result.streams.filter { bouquet.bouquetId in it.bouquetIds && it.streamId in ids }
            if (members.isEmpty() || bouquet.name.isBlank()) null
            else ChannelGroup(bouquet.name, members.map { it.streamId.toString() })
        }
        ChannelsResult(
            channels = result.streams.map { Channel(it.streamId.toString(), it.channelNumber, it.name, it.imageUrl) },
            groups = groups,
            failure = result.failure,
        ).also { if (it.failure == null) cachedChannels = it }
    }

    override suspend fun schedule(channelId: String): List<Program> {
        val stream = lock.withLock { streams }.firstOrNull { it.streamId.toString() == channelId } ?: return emptyList()
        val config = session.operatorConfig ?: return emptyList()
        return epg.events(config, stream.epgStreamId).mapNotNull { e ->
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
    }

    override suspend fun vodShelves(): VodResult = lock.withLock {
        cachedVod?.let { return it }
        val result = vod.catalog()
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
        VodResult(shelves, result.failure).also { if (it.failure == null) cachedVod = it }
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
    suspend fun clear() = lock.withLock { cachedChannels = null; cachedVod = null; streams = emptyList(); epg.clear() }
}
