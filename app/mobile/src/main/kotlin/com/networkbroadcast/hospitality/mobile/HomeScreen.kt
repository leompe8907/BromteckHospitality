package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.qrImageBitmap
import com.networkbroadcast.hospitality.entertainment.Channel
import com.networkbroadcast.hospitality.entertainment.VodItem
import com.networkbroadcast.hospitality.entertainment.nowAndNext
import com.networkbroadcast.hospitality.hotel.HotelData
import com.networkbroadcast.hospitality.hotel.WifiNetwork
import com.networkbroadcast.hospitality.hotel.resolve
import com.networkbroadcast.hospitality.panaccess.HomeWidget
import com.networkbroadcast.hospitality.services.ServiceCatalog
import com.networkbroadcast.hospitality.services.ServicesText
import com.networkbroadcast.hospitality.services.localized
import kotlinx.coroutines.delay

/*
 * Home del celular: la grilla estilo iOS del mockup (docs/mockup-celular/index.html, ".grid", ".tile",
 * ".wd"). 4 columnas, 18 dp de margen, 12 dp entre columnas, filas de 79,5 dp con 22 dp entre filas.
 * Tile S = 1x1 (icono con rótulo debajo), M = 2x2, W = 4x2. Los tiles y su orden salen de
 * [HomeLayout.kt] (parámetro propuesto X_APP_MOBILE_TILES, con el orden del mockup por defecto) y los
 * widgets de la cuenta (X_APP_WIDGETS) entran como tiles. Se recorre con el scroll vertical de la home.
 */

private val COL_GAP = 12.dp
private val ROW_H = 79.5.dp
private val ROW_GAP = 22.dp
private val SKY = Color(0xFF4A97D2)   // el azul de cielo del tile de clima del mockup (.sky)

/** Lo que la home necesita saber; todo sale de la cuenta y de la marca, nada se inventa. */
class HomeData(
    val tiles: List<HomeTile>,
    val widgets: List<HomeWidget>,
    val wifi: WifiNetwork?,
    val weatherTemp: Int?,
    val weatherText: String?,
    val hotel: HotelData?,
    val catalog: ServiceCatalog?,
    val guestName: String?,
    val roomNumber: String?,
    val logoUrl: String?,
)

/** A dónde lleva cada tile. */
class HomeActions(
    val onRemote: () -> Unit,
    val onLive: () -> Unit,
    val onVod: () -> Unit,
    val onWifi: () -> Unit,
    val onServices: () -> Unit,
    val onHotel: () -> Unit,
    val onWidget: (HomeWidget) -> Unit,
    /** Check-out (termina la estadía); null si la marca no lo ofrece. */
    val onCheckout: (() -> Unit)? = null,
)

