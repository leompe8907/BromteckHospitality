package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import com.panaccess.android.drm.CasError
import com.panaccess.android.drm.ICasFunctionCaller
import com.panaccess.android.drm.PanaccessDrm
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.coroutines.resume

/** Datos con los que el DRM encuentra al operador (VENDOR / BRANDING / DISCOVERY_* en PanaccessApp). */
data class PanaccessEndpoint(
    val vendor: String,
    val branding: String,
    val discoveryMode: String?,
    val discoveryHint: String?,
)

/** Resultado de una llamada al CAS. Misma forma que `shared/drm/CasClient.kt` (iOS) del base. */
sealed interface CasResult {
    /** [rawJson] es el sobre completo `{success, answer}`; se parsea con `CasEnvelope`. */
    data class Success(val rawJson: String) : CasResult
    data class Failure(val code: String?, val message: String?) : CasResult
    data object Timeout : CasResult
}

/**
 * Transporte hacia el DRM de Panaccess: una llamada, una respuesta. La orquestación (login con
 * licencias, catálogo) vive en [LoginService] y [CatalogService].
 *
 * Equivale a `DrmFunctions` + `DrmInitUseCase` de PanaccessApp, y a `CasClient` de su versión iOS,
 * sin BuildConfig ni Activity. Todo corre en [Dispatchers.IO]: el DRM hace la red en el hilo que
 * lo llama, y desde el principal Android corta la app (NetworkOnMainThreadException).
 */
class PanaccessClient(
    context: Context,
    private val endpoint: PanaccessEndpoint,
    private val appVersion: String,
    private val debug: Boolean,
) {
    private val appContext = context.applicationContext
    private val drm get() = PanaccessDrm.getInst()

    /** Idempotente. Lanza DrmException si el DRM no arranca. */
    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (drm.isInitialized()) return@withContext
        // Mismos argumentos que DrmInitUseCase: "Kitkat" fijo y flags 7 en debug (logs del DRM).
        drm.init(appContext, endpoint.vendor, endpoint.branding, appVersion, "Kitkat", if (debug) 7 else 0)
    }

    /**
     * Fija las credenciales. Es local: no valida contra el servidor, pero deja
     * isLoginCredentialsSet() = true; si después el login falla hay que llamar a [reinitialize].
     *
     * [password] en claro o ya en md5 (32 caracteres), como LoginRepositoryImpl.setLoginCredentials.
     * El descubrimiento (cvsys/intv) sólo se manda si el usuario es un email, igual que el base.
     */
    suspend fun setLoginCredentials(username: String, password: String) = withContext(Dispatchers.IO) {
        ensureInitialized()
        val passwordMd5 = if (password.length == 32) password else md5(password + "_panaccess")
        if (isEmail(username)) {
            drm.setLoginCredentials(username, passwordMd5, endpoint.discoveryMode, endpoint.discoveryHint)
        } else {
            drm.setLoginCredentials(username, passwordMd5, null, null)
        }
    }

    suspend fun verifyLoginCredentials(timeoutMs: Int = 30_000): CasResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont -> drm.verifyLoginCredentials(caller(cont), null, 0, timeoutMs) }
    }

    /**
     * Recuperación de sesión (la pone [PanaccessSession]). Si una llamada falla, se intenta
     * recuperar la sesión y se reintenta UNA vez, como PriorityCasFunctionCaller del base.
     */
    var recovery: (suspend () -> Boolean)? = null

    /** Llama a una función del CAS ("getStreamingLicenses", "getBouquets"…), con recuperación. */
    suspend fun call(function: String, params: Map<String, String> = emptyMap(), timeoutMs: Int = 30_000): CasResult {
        val first = callRaw(function, params, timeoutMs)
        if (first is CasResult.Success) return first
        val recover = recovery ?: return first
        return if (recover()) callRaw(function, params, timeoutMs) else first
    }

    /** Sin recuperación: para la recuperación misma y para el login (que tiene su propia lógica). */
    suspend fun callRaw(
        function: String,
        params: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30_000,
        noLoginRequired: Boolean = false,
    ): CasResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            drm.callCasFunction(caller(cont), function, HashMap(params), null, 0, timeoutMs, noLoginRequired)
        }
    }

    /**
     * Servidor del operador; base de las URLs de imágenes de VOD (`<server>/public/images/<id>/v/<variante>`).
     *
     * A diferencia de [callRaw], el DRM no tiene una versión con timeout de esta llamada: es
     * bloqueante y puede tardar mucho si está actualizando el servidor responsable
     * (`LOCAL_BUSY_UPDATING_RESPONSIBLE_SERVER` del AAR). [withTimeoutOrNull] no corta el hilo
     * nativo bloqueado, pero sí libera la corrutina (y el lock de [PanaccessEntertainmentSource])
     * en vez de dejar VOD colgado para siempre.
     */
    suspend fun responsibleServer(): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(RESPONSIBLE_SERVER_TIMEOUT_MS) { runCatching { drm.getResponsibleServer() }.getOrNull() }
    }

    /**
     * URLs HLS para el player. El DRM reescribe la URL del catálogo con la sesión actual, así que
     * hay que pedirla en cada reproducción y en cada reintento, nunca guardarla.
     */
    suspend fun liveStreamUrl(catalogUrl: String): String? = resolve { drm.getTopLevelStreamM3u8Url(catalogUrl) }
    suspend fun catchupUrl(catchupId: Int): String? = resolve { drm.getTopLevelCatchupM3u8Url(catchupId) }
    suspend fun vodUrl(vodId: Int): String? = resolve { drm.getTopLevelVodM3u8Url(vodId) }

    /** deinit + init. Única forma que da el SDK de borrar credenciales tras un login fallido. */
    suspend fun reinitialize() = withContext(Dispatchers.IO) {
        runCatching { if (drm.isInitialized()) drm.deinit() }
        ensureInitialized()
    }

    /**
     * Borra los archivos de personalización del DRM (`filesDir/.pd*`). Pueden quedar en un estado
     * que hace fallar el login siempre; LoginUseCase del base los borra y reintenta una vez.
     */
    suspend fun deleteLocalDrmFiles(): Int = withContext(Dispatchers.IO) {
        appContext.filesDir.listFiles { f -> f.name.startsWith(".pd") }.orEmpty().count { it.delete() }
    }

    private suspend fun resolve(block: () -> String?): String? =
        withContext(Dispatchers.IO) { runCatching(block).getOrNull()?.takeIf { it.isNotBlank() } }

    private fun caller(cont: CancellableContinuation<CasResult>) = object : ICasFunctionCaller {
        override fun onSuccess(json: JSONObject?) {
            if (cont.isActive) cont.resume(CasResult.Success((json ?: JSONObject()).toString()))
        }
        override fun onFailure(error: CasError) {
            if (cont.isActive) cont.resume(CasResult.Failure(error.code, error.message))
        }
        override fun onTimeout() {
            if (cont.isActive) cont.resume(CasResult.Timeout)
        }
    }

    companion object {
        private const val RESPONSIBLE_SERVER_TIMEOUT_MS = 15_000L

        internal fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Igual que Utils.isAnEmail del base: decide si se usa el descubrimiento por hint. */
        internal fun isEmail(text: String): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches()
    }
}
