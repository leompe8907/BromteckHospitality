package com.networkbroadcast.hospitality.hotel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotelDataTest {

    @Test
    fun `resolve cae al idioma de respaldo y despues al ingles`() {
        val text: LocalizedText = mapOf("en" to "Pool", "es" to "Piscina")
        assertEquals("Piscina", text.resolve("es"))
        assertEquals("Piscina", text.resolve("hu"))
        assertEquals("Pool", text.resolve("hu", fallback = "de"))
        assertEquals("", emptyMap<String, String>().resolve("es"))
    }

    @Test
    fun `lee el formato de la rama RIU`() {
        val data = parseHotelData(
            """
            {"hotelInfo":{"name":{"es":"Resort"},"description":{"es":"Frente al mar"}},
             "images":[{"id":1,"title":{"es":"Piscina"},"description":{"es":"..."},
                        "url":"http://x/1.jpg","thumbnailUrl":"http://x/1.jpg"}]}
            """
        )
        assertEquals("Resort", data.hotelInfo.name.resolve("es"))
        assertEquals(1, data.images.size)
        assertEquals(0, data.videos.size)
    }

    @Test
    fun `el respaldo se usa cuando la fuente principal falla`() = runBlocking {
        val ok = HotelData(HotelInfo(mapOf("es" to "Local"), emptyMap()))
        val repo = FallbackHotelRepository(
            primary = object : HotelRepository { override suspend fun load(): HotelData? = null },
            fallback = object : HotelRepository { override suspend fun load(): HotelData? = ok },
        )
        assertEquals("Local", repo.load()?.hotelInfo?.name?.resolve("es"))
        assertNull(object : HotelRepository { override suspend fun load(): HotelData? = null }.load())
    }
}
