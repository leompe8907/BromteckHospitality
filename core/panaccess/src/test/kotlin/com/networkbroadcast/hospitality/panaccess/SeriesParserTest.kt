package com.networkbroadcast.hospitality.panaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesParserTest {

    private val sample = """
        {"success":true,"answer":{"id":10,"name":"Serie","isSeries":true,"seasons":[
          {"id":22,"seasonNumber":2,"name":"Segunda","episodes":[{"id":201,"name":"Dos uno","episodeNumber":1,"duration":1800}]},
          {"id":21,"seasonNumber":1,"name":"Primera","episodes":[
            {"id":102,"name":"Uno dos","episodeNumber":2,"duration":2700,"description":"<p>Hola &amp; chau</p>","image3Id":55},
            {"id":101,"name":"Uno uno","episodeNumber":1,"duration":0},
            {"name":"sin id","episodeNumber":3}]},
          {"id":23,"seasonNumber":3,"name":"Vacia","episodes":[]}
        ]}}
    """

    @Test
    fun `temporadas y episodios en orden, sin episodios sin id ni temporadas vacias`() {
        val seasons = parseSeasons(sample, "http://cdn")
        assertEquals(listOf(1, 2), seasons.map { it.number })
        assertEquals("Primera", seasons[0].title)
        assertEquals(listOf("101", "102"), seasons[0].episodes.map { it.id })
        assertEquals(listOf("201"), seasons[1].episodes.map { it.id })
    }

    @Test
    fun `campos del episodio`() {
        val e = parseSeasons(sample, "http://cdn")[0].episodes
        assertEquals("Hola & chau", e[1].synopsis)
        assertEquals(45, e[1].durationMinutes)
        assertEquals("http://cdn/public/images/55/v/vod_extra.jpg", e[1].imageUrl)
        assertNull(e[0].durationMinutes)
        assertNull(e[0].imageUrl)
        assertNull(parseSeasons(sample, null)[0].episodes[1].imageUrl)
    }

    @Test
    fun `sin temporadas pero con episodios es una sola temporada`() {
        val raw = """{"success":true,"answer":{"id":10,"episodes":[{"id":5,"name":"Solo","episodeNumber":1}]}}"""
        val seasons = parseSeasons(raw, null)
        assertEquals(1, seasons.size)
        assertEquals(1, seasons[0].number)
        assertEquals("5", seasons[0].episodes[0].id)
    }

    @Test
    fun `respuesta vacia o rota no inventa nada`() {
        assertTrue(parseSeasons("""{"success":true,"answer":{"id":10}}""", null).isEmpty())
        assertTrue(parseSeasons("""{"success":true,"answer":null}""", null).isEmpty())
        assertTrue(parseSeasons("no es json", null).isEmpty())
    }
}
