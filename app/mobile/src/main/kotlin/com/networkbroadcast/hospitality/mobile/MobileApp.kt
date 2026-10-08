package com.networkbroadcast.hospitality.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.companion.LinkState
import com.networkbroadcast.hospitality.companion.TvCommand
import com.networkbroadcast.hospitality.companion.isValidPairingCode
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.hotel.HotelData
import com.networkbroadcast.hospitality.hotel.resolve
import com.networkbroadcast.hospitality.services.OrderLocation
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServiceItem
import com.networkbroadcast.hospitality.services.ServicesText
import com.networkbroadcast.hospitality.services.localized
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * App del celular: compañera de la TV de la habitación. No reproduce nada ni usa licencias de
 * Panaccess: muestra la info del hotel y le manda órdenes a la TV emparejada.
 *
 * El idioma es el del celular (es el del huésped), a diferencia de la TV que lo pregunta.
 */
/** Pantallas de la app; se apilan y "atrás" vuelve a la anterior. */
private enum class MobileScreen { Home, Pair, Services, Order, Orders }

@Composable
fun MobileApp(
    app: MobileApplication,
    incomingCode: String?,
    onCodeConsumed: () -> Unit,
    incomingSpot: OrderLocation? = null,
    onSpotConsumed: () -> Unit = {},
) {
    val brand = app.brand
    val language = Locale.getDefault().language.takeIf { it in brand.languages } ?: brand.defaultLanguage
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
    LaunchedEffect(incomingSpot) {
        if (incomingSpot != null && services != null) {
            spot = incomingSpot
            stack.retainAll(listOf(MobileScreen.Home))
            open(MobileScreen.Services)
            onSpotConsumed()
        }
    }
    val roomNumber = (link as? LinkState.Linked)?.tv?.roomNumber

    Box(Modifier.fillMaxSize().background(HospitalityTheme.colors.background).systemBarsPadding()) {
        when (stack.last()) {
            MobileScreen.Home -> HomeScreen(
                app, text, servicesText, language, link, hotel, catalog,
                onPair = { open(MobileScreen.Pair) },
                onServices = { open(MobileScreen.Services) },
                onOrders = { open(MobileScreen.Orders) },
            )
            MobileScreen.Pair -> if (link is LinkState.Linked) {
                LaunchedEffect(Unit) { back(); onCodeConsumed() }
            } else {
                PairScreen(app, text, initialCode = incomingCode.orEmpty(), onDone = { back(); onCodeConsumed() })
            }
            MobileScreen.Services -> services?.let {
                MobileServicesScreen(
                    it, catalog, servicesText, language, spot,
                    onOpenItem = { item -> selectedItem = item; open(MobileScreen.Order) },
                    onMyOrders = { open(MobileScreen.Orders) },
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
            MobileScreen.Orders -> services?.let { MobileOrdersScreen(it, catalog, servicesText, language) }
        }
        if (stack.size > 1) BackHandler {
            if (stack.last() == MobileScreen.Pair) onCodeConsumed()
            back()
        }
    }
}

@Composable
private fun HomeScreen(
    app: MobileApplication,
    text: UiText,
    servicesText: ServicesText,
    language: String,
    link: LinkState,
    hotel: HotelData?,
    catalog: ServiceCatalog?,
    onPair: () -> Unit,
    onServices: () -> Unit,
    onOrders: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(sent) { if (sent) { delay(2_000); sent = false } }
    fun send(command: TvCommand) = scope.launch { sent = app.companion.send(command) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            hotel?.hotelInfo?.name?.resolve(language, app.brand.defaultLanguage) ?: app.brand.displayName,
            style = HospitalityTheme.typography.display, color = colors.textPrimary,
        )

        // Estado de la conexión con la TV de la habitación.
        when (link) {
            is LinkState.Linked -> {
                FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(text.connectedTo, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                            Text(
                                listOfNotNull(link.tv.tvName, link.tv.roomNumber?.let { "${text.room} $it" }).joinToString(" · "),
                                style = HospitalityTheme.typography.label, color = colors.textPrimary,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    FocusCard(onClick = { scope.launch { app.companion.unlink() } }, modifier = Modifier.size(width = 150.dp, height = 44.dp)) {
                        Text(text.disconnect, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.align(Alignment.Center))
                    }
                }
                SectionTitle(text.onTheTv)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TvAction(text.openGuideOnTv, R.drawable.ic_live, Modifier.weight(1f)) { send(TvCommand.OpenGuide) }
                    TvAction(text.openHotelOnTv, R.drawable.ic_info, Modifier.weight(1f)) { send(TvCommand.OpenHotelInfo) }
                }
                if (sent) Text(text.sentToTv, style = HospitalityTheme.typography.body, color = colors.accent)
            }
            else -> FocusCard(onClick = onPair, modifier = Modifier.fillMaxWidth().height(110.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_live), contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(text.connectTv, style = HospitalityTheme.typography.label, color = colors.textPrimary)
                        Text(text.connectHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                    }
                }
            }
        }
        if (app.isDemoCompanion) Text(text.demoNote, style = HospitalityTheme.typography.body, color = colors.textMuted)

        // Servicios del hotel: pedir a la habitación o desde la piscina / playa.
        app.guestServices?.let { services ->
            val orders by services.orders.collectAsState()
            SectionTitle(servicesText.title)
            FocusCard(onClick = onServices, modifier = Modifier.fillMaxWidth().height(150.dp)) {
                catalog?.categories?.flatMap { it.items }?.firstNotNullOfOrNull { it.imageUrl }?.let {
                    AsyncImage(model = imageModel(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x990E2E32)))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(
                        catalog?.categories?.joinToString(" · ") { it.name.localized(language) }.orEmpty(),
                        style = HospitalityTheme.typography.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(servicesText.subtitleMobile, style = HospitalityTheme.typography.label, color = colors.textPrimary)
                }
            }
            orders.firstOrNull { it.status.isOpen }?.let { active ->
                FocusCard(onClick = onOrders, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(servicesText.myOrders, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                            Text(
                                "${active.summary}: ${servicesText.status(active.status)}",
                                style = HospitalityTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Información del hotel (la de la marca; con el backend, la de la estadía).
        hotel?.let { data ->
            SectionTitle(text.aboutHotel)
            data.images.takeIf { it.isNotEmpty() }?.let { images ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(images, key = { it.id }) { image ->
                        Box(Modifier.size(width = 220.dp, height = 140.dp).clip(RoundedCornerShape(14.dp))) {
                            AsyncImage(
                                model = imageModel(image.thumbnailUrl ?: image.url), contentDescription = image.title.resolve(language),
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                image.title.resolve(language), style = HospitalityTheme.typography.body, color = androidx.compose.ui.graphics.Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Color(0x990E2E32)).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Text(data.hotelInfo.description.resolve(language, app.brand.defaultLanguage), style = HospitalityTheme.typography.body, color = colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TvAction(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, modifier = modifier.height(100.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.accent, modifier = Modifier.padding(14.dp).size(26.dp))
        Text(label, style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
    }
}

/** Ingresar el código de 6 dígitos que muestra la TV (o llega precargado desde el QR). */
@Composable
private fun PairScreen(app: MobileApplication, text: UiText, initialCode: String, onDone: () -> Unit) {
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

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(text.connectTv, style = HospitalityTheme.typography.title, color = colors.textPrimary)
        Text(text.connectHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
            label = { Text(text.code) },
            singleLine = true,
            enabled = !busy,
            textStyle = TextStyle(fontSize = 32.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center, color = colors.textPrimary),
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
            Text(if (busy) "…" else text.connect, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
        }
        if (app.isDemoCompanion) Text(text.demoNote, style = HospitalityTheme.typography.body, color = colors.textMuted)
    }
}
