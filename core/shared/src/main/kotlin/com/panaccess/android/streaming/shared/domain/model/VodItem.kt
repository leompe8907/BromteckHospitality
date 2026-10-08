package com.panaccess.android.streaming.shared.domain.model

import com.panaccess.android.streaming.shared.cas.PrimitiveAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contenido de video bajo demanda.
 *
 * **Los campos son los que devuelve el backend de verdad**, no los que sugería el código de
 * Android: se pidió `getVodContent` contra la cuenta real y se modeló sobre esa respuesta. Importa
 * porque la versión anterior de este modelo buscaba un campo `poster` **que no existe** en
 * `getVodContent`, así que el catálogo entero se dibujaba sin imágenes.
 *
 * Hay dos llamadas con dos niveles de detalle, y conviene no confundirlas:
 *
 * - **`getVodContent`** (paginada, `offset`/`limit`) es el catálogo: trae lo de acá arriba, sin
 *   precios ni estado de compra, y **sin URLs de imagen** — sólo `image1Id`/`image2Id`/`image3Id`,
 *   con los que la URL se arma sola (ver [VodImages]).
 * - **`getVodInfo`** (`vodIds`) es la ficha: agrega `purchased`, `rentable`, `buyable`,
 *   `rentPrice`, `rentPeriod`, `rentedUntil`, `expiry`, `assets`, `markers` y un `poster` ya
 *   resuelto. Ver [VodDetail].
 *
 * Se reproduce con `getTopLevelVodM3u8UrlByVodId` de la librería.
 */
@Serializable
data class VodItem(
    @SerialName("id") val vodId: Int = 0,
    @SerialName("name") val name: String = "",
    // `synopsis` y no `description`: en Swift, `description` choca con el de
    // `CustomStringConvertible` (que no es opcional) y el compilador rechaza el `if let`.
    @SerialName("description") val synopsis: String? = null,
    /** Segundos. */
    @SerialName("duration") val durationSeconds: Int = 0,
    @SerialName("isSeries") val isSeries: Boolean = false,
    @SerialName("parentalControl") val parentalControl: Boolean = false,
    /** Calificación del operador, 0-100. `0` es "sin calificar", no "malísima". */
    @SerialName("rating") val rating: Int = 0,
    /**
     * Clasificación por edad **como etiqueta**: `"L"` (libre), `"10"`, `"16"`… No es un número:
     * se modeló `Int?` mirando un operador donde siempre venía `null`, y con otro que manda `"L"`
     * fallaba el parseo de la lista entera y el catálogo se veía vacío. Android la lee igual, como
     * texto (`Video.parseParentalRating`).
     */
    @Serializable(with = PrimitiveAsStringSerializer::class)
    @SerialName("parentalRating") val parentalRating: String? = null,
    /** Ids de categoría a las que pertenece; cruzan con `getOttCategoryGroups`. */
    @SerialName("categories") val categoryIds: List<Int> = emptyList(),
    @SerialName("priority") val priority: Int = 0,
    /** Póster vertical. */
    @SerialName("image1Id") val posterImageId: Int? = null,
    /** Fondo 16:9. */
    @SerialName("image2Id") val backgroundImageId: Int? = null,
    /** Imagen extra 16:9. */
    @SerialName("image3Id") val extraImageId: Int? = null,
    /** `"yyyy-MM-dd HH:mm:ss"`. De acá sale el año que se muestra en la ficha. */
    @SerialName("libraryReleaseDate") val libraryReleaseDate: String? = null,
) {
    /** Año de estreno, o `null` si el backend no lo mandó. */
    val year: Int? get() = libraryReleaseDate?.take(4)?.toIntOrNull()

    /**
     * Edad mínima, o `null` si no la hay. `"L"` es 0: libre para todas las edades.
     * Mismo criterio que `Video.parseParentalRating` en Android.
     */
    val parentalRatingAge: Int?
        get() = when (val rating = parentalRating) {
            null -> null
            "L", "l" -> 0
            else -> rating.toIntOrNull()
        }

    val durationMinutes: Int get() = durationSeconds / 60
}

/**
 * Ficha completa de un título (`getVodInfo`).
 *
 * Hereda los campos del catálogo y agrega lo comercial. Se modela aparte y no como campos
 * opcionales de [VodItem] porque **son dos respuestas distintas**: mezclar las dos en un tipo deja
 * a quien lo lee sin saber si un `purchased = false` significa "no comprado" o "no preguntamos".
 */
