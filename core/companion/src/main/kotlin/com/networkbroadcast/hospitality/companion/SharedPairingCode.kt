package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Un solo código de emparejamiento para toda la TV: el tile del Lobby y [PairPhoneDialog] lo leen de acá
 * en vez de pedir cada uno el suyo (con el backend real, pedir un segundo código podría invalidar el
 * primero). Mientras haya alguien mirando se renueva solo al vencer; sin nadie mirando se detiene, y al
 * volver a mirar se pide uno nuevo en lugar de mostrar uno que ya venció.
 */
class SharedPairingCode(
    pairing: TvPairing,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /** Código vigente; null hasta que llega el primero o si no hay backend. */
    val code: StateFlow<PairingCode?> = flow {
        while (true) {
            val current = pairing.requestCode()
            emit(current)
            val ttl = current?.let { it.expiresAtEpochSec - clock() } ?: RETRY_SECONDS
            delay(ttl.coerceAtLeast(MIN_REFRESH_SECONDS) * 1000)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), null)

    private companion object {
        const val RETRY_SECONDS = 60L
        const val MIN_REFRESH_SECONDS = 30L
    }
}
