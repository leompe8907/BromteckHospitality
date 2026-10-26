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

    /**
     * Dónde quedó una película, serie o evento de catchup, para retomarlo donde se dejó. 0 = desde
     * el principio.
     *
     * Vive acá y no en la sesión de Panaccess porque es del huésped, no de la TV: el check-out lo
     * borra junto con el idioma ([clear]), que es lo que corresponde -- el próximo huésped no tiene
     * por qué encontrarse una película empezada por otro.
     *
     * Cuando exista el backend del hotel esto puede pasar a ser remoto (sobreviviría a un cambio de
     * habitación y se podría ver desde el celular), pero el contrato de acá no cambiaría.
     */
    fun resumePositionMs(contentKey: String): Long = prefs.getLong(resumeKey(contentKey), 0L)

    /**
     * Guarda, o borra, según valga la pena retomar:
     * - Sin duración conocida: no se guarda nada (no hay con qué decidir).
     * - Antes de [RESUME_MIN_MS]: recién empezó, retomar ahí no aporta y molesta si fue un vistazo.
     * - En los últimos [RESUME_TAIL_MS]: se considera visto, así que la próxima arranca de cero.
     */
    fun saveResumePosition(contentKey: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val worthResuming = positionMs >= RESUME_MIN_MS && positionMs < durationMs - RESUME_TAIL_MS
        prefs.edit().apply {
            if (worthResuming) putLong(resumeKey(contentKey), positionMs) else remove(resumeKey(contentKey))
        }.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun resumeKey(contentKey: String) = "$KEY_RESUME_PREFIX$contentKey"

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_WELCOMED = "welcomed"
        const val KEY_RESUME_PREFIX = "resume_"

        /** Menos de esto es "apenas lo abrí": no se ofrece retomar. */
        const val RESUME_MIN_MS = 30_000L

        /** Esto antes del final cuenta como terminado (títulos de cierre incluidos). */
        const val RESUME_TAIL_MS = 60_000L
    }
}
