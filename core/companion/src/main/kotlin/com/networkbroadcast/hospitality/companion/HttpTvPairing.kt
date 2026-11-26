package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Resultado del alta de la TV en el backend. */
enum class TvRegistration {
    /** Todavía no se intentó, o el backend borró a esta TV y hay que registrarla de nuevo. */
    Unknown,
    Registered,

    /** La habitación ya tiene una TV registrada (409): el staff tiene que liberarla desde el panel. */
    AlreadyRegistered,

    /** El backend no conoce el hotel (404): revisar el `X_HOTEL_ID`. */
    UnknownHotel,

    /** Falta el código de activación o no sirve (403): el instalador tiene que pedir uno nuevo. */
    ActivationRequired,
}

/**
 * [TvPairing] contra el backend de Hospitality (`BromteckHospitalityBackend`, app `pairing/`).
 * Reglas y forma de los mensajes: `docs/emparejamiento.md` del backend y su `contract/pairing.json`.
 *
 * - La TV se registra sola la primera vez (`POST /api/tv/register/`) y guarda el secreto en [store];
 *   de ahí en más manda `Authorization: Tv <secreto>`. Si el backend responde 401 es que el staff
 *   liberó la habitación: se olvida el secreto y se vuelve a registrar al próximo pedido.
 * - Con [start] consulta cada pocos segundos las órdenes (`/api/tv/commands/?after=cursor`) y los
 *   celulares (`/api/tv/phones/`). No hay push: el backend resolvió el tiempo real con polling.
 * - El número de estadía (`stay.number`) llega en esas dos respuestas y no vence: si sube sin que la
 *   TV haya hecho el check-out, hubo uno por otro camino y se emite por [checkouts].
 *
 * [room] es el `licenseName` de la licencia de Panaccess de la TV (el número de habitación) y
 * [hotelId] el `X_HOTEL_ID` de la cuenta.
 */
