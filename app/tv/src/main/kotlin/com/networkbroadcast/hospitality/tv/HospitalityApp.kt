package com.networkbroadcast.hospitality.tv

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
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.entertainment.ChannelsResult
import com.networkbroadcast.hospitality.entertainment.Playable
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.VodResult
import com.networkbroadcast.hospitality.companion.TvCommand
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Screen { Home, AboutHotel, LiveTv, Guide, Player, Vod, VodDetail, MediaPlayer, Services, MyOrders }

private enum class Auth { Provisioning, Checking, NeedsLogin, ChooseLicense, Ready }

/** Acciones de la home. Cada una existe sólo si su flag está prendido. */
enum class HomeAction { LiveTv, Guide, Vod, Services, MyOrders, AboutHotel, PairPhone, Language, Checkout }

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
    var vod by remember { mutableStateOf<VodResult?>(null) }
    var playingChannel by remember { mutableStateOf<String?>(null) }
    var guideChannel by remember { mutableStateOf<String?>(null) }
    var selectedVod by remember { mutableStateOf<VodItem?>(null) }
    var media by remember { mutableStateOf<MediaRequest?>(null) }
    LaunchedEffect(auth) {
        if (auth != Auth.Ready) return@LaunchedEffect
        if (channels == null) channels = entertainment.channels()
        if (brand.features.vod && vod == null) vod = entertainment.vodShelves()
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
            TvCommand.OpenGuide -> open(Screen.Guide)
            TvCommand.OpenHotelInfo -> if (app.showsHotelInfo) open(Screen.AboutHotel)
            null -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(HospitalityTheme.colors.background)) {
        when (auth) {
            Auth.Provisioning -> { CenteredMessage(if (restoreFailed) setupText.reconnecting else setupText.provisioning); return@Box }
            Auth.Checking -> { CenteredMessage(if (restoreFailed) setupText.reconnecting else setupText.connecting); return@Box }
            Auth.NeedsLogin -> {
                LoginScreen(
                    text = setupText,
                    brandName = brand.displayName,
                    backgrounds = brand.backgrounds,
                    initialUser = panaccess.username,
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
        if (onHome) HotelBackdrop(brand.backgrounds)
        if (current == null || choosingLanguage) {
            LanguageScreen(
                brand = brand,
                selected = current,
                onSelected = {
                    session.language = it
                    language = it
                    choosingLanguage = false
                },
            )
            if (choosingLanguage) BackHandler { choosingLanguage = false }
            return@Box
        }

        val text = UiText.of(current)
        val channelList = channels?.channels.orEmpty()
        when (backStack.last()) {
            Screen.Home -> HomeScreen(
                brand = brand,
                text = text,
                // Número de habitación: el nombre de la licencia, como MainFragment del base.
                roomNumber = panaccess.license?.licenseName?.takeIf { panaccess.operatorConfig?.showRoomNumber != false },
                // Segundo logo del hotel: tvModel de la licencia (MainFragment.onLicensesChanged del base).
                secondLogoUrl = panaccess.license?.tvModel?.takeIf { it.startsWith("http") },
                quickActions = brand.quickActions(app.showsHotelInfo),
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
                        HomeAction.Guide -> open(Screen.Guide)
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
            Screen.LiveTv -> LiveTvScreen(
                channels = channels,
                text = text,
                focusOn = playingChannel,
                onPlay = { playingChannel = it; open(Screen.Player) },
            )
            Screen.Player -> PlayerScreen(
                source = entertainment,
                channels = channelList,
                startChannelId = playingChannel,
                text = text,
                onChannelChanged = { playingChannel = it },
            )
            Screen.Guide -> GuideScreen(
                source = entertainment,
                channels = channelList,
                text = text,
                focusChannel = guideChannel,
                onChannelFocused = { guideChannel = it },
                onWatchLive = { playingChannel = it; open(Screen.Player) },
                onWatchCatchup = { program ->
                    program.catchupId?.let { media = MediaRequest(Playable.Catchup(it), program.title); open(Screen.MediaPlayer) }
                },
            )
            Screen.Vod -> VodScreen(
                vod = vod,
                text = text,
                focusOn = selectedVod?.id,
                onOpen = { selectedVod = it; open(Screen.VodDetail) },
            )
            Screen.VodDetail -> selectedVod?.let { item ->
                VodDetailScreen(item, text) { media = MediaRequest(Playable.Vod(item.id), item.title); open(Screen.MediaPlayer) }
            }
            Screen.MediaPlayer -> media?.let { MediaPlayerScreen(entertainment, it, text) }
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
                // Nombre y habitación: de la estadía cuando exista el backend; hoy, los de demo de la marca.
                guestName = brand.welcome.demoGuestName,
                hotelName = brand.displayName,
                room = panaccess.license?.licenseName ?: brand.welcome.demoRoom,
                message = brand.welcome.message.let { it[current] ?: it[brand.defaultLanguage] ?: it["en"] },
                imageUrl = brand.backgrounds.firstOrNull(),
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
                guestName = brand.welcome.demoGuestName,
                onViewOrders = { selectedService = null; open(Screen.MyOrders) },
                onDismiss = { selectedService = null },
            )
        }
        if (selectedService == null) OrderStatusBanner(orderBanner)

        if (confirmingCheckout) {
            CheckoutDialog(
                text = text,
                onConfirm = {
                    // Check-out del huésped: borra su sesión y desempareja sus celulares. La de
                    // Panaccess (la TV) queda.
                    session.clear()
                    scope.launch { app.tvPairing.unpairAll() }
                    scope.launch { guestServices?.endStay() }
                    knownStatuses.clear()
                    confirmingCheckout = false
                    lastHomeAction = null
                    showingWelcome = brand.welcome.enabled
                    backStack.retainAll(listOf(Screen.Home))
                    language = if (brand.features.languageSelection) null else brand.defaultLanguage
                },
                onDismiss = { confirmingCheckout = false },
            )
        }
    }
}
