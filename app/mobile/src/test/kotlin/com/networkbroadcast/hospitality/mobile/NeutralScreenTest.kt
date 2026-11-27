package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.brand.BrandConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeutralScreenTest {
    private val base = BrandConfig(id = "x", applicationId = "x.x", displayName = "X")

    @Test
    fun `el logo sale del panel o de la marca, y si no hay ninguno no hay logo`() {
        assertNull(neutralLogo(base, null))
        assertEquals("asset:logo.png", neutralLogo(base.copy(showLogo = true), null))
        assertEquals("https://ejemplo.com/logo.png", neutralLogo(base.copy(showLogo = true), "https://ejemplo.com/logo.png"))
    }

    @Test
    fun `el codigo sale del enlace del QR o de los 6 digitos solos`() {
        assertEquals("482915", pairingCodeFrom("hospitality://pair?code=482915"))
        assertEquals("482915", pairingCodeFrom("https://ejemplo.com/p?room=101&code=482915&x=1"))
        assertEquals("482915", pairingCodeFrom(" 482 915 "))
        assertNull(pairingCodeFrom("hospitality://pair?code=4829"))
        assertNull(pairingCodeFrom("https://ejemplo.com"))
        assertNull(pairingCodeFrom(null))
    }

    @Test
    fun `el QR de adorno tiene las tres esquinas de un QR`() {
        val m = qrModules()
        assertEquals(25, m.size)
        // Esquina de arriba a la izquierda: borde lleno, anillo vacío, centro lleno.
        assertTrue(m[0][0] && m[0][6] && m[6][0] && m[3][3])
        assertFalse(m[1][1] || m[7][7])
        assertTrue(m[0][24] && m[24][0])
    }

    @Test
    fun `los textos estan traducidos en los cinco idiomas`() {
        val es = NeutralText.of("es")
        for (lang in listOf("en", "pt", "fr", "de")) {
            val t = NeutralText.of(lang)
            // "Escanear código QR" se escribe igual en portugués: se comparan textos que cambian en todos.
            assertFalse(lang, t.title == es.title || t.codeHint == es.codeHint || t.invalidCode == es.invalidCode)
        }
        assertEquals(es, NeutralText.of("xx"))
    }
}
