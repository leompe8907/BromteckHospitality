package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.panaccess.HomeWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLanguageTest {
    private val languages = listOf("es", "en", "pt", "fr", "de")

    @Test
    fun `usa el idioma que manda Panaccess`() {
        assertEquals("pt", accountLanguage("pt", "es", languages))
    }

    @Test
    fun `sin idioma de Panaccess usa el de la marca`() {
        assertEquals("es", accountLanguage(null, "es", languages))
        assertEquals("en", accountLanguage("  ", "en", languages))
    }

    @Test
    fun `un idioma que la marca no ofrece se ignora`() {
        assertEquals("es", accountLanguage("it", "es", languages))
    }

    @Test
    fun `acepta mayusculas y etiquetas con region`() {
        assertEquals("pt", accountLanguage("PT-BR", "es", languages))
        assertEquals("de", accountLanguage("de_AT", "es", languages))
    }

    @Test
    fun `la eleccion del huesped en la TV gana sobre la de Panaccess`() {
        assertEquals("fr", accountLanguage("pt", "es", languages, guestChoice = "fr"))
        assertEquals("pt", accountLanguage("pt", "es", languages, guestChoice = "it"))
    }

    @Test
    fun `datos de la ficha del widget como en la TV`() {
        val widget = HomeWidget("Bar", "Cocteles", "a.jpg", hours = "19 a 02", floor = "5", gallery = emptyList())
        assertEquals("19 a 02 · Piso 5", widgetMeta(widget, "Piso"))
        assertEquals("Piso 5", widgetMeta(widget.copy(hours = null), "Piso"))
        assertNull(widgetMeta(widget.copy(hours = null, floor = null), "Piso"))
        assertEquals(listOf("a.jpg"), widgetGallery(widget))
        assertEquals(listOf("a.jpg", "b.jpg"), widgetGallery(widget.copy(gallery = listOf("a.jpg", "b.jpg"))))
    }

    @Test
    fun `sin config del operador no hay widgets`() {
        assertTrue(accountWidgets(null, "es").isEmpty())
    }
}
