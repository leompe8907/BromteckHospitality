package com.networkbroadcast.hospitality.mobile

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * "Seguir viendo": dónde quedó cada película o episodio. Se guarda solo en el celular
 * ([PrefsResumeStore]); nada va a ningún servidor. La lógica (cuándo guardar, cuándo borrar, cómo
 * serializar) es pura y está en este archivo para poder probarla sin Android.
 */
data class ResumeEntry(
    /** Id del contenido en Panaccess (el de `Playable.Vod`). */
    val vodId: String,
    val title: String,
    /** Segunda línea (temporada y episodio), o null en una película. */
    val subtitle: String?,
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Cuándo se guardó (epoch ms): ordena la fila, lo más reciente primero. */
    val savedAtMs: Long,
) {
    /** Cuánto lleva visto, 0..1, para la barra de la tarjeta. */
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

object ResumePolicy {
    /** Menos que esto no vale la pena retomar (y no pisa lo guardado antes). */
    const val MIN_POSITION_MS = 15_000L

    /** Si falta menos que esto, o ya se vio el 95 %, se considera terminada y se borra. */
    const val FINISHED_REMAINING_MS = 90_000L
    const val FINISHED_FRACTION = 0.95f

    /** Al retomar se vuelve un poco atrás para recuperar el hilo. */
    const val REWIND_MS = 3_000L

    const val MAX_ENTRIES = 20

    enum class Action { Save, Clear, Ignore }

    /** Qué hacer con la posición actual de un contenido: guardarla, borrarla (terminó) o dejar todo como está. */
    fun decide(positionMs: Long, durationMs: Long): Action = when {
        durationMs <= 0L || positionMs < 0L -> Action.Ignore
        durationMs - positionMs <= FINISHED_REMAINING_MS || positionMs >= durationMs * FINISHED_FRACTION -> Action.Clear
        positionMs < MIN_POSITION_MS -> Action.Ignore
        else -> Action.Save
    }

    fun startPosition(entry: ResumeEntry): Long = (entry.positionMs - REWIND_MS).coerceAtLeast(0L)
}

/** La lista con [entry] al frente (reemplaza la anterior del mismo contenido) y como mucho [max]. */
fun List<ResumeEntry>.upserted(entry: ResumeEntry, max: Int = ResumePolicy.MAX_ENTRIES): List<ResumeEntry> =
    (listOf(entry) + filterNot { it.vodId == entry.vodId }).sortedByDescending { it.savedAtMs }.take(max)

fun List<ResumeEntry>.without(vodId: String): List<ResumeEntry> = filterNot { it.vodId == vodId }

/** La lista después de ver un rato de [vodId]: guarda el avance, borra si terminó o la deja igual ([ResumePolicy.decide]). */
fun List<ResumeEntry>.afterProgress(
    vodId: String, title: String, subtitle: String?, posterUrl: String?,
    positionMs: Long, durationMs: Long, nowMs: Long,
): List<ResumeEntry> = when (ResumePolicy.decide(positionMs, durationMs)) {
    ResumePolicy.Action.Save -> upserted(ResumeEntry(vodId, title, subtitle, posterUrl, positionMs, durationMs, nowMs))
    ResumePolicy.Action.Clear -> without(vodId)
    ResumePolicy.Action.Ignore -> this
}

/** Una entrada por línea, campos separados por tab y escapados (URL-encoding): sin dependencias de JSON. */
fun List<ResumeEntry>.encode(): String = joinToString("\n") { e ->
    listOf(
        e.vodId, e.title, e.subtitle.orEmpty(), e.posterUrl.orEmpty(),
        e.positionMs.toString(), e.durationMs.toString(), e.savedAtMs.toString(),
    ).joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
}

/** Lo contrario de [encode]; las líneas rotas se saltean en vez de perder todo. */
fun decodeResume(raw: String?): List<ResumeEntry> =
    raw.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split('\t').map { URLDecoder.decode(it, "UTF-8") }
        if (f.size != 7) return@mapNotNull null
        ResumeEntry(
            vodId = f[0].ifEmpty { return@mapNotNull null },
            title = f[1],
            subtitle = f[2].ifEmpty { null },
            posterUrl = f[3].ifEmpty { null },
            positionMs = f[4].toLongOrNull() ?: return@mapNotNull null,
            durationMs = f[5].toLongOrNull() ?: return@mapNotNull null,
            savedAtMs = f[6].toLongOrNull() ?: return@mapNotNull null,
        )
    }.sortedByDescending { it.savedAtMs }

/** Dónde se guarda la lista. */
interface ResumeStore {
    fun load(): List<ResumeEntry>
    fun save(entries: List<ResumeEntry>)
}

/** SharedPreferences privadas de la app. */
class PrefsResumeStore(context: Context) : ResumeStore {
    private val prefs = context.applicationContext.getSharedPreferences("resume", Context.MODE_PRIVATE)
    override fun load(): List<ResumeEntry> = decodeResume(prefs.getString(KEY, null))
    override fun save(entries: List<ResumeEntry>) { prefs.edit().putString(KEY, entries.encode()).apply() }

    private companion object { const val KEY = "entries" }
}
