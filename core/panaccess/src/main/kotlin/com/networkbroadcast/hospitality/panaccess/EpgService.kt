package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import android.util.Log
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.epg.EpgApiCredentials
import com.panaccess.android.streaming.shared.epg.EpgCache
import com.panaccess.android.streaming.shared.epg.EpgEvent
import com.panaccess.android.streaming.shared.epg.EpgUrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Guía de programación. No es una función del CAS: es un ZIP con un JSON que se baja del CDN del
 * operador (`epgCdnGroupId` de getClientConfig), con un accessToken calculado acá.
 *
 * Port de `shared/iosMain/.../epg/EpgService.kt` del base (que replica EPGDownloader de Android):
 * misma URL, caché de 3 h por canal, como mucho 5 descargas a la vez.
 */
class EpgService(context: Context, private val credentials: EpgApiCredentials) {

    private val cache: EpgCache = FileEpgCache(File(context.cacheDir, "epg"))
    private val gate = Semaphore(EpgCache.MAX_CONCURRENT_DOWNLOADS)

    /** Eventos de un canal (ayer → 3 días adelante, como EPG_DAYS_BACK/AHEAD del base). */
    suspend fun events(config: OperatorConfig, epgStreamId: Int, pastDays: Int = 1): List<EpgEvent> {
        if (epgStreamId <= 0) return emptyList()
        val operator = config.operator ?: return emptyList()
        val cdn = config.epgCdnUrls.firstOrNull()?.trimEnd('/') ?: return emptyList()
        cache.read(epgStreamId, pastDays, EpgCache.DEFAULT_MAX_AGE_MILLIS)?.let { return parse(it) }
        val json = gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching { download(EpgUrlBuilder.downloadUrl(cdn, credentials, operator, epgStreamId, pastDays)) }
                    .onFailure { Log.w(TAG, "EPG $epgStreamId: ${it.message}") }
                    .getOrNull()
            }
        } ?: return emptyList()
        cache.write(epgStreamId, pastDays, json)
        return parse(json)
    }

    fun clear() = cache.clear()

    /** Baja el ZIP y devuelve el JSON de su primera entrada. */
    private fun download(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode !in 200..299) return null
            ZipInputStream(connection.inputStream.buffered()).use { zip ->
                zip.nextEntry ?: return null
                return zip.readBytes().toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): List<EpgEvent> = runCatching {
        val element = CasEnvelope.json.parseToJsonElement(json)
        val array = element as? JsonArray ?: CasEnvelope.answer(json) as? JsonArray ?: return emptyList()
        CasEnvelope.json.decodeFromJsonElement(ListSerializer(EpgEvent.serializer()), array)
    }.getOrElse { Log.w(TAG, "EPG mal formado: $it"); emptyList() }

    private companion object {
        const val TAG = "Hospitality.Epg"
    }
}

/** Caché en disco: un JSON por canal y ventana, vencido por fecha de modificación. */
private class FileEpgCache(private val dir: File) : EpgCache {
    private fun file(id: Int, pastDays: Int) = File(dir, "${id}_$pastDays.json")

    override fun read(epgStreamId: Int, pastDays: Int, maxAgeMillis: Long): String? {
        val f = file(epgStreamId, pastDays)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMillis) return null
        return runCatching { f.readText() }.getOrNull()
    }

    override fun write(epgStreamId: Int, pastDays: Int, json: String) {
        runCatching { dir.mkdirs(); file(epgStreamId, pastDays).writeText(json) }
    }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