@Composable
fun HomeScreen(
    data: HomeData,
    model: ContentModel,
    language: String,
    text: UiText,
    contentText: ContentText,
    servicesText: ServicesText,
    remoteText: RemoteText,
    hotelName: String?,
    actions: HomeActions,
) {
    val colors = HospitalityTheme.colors
    val needsContent = data.tiles.any { it is HomeTile.Builtin && (it.id == TileIds.LIVE || it.id == TileIds.VOD) }
    LaunchedEffect(model, needsContent) { if (needsContent) model.load() }
    val now by produceState(System.currentTimeMillis() / 1000) { while (true) { delay(30_000); value = System.currentTimeMillis() / 1000 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Cabecera (.hero): logo del hotel, la TV de la habitación (abre el control) y el saludo.
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp)) {
            if (data.logoUrl != null || hotelName != null || data.roomNumber != null) Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                if (data.logoUrl != null || hotelName != null) {
                    Row(
                        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(colors.surface).border(1.dp, colors.surfaceBorder, RoundedCornerShape(19.dp))
                            .padding(start = 11.dp, end = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (data.logoUrl != null) {
                            AsyncImage(model = imageModel(data.logoUrl), contentDescription = hotelName, contentScale = ContentScale.Fit, modifier = Modifier.height(24.dp).width(110.dp))
                        } else {
                            Text(hotelName.orEmpty(), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = HospitalityTheme.typography.label.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.34.sp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                data.roomNumber?.let { room ->
                    // `.pill` del mockup: "TV 101" con el punto de conectada.
                    Row(
                        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(colors.surface).border(1.dp, colors.surfaceBorder, RoundedCornerShape(15.dp))
                            .clickable(onClick = actions.onRemote).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(colors.accent)
                        Text("TV $room", color = colors.textPrimary, maxLines = 1, style = HospitalityTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 22.dp, bottom = 20.dp)) {
                data.roomNumber?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        Text("${text.room} $it".uppercase(), color = colors.accent,
                            style = HospitalityTheme.typography.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.47.sp))
                    }
                }
                val hour = remember(now) { java.util.Calendar.getInstance().apply { timeInMillis = now * 1000 }.get(java.util.Calendar.HOUR_OF_DAY) }
                Text(greetingFor(text, hour, data.guestName), color = colors.textPrimary,
                    style = HospitalityTheme.typography.display.copy(fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.26).sp),
                    modifier = Modifier.padding(top = 5.dp))
            }
        }

        // Grilla (.grid).
        val placements = remember(data.tiles) { placeTiles(data.tiles.map { it.size }) }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            val colW = (maxWidth - COL_GAP * 3) / 4
            fun wOf(s: TileSize): Dp = colW * s.cols + COL_GAP * (s.cols - 1)
            fun hOf(s: TileSize): Dp = ROW_H * s.rows + ROW_GAP * (s.rows - 1)
            val rows = gridRows(placements)
            val total = if (rows == 0) 0.dp else ROW_H * rows + ROW_GAP * (rows - 1)
            Box(Modifier.fillMaxWidth().height(total)) {
                data.tiles.forEachIndexed { i, tile ->
                    val p = placements[i]
                    Box(Modifier.offset(x = (colW + COL_GAP) * p.col, y = (ROW_H + ROW_GAP) * p.row).size(wOf(p.size), hOf(p.size))) {
                        TileContent(tile, data, model, language, text, contentText, servicesText, remoteText, hotelName, actions, now, wOf(p.size), hOf(p.size))
                    }
                }
            }
        }
        Spacer(Modifier.height(70.dp))   // .homefoot: lugar para los rótulos de la última fila
        actions.onCheckout?.let { onCheckout ->
            // Al final de la home, en el color de confirmar (X_APP_CONFIRM_COLOR; sin él, el acento de la marca) para que se
            // vea. Pide confirmación antes de terminar la estadía.
            val shape = RoundedCornerShape(rad(25, 2))
            Box(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 40.dp).fillMaxWidth().height(50.dp).clip(shape)
                    .background(colors.accent).clickable(onClick = onCheckout),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.checkout, color = HospitalityColors.readableTextColor(colors.accent), style = HospitalityTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun TileContent(
    tile: HomeTile, data: HomeData, model: ContentModel, language: String, text: UiText, contentText: ContentText,
    servicesText: ServicesText, remoteText: RemoteText, hotelName: String?, actions: HomeActions, now: Long, w: Dp, h: Dp,
) {
    when (tile) {
        is HomeTile.Widget -> {
            val widget = data.widgets.getOrNull(tile.index) ?: return
            when (tile.size) {
                TileSize.S -> SmallTile(widget.title, w, h, { actions.onWidget(widget) }) { PhotoFill(widget.imageUrl, scrim = false) }
                else -> PhotoWidgetTile(
                    imageUrl = widget.imageUrl, size = tile.size,
                    kicker = widgetMeta(widget, text.floorLabel), title = widget.title, description = widget.description,
                    onClick = { actions.onWidget(widget) },
                )
            }
        }
        is HomeTile.Builtin -> when (tile.id) {
            TileIds.REMOTE -> RemoteTile(tile.size, data.roomNumber, remoteText, actions.onRemote, w, h)
            TileIds.LIVE -> LiveTile(tile.size, model, now, text, contentText, w, h, actions.onLive)
            TileIds.VOD -> VodTile(tile.size, model, contentText, actions.onVod, w, h)
            TileIds.WIFI -> data.wifi?.let { WifiTile(tile.size, it, text, contentText, actions.onWifi, w, h) }
            TileIds.WEATHER -> WeatherTile(tile.size, data.weatherTemp, data.weatherText, contentText, w, h)
            TileIds.SERVICES -> ServicesTile(tile.size, data.catalog, language, servicesText, actions.onServices, w, h)
            TileIds.HOTEL -> HotelTile(tile.size, data, text, hotelName, actions.onHotel, w, h)
        }
    }
}

/* ---------------------------------------------------------------- piezas ---------------------------------------------------------------- */

/** Tile S (`.tile .sq`): cuadrado con el ícono en el color de acento y el rótulo debajo, en el hueco entre filas. */
@Composable
private fun SmallTile(label: String, w: Dp, h: Dp, onClick: () -> Unit, fill: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(15))
    Box(Modifier.size(w, h)) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(fill ?: colors.surface).then(if (fill == null) Modifier.border(1.dp, colors.surfaceBorder, shape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center, content = content,
        )
        Text(
            label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = HospitalityTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.offset(x = (-4).dp, y = h + 4.dp).requiredWidth(w + 8.dp),
        )
    }
}

/** Tile M/W (`.wd`): tarjeta con las esquinas grandes del tema; [content] la llena. */
@Composable
private fun WidgetTile(onClick: (() -> Unit)?, fill: Color? = null, borderless: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(20))
    Box(
        Modifier.fillMaxSize().clip(shape).background(fill ?: colors.surface)
            .then(if (borderless) Modifier else Modifier.border(1.dp, colors.surfaceBorder, shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Rótulo de un widget (`.wk`): ícono de 14 dp y texto en mayúsculas de 10 sp. */
@Composable
private fun Kicker(label: String, icon: Int? = null, color: Color = HospitalityTheme.colors.textSecondary, iconColor: Color = HospitalityTheme.colors.accent) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        Text(label.uppercase(), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = HospitalityTheme.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp))
    }
}

/** Foto a todo el tile; con [scrim] una capa lisa de negro al 42 % (`.scrim`) para que se lea el texto. */
@Composable
private fun PhotoFill(url: String, scrim: Boolean = true) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (scrim) Box(Modifier.fillMaxSize().background(Color(0x6B000000)))
    }
}

