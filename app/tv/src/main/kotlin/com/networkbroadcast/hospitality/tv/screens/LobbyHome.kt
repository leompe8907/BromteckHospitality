package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors
import com.networkbroadcast.hospitality.panaccess.HomeWidget
import com.networkbroadcast.hospitality.tv.HomeAction
import com.networkbroadcast.hospitality.tv.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/*
 * PRUEBA DE IMPLEMENTACIÓN — dirección "Lobby" (20-nov-2026).
 *
 * Home sin filas: la foto del hotel de fondo, un encabezado chico y nueve lugares de cuatro
 * tamaños en posiciones fijas (principal, cuatro medianos, dos anchos, dos chicos), como el
 * prototipo Papeleria/hospitality-tv/lobby.html. Se activa con "direction": "LOBBY" en brand.json;
 * el resto de la app no cambia.
 *
 * En esta prueba el reparto de piezas es automático (por prioridad, según lo que la marca tenga
 * prendido). En la versión real lo elige el hotel con un bloque "home" en brand.json.
 * Colores, logos y textos salen de lo que configura el operador (ver LobbyStyle.kt).
 */

/** Lienzo de la TV en dp: 960×540 (1920×1080 px a densidad 2). Mismas medidas que el prototipo v4. */
private val Pad = 48.dp

private data class Slot(val x: Dp, val y: Dp, val w: Dp, val h: Dp)

private val Hero = Slot(48.dp, 88.dp, 424.dp, 268.dp)
private val Bigs = listOf(Slot(488.dp, 88.dp, 208.dp, 130.dp), Slot(704.dp, 88.dp, 208.dp, 130.dp), Slot(488.dp, 226.dp, 208.dp, 130.dp), Slot(704.dp, 226.dp, 208.dp, 130.dp))
private val Wides = listOf(Slot(48.dp, 372.dp, 280.dp, 118.dp), Slot(336.dp, 372.dp, 280.dp, 118.dp))
private val Smalls = listOf(Slot(624.dp, 372.dp, 140.dp, 118.dp), Slot(772.dp, 372.dp, 140.dp, 118.dp))

/** Lo que ocupa un lugar: una acción de la app o un widget del hotel. */
private sealed interface Tile {
    data class Act(val a: HomeAction) : Tile
    data class Hotel(val w: HomeWidget) : Tile
}

