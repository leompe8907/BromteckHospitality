package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.networkbroadcast.hospitality.tv.R
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Servicios del hotel en la TV. Desde la TV siempre se pide a la habitación y se carga a su cuenta;
 * pedir desde la piscina o la playa es cosa del celular (QR del camastro).
 */
@Composable
fun ServicesScreen(
    services: GuestServices,
    text: ServicesText,
    language: String,
    focusOn: String?,
    onOpenItem: (ServiceItem) -> Unit,
    onMyOrders: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val catalog by produceState<ServiceCatalog?>(null) { value = services.catalog() }
    val orders by services.orders.collectAsState()
    val current = catalog ?: return CenteredMessage("…")
    val room = OrderLocation.room("")
    val categories = current.categories.map { c -> c to c.items.filter { it.availableIn(room) } }.filter { it.second.isNotEmpty() }
    val target = focusOn?.takeIf { id -> categories.any { (_, items) -> items.any { it.id == id } } } ?: categories.firstOrNull()?.second?.firstOrNull()?.id
    val first = rememberInitialFocus(target)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text.title, style = HospitalityTheme.typography.title, color = colors.textPrimary)
                    Text(text.subtitleTv, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                }
                val open = orders.count { it.status.isOpen }
                FocusCard(onClick = onMyOrders, modifier = Modifier.size(width = 220.dp, height = 64.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        Text(text.myOrders, style = HospitalityTheme.typography.label, color = colors.textPrimary, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        if (open > 0) CountBadge(open)
                    }
                }
            }
        }
        itemsIndexed(categories) { _, (category, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(category.name.localized(language), Modifier.padding(start = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = FocusListPadding) {
                    items(items, key = { it.id }) { item ->
                        FocusCard(
                            onClick = { onOpenItem(item) },
                            modifier = Modifier.size(width = 230.dp, height = 190.dp)
                                .then(if (item.id == target) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                ItemImage(item, Modifier.fillMaxWidth().height(112.dp))
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        item.name.localized(language), style = HospitalityTheme.typography.label, color = colors.textPrimary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        priceLine(item, current, text, language), style = HospitalityTheme.typography.body, color = colors.accent,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (services is DemoGuestServices) {
            item { Text(text.demoGuest, style = HospitalityTheme.typography.body, color = colors.textMuted, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}

/**
 * Pedido de un servicio: cantidad, adónde va (la habitación) y cómo se paga (cargo a la
 * habitación; con tarjeta llega con la pasarela). "Confirmar pedido" es la confirmación del cargo:
 * el texto lo dice antes de apretarlo.
 */
@Composable
fun ServiceOrderDialog(
    item: ServiceItem,
    services: GuestServices,
    text: ServicesText,
    language: String,
    roomNumber: String,
    guestName: String?,
    onViewOrders: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    val catalog by produceState<ServiceCatalog?>(null) { value = services.catalog() }
    var quantity by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val currency = catalog?.currency ?: "USD"
    val total = item.price * quantity
    val totalText = formatPrice(total, currency, language, text.free)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.width(820.dp).height(420.dp).clip(RoundedCornerShape(18.dp)).background(colors.background)) {
            ItemImage(item, Modifier.width(300.dp).fillMaxHeight())
            if (placed) {
                val first = rememberInitialFocus("placed")
                Column(Modifier.weight(1f).padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text.placed, style = HospitalityTheme.typography.title, color = colors.textPrimary)
                    Text(text.placedHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DialogButton(text.viewOrders, colors.accent, Modifier.focusRequester(first), onViewOrders)
                        DialogButton(text.keepBrowsing, colors.textPrimary, Modifier, onDismiss)
                    }
                }
            } else {
                val first = rememberInitialFocus("order")
                Column(Modifier.weight(1f).padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.name.localized(language), style = HospitalityTheme.typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.description.localized(language), style = HospitalityTheme.typography.body, color = colors.textSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text.quantity, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(110.dp))
                        StepButton("−", enabled = quantity > 1) { quantity-- }
                        Text("$quantity", style = HospitalityTheme.typography.section, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                        StepButton("+", enabled = quantity < 9) { quantity++ }
                        Spacer(Modifier.weight(1f))
                        Text(totalText, style = HospitalityTheme.typography.section, color = colors.accent, maxLines = 1)
                    }
                    InfoLine(text.deliverTo, text.room.format(roomNumber))
                    InfoLine(text.payment, if (total > 0) text.roomCharge.format(roomNumber) else text.free)
                    if (total > 0) {
                        Text(
                            text.confirmCharge.format(totalText, roomNumber), style = HospitalityTheme.typography.body, color = colors.textMuted,
                            maxLines = 2,
                        )
                    }
                    if (failed) Text(text.orderFailed, style = HospitalityTheme.typography.body, color = colors.accent)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DialogButton(if (busy) text.placing else text.placeOrder, colors.accent, Modifier.focusRequester(first)) {
                            if (busy) return@DialogButton
                            busy = true
                            failed = false
                            scope.launch {
                                val result = services.placeOrder(
                                    OrderRequest(
                                        roomNumber = roomNumber,
                                        guestName = guestName,
                                        location = OrderLocation.room(roomNumber),
                                        lines = listOf(OrderLine(item.id, item.name.localized(language), quantity, item.price)),
                                        payment = PaymentMethod.RoomCharge,
                                        chargeConfirmed = true,
                                    ),
                                )
                                busy = false
                                if (result != null) placed = true else failed = true
                            }
                        }
                        DialogButton(text.back, colors.textPrimary, Modifier, onDismiss)
                    }
                }
            }
        }
    }
}

/** Pedidos de la estadía con su estado. Un pedido recién hecho se puede cancelar (OK sobre él). */
@Composable
fun MyOrdersScreen(services: GuestServices, text: ServicesText, language: String) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    val catalog by produceState<ServiceCatalog?>(null) { value = services.catalog() }
    val orders by services.orders.collectAsState()
    val first = rememberInitialFocus(orders.firstOrNull()?.id ?: "empty")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(text.myOrders, style = HospitalityTheme.typography.title, color = colors.textPrimary) }
        if (orders.isEmpty()) {
            item {
                FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth().height(90.dp).focusRequester(first)) {
                    Text(text.noOrders, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterStart).padding(24.dp))
                }
            }
        }
        itemsIndexed(orders, key = { _, o -> o.id }) { index, order ->
            OrderRow(
                order = order,
                catalog = catalog,
                text = text,
                language = language,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                onClick = { if (order.status == OrderStatus.Requested) scope.launch { services.cancel(order.id) } },
            )
        }
        if (services is DemoGuestServices) {
            item { Text(text.demoGuest, style = HospitalityTheme.typography.body, color = colors.textMuted, modifier = Modifier.padding(start = 12.dp)) }
        }
    }
}