@Serializable
data class VodDetail(
    @SerialName("id") val vodId: Int = 0,
    @SerialName("name") val name: String = "",
    // `synopsis` y no `description`: en Swift, `description` choca con el de
    // `CustomStringConvertible` (que no es opcional) y el compilador rechaza el `if let`.
    @SerialName("description") val synopsis: String? = null,
    @SerialName("duration") val durationSeconds: Int = 0,
    @SerialName("rating") val rating: Int = 0,
    @Serializable(with = PrimitiveAsStringSerializer::class)
    @SerialName("parentalRating") val parentalRating: String? = null,
    @SerialName("parentalControl") val parentalControl: Boolean = false,
    @SerialName("categories") val categoryIds: List<Int> = emptyList(),
    @SerialName("image1Id") val posterImageId: Int? = null,
    @SerialName("image2Id") val backgroundImageId: Int? = null,
    @SerialName("image3Id") val extraImageId: Int? = null,
    /** URL ya armada por el backend, en tamaño original. */
    @SerialName("poster") val posterUrl: String? = null,
    @SerialName("purchased") val purchased: Boolean = false,
    @SerialName("rentable") val rentable: Boolean = false,
    @SerialName("buyable") val buyable: Boolean = false,
    @SerialName("rentPrice") val rentPrice: Double = 0.0,
    @SerialName("buyPrice") val buyPrice: Double = 0.0,
    /** Precios ya formateados con la moneda del operador (`"0,00 €"`). Se usan tal cual: el
     *  formato de moneda es decisión del operador, no del dispositivo. */
    @SerialName("rentPriceF") val rentPriceFormatted: String? = null,
    @SerialName("buyPriceF") val buyPriceFormatted: String? = null,
    /** Segundos que dura un alquiler. */
    @SerialName("rentPeriod") val rentPeriodSeconds: Int = 0,
    @SerialName("rentedUntil") val rentedUntil: String? = null,
    @SerialName("expiry") val expiry: String? = null,
) {
    val durationMinutes: Int get() = durationSeconds / 60
}

/**
 * Arma las URLs de imagen de un título.
 *
 * El backend no las manda en el catálogo: manda ids. El formato lo define el CAS y es
 * `<servidor>/public/images/<id>/v/<variante>`, donde la variante es un recorte ya generado. Los
 * nombres de variante salen de `Video.kt` de Android — **no se inventan**: si se pide una que no
 * existe, el CDN devuelve 404.
 *
 * El servidor base es el que resolvió la librería DRM (`getResponsibleServer`), así que no está
 * cableado: cada operador tiene el suyo (en la cuenta de pruebas, `cv10.panaccess.com`).
 *
 * **Cada id tiene sólo las variantes de su rol**, y conviene no pedirle otras. Comprobado contra
 * el CDN real: `image1Id` responde a `vod_poster_list`, `vod_poster_info` y `original`, y a
 * `vod_extra` contesta con una página de error; `image3Id` responde sólo a `vod_extra`. Y el error
 * **no viene como 404**: viene `200` con `text/html`, así que nadie se entera salvo porque la
 * imagen no aparece. De ahí que cada función de acá pare firme en un id concreto.
 */
object VodImages {

    /** Póster chico, para grillas y filas. */
    const val POSTER_LIST = "vod_poster_list.jpg"

    /** Póster grande, para la ficha. */
    const val POSTER_INFO = "vod_poster_info.jpg"

    /** 16:9 de fondo. */
    const val BACKGROUND = "vod_background.jpg"

    /** 16:9 extra. */
    const val EXTRA = "vod_extra.jpg"

    /** La imagen tal como se subió. Es la que devuelve `getVodInfo` en su campo `poster`. */
    const val ORIGINAL = "original.jpg"

    fun url(baseUrl: String?, imageId: Int?, variant: String): String? {
        if (baseUrl.isNullOrBlank()) return null
        if (imageId == null || imageId <= 0) return null
        return "${baseUrl.trimEnd('/')}/public/images/$imageId/v/$variant"
    }

    fun poster(baseUrl: String?, item: VodItem): String? =
        url(baseUrl, item.posterImageId, POSTER_LIST)

    fun bigPoster(baseUrl: String?, item: VodItem): String? =
        url(baseUrl, item.posterImageId, POSTER_INFO)

    fun background(baseUrl: String?, item: VodItem): String? =
        url(baseUrl, item.backgroundImageId, BACKGROUND)

    fun extra(baseUrl: String?, item: VodItem): String? =
        url(baseUrl, item.extraImageId, EXTRA)

    /**
     * Imagen apaisada para la cabecera de la ficha: el fondo, y si no hay, la extra.
     *
     * Hace falta la alternativa: en el catálogo real la mayoría de los títulos viene con
     * `image2Id` en `null` pero sí trae `image3Id`, que es 16:9 igual. Sin esta cadena, casi
     * ninguna ficha tendría cabecera.
     */
    fun header(baseUrl: String?, item: VodItem): String? =
        background(baseUrl, item) ?: extra(baseUrl, item)
}
