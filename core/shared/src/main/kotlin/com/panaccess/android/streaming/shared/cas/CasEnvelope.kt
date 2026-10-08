package com.panaccess.android.streaming.shared.cas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parseo del "sobre" con el que responde el CAS de Panaccess.
 *
 * Toda función del CAS devuelve la misma forma:
 *
 * ```json
 * { "success": true, "answer": <lo que sea> }
 * ```
 *
 * Del lado Android esto se consume como `result.optJSONArray("answer")` en cada call site (ver
 * `LoginRepositoryImpl`). Acá se centraliza una sola vez, porque `JSONObject` es Android-only y
 * en `commonMain` hay que usar `kotlinx.serialization`.
 */
object CasEnvelope {

    /**
     * Configuración deliberadamente tolerante:
     * - `ignoreUnknownKeys`: el CAS agrega campos entre versiones y no se puede romper por eso.
     * - `isLenient`: la librería devuelve números y booleanos como strings según la función.
     * - `coerceInputValues`: un `null` explícito en un campo con default usa el default en vez
     *   de fallar.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** `success` del sobre, o `false` si no vino. */
    fun isSuccess(raw: String): Boolean =
        runCatching {
            json.parseToJsonElement(raw).jsonObject["success"]?.jsonPrimitive?.content == "true"
        }.getOrDefault(false)

    /** El `answer` crudo, o `null` si no hay. */
    fun answer(raw: String): JsonElement? =
        runCatching { json.parseToJsonElement(raw).jsonObject["answer"] }.getOrNull()

    /**
     * Decodifica `answer` como lista de [T].
     *
     * Devuelve lista vacía si `answer` no es un array — no lanza: varias funciones del CAS
     * devuelven `answer: false` o un objeto en vez de lista cuando no hay datos, y eso es una
     * respuesta válida, no un error de parseo.
     */
    inline fun <reified T> answerList(raw: String): List<T> {
        val element = answer(raw) as? JsonArray ?: return emptyList()
        return runCatching { json.decodeFromJsonElement<List<T>>(element) }
            .onFailure {
                // Un fallo de parseo NO puede verse igual que "no hay datos": son causas
                // distintas y confundirlas cuesta horas de diagnóstico (ya pasó una vez con el
                // catálogo vacío). Si esto aparece, el modelo no coincide con lo que manda el
                // CAS — revisar los @SerialName y los tipos contra la respuesta cruda.
                println("[CasEnvelope] ERROR parseando answer como List<${T::class.simpleName}>: $it")
            }
            .getOrDefault(emptyList())
    }

    /** Decodifica `answer` como objeto [T], o `null` si no se puede. */
    inline fun <reified T> answerObject(raw: String): T? {
        val element = answer(raw) as? JsonObject ?: return null
        return runCatching { json.decodeFromJsonElement<T>(element) }.getOrNull()
    }
}
