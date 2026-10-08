package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.flow.StateFlow

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

/** Lado TV. */
interface TvPairing {
    /** Pide un código nuevo para mostrar. Si no hay backend, null. */
    suspend fun requestCode(): PairingCode?

    /** Celulares emparejados con esta TV ahora. */
    val pairedPhones: StateFlow<List<PairedPhone>>

    /** Órdenes que llegan desde los celulares (p. ej. "reproducir el canal 4"). */
    val commands: StateFlow<TvCommand?>

    /** Check-out: se desempareja todo. */
    suspend fun unpairAll()
}

/** A qué TV quedó vinculado el celular. */
data class LinkedTv(val roomNumber: String?, val tvName: String)

sealed interface LinkState {
    data object Unlinked : LinkState
    data object Linking : LinkState
    data class Linked(val tv: LinkedTv) : LinkState
    data class Failed(val reason: Reason) : LinkState

    enum class Reason { InvalidCode, Expired, Network, NotAvailable }
}

/** Lado celular. */
interface CompanionLink {
    val state: StateFlow<LinkState>

    /** Empareja con el código que muestra la TV. */
    suspend fun link(code: String): LinkState

    suspend fun unlink()

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
