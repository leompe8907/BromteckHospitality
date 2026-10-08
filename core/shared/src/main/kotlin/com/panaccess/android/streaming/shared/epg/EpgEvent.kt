package com.panaccess.android.streaming.shared.epg

import com.panaccess.android.streaming.shared.cas.LenientBooleanSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Textos del evento en un idioma.
 *
 * El EPG trae los títulos y descripciones **anidados en un array `languages`**, no en la raíz del
 * evento. Se verificó contra el backend: parseando `title` del nivel superior los títulos venían
 * todos vacíos aunque los horarios estuvieran bien.
 */
@Serializable
data class EpgLocalizedText(
    @SerialName("title") val title: String = "",
    @SerialName("shortDescription") val shortDescription: String? = null,
    @SerialName("extendedDescription") val extendedDescription: String? = null,
    @SerialName("language") val language: String? = null,
)

/**
 * Evento (programa) de la guía, tal como viene en el JSON que sirve el CDN de EPG.
 *
 * Los tiempos llegan como texto `"YYYY-MM-DD HH:MM:SS"` **en UTC**, aunque no traigan indicador
 * de zona (se verificó contra Android y contra el backend; ver el KDoc de `EpgSchedule`). Se
 * conservan como string y los interpreta `EpgSchedule.parse`: el modelo es lo que manda el CDN,
 * sin reinterpretar.
 */
@Serializable
data class EpgEvent(
    @SerialName("eventId") val eventId: Long = 0,
    @SerialName("start") val start: String = "",
    @SerialName("end") val end: String = "",
    @SerialName("epgStreamId") val epgStreamId: Int = 0,
    @SerialName("catchupId") val catchupId: Int = 0,
    @SerialName("imageUrl") val imageUrl: String? = null,
    /**
     * Edad mínima; **-1 si el evento no la trae**, como `EpgEvents` en Android (`optInt(…, -1)`).
     * No es lo mismo que 0: con la norma de Brasil el 0 es "L" (libre).
     */
    @SerialName("parentalRating") val parentalRating: Int = -1,
    /**
     * El programa pide el PIN de la licencia aunque el canal no lo pida (`EpgEvents.parentalControl`
     * en Android, que lo lee con `optBoolean`). Ver `ParentalPolicy`.
     */
    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("parentalControl") val parentalControl: Boolean = false,
    @SerialName("languages") val languages: List<EpgLocalizedText> = emptyList(),
) {
    /**
     * Título en el primer idioma disponible.
     *
     * Android hace lo mismo y deja un `TODO: handle multiple languages` en `EpgEvents.load()`. La
     * config trae `epgLanguages` (p. ej. `["eng","deu"]`), así que elegir por preferencia del
     * usuario es posible — pero es una decisión de producto, no un detalle de parseo.
     */
    val title: String get() = languages.firstOrNull()?.title.orEmpty()

    val shortDescription: String? get() = languages.firstOrNull()?.shortDescription

    val extendedDescription: String? get() = languages.firstOrNull()?.extendedDescription

    /**
     * Calificación por edad como la muestra Android: `+13`, o `null` si el evento no la trae.
     *
     * `EpgEventInfoFragment` sólo la muestra cuando es mayor que 0 — el 0 significa "sin
     * calificación", no "apto para todo público".
     */
    val parentalRatingLabel: String? get() = if (parentalRating > 0) "+$parentalRating" else null

    /** `true` si el evento se puede ver por catchup. */
    val hasCatchup: Boolean get() = catchupId > 0
}
