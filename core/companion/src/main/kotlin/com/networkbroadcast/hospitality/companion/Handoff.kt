package com.networkbroadcast.hospitality.companion

/**
 * Lo que el backend le pasa al celular al canjear el código: el tema y los datos del hotel para que
 * la app se vea "del hotel" antes de que haya sesión de Panaccess (después manda `OperatorConfig`).
 * Siempre llegan las mismas claves; `null` = el backend no lo sabe (docs/emparejamiento.md del
 * backend, "Handoff TV → celular"). Colores `#RRGGBB`, URLs `https`.
 *
 * Nunca lleva sesión ni licencia: el backend rechaza esas claves.
 */
data class Handoff(
    val hotelName: String? = null,
    val roomNumber: String? = null,
    val language: String? = null,
    val guestName: String? = null,
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
    val colors: HandoffColors = HandoffColors(),
)

data class HandoffColors(
    val card: String? = null,
    val text: String? = null,
    val playerControl: String? = null,
    val confirm: String? = null,
    val cancel: String? = null,
)
