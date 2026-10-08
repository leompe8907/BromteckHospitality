package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.panaccess.android.drm.PanaccessDrm
import com.panaccess.android.streaming.shared.cas.CasEnvelope
import com.panaccess.android.streaming.shared.domain.model.License
import com.panaccess.android.streaming.shared.domain.model.LoginError
import com.panaccess.copyprotect.CopyprotectService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado de abrir la sesión, listo para que la pantalla de login lo muestre. */
sealed interface SessionOutcome {
    data object Ok : SessionOutcome
    data class Failed(val error: LoginError, val detail: String) : SessionOutcome
    /** Credenciales válidas pero ninguna licencia se habilitó sola (p. ej. todas en uso): elegir. */
    data class ChooseLicense(val licenses: List<License>, val detail: String) : SessionOutcome
}

/**
 * Sesión de Panaccess de la TV (no del huésped): la abre una vez el instalador o el staff y queda
 * guardada para que la TV entre sola al reiniciar. El check-out del huésped no la toca.
 *
 * Guarda usuario, md5 de la contraseña (nunca en claro, a diferencia del base) y la licencia
 * habilitada, cifrado con EncryptedSharedPreferences. Después del login trae la configuración
 * del cliente ([operatorConfig]) para la marca blanca, y se ofrece como recuperación de sesión
 * para todas las llamadas del [client].
 */
