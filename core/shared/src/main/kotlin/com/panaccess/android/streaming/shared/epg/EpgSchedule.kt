package com.panaccess.android.streaming.shared.epg

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Programa actual y siguiente de un canal. */
data class NowAndNext(
    val current: EpgEvent?,
    val next: EpgEvent?,
)

/**
 * Interpreta los horarios del EPG y deriva "actual / siguiente".
 *
 * **Los horarios del EPG están en UTC**, aunque lleguen como texto `"YYYY-MM-DD HH:MM:SS"` sin
 * indicador de zona. Se verificó de dos formas independientes:
 *
 * - Android los parsea con `SimpleDateFormat` + `TimeZone.getTimeZone("GMT")`
 *   (`EpgEvents.load()`).
 * - Contra el dispositivo: con `ahora = 15:44Z`, el primer evento del feed es `15:27–16:16`, o sea
 *   el que está al aire. Interpretándolos en la zona del operador (`America/Tegucigalpa`, UTC-6)
 *   caían en `21:27Z` y "no había nada en emisión" — seis horas de desfase.
 *
 * Esa fue la primera implementación, y es un error que **no falla, muestra el programa
 * equivocado**: sin el diagnóstico habría llegado a la UI mostrando siempre el programa de dentro
 * de seis horas. La zona del operador (`clientConfig.defaultTimeZone`) sirve para **mostrar**, no
 * para interpretar.
 */
object EpgSchedule {

    /**
     * Convierte `"YYYY-MM-DD HH:MM:SS"` (UTC) a un instante absoluto.
     *
     * Devuelve `null` si el texto no tiene el formato esperado, en vez de lanzar: un evento con
     * fecha corrupta no debería tumbar la guía entera del canal.
     */
    fun parse(dateTime: String): Instant? = runCatching {
        // El CAS usa un espacio como separador; ISO-8601 exige 'T'.
        LocalDateTime.parse(dateTime.trim().replace(' ', 'T')).toInstant(TimeZone.UTC)
    }.getOrNull()

    /**
     * Programa en emisión y el que sigue.
     *
     * Misma condición de "actual" que `EpgEventsDao.getCurrentEventByStreamId` en Android:
     * `start <= ahora <= end`. El "siguiente" es el primero que empieza después de ahora.
     *
     * @param events no hace falta que vengan ordenados: se ordenan acá por instante de inicio.
     */
    /**
     * Sobrecarga sin el instante, para el consumidor Swift: los valores por defecto de Kotlin no
     * cruzan a Swift, así que sin esto habría que construir un `Instant` del lado Swift para algo
     * que es simplemente "ahora".
     */
    fun nowAndNext(events: List<EpgEvent>): NowAndNext = nowAndNext(events, Clock.System.now())

    /** [now] explícito: es lo que hace testeable esta lógica sin depender del reloj real. */
    fun nowAndNext(events: List<EpgEvent>, now: Instant): NowAndNext {
        val timed = events.mapNotNull { event ->
            val start = parse(event.start) ?: return@mapNotNull null
            val end = parse(event.end) ?: return@mapNotNull null
            Triple(event, start, end)
        }.sortedBy { it.second }

        return NowAndNext(
            current = timed.firstOrNull { (_, start, end) -> start <= now && now <= end }?.first,
            next = timed.firstOrNull { (_, start, _) -> start > now }?.first,
        )
    }

    /**
     * Hora de inicio `HH:MM` para mostrar.
     *
     * @param displayTimeZoneId zona en la que mostrar. Acá **sí** aplica
     *   `clientConfig.defaultTimeZone`: el operador define en qué huso se anuncia su grilla. Si el
     *   id no se reconoce se cae a la zona del dispositivo.
     */
    fun displayTime(event: EpgEvent, displayTimeZoneId: String): String? {
        val instant = parse(event.start) ?: return null
        val zone = runCatching { TimeZone.of(displayTimeZoneId) }
            .getOrDefault(TimeZone.currentSystemDefault())
        val local = instant.toLocalDateTime(zone)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    /**
     * Rango `HH:MM – HH:MM` del evento, en la zona indicada. `null` si las fechas no se pueden
     * interpretar.
     */
    fun displayRange(event: EpgEvent, displayTimeZoneId: String): String? {
        val from = displayTime(event, displayTimeZoneId) ?: return null
        val to = parse(event.end)?.let { instant ->
            val zone = runCatching { TimeZone.of(displayTimeZoneId) }
                .getOrDefault(TimeZone.currentSystemDefault())
            val local = instant.toLocalDateTime(zone)
            "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        } ?: return from
        return "$from – $to"
    }

    /**
     * Duración en minutos, o `null` si las fechas no se pueden interpretar.
     *
     * Android la muestra en el detalle del evento como `N min`
     * (`EpgEventInfoFragment`: `event.duration / 60000`).
     */
    fun durationMinutes(event: EpgEvent): Int? {
        val start = parse(event.start) ?: return null
        val end = parse(event.end) ?: return null
        return ((end.toEpochMilliseconds() - start.toEpochMilliseconds()) / 60_000).toInt()
    }

    /**
     * Diagnóstico: cómo se están interpretando los horarios.
     *
     * Existe porque un error de zona acá **no falla, muestra el programa equivocado** — el tipo de
     * bug que se detecta tarde y en producción. Fue lo que destapó que los horarios eran UTC.
     */
    fun explain(events: List<EpgEvent>): String {
        val now = Clock.System.now()
        val first = events.firstOrNull() ?: return "sin eventos"
        val start = parse(first.start)
        val end = parse(first.end)
        return "ahora=$now primero=${first.start}->$start fin=${first.end}->$end " +
            "enEmision=${start != null && end != null && start <= now && now <= end}"
    }
}
