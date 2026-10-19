package com.networkbroadcast.hospitality.panaccess

import android.util.Log
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Un cuadrado de la fila de widgets de la home, configurado por Delancer (ver OperatorConfig.widgets).
 * Pensado para instalaciones del hotel (bar, restaurant, gimnasio, pileta): al tocarlo abre una
 * ficha de solo lectura (sin ninguna acción) con horario, piso y una galería de fotos navegable.
 * [imageUrl] es la foto que se ve en la tarjeta de la home y también la primera de la galería.
 */
data class HomeWidget(
    val title: String,
    val description: String?,
    val imageUrl: String,
    val hours: String? = null,
    val floor: String? = null,
    /** Fotos de la ficha de detalle, en orden; incluye siempre [imageUrl] primero. */
    val gallery: List<String> = listOf(imageUrl),
)

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
    val weatherTempCelsius: Int?,
    val weatherDescription: String?,
    /** true si `answer.weather` vino en la respuesta, haya podido parsearse o no -- para
     *  distinguir en logs "Cableatlántico no configuró esto" de "vino pero no se pudo leer". */
    val hasWeatherField: Boolean,
) {
    fun param(key: String): String? = parameters[key]?.takeIf { it.isNotBlank() }
    fun flag(key: String): Boolean? = param(key)?.let { it.equals("true", true) || it == "1" }

    // Clima: Panaccess ya lo resuelve en su backend (consulta OpenWeatherMap con la ubicación
    // que Cableatlántico cargó para esta cuenta) y lo manda embebido en getClientConfig — acá
    // solo se parsea, no se llama a ninguna API propia (ver docs/decisiones.md en el backend
    // para el camino alternativo, para hoteles que no pasen por Panaccess).
    // Flag declarativo para Delancer: true = mostrar (análogo a X_APP_LOGO/X_APP_CARD_COLOR).
    // "X_SHOW_WEATHER"/"SHOW_WEATHER_DATA" quedan como fallback de cuentas viejas que ya los
    // traían (se vieron con dos nombres distintos según la cuenta) -- ahí el default es
    // mostrarlo apenas haya temperatura, y =false lo oculta a propósito.
    val weatherText: String?
        get() {
            val shown = flag("X_APP_SHOW_WEATHER") ?: flag("X_SHOW_WEATHER") ?: flag("SHOW_WEATHER_DATA") ?: true
            if (!shown) return null
            val temp = weatherTempCelsius ?: return null
            return listOfNotNull("$temp°", weatherDescription).joinToString(" · ")
        }

    // Contenido "acerca del hotel" del sistema viejo: GET <url>/<carpeta>/images_hotel
    val hotelApk: Boolean? get() = flag("X_HOTEL_APK")
    val hotelShowAbout: Boolean? get() = flag("X_HOTEL_SHOW_ABOUT")
    val hotelBaseUrl: String? get() = param("X_HOTEL_APK_BASE_URL")
    val hotelBaseFolder: String? get() = param("X_HOTEL_APK_BASE_FOLDER")
    val showRoomNumber: Boolean? get() = flag("X_DESIGN_SHOW_HOTEL_ROOM_NUMBER")
    val defaultLanguage: String? get() = param("X_DEFAULT_LANGUAGE")

    // Fondo de marca configurable por Delancer desde el panel de Cableatlántico. Si no está
    // configurado, se cae al fondo genérico de la marca (ver HospitalityApp.kt).
    val hotelBackgroundUrl: String? get() = param("X_APP_LOGO_BACKGROUND")

    // Logo de marca configurable por Delancer. Nombre propuesto, análogo a X_APP_LOGO_BACKGROUND
    // — a confirmar/pedir a Cableatlántico igual que se hizo con el fondo. Si no está configurado,
    // se cae al logo estático de la marca (ver LanguageScreen.kt).
    val hotelLogoUrl: String? get() = param("X_APP_LOGO")

    // Color de las tarjetas (Entretenimiento y Acciones rápidas) configurable por Delancer, en
    // hex (ej. "#163F42"). El color de texto sobre la tarjeta se calcula solo según este valor
    // (ver HospitalityColors.readableTextColor) -- no hace falta un parámetro de texto aparte.
    val cardColorHex: String? get() = param("X_APP_CARD_COLOR")

    // Imagen de placeholder (fondo del hotel, pósters de VOD mientras cargan o si fallan)
    // configurable por Delancer. Si no está configurada, se cae al gris fijo de siempre según el
    // tema (ver screens/Common.kt#rememberPlaceholderPainter en app/tv).
    val placeholderImageUrl: String? get() = param("X_APP_PLACEHOLDER_IMAGE")

    // Widgets personalizables (cuadrados de la home con foto + título + descripción) configurados
    // por Delancer vía Panaccess -- no por el panel de staff propio (eso es para hoteles con
    // backend propio, ver Parte C del plan). Slots fijos (X_APP_WIDGET_1.. _4) en vez de una lista
    // dinámica porque los parámetros de Panaccess son pares clave/valor sueltos, no un array; un
    // widget solo cuenta si tiene título E imagen -- la descripción es opcional.
    val widgets: List<HomeWidget>
        get() = (1..MAX_WIDGETS).mapNotNull { n ->
            val title = param("X_APP_WIDGET_${n}_TITLE") ?: return@mapNotNull null
            val imageUrl = param("X_APP_WIDGET_${n}_IMAGE") ?: return@mapNotNull null
            HomeWidget(
                title = title,
                description = param("X_APP_WIDGET_${n}_DESC"),
                imageUrl = imageUrl,
                hours = param("X_APP_WIDGET_${n}_HOURS"),
                floor = param("X_APP_WIDGET_${n}_FLOOR"),
                gallery = listOfNotNull(imageUrl, param("X_APP_WIDGET_${n}_IMAGE_2"), param("X_APP_WIDGET_${n}_IMAGE_3")),
            )
        }

    companion object {
        private const val MAX_WIDGETS = 4

        fun parse(rawJson: String): OperatorConfig {
            val answer = CasEnvelope.answer(rawJson) as? JsonObject
            val weather = weather(answer)
            return OperatorConfig(
                parameters = parameters(answer),
                operator = text(answer?.get("subscriber")?.let { runCatching { it.jsonObject["operator"] }.getOrNull() }),
                subscriberName = subscriberName(answer),
                epgCdnUrls = epgCdnUrls(answer),
                defaultTimeZone = text(answer?.get("defaultTimeZone")),
                weatherTempCelsius = weather.first,
                weatherDescription = weather.second,
                // Ojo: Panaccess manda la clave "weather" igual aunque no haya clima configurado
                // para esta cuenta -- con el valor JSON null (JsonNull), no ausente. Por eso no
                // alcanza con "!= null" de Kotlin: hay que descartar explícitamente JsonNull.
                hasWeatherField = runCatching { answer?.get("weather")?.let { it !is JsonNull } }.getOrNull() == true,
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

        /**
         * `answer.weather.data` es un string con JSON adentro (así lo manda Panaccess, no un
         * objeto anidado directo) -- formato crudo de OpenWeatherMap: ver `Openweather.kt`/
         * `ClientConfig.kt` del repo base. Temperatura en Kelvin, se convierte acá.
         */
        private fun weather(answer: JsonObject?): Pair<Int?, String?> {
            val weatherObj = runCatching { answer?.get("weather")?.jsonObject }.getOrNull()
            // Log de diagnóstico permanente: provider+data crudos tal cual los manda Panaccess,
            // para poder ver de entrada si la cuenta trae algo raro (ej. provider que no sea
            // "OPENWEATHER", que es lo único que acepta la app oficial -- ver ClientConfig.java del
            // repo base) sin tener que ir a agregar un log cada vez que haya que investigar esto.
            // runCatching: android.util.Log no está mockeado en los tests unitarios de esta clase
            // (JVM puro, sin Android real) y tira RuntimeException apenas se la llama -- acá no
            // importa, es solo diagnóstico.
            runCatching {
                Log.d(
                    "Hospitality.Session",
                    "weather crudo: provider=${weatherObj?.get("provider")?.let { text(it) }}, " +
                        "data=${weatherObj?.get("data")?.let { text(it) }?.take(300)}",
                )
            }
            val dataString = weatherObj?.get("data")?.let { text(it) } ?: return null to null
            val first = runCatching {
                Json.parseToJsonElement(dataString).jsonObject["list"]?.let { it as JsonArray }?.firstOrNull()?.jsonObject
            }.getOrNull() ?: return null to null
            val tempKelvin = runCatching { first["main"]?.jsonObject?.get("temp")?.jsonPrimitive?.content?.toDouble() }.getOrNull()
            val tempCelsius = tempKelvin?.let { Math.round(it - 273.15).toInt() }
            val description = runCatching {
                (first["weather"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("description")?.jsonPrimitive?.content
            }.getOrNull()
            return tempCelsius to description
        }
    }
}
