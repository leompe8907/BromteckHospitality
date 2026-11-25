package com.networkbroadcast.hospitality.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.companion.LinkState
import com.networkbroadcast.hospitality.companion.isValidPairingCode
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.hotel.HotelActivity
import com.networkbroadcast.hospitality.hotel.HotelData
import com.networkbroadcast.hospitality.hotel.parseOperatorWifi
import com.networkbroadcast.hospitality.hotel.resolve
import com.networkbroadcast.hospitality.panaccess.HomeWidget
import com.networkbroadcast.hospitality.services.OrderLocation
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServiceItem
import com.networkbroadcast.hospitality.services.ServicesText
import kotlinx.coroutines.launch

/**
 * App del celular: compañera de la TV de la habitación. Muestra el contenido de Panaccess
 * (canales, guía, películas; ver ContentScreens.kt), los widgets de la cuenta, la info del hotel y
 * los servicios, todo desde la home en grilla del mockup (ver HomeScreen.kt).
 *
 * El idioma es el de la cuenta de la TV (X_DEFAULT_LANGUAGE de Panaccess, o el de la marca).
 */
/** Pantallas de la app; se apilan y "atrás" vuelve a la anterior. */
private enum class MobileScreen { Home, Pair, Services, Order, Orders, Content, Widget, Wifi, Hotel }

