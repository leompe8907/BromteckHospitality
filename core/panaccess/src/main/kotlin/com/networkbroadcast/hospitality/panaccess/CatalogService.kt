package com.networkbroadcast.hospitality.panaccess

import android.util.Log
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.domain.model.Bouquet
import com.panaccess.android.streaming.shared.domain.model.Stream

/**
 * Catálogo de canales. Port de `shared/iosMain/.../catalog/CatalogService.kt` del base: mismas
 * funciones del CAS, mismos timeouts, mismo orden (por número de canal; los sin número al final).
 */
class CatalogService(private val client: PanaccessClient) {

    data class Channels(val streams: List<Stream>, val bouquets: List<Bouquet>, val failure: String?)

    suspend fun channels(): Channels {
        val streams = when (val r = client.call("getAvailableStreams", timeoutMs = TIMEOUT)) {
            is CasResult.Success -> CasEnvelope.answerList<Stream>(r.rawJson)
                .filter { it.authorized && !it.isAudio }
                .sortedBy { if (it.channelNumber > 0) it.channelNumber else Int.MAX_VALUE }
            else -> return Channels(emptyList(), emptyList(), describe(r)).also { Log.w("Hospitality.Catalog", "getAvailableStreams -> ${it.failure}") }
        }
        // Sin bouquets igual hay canales: es información para agrupar, no un requisito.
        val bouquets = when (val r = client.call("getBouquets", timeoutMs = TIMEOUT)) {
            is CasResult.Success -> CasEnvelope.answerList<Bouquet>(r.rawJson)
                .sortedBy { if (it.priority == 0) Int.MAX_VALUE else it.priority }
            else -> emptyList()
        }
        Log.i("Hospitality.Catalog", "canales autorizados=${streams.size}, bouquets=${bouquets.size}")
        return Channels(streams, bouquets, null)
    }

    /** Grupos de catchup (un grupo por canal con programas grabados). */
    suspend fun catchupGroups(): List<com.panaccess.android.streaming.shared.domain.model.CatchupGroup> =
        (client.call("getCatchupGroups") as? CasResult.Success)
            ?.let { CasEnvelope.answerList<com.panaccess.android.streaming.shared.domain.model.CatchupGroup>(it.rawJson) }
            .orEmpty()
            .also { Log.i("Hospitality.Catalog", "grupos de catchup=${it.size}") }

    /** Programas grabados de un canal, por su epgStreamId. */
    suspend fun catchupEvents(epgStreamId: Int): List<com.panaccess.android.streaming.shared.domain.model.Catchup> =
        (client.call("getCatchupEvents", mapOf("epgStreamId" to "$epgStreamId", "separateDescMode" to "true")) as? CasResult.Success)
            ?.let { CasEnvelope.answerList<com.panaccess.android.streaming.shared.domain.model.Catchup>(it.rawJson) }
            .orEmpty()

    private fun describe(r: CasResult) = when (r) {
        is CasResult.Success -> "ok"
        is CasResult.Failure -> r.message ?: r.code ?: "sin detalle"
        CasResult.Timeout -> "timeout"
    }

    private companion object {
        const val TIMEOUT = 60_000
    }
}
