package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.brand.BrandConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeutralScreenTest {
    private val base = BrandConfig(id = "x", applicationId = "x.x", displayName = "X")

    @Test
    fun `una marca sin logo ni fondos es generica`() {
        assertFalse(base.hasHotelIdentity())
    }

    @Test
    fun `con un logo del panel, un logo de la marca o fondos la pantalla es del hotel`() {
        assertTrue(base.copy(showLogo = true).hasHotelIdentity())
        assertTrue(base.hasHotelIdentity(logoUrl = "https://ejemplo.com/logo.png"))
        assertTrue(base.copy(backgrounds = listOf("asset:fondos/1.jpg")).hasHotelIdentity())
    }

    @Test
    fun `los pasos estan traducidos en los cinco idiomas`() {
        val es = NeutralText.of("es")
        for (lang in listOf("en", "pt", "fr", "de")) {
            val t = NeutralText.of(lang)
            assertFalse(lang, t.step1 == es.step1 || t.step2 == es.step2 || t.title == es.title)
        }
        assertEquals(es, NeutralText.of("xx"))
    }
}
