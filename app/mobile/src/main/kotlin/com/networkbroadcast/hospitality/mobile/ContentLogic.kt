package com.networkbroadcast.hospitality.mobile

import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.Program

/** Una fila de la lista de canales: un bouquet del operador, con sus canales en su orden. */
data class ChannelRow(val title: String, val channels: List<Channel>)

/**
 * Los canales agrupados por bouquet, igual que la TV. Sin bouquets, una sola fila con todos
 * ([fallbackTitle]). Los ids que el bouquet nombra y no están entre los canales se descartan, y
 * las filas que quedan vacías no se muestran.
 */
fun ChannelsResult.toRows(fallbackTitle: String): List<ChannelRow> {
    if (groups.isEmpty()) return if (channels.isEmpty()) emptyList() else listOf(ChannelRow(fallbackTitle, channels))
    val byId = channels.associateBy { it.id }
    return groups.mapNotNull { group ->
        val members = group.channelIds.mapNotNull(byId::get)
        if (members.isEmpty()) null else ChannelRow(group.name, members)
    }
}

/** Cuánto va del programa (0..1) a las [nowSec]. */
fun Program.progress(nowSec: Long): Float {
    val total = endEpochSec - startEpochSec
    if (total <= 0) return 0f
    return ((nowSec - startEpochSec).toFloat() / total).coerceIn(0f, 1f)
}

/** El programa al aire y los que siguen, para la tira de la guía. Lo ya emitido no entra. */
fun List<Program>.upcoming(nowSec: Long, limit: Int = 10): List<Program> =
    filter { it.endEpochSec > nowSec }.sortedBy { it.startEpochSec }.take(limit)

/** "1:05:09" / "5:09" para el tiempo de reproducción. */
fun formatClock(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
