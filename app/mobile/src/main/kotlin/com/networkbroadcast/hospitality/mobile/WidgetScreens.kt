package com.networkbroadcast.hospitality.mobile

import androidx.compose.runtime.Composable
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.panaccess.HomeWidget
import com.networkbroadcast.hospitality.panaccess.OperatorConfig

/**
 * Los widgets de la cuenta (X_APP_WIDGETS), en el idioma de la cuenta y en el orden en que los manda
 * Panaccess: los mismos que muestra la TV. Sin config o sin widgets, la lista vacía (no se muestra nada).
 */
fun accountWidgets(config: OperatorConfig?, language: String): List<HomeWidget> =
    config?.widgets(language).orEmpty()

/** Línea de datos de un widget, como en la ficha de la TV: horario y piso ("Piso 5"). null si no hay ninguno. */
fun widgetMeta(widget: HomeWidget, floorLabel: String): String? =
    listOfNotNull(widget.hours, widget.floor?.let { "$floorLabel $it" }).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Las fotos de la ficha: la galería, o la foto de la tarjeta si la galería vino vacía (igual que la TV). */
fun widgetGallery(widget: HomeWidget): List<String> = widget.gallery.ifEmpty { listOf(widget.imageUrl) }

/**
 * Ficha de un widget de la cuenta: la pantalla de ficha del mockup ([FichaScreen]) con las fotos, el
 * nombre, el horario y el piso, y la descripción. Solo lectura, con el mismo contenido que la ficha de la TV.
 */
@Composable
fun WidgetDetailScreen(widget: HomeWidget, text: UiText, contentText: ContentText, onBack: () -> Unit) {
    FichaScreen(
        topTitle = text.highlights,
        title = widget.title,
        metaLine = widgetMeta(widget, text.floorLabel),
        metaIcon = R.drawable.ic_clock,
        paragraphs = widget.description?.split("\n\n").orEmpty(),
        photos = widgetGallery(widget).map { FichaPhoto(it) },
        text = contentText,
        onBack = onBack,
    )
}
