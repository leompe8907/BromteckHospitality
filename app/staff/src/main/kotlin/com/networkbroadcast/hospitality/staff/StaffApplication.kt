package com.networkbroadcast.hospitality.staff

import android.app.Application
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.BrandLoader
import com.networkbroadcast.hospitality.services.AssetServiceCatalog
import com.networkbroadcast.hospitality.services.DemoStaffServices
import com.networkbroadcast.hospitality.services.StaffServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StaffApplication : Application() {

    val brand: BrandConfig by lazy { BrandLoader.load(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Pedidos del turno. Hoy la versión de PRUEBA (pedidos de ejemplo con el catálogo de la marca);
     * con el backend propio se reemplaza acá por los pedidos reales de los huéspedes.
     */
    val staffServices: StaffServices by lazy {
        val catalog = AssetServiceCatalog(this, brand.services.asset ?: "services.json")
        DemoStaffServices(catalog::load, appScope).also { it.start() }
    }
    val isDemo: Boolean get() = staffServices is DemoStaffServices
}
