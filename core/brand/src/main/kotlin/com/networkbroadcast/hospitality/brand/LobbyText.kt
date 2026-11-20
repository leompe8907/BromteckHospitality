package com.networkbroadcast.hospitality.brand

/**
 * Textos propios de la dirección "Lobby", aparte de [UiText] (que ya trae lo común: Ahora, Después,
 * Piso, Habitación...). Mismos idiomas: es, en, pt, fr, de. Un idioma sin textos propios cae en
 * inglés, como [UiText.of].
 */
data class LobbyText(
    /** Botón/rótulo "volver" de las pantallas internas. */
    val back: String,
    /** Primera categoría de TV en vivo: todos los canales. */
    val all: String,
    /** Encabezado de la programación de un canal. */
    val today: String,
    /** Saludo del encabezado de la home: "Hola" + nombre. */
    val hello: String,
    /** Junto al QR de "Conectar celular". */
    val scanOrEnter: String,
    /** Ayuda del cartel del reproductor. */
    val zapHint: String,
    /** Pantalla de reposo. */
    val pressAnyKey: String,
) {
    companion object {
        private val es = LobbyText(
            back = "‹ Atrás", all = "Todos", today = "Hoy", hello = "Hola",
            scanOrEnter = "Escanea o ingresa:",
            zapHint = "▲ ▼ cambiar canal · Atrás para salir",
            pressAnyKey = "Cualquier tecla para volver",
        )
        private val en = LobbyText(
            back = "‹ Back", all = "All", today = "Today", hello = "Hello",
            scanOrEnter = "Scan or enter:",
            zapHint = "▲ ▼ change channel · Back to exit",
            pressAnyKey = "Press any key to return",
        )
        private val pt = LobbyText(
            back = "‹ Voltar", all = "Todos", today = "Hoje", hello = "Olá",
            scanOrEnter = "Escaneie ou digite:",
            zapHint = "▲ ▼ trocar de canal · Voltar para sair",
            pressAnyKey = "Qualquer tecla para voltar",
        )
        private val fr = LobbyText(
            back = "‹ Retour", all = "Toutes", today = "Aujourd'hui", hello = "Bonjour",
            scanOrEnter = "Scannez ou saisissez :",
            zapHint = "▲ ▼ changer de chaîne · Retour pour quitter",
            pressAnyKey = "Appuyez sur une touche pour revenir",
        )
        private val de = LobbyText(
            back = "‹ Zurück", all = "Alle", today = "Heute", hello = "Hallo",
            scanOrEnter = "Scannen oder eingeben:",
            zapHint = "▲ ▼ Sender wechseln · Zurück zum Beenden",
            pressAnyKey = "Beliebige Taste zum Zurückkehren",
        )

        private val byLanguage = mapOf("es" to es, "en" to en, "pt" to pt, "fr" to fr, "de" to de)

        fun of(language: String): LobbyText = byLanguage[language] ?: en
    }
}
