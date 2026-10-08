package com.panaccess.android.streaming.shared.epg

/**
 * Caché de guías por canal.
 *
 * Hace falta porque **cada guía es una descarga HTTP con su ZIP**, y son 165 canales: sin caché,
 * abrir la app o volver a una pantalla vuelve a bajar todo. Android persiste los eventos en Room
 * (`EpgDatabase`); acá se guarda el JSON crudo por canal, que es más simple y alcanza — lo que se
 * quiere evitar es la descarga, no el parseo.
 *
 * La política de frescura es la misma que Android:
 * `TIME_BETWEEN_SAME_EPG_REQUEST_MILLIS = 3 horas` en `EpgEventsRemoteDataSource`.
 */
interface EpgCache {

    /**
     * JSON guardado si sigue fresco, o `null` si no hay o venció.
     *
     * [pastDays] forma parte de la identidad de lo guardado, no es un detalle: la guía de un canal
     * con 2 días hacia atrás **no es la misma** que con 1, y la URL de descarga cambia. Sin esto,
     * cambiar `epgDaysBack` seguía sirviendo la guía vieja hasta que venciera.
     */
    fun read(epgStreamId: Int, pastDays: Int, maxAgeMillis: Long): String?

    fun write(epgStreamId: Int, pastDays: Int, json: String)

    /** Borra todo. Para cuando cambia la sesión o la licencia. */
    fun clear()

    companion object {
        /** 3 horas, igual que `TIME_BETWEEN_SAME_EPG_REQUEST_MILLIS` en Android. */
        const val DEFAULT_MAX_AGE_MILLIS: Long = 3 * 60 * 60 * 1000

        /**
         * Descargas simultáneas. Android usa 5 (`EPGDownloader` lo dice explícitamente: "runs up
         * to 5 downloads concurrently"). Más no acelera —el cuello es el CDN— y satura la red del
         * dispositivo.
         */
        const val MAX_CONCURRENT_DOWNLOADS = 5
    }
}
