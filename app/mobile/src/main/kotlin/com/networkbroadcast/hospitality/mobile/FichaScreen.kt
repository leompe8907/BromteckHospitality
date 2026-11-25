package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/** Una foto de la galería de una ficha; [title] y [subtitle] salen sobre la foto cuando los hay. */
data class FichaPhoto(val url: String, val title: String? = null, val subtitle: String? = null)

/**
 * Pantalla de ficha del mockup (`s_hotel`: `.hhero`, `.hbody`, `.gth`), la misma para un widget de la cuenta
 * y para la ficha del hotel. De arriba abajo: la barra superior con volver, la galería grande (16 dp de
 * margen, la mitad del alto de la pantalla, esquinas suaves, se pasa con el dedo, puntos arriba a la
 * derecha), el título (28 sp), la línea de datos en una píldora (color de acento) y el texto en una
 * tarjeta (14,5 sp, interlineado 1,6; con "Leer más" si es largo). Sin grilla de miniaturas: repetía las fotos cuando había pocas. Ocupa toda la pantalla y se
 * desplaza si hace falta. [extra] va al final, para lo que sólo tiene una ficha.
 */
@Composable
fun FichaScreen(
    topTitle: String,
    title: String,
    metaLine: String?,
    metaIcon: Int,
    paragraphs: List<String>,
    photos: List<FichaPhoto>,
    text: ContentText,
    onBack: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = HospitalityTheme.colors
    val scroll = rememberScrollState()
    val pager = rememberPagerState(pageCount = { photos.size })
    var expanded by remember { mutableStateOf(false) }
    val body = paragraphs.filter { it.isNotBlank() }
    val long = body.joinToString("").length > 260

    Column(Modifier.fillMaxSize()) {
        TopBar(topTitle, onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // La foto ocupa la mitad de la pantalla (entre 240 y 460 dp) para que una ficha con poco texto no quede vacía.
            val heroHeight = (maxHeight * 0.5f).coerceIn(240.dp, 460.dp)
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                if (photos.isNotEmpty()) {
                    val heroShape = RoundedCornerShape(rad(12))
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().height(heroHeight).clip(heroShape)) {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            val photo = photos[page]
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(model = imageModel(photo.url), contentDescription = photo.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (photo.title != null) {
                                    Column(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x8C000000)).padding(horizontal = 16.dp, vertical = 14.dp),
                                    ) {
                                        Text(photo.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = HospitalityTheme.typography.title.copy(fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                                        photo.subtitle?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                style = HospitalityTheme.typography.body.copy(fontSize = 12.5.sp, lineHeight = 17.sp), modifier = Modifier.padding(top = 3.dp))
                                        }
                                    }
                                }
                            }
                        }
                        if (photos.size > 1) {
                            Row(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(photos.size) { i ->
                                    val on = i == pager.currentPage
                                    Box(Modifier.size(width = if (on) 16.dp else 6.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) Color.White else Color(0x66FFFFFF)))
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 40.dp)) {
                    Text(title, color = colors.textPrimary, style = HospitalityTheme.typography.title.copy(fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 4.dp))
                    metaLine?.let {
                        val pill = RoundedCornerShape(50)
                        Row(
                            Modifier.padding(top = 4.dp, bottom = 14.dp).clip(pill).background(colors.surface).border(1.dp, colors.surfaceBorder, pill).padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(painterResource(metaIcon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                            Text(it, color = colors.accent, style = HospitalityTheme.typography.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    if (body.isNotEmpty()) {
                        val card = RoundedCornerShape(rad(18))
                        Column(Modifier.fillMaxWidth().clip(card).background(colors.surface).border(1.dp, colors.surfaceBorder, card).padding(16.dp)) {
                            Text(
                                body.joinToString("\n\n"), color = colors.textSecondary,
                                style = HospitalityTheme.typography.body.copy(fontSize = 14.5.sp, lineHeight = 23.2.sp),
                                maxLines = if (long && !expanded) 7 else Int.MAX_VALUE, overflow = TextOverflow.Clip,
                            )
                            if (long) {
                                Text(
                                    if (expanded) text.readLess else text.readMore, color = colors.accent,
                                    style = HospitalityTheme.typography.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(top = 10.dp).clickable { expanded = !expanded },
                                )
                            }
                        }
                    }
                    extra()
                }
            }
        }
    }
}
