package com.panaccess.android.streaming.shared.domain.model

/**
 * Decodifica las entidades HTML con las que el CAS manda los nombres.
 *
 * No es un caso raro ni cosmético: se vio `Tony D&#039;Amario` como nombre de grupo y
 * `Café &amp; Bar` en canales. Android lo resuelve pasando todo por `Html.fromHtml`, que en
 * `commonMain` no existe.
 *
 * Cubre las frecuentes. Si aparecen casos raros conviene una librería, no ampliar esta tabla sin
 * fin: `Stream.name` ya trae su propia copia de esta lógica y sería el primer candidato a unificar.
 */
object HtmlEntities {

    fun decode(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
}
