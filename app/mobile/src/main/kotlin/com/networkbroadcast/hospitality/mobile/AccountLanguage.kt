package com.networkbroadcast.hospitality.mobile

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Idioma del celular: el de la cuenta de la TV, no el del sistema. Orden:
 *  1. [guestChoice]: el idioma que el huésped elija en la TV (todavía no llega al celular; cuando
 *     llegue por el QR, se pasa acá y nada más cambia).
 *  2. [operatorDefault]: lo que manda Panaccess (X_DEFAULT_LANGUAGE, `OperatorConfig.defaultLanguage`).
 *  3. [brandDefault]: el de la marca (`brand.defaultLanguage`), que también es el de la pantalla
 *     neutra, antes de tener sesión.
 * Un idioma que la marca no ofrece ([brandLanguages]) se ignora, igual que el selector de la TV.
 * Acepta etiquetas como "es-AR" o "pt_BR" (se queda con "es" / "pt").
 */
fun accountLanguage(
    operatorDefault: String?,
    brandDefault: String,
    brandLanguages: List<String>,
    guestChoice: String? = null,
): String {
    fun normalized(tag: String?): String? =
        tag?.trim()?.lowercase()?.split('-', '_')?.firstOrNull()?.takeIf { it.isNotEmpty() && it in brandLanguages }
    return normalized(guestChoice) ?: normalized(operatorDefault) ?: brandDefault
}

/**
 * Locale del idioma de la cuenta, para fechas, horas y nombres de idiomas. Lo provee [MainActivity];
 * sin proveedor cae al del sistema.
 */
val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault() }
