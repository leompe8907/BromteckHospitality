package com.networkbroadcast.hospitality.panaccess

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.panaccess.copyprotect.CopyprotectService
import java.util.concurrent.Executors

/**
 * Huella antipiratería del operador sobre el video (CopyprotectService de pan_copyprotect): el
 * servicio dibuja dentro de un ConstraintLayout cuando el operador lo pide.
 *
 * UN solo contenedor por proceso, registrado una vez y nunca desregistrado: unregister() no vuelve
 * nunca (probado en el hilo principal y en uno de fondo), y sin él un segundo register se rechaza
 * ("already registered"). El base funciona igual de hecho: su VideoFragment vive toda la sesión.
 * Cada player pone este contenedor como capa sobre el video ([overlay]).
 */
object CopyProtection {
    private const val TAG = "Hospitality.Player"

    // Los métodos del servicio son synchronized: nunca desde el hilo principal, y en orden.
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "copyprotect").apply { isDaemon = true } }
    private var container: ConstraintLayout? = null

    /** El contenedor para poner encima del video. Si lo tenía otro player, se lo saca. */
    fun overlay(context: Context): ConstraintLayout {
        val view = container ?: ConstraintLayout(context.applicationContext).also { created ->
            container = created
            safely("register") { CopyprotectService.getInst().register(TAG, created) }
        }
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /** Al salir del player. */
    fun detach() = safely("clear") {
        CopyprotectService.getInst().clearCurrentStream()
        CopyprotectService.getInst().clearCurrentService()
    }

    /** [streamId] es el id del canal en Panaccess (Channel.id). */
    fun onLiveStream(streamId: String) = safely("setCurrentStream") {
        streamId.toIntOrNull()?.let { CopyprotectService.getInst().setCurrentStream(it, "") }
    }

    /** Catchup y VOD no son un stream en vivo: se limpia lo anterior. */
    fun onNonLive() = safely("clearCurrentStream") { CopyprotectService.getInst().clearCurrentStream() }

    private fun safely(what: String, block: () -> Unit) {
        worker.execute {
            runCatching(block).onFailure { Log.w(TAG, "copyprotect $what: ${it.message}") }
        }
    }
}
