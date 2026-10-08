package com.networkbroadcast.hospitality.services

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Lee el catálogo de un archivo de la marca (assets/services.json). Se lee una vez. */
class AssetServiceCatalog(private val context: Context, private val assetName: String) {
    private var cached: ServiceCatalog? = null
    private val lock = Mutex()

    suspend fun load(): ServiceCatalog? = lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetName).bufferedReader().use { CatalogJson.decodeFromString<ServiceCatalog>(it.readText()) }
            }.getOrNull()
        }.also { cached = it }
    }
}

internal val CatalogJson = Json { ignoreUnknownKeys = true }

/**
 * Versión de PRUEBA del lado huésped, hasta que exista el backend. No le llega a nadie: el pedido
 * avanza solo (aceptado, en camino, entregado) para poder mostrar el seguimiento en una demo.
 * Con el backend, lo avanza el mesero desde su app.
 */
class DemoGuestServices(
    private val loadCatalog: suspend () -> ServiceCatalog?,
    private val scope: CoroutineScope,
    /** Espera antes de cada paso: aceptado, en camino, entregado. */
    private val stepDelaysMillis: List<Long> = listOf(6_000, 14_000, 22_000),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : GuestServices {
    private val _orders = MutableStateFlow<List<ServiceOrder>>(emptyList())
    override val orders: StateFlow<List<ServiceOrder>> = _orders.asStateFlow()
    private var counter = 0

    override suspend fun catalog(): ServiceCatalog? = loadCatalog()

    override suspend fun placeOrder(request: OrderRequest): ServiceOrder? {
        if (request.lines.isEmpty()) return null
        if (request.payment == PaymentMethod.RoomCharge && !request.chargeConfirmed && request.lines.sumOf { it.subtotal } > 0) return null
        delay(600)
        val now = clock()
        val order = ServiceOrder(
            id = "demo-${now}-${++counter}",
            roomNumber = request.roomNumber,
            guestName = request.guestName,
            location = request.location,
            lines = request.lines,
            payment = request.payment,
            status = OrderStatus.Requested,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        _orders.update { listOf(order) + it }
        scope.launch {
            for (wait in stepDelaysMillis) {
                delay(wait)
                val current = _orders.value.firstOrNull { it.id == order.id } ?: return@launch
                val next = current.status.next() ?: return@launch
                replace(current.advancedTo(next, clock()))
            }
        }
        return order
    }

    override suspend fun cancel(orderId: String): Boolean {
        val current = _orders.value.firstOrNull { it.id == orderId } ?: return false
        if (current.status != OrderStatus.Requested) return false
        replace(current.copy(status = OrderStatus.Cancelled, updatedAtMillis = clock()))
        return true
    }

    override suspend fun endStay() { _orders.value = emptyList() }

    private fun replace(order: ServiceOrder) = _orders.update { list -> list.map { if (it.id == order.id) order else it } }
}

/**
 * Versión de PRUEBA del lado personal. Arranca con pedidos de ejemplo y suma uno nuevo cada tanto,
 * para que la app del mesero se vea trabajando. Con el backend, los pedidos son los de los huéspedes.
 */
class DemoStaffServices(
    private val loadCatalog: suspend () -> ServiceCatalog?,
    private val scope: CoroutineScope,
    private val newOrderEveryMillis: Long = 75_000,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) : StaffServices {
    private val _queue = MutableStateFlow<List<ServiceOrder>>(emptyList())
    override val queue: StateFlow<List<ServiceOrder>> = _queue.asStateFlow()
    private var counter = 0
    private var started = false

    override suspend fun catalog(): ServiceCatalog? = loadCatalog()

    /** Carga los pedidos de ejemplo y empieza a generar nuevos. Llamarlo una vez al abrir la app. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val catalog = loadCatalog() ?: return@launch
            _queue.value = seed(catalog)
            while (true) {
                delay(newOrderEveryMillis)
                if (_queue.value.count { it.status == OrderStatus.Requested } < 6) randomOrder(catalog)?.let { o -> _queue.update { listOf(o) + it } }
            }
        }
    }

    override suspend fun advance(orderId: String): ServiceOrder? {
        val current = _queue.value.firstOrNull { it.id == orderId } ?: return null
        val next = current.status.next() ?: return null
        val updated = current.advancedTo(next, clock())
        _queue.update { list -> list.map { if (it.id == orderId) updated else it } }
        return updated
    }

    override suspend fun cancel(orderId: String): Boolean {
        val current = _queue.value.firstOrNull { it.id == orderId }?.takeIf { it.status.isOpen } ?: return false
        _queue.update { list -> list.map { if (it.id == orderId) current.copy(status = OrderStatus.Cancelled, updatedAtMillis = clock()) else it } }
        return true
    }

    /** Un pedido de cada tipo: desde la piscina, desde la playa, a la habitación, y uno ya entregado. */
    internal fun seed(catalog: ServiceCatalog): List<ServiceOrder> {
        val now = clock()
        fun order(minutesAgo: Int, room: String, guest: String, location: OrderLocation, items: List<Pair<String, Int>>, status: OrderStatus): ServiceOrder? {
            val lines = items.mapNotNull { (id, qty) -> catalog.item(id)?.let { OrderLine(it.id, it.name.localized("es"), qty, it.price) } }
            if (lines.isEmpty()) return null
            val at = now - minutesAgo * 60_000L
            return ServiceOrder(
                id = "seed-${++counter}", roomNumber = room, guestName = guest, location = location, lines = lines,
                payment = PaymentMethod.RoomCharge, status = status, createdAtMillis = at, updatedAtMillis = at,
                chargeRegistered = status == OrderStatus.Delivered && lines.sumOf { it.subtotal } > 0,
            )
        }
        val pool = catalog.zones.getOrNull(0)?.id
        val beach = catalog.zones.getOrNull(1)?.id ?: pool
        return listOfNotNull(
            pool?.let { order(1, "214", "Marta", OrderLocation.spot(it, "12"), listOf("champagne" to 1), OrderStatus.Requested) },
            order(3, "305", "Carlos", OrderLocation.room("305"), listOf("club-sandwich" to 2, "coconut-water" to 2), OrderStatus.Requested),
            beach?.let { order(9, "118", "Ana", OrderLocation.spot(it, "7"), listOf("massage-60" to 1), OrderStatus.Accepted) },
            order(25, "214", "Marta", OrderLocation.room("214"), listOf("towels" to 1), OrderStatus.Delivered),
        )
    }

    private fun randomOrder(catalog: ServiceCatalog): ServiceOrder? {
        val items = catalog.categories.flatMap { it.items }
        if (items.isEmpty()) return null
        val room = listOf("102", "118", "207", "214", "305", "312", "401").random(random)
        val zone = catalog.zones.randomOrNull(random)
        val location = if (zone != null && random.nextBoolean()) OrderLocation.spot(zone.id, (1..zone.spots).random(random).toString()) else OrderLocation.room(room)
        val candidates = items.filter { it.availableIn(location) }.ifEmpty { return null }
        val item = candidates.random(random)
        val now = clock()
        return ServiceOrder(
            id = "demo-${now}-${++counter}", roomNumber = room, guestName = listOf("Lucía", "John", "Pedro", "Sofía", "Peter", "Camila").random(random),
            location = location, lines = listOf(OrderLine(item.id, item.name.localized("es"), if (item.price < 300) (1..2).random(random) else 1, item.price)),
            payment = PaymentMethod.RoomCharge, status = OrderStatus.Requested, createdAtMillis = now, updatedAtMillis = now,
        )
    }
}

/** El pedido un paso adelante; al entregarlo, el cargo queda registrado en la cuenta de la habitación. */
internal fun ServiceOrder.advancedTo(next: OrderStatus, now: Long): ServiceOrder = copy(
    status = next,
    updatedAtMillis = now,
    chargeRegistered = chargeRegistered || (next == OrderStatus.Delivered && payment == PaymentMethod.RoomCharge && total > 0),
)
