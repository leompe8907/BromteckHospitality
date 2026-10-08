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
