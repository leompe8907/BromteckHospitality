package com.networkbroadcast.hospitality.staff

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.designsystem.qrImageBitmap
import com.networkbroadcast.hospitality.services.OrderStatus
import com.networkbroadcast.hospitality.services.PaymentMethod
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServiceOrder
import com.networkbroadcast.hospitality.services.ServiceZone
import com.networkbroadcast.hospitality.services.ServicesText
import com.networkbroadcast.hospitality.services.formatPrice
import com.networkbroadcast.hospitality.services.label
import com.networkbroadcast.hospitality.services.localized
import com.networkbroadcast.hospitality.services.spotLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Pestañas: los pedidos por estado y los códigos QR de las ubicaciones. */
private enum class StaffTab { New, Active, Done, Qr }

/**
 * App del personal: la cola de pedidos del turno. Cada pedido muestra primero DÓNDE está el huésped
 * (habitación o camastro), que es lo que el mesero necesita para ir directo; después qué pidió y a
 * qué cuenta va. Un botón por paso: aceptar → salir a entregar → marcar entregado.
 */
@Composable
fun StaffApp(app: StaffApplication) {
    val brand = app.brand
    val language = Locale.getDefault().language.takeIf { it in brand.languages } ?: brand.defaultLanguage
    val text = ServicesText.of(language)
    val services = app.staffServices
    val queue by services.queue.collectAsState()
    val catalog by produceState<ServiceCatalog?>(null) { value = services.catalog() }
    var tab by remember { mutableStateOf(StaffTab.New) }
    // "hace 3 min": se recalcula cada 30 s.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val colors = HospitalityTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background).systemBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(brand.displayName, style = HospitalityTheme.typography.body, color = colors.textSecondary)
            Text(if (tab == StaffTab.Qr) text.qrCodes else text.staffTitle, style = HospitalityTheme.typography.title, color = colors.textPrimary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(text.tabNew, queue.count { it.status == OrderStatus.Requested }, tab == StaffTab.New, Modifier.weight(1f)) { tab = StaffTab.New }
            TabChip(text.tabActive, queue.count { it.status == OrderStatus.Accepted || it.status == OrderStatus.OnTheWay }, tab == StaffTab.Active, Modifier.weight(1f)) { tab = StaffTab.Active }
            TabChip(text.tabDone, null, tab == StaffTab.Done, Modifier.weight(1f)) { tab = StaffTab.Done }
            FocusCard(onClick = { tab = StaffTab.Qr }, modifier = Modifier.size(width = 52.dp, height = 44.dp)) {
                Icon(
                    painterResource(R.drawable.ic_qr), contentDescription = text.qrCodes,
                    tint = if (tab == StaffTab.Qr) colors.accent else colors.textSecondary, modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
            }
        }
        when (tab) {
            StaffTab.Qr -> QrCodes(catalog, text, language)
            else -> {
                val shown = queue.filter {
                    when (tab) {
                        StaffTab.New -> it.status == OrderStatus.Requested
                        StaffTab.Active -> it.status == OrderStatus.Accepted || it.status == OrderStatus.OnTheWay
                        else -> !it.status.isOpen
                    }
                }
                val scope = rememberCoroutineScope()
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (shown.isEmpty()) item { Text(text.emptyQueue, style = HospitalityTheme.typography.body, color = colors.textSecondary, modifier = Modifier.padding(8.dp)) }
                    items(shown, key = { it.id }) { order ->
                        StaffOrderCard(order, catalog, text, language, now) { scope.launch { services.advance(order.id) } }
                    }
                    if (app.isDemo) item { Text(text.demoStaff, style = HospitalityTheme.typography.body, color = colors.textMuted, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StaffOrderCard(order: ServiceOrder, catalog: ServiceCatalog?, text: ServicesText, language: String, now: Long, onAdvance: () -> Unit) {
    val colors = HospitalityTheme.colors
    val minutes = ((now - order.createdAtMillis) / 60_000).toInt()
    val ago = if (minutes < 1) text.justNow else text.minutesAgo.format(minutes)
    val price = formatPrice(order.total, catalog?.currency ?: "USD", language, text.free)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
            .border(if (order.status == OrderStatus.Requested) 2.dp else 1.dp, if (order.status == OrderStatus.Requested) colors.accent else colors.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                order.location.label(catalog, text, language), style = HospitalityTheme.typography.title, color = colors.textPrimary,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(ago, style = HospitalityTheme.typography.body, color = colors.textSecondary)
        }
        Text(order.summary, style = HospitalityTheme.typography.label, color = colors.textPrimary)
        Text(
            listOfNotNull(order.guestName?.let { "${text.guest}: $it" }, text.room.format(order.roomNumber)).joinToString(" · "),
            style = HospitalityTheme.typography.body, color = colors.textSecondary,
        )
        val payment = when {
            order.total == 0.0 -> text.free
            order.payment == PaymentMethod.Card -> "${text.paidByCard} · $price"
            order.chargeRegistered -> "✓ ${text.chargeRegistered.format(order.roomNumber)} · $price"
            else -> "${text.chargeTo.format(order.roomNumber)} · $price"
        }
        Text(payment, style = HospitalityTheme.typography.body, color = if (order.chargeRegistered) colors.accent else colors.textSecondary)
        val action = when (order.status) {
            OrderStatus.Requested -> text.accept
            OrderStatus.Accepted -> text.startDelivery
            OrderStatus.OnTheWay -> text.markDelivered
            else -> null
        }
        if (action != null) {
            FocusCard(onClick = onAdvance, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp)) {
                Box(Modifier.fillMaxSize().background(if (order.status == OrderStatus.Requested) colors.accent else colors.background))
                Text(
                    action, style = HospitalityTheme.typography.label,
                    color = if (order.status == OrderStatus.Requested) colors.background else colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            Text(text.status(order.status), style = HospitalityTheme.typography.label, color = colors.textMuted)
        }
    }
}

/**
 * Un QR por puesto de cada zona, para imprimir y pegar en el camastro o la mesa. El huésped lo
 * escanea con la cámara del celular y la app abre los servicios con la ubicación ya cargada.
 */
@Composable
private fun QrCodes(catalog: ServiceCatalog?, text: ServicesText, language: String) {
    val colors = HospitalityTheme.colors
    val zones = catalog?.zones.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(text.qrHint, style = HospitalityTheme.typography.body, color = colors.textSecondary)
        }
        zones.forEach { zone ->
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(zone.name.localized(language), Modifier.padding(top = 8.dp)) }
            items((1..zone.spots).map { it.toString() }, key = { "${zone.id}-$it" }) { spot -> QrTile(zone, spot, language) }
        }
    }
}

@Composable
private fun QrTile(zone: ServiceZone, spot: String, language: String) {
    val qr = remember(zone.id, spot) { qrImageBitmap(spotLink(zone.id, spot)) }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(qr, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(120.dp))
        Text(
            "${zone.spotLabel.localized(language)} $spot", style = HospitalityTheme.typography.label, color = Color(0xFF0E2E32),
            maxLines = 1,
        )
    }
}

@Composable
private fun TabChip(label: String, count: Int?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    FocusCard(onClick = onClick, modifier = modifier.height(44.dp)) {
        if (selected) Box(Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.18f)))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HospitalityTheme.typography.body, color = if (selected) colors.textPrimary else colors.textSecondary, maxLines = 1)
            if (count != null && count > 0) {
                Box(Modifier.padding(start = 6.dp).size(22.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Text("$count", style = HospitalityTheme.typography.body.copy(fontSize = HospitalityTheme.typography.body.fontSize * 0.75f), color = colors.background)
                }
            }
        }
    }
}
