package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTvPairingTest {
    private val transport = FakeTransport()
    private val store = InMemoryKeyValueStore()
    private val tv = HttpTvPairing(transport, store, hotelId = "hotel-1", room = "101", tvName = "TV 101", commandHoldMs = 0)

    private fun registered() { store.put("tv.secret", "s3cret") }

    @Test
    fun `se registra una vez y guarda el secreto`() = runBlocking {
        transport.reply(201, """{"tvId":"t1","token":"s3cret"}""")
        transport.reply(201, """{"code":"123456","qrPayload":"https://h/pair?code=123456","expiresAtEpochSec":1600}""")
        transport.reply(201, """{"code":"654321","qrPayload":"https://h/pair?code=654321","expiresAtEpochSec":2200}""")

        val code = tv.requestCode()
        assertEquals(PairingCode("123456", "https://h/pair?code=123456", 1600), code)
        assertEquals(TvRegistration.Registered, tv.registration.value)
        tv.requestCode()

        val register = transport.requests[0]
        assertEquals("/api/tv/register/", register.path)
        assertEquals("""{"hotelId":"hotel-1","room":"101","name":"TV 101"}""", register.body)
        assertFalse(register.headers.containsKey("Authorization"))
        // El segundo pedido de código ya no se registra: usa el secreto guardado.
        assertEquals(3, transport.requests.size)
        assertEquals("Tv s3cret", transport.requests[2].headers["Authorization"])
    }

    @Test
    fun `manda el codigo de activacion al registrarse y avisa si no sirve`() = runBlocking {
        val withCode = HttpTvPairing(transport, store, "hotel-1", "101", activationCode = "ABCD-EFGH", commandHoldMs = 0)
        transport.reply(403, """{"error":"InvalidActivationCode"}""")
        assertNull(withCode.requestCode())
        assertEquals(TvRegistration.ActivationRequired, withCode.registration.value)
        assertEquals("""{"hotelId":"hotel-1","room":"101","activationCode":"ABCD-EFGH"}""", transport.requests.single().body)
        assertNull(store.get("tv.secret"))
    }

    @Test
    fun `habitacion ya registrada o hotel desconocido no dan codigo`() = runBlocking {
        transport.reply(409, """{"error":"AlreadyRegistered"}""")
        assertNull(tv.requestCode())
        assertEquals(TvRegistration.AlreadyRegistered, tv.registration.value)
        transport.reply(404, """{"error":"UnknownHotel"}""")
        assertNull(tv.requestCode())
        assertEquals(TvRegistration.UnknownHotel, tv.registration.value)
        assertNull(store.get("tv.secret"))
    }

    @Test
    fun `sin red no hay codigo ni se rompe`() = runBlocking {
        registered()
        transport.offline()
        assertNull(tv.requestCode())
        transport.reply(429, """{"error":"TooManyAttempts"}""")
        assertNull(tv.requestCode())
        assertEquals("s3cret", store.get("tv.secret"))
    }

    @Test
    fun `un 401 descarta el secreto, el cursor y la estadia, y se registra de nuevo`() = runBlocking {
        registered()
        store.put("tv.commandCursor", "40")
        store.put("tv.stayNumber", "3")
        transport.reply(401, """{"error":"InvalidCredentials"}""")
        assertNull(tv.requestCode())
        assertNull(store.get("tv.secret"))
        assertNull(store.get("tv.commandCursor"))
        assertNull(store.get("tv.stayNumber"))
        assertEquals(TvRegistration.Unknown, tv.registration.value)

        transport.reply(201, """{"tvId":"t2","token":"nuevo"}""")
        transport.reply(201, """{"code":"111111","qrPayload":"q","expiresAtEpochSec":5}""")
        assertEquals("111111", tv.requestCode()?.code)
        assertEquals("nuevo", store.get("tv.secret"))
    }

    @Test
    fun `lista los celulares emparejados`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[{"id":"p1","name":"Pixel","pairedAt":"x"},{"id":"p2","name":"iPhone","pairedAt":"y"}],"stay":{"number":0,"checkedOutAt":null}}""")
        tv.pollPhones()
        assertEquals(listOf(PairedPhone("p1", "Pixel"), PairedPhone("p2", "iPhone")), tv.pairedPhones.value)
        assertEquals("/api/tv/phones/", transport.requests.single().path)
    }

    @Test
    fun `ejecuta las ordenes en orden y recuerda el cursor`() = runBlocking {
        registered()
        transport.reply(200, """{"commands":[
            {"id":5,"type":"PlayChannel","channelId":"4","phoneId":"p1","createdAt":"x"},
            {"id":6,"type":"OpenGuide","phoneId":"p1","createdAt":"x"},
            {"id":7,"type":"Reboot","phoneId":"p1","createdAt":"x"}],"cursor":7,"stay":{"number":0}}""")
        transport.reply(200, """{"commands":[],"cursor":7,"stay":{"number":0}}""")

        val seen = mutableListOf<TvCommand>()
        val job = tv.commands.onEach { it?.let(seen::add) }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        tv.pollCommands()
        tv.pollCommands()
        job.cancel()

        assertEquals("/api/tv/commands/?after=0", transport.requests[0].path)
        assertEquals("/api/tv/commands/?after=7", transport.requests[1].path)
        assertEquals("7", store.get("tv.commandCursor"))
        // La orden de tipo desconocido se ignora; las otras dos se ejecutan en orden.
        assertEquals(listOf(TvCommand.PlayChannel("4"), TvCommand.OpenGuide), seen)
        assertNull(tv.commands.value)
    }

    @Test
    fun `dos ordenes iguales seguidas llegan las dos`() = runBlocking {
        registered()
        transport.reply(200, """{"commands":[{"id":1,"type":"OpenGuide"},{"id":2,"type":"OpenGuide"}],"cursor":2,"stay":{"number":0}}""")
        val seen = mutableListOf<TvCommand>()
        val job = tv.commands.onEach { it?.let(seen::add) }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        tv.pollCommands()
        job.cancel()
        assertEquals(listOf(TvCommand.OpenGuide, TvCommand.OpenGuide), seen)
    }

    @Test
    fun `una TV recien instalada adopta el numero de estadia sin avisar check-out`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[],"stay":{"number":4,"checkedOutAt":null}}""")
        var checkouts = 0
        val job = tv.checkouts.onEach { checkouts++ }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        tv.pollPhones()
        job.cancel()
        assertEquals(0, checkouts)
        assertEquals("4", store.get("tv.stayNumber"))
    }

    @Test
    fun `si el numero de estadia sube hubo check-out aunque la TV estuviera apagada`() = runBlocking {
        registered()
        store.put("tv.stayNumber", "4")
        transport.reply(200, """{"commands":[],"cursor":0,"stay":{"number":5,"checkedOutAt":"x"}}""")
        transport.reply(200, """{"commands":[],"cursor":0,"stay":{"number":5,"checkedOutAt":"x"}}""")
        var checkouts = 0
        val job = tv.checkouts.onEach { checkouts++ }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        tv.pollCommands()
        tv.pollCommands()
        job.cancel()
        // Una sola vez: el número nuevo queda guardado.
        assertEquals(1, checkouts)
        assertEquals("5", store.get("tv.stayNumber"))
    }

    @Test
    fun `la orden Checkout y el numero de estadia no disparan dos veces`() = runBlocking {
        registered()
        store.put("tv.stayNumber", "1")
        transport.reply(200, """{"commands":[{"id":9,"type":"Checkout","stayNumber":2,"phoneId":null}],"cursor":9,"stay":{"number":2}}""")
        var checkouts = 0
        val job = tv.checkouts.onEach { checkouts++ }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        tv.pollCommands()
        job.cancel()
        assertEquals(1, checkouts)
    }

    @Test
    fun `el check-out de la propia TV guarda el numero sin avisar a nadie`() = runBlocking {
        registered()
        store.put("tv.stayNumber", "1")
        transport.reply(200, """{"unpaired":2,"stay":{"number":2,"checkedOutAt":"x"}}""")
        transport.reply(200, """{"commands":[{"id":9,"type":"Checkout","stayNumber":2}],"cursor":9,"stay":{"number":2}}""")
        var checkouts = 0
        val job = tv.checkouts.onEach { checkouts++ }.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        assertTrue(tv.checkOut())
        tv.pollCommands()
        job.cancel()
        assertEquals("/api/tv/checkout/", transport.requests[0].path)
        assertEquals(0, checkouts)
        assertEquals("2", store.get("tv.stayNumber"))
    }

    @Test
    fun `check-out sin red devuelve false y no toca nada`() = runBlocking {
        registered()
        store.put("tv.stayNumber", "1")
        transport.offline()
        assertFalse(tv.checkOut())
        assertEquals("1", store.get("tv.stayNumber"))
    }

    @Test
    fun `desemparejar todo y uno`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[{"id":"p1","name":"A"},{"id":"p2","name":"B"}],"stay":{"number":0}}""")
        tv.pollPhones()
        transport.reply(204)
        assertTrue(tv.unpair("p1"))
        assertEquals(listOf("p2"), tv.pairedPhones.value.map { it.id })
        assertEquals("DELETE", transport.requests[1].method)
        assertEquals("/api/tv/phones/p1/", transport.requests[1].path)
        transport.reply(404, """{"error":"UnknownPhone"}""")
        assertFalse(tv.unpair("zzz"))
        transport.reply(200, """{"unpaired":1}""")
        tv.unpairAll()
        assertEquals(emptyList<PairedPhone>(), tv.pairedPhones.value)
        assertEquals("/api/tv/unpair-all/", transport.requests.last().path)
    }

    @Test
    fun `un cuerpo que no es JSON no rompe el polling`() = runBlocking {
        registered()
        transport.reply(200, "<html>Bad gateway</html>")
        transport.reply(200, "[1,2]")
        tv.pollPhones()
        tv.pollCommands()
        assertEquals(emptyList<PairedPhone>(), tv.pairedPhones.value)
        assertNull(store.get("tv.commandCursor"))
    }

    @Test
    fun `los celulares que esperan confirmacion salen aparte y se aceptan o rechazan`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[],"pending":[{"id":"p1","name":"Pixel","requestedAt":"x","expiresAt":"y"},{"id":"p2","name":"iPhone"}],"stay":{"number":0}}""")
        tv.pollPhones()
        assertEquals(listOf(PairedPhone("p1", "Pixel"), PairedPhone("p2", "iPhone")), tv.pendingPhones.value)
        assertEquals(emptyList<PairedPhone>(), tv.pairedPhones.value)

        transport.reply(204)
        assertTrue(tv.acceptPhone("p1"))
        assertEquals("POST", transport.requests.last().method)
        assertEquals("/api/tv/phones/p1/accept/", transport.requests.last().path)
        assertEquals(listOf(PairedPhone("p1", "Pixel")), tv.pairedPhones.value)
        assertEquals(listOf("p2"), tv.pendingPhones.value.map { it.id })

        transport.reply(204)
        assertTrue(tv.rejectPhone("p2"))
        assertEquals("DELETE", transport.requests.last().method)
        assertEquals(emptyList<PairedPhone>(), tv.pendingPhones.value)
    }

    @Test
    fun `aceptar uno que ya cayo da false y no cambia las listas`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[],"pending":[{"id":"p1","name":"Pixel"}],"stay":{"number":0}}""")
        tv.pollPhones()
        transport.reply(404, """{"error":"UnknownPhone"}""")
        assertFalse(tv.acceptPhone("p1"))
        assertEquals(listOf("p1"), tv.pendingPhones.value.map { it.id })
        transport.offline()
        assertFalse(tv.acceptPhone("p1"))
    }

    @Test
    fun `un check-out vacia tambien los pendientes`() = runBlocking {
        registered()
        transport.reply(200, """{"phones":[],"pending":[{"id":"p1","name":"Pixel"}],"stay":{"number":0}}""")
        tv.pollPhones()
        transport.reply(200, """{"unpaired":1,"stay":{"number":1}}""")
        assertTrue(tv.checkOut())
        assertEquals(emptyList<PairedPhone>(), tv.pendingPhones.value)
    }
}
