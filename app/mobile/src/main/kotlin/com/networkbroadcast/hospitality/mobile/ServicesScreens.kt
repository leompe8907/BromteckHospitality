package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.services.DemoGuestServices
import com.networkbroadcast.hospitality.services.GuestServices
import com.networkbroadcast.hospitality.services.OrderLine
import com.networkbroadcast.hospitality.services.OrderLocation
import com.networkbroadcast.hospitality.services.OrderRequest
import com.networkbroadcast.hospitality.services.OrderStatus
import com.networkbroadcast.hospitality.services.PaymentMethod
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServiceItem
import com.networkbroadcast.hospitality.services.ServiceOrder
import com.networkbroadcast.hospitality.services.ServicesText
import com.networkbroadcast.hospitality.services.formatPrice
import com.networkbroadcast.hospitality.services.label
import com.networkbroadcast.hospitality.services.localized
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Catálogo de servicios en el celular. A diferencia de la TV, acá se puede pedir desde la piscina o
 * la playa: si el huésped escaneó el QR de su camastro, [spot] trae la ubicación.
 */
@Composable
fun MobileServicesScreen(
    services: GuestServices,
    catalog: ServiceCatalog?,
    text: ServicesText,
    language: String,
    spot: OrderLocation?,
    onOpenItem: (ServiceItem) -> Unit,
    onMyOrders: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val orders by services.orders.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text.title, style = HospitalityTheme.typography.title, color = colors.textPrimary)
        Text(text.subtitleMobile, style = HospitalityTheme.typography.body, color = colors.textSecondary)
        spot?.let { SpotBanner(text.youAreAt.format(it.label(catalog, text, language))) }
        FocusCard(onClick = onMyOrders, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                Text(text.myOrders, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.padding(start = 12.dp).weight(1f))
                val open = orders.count { it.status.isOpen }
                if (open > 0) Badge(open)
            }
        }
        val current = catalog ?: return@Column
        current.categories.forEach { category ->
            SectionTitle(category.name.localized(language))
            category.items.forEach { item ->
                val here = spot == null || item.availableIn(spot)
                FocusCard(onClick = { onOpenItem(item) }, modifier = Modifier.fillMaxWidth().height(92.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        ItemThumb(item, Modifier.width(110.dp).height(92.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name.localized(language), style = HospitalityTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (here) priceLine(item, current, text, language) else text.notAvailableHere,
                                style = HospitalityTheme.typography.body, color = if (here) colors.accent else colors.textMuted, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (services is DemoGuestServices) Text(text.demoGuest, style = HospitalityTheme.typography.body, color = colors.textMuted)
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Pedido de un servicio desde el celular: cantidad, adónde (habitación o un camastro), forma de
 * pago y confirmación explícita del cargo a la habitación. Sin conexión con la TV no se sabe la
 * habitación, así que se pide conectar primero.
 */
@Composable
fun MobileOrderScreen(
    item: ServiceItem,
    services: GuestServices,
    catalog: ServiceCatalog?,
    text: ServicesText,
    language: String,
    roomNumber: String?,
    guestName: String?,
    spot: OrderLocation?,
    onConnect: () -> Unit,
    onViewOrders: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    val zones = catalog?.zones.orEmpty().filter { it.id in item.availableAt }
    // Ubicación inicial: el camastro del QR si el servicio llega ahí; si no, la habitación.
    var zoneId by remember { mutableStateOf(spot?.zoneId?.takeIf { z -> zones.any { it.id == z } } ?: if (ServiceItem.ROOM in item.availableAt) null else zones.firstOrNull()?.id) }
    var spotNumber by remember { mutableStateOf(spot?.takeIf { it.zoneId == zoneId }?.spot.orEmpty()) }
    var quantity by remember { mutableIntStateOf(1) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val currency = catalog?.currency ?: "USD"
    val total = item.price * quantity
    val totalText = formatPrice(total, currency, language, text.free)
    val location = zoneId?.let { OrderLocation.spot(it, spotNumber.trim()) } ?: roomNumber?.let { OrderLocation.room(it) }
    val spotFromQr = spot != null && spot.zoneId == zoneId && spot.spot == spotNumber
    val ready = roomNumber != null && location != null && (zoneId == null || spotNumber.isNotBlank()) && (total == 0.0 || confirmed) && !busy

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            ItemThumb(item, Modifier.fillMaxSize())
            FocusCard(onClick = onBack, modifier = Modifier.padding(12.dp).size(width = 96.dp, height = 40.dp)) {
                Text(text.back, style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(item.name.localized(language), style = HospitalityTheme.typography.title, color = colors.textPrimary)
            Text(item.description.localized(language), style = HospitalityTheme.typography.body, color = colors.textSecondary)
            if (placed) {
                Text(text.placed, style = HospitalityTheme.typography.title, color = colors.accent)
                Text(text.placedHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                WideButton(text.viewOrders, colors.accent, onClick = onViewOrders)
                WideButton(text.keepBrowsing, colors.textPrimary, onClick = onBack)
                return@Column
            }
            if (roomNumber == null) {
                Text(text.connectToOrder, style = HospitalityTheme.typography.body, color = colors.accent)
                WideButton(text.order, colors.accent, onClick = onConnect)
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text.quantity, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                StepButton("−", quantity > 1) { quantity-- }
                Text("$quantity", style = HospitalityTheme.typography.section, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
                StepButton("+", quantity < 9) { quantity++ }
            }

            SectionTitle(text.deliverTo)
            if (ServiceItem.ROOM in item.availableAt) {
                ChoiceRow(selected = zoneId == null, title = text.room.format(roomNumber)) { zoneId = null }
            }
            zones.forEach { zone ->
                val selected = zoneId == zone.id
                ChoiceRow(selected = selected, title = zone.name.localized(language)) {
                    zoneId = zone.id
                    spotNumber = spot?.takeIf { it.zoneId == zone.id }?.spot.orEmpty()
                }
                if (selected) {
                    if (spotFromQr) {
                        SpotBanner(text.youAreAt.format(OrderLocation.spot(zone.id, spotNumber).label(catalog, text, language)))
                    } else {
                        OutlinedTextField(
                            value = spotNumber,
                            onValueChange = { spotNumber = it.filter(Char::isDigit).take(3) },
                            label = { Text(text.spotNumber.format(zone.spotLabel.localized(language).lowercase())) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.focus, unfocusedBorderColor = colors.surfaceBorder,
                                focusedLabelColor = colors.focus, unfocusedLabelColor = colors.textMuted, cursorColor = colors.focus,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SectionTitle(text.payment)
            ChoiceRow(selected = true, title = if (total > 0) text.roomCharge.format(roomNumber) else text.free) {}
            if (total > 0) ChoiceRow(selected = false, enabled = false, title = text.card, subtitle = text.cardSoon) {}

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text.total, style = HospitalityTheme.typography.label, color = colors.textSecondary)
                Text(totalText, style = HospitalityTheme.typography.section, color = colors.accent, maxLines = 1)
            }
            if (total > 0) {
                CheckRow(checked = confirmed, label = text.confirmCharge.format(totalText, roomNumber)) { confirmed = !confirmed }
            }
            if (failed) Text(text.orderFailed, style = HospitalityTheme.typography.body, color = colors.accent)
            WideButton(if (busy) text.placing else text.placeOrder, if (ready) colors.accent else colors.textMuted, enabled = ready) {
                val target = location ?: return@WideButton
                busy = true
                failed = false
                scope.launch {
                    val result = services.placeOrder(
                        OrderRequest(
                            roomNumber = roomNumber,
                            guestName = guestName,
                            location = target,
                            lines = listOf(OrderLine(item.id, item.name.localized(language), quantity, item.price)),
                            payment = PaymentMethod.RoomCharge,
                            chargeConfirmed = confirmed,
                        ),
                    )
                    busy = false
                    if (result != null) placed = true else failed = true
                }
            }
            if (services is DemoGuestServices) Text(text.demoGuest, style = HospitalityTheme.typography.body, color = colors.textMuted)
        }
    }
}

/** Pedidos de la estadía con su estado; el recién hecho se puede cancelar. */
@Composable
fun MobileOrdersScreen(services: GuestServices, catalog: ServiceCatalog?, text: ServicesText, language: String) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    val orders by services.orders.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text.myOrders, style = HospitalityTheme.typography.title, color = colors.textPrimary)
        if (orders.isEmpty()) Text(text.noOrders, style = HospitalityTheme.typography.body, color = colors.textSecondary)
        orders.forEach { order ->
            OrderCard(order, catalog, text, language) { scope.launch { services.cancel(order.id) } }
        }
        if (services is DemoGuestServices) Text(text.demoGuest, style = HospitalityTheme.typography.body, color = colors.textMuted)
    }
}

@Composable
private fun OrderCard(order: ServiceOrder, catalog: ServiceCatalog?, text: ServicesText, language: String, onCancel: () -> Unit) {
    val colors = HospitalityTheme.colors
    val time = remember(order.createdAtMillis) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(order.createdAtMillis)) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.surfaceBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(order.summary, style = HospitalityTheme.typography.label, color = colors.textPrimary)
        Text(
            listOf(time, order.location.label(catalog, text, language), formatPrice(order.total, catalog?.currency ?: "USD", language, text.free)).joinToString(" · "),
            style = HospitalityTheme.typography.body, color = colors.textSecondary,
        )
        if (order.status == OrderStatus.Cancelled) {
            Text(text.cancelled, style = HospitalityTheme.typography.label, color = colors.textMuted)
        } else {
            Steps(order.status, text)
        }
        if (order.status == OrderStatus.Requested) {
            FocusCard(onClick = onCancel, modifier = Modifier.size(width = 170.dp, height = 40.dp)) {
                Text(text.cancelOrder, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Recibido → Aceptado → En camino → Entregado. */
@Composable
private fun Steps(status: OrderStatus, text: ServicesText) {
    val colors = HospitalityTheme.colors
    val steps = listOf(OrderStatus.Requested, OrderStatus.Accepted, OrderStatus.OnTheWay, OrderStatus.Delivered)
    Row(Modifier.fillMaxWidth()) {
        steps.forEach { step ->
            val reached = step.ordinal <= status.ordinal
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (reached) colors.accent else colors.surfaceBorder))
                Text(
                    text.status(step), style = HospitalityTheme.typography.body.copy(fontSize = HospitalityTheme.typography.body.fontSize * 0.75f),
                    color = if (step == status) colors.textPrimary else if (reached) colors.textSecondary else colors.textMuted, maxLines = 1,
                )
            }
        }
    }
}

/** "Estás en Piscina · Camastro 12": la ubicación que llegó del QR. */
@Composable
fun SpotBanner(message: String) {
    val colors = HospitalityTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
        Text(message, style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun priceLine(item: ServiceItem, catalog: ServiceCatalog, text: ServicesText, language: String): String =
    listOfNotNull(formatPrice(item.price, catalog.currency, language, text.free), item.durationMin?.let { "$it ${text.minutes}" }).joinToString(" · ")

@Composable
private fun ItemThumb(item: ServiceItem, modifier: Modifier) {
    val colors = HospitalityTheme.colors
    Box(modifier.background(colors.background), contentAlignment = Alignment.Center) {
        val url = item.imageUrl
        if (url != null) {
            AsyncImage(model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(if (subtitle != null) 64.dp else 52.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) colors.accent else colors.surfaceBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent)) }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = HospitalityTheme.typography.body, color = if (enabled) colors.textPrimary else colors.textMuted)
                subtitle?.let { Text(it, style = HospitalityTheme.typography.body, color = colors.textMuted) }
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (checked) colors.accent else Color.Transparent)
                    .border(2.dp, if (checked) colors.accent else colors.surfaceBorder, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { if (checked) Text("✓", style = HospitalityTheme.typography.body, color = colors.background) }
            Text(label, style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, enabled = enabled, modifier = Modifier.size(width = 52.dp, height = 44.dp)) {
        Text(label, style = HospitalityTheme.typography.section, color = if (enabled) colors.textPrimary else colors.textMuted, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun WideButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    FocusCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(label, style = HospitalityTheme.typography.label, color = color, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Badge(count: Int) {
    val colors = HospitalityTheme.colors
    Box(Modifier.size(26.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Text("$count", style = HospitalityTheme.typography.body, color = colors.background)
    }
}
