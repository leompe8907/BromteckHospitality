package com.networkbroadcast.hospitality.entertainment

/**
 * Lo que la app de hotel necesita del entretenimiento, sin saber quién lo provee.
 * La implementación real es PanaccessEntertainmentSource (core/panaccess); [FakeEntertainmentSource]
 * queda para previews y pruebas sin cuenta.
 */
interface EntertainmentSource {
    /** Canales que el huésped puede ver, ordenados por número, y sus agrupaciones (bouquets). */
    suspend fun channels(): ChannelsResult

    /** Snapshot local de canales/bouquets, si el proveedor lo persiste. */
    suspend fun cachedChannels(): ChannelsResult? = null

    /** Fuerza el refresco remoto aun cuando haya un snapshot local cargado. */
    suspend fun refreshChannels(): ChannelsResult = channels()

    /** Programación de un canal (ayer → próximos días), ordenada por inicio. */
    suspend fun schedule(channelId: String): List<Program>

    /** Grupos y eventos realmente disponibles para ver en diferido. */
    suspend fun catchupCatalog(): CatchupCatalog = CatchupCatalog(emptyList(), emptyMap())

    /** Último snapshot de catchup local, si la implementación mantiene caché persistida. */
    suspend fun cachedCatchupCatalog(): CatchupCatalog? = null

    /** Catálogo de películas y series, en filas. */
    suspend fun vodShelves(): VodResult

    /** Snapshot local de películas/series, si el proveedor lo persiste. */
    suspend fun cachedVodShelves(): VodResult? = null

    /** Fuerza el refresco remoto aun cuando haya un snapshot local cargado. */
    suspend fun refreshVodShelves(): VodResult = vodShelves()

    /**
     * URL lista para el player. Se pide en cada reproducción y en cada reintento: en Panaccess va
     * atada a la sesión actual del DRM y no sirve guardarla. null si no se puede ver.
     */
    suspend fun playbackUrl(target: Playable): String?

    /**
     * Adelanta la resolución de [playbackUrl] para algo que el huésped todavía no abrió (p. ej. el
     * evento que tiene enfocado en "Eventos anteriores"). La URL queda reservada para el primer
     * [playbackUrl] de ese mismo target y se consume ahí: los reintentos del player siguen pidiendo
     * una nueva, que es lo que hace falta cuando el problema fue la sesión del DRM.
     * Por omisión no hace nada: resolver de más nunca es obligatorio.
     */
    suspend fun prefetchPlaybackUrl(target: Playable) = Unit

    /**
     * Intenta recuperar la sesión con el proveedor (p. ej. después de varios errores del player).
     * true si quedó utilizable y conviene reintentar.
     */
    suspend fun recover(): Boolean = false
}

/** Qué se quiere reproducir. */
sealed interface Playable {
    data class Live(val channelId: String) : Playable
    data class Catchup(val catchupId: Int) : Playable
    data class Vod(val vodId: String) : Playable
}

data class ChannelsResult(
    val channels: List<Channel>,
    val groups: List<ChannelGroup> = emptyList(),
    val failure: String? = null,
)

data class CatchupCatalog(
    /** Solo canales que devolvieron al menos un evento válido de catchup. */
    val channels: List<Channel>,
    /** Eventos indexados por el id de canal que usa la app. */
    val programsByChannel: Map<String, List<Program>>,
    val failure: String? = null,
)

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String? = null,
    /** true si el canal tiene catchup habilitado (Stream.hasCatchup); no implica que haya programas ya emitidos. */
    val hasCatchup: Boolean = false,
)

/** Diseño de una fila de canales según el customData del bouquet de Panaccess. */
data class ChannelGroupLayout(
    val type: String = "horizontal_grid",
    val rows: Int = 1,
    val columns: Int = 1,
    val cardDesign: String = "logo_normal",
    val logoIndex: Int = 1,
)

/** Un bouquet con sus canales y su diseño, en el orden del operador. */
data class ChannelGroup(
    val name: String,
    val channelIds: List<String>,
    val layout: ChannelGroupLayout = ChannelGroupLayout(),
)

data class Program(
    val title: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val description: String? = null,
    /** Id de catchup si el programa ya emitido se puede volver a ver. */
    val catchupId: Int? = null,
    val imageUrl: String? = null,
) {
    fun isLive(nowSec: Long) = nowSec in startEpochSec until endEpochSec
    fun isPast(nowSec: Long) = endEpochSec <= nowSec
}

data class VodResult(val shelves: List<VodShelf>, val failure: String? = null)

data class VodShelf(val title: String, val items: List<VodItem>)

data class VodItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val synopsis: String? = null,
    val durationMinutes: Int? = null,
)

/** Programa actual y siguiente de una programación. */
fun List<Program>.nowAndNext(nowSec: Long = System.currentTimeMillis() / 1000): Pair<Program?, Program?> =
    firstOrNull { it.isLive(nowSec) } to firstOrNull { it.startEpochSec > nowSec }
