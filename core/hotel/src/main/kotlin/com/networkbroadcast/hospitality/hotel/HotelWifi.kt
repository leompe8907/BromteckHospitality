package com.networkbroadcast.hospitality.hotel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * La red Wi-Fi para huéspedes tal cual la carga el operador en Panaccess (parámetro X_APP_WIFI, ver
 * [parseOperatorWifi]). [security] llega como texto ("WPA", "WEP" o "nopass") y se interpreta en
 * [toNetwork], que es tolerante con lo que no conoce.
 *
 * Es el mismo contrato que publica el backend propio de los hoteles en su content.json (`"wifi"`), así que
 * la misma regla de QR sirve cuando ese backend exista.
 */
data class HotelWifi(
    val ssid: String = "",
    val password: String? = null,
    val security: String = "WPA",
    val hidden: Boolean = false,
)

/** El tipo de seguridad de la red, con el texto que va en el QR (`T:`). */
enum class WifiSecurity(val qrValue: String) { WPA("WPA"), WEP("WEP"), OPEN("nopass") }

/** Una red lista para mostrar y para convertir en QR; [password] es null sólo en una red abierta. */
data class WifiNetwork(
    val ssid: String,
    val password: String?,
    val security: WifiSecurity,
    val hidden: Boolean,
) {
    /** El texto del QR, en el formato que leen las cámaras de Android e iOS (ver [wifiQrPayload]). */
    val qrPayload: String get() = wifiQrPayload(this)
}

/**
 * La red a mostrar, o null si no hay una que sirva (la pieza de Wi-Fi no se dibuja):
 *  - sin `wifi`, o sin nombre de red;
 *  - una red con clave pero sin la clave: el huésped no podría conectarse con ese QR.
 * `nopass` (también "none"/"open") es red abierta y la clave, si vino, se ignora. Un tipo que no conocemos
 * ("WPA2", "WPA3", "SAE"…) se trata como WPA, que es lo que entienden los celulares.
 */
fun HotelWifi?.toNetwork(): WifiNetwork? {
    if (this == null || ssid.isBlank()) return null
    val security = when (security.trim().lowercase()) {
        "nopass", "none", "open" -> WifiSecurity.OPEN
        "wep" -> WifiSecurity.WEP
        else -> WifiSecurity.WPA
    }
    if (security == WifiSecurity.OPEN) return WifiNetwork(ssid, null, security, hidden)
    val key = password?.takeIf { it.isNotBlank() } ?: return null
    return WifiNetwork(ssid, key, security, hidden)
}

/**
 * La red del parámetro X_APP_WIFI de Panaccess, o null si no hay una que sirva.
 *
 * Formato: un objeto JSON `{"ssid":"HotelGuest","password":"abc12345","security":"WPA","hidden":false}`.
 * Solo `ssid` es obligatorio, y `password` para una red con clave. `security` por defecto WPA y `hidden` por
 * defecto false. Como el valor lo escribe una persona en un campo de texto, `hidden` también se acepta como
 * texto ("true"/"1"), y los números en `ssid` o `password` se toman como texto. Una clave se respeta tal cual
 * (sin recortar espacios). Si no es un objeto JSON válido, o falta lo obligatorio, no hay red.
 */
fun parseOperatorWifi(raw: String?): WifiNetwork? {
    if (raw.isNullOrBlank()) return null
    val obj = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
    fun text(key: String): String? = (obj[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    val hidden = text("hidden")?.trim()?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: false
    return HotelWifi(
        ssid = text("ssid").orEmpty(),
        password = text("password"),
        security = text("security") ?: "WPA",
        hidden = hidden,
    ).toNetwork()
}

private val HEX_ONLY = Regex("[0-9A-Fa-f]+")

/** Escapa lo que el formato usa como separador (`\ ; , : "`) con una barra invertida. */
private fun escapeWifiField(value: String): String = buildString {
    for (c in value) {
        if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
        append(c)
    }
}

/** Un valor formado sólo por dígitos hexadecimales se encierra entre comillas: si no, el celular lo toma como hex crudo. */
private fun wifiField(value: String): String =
    escapeWifiField(value).let { if (HEX_ONLY.matches(value)) "\"$it\"" else it }

/**
 * `WIFI:T:<WPA|WEP|nopass>;S:<ssid>;P:<clave>;H:true;;` (el formato de ZXing que leen las cámaras de
 * Android e iOS). Sin `P:` en una red abierta, y `H:true` sólo si la red está oculta.
 */
fun wifiQrPayload(network: WifiNetwork): String = buildString {
    append("WIFI:T:").append(network.security.qrValue).append(";S:").append(wifiField(network.ssid)).append(';')
    if (network.security != WifiSecurity.OPEN && network.password != null) {
        append("P:").append(wifiField(network.password)).append(';')
    }
    if (network.hidden) append("H:true;")
    append(';')
}
