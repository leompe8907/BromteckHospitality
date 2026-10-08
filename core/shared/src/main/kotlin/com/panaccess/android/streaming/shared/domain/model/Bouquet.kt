package com.panaccess.android.streaming.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paquete comercial de canales, devuelto por `getBouquets`.
 *
 * Ojo con la clave del id: en el JSON es **`bouquetId`**, no `id` (a diferencia de `Stream`, donde
 * sí es `id`). Cada función del CAS nombra sus campos a su manera; conviene mirar el modelo de
 * Android antes de asumir.
 */
@Serializable
data class Bouquet(
    @SerialName("bouquetId") val bouquetId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("priority") val priority: Int = 0,
    @SerialName("featured") val featured: Boolean = false,
    @SerialName("isMain") val isMain: Boolean = false,
    @SerialName("backgroundColor") val backgroundColor: String? = null,
    @SerialName("textColor") val textColor: String? = null,
    @SerialName("customData") val customData: String? = null,
)
