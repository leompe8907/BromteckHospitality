package com.networkbroadcast.hospitality.services

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Servicios del hotel: el huésped pide (masaje, champán, room service, toallas) desde la TV, el
 * celular o escaneando el QR de un camastro; el personal lo recibe en la app del mesero, lo
 * acepta, lo lleva y lo marca entregado; el cargo va a la cuenta de la habitación.
 *
 * Pasa por el backend propio (otro proyecto, ver docs/backend-idea.md). Dos lados, dos contratos:
 * [GuestServices] lo usan la TV y el celular; [StaffServices], la app del mesero. Mientras no exista
 * el backend, DemoGuestServices / DemoStaffServices permiten construir y mostrar las pantallas.
 */

/** Catálogo del hotel. Lo arma el hotel en el panel; hoy sale del services.json de la marca. */
@Serializable
data class ServiceCatalog(
    /** Código ISO 4217 (MXN, USD…). */
    val currency: String = "USD",
    val categories: List<ServiceCategory> = emptyList(),
    /** Lugares del hotel desde donde se puede pedir, además de la habitación (piscina, playa). */
    val zones: List<ServiceZone> = emptyList(),
) {
    fun item(id: String): ServiceItem? = categories.firstNotNullOfOrNull { c -> c.items.firstOrNull { it.id == id } }
    fun zone(id: String?): ServiceZone? = zones.firstOrNull { it.id == id }
}

@Serializable
data class ServiceCategory(
    val id: String,
    /** Texto por idioma: {"es": "...", "en": "..."}. */
    val name: Map<String, String>,
    val items: List<ServiceItem> = emptyList(),
)

@Serializable
data class ServiceItem(
    val id: String,
    val name: Map<String, String>,
    val description: Map<String, String> = emptyMap(),
    /** 0 = sin cargo (toallas, almohadas). */
    val price: Double = 0.0,
    /** URL o "asset:carpeta/foto.jpg" de la marca. */
    val imageUrl: String? = null,
    /** Para servicios con duración (masajes). */
    val durationMin: Int? = null,
    /** Dónde se puede pedir: "room" y/o ids de [ServiceZone]. */
    val availableAt: List<String> = listOf(ROOM),
) {
    fun availableIn(location: OrderLocation): Boolean =
        if (location.zoneId == null) ROOM in availableAt else location.zoneId in availableAt

    companion object {
        const val ROOM = "room"
    }
}

/** Un lugar del hotel con puestos numerados: "Piscina", camastros 1 a 40. */
@Serializable
data class ServiceZone(
    val id: String,
    val name: Map<String, String>,
    /** Cómo se llama cada puesto: "Camastro", "Mesa". */
    val spotLabel: Map<String, String>,
    val spots: Int,
)

/** Adónde se lleva el pedido: la habitación, o un puesto de una zona (camastro 12 de la piscina). */
@Serializable
data class OrderLocation(
    val roomNumber: String? = null,
    val zoneId: String? = null,
    val spot: String? = null,
) {
    val isRoom: Boolean get() = zoneId == null

    companion object {
        fun room(number: String) = OrderLocation(roomNumber = number)
        fun spot(zoneId: String, spot: String) = OrderLocation(zoneId = zoneId, spot = spot)
    }
}

@Serializable
data class OrderLine(val itemId: String, val name: String, val quantity: Int, val unitPrice: Double) {
    val subtotal: Double get() = unitPrice * quantity
}

/** El orden importa: el pedido avanza de a un paso. */
enum class OrderStatus { Requested, Accepted, OnTheWay, Delivered, Cancelled;

    /** El paso siguiente que da el personal, o null si ya terminó. */
    fun next(): OrderStatus? = when (this) {
        Requested -> Accepted
        Accepted -> OnTheWay
        OnTheWay -> Delivered
        Delivered, Cancelled -> null
    }

    val isOpen: Boolean get() = this != Delivered && this != Cancelled
}

/**
 * Cómo se paga. El cargo a la habitación va a la cuenta del huésped en el sistema del hotel
 * (Opera); la tarjeta, por la pasarela de pagos. Ninguno de los dos existe todavía en la app.
 */