class HttpTvPairing(
    private val transport: HttpTransport,
    private val store: KeyValueStore,
    private val hotelId: String,
    private val room: String,
    private val tvName: String? = null,
    /**
     * Código de activación de un solo uso que da el staff o la empresa (`create_tv_activation_code` en el backend).
     * Solo hace falta con `PAIRING_REQUIRE_ACTIVATION`; se manda al registrar y después no se usa más.
     */
    private val activationCode: String? = null,
    /** Cuánto queda una orden en [commands] antes de volver a null. Ver [publish]. */
    private val commandHoldMs: Long = 500,
) : TvPairing {
    private val phones = MutableStateFlow<List<PairedPhone>>(emptyList())
    private val pending = MutableStateFlow<List<PairedPhone>>(emptyList())
    private val command = MutableStateFlow<TvCommand?>(null)
    private val checkoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val registrationState = MutableStateFlow(TvRegistration.Unknown)
    private val registerLock = Mutex()
    private val commandLock = Mutex()

    override val pairedPhones: StateFlow<List<PairedPhone>> = phones.asStateFlow()
    override val pendingPhones: StateFlow<List<PairedPhone>> = pending.asStateFlow()
    override val commands: StateFlow<TvCommand?> = command.asStateFlow()
    override val checkouts: Flow<Unit> = checkoutEvents

    val registration: StateFlow<TvRegistration> = registrationState.asStateFlow()

    override suspend fun requestCode(): PairingCode? {
        val response = call("POST", "/api/tv/pairing-code/", buildJsonObject { }) ?: return null
        if (response.status != 201) return null
        return parsed {
            val body = parseObject(response.body)
            PairingCode(
                code = body.string("code") ?: return@parsed null,
                qrPayload = body.string("qrPayload") ?: return@parsed null,
                expiresAtEpochSec = body.long("expiresAtEpochSec") ?: return@parsed null,
            )
        }
    }

    override suspend fun unpairAll() {
        val response = call("POST", "/api/tv/unpair-all/") ?: return
        if (response.status == 200) {
            phones.value = emptyList()
            pending.value = emptyList()
        }
    }

    override suspend fun checkOut(): Boolean {
        val response = call("POST", "/api/tv/checkout/") ?: return false
        if (response.status != 200) return false
        phones.value = emptyList()
        pending.value = emptyList()
        // El número nuevo se guarda sin avisar: el check-out ya lo hizo esta TV, no es "otro camino".
        parsed { parseObject(response.body).obj("stay")?.long("number")?.let { store.put(KEY_STAY, it.toString()) } }
        return true
    }

    /** Desvincula UN celular (el `id` sale de [pairedPhones]); ese celular recibe 401 Unlinked. */
    suspend fun unpair(phoneId: String): Boolean {
        val response = call("DELETE", "/api/tv/phones/$phoneId/") ?: return false
        if (response.status == 204) {
            phones.value = phones.value.filterNot { it.id == phoneId }
            pending.value = pending.value.filterNot { it.id == phoneId }
        }
        return response.status == 204
    }

    override suspend fun rejectPhone(id: String): Boolean = unpair(id)

    override suspend fun acceptPhone(id: String): Boolean {
        val response = call("POST", "/api/tv/phones/$id/accept/") ?: return false
        if (response.status != 204) return false
        // Pasa de pendiente a emparejado sin esperar a la próxima consulta.
        pending.value.firstOrNull { it.id == id }?.let { accepted ->
            pending.value = pending.value.filterNot { it.id == id }
            phones.value = phones.value + accepted
        }
        return true
    }

    /** Una consulta de celulares emparejados. Pública para poder probarla sin esperar un intervalo. */
    suspend fun pollPhones() {
        val response = call("GET", "/api/tv/phones/") ?: return
        if (response.status != 200) return
        parsed {
            val body = parseObject(response.body)
            phones.value = body.array("phones").mapNotNull(::parsePhone)
            pending.value = body.array("pending").mapNotNull(::parsePhone)
            handleStay(body.obj("stay")?.long("number"))
        }
    }

    private fun parsePhone(item: JsonElement): PairedPhone? {
        val phone = item as? JsonObject ?: return null
        return PairedPhone(phone.string("id") ?: return null, phone.string("name") ?: "")
    }

    /** Una consulta de órdenes. Ejecuta (publica) las que lleguen, en orden. */
    suspend fun pollCommands() {
        val cursor = store.get(KEY_CURSOR)?.toLongOrNull() ?: 0L
        val response = call("GET", "/api/tv/commands/?after=$cursor") ?: return
        if (response.status != 200) return
        val received = parsed {
            val body = parseObject(response.body)
            // El cursor se guarda antes de ejecutar: si la app muere en el medio no repite la orden.
            body.long("cursor")?.let { store.put(KEY_CURSOR, it.toString()) }
            handleStay(body.obj("stay")?.long("number"))
            body.array("commands").mapNotNull(::parseServerCommand)
        } ?: return
        for (item in received) {
            when (item) {
                is ServerCommand.FromPhone -> publish(item.command)
                is ServerCommand.Checkout -> handleStay(item.stayNumber)
            }
        }
    }

    /**
     * Arranca el polling en [scope] hasta que se cancele el Job que devuelve. Las órdenes se piden más
     * seguido que los celulares: la orden es lo que el huésped está esperando mirando la TV.
     */
    fun start(
        scope: CoroutineScope,
        commandsEveryMs: Long = 2_500,
        phonesEveryMs: Long = 3_000,
    ): Job = scope.launch {
        launch { while (isActive) { pollCommands(); delay(commandsEveryMs) } }
        launch { while (isActive) { pollPhones(); delay(phonesEveryMs) } }
    }

    /**
     * [commands] es un StateFlow: dos órdenes iguales seguidas (abrir la guía, salir, abrir la guía)
     * no se emitirían la segunda vez. Por eso cada orden queda un instante y vuelve a null, así la
     * siguiente siempre es un valor nuevo para quien mira.
     */
    private suspend fun publish(item: TvCommand) = commandLock.withLock {
        command.value = item
        delay(commandHoldMs)
        command.value = null
    }

    /**
     * Regla del número de estadía (`stay.rule` de contract/pairing.json): sin número guardado se adopta
     * el actual sin resetear; si el del servidor es MAYOR hubo un check-out (también con la TV apagada).
     */
    @Synchronized
    private fun handleStay(number: Long?) {
        if (number == null) return
        val seen = store.get(KEY_STAY)?.toLongOrNull()
        if (seen != null && number <= seen) return
        store.put(KEY_STAY, number.toString())
        if (seen != null) {
            phones.value = emptyList()
            pending.value = emptyList()
            checkoutEvents.tryEmit(Unit)
        }
    }

    /** El secreto de la TV; si no hay, la registra. null si no se pudo (sin red, 409, hotel desconocido). */
    private suspend fun credential(): String? = registerLock.withLock {
        store.get(KEY_SECRET) ?: register()
    }

    private suspend fun register(): String? {
        val body = buildJsonObject {
            put("hotelId", hotelId)
            put("room", room)
            tvName?.let { put("name", it) }
            activationCode?.let { put("activationCode", it) }
        }
        val response = try {
            transport.execute(HttpRequest("POST", "/api/tv/register/", body = body.toString()))
        } catch (_: IOException) {
            return null
        }
        when (response.status) {
            201 -> {
                val token = parsed { parseObject(response.body).string("token") } ?: return null
                store.put(KEY_SECRET, token)
                registrationState.value = TvRegistration.Registered
                return token
            }
            403 -> registrationState.value = TvRegistration.ActivationRequired
            409 -> registrationState.value = TvRegistration.AlreadyRegistered
            404 -> registrationState.value = TvRegistration.UnknownHotel
        }
        return null
    }

    /** Pedido con el secreto de la TV. null si no hay credencial o no hay red; el 401 la descarta. */
    private suspend fun call(method: String, path: String, body: JsonElement? = null): HttpResponse? {
        val secret = credential() ?: return null
        val response = try {
            transport.execute(HttpRequest(method, path, mapOf("Authorization" to "Tv $secret"), body?.toString()))
        } catch (_: IOException) {
            return null
        }
        if (response.status == 401) {
            // El staff liberó la habitación (o borró la TV): ese secreto ya no existe. El cursor y el
            // número de estadía eran de la TV anterior; si se conservaran se perderían check-outs.
            store.remove(KEY_SECRET)
            store.remove(KEY_CURSOR)
            store.remove(KEY_STAY)
            registrationState.value = TvRegistration.Unknown
            return null
        }
        return response
    }

    /** Lee un cuerpo; si el backend mandó algo que no es lo que dice el contrato, devuelve null. */
    private inline fun <T> parsed(block: () -> T?): T? = try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val KEY_SECRET = "tv.secret"
        const val KEY_CURSOR = "tv.commandCursor"
        const val KEY_STAY = "tv.stayNumber"
    }
}
