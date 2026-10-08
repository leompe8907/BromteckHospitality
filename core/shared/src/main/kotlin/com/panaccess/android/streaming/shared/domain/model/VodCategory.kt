package com.panaccess.android.streaming.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Categoría de VOD: "Acción", "Comedia", "Recomendados"…
 *
 * Los títulos traen `categories` con estos ids, así que son la forma natural de armar filas de
 * catálogo — **pero sólo las de los grupos curados**, ver [VodCategoryGroup].
 */
@Serializable
data class VodCategory(
    @SerialName("id") val categoryId: Int = 0,
    @SerialName("name") val rawName: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
) {
    /** El CAS manda entidades HTML en los nombres (`Tony D&#039;Amario`). */
    val name: String get() = HtmlEntities.decode(rawName)
}

/**
 * Grupo de categorías (`getOttCategoryGroups`): agrupa las categorías por para qué sirven.
 *
 * El [type] **no lo inventa el operador**: es un enum del CAS, y Android lo tiene escrito en
 * `CategoryGroup.kt`. Importa mucho, porque no todos los grupos son para mostrar en filas: Android
 * tiene accesores distintos para cada tipo (`getGenreLibraries`, `getActorsLibraries`,
 * `getDirectorsLibraries`, `getYearLibraries`) y los usa para filtrar, no para armar estanterías.
 *
 * Se comprobó en un operador real: sus grupos son "Recomendado" (1 categoría), "Listas" (17),
 * "Genero" (17), "Direção" (**442**), "Ano" (43) y "Actor" (**1.468**). Tratarlos todos igual daba
 * 361 filas — una por director y una por actor.
 */
@Serializable
data class VodCategoryGroup(
    @SerialName("id") val groupId: Int = 0,
    @SerialName("name") val rawName: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: Int = TYPE_OTHER,
    @SerialName("categories") val categories: List<VodCategory> = emptyList(),
) {
    val name: String get() = HtmlEntities.decode(rawName)

    /**
     * Si este grupo sirve para armar filas de catálogo.
     *
     * Sólo los **curados**: lo recomendado, las listas que armó el operador, los géneros y los
     * destacados. Actor, dirección y año son índices —una entrada por persona o por año— y
     * pertenecen a una pantalla de búsqueda o filtro, no a la portada. Los de tipo desconocido
     * quedan afuera: se vio un grupo de tipo 0 llamado como un actor y con cero categorías.
     */
    val isShelfWorthy: Boolean
        get() = type == TYPE_GENRE || type == TYPE_LIST ||
            type == TYPE_RECOMMENDED || type == TYPE_FEATURE

    companion object {
        // Valores de `CategoryGroup.kt` de Android. Son del CAS: no se renumeran por operador.
        const val TYPE_OTHER = 0
        const val TYPE_GENRE = 1
        const val TYPE_YEAR = 2
        const val TYPE_ACTOR = 3
        const val TYPE_FEATURE = 4
        const val TYPE_LIST = 5
        const val TYPE_RECOMMENDED = 6
        const val TYPE_DIRECTOR = 7
        const val TYPE_ADULT = 8
    }
}

/**
 * Biblioteca de VOD (`getVodLibraries`). Es la unidad con la que Android arma las filas de la
 * pantalla de películas (`addVodLibraryRows`).
 *
 * Cuántas hay depende del operador: uno de prueba tiene **una sola** ("Multicable", 81 títulos) y
 * otro tiene dos ("Movies" 484, "Series" 482). Con una sola, armar las filas por biblioteca daría
 * una fila con el catálogo entero adentro, así que en iOS las filas se arman por categoría; ver
 * `VodShelves`.
 */
@Serializable
data class VodLibrary(
    @SerialName("id") val libraryId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("count") val count: Int = 0,
    @SerialName("priority") val priority: Int = 0,
    @SerialName("parentalControl") val parentalControl: Boolean = false,
)