@Composable
fun MobileApp(
    app: MobileApplication,
    /** Idioma de la cuenta de la TV (ver [accountLanguage]); lo decide [MainActivity]. */
    language: String,
    incomingCode: String?,
    onCodeConsumed: () -> Unit,
    incomingSpot: OrderLocation? = null,
    onSpotConsumed: () -> Unit = {},
) {
    val brand = app.brand
    val text = UiText.of(language)
    val servicesText = ServicesText.of(language)
    val link by app.companion.state.collectAsState()
    val stack = remember { mutableStateListOf(MobileScreen.Home) }
    fun open(screen: MobileScreen) { if (stack.last() != screen) stack.add(screen) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    var hotel by remember { mutableStateOf<HotelData?>(null) }
    LaunchedEffect(Unit) { hotel = app.hotelRepository?.load() }
    LaunchedEffect(incomingCode) { if (incomingCode != null) open(MobileScreen.Pair) }

    // Servicios: el catálogo se lee una vez; la ubicación del QR vale hasta cerrar la app.
    val services = app.guestServices
    val catalog by produceState<ServiceCatalog?>(null, services) { value = services?.catalog() }
    var spot by remember { mutableStateOf<OrderLocation?>(null) }
    var selectedItem by remember { mutableStateOf<ServiceItem?>(null) }
    // Widgets de la cuenta (X_APP_WIDGETS): los mismos y en el mismo orden que la home de la TV.
    val operatorConfig = app.panaccess.operatorConfig
    val widgets = remember(operatorConfig, language) { accountWidgets(operatorConfig, language) }
    var selectedWidget by remember { mutableStateOf<HomeWidget?>(null) }
    LaunchedEffect(incomingSpot) {
        if (incomingSpot != null && services != null) {
            spot = incomingSpot
            stack.retainAll(listOf(MobileScreen.Home))
            open(MobileScreen.Services)
            onSpotConsumed()
        }
    }
    val roomNumber = (link as? LinkState.Linked)?.tv?.roomNumber

    // Contenido de Panaccess (canales, guía, películas): el catálogo se carga una vez para toda la app.
    val content = remember(app) {
        ContentModel(
            app.entertainment,
            vodEnabled = app.brand.features.vod,
            catchupEnabled = app.brand.features.liveTv && app.brand.features.guide,
            resumeStore = PrefsResumeStore(app),
        )
    }
    val contentText = ContentText.of(language)
    var contentTab by remember { mutableStateOf(ContentTab.Live) }
    // La pantalla de contenido usa "atrás" para salir de la pantalla completa o de una serie.
    var contentHandlesBack by remember { mutableStateOf(false) }

    // Qué tiles hay y en qué orden: el parámetro propuesto X_APP_MOBILE_TILES o, sin él, el orden del mockup.
    // Solo se dibuja lo que la marca tiene prendido y lo que la cuenta tiene (ver resolveHomeTiles).
    val wifi = remember(operatorConfig) { parseOperatorWifi(operatorConfig?.wifiRaw) }
    val tiles = remember(operatorConfig, widgets, wifi, hotel, services, brand) {
        val available = buildSet {
            if (brand.features.liveTv) add(TileIds.LIVE)
            if (brand.features.vod) add(TileIds.VOD)
            if (wifi != null) add(TileIds.WIFI)
            if (operatorConfig?.weatherText != null) add(TileIds.WEATHER)
            if (services != null) add(TileIds.SERVICES)
            if (brand.features.hotelInfo && hotel != null) add(TileIds.HOTEL)
            if (widgets.isNotEmpty()) add(TileIds.WIDGETS)
        }
        val config = parseTileConfig(operatorConfig?.param(MOBILE_TILES_PARAM)) ?: DEFAULT_HOME_TILES
        resolveHomeTiles(config, available, widgets.size)
    }
    val hotelName = hotel?.hotelInfo?.name?.resolve(language, brand.defaultLanguage)

    CompositionLocalProvider(LocalBackLabel provides servicesText.back) {
        Box(Modifier.fillMaxSize()) {
            MobileBackdrop(remember(operatorConfig) { parseMobileBackground(operatorConfig?.param(MOBILE_BACKGROUND_PARAM)) })
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (stack.last()) {
                    MobileScreen.Home -> HomeScreen(
                        data = HomeData(
                            tiles = tiles, widgets = widgets, wifi = wifi,
                            weatherTemp = operatorConfig?.weatherTempCelsius, weatherText = operatorConfig?.weatherDescription,
                            hotel = hotel, catalog = catalog,
                            guestName = operatorConfig?.subscriberName, roomNumber = roomNumber,
                            logoUrl = operatorConfig?.hotelLogoUrl,
                        ),
                        model = content, language = language, text = text, contentText = contentText, servicesText = servicesText,
                        hotelName = hotelName,
                        actions = HomeActions(
                            onLive = { contentTab = ContentTab.Live; open(MobileScreen.Content) },
                            onVod = { contentTab = ContentTab.Vod; open(MobileScreen.Content) },
                            onWifi = { open(MobileScreen.Wifi) },
                            onServices = { open(MobileScreen.Services) },
                            onHotel = { open(MobileScreen.Hotel) },
                            onWidget = { selectedWidget = it; open(MobileScreen.Widget) },
                        ),
                    )
                    MobileScreen.Content -> ContentScreen(
                        content, app.entertainment, text, contentText, app.brand.features, contentTab,
                        placeholderUrl = operatorConfig?.placeholderImageUrl,
                        onHandlesBack = { contentHandlesBack = it },
                        onBack = { back() },
                    )
                    MobileScreen.Pair -> if (link is LinkState.Linked) {
                        LaunchedEffect(Unit) { back(); onCodeConsumed() }
                    } else {
                        PairScreen(app, text, initialCode = incomingCode.orEmpty(), onBack = { onCodeConsumed(); back() }, onDone = { back(); onCodeConsumed() })
                    }
                    MobileScreen.Services -> services?.let {
                        MobileServicesScreen(
                            it, catalog, servicesText, language, spot,
                            onOpenItem = { item -> selectedItem = item; open(MobileScreen.Order) },
                            onMyOrders = { open(MobileScreen.Orders) },
                            onBack = { back() },
                        )
                    }
                    MobileScreen.Order -> {
                        val item = selectedItem
                        if (services != null && item != null) {
                            MobileOrderScreen(
                                item, services, catalog, servicesText, language,
                                roomNumber = roomNumber,
                                guestName = brand.welcome.demoGuestName,
                                spot = spot,
                                onConnect = { open(MobileScreen.Pair) },
                                onViewOrders = { stack.removeAt(stack.lastIndex); open(MobileScreen.Orders) },
                                onBack = { back() },
                            )
                        }
                    }
                    MobileScreen.Orders -> services?.let { MobileOrdersScreen(it, catalog, servicesText, language, onBack = { back() }) }
                    MobileScreen.Widget -> selectedWidget?.let { WidgetDetailScreen(it, text, contentText, onBack = { back() }) }
                    MobileScreen.Wifi -> wifi?.let { WifiScreen(it, text, contentText, onBack = { back() }) }
                    MobileScreen.Hotel -> hotel?.let { HotelFicha(it, language, brand.defaultLanguage, text, contentText, hotelName, onBack = { back() }) }
                }
                if (stack.size > 1 && !contentHandlesBack) BackHandler {
                    if (stack.last() == MobileScreen.Pair) onCodeConsumed()
                    back()
                }
            }
        }
    }
}

