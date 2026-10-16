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
    fun `X_APP_PLACEHOLDER_COLOR configurado se lee, sin el queda null (cae al gris fijo)`() {
        val conConfig = OperatorConfig.parse(
            """{"success":true,"answer":{"device":{"parameters":{"X_APP_PLACEHOLDER_COLOR":"#163F42"}}}}""",
        )
        assertEquals("#163F42", conConfig.placeholderColorHex)
        val sinConfig = OperatorConfig.parse("""{"success":true,"answer":{"device":{"parameters":{}}}}""")
        assertNull(sinConfig.placeholderColorHex)
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
}
