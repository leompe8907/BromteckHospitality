package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import android.util.Log
import com.panaccess.android.drm.PanaccessDrm
import com.panaccess.copyprotect.IOsmProcessor
import com.panaccess.copyprotect.Osm
import com.panaccess.copyprotect.OsmListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Mensaje del operador para mostrar en pantalla. */
data class OperatorMessage(val id: Int, val text: String)

/**
 * Mensajes en pantalla (OSM) que el operador manda desde Panaccess: avisos a una TV, a un grupo o a
 * todas. Es la base de la "mensajería dirigida" del plan, sin backend propio.
 *
 * Port de OsmHelper del base: el DRM los entrega por setCVMessageListener; se ignora el último ya
 * mostrado (se recuerda entre reinicios) y los anteriores a la instalación de la app.
 */
class OsmWatcher(context: Context) {

    private val prefs = context.getSharedPreferences("osm", Context.MODE_PRIVATE)
    private val installedAt: Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrDefault(System.currentTimeMillis())
    private val _current = MutableStateFlow<OperatorMessage?>(null)

    /** Mensaje a mostrar, o null. Si llega otro antes de cerrar éste, lo reemplaza. */
    val current: StateFlow<OperatorMessage?> = _current.asStateFlow()
    private var started = false

    /** Se llama con la sesión ya abierta. Idempotente. */
    fun start() {
        if (started) return
        started = true
        val lastId = prefs.getInt(KEY_LAST_ID, -1)
        val listener = OsmListener(lastId, "")
        listener.registerProcessor(TAG, object : IOsmProcessor {
            override fun isTagBased(): Boolean = false
            override fun onMessage(osm: Osm): Boolean {
                if (osm.getID() == prefs.getInt(KEY_LAST_ID, -1)) return true
                prefs.edit().putInt(KEY_LAST_ID, osm.getID()).apply()
                if (sentAt(osm.getDateTime()) < installedAt) {
                    Log.i(TAG, "OSM ${osm.getID()} anterior a la instalación: se ignora")
                    return true
                }
                Log.i(TAG, "OSM ${osm.getID()} recibido")
                _current.value = OperatorMessage(osm.getID(), osm.getMessage())
                return true
            }
        })
        runCatching { PanaccessDrm.getInst().setCVMessageListener(listener, 0) }
            .onFailure { Log.w(TAG, "no se pudo registrar el listener de OSM: ${it.message}") }
    }

    /** El huésped cerró el mensaje. */
    fun dismiss() {
        _current.value = null
    }

    /** "yyyy-MM-dd HH:mm" en GMT, como lo interpreta OsmHelper del base. */
    private fun sentAt(dateTime: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(dateTime)!!.time
    }.getOrDefault(System.currentTimeMillis())

    private companion object {
        const val TAG = "Hospitality.Osm"
        const val KEY_LAST_ID = "last_id"
    }
}
