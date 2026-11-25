package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedPairingCodeTest {

    private class CountingPairing : TvPairing {
        val requests = AtomicInteger()
        override suspend fun requestCode() =
            PairingCode("%06d".format(requests.incrementAndGet()), "hospitality://pair?code=x", System.currentTimeMillis() / 1000 + 600)
        override val pairedPhones: StateFlow<List<PairedPhone>> = MutableStateFlow(emptyList())
        override val commands: StateFlow<TvCommand?> = MutableStateFlow(null)
        override suspend fun unpairAll() = Unit
    }

    @Test
    fun `tile y dialogo ven el mismo codigo y se pide uno solo`() = runBlocking {
        val pairing = CountingPairing()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val shared = SharedPairingCode(pairing, scope)

        val tile = scope.launch { shared.code.collect { } }
        val fromTile = shared.code.first { it != null }
        val fromDialog = shared.code.first { it != null }

        assertEquals(fromTile, fromDialog)
        assertEquals(1, pairing.requests.get())
        tile.cancel()
        scope.cancel()
    }
}
