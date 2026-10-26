package com.networkbroadcast.hospitality.tv

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.CatchupCatalog
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.companion.TvCommand
import com.networkbroadcast.hospitality.panaccess.HomeWidget
import com.networkbroadcast.hospitality.panaccess.SessionOutcome
import com.networkbroadcast.hospitality.tv.screens.AboutHotelScreen
import com.networkbroadcast.hospitality.tv.screens.CenteredMessage
import com.networkbroadcast.hospitality.tv.screens.CheckoutDialog
import com.networkbroadcast.hospitality.tv.screens.GuideScreen
import com.networkbroadcast.hospitality.tv.screens.HomePreviews
import com.networkbroadcast.hospitality.tv.screens.HomeScreen
import com.networkbroadcast.hospitality.tv.screens.HotelBackdrop
import com.networkbroadcast.hospitality.tv.screens.WelcomeDialog
import com.networkbroadcast.hospitality.entertainment.nowAndNext
import com.networkbroadcast.hospitality.tv.screens.LanguageScreen
import com.networkbroadcast.hospitality.tv.screens.LicenseScreen
import com.networkbroadcast.hospitality.tv.screens.OperatorMessageDialog
import com.networkbroadcast.hospitality.tv.screens.LiveTvScreen
import com.networkbroadcast.hospitality.tv.screens.LoginScreen
import com.networkbroadcast.hospitality.tv.screens.MediaPlayerScreen
import com.networkbroadcast.hospitality.tv.screens.PairPhoneDialog
import com.networkbroadcast.hospitality.tv.screens.PlayerScreen
import com.networkbroadcast.hospitality.tv.screens.VodDetailScreen
import com.networkbroadcast.hospitality.tv.screens.WidgetDetailScreen
import com.networkbroadcast.hospitality.tv.screens.VodScreen
import com.panaccess.android.streaming.shared.domain.model.License
import com.panaccess.android.streaming.shared.domain.model.LoginError
import com.networkbroadcast.hospitality.services.OrderStatus
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServiceItem
import com.networkbroadcast.hospitality.services.ServiceOrder
import com.networkbroadcast.hospitality.services.ServicesText
import com.networkbroadcast.hospitality.services.localized
import com.networkbroadcast.hospitality.tv.screens.HomeServices
import com.networkbroadcast.hospitality.tv.screens.MyOrdersScreen
import com.networkbroadcast.hospitality.tv.screens.OrderStatusBanner
import com.networkbroadcast.hospitality.tv.screens.ServiceOrderDialog
import com.networkbroadcast.hospitality.tv.screens.ServicesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Screen { Home, AboutHotel, LiveTv, Guide, Player, Vod, VodDetail, MediaPlayer, Services, MyOrders, WidgetDetail }

private enum class Auth { Provisioning, Checking, NeedsLogin, ChooseLicense, Ready }

/** Acciones de la home. Cada una existe sólo si su flag está prendido. */
enum class HomeAction { LiveTv, Guide, Vod, CatchUp, Services, MyOrders, AboutHotel, PairPhone, Language, Checkout }

fun BrandConfig.quickActions(showsHotelInfo: Boolean): List<HomeAction> = buildList {
    if (showsHotelInfo) add(HomeAction.AboutHotel)
    if (features.companion) add(HomeAction.PairPhone)
    if (features.languageSelection && languages.size > 1) add(HomeAction.Language)
    if (features.checkout) add(HomeAction.Checkout)
}

/** Lo que se reproduce en el player de catchup/VOD (el de TV en vivo tiene el suyo, con zapping). */
data class MediaRequest(val target: Playable, val title: String)

