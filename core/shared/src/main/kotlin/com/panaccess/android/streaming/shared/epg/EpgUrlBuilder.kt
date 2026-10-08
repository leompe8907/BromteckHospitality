package com.panaccess.android.streaming.shared.epg

/**
 * Credenciales de la API de EPG.
 *
 * En Android son `buildConfigField` (`EPG_API_TOKEN` / `EPG_API_KEY`), o sea **por marca**. Acá se
 * pasan como parámetro por lo mismo que [com.panaccess.android.streaming.shared.config.BrandDefaults]:
 * cablearlas haría que todas las marcas usaran las de la primera.
 */
data class EpgApiCredentials(val apiToken: String, val apiKey: String)

/**
 * Hash SHA-256 en hexadecimal.
 *
 * ÚNICO CAMBIO respecto del original: allá es `expect fun` (KMP) con la implementación Android en
 * androidMain/epg/Sha256.android.kt. Este módulo no es multiplataforma, así que va acá el cuerpo
 * de esa implementación, idéntico.
 */
fun sha256Hex(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/**
 * Arma la URL de descarga del EPG.
 *
 * El EPG **no se pide por `callCasFunction`**: es una descarga HTTP directa contra el CDN, con un
 * `accessToken` calculado del lado del cliente. Formato copiado de `EPGDownloader` en Android:
 *
 * ```
 * <cdn>/?pid=guest.home.login&requestMode=download&d=epg&apiToken=<token>
 *      &accessToken=<sha256(apiKey+operator+apiKey+epgStreamId+apiKey)>
 *      &operator=<operator>&epgStreamId=<id>[&pastDays=<n>&oldMode=true]
 * ```
 *
 * La respuesta es un **ZIP** con un único JSON adentro.
 */
object EpgUrlBuilder {

    fun downloadUrl(
        cdnBaseUrl: String,
        credentials: EpgApiCredentials,
        operator: String,
        epgStreamId: Int,
        pastDays: Int,
    ): String {
        val accessToken = accessToken(credentials.apiKey, operator, epgStreamId)

        val base = "$cdnBaseUrl/?pid=guest.home.login&requestMode=download&d=epg" +
            "&apiToken=${credentials.apiToken}" +
            "&accessToken=$accessToken" +
            "&operator=$operator" +
            "&epgStreamId=$epgStreamId"

        // `oldMode=true` va SIEMPRE junto con pastDays, no por separado: así lo hace Android.
        return if (pastDays > 0) "$base&pastDays=$pastDays&oldMode=true" else base
    }

    /** `sha256(apiKey + operator + apiKey + epgStreamId + apiKey)` — literal de Android. */
    fun accessToken(apiKey: String, operator: String, epgStreamId: Int): String =
        sha256Hex("$apiKey$operator$apiKey$epgStreamId$apiKey")
}
