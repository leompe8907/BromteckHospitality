package com.networkbroadcast.hospitality.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesTest {

    private val catalog = ServiceCatalog(
        currency = "MXN",
        categories = listOf(
            ServiceCategory(
                "drinks", mapOf("es" to "Bebidas"),
                listOf(
                    ServiceItem("champagne", mapOf("es" to "Champán", "en" to "Champagne"), price = 2400.0, availableAt = listOf("room", "pool")),
                    ServiceItem("towels", mapOf("es" to "Toallas"), price = 0.0),
                ),
            ),
        ),
        zones = listOf(ServiceZone("pool", mapOf("es" to "Piscina"), mapOf("es" to "Camastro"), 40)),
    )

    @Test
    fun `el enlace del QR de un puesto ida y vuelta`() {
        val link = spotLink("pool", "12")
        assertEquals(OrderLocation.spot("pool", "12"), parseSpotLink(link))
        assertNull(parseSpotLink("hospitality://pair?code=123456"))
        assertNull(parseSpotLink("hospitality://spot?zone=pool"))
    }

    @Test
    fun `etiqueta de la ubicacion y disponibilidad por lugar`() {
        val text = ServicesText.Es
        assertEquals("Habitación 214", OrderLocation.room("214").label(catalog, text, "es"))
        assertEquals("Piscina · Camastro 12", OrderLocation.spot("pool", "12").label(catalog, text, "es"))
        assertTrue(catalog.item("champagne")!!.availableIn(OrderLocation.spot("pool", "1")))
        assertFalse(catalog.item("towels")!!.availableIn(OrderLocation.spot("pool", "1")))
    }

    @Test
    fun `el estado avanza de a un paso y al entregar se registra el cargo`() {
        val order = ServiceOrder(
            "1", "214", null, OrderLocation.room("214"), listOf(OrderLine("champagne", "Champán", 2, 2400.0)),
            PaymentMethod.RoomCharge, OrderStatus.Requested, 0, 0,
        )
        assertEquals(4800.0, order.total, 0.0)
        assertEquals("2 × Champán", order.summary)
        val delivered = order.advancedTo(OrderStatus.Accepted, 1).advancedTo(OrderStatus.OnTheWay, 2).advancedTo(OrderStatus.Delivered, 3)
        assertTrue(delivered.chargeRegistered)
        assertNull(OrderStatus.Delivered.next())
        assertFalse(order.advancedTo(OrderStatus.Accepted, 1).chargeRegistered)
    }

    @Test
    fun `sin confirmar el cargo a la habitacion no se manda el pedido`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val guest = DemoGuestServices({ catalog }, scope, stepDelaysMillis = listOf(50, 50, 50))
        val lines = listOf(OrderLine("champagne", "Champán", 1, 2400.0))
        assertNull(guest.placeOrder(OrderRequest("214", null, OrderLocation.room("214"), lines, PaymentMethod.RoomCharge, chargeConfirmed = false)))
        val placed = guest.placeOrder(OrderRequest("214", null, OrderLocation.room("214"), lines, PaymentMethod.RoomCharge, chargeConfirmed = true))
        assertNotNull(placed)
        delay(400)
        assertEquals(OrderStatus.Delivered, guest.orders.value.single().status)
        assertTrue(guest.orders.value.single().chargeRegistered)
        guest.endStay()
        assertTrue(guest.orders.value.isEmpty())
        scope.cancel()
    }

    @Test
    fun `lo sin cargo no pide confirmar y se puede cancelar antes de que lo acepten`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val guest = DemoGuestServices({ catalog }, scope, stepDelaysMillis = listOf(10_000))
        val placed = guest.placeOrder(
            OrderRequest("214", null, OrderLocation.room("214"), listOf(OrderLine("towels", "Toallas", 1, 0.0)), PaymentMethod.RoomCharge, chargeConfirmed = false),
        )!!
        assertTrue(guest.cancel(placed.id))
        assertEquals(OrderStatus.Cancelled, guest.orders.value.single().status)
        assertFalse(guest.cancel(placed.id))
        scope.cancel()
    }

    @Test
    fun `el mesero arranca con pedidos de ejemplo y los avanza`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val staff = DemoStaffServices({ catalog }, scope)
        val seeded = staff.seed(catalog)
        assertTrue(seeded.any { !it.location.isRoom })
        staff.start()
        delay(200)
        val first = staff.queue.value.first { it.status == OrderStatus.Requested }
        assertEquals(OrderStatus.Accepted, staff.advance(first.id)?.status)
        scope.cancel()
    }
}
