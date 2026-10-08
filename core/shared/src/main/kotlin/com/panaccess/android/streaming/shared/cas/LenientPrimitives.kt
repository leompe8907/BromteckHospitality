package com.panaccess.android.streaming.shared.cas

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lectores tolerantes para campos que **el mismo CAS manda con distinto tipo según el operador**.
 *
 * No es paranoia: costó un catálogo entero. `parentalRating` venía `null` en el operador con el
 * que se modeló y `"L"` / `"16"` / `"10"` en otro. Declarado como `Int?`, el segundo rompía la
 * deserialización de **toda la lista**, y como `answerList` devuelve lista vacía cuando no puede
 * parsear, la pantalla de películas se veía igual que si el operador no tuviera catálogo.
 *
 * La lección general: un campo que no se vio en dos operadores distintos no tiene tipo conocido.
 * Donde el tipo dependa del operador, se lee así y no con el serializador estricto.
 */

/**
 * Cualquier primitivo JSON leído como texto. `null` sigue siendo `null`.
 *
 * Sirve para valores que son **etiquetas**, aunque a veces lleguen sin comillas: una clasificación
 * `16` y una `"16"` significan lo mismo, y `"L"` no es un número pero es igual de válida.
 */
object PrimitiveAsStringSerializer : KSerializer<String?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PrimitiveAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val content = (element as? JsonPrimitive)?.content ?: return null
        return content.ifBlank { null }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "")
    }
}

/**
 * Entero tolerante: acepta número, número entrecomillado, o texto no numérico (que vale 0).
 *
 * El 0 para lo no numérico sigue a Android, que traduce `"L"` —libre para todas las edades— a 0
 * en `Video.parseParentalRating`.
 */
object LenientIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return 0
        val content = (element as? JsonPrimitive)?.content ?: return 0
        return content.toIntOrNull() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * Booleano tolerante: `true`/`false` con o sin comillas, y `1`/`0`. Lo demás —incluido `null`—
 * vale `false`, como el `optBoolean(campo, false)` con el que Android lee `parentalControl`.
 */
object LenientBooleanSerializer : KSerializer<Boolean> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return false
        return when ((element as? JsonPrimitive)?.content?.lowercase()) {
            "true", "1" -> true
            else -> false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}
