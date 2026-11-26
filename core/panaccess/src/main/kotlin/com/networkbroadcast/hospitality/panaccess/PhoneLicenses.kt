package com.networkbroadcast.hospitality.panaccess

import com.panaccess.android.streaming.shared.domain.model.License

/**
 * Qué licencias de la cuenta del hotel puede probar un CELULAR de una habitación.
 *
 * SUPUESTO (a confirmar con quien administra Panaccess, ver docs/plan-emparejamiento-celular.md): las cuatro
 * licencias de una habitación se llaman `101` (la de la TV, como hoy), `101-m1`, `101-m2` y `101-m3` (las de los
 * celulares). El celular **solo** prueba las `-mN` de SU habitación, en orden, y nunca la de la TV ni las de otra
 * habitación. Si el nombre cambia, se cambia solo [MOBILE_SUFFIX].
 *
 * Quien use esta lista tiene que habilitarlas con `failIfInUse = true` (no robar la licencia de otro celular): si
 * ninguna está libre, el celular abre sin video y queda como control remoto.
 */
object PhoneLicenses {
    /** `-m` + número: "-m1", "-m2"... (de "móvil"). */
    const val MOBILE_SUFFIX = "-m"

    /** Las licencias de celular de la habitación [room], ordenadas por número (m1, m2, m3). Vacía si no hay ninguna. */
    fun candidates(licenses: List<License>, room: String): List<License> {
        val room = room.trim()
        if (room.isEmpty()) return emptyList()
        val pattern = Regex("^${Regex.escape(room)}${Regex.escape(MOBILE_SUFFIX)}(\\d+)$", RegexOption.IGNORE_CASE)
        return licenses
            .mapNotNull { license ->
                val number = license.licenseName?.trim()?.let { pattern.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()
                number?.let { it to license }
            }
            .sortedBy { it.first }
            .map { it.second }
    }
}
