package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * [CompanionLink] contra el backend de Hospitality (`POST /api/phone/pair/` y el resto de `/api/phone/`).
 * Reglas y forma de los mensajes: `docs/emparejamiento.md` del backend y su `contract/pairing.json`.
 *
 * - El token de la vinculación se guarda en [store] (cifrado en Android) y se manda como
 *   `Authorization: Phone <token>`. Vale hasta el check-out. Al crear el cliente, si hay uno guardado,
 *   el estado arranca en [LinkState.Linked]; [refresh] confirma con el backend que sigue vigente.
 * - Con la confirmación en la TV, el canje deja al celular PENDIENTE: [link] queda en [LinkState.Linking]
 *   y consulta `/api/phone/me/` hasta que alguien lo acepta en la TV, lo rechaza o se vence el plazo
 *   (en esos dos casos, `Failed(Expired)`). El token recién se guarda cuando lo aceptan.
 * - El login de Panaccess del hotel (`session`) llega UNA sola vez, en el canje o en la primera consulta
 *   después de aceptado, y el backend no lo guarda: se conserva en `Linked.tv.login` (cifrado en [store])
 *   hasta que se desvincule. Ver [HotelLogin].
 * - Un 401 en cualquier pedido autenticado (`Unlinked` por check-out o porque la TV lo desvinculó, o
 *   `InvalidCredentials`) borra lo guardado y deja el estado en [LinkState.Unlinked].
 * - No hay "cerrar sesión" contra el backend: [unlink] solo olvida el token acá.
 */
class HttpCompanionLink(
    private val transport: HttpTransport,
    private val store: KeyValueStore,
    /** Cómo se llama este celular en la lista de la TV ("Pixel de Ana"). Opcional. */
    private val deviceName: String? = null,
    /** Cada cuánto pregunta si lo aceptaron en la TV. */
    private val confirmPollMs: Long = 2_000,
    /** Cuánto espera la aceptación (el backend lo descarta a los 120 s). */
    private val confirmTimeoutMs: Long = 130_000,
) : CompanionLink {

    private val _state = MutableStateFlow<LinkState>(restore())
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    override suspend fun link(code: String): LinkState {
        if (!isValidPairingCode(code)) return fail(LinkState.Reason.InvalidCode)
        _state.value = LinkState.Linking
        val body = buildJsonObject {
            put("code", code)
            deviceName?.let { put("deviceName", it) }
        }
        val response = try {
            transport.execute(HttpRequest("POST", "/api/phone/pair/", body = body.toString()))
        } catch (_: IOException) {
            return fail(LinkState.Reason.Network)
        }
        if (response.status != 201) return fail(linkFailureReason(response.status))
        val result: JsonObject
        val token: String
        var tv: LinkedTv
        try {
            result = parseObject(response.body)
            token = result.string("token") ?: return fail(LinkState.Reason.NotAvailable)
            tv = parseLinkedTv(result.obj("tv") ?: return fail(LinkState.Reason.NotAvailable), result["handoff"], result["session"])
        } catch (_: IllegalArgumentException) {
            return fail(LinkState.Reason.NotAvailable)
        }
        if (result.string("status") == "pending") {
            tv = awaitAcceptance(token, tv) ?: return fail(LinkState.Reason.Expired)
        }
        store.put(KEY_TOKEN, token)
        store.put(KEY_TV, tv.toJson().toString())
        return LinkState.Linked(tv).also { _state.value = it }
    }

    /** Consulta hasta que lo aceptan (devuelve la TV, con el login si ya llegó), lo rechazan o se agota el plazo (null). */
    private suspend fun awaitAcceptance(token: String, pairedTv: LinkedTv): LinkedTv? = withTimeoutOrNull(confirmTimeoutMs) {
        var outcome: Acceptance? = null
        while (outcome == null) {
            delay(confirmPollMs)
            outcome = checkAcceptance(token, pairedTv)
        }
        (outcome as? Acceptance.Accepted)?.tv
    }

    private sealed interface Acceptance {
        class Accepted(val tv: LinkedTv) : Acceptance
        data object Rejected : Acceptance
    }

    /** Una consulta de estado: null = todavía pendiente (o sin red / respuesta rara): se sigue esperando. */
    private suspend fun checkAcceptance(token: String, pairedTv: LinkedTv): Acceptance? {
        val response = try {
            transport.execute(HttpRequest("GET", "/api/phone/me/", mapOf("Authorization" to "Phone $token")))
        } catch (_: IOException) {
            return null
        }
        if (response.status == 401) return Acceptance.Rejected // lo rechazaron o se venció en el backend
        if (response.status != 200) return null
        return try {
            val result = parseObject(response.body)
            if (result.string("status") != "active") return null
            Acceptance.Accepted(
                pairedTv.copy(
                    tvName = result.obj("tv")?.string("tvName") ?: pairedTv.tvName,
                    handoff = parseHandoff(result["handoff"]) ?: pairedTv.handoff,
                    login = parseHotelLogin(result["session"]) ?: pairedTv.login,
                ),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun unlink() = forget()

    override suspend fun refresh() {
        val response = authorized("GET", "/api/phone/me/") ?: return
        if (response.status != 200) return
        try {
            val result = parseObject(response.body)
            val current = (_state.value as? LinkState.Linked)?.tv
            val fresh = parseLinkedTv(result.obj("tv") ?: return, result["handoff"], result["session"])
            // El login sale una sola vez: si esta consulta no lo trae, se conserva el que ya teníamos.
            val tv = fresh.copy(login = fresh.login ?: current?.login)
            store.put(KEY_TV, tv.toJson().toString())
            _state.value = LinkState.Linked(tv)
        } catch (_: IllegalArgumentException) {
            // Respuesta rara: se queda con lo que tenía.
        }
    }

    override suspend fun checkOut(): Boolean {
        val response = authorized("POST", "/api/phone/checkout/") ?: return false
        if (response.status != 200) return false
        forget()
        return true
    }

    override suspend fun send(command: TvCommand): Boolean {
        if (_state.value !is LinkState.Linked) return false
        val response = authorized("POST", "/api/phone/commands/", command.toBody().toString()) ?: return false
        return response.status == 202
    }

    /**
     * Pedido con el token de la vinculación. null si no hay vinculación o no hay red. Un 401 es que ya
     * no está vinculado: se olvida todo y también devuelve null.
     */
    private suspend fun authorized(method: String, path: String, body: String? = null): HttpResponse? {
        val token = store.get(KEY_TOKEN) ?: return null
        val response = try {
            transport.execute(HttpRequest(method, path, mapOf("Authorization" to "Phone $token"), body))
        } catch (_: IOException) {
            return null
        }
        if (response.status == 401) {
            forget()
            return null
        }
        return response
    }

    private fun forget() {
        store.remove(KEY_TOKEN)
        store.remove(KEY_TV)
        _state.value = LinkState.Unlinked
    }

    private fun fail(reason: LinkState.Reason): LinkState = LinkState.Failed(reason).also { _state.value = it }

    private fun restore(): LinkState {
        if (store.get(KEY_TOKEN) == null) return LinkState.Unlinked
        return try {
            LinkState.Linked(parseLinkedTv(parseObject(store.get(KEY_TV).orEmpty())))
        } catch (_: IllegalArgumentException) {
            store.remove(KEY_TOKEN)
            store.remove(KEY_TV)
            LinkState.Unlinked
        }
    }

    private companion object {
        const val KEY_TOKEN = "phone.token"
        const val KEY_TV = "phone.tv"
    }
}
