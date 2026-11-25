package com.networkbroadcast.hospitality.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily

/**
 * Una sola fuente en todo el celular: la de texto de la marca ([HospitalityBodyFamily], Rubik), que
 * es la que usa la TV en su home (tiles, títulos, saludo, diálogos). Se pidió una familia sola;
 * por eso también los títulos (`display`/`title`) se arman con ella, no con la serif de los decks.
 */
val MobileFontFamily: FontFamily = HospitalityBodyFamily

private fun TextStyle.rubik() = copy(fontFamily = MobileFontFamily)

/** Typography de Material con Rubik en todos los estilos: lo que dibuja Material (campos de texto, etiquetas) no cae en Roboto. */
private fun mobileMaterialTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.rubik(), displayMedium = base.displayMedium.rubik(), displaySmall = base.displaySmall.rubik(),
        headlineLarge = base.headlineLarge.rubik(), headlineMedium = base.headlineMedium.rubik(), headlineSmall = base.headlineSmall.rubik(),
        titleLarge = base.titleLarge.rubik(), titleMedium = base.titleMedium.rubik(), titleSmall = base.titleSmall.rubik(),
        bodyLarge = base.bodyLarge.rubik(), bodyMedium = base.bodyMedium.rubik(), bodySmall = base.bodySmall.rubik(),
        labelLarge = base.labelLarge.rubik(), labelMedium = base.labelMedium.rubik(), labelSmall = base.labelSmall.rubik(),
    )
}

/**
 * Va dentro de `HospitalityTheme`: deja la fuente de la TV como la de Material y como estilo de texto
 * por defecto, para que un `Text` o un `OutlinedTextField` sin estilo propio no use la del sistema.
 */
@Composable
fun MobileFonts(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = mobileMaterialTypography(),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = MobileFontFamily), content)
    }
}
