package com.networkbroadcast.hospitality.companion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Forma de los mensajes con el backend (`contract/pairing.json` y docs/emparejamiento.md del
 * backend). Todo lo que sale de un cuerpo mal formado lanza IllegalArgumentException: los clientes
 * lo tratan como "el backend no respondió algo usable".
 */

internal fun parseObject(text: String): JsonObject = Json.parseToJsonElement(text) as? JsonObject
    ?: throw IllegalArgumentException("se esperaba un objeto JSON")

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

/** Cuerpo de `POST /api/phone/commands/`. */
internal fun TvCommand.toBody(): JsonObject = buildJsonObject {
    when (val c = this@toBody) {
        is TvCommand.PlayChannel -> { put("type", "PlayChannel"); put("channelId", c.channelId) }
        is TvCommand.PlayVod -> { put("type", "PlayVod"); put("vodId", c.vodId) }
        TvCommand.OpenGuide -> put("type", "OpenGuide")
        TvCommand.OpenHotelInfo -> put("type", "OpenHotelInfo")
        is TvCommand.Key -> { put("type", "Key"); put("key", c.key.name) }
    }
}

/** Una orden de `GET /api/tv/commands/`. */
internal sealed interface ServerCommand {
    val id: Long

    /** La mandó un celular: la TV la ejecuta. */
    data class FromPhone(override val id: Long, val command: TvCommand) : ServerCommand

    /** La manda el servidor al terminar la estadía. */
    data class Checkout(override val id: Long, val stayNumber: Long?) : ServerCommand
}

/** null si el tipo no se conoce (una versión más nueva del backend): la TV la ignora en lugar de romperse. */
internal fun parseServerCommand(item: JsonElement): ServerCommand? {
    val obj = item as? JsonObject ?: return null
    val id = obj.long("id") ?: return null
    return when (obj.string("type")) {
        "PlayChannel" -> obj.string("channelId")?.let { ServerCommand.FromPhone(id, TvCommand.PlayChannel(it)) }
        "PlayVod" -> obj.string("vodId")?.let { ServerCommand.FromPhone(id, TvCommand.PlayVod(it)) }
        "OpenGuide" -> ServerCommand.FromPhone(id, TvCommand.OpenGuide)
        "OpenHotelInfo" -> ServerCommand.FromPhone(id, TvCommand.OpenHotelInfo)
        // Una tecla que esta versión no conoce se ignora, igual que un tipo desconocido.
        "Key" -> RemoteKey.entries.firstOrNull { it.name == obj.string("key") }?.let { ServerCommand.FromPhone(id, TvCommand.Key(it)) }
        "Checkout" -> ServerCommand.Checkout(id, obj.long("stayNumber"))
        else -> null
    }
}

internal fun parseHandoff(element: JsonElement?): Handoff? {
    val obj = element as? JsonObject ?: return null
    val colors = obj.obj("colors")
    return Handoff(
        hotelName = obj.string("hotelName"),
        roomNumber = obj.string("roomNumber"),
        language = obj.string("language"),
        guestName = obj.string("guestName"),
        logoUrl = obj.string("logoUrl"),
        backgroundUrl = obj.string("backgroundUrl"),
        colors = HandoffColors(
            card = colors?.string("card"),
            text = colors?.string("text"),
            playerControl = colors?.string("playerControl"),
            confirm = colors?.string("confirm"),
            cancel = colors?.string("cancel"),
        ),
    )
}

private fun JsonObjectBuilder.field(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}

/** `session` del canje o de `/api/phone/me/`: `{username, passwordMd5}` (nombres provisorios, ver contract/pairing.json). */
internal fun parseHotelLogin(element: JsonElement?): HotelLogin? {
    val obj = element as? JsonObject ?: return null
    return HotelLogin(obj.string("username") ?: return null, obj.string("passwordMd5") ?: return null)
}

internal fun Handoff.toJson(): JsonObject = buildJsonObject {
    field("hotelName", hotelName)
    field("roomNumber", roomNumber)
    field("language", language)
    field("guestName", guestName)
    field("logoUrl", logoUrl)
    field("backgroundUrl", backgroundUrl)
    put("colors", buildJsonObject {
        field("card", colors.card)
        field("text", colors.text)
        field("playerControl", colors.playerControl)
        field("confirm", colors.confirm)
        field("cancel", colors.cancel)
    })
}

internal fun LinkedTv.toJson(): JsonObject = buildJsonObject {
    field("roomNumber", roomNumber)
    put("tvName", tvName)
    handoff?.let { put("handoff", it.toJson()) }
    login?.let { put("login", buildJsonObject { put("username", it.username); put("passwordMd5", it.passwordMd5) }) }
}

internal fun parseLinkedTv(obj: JsonObject, handoff: JsonElement? = obj["handoff"], login: JsonElement? = null): LinkedTv = LinkedTv(
    roomNumber = obj.string("roomNumber"),
    tvName = obj.string("tvName") ?: "TV",
    handoff = parseHandoff(handoff),
    // Guardado: `login` dentro del objeto; recién llegado del backend: `session`, que se pasa aparte.
    login = parseHotelLogin(login ?: obj["login"]),
)

/** `error` del cuerpo de un error del backend, si lo trae. */
internal fun errorName(body: String): String? = runCatching { parseObject(body).string("error") }.getOrNull()

/** Los errores del canje (`linkErrors` de contract/pairing.json) a los motivos de la app. */
internal fun linkFailureReason(status: Int): LinkState.Reason = when (status) {
    400 -> LinkState.Reason.InvalidCode
    410 -> LinkState.Reason.Expired
    // 503 (no disponible) y 429 (TooManyAttempts: no existe en Reason, ver docs/plan-emparejamiento-celular.md D22).
    else -> LinkState.Reason.NotAvailable
}
