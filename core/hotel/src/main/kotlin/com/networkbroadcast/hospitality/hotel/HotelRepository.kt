package com.networkbroadcast.hospitality.hotel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fuente del contenido "acerca del hotel". Hoy: un JSON en assets o en una URL. Mañana: el
 * backend propio (panel de administración), implementando esta misma interfaz.
 */
interface HotelRepository {
    /** null si no hay contenido o no se pudo leer; la UI muestra el estado de error. */
    suspend fun load(): HotelData?
}

internal val hotelJson = Json { ignoreUnknownKeys = true }

fun parseHotelData(text: String): HotelData = hotelJson.decodeFromString(HotelData.serializer(), text)

class AssetHotelRepository(
    private val context: Context,
    private val assetName: String,
) : HotelRepository {
    override suspend fun load(): HotelData? = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetName).bufferedReader().use { parseHotelData(it.readText()) }
        }.getOrNull()
    }
}

class UrlHotelRepository(private val url: String) : HotelRepository {
    override suspend fun load(): HotelData? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                connection.inputStream.bufferedReader().use { parseHotelData(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/** Primero la URL; si no responde (el servidor viejo del hotel se cayó), el asset local. */
class FallbackHotelRepository(
    private val primary: HotelRepository,
    private val fallback: HotelRepository,
) : HotelRepository {
    override suspend fun load(): HotelData? = primary.load() ?: fallback.load()
}
