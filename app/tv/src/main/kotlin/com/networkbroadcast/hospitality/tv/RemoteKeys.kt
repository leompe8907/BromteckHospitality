package com.networkbroadcast.hospitality.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.networkbroadcast.hospitality.companion.RemoteKey

/**
 * Ejecuta una tecla del control remoto del celular como si viniera del control de la TV: las flechas, OK y canal
 * +/- entran por el mismo camino que una tecla real (foco, reproductor, salir del reposo). Volver pasa por el
 * dispatcher de "atrás" (los BackHandler de las pantallas); inicio lo resuelve quien llama, y el volumen va directo
 * al sistema.
 */
internal fun Context.pressRemoteKey(key: RemoteKey, onHome: () -> Unit) {
    val activity = findActivity() ?: return
    when (key) {
        RemoteKey.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
        RemoteKey.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
        RemoteKey.MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
        RemoteKey.BACK -> (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
        RemoteKey.HOME -> onHome()
        RemoteKey.UP -> activity.tap(KeyEvent.KEYCODE_DPAD_UP)
        RemoteKey.DOWN -> activity.tap(KeyEvent.KEYCODE_DPAD_DOWN)
        RemoteKey.LEFT -> activity.tap(KeyEvent.KEYCODE_DPAD_LEFT)
        RemoteKey.RIGHT -> activity.tap(KeyEvent.KEYCODE_DPAD_RIGHT)
        RemoteKey.OK -> activity.tap(KeyEvent.KEYCODE_DPAD_CENTER)
        RemoteKey.CHANNEL_UP -> activity.tap(KeyEvent.KEYCODE_CHANNEL_UP)
        RemoteKey.CHANNEL_DOWN -> activity.tap(KeyEvent.KEYCODE_CHANNEL_DOWN)
    }
}

private fun Context.adjustVolume(direction: Int) {
    val audio = getSystemService(AudioManager::class.java) ?: return
    // FLAG_SHOW_UI: la barra de volumen del sistema, la misma que con el control de la TV.
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}

private fun Activity.tap(keyCode: Int) {
    val now = SystemClock.uptimeMillis()
    dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
    dispatchKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
