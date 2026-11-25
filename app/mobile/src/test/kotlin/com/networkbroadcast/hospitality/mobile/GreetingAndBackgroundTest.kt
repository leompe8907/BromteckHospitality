package com.networkbroadcast.hospitality.mobile

import androidx.compose.ui.graphics.Color
import com.networkbroadcast.hospitality.brand.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GreetingAndBackgroundTest {
    private val es = UiText.of("es")

    @Test
    fun `el saludo cambia con la hora como en la TV`() {
        assertEquals("Buenas noches", greetingFor(es, 4, null))
        assertEquals("Buenos días", greetingFor(es, 5, null))
        assertEquals("Buenos días", greetingFor(es, 11, null))
        assertEquals("Buenas tardes", greetingFor(es, 12, null))
        assertEquals("Buenas tardes", greetingFor(es, 19, null))
        assertEquals("Buenas noches", greetingFor(es, 20, null))
        assertEquals("Buenas noches", greetingFor(es, 0, null))
    }

    @Test
    fun `con nombre se agrega despues de una coma y un nombre vacio se ignora`() {
        assertEquals("Buenos días, Mario Bromteck", greetingFor(es, 9, " Mario Bromteck "))
        assertEquals("Buenos días", greetingFor(es, 9, "  "))
    }

    @Test
    fun `el fondo del celular es una foto, un color o nada`() {
        assertNull(parseMobileBackground(null))
        assertNull(parseMobileBackground("   "))
        assertEquals(MobileBackground.Photo("https://ejemplo.com/fondo.jpg"), parseMobileBackground(" https://ejemplo.com/fondo.jpg "))
        assertEquals(MobileBackground.Solid(Color(0xFF0E2E32)), parseMobileBackground("#0E2E32"))
        assertNull(parseMobileBackground("#zzz"))
    }
}
