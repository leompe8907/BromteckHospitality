package com.networkbroadcast.hospitality.panaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorConfigTest {

    private val sample = """
        {"success":true,"answer":{
          "subscriber":{"operator":"intv","firstName":"Ana","lastName":"Paz"},
          "device":{"parameters":{"X_HOTEL_SHOW_ABOUT":"true","X_HOTEL_APK_BASE_URL":"http://h/","X_HOTEL_APK_BASE_FOLDER":"HotelRoyal","X_DESIGN_SHOW_HOTEL_ROOM_NUMBER":"0","X_APP_LOGO_BACKGROUND":"https://i.imgur.com/jpFevda.jpeg","X_APP_LOGO":"https://i.imgur.com/logo.png"}},
          "epgCdnGroupId":"3",
          "cdnServers":[{"id":1,"urls":["http://otro"]},{"id":3,"urls":["http://epg.cdn/","http://epg2.cdn/"]}],
          "defaultTimeZone":"America/Recife",
          "weather":{"provider":"OPENWEATHER","data":"{\"list\":[{\"main\":{\"temp\":302.15},\"weather\":[{\"description\":\"cielo claro\"}]}]}"}
        }}
    """

    @Test
    fun `lee parametros X, operador y CDN de la guia como el ClientConfigService del base`() {
        val config = OperatorConfig.parse(sample)
        assertEquals("intv", config.operator)
        assertEquals("Ana Paz", config.subscriberName)
        assertEquals(listOf("http://epg.cdn/", "http://epg2.cdn/"), config.epgCdnUrls)
        assertEquals("America/Recife", config.defaultTimeZone)
        assertTrue(config.hotelShowAbout == true)
        assertEquals("HotelRoyal", config.hotelBaseFolder)
        assertEquals(false, config.showRoomNumber)
        assertEquals("https://i.imgur.com/jpFevda.jpeg", config.hotelBackgroundUrl)
        assertEquals("https://i.imgur.com/logo.png", config.hotelLogoUrl)
        assertEquals("29° · cielo claro", config.weatherText)
        assertTrue(config.hasWeatherField)
    }

    @Test
    fun `sin dato de clima en la respuesta, weatherText queda null`() {
        val config = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}}}}""")
        assertNull(config.weatherText)
        assertTrue(!config.hasWeatherField)
    }

    @Test
    fun `con weather null (hotel sin clima configurado en Panaccess) no rompe y queda sin dato`() {
        // Así lo manda Panaccess cuando no hay clima configurado para la cuenta: la clave
        // "weather" viene igual, pero con valor JSON null, no ausente.
        val config = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}},"weather":null}}""")
        assertNull(config.weatherText)
        assertTrue(!config.hasWeatherField)
    }

    @Test
    fun `con X_SHOW_WEATHER en false se oculta aunque el dato venga`() {
        val config = OperatorConfig.parse(
            sample.replace("\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"", "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\",\"X_SHOW_WEATHER\":\"false\""),
        )
        assertNull(config.weatherText)
    }

    @Test
    fun `con SHOW_WEATHER_DATA en false (nombre visto en una cuenta real, sin prefijo X_) tambien se oculta`() {
        val config = OperatorConfig.parse(
            sample.replace("\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"", "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\",\"SHOW_WEATHER_DATA\":\"false\""),
        )
        assertNull(config.weatherText)
    }

    @Test
    fun `una respuesta sin answer no rompe`() {
        val config = OperatorConfig.parse("""{"success":false}""")
        assertTrue(config.parameters.isEmpty())
        assertNull(config.operator)
        assertTrue(config.epgCdnUrls.isEmpty())
    }

    @Test
    fun `sin X_APP_LOGO_BACKGROUND el fondo queda null (cae al generico de la marca)`() {
        val config = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}}}}""")
        assertNull(config.hotelBackgroundUrl)
    }

    @Test
    fun `X_APP_PLACEHOLDER_IMAGE configurado se lee, sin el queda null (cae al gris fijo)`() {
        val conConfig = OperatorConfig.parse(
            """{"success":true,"answer":{"device":{"parameters":{"X_APP_PLACEHOLDER_IMAGE":"https://i.imgur.com/placeholder.jpg"}}}}""",
        )
        assertEquals("https://i.imgur.com/placeholder.jpg", conConfig.placeholderImageUrl)
        val sinConfig = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}}}}""")
        assertNull(sinConfig.placeholderImageUrl)
    }

    @Test
    fun `X_APP_SHOW_WEATHER manda sobre los flags viejos (declarativo, sea cual sea el otro valor)`() {
        val oculto = OperatorConfig.parse(
            sample.replace(
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"",
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\",\"X_APP_SHOW_WEATHER\":\"false\",\"SHOW_WEATHER_DATA\":\"true\"",
            ),
        )
        assertNull(oculto.weatherText)
        val mostrado = OperatorConfig.parse(
            sample.replace(
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"",
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\",\"X_APP_SHOW_WEATHER\":\"true\",\"SHOW_WEATHER_DATA\":\"false\"",
            ),
        )
        assertEquals("29° · cielo claro", mostrado.weatherText)
    }

    @Test
    fun `widgets lee un JSON por slot (titulo, descripcion, horario, piso, imagenes), hasta 4`() {
        val config = OperatorConfig.parse(
            sample.replace(
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"",
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"," +
                    "\"X_APP_WIDGET_1\":\"{\\\"title\\\":\\\"Bar Valentina\\\",\\\"description\\\":\\\"Música en vivo los viernes\\\"," +
                    "\\\"hours\\\":\\\"Abierto de 19 a 02am\\\",\\\"floor\\\":\\\"5\\\"," +
                    "\\\"images\\\":[\\\"https://i.imgur.com/bar1.jpg\\\",\\\"https://i.imgur.com/bar2.jpg\\\",\\\"https://i.imgur.com/bar3.jpg\\\"]}\"," +
                    // Slot 2: JSON mal formado -- se descarta entero, no rompe el resto.
                    "\"X_APP_WIDGET_2\":\"{not valid json\"," +
                    // Slot 3: sin "images" -- se descarta, aunque tenga título.
                    "\"X_APP_WIDGET_3\":\"{\\\"title\\\":\\\"Sin fotos\\\"}\"," +
                    // Slot 4: mínimo válido, solo título + 1 imagen.
                    "\"X_APP_WIDGET_4\":\"{\\\"title\\\":\\\"Piscina\\\",\\\"images\\\":[\\\"https://i.imgur.com/pool.jpg\\\"]}\"",
            ),
        )

        val widgets = config.widgets("es")
        assertEquals(2, widgets.size)
        val bar = widgets[0]
        assertEquals("Bar Valentina", bar.title)
        assertEquals("Música en vivo los viernes", bar.description)
        assertEquals("Abierto de 19 a 02am", bar.hours)
        assertEquals("5", bar.floor)
        assertEquals(
            listOf("https://i.imgur.com/bar1.jpg", "https://i.imgur.com/bar2.jpg", "https://i.imgur.com/bar3.jpg"),
            bar.gallery,
        )

        val pool = widgets[1]
        assertEquals("Piscina", pool.title)
        assertNull(pool.description)
        assertNull(pool.hours)
        assertNull(pool.floor)
        assertEquals(listOf("https://i.imgur.com/pool.jpg"), pool.gallery)
    }

    @Test
    fun `titulo y descripcion por idioma -- resuelve al idioma pedido, cae a español y despues ingles`() {
        val config = OperatorConfig.parse(
            sample.replace(
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"",
                "\"X_DESIGN_SHOW_HOTEL_ROOM_NUMBER\":\"0\"," +
                    "\"X_APP_WIDGET_1\":\"{\\\"title\\\":{\\\"es\\\":\\\"Bar Valentina\\\",\\\"en\\\":\\\"Valentina Bar\\\"}," +
                    "\\\"description\\\":{\\\"es\\\":\\\"Música en vivo\\\"}," +
                    "\\\"images\\\":[\\\"https://i.imgur.com/bar1.jpg\\\"]}\"," +
                    // Slot 2: título como texto simple (sin por-idioma) -- vale para cualquier idioma.
                    "\"X_APP_WIDGET_2\":\"{\\\"title\\\":\\\"Gimnasio\\\",\\\"images\\\":[\\\"https://i.imgur.com/gym.jpg\\\"]}\"",
            ),
        )

        val en = config.widgets("en")
        assertEquals("Valentina Bar", en[0].title)
        // Sin "en" en la descripción: cae a español, no queda vacía.
        assertEquals("Música en vivo", en[0].description)
        assertEquals("Gimnasio", en[1].title)

        // Idioma sin traducir ("pt"): cae a español (primer fallback), nunca a null/vacío.
        val pt = config.widgets("pt")
        assertEquals("Bar Valentina", pt[0].title)
    }

    @Test
    fun `sin ningun X_APP_WIDGET configurado, widgets queda vacio`() {
        val config = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}}}}""")
        assertTrue(config.widgets("es").isEmpty())
    }
}
