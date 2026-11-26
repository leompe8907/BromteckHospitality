package com.networkbroadcast.hospitality.companion

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lo que el emparejamiento tiene que recordar entre arranques: el secreto de la TV, el cursor de
 * órdenes, el último número de estadía, el token del celular. Son secretos: la versión de Android
 * los guarda cifrados.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val values = HashMap<String, String>()

    @Synchronized override fun get(key: String): String? = values[key]
    @Synchronized override fun put(key: String, value: String) { values[key] = value }
    @Synchronized override fun remove(key: String) { values.remove(key) }
}

/** [KeyValueStore] cifrado con el Keystore de Android (mismo esquema que `PanaccessSession`). */
class EncryptedKeyValueStore(context: Context, name: String) : KeyValueStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        name,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
