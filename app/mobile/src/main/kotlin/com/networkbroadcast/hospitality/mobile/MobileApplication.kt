package com.networkbroadcast.hospitality.mobile

import android.app.Application
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.BrandLoader
import com.networkbroadcast.hospitality.companion.CompanionLink
import com.networkbroadcast.hospitality.companion.DemoCompanionLink
import com.networkbroadcast.hospitality.hotel.AssetHotelRepository
import com.networkbroadcast.hospitality.hotel.FallbackHotelRepository
import com.networkbroadcast.hospitality.hotel.HotelRepository
import com.networkbroadcast.hospitality.hotel.UrlHotelRepository
import com.networkbroadcast.hospitality.services.AssetServiceCatalog
import com.networkbroadcast.hospitality.services.DemoGuestServices
import com.networkbroadcast.hospitality.services.GuestServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MobileApplication : Application() {

    val brand: BrandConfig by lazy { BrandLoader.load(this) }

    /**
     * Emparejamiento con la TV. Hoy la versión de PRUEBA (acepta cualquier código de 6 dígitos);
     * cuando exista el backend propio se reemplaza acá por su implementación, sin tocar pantallas.
     */
    // La versión de prueba "conecta" con la habitación de demo de la marca (para poder pedir servicios).
    val companion: CompanionLink by lazy { DemoCompanionLink(roomNumber = brand.welcome.demoRoom) }
    val isDemoCompanion: Boolean get() = companion is DemoCompanionLink

    /** Contenido del hotel: el de la marca. Con el backend, el de la estadía emparejada. */
    val hotelRepository: HotelRepository? by lazy {
        val asset = brand.hotel.asset?.let { AssetHotelRepository(this, it) }
        val url = brand.hotel.url?.let { UrlHotelRepository(it) }
        when {
            url != null && asset != null -> FallbackHotelRepository(url, asset)
            else -> url ?: asset
        }
    }

    /** Trabajo que sigue aunque se cierre una pantalla (el avance de un pedido de prueba). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Servicios del hotel (pedidos). Hoy la versión de PRUEBA con el catálogo de la marca; con el
     * backend propio se reemplaza acá. null si la marca no tiene catálogo o no los ofrece.
     */
    val guestServices: GuestServices? by lazy {
        brand.services.asset?.takeIf { brand.features.roomService }?.let { asset ->
            DemoGuestServices(AssetServiceCatalog(this, asset)::load, appScope)
        }
    }
}
