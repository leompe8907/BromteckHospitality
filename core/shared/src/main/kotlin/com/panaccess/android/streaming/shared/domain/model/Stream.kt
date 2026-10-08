package com.panaccess.android.streaming.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canal del catálogo OTT, tal como lo devuelve `getAvailableStreams`.
 *
 * **Esta —y no `getAvailableServices`— es la fuente de canales en mobile.** Se verificó contra el
 * backend real: la cuenta de pruebas devuelve `getAvailableServices -> {"answer":[]}` y en cambio
 * `getAvailableStreams` trae los canales con su URL de reproducción. Tiene sentido: los
 * *services* son servicios DVB-C/S sintonizados por hardware (por eso `Service` en Android carga
 * frecuencia, modulación, symbol rate…), mientras los *streams* son el catálogo IPTV/OTT.
 *
 * Espejo de `com.panaccess.android.streaming.data.Stream` de `:app`.
 */
@Serializable
data class Stream(
    @SerialName("id") val streamId: Int = 0,
    @SerialName("name") val rawName: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("img") val imageUrl: String? = null,
    @SerialName("lcn") val channelNumber: Int = 0,
    @SerialName("type") val type: Int = STREAM_TYPE_VIDEO,
    @SerialName("authorized") val authorized: Boolean = false,
    @SerialName("parentalControl") val parentalControl: Boolean = false,
    // Llega **entrecomillado** en el JSON ("epgStreamId":"699343"). Se declara String para no
    // depender de la coerción del parser, y se expone convertido más abajo.
    @SerialName("epgStreamId") val rawEpgStreamId: String? = null,
    @SerialName("catchupDays") val catchupDays: Int = 0,
    @SerialName("backgroundColor") val backgroundColor: String? = null,
    @SerialName("bouquetIds") val bouquetIds: List<Int> = emptyList(),
) {
    /**
     * Nombre para mostrar. El CAS manda entidades HTML y Android lo pasa por `Html.fromHtml`;
     * acá se decodifican las más frecuentes. Si aparecen casos raros conviene una librería, no
     * ampliar esta tabla indefinidamente.
     */
    val name: String
        get() = rawName
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")

    val epgStreamId: Int get() = rawEpgStreamId?.toIntOrNull() ?: 0

    /** Los de audio se muestran como radio en la UI. */
    val isAudio: Boolean get() = type == STREAM_TYPE_AUDIO || type == STREAM_TYPE_EXTERN_AUDIO

    /** `true` si tiene catchup disponible. */
    val hasCatchup: Boolean get() = catchupDays > 0

    companion object {
        const val STREAM_TYPE_VIDEO = 1
        const val STREAM_TYPE_AUDIO = 2
        const val STREAM_TYPE_EXTERN_VIDEO = 101
        const val STREAM_TYPE_EXTERN_AUDIO = 102
    }
}
