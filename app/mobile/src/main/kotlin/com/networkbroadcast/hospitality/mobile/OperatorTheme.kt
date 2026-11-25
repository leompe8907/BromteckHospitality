package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.panaccess.OperatorConfig

/**
 * Colores del celular: los de la marca, pisados por lo que manda el operador en Panaccess, igual
 * que la TV (X_APP_CARD_COLOR, X_APP_TEXT_COLOR, X_APP_CONFIRM_COLOR como acento). El fondo no sale
 * del operador: es el color liso de la dirección visual de la marca; el fondo con imagen de la TV
 * es horizontal y en el celular queda mal. Lo que el operador no define queda como está.
 */
fun HospitalityColors.withOperator(config: OperatorConfig?): HospitalityColors {
    if (config == null) return this
    val withCard = withCustomCardColor(config.cardColorHex)
    val withText = withCard.withCustomTextColor(config.textColorHex)
    val accent = HospitalityColors.parseHex(config.confirmColorHex) ?: accent
    return withText.copy(background = background, accent = accent)
}
