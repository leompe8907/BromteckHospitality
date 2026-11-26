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
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    /** true si esta sesión es de un celular emparejado (abierta con [loginForPhone]) y no una de depuración o de TV. */
    val isPhoneSession: Boolean get() = prefs.getString(KEY_PHONE_ROOM, null) != null
    val hasSavedLogin: Boolean get() = username != null && prefs.getString(KEY_PASS_MD5, null) != null

    /** Último X_APP_SPLASH que trajo getClientConfig, cacheado en disco -- operatorConfig recién
     *  está disponible después de un login/restore exitoso, pero el splash hace falta justo antes
     *  de eso (arranque de la app, Auth.Provisioning/Checking en HospitalityApp), así que esto
     *  sirve el último valor conocido en vez de nada mientras se resuelve la sesión. */
    val cachedSplashImageUrl: String? get() = prefs.getString(KEY_SPLASH_URL, null)

    init {
        client.recovery = ::recover
    }

    suspend fun login(username: String, password: String, preferredLicenseName: String? = null): SessionOutcome {
        val user = username.trim()
        val md5 = if (password.length == 32) password else PanaccessClient.md5(password + "_panaccess")
        return open(user, md5, preferredLicenseName)
    }

    /**
     * Login de un CELULAR con el login del hotel que le dio el backend al emparejar (`HotelLogin`): usuario y md5. Solo
     * toma una licencia de celular de [room] sin robarle ninguna a otro (ver [LoginService.loginForPhone]). Sin
     * licencia libre devuelve [SessionOutcome.Failed] y el celular sigue como control remoto, sin video. Recuerda que
     * es un celular: [restore] y [recover] siguen la misma regla.
     */
    suspend fun loginForPhone(username: String, md5: String, room: String): SessionOutcome =
        openAsPhone(username.trim(), md5, room.trim(), saved = false)

    /** Reintenta con lo guardado. Si el servidor rechaza las credenciales, se olvidan. */
    suspend fun restore(): SessionOutcome {
        val user = prefs.getString(KEY_USER, null)
        val md5 = prefs.getString(KEY_PASS_MD5, null)
        if (user == null || md5 == null) return SessionOutcome.Failed(LoginError.InvalidCredentials, "sin sesión guardada")
        val phoneRoom = prefs.getString(KEY_PHONE_ROOM, null)
        val outcome = if (phoneRoom != null) openAsPhone(user, md5, phoneRoom, saved = true) else open(user, md5)
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
     * Cierra la sesión de ESTE equipo: olvida el login y la licencia y suelta las credenciales del DRM. Es lo que hace un
     * celular al hacer check-out. La TV no lo usa (nunca cierra sesión). Ojo: no hay una llamada conocida que le diga a
     * Panaccess que libere la licencia; si queda "en uso" hasta que venza la sesión del lado de Panaccess está sin
     * confirmar (ver docs/plan-emparejamiento-celular.md).
     */
    suspend fun close() {
        forget()
        license = null
        operatorConfig = null
        runCatching { client.reinitialize() }
    }

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
        // Es la licencia que este equipo ya venía usando: puede figurar en uso por su propia sesión caída.
        val enabled = verified && loginService.enable(current, failIfInUse = false) is CasResult.Success
        Log.i(TAG, "recuperación de sesión: loggedIn=$stillIn, verificada=$verified, licencia=$enabled")
        lastRecoveryOk = enabled
        enabled
    }

    private suspend fun openAsPhone(user: String, md5: String, room: String, saved: Boolean): SessionOutcome {
        val result = loginService.loginForPhone(
            user, md5, room, savedLicenseKey = prefs.getString(KEY_LICENSE, null).takeIf { saved },
        )
        // Las credenciales sirvieron aunque no haya licencia libre: se guardan para reintentar al volver a abrir la app.
        val credentialsWork = result is LoginResult.Success ||
            (result is LoginResult.Failure && result.step in setOf("phoneLicenses", "setStreamingLicense"))
        if (credentialsWork) {
            prefs.edit().putString(KEY_USER, user).putString(KEY_PASS_MD5, md5).putString(KEY_PHONE_ROOM, room).apply()
        }
        return when (result) {
            is LoginResult.Success -> onLicenseEnabled(result.license)
            // Nunca "elegir licencia": un celular no elige a mano, y la lista incluiría la de la TV.
            is LoginResult.NeedsLicenseSelection -> SessionOutcome.Failed(LoginError.Unknown("phone"), "elegir licencia")
            is LoginResult.Failure -> SessionOutcome.Failed(result.error, "${result.step}: ${result.detail}")
        }
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

    /**
     * Re-pide getClientConfig sin pasar por login: para refrescar valores que pueden cambiar entre
     * huéspedes (hoy sólo el fondo -- ver el checkout en HospitalityApp) sin forzar un re-login
     * completo, que cortaría la sesión de Panaccess de la TV.
     */
    suspend fun refreshOperatorConfig() {
        operatorConfig = fetchOperatorConfig()
        cacheSplashImageUrl(operatorConfig)
        // Mismo chequeo de clima que onLicenseEnabled, acá también: el checkout es la única otra
        // vía por la que se vuelve a pedir getClientConfig en una sesión larga, y queremos poder
        // ver si el clima empezó a llegar sin tener que forzar un re-login completo.
        Log.i(
            TAG,
            "config refrescada; clima=${operatorConfig?.weatherText ?: "sin dato"} " +
                "(campo weather presente=${operatorConfig?.hasWeatherField})",
        )
    }

    // No se borra el valor cacheado cuando viene null (config no configurada o fetch fallido):
    // mismo criterio que el catálogo cacheado -- se guarda el último bueno conocido, nunca se
    // vacía por una respuesta vacía puntual.
    private fun cacheSplashImageUrl(config: OperatorConfig?) {
        config?.splashImageUrl?.let { prefs.edit().putString(KEY_SPLASH_URL, it).apply() }
    }

    private suspend fun fetchOperatorConfig(): OperatorConfig? =
        (client.call("getClientConfig") as? CasResult.Success)?.let { OperatorConfig.parse(it.rawJson) }
            ?.let { resolveUrlParam(it, "X_APP_WIDGETS") }
            ?.let { resolveUrlParam(it, "X_APP_SERVICES") }
            ?.also { logColorParams(it) }

    /**
     * Los parámetros de color tal cual los manda Cableatlántico, crudos. Sin esto no hay forma de
     * distinguir "el hotel no lo configuró" de "lo configuró con un formato que no parsea": los
     * dos casos terminan en el color por defecto de la marca, en silencio.
     * [HospitalityColors.parseHex] avisa del segundo caso, pero del primero no puede avisar nadie.
     */
    private fun logColorParams(config: OperatorConfig) {
        val colorKeys = listOf(
            "X_APP_CARD_COLOR", "X_APP_TEXT_COLOR", "X_APP_PLAYER_CONTROL_COLOR",
            "X_APP_CONFIRM_COLOR", "X_APP_CANCEL_COLOR",
        )
        val shown = colorKeys.joinToString(", ") { key ->
            "$key=${config.param(key)?.let { "\"$it\"" } ?: "(sin configurar)"}"
        }
        Log.i(TAG, "colores del operador: $shown")
    }

    /**
     * X_APP_WIDGETS puede venir como el JSON directo, o como una URL a un archivo con ese JSON
     * adentro (p. ej. un link para compartir de un NAS) -- Cableatlántico eligió esto último
     * cuando el campo del parámetro en Panaccess no alcanzaba para el JSON completo (varios
     * widgets, título/descripción/horario en 5 idiomas, fotos). Se resuelve acá, una sola vez al
     * traer la config, para que OperatorConfig.widgets() siga siendo puro/síncrono y no le
     * importe de dónde salió el texto.
     *
     * Lo mismo vale para X_APP_SERVICES (catálogo de servicios del hotel, ver
     * services/OperatorServices.kt): el JSON con todas las categorías, textos por idioma y fotos
     * tampoco entra en el campo del parámetro.
     */
    private suspend fun resolveUrlParam(config: OperatorConfig, key: String): OperatorConfig {
        val raw = config.param(key) ?: return config
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return config
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                (URL(raw).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    inputStream.bufferedReader().use { it.readText() }
                }
            }.getOrNull()
        }
        if (fetched == null) {
            Log.w(TAG, "$key: no se pudo descargar el JSON de $raw")
            return config
        }
        return config.copy(parameters = config.parameters + (key to fetched))
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
        operatorConfig = fetchOperatorConfig()
        cacheSplashImageUrl(operatorConfig)
        Log.i(
            TAG,
            "sesión abierta; operador=${operatorConfig?.operator}, parámetros X_=${operatorConfig?.parameters?.size}, " +
                "cdn guía=${operatorConfig?.epgCdnUrls?.size}, clima=${operatorConfig?.weatherText ?: "sin dato"} " +
                "(campo weather presente=${operatorConfig?.hasWeatherField})",
        )
        // Sólo parámetros de marca/hotel (nunca datos del abonado). Los diseños de bouquet se
        // leen desde getBouquets.customData, no desde getClientConfig.
        operatorConfig?.parameters
            ?.filterKeys { it.startsWith("X_HOTEL") || it.startsWith("X_DEFAULT_LANG") }
            ?.let { Log.i(TAG, "marca desde el operador: $it") }
        return SessionOutcome.Ok
    }

    private companion object {
        const val TAG = "Hospitality.Session"
        const val KEY_USER = "user"
        const val KEY_PASS_MD5 = "pass_md5"
        const val KEY_LICENSE = "license_key"
        /** Presente solo en un celular: la habitación cuyas licencias `-mN` puede usar. */
        const val KEY_PHONE_ROOM = "phone_room"
        const val KEY_SPLASH_URL = "splash_url"
    }
}
