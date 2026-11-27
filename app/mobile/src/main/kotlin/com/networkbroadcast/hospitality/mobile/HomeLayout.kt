package com.networkbroadcast.hospitality.mobile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parámetro de Panaccess con el orden y el tamaño de los tiles de la home del celular.
 *
 * NOMBRE PROPUESTO: este parámetro todavía NO existe en Panaccess; hay que crearlo (igual que se
 * hizo con X_APP_LOGO). Se lee con `operatorConfig.param(...)`. Si no existe, o no es un JSON
 * válido, se usa [DEFAULT_HOME_TILES]. El valor es un array JSON como el `CONFIG.home` del mockup
 * (docs/mockup-celular/index.html):
 * `[{"id":"live","size":"M"},{"id":"vod","size":"M"},{"id":"widgets","size":"M"},{"id":"wifi","size":"S"}]`
 */
const val MOBILE_TILES_PARAM = "X_APP_MOBILE_TILES"

/** Tamaño de un tile en la grilla de 4 columnas: S icono 1x1, M widget 2x2, W widget ancho 4x2. */
enum class TileSize(val cols: Int, val rows: Int) { S(1, 1), M(2, 2), W(4, 2) }

/** Un tile de la configuración: qué es y de qué tamaño. */
data class TileSpec(val id: String, val size: TileSize)

/** Ids de tile que el celular sabe dibujar. `widgets` se expande a los widgets de la cuenta (X_APP_WIDGETS). */
object TileIds {
    const val LIVE = "live"
    const val VOD = "vod"
    const val WIFI = "wifi"
    const val WEATHER = "weather"
    const val SERVICES = "services"
    const val HOTEL = "hotel"
    const val WIDGETS = "widgets"
    // El control remoto siempre está: si se está en la app del celular, hay vinculación con la TV.
    const val REMOTE = "remote"
    // Existe en el mockup pero hoy no se dibuja: no hay pantalla de ajustes.
    const val SETTINGS = "settings"
}

/**
 * Orden por defecto: el de `CONFIG.brands.generic.home` del mockup (remote W, live M, vod M, wifi S,
 * weather S, services S, settings S) con `widgets` después de las películas, y `hotel` (que el mockup
 * tiene como id pero fuera de su orden por defecto) al final de los chicos. Lo que la cuenta o la
 * marca no tiene no se dibuja (ver [resolveHomeTiles]).
 */
val DEFAULT_HOME_TILES: List<TileSpec> = listOf(
    TileSpec(TileIds.REMOTE, TileSize.W),
    TileSpec(TileIds.LIVE, TileSize.M),
    TileSpec(TileIds.VOD, TileSize.M),
    TileSpec(TileIds.WIDGETS, TileSize.M),
    TileSpec(TileIds.WIFI, TileSize.S),
    TileSpec(TileIds.WEATHER, TileSize.S),
    TileSpec(TileIds.SERVICES, TileSize.S),
    TileSpec(TileIds.HOTEL, TileSize.S),
    TileSpec(TileIds.SETTINGS, TileSize.S),
)

/** Los tamaños que admite cada tile (como `TILES[x].sizes` del mockup): todos, salvo ajustes (solo S). */
private fun allowedSizes(id: String): List<TileSize> =
    if (id == TileIds.SETTINGS) listOf(TileSize.S) else TileSize.entries

/**
 * El tile pedido en [requested], o el más grande que ese tile admita sin pasarse (regla de caída del
 * mockup: `[...sizes].reverse().find(s => idx(s) <= want) || sizes[0]`).
 */
fun fitSize(id: String, requested: TileSize): TileSize {
    val allowed = allowedSizes(id)
    if (requested in allowed) return requested
    return allowed.reversed().firstOrNull { it.ordinal <= requested.ordinal } ?: allowed.first()
}

/**
 * Lee el parámetro [MOBILE_TILES_PARAM]. null si no existe o no es un array JSON utilizable (entonces
 * va el orden por defecto). Un elemento sin `id`, o que no es un objeto, se descarta; un `size`
 * desconocido o ausente se toma como S.
 */
