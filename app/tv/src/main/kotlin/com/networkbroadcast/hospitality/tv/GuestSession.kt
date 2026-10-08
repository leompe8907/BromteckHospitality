package com.networkbroadcast.hospitality.tv

import android.content.Context

/**
 * Lo que la TV recuerda del huésped actual. Hoy el idioma y si ya vio la bienvenida. La habitación sale de la licencia de
 * Panaccess; el nombre llegará del panel o del PMS (deck "Personalización").
 * El check-out lo borra todo, como hacía CheckoutHotelAction en PanaccessApp.
 */
class GuestSession(context: Context) {
    private val prefs = context.getSharedPreferences("guest_session", Context.MODE_PRIVATE)

    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Ya vio la bienvenida en esta estadía. */
    var welcomed: Boolean
        get() = prefs.getBoolean(KEY_WELCOMED, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOMED, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_WELCOMED = "welcomed"
    }
}
