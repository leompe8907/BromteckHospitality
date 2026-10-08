package com.panaccess.android.streaming.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Licencia de streaming ("tarjeta") que el CAS asocia a una cuenta.
 *
 * Una cuenta puede tener varias, y **el login no está completo hasta que haya una habilitada**:
 * el flujo es `setLoginCredentials` → `verifyLoginCredentials` → `getStreamingLicenses` →
 * `setStreamingLicense`. Ver `LoginUseCase` del lado Android.
 *
 * Espejo de `com.panaccess.android.streaming.domain.model.License` de `:app`, que hoy parsea a
 * mano con `JSONObject`. Cuando `:app` consuma este módulo, ese duplicado se elimina.
 *
 * [pin] sólo viene si la llamada a `getStreamingLicenses` pidió `withPins=true`; si no, hay que
 * pedírselo al usuario. `setStreamingLicense` lo exige de 4 dígitos.
 */
@Serializable
data class License(
    val key: String? = null,
    val products: String? = null,
    val pin: String? = null,
    @SerialName("stbSerial") val stbSerial: String? = null,
    @SerialName("ciModule") val ciModule: String? = null,
    @SerialName("tvModel") val tvModel: String? = null,
    @SerialName("licenseName") val licenseName: String? = null,
    val active: Boolean = false,
    val selected: Boolean = false,
) {
    /** `true` si la licencia trae un PIN utilizable sin preguntarle al usuario. */
    val hasUsablePin: Boolean get() = pin?.length == 4
}
