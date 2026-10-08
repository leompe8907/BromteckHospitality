package com.networkbroadcast.hospitality.panaccess

import android.util.Log
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.domain.model.VodCategoryGroup
import com.panaccess.android.streaming.shared.domain.model.VodItem

/**
 * Catálogo de VOD: getVodContent paginado (de a 100 hasta que venga vacío, como DataStore del
 * base) y getOttCategoryGroups para armar las filas. Port de CatalogService.vod* de iOS.
 */
class VodService(private val client: PanaccessClient) {

    data class Catalog(val items: List<VodItem>, val groups: List<VodCategoryGroup>, val failure: String?)

    suspend fun catalog(): Catalog {
        val items = mutableListOf<VodItem>()
        var offset = 0
        while (offset < MAX_ITEMS) {
            val r = client.call("getVodContent", mapOf("offset" to "$offset", "limit" to "$PAGE"))
            val page = (r as? CasResult.Success)?.let { CasEnvelope.answerList<VodItem>(it.rawJson) }
                ?: return Catalog(items, emptyList(), if (items.isEmpty()) describe(r) else null)
            if (page.isEmpty()) break
            items += page
            offset += page.size
        }
        val groups = (client.call("getOttCategoryGroups", mapOf("includeList" to "true")) as? CasResult.Success)
            ?.let { CasEnvelope.answerList<VodCategoryGroup>(it.rawJson) }.orEmpty()
        Log.i("Hospitality.Vod", "títulos=${items.size}, grupos de categorías=${groups.size}")
        return Catalog(items, groups, null)
    }

    private fun describe(r: CasResult) = when (r) {
        is CasResult.Success -> "ok"
        is CasResult.Failure -> r.message ?: r.code ?: "sin detalle"
        CasResult.Timeout -> "timeout"
    }

    private companion object {
        const val PAGE = 100
        const val MAX_ITEMS = 2_000
    }
}
