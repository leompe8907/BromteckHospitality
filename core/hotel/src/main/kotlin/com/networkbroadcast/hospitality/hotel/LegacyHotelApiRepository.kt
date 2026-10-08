package com.networkbroadcast.hospitality.hotel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.HttpURLConnection
import java.net.URL

/**
 * Contenido del hotel desde la API del sistema viejo, la misma que usa PanaccessApp
 * (JsonHotelPlaceholderInterface): `GET <baseUrl>/<carpeta>/images_hotel` y `/videos_hotel`, cada
 * una con una lista de `{id, title, url, thumbnailUrl}`.
 *
 * baseUrl y carpeta llegan del operador (X_HOTEL_APK_BASE_URL / X_HOTEL_APK_BASE_FOLDER), así que
 * una TV genérica toma las fotos de su hotel sin recompilar. La API no trae nombre ni descripción:
 * se completan con [name] y [description] (los de la marca o vacíos).
 */
class LegacyHotelApiRepository(
    private val baseUrl: String,
    private val folder: String,
    private val name: LocalizedText,
    private val description: LocalizedText = emptyMap(),
) : HotelRepository {

    @Serializable
    private data class LegacyMedia(val id: Int = 0, val title: String? = null, val url: String = "", val thumbnailUrl: String? = null)

    override suspend fun load(): HotelData? = withContext(Dispatchers.IO) {
        val images = fetch("images_hotel")
        val videos = fetch("videos_hotel")
        if (images == null && videos == null) return@withContext null
        HotelData(
            hotelInfo = HotelInfo(name, description),
            images = images.orEmpty().map { it.toMedia() },
            videos = videos.orEmpty().map { it.toMedia() },
        )
    }

    private fun LegacyMedia.toMedia() = HotelMedia(
        id = id,
        title = title?.takeIf { it.isNotBlank() }?.let { mapOf("es" to it) } ?: emptyMap(),
        url = url,
        thumbnailUrl = thumbnailUrl,
    )

    private fun fetch(endpoint: String): List<LegacyMedia>? = runCatching {
        val url = "${baseUrl.trimEnd('/')}/${folder.trim('/')}/$endpoint"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
        try {
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            hotelJson.decodeFromString(ListSerializer(LegacyMedia.serializer()), text).filter { it.url.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
