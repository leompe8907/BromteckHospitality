package com.networkbroadcast.hospitality.panaccess

import com.networkbroadcast.hospitality.entertainment.ChannelGroupLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lee el formato de layouts por dispositivo que Panaccess entrega en Bouquet.customData. */
internal object BouquetServiceLayoutConfig {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(customData: String?): ChannelGroupLayout {
        val raw = customData?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return ChannelGroupLayout()
        val parsed = parseObjectCandidates(raw)
        if (parsed != null) {
            val layouts = parsed["layouts"] as? JsonObject
            val tv = layouts?.get("tv") as? JsonObject
            if (tv != null) return parseDeviceLayout(tv)
            if (parsed.containsKey("layouts")) return ChannelGroupLayout()
            if (parsed.containsKey("type") || parsed.containsKey("card_design")) return parseDeviceLayout(parsed)
            return ChannelGroupLayout()
        }

        // Formato legado: el customData contiene directamente el diseño de tarjeta.
        return ChannelGroupLayout(cardDesign = normalizeCardDesign(raw))
    }

    private fun parseDeviceLayout(obj: JsonObject): ChannelGroupLayout {
        val type = obj.text("type")?.lowercase()?.takeIf { it in setOf("horizontal_grid", "vertical_grid") } ?: "none"
        return ChannelGroupLayout(
            type = type,
            rows = (obj.number("rows") ?: 1).coerceAtLeast(1),
            columns = (obj.number("columns") ?: 1).coerceAtLeast(1),
            cardDesign = normalizeCardDesign(obj.text("card_design") ?: "logo_normal"),
            logoIndex = (obj.number("logo_index") ?: 1).coerceIn(1, 3),
        )
    }

    private fun normalizeCardDesign(raw: String): String = raw.trim().lowercase()
        .removePrefix("service_layout_")
        .replace("logo_grande", "logo_big")
        .takeIf { it in CARD_DESIGNS }
        ?: "logo_normal"

    private fun parseObjectCandidates(raw: String): JsonObject? {
        val candidates = linkedSetOf<String>()
        candidates += raw
        val unquoted = raw.removeSurrounding("\"").replace("\\\"", "\"")
        candidates += unquoted
        for (candidate in candidates.toList()) {
            if (!candidate.startsWith("{") && candidate.contains("\"layouts\"")) candidates += "{$candidate"
            var trimmed = candidate
            while (trimmed.endsWith("}")) {
                if (runCatching { json.parseToJsonElement(trimmed).jsonObject }.isSuccess) break
                trimmed = trimmed.dropLast(1)
            }
            candidates += trimmed
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.number(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private val CARD_DESIGNS = setOf(
        "logo_normal", "logo_with_number", "event_normal", "logo_big", "event_big", "event_and_logo",
        "event_and_logo_in_bottom", "event_and_logo_in_top", "event_line", "channel_full_info",
    )
}
