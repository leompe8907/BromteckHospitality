package com.networkbroadcast.hospitality.entertainment

/** Datos fijos para construir y probar pantallas sin cuenta de Panaccess. No reproduce nada. */
class FakeEntertainmentSource : EntertainmentSource {

    private val channels = listOf(
        Channel("c1", 1, "Canal del Hotel"),
        Channel("c2", 2, "Noticias 24"),
        Channel("c3", 3, "Deportes HD"),
        Channel("c4", 4, "Cine Clásico"),
    )

    override suspend fun channels() = ChannelsResult(
        channels = channels,
        groups = listOf(ChannelGroup("Todos", channels.map { it.id })),
    )

    override suspend fun schedule(channelId: String): List<Program> {
        val now = System.currentTimeMillis() / 1000
        val name = channels.firstOrNull { it.id == channelId }?.name ?: return emptyList()
        return (-2..3).map { i ->
            val start = now - 1_800 + i * 3_600L
            Program("Programa ${i + 3} de $name", start, start + 3_600)
        }
    }

    override suspend fun vodShelves() = VodResult(
        listOf(
            VodShelf("Películas", listOf(VodItem("v1", "Película 1", year = 2024), VodItem("v2", "Película 2", year = 2023))),
            VodShelf("Series", listOf(VodItem("s1", "Serie 1"), VodItem("s2", "Serie 2"))),
        ),
    )

    override suspend fun playbackUrl(target: Playable): String? = null
}
