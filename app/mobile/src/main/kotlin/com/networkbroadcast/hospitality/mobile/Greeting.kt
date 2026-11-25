package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.brand.UiText

/**
 * Saludo de la home según la hora del dispositivo, con los mismos tramos y textos que la TV: de 5 a 11
 * "Buenos días", de 12 a 19 "Buenas tardes" y el resto "Buenas noches". Con nombre del huésped,
 * "Buenos días, Mario Bromteck".
 */
fun greetingFor(text: UiText, hour: Int, name: String?): String {
    val greeting = when (hour) {
        in 5..11 -> text.goodMorning
        in 12..19 -> text.goodAfternoon
        else -> text.goodEvening
    }
    return if (name.isNullOrBlank()) greeting else "$greeting, ${name.trim()}"
}
