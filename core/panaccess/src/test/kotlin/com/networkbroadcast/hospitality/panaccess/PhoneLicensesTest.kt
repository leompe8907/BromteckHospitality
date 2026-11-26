package com.networkbroadcast.hospitality.panaccess

import com.panaccess.android.streaming.shared.domain.model.License
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLicensesTest {
    private fun license(name: String?) = License(key = "k-$name", licenseName = name)
    private fun names(licenses: List<License>, room: String) = PhoneLicenses.candidates(licenses, room).map { it.licenseName }

    @Test
    fun `devuelve solo las licencias de celular de esa habitacion y en orden`() {
        val all = listOf("101", "101-m3", "101-m1", "102-m1", "101-m2", "1010-m1", "x101-m1", "101-mx", "101-m").map(::license)
        assertEquals(listOf("101-m1", "101-m2", "101-m3"), names(all, "101"))
        assertEquals(listOf("102-m1"), names(all, "102"))
    }

    @Test
    fun `nunca incluye la licencia de la TV`() {
        assertEquals(emptyList<String?>(), names(listOf(license("101")), "101"))
    }

    @Test
    fun `ordena por numero y no por texto`() {
        val all = listOf("5-m10", "5-m2", "5-m1").map(::license)
        assertEquals(listOf("5-m1", "5-m2", "5-m10"), names(all, "5"))
    }

    @Test
    fun `ignora mayusculas, espacios y nombres vacios o nulos`() {
        val all = listOf(license(" 101-M1 "), license(null), license(""), license("101-m2"))
        assertEquals(listOf(" 101-M1 ", "101-m2"), names(all, " 101 "))
    }

    @Test
    fun `una habitacion con caracteres especiales no rompe la busqueda`() {
        val all = listOf("A.1-m1", "AX1-m1", "(2)-m1").map(::license)
        assertEquals(listOf("A.1-m1"), names(all, "A.1"))
        assertEquals(listOf("(2)-m1"), names(all, "(2)"))
        assertEquals(emptyList<String?>(), names(all, ""))
    }
}
