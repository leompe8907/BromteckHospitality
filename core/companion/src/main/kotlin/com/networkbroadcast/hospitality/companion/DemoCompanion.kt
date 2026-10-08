package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Versiones de PRUEBA, hasta que exista el backend. No conectan nada de verdad: la TV inventa un
 * código y el celular acepta cualquier código de 6 dígitos. Sirven para construir y mostrar las
 * pantallas; se reemplazan por la implementación del backend sin tocar la UI.
 */
class DemoTvPairing(private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) : TvPairing {
    private val phones = MutableStateFlow<List<PairedPhone>>(emptyList())
    private val pending = MutableStateFlow<TvCommand?>(null)

    override suspend fun requestCode(): PairingCode {
        val code = (0 until 6).map { Random.nextInt(10) }.joinToString("")
        return PairingCode(code, "hospitality://pair?code=$code", clock() + 10 * 60)
    }

    override val pairedPhones: StateFlow<List<PairedPhone>> = phones.asStateFlow()
    override val commands: StateFlow<TvCommand?> = pending.asStateFlow()

    override suspend fun unpairAll() { phones.value = emptyList() }
}

class DemoCompanionLink(private val roomNumber: String? = null) : CompanionLink {
    private val _state = MutableStateFlow<LinkState>(LinkState.Unlinked)
    override val state: StateFlow<LinkState> = _state.asStateFlow()

    override suspend fun link(code: String): LinkState {
        if (!isValidPairingCode(code)) return LinkState.Failed(LinkState.Reason.InvalidCode).also { _state.value = it }
        _state.value = LinkState.Linking
        delay(800)
        return LinkState.Linked(LinkedTv(roomNumber, "TV de la habitación")).also { _state.value = it }
    }

    override suspend fun unlink() { _state.value = LinkState.Unlinked }

    override suspend fun send(command: TvCommand): Boolean = _state.value is LinkState.Linked
}
