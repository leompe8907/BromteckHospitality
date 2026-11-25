package com.networkbroadcast.hospitality.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.LobbyText
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.designsystem.HospitalityBodyFamily
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * PRUEBA de la dirección "Lobby": pantalla de reposo. Aparece tras un rato sin
 * tocar el control (fuera del reproductor) y se va con cualquier tecla. Cambia con el clima que ya
 * resuelve Panaccess (OperatorConfig.weatherDescription): despejado = foto tal cual, nublado =
 * foto desaturada, lluvia = más oscura y con lluvia animada; de noche, más oscura. Si la cuenta no
 * trae clima, se ve la versión neutra (foto + hora + fecha), sin inventar datos.
 */

/** Tiempo sin tocar el control hasta que aparece la pantalla de reposo (el prototipo usa 45 s). */
const val LOBBY_IDLE_MS = 45_000L

private enum class Sky { CLEAR, CLOUDY, RAIN }

private fun skyOf(description: String?): Sky {
    val d = description?.lowercase() ?: return Sky.CLEAR
    return when {
        listOf("lluv", "llovizna", "rain", "drizzle", "chuv", "torment", "storm", "tempest", "chubasco", "shower").any { it in d } -> Sky.RAIN
        listOf("nub", "cloud", "overcast", "niebla", "fog", "mist", "brum", "neblina", "cubierto").any { it in d } -> Sky.CLOUDY
        else -> Sky.CLEAR
    }
}

private fun scaled(m: ColorMatrix, b: Float) = m.apply {
    timesAssign(ColorMatrix(floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
}

/** Cómo se escribe la fecha larga en cada idioma ("jueves 20 de noviembre", "Thursday, November 20"). */
private fun datePattern(language: String) = when (language) {
    "en" -> "EEEE, MMMM d"
    "de" -> "EEEE, d. MMMM"
    "fr" -> "EEEE d MMMM"
    else -> "EEEE d 'de' MMMM"
}

@Composable
fun LobbyScreensaver(
    brand: BrandConfig,
    /** Logo que cargó el operador (X_APP_LOGO); null = el de la marca, si lo trae. */
    logoUrl: String?,
    images: List<String>,
    language: String,
    weatherTempCelsius: Int?,
    weatherDescription: String?,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1_000) } }
    var index by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) { while (images.size > 1) { delay(20_000); index = (index + 1) % images.size } }

    val hasWeather = weatherTempCelsius != null
    val sky = if (hasWeather) skyOf(weatherDescription) else Sky.CLEAR
    val night = now.hour >= 19 || now.hour < 7
    val filter = remember(sky, night) {
        val m = when (sky) {
            Sky.CLEAR -> scaled(ColorMatrix(), if (night) 0.8f else 1f)
            Sky.CLOUDY -> scaled(ColorMatrix().apply { setToSaturation(0.25f) }, if (night) 0.62f else 0.78f)
            Sky.RAIN -> scaled(ColorMatrix().apply { setToSaturation(0.3f) }, if (night) 0.45f else 0.55f)
        }
        ColorFilter.colorMatrix(m)
    }
    val locale = Locale.forLanguageTag(language)
    val date = now.format(DateTimeFormatter.ofPattern(datePattern(language), locale)).replaceFirstChar { it.titlecase(locale) }
    val weatherLine = if (hasWeather) listOfNotNull("$weatherTempCelsius°", weatherDescription?.replaceFirstChar { it.titlecase(locale) }).joinToString(" · ") else null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (images.isNotEmpty()) {
            Crossfade(targetState = images[index % images.size], animationSpec = tween(2_400), label = "idle-photo") { url ->
                AsyncImage(
                    model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop,
                    colorFilter = filter, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (sky == Sky.RAIN) Rain()
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(0f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.7f), radius = 1400f),
            ),
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            (logoUrl ?: if (brand.showLogo) imageModel("asset:logo.png") else null)?.let {
                AsyncImage(
                    model = it, contentDescription = brand.displayName, contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(top = 36.dp).height(22.dp),
                )
            }
            Text(
                "%02d:%02d".format(now.hour, now.minute),
                style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 150.sp, lineHeight = 150.sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp),
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 92.dp),
            )
            Text(
                listOfNotNull(date, weatherLine).joinToString("  ·  "),
                style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 17.sp),
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
            )
        }
        Text(
            LobbyText.of(language).pressAnyKey,
            style = TextStyle(fontFamily = HospitalityBodyFamily, fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 36.dp),
        )
    }
}

/** Lluvia: dos capas de trazos finos en diagonal que bajan a distinta velocidad. */
@Composable
private fun Rain() {
    val t = rememberInfiniteTransition(label = "rain")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rain-a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "rain-b")
    Canvas(Modifier.fillMaxSize()) {
        fun layer(phase: Float, gap: Float, len: Float, alpha: Float, seed: Int) {
            val color = Color(0xFFDCE8FF).copy(alpha = alpha)
            var x = -size.height * 0.2f + (seed * 37 % gap.toInt())
            var row = 0
            while (x < size.width + size.height * 0.2f) {
                val step = 150f
                var y = ((phase * step + row * 113f) % step) - len
                while (y < size.height) {
                    drawLine(color, Offset(x, y), Offset(x - len * 0.18f, y + len), strokeWidth = 1.4f)
                    y += step
                }
                x += gap; row++
            }
        }
        layer(a, 38f, 46f, 0.20f, 1)
        layer(b, 67f, 30f, 0.14f, 2)
    }
}
