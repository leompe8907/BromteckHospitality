package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCompanionTest {

    @Test
    fun `el codigo es de 6 digitos y vence en 10 minutos`() = runBlocking {
        val code = DemoTvPairing(clock = { 1_000 }).requestCode()
        assertTrue(isValidPairingCode(code.code))
        assertEquals(1_600, code.expiresAtEpochSec)
        assertTrue(code.qrPayload.endsWith(code.code))
    }

    @Test
    fun `codigo invalido no empareja y sin emparejar no se puede mandar nada`() = runBlocking {
        val link = DemoCompanionLink()
        assertEquals(LinkState.Failed(LinkState.Reason.InvalidCode), link.link("12a"))
        assertFalse(link.send(TvCommand.OpenGuide))
        assertTrue(link.link("123456") is LinkState.Linked)
        assertTrue(link.send(TvCommand.OpenGuide))
        link.unlink()
        assertEquals(LinkState.Unlinked, link.state.value)
    }
}
