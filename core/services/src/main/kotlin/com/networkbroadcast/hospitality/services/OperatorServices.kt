package com.networkbroadcast.hospitality.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Catálogo de servicios del hotel que manda el operador por Panaccess (parámetro X_APP_SERVICES),
 * mientras no haya conexión con el sistema del hotel (CRM/PMS) para recibir pedidos: la TV muestra
 * nombre, descripción, precio y foto, y no ofrece pedir (ver [ReadOnlyServices]).
 *
 * Formato (el valor del parámetro puede ser este JSON o una URL a un archivo con él, igual que
 * X_APP_WIDGETS):
 * `{"currency":"MXN","categories":[{"name":{"es":"Bebidas","en":"Drinks"},"items":[
 * {"name":{"es":"Margarita","en":"Margarita"},"description":{"es":"Tequila, limón"},
 * "price":120,"image":"https://...","durationMin":60}]}]}`
 *
 * - `name`/`description` aceptan un texto simple o uno por idioma ("es", "en", "pt", "fr", "de");
 *   `title` vale como `name`. En cada categoría y cada ítem solo el nombre es obligatorio.
 * - `price` sin cargar = no se muestra precio (no se inventa "Sin cargo"); `0` = sin cargo.
 * - `currency` es el código ISO 4217 (MXN, USD...); sin cargar, USD.
 * - `image` (o `images`, se usa la primera); `durationMin` solo para servicios con duración (spa).
 * - Un ítem o categoría sin nombre, o una categoría sin ítems, se descarta sin afectar al resto.
 * - También se acepta el array de categorías sin el objeto que lo envuelve.
 *
 * null si el texto no es un JSON válido o no trae ninguna categoría con ítems.
 */
fun parseOperatorCatalog(raw: String): ServiceCatalog? {
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
    val (currency, categoryArray) = when (root) {
        is JsonArray -> "USD" to root
        is JsonObject -> (text(root["currency"])?.uppercase() ?: "USD") to (root["categories"] as? JsonArray)
        else -> return null
    }
    val categories = categoryArray.orEmpty().mapIndexedNotNull { ci, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val name = localized(obj["name"] ?: obj["title"]).takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
        val categoryId = text(obj["id"]) ?: "cat${ci + 1}"
        val items = (obj["items"] as? JsonArray).orEmpty().mapIndexedNotNull { ii, itemElement ->
            val item = itemElement as? JsonObject ?: return@mapIndexedNotNull null
            val itemName = localized(item["name"] ?: item["title"]).takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val price = number(item["price"])
            ServiceItem(
                id = text(item["id"]) ?: "$categoryId-${ii + 1}",
                name = itemName,
                description = localized(item["description"]),
                price = price ?: 0.0,
                showPrice = price != null,
                imageUrl = text(item["image"] ?: item["imageUrl"]) ?: (item["images"] as? JsonArray)?.firstNotNullOfOrNull { text(it) },
                durationMin = number(item["durationMin"])?.toInt(),
            )
        }
        if (items.isEmpty()) null else ServiceCategory(categoryId, name, items)
    }
    return if (categories.isEmpty()) null else ServiceCatalog(currency = currency, categories = categories)
}

private fun text(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun number(element: JsonElement?): Double? = text(element)?.replace(',', '.')?.toDoubleOrNull()

/** `{"es":"...","en":"..."}`, o un texto simple (vale para cualquier idioma: queda como "es", el de reserva). */
private fun localized(element: JsonElement?): Map<String, String> = when (element) {
    is JsonObject -> element.mapNotNull { (lang, value) -> text(value)?.let { lang to it } }.toMap()
    else -> text(element)?.let { mapOf("es" to it) }.orEmpty()
}

/**
 * Lado huésped sin pedidos: muestra el catálogo que mandó el operador y nada más. Reemplaza al
 * DemoGuestServices cuando la cuenta trae X_APP_SERVICES; cuando exista el backend con el sistema
 * del hotel, lo reemplaza una implementación que sí reciba pedidos.
 */
class ReadOnlyServices(private val data: ServiceCatalog) : GuestServices {
    override val canOrder: Boolean = false
    override suspend fun catalog(): ServiceCatalog = data
    override val orders: StateFlow<List<ServiceOrder>> = MutableStateFlow(emptyList<ServiceOrder>()).asStateFlow()
    override suspend fun placeOrder(request: OrderRequest): ServiceOrder? = null
    override suspend fun cancel(orderId: String): Boolean = false
    override suspend fun endStay() = Unit
}
