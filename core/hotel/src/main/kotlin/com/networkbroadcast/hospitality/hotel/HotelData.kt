package com.networkbroadcast.hospitality.hotel

import kotlinx.serialization.Serializable

/** Texto en varios idiomas: {"es": "...", "en": "..."}. */
typealias LocalizedText = Map<String, String>

/**
 * Devuelve el texto en [language]; si falta, prueba [fallback], después inglés, y por último
 * el primero que haya. Nunca falla por un idioma sin traducir.
 */
fun LocalizedText.resolve(language: String, fallback: String = "es"): String =
    this[language] ?: this[fallback] ?: this["en"] ?: values.firstOrNull().orEmpty()

/**
 * Formato de hotel_data.json. Es el mismo que usa la rama ia-hoteleria_250221 (personalización
 * RIU), así los JSON que ya existen sirven sin convertir. `videos` es opcional y nuevo.
 */
@Serializable
data class HotelData(
    val hotelInfo: HotelInfo,
    val images: List<HotelMedia> = emptyList(),
    val videos: List<HotelMedia> = emptyList(),
    val activities: List<HotelActivity> = emptyList(),
)

@Serializable
data class HotelInfo(
    val name: LocalizedText,
    val description: LocalizedText,
)

@Serializable
data class HotelMedia(
    val id: Int,
    val title: LocalizedText = emptyMap(),
    val description: LocalizedText = emptyMap(),
    val url: String,
    val thumbnailUrl: String? = null,
)

/**
 * Actividad con horario (ej. "Yoga — 9am, Salón A"). `date` nulo = se repite todos los días;
 * con valor, es un evento puntual (ej. un show). Formatos: `date` "2026-10-09",
 * `startTime`/`endTime` "09:00".
 */
@Serializable
data class HotelActivity(
    val id: Int,
    val title: LocalizedText,
    val description: LocalizedText = emptyMap(),
    val imageUrl: String? = null,
    val location: LocalizedText = emptyMap(),
    val date: String? = null,
    val startTime: String,
    val endTime: String? = null,
)
