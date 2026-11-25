package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelGroup
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLogicTest {
    private val a = Channel("1", 1, "A")
    private val b = Channel("2", 2, "B")
    private val c = Channel("3", 3, "C")

    @Test
    fun `sin bouquets una sola fila con todos`() {
        val rows = ChannelsResult(listOf(a, b)).toRows("Todos")
        assertEquals(listOf(ChannelRow("Todos", listOf(a, b))), rows)
    }

    @Test
    fun `sin canales no hay filas`() {
        assertTrue(ChannelsResult(emptyList()).toRows("Todos").isEmpty())
    }

    @Test
    fun `bouquets en el orden del operador, sin ids desconocidos ni filas vacias`() {
        val result = ChannelsResult(
            channels = listOf(a, b, c),
            groups = listOf(
                ChannelGroup("Noticias", listOf("3", "9", "1")),
                ChannelGroup("Vacio", listOf("9")),
                ChannelGroup("Cine", listOf("2")),
            ),
        )
        val rows = result.toRows("Todos")
        assertEquals(listOf("Noticias", "Cine"), rows.map { it.title })
        assertEquals(listOf(c, a), rows[0].channels)
    }

    @Test
    fun `progreso del programa`() {
        val p = Program("P", 100, 200)
        assertEquals(0f, p.progress(50), 0f)
        assertEquals(0.5f, p.progress(150), 0.001f)
        assertEquals(1f, p.progress(500), 0f)
        assertEquals(0f, Program("Z", 10, 10).progress(10), 0f)
    }

    @Test
    fun `la tira de la guia empieza en lo que esta al aire`() {
        val schedule = listOf(Program("viejo", 0, 100), Program("ahora", 100, 200), Program("luego", 200, 300), Program("despues", 300, 400))
        assertEquals(listOf("ahora", "luego"), schedule.upcoming(150, limit = 2).map { it.title })
    }

    @Test
    fun `reloj de reproduccion`() {
        assertEquals("0:00", formatClock(-5))
        assertEquals("5:09", formatClock(309_000))
        assertEquals("1:05:09", formatClock(3_909_000))
    }

    @Test
    fun `etiqueta de episodio en cada idioma`() {
        assertEquals("T1 · E2", ContentText.of("es").episodeLabel(1, 2))
        assertEquals("S3 · E10", ContentText.of("xx").episodeLabel(3, 10))
        assertEquals("E4", ContentText.of("pt").episodeLabel(0, 4))
        assertEquals("", ContentText.of("fr").episodeLabel(0, 0))
        assertEquals("Staffel 2", ContentText.of("de").seasonLabel(2))
    }

    @Test
    fun `solo las pestanas que la marca tiene`() {
        val all = com.networkbroadcast.hospitality.brand.Features(liveTv = true, vod = true, guide = true)
        assertEquals(listOf(ContentTab.Live, ContentTab.Guide, ContentTab.Vod), contentTabs(all))
        assertEquals(listOf(ContentTab.Live, ContentTab.Guide, ContentTab.Catchup, ContentTab.Vod), contentTabs(all, hasCatchup = true))
        val noVod = com.networkbroadcast.hospitality.brand.Features(liveTv = true, vod = false, guide = false)
        assertEquals(listOf(ContentTab.Live), contentTabs(noVod, hasCatchup = true))
    }
}
