package com.panaccess.android.streaming.shared.domain.model

/**
 * Causa de un login fallido, clasificada y **sin texto de UI**.
 *
 * Espejo de `com.panaccess.android.streaming.domain.model.LoginError` de `:app`, con una
 * diferencia deliberada: el de Android lleva un `@StringRes messageResId` adentro, lo que ata el
 * dominio a los recursos de Android y no puede vivir en `commonMain`. Acá el tipo sólo clasifica;
 * traducir a texto es responsabilidad de cada UI, que además tiene los strings por marca.
 */
sealed interface LoginError {
    data object Network : LoginError
    data object Personalization : LoginError
    data object TimeSkew : LoginError
    data object InvalidCredentials : LoginError
    data object WrongPin : LoginError
    data object LicenseInUse : LoginError
    data class Unknown(val code: String) : LoginError
}

/**
 * Clasifica el mensaje de error que devuelve el CAS.
 *
 * **Advertencia importante** (ver CLAUDE_MIGRATION_CONTEXT.md, sección 11.3): estos son los
 * mismos substrings que usa `LoginUseCase` en Android, pero **la librería de iOS no siempre
 * entrega un mensaje**. Se verificó contra el backend real que un rechazo por licencia en uso
 * llega con `CasError` nulo y payload vacío en iOS, mientras que en Android trae el texto
 * "This license is already in use".
 *
 * Esto no es una particularidad nuestra: **la app Swift existente tampoco lee el mensaje**. En
 * `LoginHelper.toggleLicenseActivation` (proyecto NBStreaming) la detección es puramente por
 * contexto:
 *
 * ```swift
 * if auto && !success           { autoActivateLicense(index: autoIndex + 1) }
 * else if failIfInUse && !success { licenseDelegate?.errorLicenseInUse(license: license) }
 * ```
 *
 * O sea, en iOS la convención establecida es `failIfInUse && !success ⇒ licencia en uso`. Este
 * mapeo por mensaje cubre el caso en que el CAS sí lo manda; lo demás se infiere por contexto
 * (ver `LoginService`). No asumir que un `Unknown` significa "error raro" — puede ser
 * simplemente que la librería no dijo nada.
 */
object LoginErrorMapper {

    fun fromMessage(message: String?): LoginError {
        if (message.isNullOrBlank()) return LoginError.Unknown("sin mensaje")

        return when {
            message.contains("IO Error") ||
                message.contains("no_responsible_server") ||
                message.contains("error_decrypt_session") -> LoginError.Network

            message.contains("datastore_hash_missmatch_repersonalize") ||
                message.contains("txt_invalid_personalization_request") ||
                message.contains("Personalization error") -> LoginError.Personalization

            message.contains("time_skew") -> LoginError.TimeSkew

            message.contains("empty_answer") ||
                message.contains("no_responsible_server_all_failed") -> LoginError.InvalidCredentials

            message.contains("The PIN is wrong") -> LoginError.WrongPin

            message.contains("This license is already in use") -> LoginError.LicenseInUse

            else -> LoginError.Unknown(message)
        }
    }

    /**
     * Indica si el error justifica el ciclo de limpieza de DRM (borrar los `.pd`, `deinit()`,
     * reinicializar y reintentar) que hace `LoginUseCase` en Android.
     *
     * Android reintenta ante **cualquier** fallo de login, una sola vez. Se replica ese criterio
     * en vez de acotarlo a los errores "de DRM": el motivo de la limpieza es justamente que el
     * estado local quedó inconsistente, y eso no siempre se puede distinguir por el mensaje.
     *
     * La excepción es la falta de red: sin conexión, borrar los ficheros de personalización sólo
     * obliga a rehacerla cuando vuelva la red, y el reintento falla igual.
     */
    fun warrantsDrmCleanupRetry(error: LoginError): Boolean = when (error) {
        is LoginError.Network -> false
        is LoginError.InvalidCredentials -> false // credenciales malas: reintentar no arregla nada
        is LoginError.WrongPin -> false
        is LoginError.LicenseInUse -> false
        else -> true
    }
}
