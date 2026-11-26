package com.networkbroadcast.hospitality.companion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los mismos casos que prueba el backend (`pairing/tests_contract.py`), del lado de la app, sobre la
 * copia de `contract/pairing.json` (ver SOURCE.md). Si el backend cambia una regla y se vuelve a copiar
 * el archivo, esto avisa qué dejó de cumplir la app.
 */
class ContractTest {
    private val contract: JsonObject = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/contract/pairing.json")!!.bufferedReader().readText(),
    ).jsonObject

    private fun cases(key: String): JsonArray = contract.getValue(key).jsonArray

    @Test
    fun `el codigo es de 6 digitos`() {
        val code = contract.getValue("code").jsonObject
        assertEquals(6, code.getValue("length").jsonPrimitive.content.toInt())
        assertEquals(true, isValidPairingCode("004217"))
        assertEquals(false, isValidPairingCode("12345"))
        assertEquals(false, isValidPairingCode("12345a"))
    }

    @Test
    fun `los errores del canje se traducen como dice el contrato`() {
        val errors = contract.getValue("linkErrors").jsonObject
        val expected = mapOf(
            "InvalidCode" to LinkState.Reason.InvalidCode,
            "Expired" to LinkState.Reason.Expired,
            "NotAvailable" to LinkState.Reason.NotAvailable,
            // No existe en Reason (ver plan, D22): se trata como no disponible.
            "TooManyAttempts" to LinkState.Reason.NotAvailable,
        )
        assertEquals(expected.keys, errors.keys)
        errors.forEach { (name, status) ->
            assertEquals(name, expected.getValue(name), linkFailureReason(status.jsonPrimitive.content.toInt()))
        }
    }

    @Test
    fun `las ordenes validas del celular se arman con el cuerpo del contrato`() {
        cases("commandCases").map { it.jsonObject }.filter { it.getValue("valid").jsonPrimitive.content == "true" }.forEach { case ->
            val body = case.getValue("body").jsonObject
            val command = when (body.getValue("type").jsonPrimitive.content) {
                "PlayChannel" -> TvCommand.PlayChannel(body.getValue("channelId").jsonPrimitive.content)
                "PlayVod" -> TvCommand.PlayVod(body.getValue("vodId").jsonPrimitive.content)
                "OpenGuide" -> TvCommand.OpenGuide
                "OpenHotelInfo" -> TvCommand.OpenHotelInfo
                else -> error("tipo sin TvCommand: $body")
            }
            assertEquals(case.getValue("name").jsonPrimitive.content, body, command.toBody())
        }
    }

    @Test
    fun `la TV entiende las ordenes que arma el backend`() {
        cases("commandCases").map { it.jsonObject }.filter { it.getValue("valid").jsonPrimitive.content == "true" }.forEach { case ->
            val body = case.getValue("body").jsonObject
            val serverShape = JsonObject(body + mapOf("id" to Json.parseToJsonElement("1")))
            val parsed = parseServerCommand(serverShape) as ServerCommand.FromPhone
            assertEquals(case.getValue("name").jsonPrimitive.content, body, parsed.command.toBody())
        }
        // Y la que manda el servidor al terminar la estadia (`commandTypes.fromServer`).
        assertEquals(listOf("Checkout"), contract.getValue("commandTypes").jsonObject.getValue("fromServer").jsonArray.map { it.jsonPrimitive.content })
        val checkout = parseServerCommand(Json.parseToJsonElement("""{"id":3,"type":"Checkout","stayNumber":2,"phoneId":null}"""))
        assertEquals(ServerCommand.Checkout(3, 2), checkout)
    }

    @Test
    fun `una orden de un tipo desconocido se ignora`() {
        assertNull(parseServerCommand(Json.parseToJsonElement("""{"id":3,"type":"Reboot"}""")))
        assertNull(parseServerCommand(Json.parseToJsonElement("""{"id":3,"type":"PlayChannel"}""")))
    }

    @Test
    fun `el handoff completo del contrato se lee entero`() {
        val case = cases("handoffCases").map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content.startsWith("completo") }
        val handoff = parseHandoff(case.getValue("clean"))
        assertNotNull(handoff)
        assertEquals("Aqua Caribe", handoff!!.hotelName)
        assertEquals("101", handoff.roomNumber)
        assertEquals("pt-BR", handoff.language)
        assertEquals("María Pérez", handoff.guestName)
        assertEquals("https://cdn.example.com/hotels/aqua/logo.png", handoff.logoUrl)
        assertEquals("https://cdn.example.com/hotels/aqua/bg.jpg", handoff.backgroundUrl)
        assertEquals(HandoffColors("#163F42", "#FFFFFF", "#E2B04A", "#2E7D32", "#B71C1C"), handoff.colors)
    }

    @Test
    fun `un handoff vacio o ausente no rompe`() {
        assertNull(parseHandoff(null))
        assertEquals(Handoff(), parseHandoff(Json.parseToJsonElement("{}")))
    }

    @Test
    fun `el handoff se guarda y se vuelve a leer igual`() {
        val handoff = Handoff(hotelName = "X", colors = HandoffColors(card = "#000000"))
        assertEquals(handoff, parseHandoff(handoff.toJson()))
    }
}