@Composable
private fun OrderRow(order: ServiceOrder, catalog: ServiceCatalog?, text: ServicesText, language: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val time = remember(order.createdAtMillis) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(order.createdAtMillis)) }
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(118.dp)) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(order.summary, style = HospitalityTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(time, order.location.label(catalog, text, language), formatPrice(order.total, catalog?.currency ?: "USD", language, text.free)).joinToString(" · "),
                    style = HospitalityTheme.typography.body, color = colors.textSecondary, maxLines = 1,
                )
                if (focused && order.status == OrderStatus.Requested) {
                    Text("OK · ${text.cancelOrder}", style = HospitalityTheme.typography.body, color = colors.accent)
                }
            }
            StatusSteps(order.status, text)
        }
    }
}

/** Recibido → Aceptado → En camino → Entregado, con el paso actual resaltado. */
@Composable
private fun StatusSteps(status: OrderStatus, text: ServicesText) {
    val colors = HospitalityTheme.colors
    if (status == OrderStatus.Cancelled) {
        Text(text.cancelled, style = HospitalityTheme.typography.label, color = colors.textMuted)
        return
    }
    val steps = listOf(OrderStatus.Requested, OrderStatus.Accepted, OrderStatus.OnTheWay, OrderStatus.Delivered)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        steps.forEach { step ->
            val reached = step.ordinal <= status.ordinal
            val currentStep = step == status
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(92.dp)) {
                Box(Modifier.size(if (currentStep) 16.dp else 12.dp).clip(CircleShape).background(if (reached) colors.accent else colors.surfaceBorder))
                Text(
                    text.status(step), style = HospitalityTheme.typography.body,
                    color = if (currentStep) colors.textPrimary else if (reached) colors.textSecondary else colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Aviso arriba a la derecha cuando cambia el estado de un pedido ("Tu pedido de champán: En camino"). */
@Composable
fun BoxScope.OrderStatusBanner(message: String?) {
    val colors = HospitalityTheme.colors
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).padding(28.dp)) {
        Row(
            Modifier.width(420.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Text(message.orEmpty(), style = HospitalityTheme.typography.body, color = colors.textPrimary, modifier = Modifier.padding(start = 14.dp), maxLines = 2)
        }
    }
}

/** Precio (o "Sin cargo") y, si tiene, la duración: "MX$1,800 · 60 min". */
private fun priceLine(item: ServiceItem, catalog: ServiceCatalog, text: ServicesText, language: String): String =
    listOfNotNull(formatPrice(item.price, catalog.currency, language, text.free), item.durationMin?.let { "$it ${text.minutes}" }).joinToString(" · ")

@Composable
private fun ItemImage(item: ServiceItem, modifier: Modifier) {
    val colors = HospitalityTheme.colors
    Box(modifier.background(colors.surface), contentAlignment = Alignment.Center) {
        val url = item.imageUrl
        if (url != null) {
            AsyncImage(model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    val colors = HospitalityTheme.colors
    Box(Modifier.size(28.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
        Text("$count", style = HospitalityTheme.typography.body, color = colors.background)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = HospitalityTheme.colors
    Row {
        Text(label, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.width(230.dp))
        Text(value, style = HospitalityTheme.typography.body, color = colors.textPrimary)
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, enabled = enabled, modifier = Modifier.size(width = 64.dp, height = 52.dp)) {
        Text(label, style = HospitalityTheme.typography.section, color = if (enabled) colors.textPrimary else colors.textMuted, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun DialogButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = modifier.size(width = 220.dp, height = 60.dp)) {
        Text(label, style = HospitalityTheme.typography.label, color = color, modifier = Modifier.align(Alignment.Center))
    }
}
