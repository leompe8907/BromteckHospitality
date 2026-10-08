package com.panaccess.android.streaming.shared.epg

import kotlinx.datetime.Instant

/**
 * Una celda de la grilla: un programa, o un hueco sin información.
 *
 * Las posiciones son **minutos desde el inicio de la grilla**, no puntos ni píxeles. La conversión
 * a pantalla la hace la UI multiplicando por el ancho de minuto de la plataforma — en Android es
 * `epg_grid_minute_width` (3 dp en teléfono, 7 dp en tablet).
 */
data class EpgSlot(
    val startMinute: Int,
    val endMinute: Int,
    /** `null` en los huecos, que la UI rotula como "sin información". */
    val event: EpgEvent?,
) {
    val widthMinutes: Int get() = endMinute - startMinute
}

/**
 * Arma las filas de la grilla de EPG: qué celda va en qué posición, para cada canal.
 *
 * Vive en `commonMain` por lo mismo que `HomeLayout`: **es regla de negocio, no presentación.**
 * Dónde empieza la grilla, cuánto dura, qué pasa con los eventos que se solapan con el borde y
 * cómo se rellenan los huecos son decisiones que ya tomó Android en `EpgGridAdapter`, y que la
 * app iOS tiene que respetar para que la guía se vea igual. Además es lógica pura: se puede
 * testear sin dispositivo, sin red y sin DRM.
 */
object EpgGrid {

    /** Duración de cada marca de la regla de tiempo. Android dibuja una cada media hora. */
    const val TIMELINE_STEP_MINUTES = 30

    /**
     * Inicio de la grilla: el instante actual **redondeado hacia abajo a la media hora**.
     *
     * Es `roundDownThirtyMinutes()` de `EpgGridAdapter`. Que empiece en "ahora" y no en el
     * principio del día es deliberado del lado Android: la grilla se abre mostrando lo que está
     * al aire.
     */
    fun start(now: Instant): Instant {
        val epochMinutes = now.toEpochMilliseconds() / 60_000
        val floored = epochMinutes - (epochMinutes % TIMELINE_STEP_MINUTES)
        return Instant.fromEpochMilliseconds(floored * 60_000)
    }

    /**
     * Inicio de la **ventana visible**: [start] menos los días de guía hacia atrás.
     *
     * **Acá se toma distancia de Android a propósito.** `EpgGridAdapter` empieza la grilla en
     * "ahora" y suma `(epgDaysBack + epgDaysAhead) * 24 h` hacia adelante, o sea que los días
     * hacia atrás alargan el futuro y **el pasado no se puede ver**. Pero la guía sí trae el
     * pasado (`pastDays` en la URL de descarga) y es lo que hace falta para llegar al catchup
     * desde la grilla, que es donde el usuario lo busca.
     *
     * La grilla abre igual en "ahora", como en Android: lo que cambia es que ahora se puede
     * desplazar hacia atrás en vez de chocarse con el borde.
     */
    fun windowStart(now: Instant, epgDaysBack: Int): Instant =
        start(now).plusHours(-epgDaysBack.coerceAtLeast(0) * 24L)

    /** Fin de la ventana visible: [start] más los días de guía hacia adelante. */
    fun windowEnd(now: Instant, epgDaysAhead: Int): Instant =
        start(now).plusHours(epgDaysAhead.coerceAtLeast(1) * 24L)

    /** Minutos desde el inicio de la grilla. Negativo si [instant] es anterior. */
    fun minutesFrom(start: Instant, instant: Instant): Int =
        ((instant.toEpochMilliseconds() - start.toEpochMilliseconds()) / 60_000).toInt()

    /**
     * Celdas de un canal, en orden.
     *
     * Reglas, todas copiadas de `EpgGridAdapter.addServiceEvents`:
     *
     * - Sólo entran los eventos que se solapan con la ventana `[start, end]`.
     * - Un evento que empezó antes de la grilla se **recorta**: arranca en el minuto 0. Así el
     *   programa que está al aire se ve desde el borde izquierdo en vez de quedar fuera.
     * - Después del último evento se rellena hasta el final con celdas "sin información": la
     *   primera hasta la hora en punto, y de ahí en adelante de una hora. Sin esto la fila
     *   terminaría en el aire y las filas quedarían de distinto largo.
     *
     * Los huecos **entre** eventos no se rellenan, igual que en Android.
     *
     * No recibe "ahora" a propósito: si el resultado dependiera del reloj habría que rehacerlo
     * cada minuto para 120 canales, y cada celda cuesta parsear dos fechas. Cuál es el programa
     * en emisión lo decide la UI comparando minutos, que es una resta.
     */
    fun slots(
        events: List<EpgEvent>,
        start: Instant,
        end: Instant,
    ): List<EpgSlot> {
        val slots = mutableListOf<EpgSlot>()
        var lastEnd = start

        events.mapNotNull { event ->
            val eventStart = EpgSchedule.parse(event.start) ?: return@mapNotNull null
            val eventEnd = EpgSchedule.parse(event.end) ?: return@mapNotNull null
            Triple(event, eventStart, eventEnd)
        }.sortedBy { it.second }.forEach { (event, eventStart, eventEnd) ->
            if (eventEnd <= start || eventStart >= end) return@forEach

            val clampedStart = if (eventStart < start) start else eventStart
            slots += EpgSlot(
                startMinute = minutesFrom(start, clampedStart),
                endMinute = minutesFrom(start, eventEnd),
                event = event,
            )
            if (eventEnd > lastEnd) lastEnd = eventEnd
        }

        var first = true
        while (lastEnd < end) {
            val fillerEnd = if (first) lastEnd.nextHour() else lastEnd.plusHours(1)
            first = false

            slots += EpgSlot(
                startMinute = minutesFrom(start, lastEnd),
                endMinute = minutesFrom(start, fillerEnd),
                event = null,
            )
            lastEnd = fillerEnd
        }

        return slots
    }

    /**
     * Marcas de la regla de tiempo, cada media hora, como instantes.
     *
     * La UI las rotula con la zona del operador (`clientConfig.defaultTimeZone`) usando
     * `EpgSchedule.displayTime`; acá se devuelven como instantes porque la zona es decisión de
     * presentación.
     */
    fun timeline(start: Instant, end: Instant): List<Instant> {
        val total = minutesFrom(start, end)
        if (total <= 0) return emptyList()

        val count = 1 + (total / TIMELINE_STEP_MINUTES)
        return (0 until count).map { i ->
            Instant.fromEpochMilliseconds(
                start.toEpochMilliseconds() + i.toLong() * TIMELINE_STEP_MINUTES * 60_000
            )
        }
    }

    private fun Instant.plusHours(hours: Long): Instant =
        Instant.fromEpochMilliseconds(toEpochMilliseconds() + hours * 60 * 60 * 1000)

    /** La hora en punto siguiente. Si ya está en punto, la de una hora después. */
    private fun Instant.nextHour(): Instant {
        val hourMillis = 60L * 60 * 1000
        val remainder = toEpochMilliseconds() % hourMillis
        val toAdd = if (remainder == 0L) hourMillis else hourMillis - remainder
        return Instant.fromEpochMilliseconds(toEpochMilliseconds() + toAdd)
    }
}