enum class PaymentMethod { RoomCharge, Card }

/** Lo que el huésped manda al pedir. */
data class OrderRequest(
    /** Habitación de la estadía: a su cuenta va el cargo, aunque se pida desde la piscina. */
    val roomNumber: String,
    val guestName: String?,
    val location: OrderLocation,
    val lines: List<OrderLine>,
    val payment: PaymentMethod,
    /** El huésped confirmó el cargo a la habitación (evita reclamos en el check-out). */
    val chargeConfirmed: Boolean,
)

@Serializable
data class ServiceOrder(
    val id: String,
    val roomNumber: String,
    val guestName: String? = null,
    val location: OrderLocation,
    val lines: List<OrderLine>,
    val payment: PaymentMethod,
    val status: OrderStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** El cargo quedó registrado en la cuenta de la habitación (se marca al entregar). */
    val chargeRegistered: Boolean = false,
) {
    val total: Double get() = lines.sumOf { it.subtotal }
    /** Resumen de una línea: "2 × Margarita de la casa, Agua de coco". */
    val summary: String get() = lines.joinToString(", ") { if (it.quantity > 1) "${it.quantity} × ${it.name}" else it.name }
}

/** Lado huésped: TV y celular. */
interface GuestServices {
    suspend fun catalog(): ServiceCatalog?

    /** Pedidos de esta estadía, del más nuevo al más viejo. Se actualizan solos cuando el personal avanza. */
    val orders: StateFlow<List<ServiceOrder>>

    /** null si no se pudo mandar. */
    suspend fun placeOrder(request: OrderRequest): ServiceOrder?

    /** Sólo mientras nadie lo aceptó. */
    suspend fun cancel(orderId: String): Boolean

    /** Check-out: el próximo huésped no ve estos pedidos. */
    suspend fun endStay()
}

/** Lado personal: la app del mesero. */
interface StaffServices {
    suspend fun catalog(): ServiceCatalog?

    /** Todos los pedidos del turno, del más nuevo al más viejo. */
    val queue: StateFlow<List<ServiceOrder>>

    /** Un paso adelante: aceptar → salir a entregar → entregado. null si no existe o ya terminó. */
    suspend fun advance(orderId: String): ServiceOrder?

    suspend fun cancel(orderId: String): Boolean
}

/** Texto en el idioma pedido; si falta, en inglés (lo entiende más gente), después el de reserva. */
fun Map<String, String>.localized(language: String, fallback: String = "es"): String =
    this[language] ?: this["en"] ?: this[fallback] ?: values.firstOrNull().orEmpty()

/** "MX$2,400.00" según el idioma; 0 → [freeLabel]. */
fun formatPrice(amount: Double, currency: String, language: String, freeLabel: String): String {
    if (amount == 0.0) return freeLabel
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(language))
    runCatching { format.currency = Currency.getInstance(currency) }
    format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    return format.format(amount)
}

/** "Habitación 214" o "Piscina · Camastro 12". */
fun OrderLocation.label(catalog: ServiceCatalog?, text: ServicesText, language: String): String {
    val zone = catalog?.zone(zoneId)
    return when {
        zoneId == null -> text.room.format(roomNumber.orEmpty())
        zone != null -> "${zone.name.localized(language)} · ${zone.spotLabel.localized(language)} $spot"
        else -> "$zoneId · $spot"
    }
}

/**
 * Enlace que va en el QR de cada puesto: hospitality://spot?zone=pool&spot=12. Lo abre la app del
 * celular con la ubicación ya cargada; el mesero imprime estos QR desde su app.
 */
fun spotLink(zoneId: String, spot: String): String = "hospitality://spot?zone=$zoneId&spot=$spot"

/** Lo contrario de [spotLink]; null si el enlace no es de un puesto. */
fun parseSpotLink(link: String): OrderLocation? {
    if (!link.startsWith("hospitality://spot?")) return null
    val params = link.substringAfter('?').split('&').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()
    val zone = params["zone"]?.takeIf { it.isNotBlank() } ?: return null
    val spot = params["spot"]?.takeIf { it.isNotBlank() } ?: return null
    return OrderLocation.spot(zone, spot)
}
