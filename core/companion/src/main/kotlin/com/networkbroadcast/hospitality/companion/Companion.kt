package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Emparejamiento celular ↔ TV de la habitación.
 *
 * Pasa SIEMPRE por el backend propio: el Wi-Fi de hotel casi siempre aísla los dispositivos entre
 * sí, así que el celular no ve a la TV en la red local (ver docs/backend-idea.md, "Emparejamiento").
 * La TV pide un código al backend y lo muestra; el huésped lo escribe (o escanea el QR) en el
 * celular; el backend vincula ese celular a la estadía de la habitación hasta el check-out.
 *
 * Dos lados, dos contratos: [TvPairing] lo usa la app de TV y [CompanionLink] la del celular.
 */

/** Código que la TV muestra para emparejar. */
data class PairingCode(
    /** 6 dígitos. */
    val code: String,
    /** Contenido del QR: el mismo código, como enlace a la app del celular. */
    val qrPayload: String,
    val expiresAtEpochSec: Long,
)

/** Un celular emparejado con esta TV. */
data class PairedPhone(val id: String, val name: String)

internal val NoPendingPhones: StateFlow<List<PairedPhone>> = MutableStateFlow(emptyList<PairedPhone>())

/** Lado TV. */
interface TvPairing {
    /** Pide un código nuevo para mostrar. Si no hay backend, null. */
    suspend fun requestCode(): PairingCode?

    /** Celulares emparejados con esta TV ahora. */
    val pairedPhones: StateFlow<List<PairedPhone>>

    /**
     * Celulares que escanearon el QR y esperan que alguien los acepte con el control remoto ("¿Permitir que
     * [nombre] se conecte?"). Hasta que se aceptan no mandan órdenes ni reciben el login. Sin backend, vacía.
     */
    val pendingPhones: StateFlow<List<PairedPhone>> get() = NoPendingPhones

    /** Acepta un celular de [pendingPhones]. false si ya no está esperando (venció, lo rechazaron o no hay red). */
    suspend fun acceptPhone(id: String): Boolean = false

    /** Rechaza un celular de [pendingPhones] (o desvincula uno ya aceptado). */
    suspend fun rejectPhone(id: String): Boolean = false

    /** Órdenes que llegan desde los celulares (p. ej. "reproducir el canal 4"). */
    val commands: StateFlow<TvCommand?>

    /** Desempareja todos los celulares, sin terminar la estadía. */
    suspend fun unpairAll()

    /**
     * Check-out desde la TV: termina la estadía de la habitación (desempareja todo y le avisa a los
     * celulares). true si el backend lo registró; false si no llegó (sin red): en ese caso los
     * celulares siguen vinculados. La versión de prueba solo desempareja.
     */
    suspend fun checkOut(): Boolean { unpairAll(); return true }

    /**
     * Avisa que terminó la estadía por otro camino (check-out desde el celular o el panel de staff,
     * también si la TV estaba apagada): la TV tiene que borrar la sesión del huésped y volver a
     * elegir idioma, sin tocar la licencia de Panaccess de la TV. No emite por el check-out que hizo
     * ella misma con [checkOut]. Sin backend, nunca emite.
     */
    val checkouts: Flow<Unit> get() = emptyFlow()
}

/**
 * El login de Panaccess del hotel que el backend le da al celular para entrar solo (usuario y md5 de la contraseña,
 * el mismo que arma `PanaccessClient`). Llega UNA sola vez, al quedar aceptado el celular, y el backend no lo guarda:
 * quien lo recibe tiene que usarlo y conservarlo. Nunca se muestra ni se loguea.
 */
data class HotelLogin(val username: String, val passwordMd5: String) {
    override fun toString(): String = "HotelLogin(username=$username, passwordMd5=***)"
}

/** A qué TV quedó vinculado el celular. */
data class LinkedTv(
    val roomNumber: String?,
    val tvName: String,
    /** Tema y datos del hotel que manda el backend al emparejar; null si no hay (versión de prueba). */
    val handoff: Handoff? = null,
    /** Login de Panaccess del hotel para este celular; null si el hotel no tiene uno cargado (control remoto sin video). */
    val login: HotelLogin? = null,
)

sealed interface LinkState {
    data object Unlinked : LinkState

    /** Canjeando el código y, con el backend real, esperando que alguien lo acepte en la TV. */
    data object Linking : LinkState
    data class Linked(val tv: LinkedTv) : LinkState
    data class Failed(val reason: Reason) : LinkState

    /** `Expired` también cubre "nadie lo aceptó en la TV a tiempo" o "lo rechazaron". */
    enum class Reason { InvalidCode, Expired, Network, NotAvailable }
}

/** Lado celular. */
interface CompanionLink {
    val state: StateFlow<LinkState>

    /** Empareja con el código que muestra la TV. */
    suspend fun link(code: String): LinkState

    /** Olvida la vinculación en este celular. Solo local: el backend no tiene un "cerrar sesión" del celular. */
    suspend fun unlink()

    /**
     * Pregunta al backend si la vinculación sigue vigente. Si ya no (check-out, o la TV desvinculó el
     * celular) pasa a [LinkState.Unlinked] y olvida lo guardado. Sin red no cambia nada. Llamar al
     * abrir la app y cada tanto. La versión de prueba no hace nada.
     */
    suspend fun refresh() {}

    /**
     * Check-out desde el celular: termina la estadía de la habitación (desvincula TODOS los celulares,
     * también éste) y la TV vuelve a elegir idioma. true si el backend lo registró.
     */
    suspend fun checkOut(): Boolean { unlink(); return true }

    /** Manda una orden a la TV emparejada. false si no está emparejado o no llegó. */
    suspend fun send(command: TvCommand): Boolean
}

/** Lo que el celular le puede pedir a la TV. Lo ejecuta la TV; el celular no reproduce. */
sealed interface TvCommand {
    data class PlayChannel(val channelId: String) : TvCommand
    data class PlayVod(val vodId: String) : TvCommand
    data object OpenGuide : TvCommand
    data object OpenHotelInfo : TvCommand
}

/** Formato del código: 6 dígitos. Lo validan las dos apps antes de llamar al backend. */
fun isValidPairingCode(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }
