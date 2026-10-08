package com.networkbroadcast.hospitality.entertainment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramTest {
    private val schedule = listOf(
        Program("A", 0, 100),
        Program("B", 100, 200),
        Program("C", 200, 300),
    )

    @Test
    fun `ahora y despues`() {
        val (now, next) = schedule.nowAndNext(150)
        assertEquals("B", now?.title)
        assertEquals("C", next?.title)
    }

    @Test
    fun `el fin es exclusivo y despues del ultimo no hay siguiente`() {
        assertEquals("C", schedule.nowAndNext(200).first?.title)
        val (now, next) = schedule.nowAndNext(300)
        assertNull(now)
        assertNull(next)
    }
}
