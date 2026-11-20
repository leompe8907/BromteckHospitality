package com.networkbroadcast.hospitality.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorServicesTest {

    private val full = """
        {"currency":"mxn","categories":[
          {"name":{"es":"Bebidas","en":"Drinks"},"items":[
            {"name":{"es":"Margarita","en":"Margarita"},"description":{"es":"Tequila y limón"},"price":120,"image":"https://x/m.jpg"},
            {"title":"Agua de coco","price":"45,5"}
          ]},
          {"name":"Spa","items":[{"name":{"es":"Masaje"},"price":1800,"durationMin":60,"images":["https://x/a.jpg","https://x/b.jpg"]}]}
        ]}
    """.trimIndent()

    @Test
    fun `parsea categorias, items, precio, foto y duracion`() {
        val catalog = parseOperatorCatalog(full)!!
        assertEquals("MXN", catalog.currency)
        assertEquals(2, catalog.categories.size)
        val margarita = catalog.categories[0].items[0]
        assertEquals("Margarita", margarita.name.localized("en"))
        assertEquals("Tequila y limón", margarita.description.localized("es"))
        assertEquals(120.0, margarita.price, 0.0)
        assertEquals("https://x/m.jpg", margarita.imageUrl)
        val massage = catalog.categories[1].items[0]
        assertEquals(60, massage.durationMin)
        assertEquals("https://x/a.jpg", massage.imageUrl)
    }

    @Test
    fun `un texto simple vale en cualquier idioma y title vale como name`() {
        val catalog = parseOperatorCatalog(full)!!
        assertEquals("Spa", catalog.categories[1].name.localized("de"))
        val coco = catalog.categories[0].items[1]
        assertEquals("Agua de coco", coco.name.localized("fr"))
        assertEquals(45.5, coco.price, 0.0)
    }

    @Test
    fun `sin precio no se muestra precio, con cero es sin cargo`() {
        val catalog = parseOperatorCatalog(
            """[{"name":"Varios","items":[{"name":"Toallas","price":0},{"name":"Plancha"}]}]""",
        )!!
        val (towels, iron) = catalog.categories.single().items
        assertEquals("Sin cargo", towels.priceText("USD", "es", "Sin cargo"))
        assertNull(iron.priceText("USD", "es", "Sin cargo"))
    }

    @Test
    fun `los items o categorias sin nombre, o vacias, se descartan sin afectar al resto`() {
        val catalog = parseOperatorCatalog(
            """{"categories":[
                {"name":"Vacia","items":[]},
                {"items":[{"name":"Sin categoria"}]},
                {"name":"Comida","items":[{"price":10},{"name":"Nachos","price":90}]}
            ]}""",
        )!!
        assertEquals("USD", catalog.currency)
        val category = catalog.categories.single()
        assertEquals("Comida", category.name.localized("es"))
        assertEquals("Nachos", category.items.single().name.localized("es"))
    }

    @Test
    fun `los ids se generan si faltan y son unicos`() {
        val catalog = parseOperatorCatalog(full)!!
        val ids = catalog.categories.flatMap { it.items }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `un JSON invalido o sin categorias con items da null`() {
        assertNull(parseOperatorCatalog("{not json"))
        assertNull(parseOperatorCatalog("https://example.com/servicios.json"))
        assertNull(parseOperatorCatalog("""{"categories":[]}"""))
        assertNull(parseOperatorCatalog("""{"currency":"MXN"}"""))
        assertNull(parseOperatorCatalog("42"))
    }

    @Test
    fun `el catalogo del operador es solo para mirar`() = runBlocking {
        val services = ReadOnlyServices(parseOperatorCatalog(full)!!)
        assertFalse(services.canOrder)
        assertTrue(services.orders.value.isEmpty())
        assertNull(services.placeOrder(OrderRequest("214", null, OrderLocation.room("214"), emptyList(), PaymentMethod.RoomCharge, chargeConfirmed = false)))
        assertFalse(services.cancel("x"))
        assertEquals(2, services.catalog().categories.size)
    }
}
