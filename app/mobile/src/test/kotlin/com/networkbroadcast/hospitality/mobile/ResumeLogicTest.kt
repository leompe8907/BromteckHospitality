package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.mobile.ResumePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeLogicTest {
    private val min = 60_000L
    private fun entry(id: String, at: Long, pos: Long = 600_000, title: String = "T $id") =
        ResumeEntry(id, title, null, null, pos, 100 * min, at)

    @Test
    fun `que hacer con la posicion`() {
        val d = 100 * min
        assertEquals(Action.Ignore, ResumePolicy.decide(5_000, d))            // recien empieza
        assertEquals(Action.Ignore, ResumePolicy.decide(20_000, 0))           // duracion desconocida
        assertEquals(Action.Save, ResumePolicy.decide(20_000, d))
        assertEquals(Action.Save, ResumePolicy.decide(50 * min, d))
        assertEquals(Action.Clear, ResumePolicy.decide(d - 60_000, d))        // faltan 60 s
        assertEquals(Action.Clear, ResumePolicy.decide((d * 0.96).toLong(), d))
        assertEquals(Action.Clear, ResumePolicy.decide(d, d))
    }

    @Test
    fun `una pelicula corta no se borra por empezar`() {
        // 3 min de duracion: a los 20 s faltan 160 s, no esta terminada.
        assertEquals(Action.Save, ResumePolicy.decide(20_000, 180_000))
    }

    @Test
    fun `retomar vuelve unos segundos atras sin pasar de cero`() {
        assertEquals(597_000L, ResumePolicy.startPosition(entry("a", 1)))
        assertEquals(0L, ResumePolicy.startPosition(entry("a", 1, pos = 1_000)))
    }

    @Test
    fun `lo ultimo visto va primero y no se repite`() {
        val list = listOf(entry("a", 1), entry("b", 2)).upserted(entry("a", 3, pos = 900_000))
        assertEquals(listOf("a", "b"), list.map { it.vodId })
        assertEquals(900_000L, list[0].positionMs)
    }

    @Test
    fun `la lista tiene tope`() {
        var list = emptyList<ResumeEntry>()
        (1..25).forEach { list = list.upserted(entry("v$it", it.toLong())) }
        assertEquals(ResumePolicy.MAX_ENTRIES, list.size)
        assertEquals("v25", list.first().vodId)
        assertEquals("v6", list.last().vodId)
    }

    @Test
    fun `borrar una entrada`() {
        val list = listOf(entry("a", 1), entry("b", 2)).without("a")
        assertEquals(listOf("b"), list.map { it.vodId })
    }

    @Test
    fun `guardar y leer conserva todo, incluso tabs, saltos y acentos`() {
        val original = listOf(
            ResumeEntry("7", "Ñandú\ty\nmás", "T1 · E2", "http://x/p.jpg?a=1&b=2", 123_000, 5_400_000, 99),
            ResumeEntry("8", "Otra", null, null, 1_000, 2_000, 50),
        )
        val back = decodeResume(original.encode())
        assertEquals(original, back)
        assertNull(back[1].subtitle)
    }

    @Test
    fun `lineas rotas se saltean`() {
        val raw = entry("a", 5).let { listOf(it).encode() } + "\nbasura\n\t\t\t\t\t\t"
        assertEquals(listOf("a"), decodeResume(raw).map { it.vodId })
        assertEquals(emptyList<ResumeEntry>(), decodeResume(null))
    }

    @Test
    fun `ver un rato guarda, terminar borra, empezar no toca`() {
        val d = 100 * min
        val start = emptyList<ResumeEntry>()
        val saved = start.afterProgress("a", "A", "T1 · E1", "p", 30 * min, d, 10)
        assertEquals(listOf("a"), saved.map { it.vodId })
        assertEquals("T1 · E1", saved[0].subtitle)
        assertEquals(saved, saved.afterProgress("a", "A", null, null, 2_000, d, 20))
        assertEquals(emptyList<ResumeEntry>(), saved.afterProgress("a", "A", null, null, d - 30_000, d, 30))
    }
}