/** Tile M/W con foto: rótulo, título grande y, en el ancho, la descripción (como el widget de la home de la TV). */
@Composable
private fun PhotoWidgetTile(imageUrl: String, size: TileSize, kicker: String?, title: String, description: String?, onClick: () -> Unit) {
    WidgetTile(onClick, borderless = true) {
        PhotoFill(imageUrl)
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            kicker?.let {
                Text(it.uppercase(), color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.47.sp))
            }
            Text(title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = HospitalityTheme.typography.label.copy(fontSize = if (size == TileSize.W) 28.sp else 21.sp, lineHeight = if (size == TileSize.W) 30.sp else 23.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 3.dp))
            if (size == TileSize.W) description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun TileIcon(icon: Int, size: Dp = 30.dp) {
    Icon(painterResource(icon), contentDescription = null, tint = HospitalityTheme.colors.accent, modifier = Modifier.size(size))
}

/* ---------------------------------------------------------------- tiles ---------------------------------------------------------------- */

/** Control remoto (`remote`, `.fill` del mockup): en el color de acento, con las flechas en chico y la TV de la habitación. */
@Composable
private fun RemoteTile(size: TileSize, roomNumber: String?, text: RemoteText, onClick: () -> Unit, w: Dp, h: Dp) {
    val colors = HospitalityTheme.colors
    val ink = HospitalityColors.readableTextColor(colors.accent)
    if (size == TileSize.S) {
        SmallTile(text.title, w, h, onClick, fill = colors.accent) {
            Icon(painterResource(R.drawable.ic_remote), contentDescription = null, tint = ink, modifier = Modifier.size(30.dp))
        }
        return
    }
    val sub = roomNumber?.let(text::tvRoom) ?: text.tvOn
    WidgetTile(onClick, fill = colors.accent, borderless = true) {
        if (size == TileSize.M) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Kicker(text.title, R.drawable.ic_remote, color = ink.copy(alpha = 0.85f), iconColor = ink)
                Box(Modifier.padding(top = 6.dp)) { InkStatus(sub, ink) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally).offset(y = 4.dp)) { MiniDpad(98.dp, ink) }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Kicker(text.title, R.drawable.ic_remote, color = ink.copy(alpha = 0.85f), iconColor = ink)
                    Text(text.tvOn, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = HospitalityTheme.typography.label.copy(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 10.dp))
                    if (roomNumber != null) InkStatus(sub, ink)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        InkPill(R.drawable.ic_volume, "+ −", ink)
                        InkPill(R.drawable.ic_tv, "CH", ink)
                    }
                }
                MiniDpad(134.dp, ink)
            }
        }
    }
}

/** Punto y "TV de la habitación 101" sobre el acento del tile del control. */
@Composable
private fun InkStatus(label: String, ink: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(ink))
        Text(label, color = ink.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = HospitalityTheme.typography.label.copy(fontSize = 12.sp))
    }
}

