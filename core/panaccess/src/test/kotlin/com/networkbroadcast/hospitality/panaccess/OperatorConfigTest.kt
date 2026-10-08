package com.networkbroadcast.hospitality.panaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorConfigTest {

    private val sample = """
        {"success":true,"answer":{
          "subscriber":{"operator":"intv","firstName":"Ana","lastName":"Paz"},
          "device":{"parameters":{"X_HOTEL_SHOW_ABOUT":"true","X_HOTEL_APK_BASE_URL":"http://h/","X_HOTEL_APK_BASE_FOLDER":"HotelRoyal","X_DESIGN_SHOW_HOTEL_ROOM_NUMBER":"0"}},
          "epgCdnGroupId":"3",
          "cdnServers":[{"id":1,"urls":["http://otro"]},{"id":3,"urls":["http://epg.cdn/","http://epg2.cdn/"]}],
          "defaultTimeZone":"America/Recife"
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
    }

    @Test
    fun `una respuesta sin answer no rompe`() {
        val config = OperatorConfig.parse("""{"success":false}""")
        assertTrue(config.parameters.isEmpty())
        assertNull(config.operator)
        assertTrue(config.epgCdnUrls.isEmpty())
    }
}
