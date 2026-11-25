package com.networkbroadcast.hospitality.mobile

/**
 * Textos del contenido del celular que `UiText` (core:brand) no trae: "Seguir viendo", temporadas y
 * episodios, pantalla completa. Mismos cinco idiomas que el resto; lo que no se conoce cae a inglés.
 */
data class ContentText(
    val continueWatching: String,
    private val season: String,
    private val seasonShort: String,
    private val episodeShort: String,
    val noEpisodes: String,
    val fullscreen: String,
    val exitFullscreen: String,
    val readMore: String,
    val readLess: String,
    /** Rótulos cortos de los tiles chicos de la home (como "Canales", "Películas" del mockup). */
    val tileChannels: String,
    val tileMovies: String,
    val tileWifi: String,
    val tileWeather: String,
    /** Título de los tiles de TV en vivo medianos y anchos ("Ahora en TV"). */
    val nowOnTv: String,
    val copy: String,
    val copied: String,
    /** "EN VIVO" del badge del player. */
    val liveBadge: String,
    /** Rótulo de la película destacada de la pestaña de películas. */
    val featured: String,
    /** Chip que muestra todas las estanterías de películas. */
    val all: String,
) {
    fun seasonLabel(number: Int) = "$season $number"

    /** "T1 · E2" (o "S1 · E2"); sin temporada informada, solo el episodio. */
    fun episodeLabel(seasonNumber: Int, episodeNumber: Int): String = buildString {
        if (seasonNumber > 0) append("$seasonShort$seasonNumber")
        if (episodeNumber > 0) { if (isNotEmpty()) append(" · "); append("$episodeShort$episodeNumber") }
    }

    companion object {
        fun of(language: String): ContentText = when (language) {
            "es" -> ContentText("Seguir viendo", "Temporada", "T", "E", "No hay episodios disponibles.", "Pantalla completa", "Salir de pantalla completa", "Leer más", "Leer menos", "Canales", "Películas", "Wi‑Fi", "Clima", "Ahora en TV", "Copiar", "Copiado", "EN VIVO", "Destacada", "Todo")
            "pt" -> ContentText("Continuar assistindo", "Temporada", "T", "E", "Não há episódios disponíveis.", "Tela cheia", "Sair da tela cheia", "Ler mais", "Ler menos", "Canais", "Filmes", "Wi‑Fi", "Clima", "Agora na TV", "Copiar", "Copiado", "AO VIVO", "Destaque", "Tudo")
            "fr" -> ContentText("Reprendre", "Saison", "S", "É", "Aucun épisode disponible.", "Plein écran", "Quitter le plein écran", "Lire la suite", "Lire moins", "Chaînes", "Films", "Wi‑Fi", "Météo", "En ce moment à la TV", "Copier", "Copié", "EN DIRECT", "À l'affiche", "Tout")
            "de" -> ContentText("Weiterschauen", "Staffel", "S", "F", "Keine Folgen verfügbar.", "Vollbild", "Vollbild beenden", "Mehr lesen", "Weniger lesen", "Sender", "Filme", "WLAN", "Wetter", "Jetzt im TV", "Kopieren", "Kopiert", "LIVE", "Empfohlen", "Alle")
            else -> ContentText("Continue watching", "Season", "S", "E", "No episodes available.", "Fullscreen", "Exit fullscreen", "Read more", "Read less", "Channels", "Movies", "Wi‑Fi", "Weather", "Now on TV", "Copy", "Copied", "LIVE", "Featured", "All")
        }
    }
}
