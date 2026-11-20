package com.networkbroadcast.hospitality.hotel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los casos de `contract/wifi_qr.json` del repo del backend (copiado tal cual a `src/test/resources/`): el panel
 * del hotel muestra el QR con la misma regla que usa la TV, y los dos lados prueban estos mismos casos.
 * Si cambia una regla del texto del QR, cambia el archivo y los tests dicen qué lado quedó atrás.
 */
class WifiQrContractTest {

    @Test
    fun `el texto del qr coincide con el del backend en todos los casos`() {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("wifi_qr.json")) { "falta wifi_qr.json" }
            .bufferedReader().use { it.readText() }
        val cases = Json.parseToJsonElement(text).jsonObject.getValue("cases").jsonArray
        assertTrue(cases.size > 10)
        for (case in cases) {
            val c = case.jsonObject
            val password = c.getValue("password").takeUnless { it is JsonNull }?.jsonPrimitive?.content
            val expected = c.getValue("payload").takeUnless { it is JsonNull }?.jsonPrimitive?.content
            val wifi = HotelWifi(
                ssid = c.getValue("ssid").jsonPrimitive.content,
                password = password,
                security = c.getValue("security").jsonPrimitive.content,
                hidden = c.getValue("hidden").jsonPrimitive.boolean,
            )
            assertEquals(c.getValue("name").jsonPrimitive.content, expected, wifi.toNetwork()?.qrPayload)
        }
    }
}