class PanaccessSession(context: Context, val client: PanaccessClient) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "panaccess_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val loginService = LoginService(client)
    private val recoveryLock = Mutex()
    private var lastRecoveryAt = 0L
    private var lastRecoveryOk = false

    /** Licencia habilitada: `licenseName` es el número de habitación, `tvModel` el segundo logo. */
    var license: License? = null
        private set
    var operatorConfig: OperatorConfig? = null
        private set

    /** true si en este proceso ya hay sesión abierta (p. ej. Android recreó la Activity). */
    val isOpen: Boolean get() = license != null

    val username: String? get() = prefs.getString(KEY_USER, null)
    val hasSavedLogin: Boolean get() = username != null && prefs.getString(KEY_PASS_MD5, null) != null

    init {
        client.recovery = ::recover
    }

    suspend fun login(username: String, password: String, preferredLicenseName: String? = null): SessionOutcome {
        val user = username.trim()
        val md5 = if (password.length == 32) password else PanaccessClient.md5(password + "_panaccess")
        return open(user, md5, preferredLicenseName)
    }

    /** Reintenta con lo guardado. Si el servidor rechaza las credenciales, se olvidan. */
    suspend fun restore(): SessionOutcome {
        val user = prefs.getString(KEY_USER, null)
        val md5 = prefs.getString(KEY_PASS_MD5, null)
        if (user == null || md5 == null) return SessionOutcome.Failed(LoginError.InvalidCredentials, "sin sesión guardada")
        val outcome = open(user, md5)
        if (outcome is SessionOutcome.Failed && outcome.error == LoginError.InvalidCredentials) forget()
        return outcome
    }

    /**
     * Elección manual (pantalla de licencias). failIfInUse=false: si otra TV la tiene, se la toma,
     * igual que LicenseSelectionScreen del base cuando el usuario confirma.
     */
    suspend fun chooseLicense(choice: License): SessionOutcome =
        when (val r = loginService.enable(choice, failIfInUse = false)) {
            is CasResult.Success -> onLicenseEnabled(choice)
            is CasResult.Failure -> SessionOutcome.Failed(LoginError.Unknown("license"), r.message ?: r.code ?: "no se pudo habilitar")
            CasResult.Timeout -> SessionOutcome.Failed(LoginError.Network, "timeout")
        }

    fun forget() = prefs.edit().clear().apply()

    /**
     * Recuperación de sesión, como CasFunctionHelper.checkIfLoggedInAndRelogin del base (modo
     * contraseña): `loggedIn` → verifyLoginCredentials → volver a habilitar la licencia. Una sola a
     * la vez; si se intentó hace menos de 30 s no se repite (evita bucles con el servidor caído).
     */
    suspend fun recover(): Boolean = recoveryLock.withLock {
        val current = license ?: return false
        val now = SystemClock.elapsedRealtime()
        if (lastRecoveryAt != 0L && now - lastRecoveryAt < 30_000) return lastRecoveryOk
        lastRecoveryAt = now
        val loggedIn = client.callRaw("loggedIn", noLoginRequired = true)
        if (loggedIn == CasResult.Timeout) { lastRecoveryOk = false; return false }
        val stillIn = (loggedIn as? CasResult.Success)?.let { CasEnvelope.answer(it.rawJson)?.toString() == "true" } ?: false
        val verified = client.verifyLoginCredentials(10_000) is CasResult.Success
        val enabled = verified && loginService.enable(current, failIfInUse = false) is CasResult.Success
        Log.i(TAG, "recuperación de sesión: loggedIn=$stillIn, verificada=$verified, licencia=$enabled")
        lastRecoveryOk = enabled
        enabled
    }

    private suspend fun open(user: String, md5: String, preferredLicenseName: String? = null): SessionOutcome {
        val result = loginService.login(
            user, md5,
            savedLicenseKey = prefs.getString(KEY_LICENSE, null).takeIf { preferredLicenseName == null },
            preferredLicenseName = preferredLicenseName,
        )
        // Las credenciales sirvieron aunque falte la licencia: se guardan para la pantalla de elección.
        if (result is LoginResult.Success || result is LoginResult.NeedsLicenseSelection ||
            (result is LoginResult.Failure && result.licenses.isNotEmpty())
        ) {
            prefs.edit().putString(KEY_USER, user).putString(KEY_PASS_MD5, md5).apply()
        }
        return when (result) {
            is LoginResult.Success -> onLicenseEnabled(result.license)
            is LoginResult.NeedsLicenseSelection -> SessionOutcome.ChooseLicense(result.licenses, "elegir licencia")
            is LoginResult.Failure ->
                if (result.licenses.isNotEmpty()) SessionOutcome.ChooseLicense(result.licenses, result.detail)
                else SessionOutcome.Failed(result.error, "${result.step}: ${result.detail}")
        }
    }

    private suspend fun onLicenseEnabled(enabled: License): SessionOutcome {
        license = enabled
        prefs.edit().putString(KEY_LICENSE, enabled.key).apply()
        // Protección de copia: lo mismo que MainFragment.onLicensesChanged del base.
        runCatching {
            CopyprotectService.getInst().setMobileLicense(enabled.key)
            PanaccessDrm.getInst().configureAppBehaviorService(true, 10)
        }.onFailure { Log.w(TAG, "copyprotect: ${it.message}") }
        // La configuración del cliente no es requisito para ver TV: si falla, seguimos.
        operatorConfig = (client.call("getClientConfig") as? CasResult.Success)?.let { OperatorConfig.parse(it.rawJson) }
        Log.i(TAG, "sesión abierta; operador=${operatorConfig?.operator}, parámetros X_=${operatorConfig?.parameters?.size}, cdn guía=${operatorConfig?.epgCdnUrls?.size}")
        // Sólo los parámetros de marca/hotel (nunca datos del abonado).
        operatorConfig?.parameters
            ?.filterKeys { it.startsWith("X_HOTEL") || it.startsWith("X_DEFAULT_LANG") || it == "X_DESIGN_SHOW_HOTEL_ROOM_NUMBER" }
            ?.let { Log.i(TAG, "marca desde el operador: $it") }
        return SessionOutcome.Ok
    }

    private companion object {
        const val TAG = "Hospitality.Session"
        const val KEY_USER = "user"
        const val KEY_PASS_MD5 = "pass_md5"
        const val KEY_LICENSE = "license_key"
    }
}
