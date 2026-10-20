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
    // hex (ej. "#163F42"). Sin X_APP_TEXT_COLOR, el color de texto sobre la tarjeta se calcula
    // solo según este valor (ver HospitalityColors.readableTextColor).
    val cardColorHex: String? get() = param("X_APP_CARD_COLOR")

    // Color de letra (e íconos que acompañan texto, ej. Acciones rápidas) configurable por
    // Delancer, en hex. Pisa el cálculo automático de contraste de X_APP_CARD_COLOR cuando el
    // operador quiere un color de letra puntual en vez de ese cálculo.
    val textColorHex: String? get() = param("X_APP_TEXT_COLOR")

    // Imagen de placeholder (fondo del hotel, pósters de VOD mientras cargan o si fallan)
    // configurable por Delancer. Si no está configurada, se cae al gris fijo de siempre según el
    // tema (ver screens/Common.kt#rememberPlaceholderPainter en app/tv).
    val placeholderImageUrl: String? get() = param("X_APP_PLACEHOLDER_IMAGE")

    // Widgets personalizables (instalaciones del hotel: bar, restaurant, gimnasio, pileta...)
    // configurados por Delancer vía Panaccess -- no por el panel de staff propio (eso es para
    // hoteles con backend propio, ver Parte C del plan). Un solo parámetro (X_APP_WIDGETS) con un
    // array JSON adentro, en vez de un parámetro por widget (como era antes, con slots fijos
    // X_APP_WIDGET_1.._4): agregar un widget más es agregar un elemento al array, no crear un
    // parámetro nuevo en Panaccess. Si el array entero viene con JSON inválido, se descarta
    // completo (lista vacía) -- es la contra de juntar todo en un solo parámetro, pero un elemento
    // individual con forma rara (no es un objeto, o le falta algo) solo se descarta a él, el resto
    // de la lista sigue andando (ver parseWidget).
    // Recibe el idioma porque título/descripción pueden venir por idioma (ver parseWidget):
    // se resuelven acá, no antes, porque el huésped puede cambiar de idioma durante la estadía.
    fun widgets(language: String): List<HomeWidget> {
        val raw = param("X_APP_WIDGETS") ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonObject)?.let { parseWidget(it, language) } }
    }

    /**
     * Formato esperado de cada elemento del array `X_APP_WIDGETS`:
     * `{"title":{"es":"Bar Valentina","en":"Valentina Bar"},"description":{"es":"..."},
     * "hours":{"es":"Abierto de 19 a 02am","en":"Open 7pm-2am"},"floor":"5",
     * "images":["https://...","https://...","https://..."]}`
     * `title`/`description`/`hours` aceptan también un texto simple en vez de un objeto por idioma
     * (se usa igual en cualquier idioma) -- todo es traducible menos `images` (son fotos, no hay
     * nada que traducir) y `floor` (es un dato, no una frase -- la palabra "Piso"/"Floor"/"Andar"
     * la pone la app sola según el idioma, ver UiText.floorLabel). Solo `title` e `images` (con al
     * menos una foto) son obligatorios; el resto es opcional. Un elemento que no sea un objeto, o
     * al que le falte algo obligatorio, se descarta (null) sin afectar al resto del array.
     */
    private fun parseWidget(obj: JsonObject, language: String): HomeWidget? {
        val title = localizedText(obj["title"]).resolve(language)?.takeIf { it.isNotBlank() } ?: return null
        val images = (obj["images"] as? JsonArray)?.mapNotNull { text(it) }.orEmpty()
        val imageUrl = images.firstOrNull() ?: return null
        return HomeWidget(
            title = title,
            description = localizedText(obj["description"]).resolve(language)?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            hours = localizedText(obj["hours"]).resolve(language)?.takeIf { it.isNotBlank() },
            // Piso: solo el número/dato, no el texto -- "Piso"/"Floor"/"Andar" lo pone la app
            // según el idioma del huésped (ver UiText.floorLabel), no hace falta traducirlo acá.
            floor = text(obj["floor"]),
            gallery = images,
        )
    }

    /** `{"es":"...","en":"..."}`, o un texto simple (vale para cualquier idioma). */
    private fun localizedText(element: kotlinx.serialization.json.JsonElement?): Map<String, String> = when (element) {
        is JsonObject -> element.mapValues { (_, v) -> text(v).orEmpty() }
        else -> text(element)?.let { mapOf("es" to it) }.orEmpty()
    }

    /** Texto en [language]; si falta, español, después inglés, y por último el primero que haya. */
    private fun Map<String, String>.resolve(language: String): String? =
        this[language] ?: this["es"] ?: this["en"] ?: values.firstOrNull()

    companion object {
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
