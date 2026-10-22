package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelGroupLayout
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.entertainment.VodShelf
import java.io.File
import java.security.MessageDigest

/** Snapshot local de los catálogos que deciden la composición de Home. Se separa por licencia. */
internal class EntertainmentCatalogCache(
    context: Context,
    private val licenseKey: () -> String?,
) {
    private val directory = File(context.filesDir, "entertainment-catalogs")

    fun readChannels(): ChannelsResult? = read("channels") { root ->
        val channels = root.optJSONArray("channels")?.toList { item ->
            Channel(
                id = item.optString("id"),
                number = item.optInt("number"),
                name = item.optString("name"),
                logoUrl = item.nullableString("logoUrl"),
                hasCatchup = item.optBoolean("hasCatchup"),
            )
        } ?: return@read null
        val groups = root.optJSONArray("groups")?.toList { item ->
            val idsJson = item.optJSONArray("channelIds")
            val ids = buildList {
                if (idsJson != null) for (index in 0 until idsJson.length()) add(idsJson.optStringValue(index))
            }
            val layout = item.optJSONObject("layout") ?: JSONObject()
            ChannelGroup(
                name = item.optString("name"),
                channelIds = ids,
                layout = ChannelGroupLayout(
                    type = layout.optString("type", "horizontal_grid"),
                    rows = layout.optInt("rows", 1),
                    columns = layout.optInt("columns", 1),
                    cardDesign = layout.optString("cardDesign", "logo_normal"),
                    logoIndex = layout.optInt("logoIndex", 1),
                ),
            )
        }.orEmpty()
        ChannelsResult(channels, groups)
    }

    fun writeChannels(result: ChannelsResult) {
        if (result.failure != null || result.channels.isEmpty()) return
        write("channels", JSONObject().apply {
            put("channels", JSONArray().apply {
                result.channels.forEach { channel -> put(JSONObject().apply {
                    put("id", channel.id)
                    put("number", channel.number)
                    put("name", channel.name)
                    putNullable("logoUrl", channel.logoUrl)
                    put("hasCatchup", channel.hasCatchup)
                }) }
            })
            put("groups", JSONArray().apply {
                result.groups.forEach { group -> put(JSONObject().apply {
                    put("name", group.name)
                    put("channelIds", JSONArray(group.channelIds))
                    put("layout", JSONObject().apply {
                        put("type", group.layout.type)
                        put("rows", group.layout.rows)
                        put("columns", group.layout.columns)
                        put("cardDesign", group.layout.cardDesign)
                        put("logoIndex", group.layout.logoIndex)
                    })
                }) }
            })
        })
    }

    fun readVod(): VodResult? = read("vod") { root ->
        val shelves = root.optJSONArray("shelves")?.toList { shelf ->
            val items = shelf.optJSONArray("items")?.toList { item ->
                VodItem(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    posterUrl = item.nullableString("posterUrl"),
                    backdropUrl = item.nullableString("backdropUrl"),
                    year = item.optInt("year").takeUnless { item.isNull("year") },
                    synopsis = item.nullableString("synopsis"),
                    durationMinutes = item.optInt("durationMinutes").takeUnless { item.isNull("durationMinutes") },
                )
            }.orEmpty()
            VodShelf(shelf.optString("title"), items)
        } ?: return@read null
        VodResult(shelves)
    }

    fun writeVod(result: VodResult) {
        if (result.failure != null || result.shelves.none { it.items.isNotEmpty() }) return
        write("vod", JSONObject().apply {
            put("shelves", JSONArray().apply {
                result.shelves.forEach { shelf -> put(JSONObject().apply {
                    put("title", shelf.title)
                    put("items", JSONArray().apply {
                        shelf.items.forEach { item -> put(JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            putNullable("posterUrl", item.posterUrl)
                            putNullable("backdropUrl", item.backdropUrl)
                            putNullable("year", item.year)
                            putNullable("synopsis", item.synopsis)
                            putNullable("durationMinutes", item.durationMinutes)
                        }) }
                    })
                }) }
            })
        })
    }

    private fun <T> read(kind: String, parse: (JSONObject) -> T?): T? {
        val file = file(kind) ?: return null
        if (!file.isFile) return null
        return runCatching { parse(JSONObject(file.readText())) }
            .onFailure { android.util.Log.w(TAG, "no se pudo leer snapshot $kind: ${it.message}") }
            .getOrNull()
    }

    private fun write(kind: String, json: JSONObject) {
        val file = file(kind) ?: return
        runCatching {
            directory.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.toString())
            if (!temporary.renameTo(file)) {
                file.writeText(json.toString())
                temporary.delete()
            }
        }.onFailure { android.util.Log.w(TAG, "no se pudo guardar snapshot $kind: ${it.message}") }
    }

    private fun file(kind: String): File? {
        val key = licenseKey()?.takeIf { it.isNotBlank() } ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$hash.$kind.json")
    }

    private inline fun <T> JSONArray.toList(map: (JSONObject) -> T): List<T> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let { add(map(it)) }
    }

    private fun JSONArray.optStringValue(index: Int): String = optString(index).takeUnless { it == "null" }.orEmpty()

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeUnless { it == "null" }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private companion object {
        const val TAG = "Hospitality.Catalog"
    }
}
