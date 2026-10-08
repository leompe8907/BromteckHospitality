package com.networkbroadcast.hospitality.tv

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Configuración por archivo del instalador: evita tipear usuario y contraseña con el control en
 * cada habitación. El script tools/configurar-tv.sh deja este archivo por adb en la carpeta de la
 * app (Android/data/<paquete>/files/setup.json, no hace falta ningún permiso) y la TV entra sola.
 *
 * El archivo trae el md5 de la contraseña (lo mismo que guarda la app), nunca la contraseña, y se
 * borra apenas se usa. Si está, gana sobre la sesión guardada: sirve también para mover una TV de
 * habitación o cambiarle la cuenta.
 */
class InstallerSetup(context: Context) {
    private val file: File? = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    @Serializable
    data class Setup(
        val user: String,
        /** md5(contraseña + "_panaccess"): 32 caracteres hex. */
        val passwordMd5: String,
        /** Habitación = nombre de la licencia a tomar. Opcional: sin ella, la primera libre. */
        val room: String? = null,
    )

    /** El archivo si existe y es válido. Uno inválido se borra para no reintentarlo en cada arranque. */
    fun read(): Setup? {
        val f = file?.takeIf { it.isFile } ?: return null
        return runCatching { json.decodeFromString(Setup.serializer(), f.readText()) }
            .getOrNull()
            ?.takeIf { it.user.isNotBlank() && it.passwordMd5.length == 32 && it.passwordMd5.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            .also { if (it == null) { Log.w(TAG, "setup.json inválido; se borra"); delete() } }
    }

    fun delete() { runCatching { file?.delete() } }

    private companion object {
        const val TAG = "Hospitality.Setup"
        const val FILE_NAME = "setup.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
