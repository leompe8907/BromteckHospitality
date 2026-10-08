package com.panaccess.android.streaming.shared.vod

import com.panaccess.android.streaming.shared.domain.model.VodCategory
import com.panaccess.android.streaming.shared.domain.model.VodCategoryGroup
import com.panaccess.android.streaming.shared.domain.model.VodItem

/** Una fila del catálogo: una categoría y los títulos que le pertenecen. */
data class VodShelf(
    val category: VodCategory,
    val items: List<VodItem>,
)

/**
 * Arma las filas del catálogo de películas.
 *
 * **Por categoría, no por biblioteca.** Android hace filas por biblioteca
 * (`addVodLibraryRows` sobre `getVodLibraries`), pero contra la cuenta real hay **una sola**
 * biblioteca con los 81 títulos adentro: esa fila sería el catálogo entero y no ayuda a elegir.
 * Las categorías sí: el operador definió diez géneros y los títulos ya traen sus ids.
 *
 * Es lógica de negocio pura, así que vive acá y se puede probar sin red ni dispositivo.
 */
object VodShelves {

    /** Debajo de esto una fila no vale el espacio que ocupa. */
    const val MIN_ITEMS_PER_SHELF = 2

    /**
     * Filas para un grupo de categorías, en el orden en que el operador las mandó.
     *
     * Se saltean las vacías y las de un solo título: una fila con un póster parece un error.
     */
    fun shelves(group: VodCategoryGroup, items: List<VodItem>): List<VodShelf> =
        group.categories
            .map { category ->
                VodShelf(
                    category = category,
                    items = items.filter { it.categoryIds.contains(category.categoryId) },
                )
            }
            .filter { it.items.size >= MIN_ITEMS_PER_SHELF }

    /**
     * Filas de **todos** los grupos, en el orden en que llegaron.
     *
     * Toma sólo los grupos **curados** (ver `VodCategoryGroup.isShelfWorthy`): con los índices de
     * actor y dirección adentro, un operador real daba 361 filas.
     *
     * Descarta filas repetidas **entre grupos**: la cuenta real tiene dos ("Genero" y "Listas")
     * con las mismas diez categorías por nombre y distinto id, y varias terminan conteniendo
     * exactamente los mismos títulos. Mostrarlas dos veces sería un error visible.
     *
     * Dentro de un mismo grupo **no se deduplica**, aunque dos categorías coincidan: ahí el
     * operador creó dos categorías distintas a propósito, y que hoy tengan los mismos títulos es
     * una casualidad del catálogo que puede dejar de serlo mañana. Se comprobó que importa: con la
     * regla aplicada dentro del grupo, "Acción" desaparecía por tener los mismos dos títulos que
     * "Aventura".
     */
    fun all(groups: List<VodCategoryGroup>, items: List<VodItem>): List<VodShelf> {
        val resultado = mutableListOf<VodShelf>()
        val deGruposAnteriores = mutableSetOf<Set<Int>>()

        for (group in groups.filter { it.isShelfWorthy }) {
            val delGrupo = shelves(group, items)
                .filterNot { shelf -> deGruposAnteriores.contains(shelf.items.map { it.vodId }.toSet()) }

            resultado += delGrupo
            deGruposAnteriores += delGrupo.map { shelf -> shelf.items.map { it.vodId }.toSet() }
        }
        return resultado
    }

    /**
     * Títulos que no quedaron en ninguna fila del grupo.
     *
     * Hace falta de verdad: sin esto, un título cuya única categoría tiene un solo elemento
     * desaparece del catálogo, y el usuario no tiene forma de llegar a él.
     */
    fun uncategorized(shelves: List<VodShelf>, items: List<VodItem>): List<VodItem> {
        val yaVisibles = shelves.flatMap { shelf -> shelf.items.map { it.vodId } }.toSet()
        return items.filterNot { yaVisibles.contains(it.vodId) }
    }
}
