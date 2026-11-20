package com.networkbroadcast.hospitality.hotel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** X_APP_WIFI: lo que escribe el operador en Panaccess y qué red sale de ahí. */
class OperatorWifiTest {

    @Test
    fun `red con clave completa`() {
        val net = parseOperatorWifi("""{"ssid":"HotelGuest","password":"abc12345x","security":"WPA","hidden":false}""")
        assertEquals(WifiNetwork("HotelGuest", "abc12345x", WifiSecurity.WPA, false), net)
        assertEquals("WIFI:T:WPA;S:HotelGuest;P:abc12345x;;", net?.qrPayload)
    }

    @Test
    fun `solo ssid y clave alcanzan, el resto toma el valor por defecto`() {
        assertEquals(
            WifiNetwork("HotelGuest", "abc12345", WifiSecurity.WPA, false),
            parseOperatorWifi("""{"ssid":"HotelGuest","password":"abc12345"}"""),
        )
    }

    @Test
    fun `red abierta ignora la clave y no lleva P en el qr`() {
        val net = parseOperatorWifi("""{"ssid":"Libre","password":"no-se-usa","security":"nopass"}""")
        assertEquals(WifiNetwork("Libre", null, WifiSecurity.OPEN, false), net)
        assertEquals("WIFI:T:nopass;S:Libre;;", net?.qrPayload)
    }

    @Test
    fun `red oculta, como booleano o como texto`() {
        assertEquals(true, parseOperatorWifi("""{"ssid":"R","password":"abc12345","hidden":true}""")?.hidden)
        assertEquals(true, parseOperatorWifi("""{"ssid":"R","password":"abc12345","hidden":"true"}""")?.hidden)
        assertEquals(true, parseOperatorWifi("""{"ssid":"R","password":"abc12345","hidden":"1"}""")?.hidden)
        assertEquals(false, parseOperatorWifi("""{"ssid":"R","password":"abc12345","hidden":"no"}""")?.hidden)
        assertEquals("WIFI:T:WPA;S:R;P:abc12345x;H:true;;", parseOperatorWifi("""{"ssid":"R","password":"abc12345x","hidden":true}""")?.qrPayload)
    }

    @Test
    fun `tipo de seguridad desconocido se trata como WPA y se acepta en minusculas`() {
        assertEquals(WifiSecurity.WPA, parseOperatorWifi("""{"ssid":"R","password":"abc12345","security":"WPA3"}""")?.security)
        assertEquals(WifiSecurity.WEP, parseOperatorWifi("""{"ssid":"R","password":"hello","security":" wep "}""")?.security)
    }

    @Test
    fun `numeros en ssid o clave se toman como texto`() {
        val net = parseOperatorWifi("""{"ssid":"Hotel","password":12345678}""")
        assertEquals("12345678", net?.password)
        // Solo dígitos: hexadecimal válido, va entre comillas en el QR.
        assertEquals("WIFI:T:WPA;S:Hotel;P:\"12345678\";;", net?.qrPayload)
    }

    @Test
    fun `la clave se respeta tal cual, con caracteres especiales escapados en el qr`() {
        val net = parseOperatorWifi("""{"ssid":"Bar;Pileta","password":" a,b:c\"d\\e "}""")
        assertEquals(" a,b:c\"d\\e ", net?.password)
        assertEquals("WIFI:T:WPA;S:Bar\\;Pileta;P: a\\,b\\:c\\\"d\\\\e ;;", net?.qrPayload)
    }

    @Test
    fun `sin red que sirva devuelve null`() {
        assertNull(parseOperatorWifi(null))
        assertNull(parseOperatorWifi(""))
        assertNull(parseOperatorWifi("   "))
        assertNull(parseOperatorWifi("""{"password":"abc12345"}"""))
        assertNull(parseOperatorWifi("""{"ssid":"   ","password":"abc12345"}"""))
        // Red con clave sin la clave: el QR no conectaría a nadie.
        assertNull(parseOperatorWifi("""{"ssid":"Red"}"""))
        assertNull(parseOperatorWifi("""{"ssid":"Red","password":"   "}"""))
        assertNull(parseOperatorWifi("""{"ssid":"Red","password":null}"""))
    }

    @Test
    fun `un valor que no es un objeto JSON valido no rompe, no hay red`() {
        for (weird in listOf("{not valid json", "[]", "\"HotelGuest\"", "123", "WIFI:T:WPA;S:Red;P:abc12345;;", """{"ssid":{"es":"x"},"password":"abc12345"}""")) {
            assertNull(weird, parseOperatorWifi(weird))
        }
    }

    @Test
    fun `claves de mas no rompen`() {
        assertNotNull(parseOperatorWifi("""{"ssid":"R","password":"abc12345","band":"5GHz"}"""))
    }
}
