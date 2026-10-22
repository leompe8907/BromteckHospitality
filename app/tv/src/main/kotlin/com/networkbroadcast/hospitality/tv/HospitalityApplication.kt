package com.networkbroadcast.hospitality.tv

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.BrandLoader
import com.networkbroadcast.hospitality.companion.DemoTvPairing
import com.networkbroadcast.hospitality.companion.TvPairing
import com.networkbroadcast.hospitality.hotel.AssetHotelRepository
import com.networkbroadcast.hospitality.hotel.FallbackHotelRepository
import com.networkbroadcast.hospitality.hotel.HotelRepository
import com.networkbroadcast.hospitality.hotel.LegacyHotelApiRepository
import com.networkbroadcast.hospitality.hotel.UrlHotelRepository
import com.networkbroadcast.hospitality.panaccess.OsmWatcher
import com.networkbroadcast.hospitality.panaccess.PanaccessClient
import com.networkbroadcast.hospitality.panaccess.PanaccessEndpoint
import com.networkbroadcast.hospitality.panaccess.PanaccessEntertainmentSource
import com.networkbroadcast.hospitality.panaccess.PanaccessSession
import com.networkbroadcast.hospitality.services.AssetServiceCatalog
import com.networkbroadcast.hospitality.services.DemoGuestServices
import com.networkbroadcast.hospitality.services.GuestServices
import com.panaccess.android.streaming.shared.epg.EpgApiCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

/**
 * Dueña de lo que tiene que durar lo que dura el proceso: la sesión de Panaccess y el catálogo.
 * Si vivieran en la Activity, cada vez que Android la recrea (rotar, desbloquear) la TV volvía a
 * hacer el login completo con licencia.
 */
class HospitalityApplication : Application(), SingletonImageLoader.Factory {

    /** Persistir imágenes precargadas en disco entre pantallas y reinicios, como Bromteckotlin. */
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("hospitality_image_cache").toOkioPath())
                .maxSizePercent(0.20)
                .build()
        }
        .build()

    val brand: BrandConfig by lazy { BrandLoader.load(this) }

    val panaccess: PanaccessSession by lazy {
        PanaccessSession(
            this,
            PanaccessClient(
                context = this,
                endpoint = brand.panaccess.let { PanaccessEndpoint(it.vendor, it.branding, it.discoveryMode, it.discoveryHint) },
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0",
                debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            ),
        )
    }

    /**
     * Emparejamiento con celulares. Hoy la versión de PRUEBA (inventa el código); cuando exista el
     * backend propio se reemplaza acá por su implementación.
     */
    val tvPairing: TvPairing by lazy { DemoTvPairing() }

    /** Trabajo que tiene que seguir aunque se cierre una pantalla (p. ej. el avance de un pedido de prueba). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Servicios del hotel (pedidos). Hoy la versión de PRUEBA con el catálogo de la marca (el pedido
     * avanza solo); con el backend propio se reemplaza acá. null si la marca no tiene catálogo.
     */
    val guestServices: GuestServices? by lazy {
        brand.services.asset?.takeIf { brand.features.roomService }?.let { asset ->
            val catalog = AssetServiceCatalog(this, asset)
            DemoGuestServices(catalog::load, appScope)
        }
    }

    /** Mensajes en pantalla del operador (OSM). Se arranca con la sesión abierta. */
    val osm: OsmWatcher by lazy { OsmWatcher(this) }

    val entertainment: PanaccessEntertainmentSource by lazy {
        PanaccessEntertainmentSource(this, panaccess, EpgApiCredentials(brand.panaccess.epgApiToken, brand.panaccess.epgApiKey))
    }

    /**
     * De dónde sale "acerca del hotel", en orden: lo que diga el operador (API del sistema viejo,
     * X_HOTEL_APK_BASE_URL + X_HOTEL_APK_BASE_FOLDER), el backend propio por hotel (marca genérica
     * + X_HOTEL_ID del operador), la URL fija de la marca, el archivo de la marca.
     * Se resuelve al abrir la pantalla porque la configuración del operador llega con el login.
     */
    fun hotelRepository(): HotelRepository? {
        val config = panaccess.operatorConfig
        val operatorRepo = config?.hotelBaseUrl?.let { url ->
            config.hotelBaseFolder?.let { folder ->
                LegacyHotelApiRepository(url, folder, name = mapOf(brand.defaultLanguage to brand.displayName))
            }
        }
        val backendRepo = brand.hotel.contentBaseUrl?.let { base ->
            config?.param("X_HOTEL_ID")?.let { hotelId -> UrlHotelRepository("$base/hotels/$hotelId/content.json") }
        }
        val asset = brand.hotel.asset?.let { AssetHotelRepository(this, it) }
        val url = brand.hotel.url?.let { UrlHotelRepository(it) }
        val chain = listOfNotNull(operatorRepo, backendRepo, url, asset)
        return chain.reduceOrNull<HotelRepository, HotelRepository> { acc, next -> FallbackHotelRepository(acc, next) }
    }

    /** "Acerca del hotel" se ofrece si la marca lo prende o si el operador lo configuró. */
    val showsHotelInfo: Boolean
        get() = (brand.features.hotelInfo || panaccess.operatorConfig?.hotelShowAbout == true) && hotelRepository() != null
}
