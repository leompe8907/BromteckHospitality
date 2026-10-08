package com.networkbroadcast.hospitality.designsystem

import androidx.compose.ui.graphics.Color
import com.networkbroadcast.hospitality.brand.BrandConfig

/** Dirección visual de la marca + sus sobrescrituras de color ("accent": "#RRGGBB", etc.). */
fun BrandConfig.themeColors(): HospitalityColors {
    val direction = runCatching { VisualDirection.valueOf(direction.uppercase()) }
        .getOrDefault(VisualDirection.RESORT)
    val base = HospitalityColors.of(direction)
    fun pick(key: String, default: Color) = colors[key]?.let(::parseHex) ?: default
    return base.copy(
        background = pick("background", base.background),
        surface = pick("surface", base.surface),
        accent = pick("accent", base.accent),
        focus = pick("focus", base.focus),
    )
}

internal fun parseHex(hex: String): Color? = runCatching {
    val clean = hex.removePrefix("#")
    val argb = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return null
    }
    Color(argb.toLong(16))
}.getOrNull()
