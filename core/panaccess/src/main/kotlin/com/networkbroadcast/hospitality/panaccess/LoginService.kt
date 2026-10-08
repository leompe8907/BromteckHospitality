package com.networkbroadcast.hospitality.panaccess

import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.domain.model.License
import com.panaccess.android.streaming.shared.domain.model.LoginError
import com.panaccess.android.streaming.shared.domain.model.LoginErrorMapper
import android.util.Log
import kotlinx.coroutines.delay

/** Desenlace del login. */
sealed interface LoginResult {
    /** Sesión utilizable: credenciales verificadas y una licencia habilitada. */
    data class Success(val license: License) : LoginResult
    /** Credenciales válidas pero ninguna licencia se habilitó sola: elegir a mano. */
    data class NeedsLicenseSelection(val licenses: List<License>) : LoginResult
    /** [step] dice en qué paso se cortó; [detail] es el texto del servidor. */
    data class Failure(
        val step: String,
        val error: LoginError,
        val detail: String,
        /** Poblado cuando falló habilitando licencia: para ofrecer elegir una a mano. */
        val licenses: List<License> = emptyList(),
    ) : LoginResult
}

/**
 * Login contra el CAS en cuatro pasos. Verificar credenciales no alcanza: sin una licencia
 * habilitada getBouquets devuelve vacío.
 *
 * 1. setLoginCredentials   2. verifyLoginCredentials
 * 3. getStreamingLicenses  4. setStreamingLicense
 *
 * Port a Android de `shared/iosMain/.../login/LoginService.kt` de PanaccessApp (commit 7310d5011),
 * que a su vez replica `LoginUseCase` de Android en modo mobile. Mismas decisiones: un reintento
 * con limpieza del DRM, licencia guardada primero con failIfInUse=false, después la primera libre
 * con failIfInUse=true.
 */
class LoginService(private val client: PanaccessClient) {

    suspend fun login(
        username: String,
        password: String,
        savedLicenseKey: String?,
        autoUseFirstAvailableLicense: Boolean = true,
        /** Licencia que asignó el instalador (su nombre es el número de habitación); se toma aunque esté en uso. */
        preferredLicenseName: String? = null,
    ): LoginResult {
        authenticate(username, password)?.let { return it }
        val licenses = when (val listing = fetchLicenses()) {
            is Listing.Loaded -> listing.licenses
            is Listing.Failed -> return listing.result
        }
        return selectLicense(licenses, autoUseFirstAvailableLicense, savedLicenseKey, preferredLicenseName)
    }

    /** null si quedó verificado. */
    private suspend fun authenticate(username: String, password: String): LoginResult? {
        var cleanupAttempted = false
        while (true) {
            try {
                client.setLoginCredentials(username, password)
            } catch (e: Exception) {
                return LoginResult.Failure("setLoginCredentials", LoginError.Unknown("drm"), e.message ?: e.toString())
            }
            val verification = client.verifyLoginCredentials()
            Log.i(TAG, "verifyLoginCredentials -> ${describe(verification)}")
            if (verification is CasResult.Success) return null
            val error = classify(verification)
            if (!cleanupAttempted && LoginErrorMapper.warrantsDrmCleanupRetry(error)) {
                cleanupAttempted = true
                client.deleteLocalDrmFiles()
                client.reinitialize()
                delay(500)
                continue
            }
            // Obligatorio: si no, isLoginCredentialsSet() queda en true con credenciales malas.
            runCatching { client.reinitialize() }
            return LoginResult.Failure("verifyLoginCredentials", error, describe(verification))
        }
    }

    private sealed interface Listing {
        data class Loaded(val licenses: List<License>) : Listing
        data class Failed(val result: LoginResult.Failure) : Listing
    }

    /** withPins=true: el servidor devuelve el PIN de cada licencia y nadie tiene que tipearlo. */
    private suspend fun fetchLicenses(): Listing {
        val licenses = when (val r = client.callRaw("getStreamingLicenses", mapOf("withPins" to "true"))) {
            is CasResult.Success -> CasEnvelope.answerList<License>(r.rawJson)
            else -> return Listing.Failed(LoginResult.Failure("getStreamingLicenses", classify(r), describe(r)))
        }
        Log.i(TAG, "getStreamingLicenses -> ${licenses.size} licencias, ${licenses.count { it.hasUsablePin }} con PIN")
        if (licenses.isEmpty()) {
            return Listing.Failed(
                LoginResult.Failure("getStreamingLicenses", LoginError.InvalidCredentials, "la cuenta no tiene licencias")
            )
        }
        return Listing.Loaded(licenses)
    }

    private suspend fun selectLicense(licenses: List<License>, autoUse: Boolean, savedKey: String?, preferredName: String? = null): LoginResult {
        // La que indicó el instalador gana: es la habitación donde quedó instalada esta TV.
        preferredName?.let { name ->
            licenses.firstOrNull { it.licenseName == name && it.hasUsablePin }?.let { license ->
                if (enable(license, failIfInUse = false) is CasResult.Success) return LoginResult.Success(license)
            }
        }
        // La de la sesión anterior primero: es la que esta TV ya venía usando.
        savedKey?.let { key ->
            licenses.firstOrNull { it.key == key && it.hasUsablePin }?.let { license ->
                if (enable(license, failIfInUse = false) is CasResult.Success) return LoginResult.Success(license)
            }
        }
        if (!autoUse) return LoginResult.NeedsLicenseSelection(licenses)
        // failIfInUse=true: que el servidor rechace las que usa otro equipo en vez de robárselas.
        var lastError: LoginError? = null
        var lastDetail = "ninguna licencia tiene PIN utilizable"
        licenses.filter { it.hasUsablePin }.forEach { license ->
            when (val r = enable(license, failIfInUse = true)) {
                is CasResult.Success -> return LoginResult.Success(license)
                is CasResult.Failure -> {
                    lastError = if (r.message.isNullOrBlank()) LoginError.LicenseInUse else LoginErrorMapper.fromMessage(r.message)
                    lastDetail = r.message ?: "licencia en uso"
                }
                CasResult.Timeout -> { lastError = LoginError.Network; lastDetail = "timeout" }
            }
        }
        return LoginResult.Failure("setStreamingLicense", lastError ?: LoginError.Unknown("pin"), lastDetail, licenses)
    }

    /** Habilita una licencia. El CAS exige PIN de 4 dígitos. */
    suspend fun enable(license: License, failIfInUse: Boolean): CasResult {
        val pin = license.pin
        if (pin == null || pin.length != 4) return CasResult.Failure(null, "PIN inválido para ${license.key}")
        val params = buildMap {
            put("licenseKey", license.key.orEmpty())
            put("pin", pin)
            if (failIfInUse) put("failIfInUse", "true")
        }
        return client.callRaw("setStreamingLicense", params).also {
            Log.i(TAG, "setStreamingLicense(${license.licenseName ?: "sin nombre"}, failIfInUse=$failIfInUse) -> ${describe(it)}")
        }
    }

    private fun classify(result: CasResult): LoginError = when (result) {
        is CasResult.Success -> LoginError.Unknown("ok")
        is CasResult.Failure -> LoginErrorMapper.fromMessage(result.message ?: result.code)
        CasResult.Timeout -> LoginError.Network
    }

    private fun describe(result: CasResult): String = when (result) {
        is CasResult.Success -> "ok"
        is CasResult.Failure -> result.message ?: result.code ?: "rechazo sin detalle"
        CasResult.Timeout -> "timeout"
    }

    private companion object {
        const val TAG = "Hospitality.Login"
    }
}
