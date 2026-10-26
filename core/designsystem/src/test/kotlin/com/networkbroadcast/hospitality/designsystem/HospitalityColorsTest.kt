package com.networkbroadcast.hospitality.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [HospitalityColors.parseHex] es el único punto por el que entran los 5 parámetros de color que
 * manda el operador (X_APP_CARD_COLOR, X_APP_TEXT_COLOR, X_APP_PLAYER_CONTROL_COLOR,
 * X_APP_CONFIRM_COLOR, X_APP_CANCEL_COLOR): lo que no parsea acá se ignora en toda la app.
 */
class HospitalityColorsTest {

    @Test
    fun `seis digitos, con y sin numeral`() {
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex("#D0D0D0"))
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex("D0D0D0"))
    }

    @Test
    fun `no distingue mayusculas de minusculas`() {
        assertEquals(HospitalityColors.parseHex("#d0d0d0"), HospitalityColors.parseHex("#D0D0D0"))
    }

    @Test
    fun `ocho digitos llevan el alfa adelante`() {
        assertEquals(Color(0xFF163F42), HospitalityColors.parseHex("#FF163F42"))
        assertEquals(Color(0x80163F42), HospitalityColors.parseHex("#80163F42"))
    }

    /** Antes esto devolvía null: la forma corta de CSS es la más probable de escribir a mano. */
    @Test
    fun `forma corta de css`() {
        assertEquals(Color(0xFFDDDDDD), HospitalityColors.parseHex("#DDD"))
        assertEquals(Color(0xFF8899AA), HospitalityColors.parseHex("#89A"))
        assertEquals(Color(0x8899AABB), HospitalityColors.parseHex("#89AB"))
    }

    /**
     * El caso que motivó el cambio: " #D0D0D0" mide 8 caracteres, así que entraba por la rama de
     * 8 dígitos, moría en el toLong(16) y el runCatching lo convertía en "no configurado".
     */
    @Test
    fun `los espacios alrededor no lo rompen`() {
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex(" #D0D0D0"))
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex("  #D0D0D0  "))
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex("D0D0D0\n"))
    }

    @Test
    fun `tolera el prefijo 0x`() {
        assertEquals(Color(0xFFD0D0D0), HospitalityColors.parseHex("0xD0D0D0"))
    }

    @Test
    fun `sin configurar es null`() {
        assertNull(HospitalityColors.parseHex(null))
    }

    @Test
    fun `lo que no es un hex es null`() {
        assertNull(HospitalityColors.parseHex(""))
        assertNull(HospitalityColors.parseHex("   "))
        assertNull(HospitalityColors.parseHex("#"))
        assertNull(HospitalityColors.parseHex("black"))
        assertNull(HospitalityColors.parseHex("negro"))
        assertNull(HospitalityColors.parseHex("rgb(0,0,0)"))
        // Largos que no son 3, 4, 6 ni 8.
        assertNull(HospitalityColors.parseHex("#12345"))
        assertNull(HospitalityColors.parseHex("#1234567"))
        assertNull(HospitalityColors.parseHex("#123456789"))
    }

    /**
     * El negro que tiene cargado hoy Cableatlántico: parsea bien, pero el ícono de adentro del
     * botón sale blanco -- por eso un negro no "desaparece", solo se funde con el panel oscuro.
     */
    @Test
    fun `el color del icono se elige por contraste`() {
        assertEquals(Color.White, HospitalityColors.readableTextColor(Color(0xFF000000)))
        assertEquals(Color.Black, HospitalityColors.readableTextColor(Color(0xFFD0D0D0)))
    }
}