@Composable
fun LobbyHome(
    brand: BrandConfig,
    text: UiText,
    logoUrl: String?,
    roomNumber: String?,
    weatherText: String?,
    subscriberName: String?,
    quickActions: List<HomeAction>,
    vodHasContent: Boolean,
    previews: HomePreviews,
    services: HomeServices?,
    widgets: List<HomeWidget>,
    onOpenWidget: (HomeWidget) -> Unit,
    focusOn: HomeAction?,
    onAction: (HomeAction) -> Unit,
    pairing: com.networkbroadcast.hospitality.companion.TvPairing? = null,
    languageCode: String? = null,
    /** true si algún canal tiene catch-up (mismo criterio que la home de siempre). */
    catchUpAvailable: Boolean = false,
    /** Segundo logo del hotel (tvModel de la licencia); null = no hay. */
    secondLogoUrl: String? = null,
) {
    val base = HospitalityTheme.colors
    val accent = lobbyConfirmColor()
    val lobby = LocalLobbyText.current
    val name = subscriberName?.takeIf { it.isNotBlank() }
    val initials = name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }
    // Fecha en el idioma que eligió el huésped, no en el del equipo.
    val locale = remember(languageCode) { Locale.forLanguageTag(languageCode ?: brand.defaultLanguage) }
    val now = remember { java.util.Date() }
    val timeText = remember(now) { SimpleDateFormat("HH:mm", locale).format(now) }
    val dayText = remember(now, locale) { SimpleDateFormat("EEEE,", locale).format(now).replaceFirstChar { it.titlecase(locale) } }
    val dateText = remember(now, locale) { SimpleDateFormat("d MMM yyyy", locale).format(now) }

    // Reparto automático por prioridad (en la versión real lo elige el hotel en brand.json -> "home").
    val has = { a: HomeAction ->
        when (a) {
            HomeAction.LiveTv -> brand.features.liveTv
            HomeAction.Guide -> brand.features.liveTv && brand.features.guide
            HomeAction.Vod -> brand.features.vod && vodHasContent
            HomeAction.CatchUp -> brand.features.guide && catchUpAvailable
            HomeAction.Services -> services != null
            else -> a in quickActions
        }
    }
    val hero: Tile = widgets.firstOrNull()?.let { Tile.Hotel(it) } ?: Tile.Act(HomeAction.LiveTv)
    // Catch-up va al final del pool: no mueve nada de lo que ya estaba, entra solo si queda lugar.
    val pool = listOf(HomeAction.LiveTv, HomeAction.Guide, HomeAction.Vod, HomeAction.AboutHotel, HomeAction.PairPhone, HomeAction.CatchUp)
        .filter { has(it) && Tile.Act(it) != hero }
    val bigs = pool.take(4).map { Tile.Act(it) }
    val wides = buildList<Tile> {
        if (has(HomeAction.Services)) add(Tile.Act(HomeAction.Services))
        widgets.drop(1).firstOrNull()?.let { add(Tile.Hotel(it)) }
        pool.drop(4).forEach { add(Tile.Act(it)) }
    }.take(2)
    val smalls = listOf(HomeAction.Language, HomeAction.Checkout).filter { has(it) }.map { Tile.Act(it) }

    val all = listOf(hero) + bigs + wides + smalls
    val target: Tile = focusOn?.let { f -> all.firstOrNull { it == Tile.Act(f) } } ?: hero
    val first = rememberInitialFocus(target)

    // Fondo: la foto del hotel ya la dibuja HotelBackdrop detrás; acá solo el velo para leer.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xC7000000), 0.18f to Color(0x59000000), 0.55f to Color(0x73000000), 1f to Color(0xE6000000)))) {

        // Encabezado del día a día: logo a la izquierda; clima, hora, "Hola" y habitación a la derecha.
        Row(Modifier.fillMaxWidth().padding(horizontal = Pad).padding(top = 25.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            // Logo que cargó el operador (X_APP_LOGO); si no hay, el de la marca; si tampoco, el nombre.
            (logoUrl ?: if (brand.showLogo) imageModel("asset:logo.png") else null)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(26.dp).widthIn(max = 220.dp))
            } ?: Text(brand.displayName.uppercase(), style = serif(15.sp).copy(letterSpacing = 4.sp), color = base.textPrimary)
            secondLogoUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(start = 18.dp).height(26.dp).widthIn(max = 120.dp))
            }
            Spacer(Modifier.weight(1f))
            val small = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 9.5.sp, color = base.textPrimary.copy(alpha = 0.78f))
            val strong = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = base.textPrimary)
            val sep = @Composable { Box(Modifier.padding(horizontal = 15.dp).width(1.dp).height(30.dp).background(base.textPrimary.copy(alpha = 0.28f))) }
            weatherText?.let {
                Column { Text(it.substringAfter(" · ", "").ifBlank { " " }, style = small); Text(it.substringBefore(" · "), style = strong) }
                sep()
            }
            Text(timeText, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = base.textPrimary))
            Column(Modifier.padding(start = 8.dp)) { Text(dayText, style = small); Text(dateText, style = strong.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp)) }
            name?.let {
                sep()
                Column(horizontalAlignment = Alignment.End) { Text(lobby.hello, style = small); Text(it, style = strong) }
                Box(Modifier.padding(start = 8.dp).size(32.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(initials.orEmpty(), style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HospitalityColors.readableTextColor(accent)))
                }
            }
            roomNumber?.let { sep(); Column { Text(text.room, style = small); Text(it, style = strong) } }
        }

        // Piezas translúcidas sobre la foto.
        val glass = lobbyGlass()
        CompositionLocalProvider(LocalHospitalityColors provides glass) {
            val place = @Composable { t: Tile, s: Slot, size: Char ->
                val focus = first.takeIf { t == target }
                when (t) {
                    is Tile.Hotel -> Piece(s, { onOpenWidget(t.w) }, focus) { HeroWidget(t.w, accent, text.floorLabel, big = size == 'L') }
                    is Tile.Act -> Piece(s, { onAction(t.a) }, focus) {
                        when {
                            size == 'L' -> LiveTile(previews, text, big = true)
                            t.a == HomeAction.PairPhone && pairing != null -> PairTile(pairing, text, size == 'M')
                            t.a == HomeAction.AboutHotel -> AboutTile(widgets, text)
                            t.a == HomeAction.Services -> BigTile(t.a, text, previews, services)
                            t.a == HomeAction.Language -> LanguageTile(text, languageCode)
                            t.a == HomeAction.Checkout -> CheckoutTile(text, roomNumber)
                            size == 'M' -> BigTile(t.a, text, previews, services)
                            else -> LabelTile(t.a, text, services, wide = size == 'W')
                        }
                    }
                }
            }
            place(hero, Hero, 'L')
            bigs.forEachIndexed { i, t -> place(t, Bigs[i], 'M') }
            wides.forEachIndexed { i, t -> place(t, Wides[i], 'W') }
            smalls.forEachIndexed { i, t -> place(t, Smalls[i], 'S') }
        }
    }
}

