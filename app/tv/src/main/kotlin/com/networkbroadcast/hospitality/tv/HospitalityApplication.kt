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
import com.networkbroadcast.hospitality.companion.SharedPairingCode
import com.networkbroadcast.hospitality.companion.TvPairing
import com.networkbroadcast.hospitality.hotel.AssetHotelRepository
import com.networkbroadcast.hospitality.hotel.FallbackHotelRepository
import com.networkbroadcast.hospitality.hotel.HotelRepository
import com.networkbroadcast.hospitality.hotel.LegacyHotelApiRepository
import com.networkbroadcast.hospitality.hotel.UrlHotelRepository
import com.networkbroadcast.hospitality.hotel.WifiNetwork
import com.networkbroadcast.hospitality.hotel.parseOperatorWifi
import com.networkbroadcast.hospitality.panaccess.OsmWatcher
import com.networkbroadcast.hospitality.panaccess.PanaccessClient
import com.networkbroadcast.hospitality.panaccess.PanaccessEndpoint
import com.networkbroadcast.hospitality.panaccess.PanaccessEntertainmentSource
import com.networkbroadcast.hospitality.panaccess.PanaccessSession
import com.networkbroadcast.hospitality.services.GuestServices
import com.networkbroadcast.hospitality.services.ReadOnlyServices
import com.networkbroadcast.hospitality.services.parseOperatorCatalog
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

    /** El código que muestran a la vez el tile del Lobby y el diálogo: uno solo para toda la TV. */
    val pairingCode: SharedPairingCode by lazy { SharedPairingCode(tvPairing, appScope) }

    /** Trabajo que tiene que seguir aunque se cierre una pantalla (p. ej. el avance de un pedido de prueba). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** El último catálogo del operador ya armado, con el texto del que salió (ver [guestServices]). */
    private var operatorServices: Pair<String, GuestServices?>? = null

    /**
     * Servicios del hotel: solo lo que manda la cuenta de Panaccess (X_APP_SERVICES), para mirar. Sin
     * ese parámetro, null y la TV no muestra la sección. No hay catálogo fijo en la marca ni pedidos de
     * prueba: pedir necesita la conexión con el sistema del hotel (CRM/PMS), que todavía no existe (ver
     * [ReadOnlyServices]); cuando exista, lo reemplaza una implementación que sí reciba pedidos. No es
     * lazy porque la configuración del operador llega con el login.
     */
    val guestServices: GuestServices?
        get() {
            val raw = panaccess.operatorConfig?.param("X_APP_SERVICES") ?: return null
            operatorServices?.takeIf { it.first == raw }?.let { return it.second }
            val built = parseOperatorCatalog(raw)?.let { ReadOnlyServices(it) }
            operatorServices = raw to built
            return built
        }

    /** El último Wi-Fi ya armado, con el texto del que salió (ver [wifi]). */
    private var operatorWifi: Pair<String, WifiNetwork?>? = null

    /**
     * Red Wi-Fi para huéspedes: la que manda la cuenta de Panaccess (X_APP_WIFI, formato en
     * `docs/x-app-wifi.md`), con la que se arma el QR. Sin ese parámetro, o si no sirve (sin nombre de
     * red, o con clave pero sin la clave), null y la TV no muestra nada de Wi-Fi. No es lazy porque la
     * configuración del operador llega con el login.
     */
    val wifi: WifiNetwork?
        get() {
            val raw = panaccess.operatorConfig?.wifiRaw ?: return null
            operatorWifi?.takeIf { it.first == raw }?.let { return it.second }
            val built = parseOperatorWifi(raw)
            operatorWifi = raw to built
            return built
        }

    /** Mensajes en pantalla del operador (OSM). Se arranca con la sesión abierta. */
    val osm: OsmWatcher by lazy { OsmWatcher(this) }

    val entertainment: PanaccessEntertainmentSource by lazy {
        PanaccessEntertainmentSource(
            this,
            panaccess,
            EpgApiCredentials(brand.panaccess.epgApiToken, brand.panaccess.epgApiKey),
            showEmptyBouquetsForPreview = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
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
