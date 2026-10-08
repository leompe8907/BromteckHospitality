package com.panaccess.android.streaming.shared.domain.model

import com.panaccess.android.streaming.shared.cas.LenientBooleanSerializer
import com.panaccess.android.streaming.shared.cas.LenientIntSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Programa disponible para ver en diferido. */
@Serializable
data class Catchup(
    @SerialName("id") val catchupId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("start") val start: String = "",
    @SerialName("duration") val durationSeconds: Int = 0,
    @SerialName("eventId") val eventId: Long = 0,
    @SerialName("shortDesc") val shortDescription: String? = null,
    @SerialName("extendedDesc") val extendedDescription: String? = null,
    // Lector tolerante por la misma razón que en `VodItem`: es el mismo campo del mismo CAS, y
    // hay operadores que lo mandan como texto. Con esta cuenta no se pudo ejercitar —no tiene
    // catchup— pero un número sigue parseando igual que antes.
    @Serializable(with = LenientIntSerializer::class)
    @SerialName("parentalRating") val parentalRating: Int = 0,
    /** El programa pide el PIN de la licencia (`Catchup.parentalControl` en Android). */
    @Serializable(with = LenientBooleanSerializer::class)
    @SerialName("parentalControl") val parentalControl: Boolean = false,
    /**
     * Imagen del programa. **Todos los catchups de la cuenta de pruebas traen una** (1.241 de
     * 1.241), a diferencia de los eventos del EPG: por eso la pantalla se puede permitir mostrar
     * tarjetas con imagen y no una lista de texto.
     */
    @SerialName("imageUrl") val imageUrl: String? = null,
    /**
     * Hasta cuándo se puede ver. El catchup **caduca**: el operador lo guarda una cantidad de días
     * y después deja de estar. Android lo modela en `Catchup.expiry` y acepta varios formatos
     * (epoch, `yyyy-MM-dd HH:mm`, ISO), así que acá se guarda crudo y lo interpreta quien lo use.
     */
    @SerialName("expiry") val expiry: String? = null,
) {
    val durationMinutes: Int get() = durationSeconds / 60
}

/**
 * Canal con sus programas en diferido, devuelto por `getCatchupGroups`.
 *
 * Se reproduce con `getTopLevelCatchupM3u8UrlByCatchupId` de la librería — igual que el vivo, la
 * URL hay que pedírsela al DRM, no se arma a mano.
 */
@Serializable
data class CatchupGroup(
    @SerialName("catchupGroupId") val catchupGroupId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("epgStreamId") val epgStreamId: Int = 0,
    @SerialName("lcn") val channelNumber: Int = 0,
    @SerialName("img") val imageUrl: String? = null,
    @SerialName("background") val backgroundColor: String? = null,
    @SerialName("catchups") val catchups: List<Catchup> = emptyList(),
)
