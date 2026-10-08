package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.foundation.background
import com.networkbroadcast.hospitality.tv.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import com.networkbroadcast.hospitality.designsystem.LocalHospitalityColors
import com.networkbroadcast.hospitality.designsystem.InfoPill
import com.networkbroadcast.hospitality.designsystem.SectionTitle
import com.networkbroadcast.hospitality.tv.HomeAction
import com.networkbroadcast.hospitality.brand.UiText
import java.util.Calendar

/** Home del mockup "resort cálido": saludo, píldora de contexto, entretenimiento y acciones. */
@Composable
fun HomeScreen(
    brand: BrandConfig,
    text: UiText,
    roomNumber: String?,
    secondLogoUrl: String?,
    quickActions: List<HomeAction>,
    /** Lo que muestran las tarjetas de entretenimiento; todo opcional (mientras carga, tarjeta lisa). */
    previews: HomePreviews = HomePreviews(),
    /** Fila "Servicios del hotel" (pedidos); null si la marca no los ofrece. */
    services: HomeServices? = null,
    /** Tarjeta que recibe el foco: la que abrió la pantalla de la que se vuelve, o la primera. */
    focusOn: HomeAction?,
    onAction: (HomeAction) -> Unit,
) {
    val colors = HospitalityTheme.colors
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> text.goodMorning
        in 12..19 -> text.goodAfternoon
        else -> text.goodEvening
    }
    val context = listOfNotNull(brand.location, roomNumber?.let { "${text.room} $it" })
    val entertainment = buildList {
        if (brand.features.liveTv) add(HomeAction.LiveTv to text.liveNow)
        if (brand.features.liveTv && brand.features.guide) add(HomeAction.Guide to text.guide)
        if (brand.features.vod) add(HomeAction.Vod to text.moviesAndSeries)
    }
    val serviceActions = if (services != null) listOf(HomeAction.Services, HomeAction.MyOrders) else emptyList()
    val all = entertainment.map { it.first } + serviceActions + quickActions
    val target = focusOn?.takeIf { it in all } ?: all.firstOrNull()
    val first = rememberInitialFocus(target)

    // Con foto de fondo, tarjetas levemente translúcidas: se ve la playa detrás sin perder lectura.
    val base = HospitalityTheme.colors
    val cardColors = if (brand.backgrounds.isEmpty()) base else base.copy(surface = base.surface.copy(alpha = 0.82f))
    CompositionLocalProvider(LocalHospitalityColors provides cardColors) {
    Row(Modifier.fillMaxSize()) {
        // Barra lateral angosta del mockup; por ahora sólo identidad (la navegación va en la home).
        Box(Modifier.width(24.dp).fillMaxHeight().background(colors.rail.copy(alpha = if (brand.backgrounds.isEmpty()) 1f else 0.6f)))

        // Desplazable: en 960x540 dp (TV 1080p) las dos filas no entran juntas. El foco hace scroll
        // solo: al pasar a "Acciones rápidas", la columna sube lo necesario para mostrarla.
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(greeting, style = HospitalityTheme.typography.display, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                secondLogoUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.height(56.dp).width(200.dp))
                }
            }
            if (context.isNotEmpty()) InfoPill(context.joinToString(" · "))

            if (entertainment.isNotEmpty()) {
                SectionTitle(text.entertainment)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    entertainment.forEachIndexed { index, (action, label) ->
                        FocusCard(
                            onClick = { onAction(action) },
                            modifier = Modifier.height(150.dp)
                                .weight(if (index == 0) 2f else 1f)
                                .then(if (action == target) Modifier.focusRequester(first) else Modifier),
                        ) {
                            when (action) {
                                HomeAction.LiveTv -> LivePreview(previews)
                                HomeAction.Guide -> GuidePreview(previews)
                                HomeAction.Vod -> VodPreview(previews)
                                else -> Unit
                            }
                            // En la tarjeta en vivo el texto deja libre el lugar del logo (a la derecha):
                            // si no, un programa de nombre largo quedaba tapado por el logo.
                            val reserveEnd = if (action == HomeAction.LiveTv && previews.liveLogoUrl != null) LiveLogoSpace else 0.dp
                            Column(Modifier.align(Alignment.BottomStart).padding(18.dp).padding(end = reserveEnd)) {
                                if (action == HomeAction.LiveTv) previews.liveProgram?.let {
                                    Text(
                                        it, style = HospitalityTheme.typography.body, color = colors.textSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(label, style = HospitalityTheme.typography.label, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }

            if (services != null) {
                SectionTitle(services.title)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    FocusCard(
                        onClick = { onAction(HomeAction.Services) },
                        modifier = Modifier.height(130.dp).weight(2f)
                            .then(if (HomeAction.Services == target) Modifier.focusRequester(first) else Modifier),
                    ) {
                        services.imageUrl?.let {
                            AsyncImage(model = imageModel(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            // Velo parejo además del degradado: la foto puede ser muy clara (atardecer) y el texto va encima.
                            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.35f)))
                            BottomScrim()
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text(services.categories, style = HospitalityTheme.typography.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(services.orderLabel, style = HospitalityTheme.typography.label, color = colors.textPrimary)
                        }
                    }
                    FocusCard(
                        onClick = { onAction(HomeAction.MyOrders) },
                        modifier = Modifier.height(130.dp).weight(1f)
                            .then(if (HomeAction.MyOrders == target) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.accent, modifier = Modifier.padding(18.dp).size(28.dp))
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            services.activeOrder?.let {
                                Text(it, style = HospitalityTheme.typography.body, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(services.myOrdersLabel, style = HospitalityTheme.typography.label, color = colors.textPrimary)
                        }
                    }
                }
            }

            if (quickActions.isNotEmpty()) {
                SectionTitle(text.quickActions)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    quickActions.forEach { action ->
                        val label = when (action) {
                            HomeAction.AboutHotel -> text.aboutHotel
                            HomeAction.PairPhone -> text.pairPhone
                            HomeAction.Language -> text.language
                            HomeAction.Checkout -> text.checkout
                            HomeAction.LiveTv -> text.liveTv
                            HomeAction.Guide -> text.guide
                            HomeAction.Vod -> text.moviesAndSeries
                            HomeAction.Services, HomeAction.MyOrders -> services?.title.orEmpty()
                        }
                        FocusCard(
                            onClick = { onAction(action) },
                            modifier = Modifier.size(width = 220.dp, height = 110.dp)
                                .then(if (action == target) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Icon(
                                painterResource(action.icon()), contentDescription = null, tint = colors.accent,
                                modifier = Modifier.padding(18.dp).size(28.dp),
                            )
                            Text(
                                label,
                                style = HospitalityTheme.typography.label,
                                color = colors.textPrimary,
                                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** La fila de servicios de la home: textos ya traducidos y el pedido en curso, si hay. */
data class HomeServices(
    val title: String,
    /** Categorías del catálogo: "Bebidas y champán · Spa y bienestar · Room service". */
    val categories: String,
    val orderLabel: String,
    val myOrdersLabel: String,
    val imageUrl: String?,
    /** El pedido abierto más reciente y su estado: "Botella de champán: En camino". */
    val activeOrder: String?,
)

/** Datos para que las tarjetas de la home no estén vacías. */
data class HomePreviews(
    /** Canal de "En vivo ahora": el último visto, o el primero. */
    val liveLogoUrl: String? = null,
    val liveChannelName: String? = null,
    /** "Ahora: <programa>" del canal de arriba. */
    val liveProgram: String? = null,
    /** Logos de los primeros canales, para la tarjeta de la guía. */
    val guideLogos: List<String> = emptyList(),
    /** Imagen apaisada de un título del catálogo, para la tarjeta de VOD. */
    val vodBackdropUrl: String? = null,
)

private fun HomeAction.icon(): Int = when (this) {
    HomeAction.AboutHotel -> R.drawable.ic_info
    HomeAction.PairPhone -> R.drawable.ic_phone
    HomeAction.Language -> R.drawable.ic_language
    HomeAction.Checkout -> R.drawable.ic_checkout
    else -> R.drawable.ic_live
}

private val LiveLogoWidth = 170.dp
private val LiveLogoSpace = LiveLogoWidth + 24.dp

@Composable
private fun BoxScope.LivePreview(p: HomePreviews) {
    val colors = HospitalityTheme.colors
    Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        // Punto "en vivo".
        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
        p.liveChannelName?.let {
            Text(
                it, style = HospitalityTheme.typography.body, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
    p.liveLogoUrl?.let {
        AsyncImage(
            model = it, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(width = LiveLogoWidth, height = 90.dp),
        )
    }
}

@Composable
private fun BoxScope.GuidePreview(p: HomePreviews) {
    if (p.guideLogos.isEmpty()) return
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Recuadros iguales con fondo blanco: los logos vienen con tamaños y fondos distintos
        // (unos blancos, otros transparentes) y sueltos se veían desparejos.
        p.guideLogos.take(3).forEach {
            Box(
                // Se reparten el ancho de la tarjeta: con tamaño fijo el tercero quedaba cortado.
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun BoxScope.VodPreview(p: HomePreviews) {
    p.vodBackdropUrl ?: return
    AsyncImage(model = p.vodBackdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    BottomScrim()
}