fun parseTileConfig(raw: String?): List<TileSpec>? {
    if (raw.isNullOrBlank()) return null
    val array = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray ?: return null
    val tiles = array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = (obj["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val size = (obj["size"] as? JsonPrimitive)?.content?.trim()?.uppercase()
            ?.let { s -> TileSize.entries.firstOrNull { it.name == s } } ?: TileSize.S
        TileSpec(id, size)
    }
    return tiles.takeIf { it.isNotEmpty() }
}

/** Lo que se dibuja en la grilla: un tile de la app o un widget de la cuenta (por su posición en X_APP_WIDGETS). */
sealed interface HomeTile {
    val size: TileSize
    data class Builtin(val id: String, override val size: TileSize) : HomeTile
    data class Widget(val index: Int, override val size: TileSize) : HomeTile
}

/**
 * Tamaño de cada uno de los [count] widgets de la cuenta cuando el parámetro pide [size]:
 *  - W: todos anchos; S: todos chicos;
 *  - M (el defecto): un mosaico que repite ancho, mediano, mediano (W, M, M, W, M, M…) para que no queden
 *    todos iguales. Lo que sobra al final no deja huecos: uno solo va ancho y dos van medianos.
 */
fun widgetSizes(size: TileSize, count: Int): List<TileSize> {
    if (size != TileSize.M) return List(count) { size }
    return List(count) { i ->
        when {
            i % 3 == 0 && count - i >= 3 -> TileSize.W
            i % 3 == 0 && count - i == 1 -> TileSize.W
            else -> TileSize.M
        }
    }
}

/**
 * La lista final de tiles: [config] en su orden, sin repetidos, solo los ids de [available] (los que la
 * marca tiene prendidos y la cuenta tiene datos), con la caída de tamaño del mockup. El id `widgets`
 * se expande a los [widgetCount] widgets de la cuenta, en el orden de Panaccess, con los tamaños de
 * [widgetSizes].
 */
fun resolveHomeTiles(config: List<TileSpec>, available: Set<String>, widgetCount: Int): List<HomeTile> {
    val seen = HashSet<String>()
    val out = ArrayList<HomeTile>()
    for (spec in config) {
        if (spec.id !in available || !seen.add(spec.id)) continue
        if (spec.id == TileIds.WIDGETS) {
            val sizes = widgetSizes(spec.size, widgetCount)
            for (i in 0 until widgetCount) out += HomeTile.Widget(i, sizes[i])
        } else {
            out += HomeTile.Builtin(spec.id, fitSize(spec.id, spec.size))
        }
    }
    return out
}

/** Dónde queda un tile en la grilla (columna y fila, desde 0). */
data class Placement(val col: Int, val row: Int, val size: TileSize)

/**
 * Coloca los tiles como `grid-auto-flow: row dense` del mockup, en [columns] columnas: cada tile
 * toma el primer lugar libre desde el principio (así un tile chico rellena un hueco que dejó uno
 * grande), sin cambiar el orden en que se declararon.
 */
fun placeTiles(sizes: List<TileSize>, columns: Int = 4): List<Placement> {
    val taken = ArrayList<BooleanArray>()
    fun free(col: Int, row: Int, size: TileSize): Boolean {
        for (r in row until row + size.rows) {
            if (r >= taken.size) continue
            for (c in col until col + size.cols) if (taken[r][c]) return false
        }
        return true
    }
    return sizes.map { size ->
        var row = 0
        var placed: Placement? = null
        while (placed == null) {
            for (col in 0..columns - size.cols) {
                if (free(col, row, size)) { placed = Placement(col, row, size); break }
            }
            if (placed == null) row++
        }
        while (taken.size < placed.row + size.rows) taken += BooleanArray(columns)
        for (r in placed.row until placed.row + size.rows) for (c in placed.col until placed.col + size.cols) taken[r][c] = true
        placed
    }
}

/** Filas que ocupa la grilla ya colocada. */
fun gridRows(placements: List<Placement>): Int = placements.maxOfOrNull { it.row + it.size.rows } ?: 0