/** Ficha del hotel (hotel_data.json de la marca): la pantalla de ficha del mockup, con las actividades al final. */
@Composable
private fun HotelFicha(
    data: HotelData, language: String, fallback: String, text: UiText, contentText: ContentText, hotelName: String?, onBack: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    FichaScreen(
        topTitle = text.aboutHotel,
        title = hotelName ?: data.hotelInfo.name.resolve(language, fallback),
        metaLine = null,
        metaIcon = R.drawable.ic_pin,
        paragraphs = data.hotelInfo.description.resolve(language, fallback).split("\n\n"),
        photos = data.images.map { FichaPhoto(it.url, it.title.resolve(language, fallback).ifEmpty { null }, it.description.resolve(language, fallback).ifEmpty { null }) },
        text = contentText,
        onBack = onBack,
    ) {
        data.activities.takeIf { it.isNotEmpty() }?.let { activities ->
            Text(
                text.activities.uppercase(), color = colors.textMuted,
                style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.32.sp),
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activities.forEach { activity ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(rad(16, 2))).background(colors.surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(activity.title.resolve(language, fallback), style = HospitalityTheme.typography.body, color = colors.textPrimary)
                        Text(activityScheduleLabel(activity), style = HospitalityTheme.typography.label, color = colors.textSecondary)
                        activity.location.resolve(language, fallback).takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = HospitalityTheme.typography.label, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Ingresar el código de 6 dígitos que muestra la TV (o llega precargado desde el QR). */
@Composable
private fun PairScreen(app: MobileApplication, text: UiText, initialCode: String, onBack: () -> Unit, onDone: () -> Unit) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initialCode.filter { it.isDigit() }.take(6)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun connect() {
        if (busy) return
        if (!isValidPairingCode(code)) { error = text.invalidCode; return }
        busy = true
        scope.launch {
            val result = app.companion.link(code)
            busy = false
            when (result) {
                is LinkState.Linked -> onDone()
                is LinkState.Failed -> error = if (result.reason == LinkState.Reason.InvalidCode) text.invalidCode else text.linkFailed
                else -> Unit
            }
        }
    }
    // Desde el QR el código ya viene completo: se conecta sin pedir nada más.
    LaunchedEffect(initialCode) { if (isValidPairingCode(code)) connect() }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(text.connectTv, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text.connectHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
                label = { Text(text.code) },
                singleLine = true,
                enabled = !busy,
                textStyle = TextStyle(fontFamily = MobileFontFamily, fontSize = 32.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center, color = colors.textPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { connect() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.focus, unfocusedBorderColor = colors.surfaceBorder,
                    focusedLabelColor = colors.focus, unfocusedLabelColor = colors.textMuted, cursorColor = colors.focus,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.accent) }
            FocusCard(onClick = ::connect, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (busy) "…" else text.connect, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
            if (app.isDemoCompanion) Text(text.demoNote, style = HospitalityTheme.typography.body, color = colors.textMuted)
        }
    }
}

private fun activityScheduleLabel(activity: HotelActivity): String {
    val time = activity.endTime?.let { "${activity.startTime}–$it" } ?: activity.startTime
    return activity.date?.let { "$it · $time" } ?: time
}