/** Pastilla sobre el acento del tile del control (`.pill` con fondo `#00000024`). */
@Composable
private fun InkPill(icon: Int, label: String, ink: Color) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.14f)).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(label, color = ink, style = HospitalityTheme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun nowProgramOf(model: ContentModel, channel: Channel?, now: Long): com.networkbroadcast.hospitality.entertainment.Program? {
    if (channel == null) return null
    val schedule by produceState(model.cachedSchedule(channel.id), channel.id) { value = model.schedule(channel.id) }
    return schedule.orEmpty().nowAndNext(now).first
}

@Composable
private fun LiveTile(size: TileSize, model: ContentModel, now: Long, text: UiText, contentText: ContentText, w: Dp, h: Dp, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    if (size == TileSize.S) {
        SmallTile(contentText.tileChannels, w, h, onClick) {
            TileIcon(R.drawable.ic_live)
            Text("● LIVE", color = Color(0xFFE5484D), style = HospitalityTheme.typography.label.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 6.dp))
        }
        return
    }
    val channels = model.channels?.channels.orEmpty()
    val channel = channels.firstOrNull()
    val program = nowProgramOf(model, channel, now)
    WidgetTile(onClick) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                Text(contentText.nowOnTv.uppercase(), color = colors.textPrimary,
                    style = HospitalityTheme.typography.label.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.14.sp))
            }
            if (size == TileSize.M && channel?.logoUrl != null) {
                AsyncImage(model = channel.logoUrl, contentDescription = channel.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).size(110.dp, 46.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).then(if (size == TileSize.W) Modifier.fillMaxWidth(0.55f) else Modifier.fillMaxWidth())) {
                channel?.let {
                    Text("${it.number} · ${it.name}".uppercase(), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = HospitalityTheme.typography.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.47.sp))
                }
                Text(program?.title ?: channel?.name ?: text.liveTv, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.label.copy(fontSize = if (size == TileSize.W) 26.sp else 17.sp, lineHeight = if (size == TileSize.W) 28.sp else 19.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 3.dp))
                program?.let {
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceBorder)) {
                        Box(Modifier.fillMaxWidth(it.progress(now)).fillMaxHeight().background(LocalPlayerColor.current))
                    }
                }
            }
            if (size == TileSize.W) {
                // Los tres canales que siguen, con lo que dan ahora (.chip3).
                Column(Modifier.align(Alignment.TopEnd).width(136.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    channels.drop(1).take(3).forEach { other ->
                        val title = nowProgramOf(model, other, now)?.title ?: other.name
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(colors.textPrimary.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${other.number}", color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            Text(title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = HospitalityTheme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/** Pósters distintos para los tiles de películas, en el orden de las estanterías de Panaccess. */
private fun posters(model: ContentModel, count: Int): List<VodItem> =
    model.vod?.shelves.orEmpty().flatMap { it.items }.filter { it.posterUrl != null }.distinctBy { it.id }.take(count)

@Composable
private fun VodTile(size: TileSize, model: ContentModel, contentText: ContentText, onClick: () -> Unit, w: Dp, h: Dp) {
    val colors = HospitalityTheme.colors
    if (size == TileSize.S) {
        SmallTile(contentText.tileMovies, w, h, onClick) { TileIcon(R.drawable.ic_movie) }
        return
    }
    WidgetTile(onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Kicker(contentText.tileMovies, R.drawable.ic_movie)
            if (size == TileSize.M) {
                // Tres pósters en abanico que asoman por abajo.
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                    val items = posters(model, 3)
                    val spec = listOf(Triple(4.dp, -8f, 1), Triple(44.dp, -1f, 0), Triple(86.dp, 7f, 2))
                    spec.forEach { (x, angle, idx) ->
                        items.getOrNull(idx)?.let { item ->
                            AsyncImage(model = imageModel(item.posterUrl.orEmpty()), contentDescription = item.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.align(Alignment.BottomStart).offset(x = x, y = 26.dp).size(64.dp, 96.dp).rotate(angle).clip(RoundedCornerShape(9.dp)).background(colors.surfaceBorder))
                        }
                    }
                }
            } else {
                Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    posters(model, 4).forEach { item ->
                        Box(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceBorder)) {
                            AsyncImage(model = imageModel(item.posterUrl.orEmpty()), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiTile(size: TileSize, network: WifiNetwork, text: UiText, contentText: ContentText, onClick: () -> Unit, w: Dp, h: Dp) {
    val colors = HospitalityTheme.colors
    if (size == TileSize.S) {
        SmallTile(contentText.tileWifi, w, h, onClick) { TileIcon(R.drawable.ic_wifi) }
        return
    }
    val qr = remember(network) { qrImageBitmap(network.qrPayload) }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
    WidgetTile(onClick) {
        if (size == TileSize.M) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Kicker(text.wifiTitle, R.drawable.ic_wifi)
                Text(network.ssid, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(network.password.orEmpty(), style = mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp), maxLines = 3, modifier = Modifier.width(70.dp))
                    QrBox(qr, 74.dp, 5.dp)
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Kicker(text.wifiTitle, R.drawable.ic_wifi)
                    Spacer(Modifier.weight(1f))
                    Text(text.wifiNetwork.uppercase(), color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.33.sp))
                    Text(network.ssid, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = HospitalityTheme.typography.label.copy(fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
                    network.password?.let {
                        Text(text.wifiPassword.uppercase(), color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.33.sp))
                        Text(it, style = mono.copy(fontSize = 13.sp, color = colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                QrBox(qr, 124.dp, 7.dp)
            }
        }
    }
}

/** QR sobre blanco (`.qrbox`), sin suavizar para que lo lea la cámara. */
@Composable
fun QrBox(qr: androidx.compose.ui.graphics.ImageBitmap, size: Dp, padding: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(if (size > 150.dp) 18.dp else 9.dp)).background(Color.White).padding(padding)) {
        Image(qr, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun WeatherTile(size: TileSize, temp: Int?, description: String?, contentText: ContentText, w: Dp, h: Dp) {
    // Solo hay temperatura y descripción (Panaccess); no hay pantalla de clima ni más datos, así que el tile no se toca.
    val label = contentText.tileWeather
    if (size == TileSize.S) {
        val shape = RoundedCornerShape(rad(15))
        Box(Modifier.size(w, h)) {
            Box(Modifier.fillMaxSize().clip(shape).background(SKY), contentAlignment = Alignment.Center) {
                Text(temp?.let { "$it°" } ?: "–", color = Color.White, style = HospitalityTheme.typography.label.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            }
            Text(label, color = HospitalityTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = HospitalityTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium), modifier = Modifier.offset(x = (-4).dp, y = h + 4.dp).requiredWidth(w + 8.dp))
        }
        return
    }
    WidgetTile(null, fill = SKY, borderless = true) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Kicker(label, null, color = Color.White.copy(alpha = 0.8f))
            Text(temp?.let { "$it°" } ?: "–", color = Color.White,
                style = HospitalityTheme.typography.label.copy(fontSize = if (size == TileSize.W) 52.sp else 46.sp, lineHeight = if (size == TileSize.W) 52.sp else 46.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 8.dp))
            description?.let { Text(it, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = HospitalityTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun ServicesTile(size: TileSize, catalog: ServiceCatalog?, language: String, servicesText: ServicesText, onClick: () -> Unit, w: Dp, h: Dp) {
    val colors = HospitalityTheme.colors
    if (size == TileSize.S) {
        SmallTile(servicesText.title, w, h, onClick) { TileIcon(R.drawable.ic_bell) }
        return
    }
    val names = catalog?.categories?.map { it.name.localized(language) }.orEmpty().take(4)
    WidgetTile(onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Kicker(servicesText.title, R.drawable.ic_bell)
            Column(Modifier.padding(top = if (size == TileSize.W) 12.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(if (size == TileSize.W) 8.dp else 6.dp)) {
                names.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (size == TileSize.W) 8.dp else 6.dp)) {
                        pair.forEach { name ->
                            Text(name, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = HospitalityTheme.typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(colors.textPrimary.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 7.dp))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelTile(size: TileSize, data: HomeData, text: UiText, hotelName: String?, onClick: () -> Unit, w: Dp, h: Dp) {
    val colors = HospitalityTheme.colors
    if (size == TileSize.S) {
        SmallTile(text.aboutHotel, w, h, onClick) {
            if (data.logoUrl != null) AsyncImage(model = imageModel(data.logoUrl), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(34.dp))
            else TileIcon(R.drawable.ic_info, 28.dp)
        }
        return
    }
    val photo = data.hotel?.images?.firstOrNull()?.let { it.thumbnailUrl ?: it.url }
    if (photo != null) {
        PhotoWidgetTile(photo, size, kicker = text.aboutHotel, title = hotelName.orEmpty(), description = null, onClick = onClick)
    } else {
        WidgetTile(onClick) {
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Kicker(text.aboutHotel)
                Text(hotelName.orEmpty(), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = HospitalityTheme.typography.label.copy(fontSize = if (size == TileSize.W) 28.sp else 21.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
