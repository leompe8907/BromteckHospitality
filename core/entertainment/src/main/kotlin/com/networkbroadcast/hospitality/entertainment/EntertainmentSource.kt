package com.networkbroadcast.hospitality.entertainment

/**
 * Lo que la app de hotel necesita del entretenimiento, sin saber quién lo provee.
 * La implementación real es PanaccessEntertainmentSource (core/panaccess); [FakeEntertainmentSource]
 * queda para previews y pruebas sin cuenta.
 */
interface EntertainmentSource {
    /** Canales que el huésped puede ver, ordenados por número, y sus agrupaciones (bouquets). */
    suspend fun channels(): ChannelsResult

    /** Programación de un canal (ayer → próximos días), ordenada por inicio. */
    suspend fun schedule(channelId: String): List<Program>

    /** Catálogo de películas y series, en filas. */
    suspend fun vodShelves(): VodResult

    /**
     * URL lista para el player. Se pide en cada reproducción y en cada reintento: en Panaccess va
     * atada a la sesión actual del DRM y no sirve guardarla. null si no se puede ver.
     */
    suspend fun playbackUrl(target: Playable): String?

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

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String? = null,
)

/** Un bouquet con sus canales, en el orden del operador. */
data class ChannelGroup(val name: String, val channelIds: List<String>)

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
