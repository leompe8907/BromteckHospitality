package com.networkbroadcast.hospitality.panaccess

import com.networkbroadcast.hospitality.entertainment.Episode
import com.networkbroadcast.hospitality.entertainment.Season
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.domain.model.VodImages
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Temporadas y episodios de `getVodSeriesInfo(seriesId)`.
 *
 * La forma sale del parser del base (Bromteckotlin: `Video.load`, que Panaccess alimenta con esta
 * misma respuesta), no de una respuesta real de esta cuenta: la serie trae `seasons[]`; cada
 * temporada trae `seasonNumber`, `name` y `episodes[]`; cada episodio, `id`, `name`, `episodeNumber`,
 * `duration` (segundos), `description` (puede traer HTML) e `image3Id`. Si no hay `seasons` pero sí
 * `episodes` a nivel de la respuesta, es una sola temporada. Todo lo que falte se omite; nada se inventa.
 *
 * @param imageServer servidor para armar la imagen del episodio ([VodImages.url]); null = sin imagen.
 */
fun parseSeasons(rawJson: String, imageServer: String?): List<Season> {
    val answer = CasEnvelope.answer(rawJson) as? JsonObject ?: return emptyList()

    fun episodes(source: JsonObject): List<Episode> =
        (source["episodes"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.int("id")?.takeIf { it > 0 } ?: return@mapNotNull null
            Episode(
                id = id.toString(),
                number = obj.int("episodeNumber") ?: 0,
                title = obj.text("name").orEmpty(),
                synopsis = obj.text("description")?.let(::stripHtml)?.takeIf { it.isNotBlank() },
                durationMinutes = obj.int("duration")?.let { it / 60 }?.takeIf { it > 0 },
                imageUrl = VodImages.url(imageServer, obj.int("image3Id"), VodImages.EXTRA),
            )
        }.sortedBy { it.number }

    val seasons = (answer["seasons"] as? JsonArray).orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val list = episodes(obj)
        if (list.isEmpty()) null else Season(obj.int("seasonNumber") ?: 0, obj.text("name"), list)
    }
    if (seasons.isNotEmpty()) return seasons.sortedBy { it.number }
    val flat = episodes(answer)
    return if (flat.isEmpty()) emptyList() else listOf(Season(answer.int("seasonNumber")?.takeIf { it > 0 } ?: 1, null, flat))
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.content.toIntOrNull() }

/** Quita las etiquetas HTML y las entidades más comunes de una sinopsis. */
internal fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()
