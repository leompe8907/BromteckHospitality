package com.networkbroadcast.hospitality.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/*
 * Piezas comunes copiadas del mockup (docs/mockup-celular/index.html; 1 px del mockup = 1 dp):
 * botón de volver (.rb0) y barra superior (.top). El radio de las tarjetas depende de la dirección
 * visual de la marca (variable --tr del mockup, ver [shapeFactorFor]).
 */

/** Factor de radios de la dirección visual (`--tr` del mockup): RESORT 1, CINEMATIC .72, MINIMAL 1.05, LOBBY .55. */
val LocalShapeFactor = staticCompositionLocalOf { 1f }

fun shapeFactorFor(direction: String): Float = when (direction.uppercase()) {
    "CINEMATIC" -> 0.72f
    "MINIMAL" -> 1.05f
    "LOBBY" -> 0.55f
    else -> 1f
}

/** `calc(base*tr + add)` del mockup, en dp. */
@Composable
fun rad(base: Int, add: Int = 0): Dp = (base * LocalShapeFactor.current + add).dp

/** Color del control del player (X_APP_PLAYER_CONTROL_COLOR; sin él, el acento), la variable `--pctl` del mockup. */
val LocalPlayerColor = staticCompositionLocalOf { Color.Unspecified }

/** Texto de accesibilidad del botón de volver (en el idioma de la cuenta). */
val LocalBackLabel = staticCompositionLocalOf { "" }

/**
 * Botón de volver del mockup (`.rb0`): círculo de 40 dp con el fondo y el borde de las tarjetas, y la
 * flecha `chevL` de 20 dp (trazo 2,2) en el color de texto. Es el mismo en todas las pantallas.
 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HospitalityTheme.colors
    Box(
        modifier.size(40.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.surfaceBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = LocalBackLabel.current, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Barra superior del mockup (`.top`): volver y el título (20 sp, seminegrita), con 16 dp a los lados.
 * La parte de arriba (barra de estado) la deja libre quien la dibuja con `systemBarsPadding`.
 */
@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = HospitalityTheme.colors
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackButton(onBack)
        Text(
            title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = HospitalityTheme.typography.title.copy(fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
