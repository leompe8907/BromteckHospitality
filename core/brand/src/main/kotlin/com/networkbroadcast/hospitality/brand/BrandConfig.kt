package com.networkbroadcast.hospitality.brand

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Configuración de una marca, leída de brands/<marca>/brand.json (que el build copia a assets).
 *
 * Reemplaza dos cosas de PanaccessApp: los ~150 buildConfigField por flavor del build.gradle de
 * 3600 líneas, y los if/else por nombre de marca en el código (p. ej. HotelInfoActionRow).
 * Agregar una marca = agregar una carpeta; no se edita Gradle ni código.
 */
@Serializable
data class BrandConfig(
    val id: String,
    val applicationId: String,
    val displayName: String,
    /** RESORT, CINEMATIC o MINIMAL: las 3 direcciones visuales del canvas. */
    val direction: String = "RESORT",
    /** Idiomas que se ofrecen en la selección inicial, en orden. */
    val languages: List<String> = listOf("es", "en", "pt"),
    val defaultLanguage: String = "es",
    /** Ciudad que se muestra en la píldora de la home. */
    val location: String? = null,
    /** true si la marca trae res/drawable/brand_logo; si no, se muestra [displayName] en texto. */
    val showLogo: Boolean = false,
    /** Cómo se conecta al middleware. Equivale a VENDOR/BRANDING/DISCOVERY_* del flavor en PanaccessApp. */
    val panaccess: PanaccessConfig = PanaccessConfig(),
    /**
     * Fondos de la home y la bienvenida (se alternan con un fundido lento). URLs, o archivos de la
     * marca como "asset:fondos/playa1.jpg". Vacío = fondo liso del tema.
     */
    val backgrounds: List<String> = emptyList(),
    /** Ventana de bienvenida al entrar a la habitación. */
    val welcome: WelcomeConfig = WelcomeConfig(),
    val features: Features = Features(),
    val hotel: HotelSource = HotelSource(),
    /** Catálogo de servicios del hotel (pedidos). Se ofrece si además está prendido features.roomService. */
    val services: ServicesSource = ServicesSource(),
    /** Sobrescrituras opcionales de los tokens de color, en hex (#RRGGBB). */
    val colors: Map<String, String> = emptyMap(),
)

/**
 * Qué se ve en esta marca. Cada flag apaga de verdad su entrada en el menú y su pantalla —a
 * diferencia de VOD_ENABLED en PanaccessApp, que existe pero no tiene ningún llamador.
 */
@Serializable
data class Features(
    val liveTv: Boolean = true,
    val vod: Boolean = true,
    val hotelInfo: Boolean = true,
    val languageSelection: Boolean = true,
    val checkout: Boolean = false,
    /** "Conectar celular" en la TV: emparejar la app del celular (core/companion). */
    val companion: Boolean = false,
    /** Guía de programación (EPG) y catchup desde la guía. */
    val guide: Boolean = true,
    /**
     * Servicios del hotel: pedidos desde la TV, el celular y los QR de los camastros (core/services).
     * Sin backend funciona con la versión de prueba, para demos.
     */
    val roomService: Boolean = false,
    // Etapa Tulum: necesitan el backend propio; apagados hasta que exista.
    val housekeeping: Boolean = false,
    val wakeUpCall: Boolean = false,
    val messaging: Boolean = false,
)

/**
 * Datos con los que el DRM de Panaccess encuentra el servidor del operador. Los valores por
 * defecto son los del flavor `royal_hotel` de PanaccessApp (operador Bromteck, cv22).
 */
@Serializable
data class PanaccessConfig(
    val vendor: String = "NetworkBroadcast",
    val branding: String = "Bromteck",
    val discoveryMode: String? = "cvsys",
    val discoveryHint: String? = "intv",
    /**
     * Credenciales de la descarga de la guía (EPG_API_TOKEN / EPG_API_KEY del defaultConfig de
     * PanaccessApp; ninguna marca las cambia). Son del integrador, no del operador.
     */
    val epgApiToken: String = "OMGRUhcoXKFqnpzZEfrF",
    val epgApiKey: String = "724aa4b262071d28844ac2fa85fe7eb198d9cb819c8913f6769b2b48a56a1f61",
)

/**
 * Bienvenida. El nombre del huésped llega de la estadía (backend / PMS); mientras no exista,
 * [demoGuestName] y [demoRoom] permiten mostrarla en demos.
 */
@Serializable
data class WelcomeConfig(
    val enabled: Boolean = true,
    /** Texto por idioma: {"es": "...", "en": "..."}. */
    val message: Map<String, String> = emptyMap(),
    val demoGuestName: String? = null,
    val demoRoom: String? = null,
)

/**
 * Resuelve una imagen de la marca (fondos de [BrandConfig.backgrounds], fotos de hotel_data.json) a algo
 * que Coil sepa cargar: "asset:carpeta/foto.jpg" sale de los assets de la marca; una URL queda igual.
 */
fun imageModel(value: String): String =
    if (value.startsWith("asset:")) "file:///android_asset/" + value.removePrefix("asset:") else value

/** De dónde sale el catálogo de servicios. Con el backend, del panel del hotel. */
@Serializable
data class ServicesSource(
    /** Archivo dentro de assets (formato de core/services: ServiceCatalog). */
    val asset: String? = null,
)

/** De dónde sale el contenido "acerca del hotel". */
@Serializable
data class HotelSource(
    /** Archivo dentro de assets (sin backend). */
    val asset: String? = "hotel_data.json",
    /** URL remota con el mismo formato; si está, tiene prioridad sobre el asset. */
    val url: String? = null,
)

object BrandLoader {
    private val json = Json { ignoreUnknownKeys = true }

    const val ASSET = "brand.json"

    fun parse(text: String): BrandConfig = json.decodeFromString(BrandConfig.serializer(), text)

    fun load(context: Context): BrandConfig =
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
}
