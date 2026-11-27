package com.networkbroadcast.hospitality.mobile

import android.app.Application
import android.content.pm.ApplicationInfo
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.BrandLoader
import com.networkbroadcast.hospitality.companion.CompanionLink
import com.networkbroadcast.hospitality.companion.DemoCompanionLink
import com.networkbroadcast.hospitality.hotel.AssetHotelRepository
import com.networkbroadcast.hospitality.hotel.FallbackHotelRepository
import com.networkbroadcast.hospitality.hotel.HotelRepository
import com.networkbroadcast.hospitality.hotel.UrlHotelRepository
import com.networkbroadcast.hospitality.panaccess.PanaccessClient
import com.networkbroadcast.hospitality.panaccess.PanaccessEndpoint
import com.networkbroadcast.hospitality.panaccess.PanaccessEntertainmentSource
import com.networkbroadcast.hospitality.panaccess.PanaccessSession
import com.networkbroadcast.hospitality.panaccess.SessionOutcome
import com.panaccess.android.streaming.shared.epg.EpgApiCredentials
import com.networkbroadcast.hospitality.services.AssetServiceCatalog
import com.networkbroadcast.hospitality.services.DemoGuestServices
import com.networkbroadcast.hospitality.services.GuestServices
import com.networkbroadcast.hospitality.companion.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la sesión de Panaccess del celular. Sin sesión se muestra la pantalla neutra (escanear el QR). */
sealed interface MobileSession {
    data object Checking : MobileSession
    data object NoSession : MobileSession
    data object Ready : MobileSession
    data class Failed(val detail: String) : MobileSession
}

class MobileApplication : Application() {

    val brand: BrandConfig by lazy { BrandLoader.load(this) }

    val isDebuggable: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Sesión de Panaccess propia del celular, para ver el contenido. Cómo la recibe del QR de la TV
     * todavía no está definido (ver docs/emparejamiento.md, "Decisiones abiertas"): mientras tanto
     * solo se abre con [debugLogin], que existe únicamente en builds de depuración.
     */
    val panaccess: PanaccessSession by lazy {
        PanaccessSession(
            this,
            PanaccessClient(
                context = this,
                endpoint = brand.panaccess.let { PanaccessEndpoint(it.vendor, it.branding, it.discoveryMode, it.discoveryHint) },
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0",
                debug = isDebuggable,
            ),
        )
    }

    val entertainment: PanaccessEntertainmentSource by lazy {
        PanaccessEntertainmentSource(
            this,
            panaccess,
            EpgApiCredentials(brand.panaccess.epgApiToken, brand.panaccess.epgApiKey),
            showEmptyBouquetsForPreview = isDebuggable,
        )
    }

    private val _session = MutableStateFlow<MobileSession>(MobileSession.Checking)
    val session: StateFlow<MobileSession> = _session.asStateFlow()

    /** Al abrir la app: si ya había una sesión guardada se retoma, si no queda la pantalla neutra. */
    fun restoreSession() {
        bindCompanionSession()
        // Antes de usar el login guardado se pregunta si la vinculación sigue vigente (si hubo check-out, la desvincula).
        appScope.launch { companion.refresh() }
        if (_session.value !is MobileSession.Checking) return
        appScope.launch {
            _session.value = if (panaccess.isOpen) MobileSession.Ready
            else if (panaccess.hasSavedLogin) panaccess.restore().toState()
            else MobileSession.NoSession
        }
    }

    private var companionBridge: Job? = null

    /**
     * Une el emparejamiento con la sesión de Panaccess. Idempotente.
     * - Al quedar vinculado con un login del hotel (`Linked.tv.login`, que el backend manda UNA sola vez), el celular entra solo a
     *   Panaccess con una licencia de celular de su habitación, sin robarle ninguna a otro. Sin licencia libre queda sin video
     *   ([MobileSession.Failed]) pero sigue siendo control remoto. Si ya hay una sesión guardada, [restoreSession] se ocupa.
     * - Al pasar a desvinculado (check-out, o la TV lo desvinculó) cierra la sesión de Panaccess, borra el login y vuelve a la
     *   pantalla neutra (escanear el QR de la TV).
     */
    fun bindCompanionSession() {
        if (companionBridge != null) return
        companionBridge = appScope.launch {
            var wasLinked = false
            companion.state.collect { link ->
                when (link) {
                    is LinkState.Linked -> {
                        wasLinked = true
                        val login = link.tv.login
                        val room = link.tv.roomNumber
                        if (login != null && room != null && !panaccess.isOpen && !panaccess.hasSavedLogin) {
                            _session.value = MobileSession.Checking
                            _session.value = panaccess.loginForPhone(login.username, login.passwordMd5, room).toState()
                        }
                    }
                    LinkState.Unlinked -> if (wasLinked) {
                        wasLinked = false
                        // Solo la sesión que abrió el emparejamiento: una de depuración (debugLogin) no se toca.
                        if (panaccess.isPhoneSession) {
                            panaccess.close()
                            _session.value = MobileSession.NoSession
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Check-out desde el celular: termina la estadía de la habitación (el backend desvincula todos los celulares y la
     * TV vuelve a elegir idioma), cierra la sesión de Panaccess y vuelve a la pantalla neutra. false si no llegó al
     * backend: entonces no se toca nada.
     */
    suspend fun checkOut(): Boolean {
        if (!companion.checkOut()) return false
        panaccess.close()
        _session.value = MobileSession.NoSession
        return true
    }

    /** Solo depuración: entra con una cuenta de prueba y la licencia de [room] (nombre de licencia). */
    fun debugLogin(user: String, password: String, room: String?) {
        check(isDebuggable) { "debugLogin solo existe en builds de depuración" }
        _session.value = MobileSession.Checking
        appScope.launch {
            _session.value = panaccess.login(user, password, preferredLicenseName = room?.takeIf { it.isNotBlank() }).toState()
        }
    }

    private fun SessionOutcome.toState(): MobileSession = when (this) {
        SessionOutcome.Ok -> MobileSession.Ready
        is SessionOutcome.Failed -> MobileSession.Failed(detail)
        is SessionOutcome.ChooseLicense -> MobileSession.Failed(detail)
    }

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
