package com.networkbroadcast.hospitality.panaccess

import com.panaccess.android.streaming.shared.cas.CasEnvelope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Configuración del cliente que manda el operador en getClientConfig. Lo que importa para la
 * marca blanca son los parámetros `X_…` de `answer.device.parameters`: los mismos ~169 que
 * ClientConfigSettings del base usa para pisar los valores del flavor.
 *
 * En vez de copiar ClientConfigSettings (2284 líneas, 219 referencias a BuildConfig) se expone el
 * mapa crudo + los getters que esta app usa. Port de `shared/iosMain/.../ClientConfigService.kt`.
 */
data class OperatorConfig(
    val parameters: Map<String, String>,
    val operator: String?,
    val subscriberName: String?,
    val epgCdnUrls: List<String>,
    val defaultTimeZone: String?,
) {
    fun param(key: String): String? = parameters[key]?.takeIf { it.isNotBlank() }
    fun flag(key: String): Boolean? = param(key)?.let { it.equals("true", true) || it == "1" }

    // Contenido "acerca del hotel" del sistema viejo: GET <url>/<carpeta>/images_hotel
    val hotelApk: Boolean? get() = flag("X_HOTEL_APK")
    val hotelShowAbout: Boolean? get() = flag("X_HOTEL_SHOW_ABOUT")
    val hotelBaseUrl: String? get() = param("X_HOTEL_APK_BASE_URL")
    val hotelBaseFolder: String? get() = param("X_HOTEL_APK_BASE_FOLDER")
    val showRoomNumber: Boolean? get() = flag("X_DESIGN_SHOW_HOTEL_ROOM_NUMBER")
    val defaultLanguage: String? get() = param("X_DEFAULT_LANGUAGE")

    companion object {
        fun parse(rawJson: String): OperatorConfig {
            val answer = CasEnvelope.answer(rawJson) as? JsonObject
            return OperatorConfig(
                parameters = parameters(answer),
                operator = text(answer?.get("subscriber")?.let { runCatching { it.jsonObject["operator"] }.getOrNull() }),
                subscriberName = subscriberName(answer),
                epgCdnUrls = epgCdnUrls(answer),
                defaultTimeZone = text(answer?.get("defaultTimeZone")),
            )
        }

        private fun text(element: kotlinx.serialization.json.JsonElement?): String? =
            runCatching { element?.jsonPrimitive?.content }.getOrNull()?.trim()?.ifEmpty { null }

        private fun parameters(answer: JsonObject?): Map<String, String> {
            val params = runCatching { answer?.get("device")?.jsonObject?.get("parameters")?.jsonObject }.getOrNull()
                ?: return emptyMap()
            return params.mapValues { (_, v) -> runCatching { v.jsonPrimitive.content }.getOrDefault("") }
        }

        private fun subscriberName(answer: JsonObject?): String? = runCatching {
            val s = answer?.get("subscriber")?.jsonObject ?: return null
            listOf("firstName", "lastName").mapNotNull { text(s[it]) }.joinToString(" ").ifEmpty { null }
        }.getOrNull()

        /** CDN de la guía: el grupo de cdnServers cuyo id es epgCdnGroupId. */
        private fun epgCdnUrls(answer: JsonObject?): List<String> {
            val groupId = text(answer?.get("epgCdnGroupId"))?.toIntOrNull() ?: return emptyList()
            val groups = answer?.get("cdnServers") as? JsonArray ?: return emptyList()
            val group = groups.firstOrNull {
                runCatching { it.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() == groupId }.getOrDefault(false)
            } ?: return emptyList()
            val urls = runCatching { group.jsonObject["urls"] as? JsonArray }.getOrNull() ?: return emptyList()
            return urls.mapNotNull { text(it) }
        }
    }
}
