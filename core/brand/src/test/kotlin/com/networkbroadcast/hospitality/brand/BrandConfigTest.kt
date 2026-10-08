package com.networkbroadcast.hospitality.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandConfigTest {

    @Test
    fun `un brand json minimo toma los valores por defecto`() {
        val brand = BrandLoader.parse(
            """{"id":"x","applicationId":"com.example.x","displayName":"X"}"""
        )
        assertEquals("RESORT", brand.direction)
        assertTrue(brand.features.liveTv)
        assertFalse(brand.features.roomService)
        assertEquals("hotel_data.json", brand.hotel.asset)
    }

    @Test
    fun `los flags y claves desconocidas no rompen la lectura`() {
        val brand = BrandLoader.parse(
            """
            {"id":"x","applicationId":"com.example.x","displayName":"X",
             "features":{"vod":false,"futuraFuncion":true},"otraClave":1}
            """
        )
        assertFalse(brand.features.vod)
    }
}