@Composable
fun HospitalityApp(app: HospitalityApplication, showLogin: Boolean = false) {
    val brand = app.brand
    val panaccess = app.panaccess
    val entertainment = app.entertainment
    val session = remember { GuestSession(app) }
    val scope = rememberCoroutineScope()
    val installerSetup = remember { InstallerSetup(app) }
    val setupFile = remember { installerSetup.read() }

    // Sesión de la TV en Panaccess: si ya está abierta en este proceso, sigue; si se configuró
    // antes, entra sola; si no, la pide una vez.
    var auth by remember {
        mutableStateOf(
            when {
                showLogin -> Auth.NeedsLogin
                // El archivo del instalador gana sobre la sesión guardada (mover de habitación, cambiar de cuenta).
                setupFile != null -> Auth.Provisioning
                panaccess.isOpen -> Auth.Ready
                panaccess.hasSavedLogin -> Auth.Checking
                else -> Auth.NeedsLogin
            }
        )
    }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf<List<License>>(emptyList()) }
    val setupText = UiText.of(brand.defaultLanguage)
    fun describe(outcome: SessionOutcome): String? = when (outcome) {
        SessionOutcome.Ok -> null
        is SessionOutcome.ChooseLicense -> outcome.detail
        is SessionOutcome.Failed -> when (outcome.error) {
            LoginError.InvalidCredentials -> setupText.loginRejected
            LoginError.Network -> setupText.loginTimeout
            else -> setupText.loginDrmError
        } + " (${outcome.detail})"
    }
    fun apply(outcome: SessionOutcome) {
        authError = describe(outcome)
        auth = when (outcome) {
            SessionOutcome.Ok -> Auth.Ready
            is SessionOutcome.ChooseLicense -> { licenses = outcome.licenses; Auth.ChooseLicense }
            is SessionOutcome.Failed -> if (auth == Auth.ChooseLicense) Auth.ChooseLicense else Auth.NeedsLogin
        }
    }
    // Con la sesión guardada, el login no vuelve a aparecer salvo que el servidor rechace el usuario
    // o la contraseña (los cambiaron o dieron de baja la cuenta). Si la TV prende sin internet o el
    // servidor no responde, se queda reintentando en vez de mostrarle el login al huésped.
    var restoreFailed by remember { mutableStateOf(false) }
    LaunchedEffect(auth) {
        // Archivo del instalador: se usa una vez y se borra. Si las credenciales no sirven y la TV ya
        // tenía una sesión, sigue con esa; sin internet, reintenta como al arrancar.
        while (auth == Auth.Provisioning) {
            val setup = setupFile ?: break
            val outcome = panaccess.login(setup.user, setup.passwordMd5, preferredLicenseName = setup.room)
            if (outcome is SessionOutcome.Failed && outcome.error == LoginError.Network) {
                restoreFailed = true
                delay(15_000)
                continue
            }
            installerSetup.delete()
            if (outcome is SessionOutcome.Failed && panaccess.hasSavedLogin) {
                auth = Auth.Checking
            } else {
                apply(outcome)
            }
        }
        while (auth == Auth.Checking) {
            val outcome = panaccess.restore()
            if (outcome is SessionOutcome.Failed && outcome.error != LoginError.InvalidCredentials && panaccess.hasSavedLogin) {
                restoreFailed = true
                delay(15_000)
            } else {
                apply(outcome)
            }
        }
    }

    // Mensajes del operador (OSM): se escuchan con la sesión abierta y se muestran encima de todo.
    val operatorMessage by app.osm.current.collectAsState()
    LaunchedEffect(auth) { if (auth == Auth.Ready) app.osm.start() }

    var language by remember {
        // Sin selección de idioma, se entra directo con el idioma por defecto de la marca.
        mutableStateOf(session.language ?: brand.defaultLanguage.takeUnless { brand.features.languageSelection })
    }
    var choosingLanguage by remember { mutableStateOf(false) }
    var confirmingCheckout by remember { mutableStateOf(false) }
    var showingPairing by remember { mutableStateOf(false) }
    var showingWelcome by remember { mutableStateOf(brand.welcome.enabled && !session.welcomed) }
    val backStack = remember { mutableStateListOf(Screen.Home) }
    var lastHomeAction by remember { mutableStateOf<HomeAction?>(null) }

    // Catálogo: se pide una vez por sesión y lo comparten las pantallas (zapping, guía, VOD).
    var channels by remember { mutableStateOf<ChannelsResult?>(null) }
    var catchups by remember { mutableStateOf<CatchupCatalog?>(null) }
    var vod by remember { mutableStateOf<VodResult?>(null) }
    var homeCatalogReady by remember { mutableStateOf(false) }
    var channelCatalogReady by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var playingChannel by remember { mutableStateOf<String?>(null) }
    var guideChannel by remember { mutableStateOf<String?>(null) }
    var selectedVod by remember { mutableStateOf<VodItem?>(null) }
    var selectedWidget by remember { mutableStateOf<HomeWidget?>(null) }
    var media by remember { mutableStateOf<MediaRequest?>(null) }
    suspend fun warmHomeImages(channelResult: ChannelsResult?, vodResult: VodResult?) {
        val operator = panaccess.operatorConfig
        val backgrounds = operator?.hotelBackgroundUrl?.let { listOf(it) } ?: brand.backgrounds
        val currentLanguage = language ?: brand.defaultLanguage
        val imageUrls = buildList {
            addAll(backgrounds)
            add(brand.backgrounds.firstOrNull())
            add(operator?.hotelLogoUrl)
            add(operator?.welcomeImageUrl)
            addAll(operator?.widgets(currentLanguage).orEmpty().map { it.imageUrl })
            addAll(channelResult?.channels.orEmpty().take(12).map { it.logoUrl })
            addAll(vodResult?.shelves.orEmpty().flatMap { shelf ->
                shelf.items.take(12).map { it.backdropUrl ?: it.posterUrl }
            })
        }
        preloadImages(context, imageUrls)
    }

    // Usa snapshots por licencia para preparar Home, refresca catálogos y espera el EPG inicial
    // antes de mostrarla para que los primeros canales ya estén precargados.
    LaunchedEffect(auth) {
        if (auth != Auth.Ready) return@LaunchedEffect

        val channelSnapshot = channels ?: entertainment.cachedChannels()
        val vodSnapshot = vod ?: if (brand.features.vod) entertainment.cachedVodShelves() else null
        val catchupSnapshot = catchups ?: if (brand.features.guide) entertainment.cachedCatchupCatalog() else null
        channels = channelSnapshot
        if (brand.features.vod) vod = vodSnapshot
        if (brand.features.guide) catchups = catchupSnapshot

        val hasCompleteSnapshot = channelSnapshot != null &&
            (!brand.features.vod || vodSnapshot != null) &&
            (!brand.features.guide || catchupSnapshot != null)
        if (hasCompleteSnapshot) {
            Log.i("Hospitality.Catalog", "precarga: preparando Home con snapshots locales")
            warmHomeImages(channelSnapshot, vodSnapshot)
            // Con snapshot completo (sesión que ya cargó el catálogo antes -- reabrir la app, volver
            // de bloquear la TV, etc.), mostrar Home/TV en vivo de una sin esperar la red: el refresh
            // de abajo actualiza todo en silencio cuando termine. Antes los dos flags se resetaban a
            // false acá arriba sin excepción, así que cada apertura mostraba "Cargando" el tiempo
            // entero del refresh + precarga de EPG aunque el catálogo en memoria ya fuera bueno
            // (inspirado en cómo lo resuelve Bromteckotlin: cache primero, red en silencio después).
            channelCatalogReady = true
            homeCatalogReady = true
        } else {
            channelCatalogReady = false
            homeCatalogReady = false
        }

        // Catch Up usa los Streams/EPG de la respuesta fresca, no alcanza con leer el snapshot
        // de Channel que sirve para dibujar Home. Completar canales primero evita esa carrera.
        val freshChannels = entertainment.refreshChannels()
        val (freshVod, freshCatchups) = coroutineScope {
            val vodRequest = if (brand.features.vod) async { entertainment.refreshVodShelves() } else null
            val catchupRequest = if (brand.features.guide) async { entertainment.catchupCatalog() } else null
            Pair(vodRequest?.await(), catchupRequest?.await())
        }
        channels = freshChannels
        channelCatalogReady = true
        if (brand.features.vod) vod = freshVod ?: VodResult(emptyList())
        if (brand.features.guide) catchups = freshCatchups ?: CatchupCatalog(emptyList(), emptyMap())

        if (!hasCompleteSnapshot) {
            warmHomeImages(freshChannels, freshVod)
        }

        // Calentar logos y la programación inicial antes de mostrar Home: sus primeros canales ya
        // podrán abrir Guía con el EPG leído del cache. Limitar a 12 evita bloquear Home por todo
        // el catálogo; los demás canales se precargan al seleccionarlos y quedan en cache.
        scope.launch { preloadImages(context, freshChannels.channels.map { it.logoUrl }) }

        val initialGuideChannels = freshChannels.channels.take(12)
        Log.i("Hospitality.Catalog", "precarga: esperando guía de ${initialGuideChannels.size} canales iniciales (máx. 5 a la vez)")
        val guideSlots = Semaphore(5)
        coroutineScope {
            initialGuideChannels.map { channel ->
                async {
                    guideSlots.withPermit {
                        runCatching { entertainment.schedule(channel.id) }
                    }
                }
            }
                .forEach { it.await() }
        }
        Log.i("Hospitality.Catalog", "precarga: EPG inicial completo (${initialGuideChannels.size} canales)")
        homeCatalogReady = true
    }
    // Vía rápida para el canal que el huésped tiene enfocado en la Guía en este momento: cupo propio
    // de descargas simultáneas, separado de la precarga general de arriba -- así no tiene que
    // esperar su turno detrás de los otros ~200 canales si la precarga general ya está ocupada.
    // GuideScreen ya pide su propia guía al enfocar (con un debounce de 250ms); esto solo adelanta
    // el pedido un poco más, mientras el huésped todavía está recorriendo la lista con el control.
    val guidePrioritySlots = remember { Semaphore(2) }
    LaunchedEffect(guideChannel) {
        val id = guideChannel ?: return@LaunchedEffect
        guidePrioritySlots.withPermit { runCatching { entertainment.schedule(id) } }
    }
    // Lo que se emite ahora en el canal de la tarjeta "En vivo ahora" (último visto, o el primero).
    val liveChannel = channels?.channels?.let { list -> list.firstOrNull { it.id == playingChannel } ?: list.firstOrNull() }
    var liveProgram by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(liveChannel?.id) {
        liveProgram = liveChannel?.let { entertainment.schedule(it.id).nowAndNext().first?.title }
    }

    fun open(screen: Screen) { backStack.add(screen) }

    // Servicios del hotel (pedidos). Habitación: el nombre de la licencia; en demos, la de la marca.
    val guestServices = app.guestServices
    val roomNumber = panaccess.license?.licenseName ?: brand.welcome.demoRoom ?: "—"
    val serviceCatalog by produceState<ServiceCatalog?>(null, guestServices) { value = guestServices?.catalog() }
    val noOrders = remember { MutableStateFlow<List<ServiceOrder>>(emptyList()) }
    val serviceOrders by (guestServices?.orders ?: noOrders).collectAsState()
    var selectedService by remember { mutableStateOf<ServiceItem?>(null) }
    var lastServiceId by remember { mutableStateOf<String?>(null) }
    // Aviso cuando cambia el estado de un pedido, esté donde esté el huésped (menos al pedir).
    var orderBanner by remember { mutableStateOf<String?>(null) }
    val knownStatuses = remember { mutableMapOf<String, OrderStatus>() }
    LaunchedEffect(serviceOrders) {
        val servicesText = ServicesText.of(language ?: brand.defaultLanguage)
        serviceOrders.forEach { order ->
            val before = knownStatuses[order.id]
            if (before != null && before != order.status && order.status != OrderStatus.Cancelled) {
                orderBanner = servicesText.statusUpdate.format(order.summary, servicesText.status(order.status))
            }
            knownStatuses[order.id] = order.status
        }
    }
    LaunchedEffect(orderBanner) { if (orderBanner != null) { delay(6_000); orderBanner = null } }

    // Órdenes desde el celular emparejado (core/companion). Con la versión de prueba no llega
    // ninguna; con el backend, "abrir la guía" en el celular la abre acá.
    val command by app.tvPairing.commands.collectAsState()
    LaunchedEffect(command) {
        if (auth != Auth.Ready) return@LaunchedEffect
        when (val c = command) {
            is TvCommand.PlayChannel -> { playingChannel = c.channelId; open(Screen.Player) }
            is TvCommand.PlayVod -> { media = MediaRequest(Playable.Vod(c.vodId), ""); open(Screen.MediaPlayer) }
            TvCommand.OpenGuide -> { lastHomeAction = HomeAction.Guide; open(Screen.Guide) }
            TvCommand.OpenHotelInfo -> if (app.showsHotelInfo) open(Screen.AboutHotel)
            null -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(HospitalityTheme.colors.background)) {
        when (auth) {
            // X_APP_SPLASH recién está disponible en operatorConfig después de un login/restore
            // exitoso -- justo lo que todavía no terminó acá. Se usa el último valor que quedó
            // cacheado de una sesión anterior (PanaccessSession.cachedSplashImageUrl); en el primer
            // arranque de la TV no hay nada cacheado y esto no dibuja nada (igual que HotelBackdrop
            // sin fotos).
            Auth.Provisioning -> {
                panaccess.cachedSplashImageUrl?.let { HotelBackdrop(listOf(it)) }
                CenteredMessage(if (restoreFailed) setupText.reconnecting else setupText.provisioning)
                return@Box
            }
            Auth.Checking -> {
                panaccess.cachedSplashImageUrl?.let { HotelBackdrop(listOf(it)) }
                CenteredMessage(if (restoreFailed) setupText.reconnecting else setupText.connecting)
                return@Box
            }
            Auth.NeedsLogin -> {
                LoginScreen(
                    text = setupText,
                    brandName = brand.displayName,
                    backgrounds = brand.backgrounds,
                    // Con --ez showLogin true no se precarga el usuario guardado: así se puede ver el
                    // formulario como lo vería una TV nueva, sin tocar la sesión/licencia real.
                    initialUser = if (showLogin) null else panaccess.username,
                    busy = authBusy,
                    error = authError,
                    onSubmit = { user, password ->
                        authBusy = true
                        scope.launch {
                            val outcome = panaccess.login(user, password)
                            authBusy = false
                            apply(outcome)
                        }
                    },
                )
                return@Box
            }
            Auth.ChooseLicense -> {
                LicenseScreen(setupText, licenses, authBusy, authError) { choice ->
                    authBusy = true
                    scope.launch {
                        val outcome = panaccess.chooseLicense(choice)
                        authBusy = false
                        apply(outcome)
                    }
                }
                BackHandler { auth = Auth.NeedsLogin }
                return@Box
            }
            Auth.Ready -> Unit
        }

        val current = language
        val onHome = current == null || choosingLanguage || backStack.last() == Screen.Home
        // Guía, TV en vivo y Series/películas (que incluye "Eventos anteriores", comparte
        // Screen.Guide) también muestran el fondo del hotel -- a pedido del cliente, para que no
        // se sienta un cambio brusco a un color sólido al salir de la Home.
        val showsHotelBackdrop = onHome || backStack.last() in setOf(Screen.Guide, Screen.LiveTv, Screen.Vod)
        // Delancer: fondo configurable desde el panel de Cableatlántico (X_APP_LOGO_BACKGROUND).
        // Si el hotel no lo cargó, se usa el fondo genérico de la marca de siempre.
        val backgrounds = panaccess.operatorConfig?.hotelBackgroundUrl?.let { listOf(it) } ?: brand.backgrounds
        val logoUrl = panaccess.operatorConfig?.hotelLogoUrl
        // Precarga del fondo/logo apenas se conocen (no bloquea nada: si no llegan a tiempo, el
        // placeholder de HotelBackdrop/LanguageScreen cubre el hueco) — mismo espíritu que la
        // precarga del catálogo de más arriba, para que no se vea el salto de color sólido a foto.
        val welcomeImageUrl = panaccess.operatorConfig?.welcomeImageUrl ?: backgrounds.firstOrNull()
        LaunchedEffect(backgrounds, logoUrl, welcomeImageUrl) {
            val imageLoader = SingletonImageLoader.get(context)
            listOfNotNull(backgrounds.firstOrNull(), logoUrl, welcomeImageUrl).distinct().forEach { url ->
                Log.i("Hospitality.Catalog", "precarga: pidiendo imagen de marca ($url)")
                runCatching { imageLoader.execute(ImageRequest.Builder(context).data(imageModel(url)).build()) }
            }
        }
        // Mientras el catálogo todavía carga se sostiene el mismo splash que venía mostrando la
        // pantalla de conexión, en vez de pasar al fondo común: cambiar de imagen a mitad de la
        // carga se lee como si la app se hubiera reiniciado sola. Recién al abrir Home aparece el
        // fondo del hotel, que ahí sí es un cambio de pantalla y no un parpadeo.
        val splashUrl = panaccess.cachedSplashImageUrl ?: panaccess.operatorConfig?.splashImageUrl
        val holdingSplash = !homeCatalogReady && splashUrl != null
        when {
            holdingSplash -> HotelBackdrop(listOf(splashUrl!!))
            showsHotelBackdrop -> HotelBackdrop(backgrounds, placeholderImageUrl = panaccess.operatorConfig?.placeholderImageUrl)
        }
        if (current == null || choosingLanguage) {
            LanguageScreen(
                brand = brand,
                selected = current,
                onSelected = {
                    session.language = it
                    language = it
                    choosingLanguage = false
                },
                logoUrl = logoUrl,
                // Mismo color que el botón Reproducir: para que se distingan de una card común.
                playerControlColorHex = panaccess.operatorConfig?.playerControlColorHex,
            )
            if (choosingLanguage) BackHandler { choosingLanguage = false }
            return@Box
        }

        val text = UiText.of(current)
        // La home espera a que haya snapshots completos o terminen las cargas iniciales para no
        // insertar/quitar filas cuando VOD o Catch Up responden después.
        if (!homeCatalogReady) { CenteredMessage(text.loading); return@Box }
        val channelList = channels?.channels.orEmpty()
        when (backStack.last()) {
            Screen.Home -> HomeScreen(
                brand = brand,
                text = text,
                logoUrl = logoUrl,
                // Número de habitación: el nombre de la licencia, como MainFragment del base.
                roomNumber = panaccess.license?.licenseName?.takeIf { panaccess.operatorConfig?.showRoomNumber != false },
                // Clima: ya resuelto por Panaccess (ver OperatorConfig.weatherText), null si el hotel no lo tiene configurado.
                weatherText = panaccess.operatorConfig?.weatherText,
                // Nombre del abonado que manda Panaccess en getClientConfig; null si la cuenta no lo trae.
                subscriberName = panaccess.operatorConfig?.subscriberName,
                // Color de las tarjetas configurable por Delancer, null = el de la marca de siempre.
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                // Color de letra (e íconos que acompañan texto) configurable por Delancer, null =
                // el que surge del contraste de cardColorHex, o el de la marca.
                textColorHex = panaccess.operatorConfig?.textColorHex,
                // Segundo logo del hotel: tvModel de la licencia (MainFragment.onLicensesChanged del base).
                secondLogoUrl = panaccess.license?.tvModel?.takeIf { it.startsWith("http") },
                quickActions = brand.quickActions(app.showsHotelInfo),
                // La presencia de catchup se determina con los grupos/eventos reales del CAS,
                // no con el flag catchupDays del catálogo de streams.
                vodHasContent = vod?.shelves?.any { it.items.isNotEmpty() } == true,
                catchUpAvailable = catchups?.channels?.isNotEmpty() == true,
                services = guestServices?.let {
                    val servicesText = ServicesText.of(current)
                    HomeServices(
                        title = servicesText.title,
                        categories = serviceCatalog?.categories?.joinToString(" · ") { it.name.localized(current) }.orEmpty(),
                        orderLabel = servicesText.order,
                        myOrdersLabel = servicesText.myOrders,
                        imageUrl = serviceCatalog?.categories?.flatMap { it.items }?.firstNotNullOfOrNull { it.imageUrl },
                        activeOrder = serviceOrders.firstOrNull { it.status.isOpen }?.let { "${it.summary}: ${servicesText.status(it.status)}" },
                    )
                },
                widgets = panaccess.operatorConfig?.widgets(current).orEmpty(),
                onOpenWidget = { selectedWidget = it; open(Screen.WidgetDetail) },
                previews = HomePreviews(
                    liveLogoUrl = liveChannel?.logoUrl,
                    liveChannelName = liveChannel?.let { "${it.number} · ${it.name}" },
                    liveProgram = liveProgram?.let { "${text.nowLabel}: $it" },
                    guideLogos = channelList.mapNotNull { it.logoUrl },
                    vodBackdropUrl = vod?.shelves?.firstOrNull()?.items?.firstNotNullOfOrNull { it.backdropUrl ?: it.posterUrl },
                ),
                focusOn = lastHomeAction,
                onAction = { action ->
                    lastHomeAction = action
                    when (action) {
                        HomeAction.LiveTv -> open(Screen.LiveTv)
                        HomeAction.Guide, HomeAction.CatchUp -> open(Screen.Guide)
                        HomeAction.Vod -> open(Screen.Vod)
                        HomeAction.Services -> open(Screen.Services)
                        HomeAction.MyOrders -> open(Screen.MyOrders)
                        HomeAction.AboutHotel -> open(Screen.AboutHotel)
                        HomeAction.PairPhone -> showingPairing = true
                        HomeAction.Language -> choosingLanguage = true
                        HomeAction.Checkout -> confirmingCheckout = true
                    }
                },
            )
            Screen.AboutHotel -> AboutHotelScreen(app.hotelRepository(), current, brand.defaultLanguage, text)
            Screen.LiveTv -> if (!channelCatalogReady) CenteredMessage(text.loading) else LiveTvScreen(
                channels = channels,
                source = entertainment,
                text = text,
                focusOn = playingChannel,
                // Mismo color que las tarjetas de la home (OperatorConfig.cardColorHex/textColorHex).
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                textColorHex = panaccess.operatorConfig?.textColorHex,
                onPlay = { playingChannel = it; open(Screen.Player) },
            )
            Screen.Player -> if (!channelCatalogReady) CenteredMessage(text.loading) else PlayerScreen(
                source = entertainment,
                channels = channelList,
                startChannelId = playingChannel,
                text = text,
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                playerControlColorHex = panaccess.operatorConfig?.playerControlColorHex,
                confirmColorHex = panaccess.operatorConfig?.confirmColorHex,
                onChannelChanged = { playingChannel = it },
            )
            Screen.Guide -> when {
                !channelCatalogReady -> CenteredMessage(text.loading)
                lastHomeAction == HomeAction.CatchUp && catchups == null -> CenteredMessage(text.loading)
                else -> GuideScreen(
                    source = entertainment,
                    channels = if (lastHomeAction == HomeAction.CatchUp) catchups?.channels.orEmpty() else channelList,
                    text = text,
                    title = if (lastHomeAction == HomeAction.CatchUp) text.catchUp else text.guide,
                    catchupProgramsByChannel = if (lastHomeAction == HomeAction.CatchUp) catchups?.programsByChannel else null,
                    emptyChannelsMessage = if (lastHomeAction == HomeAction.CatchUp) catchups?.failure?.let { "${text.noChannels} ($it)" } else null,
                    focusChannel = guideChannel,
                    onChannelFocused = { guideChannel = it },
                    onWatchLive = { playingChannel = it; open(Screen.Player) },
                    onWatchCatchup = { program ->
                        program.catchupId?.let { media = MediaRequest(Playable.Catchup(it), program.title); open(Screen.MediaPlayer) }
                    },
                    // Mismo color que las tarjetas de la home (OperatorConfig.cardColorHex/textColorHex).
                    cardColorHex = panaccess.operatorConfig?.cardColorHex,
                    textColorHex = panaccess.operatorConfig?.textColorHex,
                )
            }
            Screen.Vod -> VodScreen(
                vod = vod,
                text = text,
                focusOn = selectedVod?.id,
                placeholderImageUrl = panaccess.operatorConfig?.placeholderImageUrl,
                // Mismo color que las tarjetas de la home (OperatorConfig.cardColorHex/textColorHex).
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                textColorHex = panaccess.operatorConfig?.textColorHex,
                onOpen = { selectedVod = it; open(Screen.VodDetail) },
            )
            Screen.VodDetail -> selectedVod?.let { item ->
                VodDetailScreen(item, text, panaccess.operatorConfig?.playerControlColorHex) {
                    media = MediaRequest(Playable.Vod(item.id), item.title)
                    open(Screen.MediaPlayer)
                }
            }
            Screen.WidgetDetail -> selectedWidget?.let { WidgetDetailScreen(it, text) }
            Screen.MediaPlayer -> media?.let {
                MediaPlayerScreen(
                    entertainment, it, text,
                    playerControlColorHex = panaccess.operatorConfig?.playerControlColorHex,
                    cardColorHex = panaccess.operatorConfig?.cardColorHex,
                    confirmColorHex = panaccess.operatorConfig?.confirmColorHex,
                )
            }
            Screen.Services -> guestServices?.let { services ->
                ServicesScreen(
                    services = services,
                    text = ServicesText.of(current),
                    language = current,
                    focusOn = lastServiceId,
                    onOpenItem = { selectedService = it; lastServiceId = it.id },
                    onMyOrders = { open(Screen.MyOrders) },
                )
            }
            Screen.MyOrders -> guestServices?.let { MyOrdersScreen(it, ServicesText.of(current), current) }
        }
        if (backStack.size > 1) BackHandler { backStack.removeAt(backStack.lastIndex) }

        operatorMessage?.let { message ->
            OperatorMessageDialog(text, message.text) { app.osm.dismiss() }
        }

        if (showingWelcome && backStack.last() == Screen.Home) {
            WelcomeDialog(
                text = text,
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                textColorHex = panaccess.operatorConfig?.textColorHex,
                // Mismo color que "Confirmar" en el checkout: "Comenzar" y el pill del hotel son
                // la única acción de esta bienvenida.
                confirmColorHex = panaccess.operatorConfig?.confirmColorHex,
                // Nombre: el que manda Panaccess (getClientConfig); si la cuenta no lo trae, sin nombre
                // (WelcomeDialog ya tiene un título de respaldo para ese caso) -- nunca el de demo de la
                // marca, para no mostrarle un nombre inventado al huésped real.
                guestName = panaccess.operatorConfig?.subscriberName,
                hotelName = brand.displayName,
                room = panaccess.license?.licenseName ?: brand.welcome.demoRoom,
                message = brand.welcome.message.let { it[current] ?: it[brand.defaultLanguage] ?: it["en"] },
                imageUrl = welcomeImageUrl,
                onDismiss = { session.welcomed = true; showingWelcome = false },
            )
        }

        if (showingPairing) PairPhoneDialog(app.tvPairing, text) { showingPairing = false }

        val service = selectedService
        if (service != null && guestServices != null) {
            ServiceOrderDialog(
                item = service,
                services = guestServices,
                text = ServicesText.of(current),
                language = current,
                roomNumber = roomNumber,
                guestName = panaccess.operatorConfig?.subscriberName,
                onViewOrders = { selectedService = null; open(Screen.MyOrders) },
                onDismiss = { selectedService = null },
            )
        }
        if (selectedService == null) OrderStatusBanner(orderBanner)

        if (confirmingCheckout) {
            CheckoutDialog(
                text = text,
                cardColorHex = panaccess.operatorConfig?.cardColorHex,
                textColorHex = panaccess.operatorConfig?.textColorHex,
                confirmColorHex = panaccess.operatorConfig?.confirmColorHex,
                cancelColorHex = panaccess.operatorConfig?.cancelColorHex,
                onConfirm = {
                    // Check-out del huésped: borra su sesión y desempareja sus celulares. La de
                    // Panaccess (la TV) queda.
                    session.clear()
                    scope.launch { app.tvPairing.unpairAll() }
                    scope.launch { guestServices?.endStay() }
                    knownStatuses.clear()
                    confirmingCheckout = false
                    // Delancer puede cambiar el fondo (X_APP_LOGO_BACKGROUND) entre huéspedes sin
                    // tocar la cuenta de la TV. Se espera el refresh antes de pasar a la pantalla de
                    // idioma -- a pedido del cliente, 2026-10-19: antes se navegaba enseguida y el
                    // fondo nuevo aparecía recién un rato después (en la práctica, mientras el
                    // huésped elegía idioma), no apenas se abría esa pantalla. El logo no se re-pide
                    // todavía -- fuera de alcance por ahora.
                    scope.launch {
                        panaccess.refreshOperatorConfig()
                        lastHomeAction = null
                        showingWelcome = brand.welcome.enabled
                        backStack.retainAll(listOf(Screen.Home))
                        language = if (brand.features.languageSelection) null else brand.defaultLanguage
                    }
                },
                onDismiss = { confirmingCheckout = false },
            )
        }
    }
}
