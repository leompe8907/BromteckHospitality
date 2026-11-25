package com.networkbroadcast.hospitality.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutTest {
    private val S = TileSize.S
    private val M = TileSize.M
    private val W = TileSize.W

    @Test
    fun `el orden por defecto del mockup coloca W arriba, dos M al lado y los chicos debajo`() {
        // remote W, live M, vod M, wifi S, weather S, services S, settings S (el de CONFIG.home del mockup)
        val p = placeTiles(listOf(W, M, M, S, S, S, S))
        assertEquals(Placement(0, 0, W), p[0])
        assertEquals(Placement(0, 2, M), p[1])
        assertEquals(Placement(2, 2, M), p[2])
        assertEquals(listOf(Placement(0, 4, S), Placement(1, 4, S), Placement(2, 4, S), Placement(3, 4, S)), p.drop(3))
        assertEquals(5, gridRows(p))
    }

    @Test
    fun `un tile chico rellena el hueco que dejo uno mediano (dense)`() {
        // M deja un hueco de 2 columnas a su derecha; el W no entra ahi y salta, los S lo rellenan.
        val p = placeTiles(listOf(M, W, S, S))
        assertEquals(Placement(0, 0, M), p[0])
        assertEquals(Placement(0, 2, W), p[1])
        assertEquals(Placement(2, 0, S), p[2])
        assertEquals(Placement(3, 0, S), p[3])
    }

    @Test
    fun `sin tiles no hay filas`() {
        assertEquals(0, gridRows(placeTiles(emptyList())))
    }

    @Test
    fun `caida de tamano como el mockup`() {
        assertEquals(S, fitSize(TileIds.SETTINGS, W))
        assertEquals(S, fitSize(TileIds.SETTINGS, M))
        assertEquals(W, fitSize(TileIds.LIVE, W))
    }

    @Test
    fun `lee el parametro, ignora lo invalido y usa S si falta el tamano`() {
        val raw = """[{"id":"Live","size":"m"},{"id":"vod"},{"size":"W"},5,{"id":"wifi","size":"XL"}]"""
        assertEquals(listOf(TileSpec("live", M), TileSpec("vod", S), TileSpec("wifi", S)), parseTileConfig(raw))
        assertNull(parseTileConfig(null))
        assertNull(parseTileConfig("no es json"))
        assertNull(parseTileConfig("[]"))
        assertNull(parseTileConfig("""{"id":"live"}"""))
    }

    @Test
    fun `solo se dibuja lo disponible, sin repetidos, en el orden de la config`() {
        val config = listOf(TileSpec("remote", W), TileSpec("live", M), TileSpec("wifi", S), TileSpec("live", S), TileSpec("weather", S))
        val tiles = resolveHomeTiles(config, available = setOf("live", "weather"), widgetCount = 0)
        assertEquals(listOf(HomeTile.Builtin("live", M), HomeTile.Builtin("weather", S)), tiles)
    }

    @Test
    fun `sin widgets en la cuenta no hay tiles de widgets`() {
        val tiles = resolveHomeTiles(DEFAULT_HOME_TILES, available = setOf(TileIds.WIDGETS), widgetCount = 0)
        assertEquals(emptyList<HomeTile>(), tiles)
    }

    @Test
    fun `los widgets M hacen un mosaico de ancho, mediano, mediano sin dejar huecos`() {
        assertEquals(listOf(W, M, M, W, M, M), widgetSizes(M, 6))
        assertEquals(listOf(W, M, M, W), widgetSizes(M, 4))
        assertEquals(listOf(W, M, M, M, M), widgetSizes(M, 5))
        assertEquals(listOf(W, M, M, W, M, M, W), widgetSizes(M, 7))
        assertEquals(listOf(W, W, W), widgetSizes(W, 3))
        assertEquals(listOf(S, S), widgetSizes(S, 2))
        // Sin huecos: cada fila de la grilla queda llena.
        for (n in 1..9) {
            val placed = placeTiles(widgetSizes(M, n))
            val area = widgetSizes(M, n).sumOf { it.cols * it.rows }
            assertEquals("n=$n", gridRows(placed) * 4, area)
        }
    }

    @Test
    fun `los widgets entran como M de a dos y con cantidad impar el primero va ancho`() {
        val config = listOf(TileSpec("widgets", M))
        val avail = setOf("widgets")
        assertEquals(
            listOf(HomeTile.Widget(0, M), HomeTile.Widget(1, M)),
            resolveHomeTiles(config, avail, 2),
        )
        assertEquals(
            listOf(HomeTile.Widget(0, W), HomeTile.Widget(1, M), HomeTile.Widget(2, M)),
            resolveHomeTiles(config, avail, 3),
        )
        assertEquals(listOf(HomeTile.Widget(0, W)), resolveHomeTiles(config, avail, 1))
        assertEquals(listOf(HomeTile.Widget(0, W), HomeTile.Widget(1, W)), resolveHomeTiles(listOf(TileSpec("widgets", W)), avail, 2))
    }

    @Test
    fun `por defecto remote y settings no se dibujan aunque esten en la lista`() {
        val tiles = resolveHomeTiles(DEFAULT_HOME_TILES, available = setOf("live", "vod", "wifi"), widgetCount = 0)
        assertEquals(listOf(HomeTile.Builtin("live", M), HomeTile.Builtin("vod", M), HomeTile.Builtin("wifi", S)), tiles)
    }
}
