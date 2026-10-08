package com.networkbroadcast.hospitality.panaccess

import org.junit.Assert.assertEquals
import org.junit.Test

class PanaccessClientTest {

    @Test
    fun `md5 en hex minuscula con ceros a la izquierda, como Utils md5 del base`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", PanaccessClient.md5(""))
        assertEquals("098f6bcd4621d373cade4e832627b4f6", PanaccessClient.md5("test"))
    }
}
