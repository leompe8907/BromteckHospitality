package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCompanionLinkTest {
    private val transport = FakeTransport()
    private val store = InMemoryKeyValueStore()
    private val link = HttpCompanionLink(transport, store, deviceName = "Pixel de Ana", confirmPollMs = 1, confirmTimeoutMs = 400)

    private val pairedBody = """{"token":"tok","pairingId":"pr1","tv":{"roomNumber":"101","tvName":"TV 101"},
        "handoff":{"hotelName":"Aqua Caribe","roomNumber":"101","language":"es","guestName":null,"logoUrl":"https://cdn.example.com/l.png",
        "backgroundUrl":null,"colors":{"card":"#163F42","text":"#FFFFFF","playerControl":null,"confirm":null,"cancel":null}},"session":null}"""

    private fun linked() = runBlocking { transport.reply(201, pairedBody); link.link("123456") }

    @Test
    fun `canjear el codigo vincula, guarda el token y trae el handoff`() = runBlocking {
        transport.reply(201, pairedBody)
        val state = link.link("123456")

        val expected = LinkedTv(
            "101", "TV 101",
            Handoff(
                hotelName = "Aqua Caribe", roomNumber = "101", language = "es", logoUrl = "https://cdn.example.com/l.png",
                colors = HandoffColors(card = "#163F42", text = "#FFFFFF"),
            ),
        )
        assertEquals(LinkState.Linked(expected), state)
        assertEquals(state, link.state.value)
        assertEquals("tok", store.get("phone.token"))
        val request = transport.requests.single()
        assertEquals("/api/phone/pair/", request.path)
        assertEquals("""{"code":"123456","deviceName":"Pixel de Ana"}""", request.body)
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test
    fun `un codigo mal formado ni siquiera llama al backend`() = runBlocking {
        assertEquals(LinkState.Failed(LinkState.Reason.InvalidCode), link.link("12a"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `los errores del canje salen como motivos de la app`() = runBlocking {
        suspend fun reasonFor(status: Int, error: String) = run {
            transport.reply(status, """{"error":"$error"}""")
            (link.link("123456") as LinkState.Failed).reason
        }
        assertEquals(LinkState.Reason.InvalidCode, reasonFor(400, "InvalidCode"))
        assertEquals(LinkState.Reason.Expired, reasonFor(410, "Expired"))
        assertEquals(LinkState.Reason.NotAvailable, reasonFor(503, "NotAvailable"))
        assertEquals(LinkState.Reason.NotAvailable, reasonFor(429, "TooManyAttempts"))
        transport.offline()
        assertEquals(LinkState.Failed(LinkState.Reason.Network), link.link("123456"))
        assertNull(store.get("phone.token"))
    }

    @Test
    fun `una respuesta rara del canje no vincula`() = runBlocking {
        transport.reply(201, "no es json")
        assertEquals(LinkState.Failed(LinkState.Reason.NotAvailable), link.link("123456"))
        transport.reply(201, """{"pairingId":"x"}""")
        assertEquals(LinkState.Failed(LinkState.Reason.NotAvailable), link.link("123456"))
        assertNull(store.get("phone.token"))
    }

    @Test
    fun `al reabrir la app sigue vinculado con el tema guardado`() = runBlocking {
        val first = linked()
        val reopened = HttpCompanionLink(FakeTransport(), store)
        assertEquals(first, reopened.state.value)
    }

    @Test
    fun `manda ordenes con el token`() = runBlocking {
        linked()
        transport.reply(202, """{"id":12}""")
        assertTrue(link.send(TvCommand.PlayVod("vod-77")))
        val request = transport.requests.last()
        assertEquals("/api/phone/commands/", request.path)
        assertEquals("""{"type":"PlayVod","vodId":"vod-77"}""", request.body)
        assertEquals("Phone tok", request.headers["Authorization"])
        transport.reply(400, """{"type":["no"]}""")
        assertFalse(link.send(TvCommand.OpenGuide))
        transport.offline()
        assertFalse(link.send(TvCommand.OpenGuide))
        assertTrue(link.state.value is LinkState.Linked)
    }

    @Test
    fun `sin vincular no manda nada`() = runBlocking {
        assertFalse(link.send(TvCommand.OpenGuide))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `un 401 Unlinked borra lo guardado y vuelve a desvinculado`() = runBlocking {
        linked()
        transport.reply(401, """{"error":"Unlinked"}""")
        link.refresh()
        assertEquals(LinkState.Unlinked, link.state.value)
        assertNull(store.get("phone.token"))
        assertNull(store.get("phone.tv"))
        // Y ya no se pueden mandar ordenes ni se hacen pedidos.
        val before = transport.requests.size
        assertFalse(link.send(TvCommand.OpenGuide))
        link.refresh()
        assertEquals(before, transport.requests.size)
    }

    @Test
    fun `un 401 al mandar una orden tambien desvincula`() = runBlocking {
        linked()
        transport.reply(401, """{"error":"Unlinked"}""")
        assertFalse(link.send(TvCommand.OpenGuide))
        assertEquals(LinkState.Unlinked, link.state.value)
    }

    @Test
    fun `refresh vigente actualiza el tema y sin red no cambia nada`() = runBlocking {
        linked()
        transport.reply(200, """{"pairingId":"pr1","tv":{"roomNumber":"101","tvName":"TV nueva"},"handoff":{"hotelName":"Otro nombre","colors":{}},"pairedAt":"x"}""")
        link.refresh()
        val state = link.state.value as LinkState.Linked
        assertEquals("TV nueva", state.tv.tvName)
        assertEquals("Otro nombre", state.tv.handoff?.hotelName)
        assertEquals("Phone tok", transport.requests.last().headers["Authorization"])
        transport.offline()
        link.refresh()
        assertEquals(state, link.state.value)
    }

    @Test
    fun `unlink es solo local`() = runBlocking {
        linked()
        val requests = transport.requests.size
        link.unlink()
        assertEquals(LinkState.Unlinked, link.state.value)
        assertNull(store.get("phone.token"))
        assertEquals(requests, transport.requests.size)
    }

    @Test
    fun `check-out desde el celular termina la estadia y olvida todo`() = runBlocking {
        linked()
        transport.reply(200, """{"unpaired":2}""")
        assertTrue(link.checkOut())
        assertEquals("/api/phone/checkout/", transport.requests.last().path)
        assertEquals(LinkState.Unlinked, link.state.value)
        assertNull(store.get("phone.token"))
    }

    @Test
    fun `check-out sin red no desvincula`() = runBlocking {
        linked()
        transport.offline()
        assertFalse(link.checkOut())
        assertTrue(link.state.value is LinkState.Linked)
    }

    private val pendingBody = """{"token":"tok","pairingId":"pr1","tv":{"roomNumber":"101","tvName":"TV 101"},"handoff":{"hotelName":"Aqua Caribe"},"status":"pending","session":null}"""
    private fun meBody(status: String, session: String = "null") =
        """{"pairingId":"pr1","tv":{"roomNumber":"101","tvName":"TV 101"},"handoff":{"hotelName":"Aqua Caribe"},"pairedAt":"x","status":"$status","session":$session}"""

    @Test
    fun `con confirmacion espera en Linking hasta que lo aceptan y guarda el login que llega una sola vez`() = runBlocking {
        transport.reply(201, pendingBody)
        transport.reply(200, meBody("pending"))
        transport.reply(200, meBody("pending"))
        transport.reply(200, meBody("active", """{"username":"hotel@aqua","passwordMd5":"abcdef0123456789abcdef0123456789"}"""))

        val state = link.link("123456")

        val tv = (state as LinkState.Linked).tv
        assertEquals(HotelLogin("hotel@aqua", "abcdef0123456789abcdef0123456789"), tv.login)
        assertEquals("Aqua Caribe", tv.handoff?.hotelName)
        assertEquals("tok", store.get("phone.token"))
        assertEquals("Phone tok", transport.requests[1].headers["Authorization"])
        // El login queda guardado (cifrado en Android) y sobrevive a reabrir la app.
        assertEquals(tv, (HttpCompanionLink(FakeTransport(), store).state.value as LinkState.Linked).tv)
    }

    @Test
    fun `mientras esta pendiente no guarda el token`() = runBlocking {
        transport.reply(201, pendingBody)
        transport.reply(401, """{"error":"Unlinked"}""")
        link.link("123456")
        assertNull(store.get("phone.token"))
    }

    @Test
    fun `si lo rechazan o se vence queda Failed Expired y no guarda nada`() = runBlocking {
        transport.reply(201, pendingBody)
        transport.reply(401, """{"error":"Unlinked"}""")
        assertEquals(LinkState.Failed(LinkState.Reason.Expired), link.link("123456"))
        assertNull(store.get("phone.token"))
        assertNull(store.get("phone.tv"))
    }

    @Test
    fun `si nadie lo acepta a tiempo se rinde`() = runBlocking {
        transport.reply(201, pendingBody)
        repeat(2_000) { transport.reply(200, meBody("pending")) }
        assertEquals(LinkState.Failed(LinkState.Reason.Expired), link.link("123456"))
        assertNull(store.get("phone.token"))
    }

    @Test
    fun `un corte de red mientras espera no lo rechaza`() = runBlocking {
        transport.reply(201, pendingBody)
        transport.offline()
        transport.reply(500)
        transport.reply(200, meBody("active"))
        assertTrue(link.link("123456") is LinkState.Linked)
    }

    @Test
    fun `sin confirmacion el login sale en el canje`() = runBlocking {
        transport.reply(201, pairedBody.replace(""""session":null""", """"session":{"username":"u","passwordMd5":"${"a".repeat(32)}"}"""))
        val tv = (link.link("123456") as LinkState.Linked).tv
        assertEquals(HotelLogin("u", "a".repeat(32)), tv.login)
        assertEquals(1, transport.requests.size) // no espera nada
    }

    @Test
    fun `refresh conserva el login que ya tenia aunque el backend no lo vuelva a mandar`() = runBlocking {
        transport.reply(201, pairedBody.replace(""""session":null""", """"session":{"username":"u","passwordMd5":"${"a".repeat(32)}"}"""))
        link.link("123456")
        transport.reply(200, meBody("active"))
        link.refresh()
        assertEquals("u", (link.state.value as LinkState.Linked).tv.login?.username)
    }

    @Test
    fun `el login no sale en el toString`() {
        assertFalse(HotelLogin("u", "a".repeat(32)).toString().contains("a".repeat(32)))
    }
}