@Composable
private fun Piece(
    slot: Slot,
    onClick: () -> Unit,
    focus: androidx.compose.ui.focus.FocusRequester?,
    content: @Composable BoxScope.() -> Unit,
) {
    FocusCard(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.offset(x = slot.x, y = slot.y).size(slot.w, slot.h)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
    ) { content() }
}

private fun serif(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    // Una sola familia en toda la home (pedido de Esteban): la de texto, también en los títulos.
    fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily,
    fontWeight = FontWeight.Medium,
    fontSize = size,
)

@Composable
private fun BoxScope.Scrim() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x0D000000), 0.5f to Color(0x73000000), 1f to Color(0xEB000000))))
}

@Composable
private fun Kicker(s: String, color: Color = HospitalityTheme.colors.textPrimary) =
    Text(s.uppercase(), style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp), color = color.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun BoxScope.HeroWidget(w: HomeWidget, accent: Color, floorLabel: String, big: Boolean = true) {
    val c = HospitalityTheme.colors
    AsyncImage(model = imageModel(w.imageUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Scrim()
    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Kicker(listOfNotNull(w.hours, w.floor?.let { "$floorLabel $it" }).joinToString(" · ").ifEmpty { " " }, accent)
        Text(w.title, style = serif(if (big) 40.sp else 20.sp).copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        w.description?.takeIf { big }?.let { Text(it, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 12.sp), color = c.textPrimary.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun BoxScope.LiveTile(p: HomePreviews, text: UiText, big: Boolean) {
    val c = HospitalityTheme.colors
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B3A5C), Color(0xFF0B0F1A), Color(0xFF5A3020)))))
    p.liveLogoUrl?.let {
        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.align(Alignment.Center).padding(bottom = 30.dp).size(if (big) 160.dp else 90.dp, if (big) 80.dp else 46.dp))
    }
    Scrim()
    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
            Spacer(Modifier.width(6.dp))
            Kicker(p.liveChannelName ?: text.liveNow)
        }
        Text(
            p.liveProgram ?: text.liveNow,
            style = if (big) serif(30.sp) else TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun BoxScope.BigTile(a: HomeAction, text: UiText, p: HomePreviews, services: HomeServices?) {
    val c = HospitalityTheme.colors
    when (a) {
        HomeAction.LiveTv -> LiveTile(p, text, big = false)
        HomeAction.Vod -> {
            p.vodBackdropUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Scrim()
            TileText(text.moviesAndSeries, null)
        }
        HomeAction.Guide, HomeAction.CatchUp -> {
            Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.guideLogos.take(3).forEach {
                    Box(Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(5.dp)).background(Color.White).padding(3.dp), contentAlignment = Alignment.Center) {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            TileText(if (a == HomeAction.CatchUp) text.catchUp else text.guide, null)
        }
        HomeAction.Services -> {
            services?.imageUrl?.let { AsyncImage(model = imageModel(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()); Scrim() }
            TileText(services?.title ?: "", services?.categories)
        }
        else -> TileText(label(a, text, services), null)
    }
}

@Composable
private fun BoxScope.TileText(title: String, kicker: String?) {
    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
        kicker?.takeIf { it.isNotBlank() }?.let { Kicker(it) }
        Text(title, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = HospitalityTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BoxScope.LabelTile(a: HomeAction, text: UiText, services: HomeServices?, wide: Boolean) {
    val c = HospitalityTheme.colors
    Icon(painterResource(a.lobbyIcon()), contentDescription = null, tint = c.textPrimary.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(18.dp))
    Text(
        label(a, text, services),
        style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = if (wide) 15.sp else 12.sp, fontWeight = FontWeight.SemiBold),
        color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
    )
}

private fun label(a: HomeAction, text: UiText, services: HomeServices?) = when (a) {
    HomeAction.AboutHotel -> text.aboutHotel
    HomeAction.PairPhone -> text.pairPhone
    HomeAction.Language -> text.language
    HomeAction.Checkout -> text.checkout
    HomeAction.LiveTv -> text.liveTv
    HomeAction.Guide -> text.guide
    HomeAction.Vod -> text.moviesAndSeries
    HomeAction.CatchUp -> text.catchUp
    HomeAction.Services, HomeAction.MyOrders -> services?.title.orEmpty()
}

private fun HomeAction.lobbyIcon(): Int = when (this) {
    HomeAction.AboutHotel -> R.drawable.ic_info
    HomeAction.PairPhone -> R.drawable.ic_phone
    HomeAction.Language -> R.drawable.ic_language
    HomeAction.Checkout -> R.drawable.ic_checkout
    HomeAction.Services, HomeAction.MyOrders -> R.drawable.ic_bell
    else -> R.drawable.ic_live
}


/* ---- piezas con contenido propio (feedback de Enrique: no dejarlas vacías) ---- */

@Composable
private fun BoxScope.PairTile(pairing: com.networkbroadcast.hospitality.companion.TvPairing, text: UiText, big: Boolean) {
    val c = HospitalityTheme.colors
    var code by remember { androidx.compose.runtime.mutableStateOf<com.networkbroadcast.hospitality.companion.PairingCode?>(null) }
    androidx.compose.runtime.LaunchedEffect(pairing) {
        while (true) {
            code = pairing.requestCode()
            val ttl = code?.let { it.expiresAtEpochSec - System.currentTimeMillis() / 1000 } ?: 60
            kotlinx.coroutines.delay(ttl.coerceAtLeast(30) * 1000)
        }
    }
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val qrSize = if (big) 82.dp else 78.dp
        Box(Modifier.size(qrSize).clip(RoundedCornerShape(7.dp)).background(Color.White).padding(5.dp), contentAlignment = Alignment.Center) {
            code?.let { pc ->
                val qr = remember(pc.qrPayload) { com.networkbroadcast.hospitality.designsystem.qrImageBitmap(pc.qrPayload) }
                androidx.compose.foundation.Image(qr, contentDescription = null, filterQuality = androidx.compose.ui.graphics.FilterQuality.None, modifier = Modifier.fillMaxSize())
            }
        }
        Column {
            Text(text.pairPhone, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 2)
            Text(LocalLobbyText.current.scanOrEnter, maxLines = 1, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 10.sp), color = c.textPrimary.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
            Text(code?.code?.chunked(3)?.joinToString(" ") ?: "··· ···", style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = c.textPrimary, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun BoxScope.AboutTile(widgets: List<HomeWidget>, text: UiText) {
    Row(Modifier.align(Alignment.TopStart).padding(14.dp)) {
        widgets.take(4).forEach {
            AsyncImage(model = imageModel(it.imageUrl), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(end = 0.dp).offset(x = (-6).dp * widgets.indexOf(it)).size(30.dp).clip(CircleShape))
        }
    }
    TileText(text.aboutHotel, widgets.take(4).joinToString(" · ") { it.title })
}

@Composable
private fun BoxScope.LanguageTile(text: UiText, code: String?) {
    val c = HospitalityTheme.colors
    Text((code ?: "es").uppercase(), style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary,
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
        Kicker(text.language)
        Text(UiText.languageNames[code ?: "es"] ?: "", style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary)
    }
}

@Composable
private fun BoxScope.CheckoutTile(text: UiText, room: String?) {
    val c = HospitalityTheme.colors
    Icon(painterResource(R.drawable.ic_checkout), contentDescription = null, tint = c.textPrimary.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(18.dp))
    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
        room?.let { Kicker("${text.room} $it") }
        Text(text.checkout, style = TextStyle(fontFamily = com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary)
    }
}
