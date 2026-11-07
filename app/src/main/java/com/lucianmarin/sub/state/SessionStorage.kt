package com.lucianmarin.sub.state

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucianmarin.sub.model.SubMember
import com.lucianmarin.sub.network.APIClient
import com.lucianmarin.sub.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subdroid_settings")

class SessionStorage(private val context: Context) {

    companion object {
        private const val SECURE_PREFS_NAME = "subdroid_secure_session"
        private const val FEED_CACHE_PREFS_NAME = "subdroid_feed_cache"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_SAVED_USER_JSON = "saved_user_json"

        private val PREF_THEME_MODE = stringPreferencesKey("theme_mode")
        private val PREF_API_BASE_URL = stringPreferencesKey("api_base_url")
        private val PREF_LAST_NOTIFICATIONS = intPreferencesKey("last_unread_notifications")
        private val PREF_LAST_MESSAGES = intPreferencesKey("last_unread_messages")
    }

    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val feedCachePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(FEED_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val memoryFeedCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun <T> getCachedFeed(urlKey: String, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val raw = memoryFeedCache[urlKey] ?: feedCachePrefs.getString(urlKey, null) ?: return null
        return try {
            val decoded = APIClient.json.decodeFromString(serializer, raw)
            memoryFeedCache[urlKey] = raw
            decoded
        } catch (_: Exception) {
            null
        }
    }

    fun <T> setCachedFeed(urlKey: String, value: T, serializer: kotlinx.serialization.KSerializer<T>) {
        try {
            val encoded = APIClient.json.encodeToString(serializer, value)
            memoryFeedCache[urlKey] = encoded
            feedCachePrefs.edit().putString(urlKey, encoded).apply()
        } catch (_: Exception) {
        }
    }

    fun clearFeedCache() {
        memoryFeedCache.clear()
        feedCachePrefs.edit().clear().apply()
    }

    fun getAuthToken(): String? =
        securePrefs.getString(KEY_AUTH_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setAuthToken(token: String?) {
        val editor = securePrefs.edit()
        if (token.isNullOrBlank()) {
            editor.remove(KEY_AUTH_TOKEN)
        } else {
            editor.putString(KEY_AUTH_TOKEN, token)
        }
        editor.apply()
    }

    fun getSavedUser(): SubMember? {
        val raw = securePrefs.getString(KEY_SAVED_USER_JSON, null) ?: return null
        return try {
            APIClient.json.decodeFromString(SubMember.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    fun setSavedUser(user: SubMember?) {
        val editor = securePrefs.edit()
        if (user == null) {
            editor.remove(KEY_SAVED_USER_JSON)
        } else {
            try {
                val encoded = APIClient.json.encodeToString(SubMember.serializer(), user)
                editor.putString(KEY_SAVED_USER_JSON, encoded)
            } catch (_: Exception) {
            }
        }
        editor.apply()
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromId(prefs[PREF_THEME_MODE] ?: ThemeMode.SYSTEM.id)
    }

    val apiBaseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREF_API_BASE_URL]?.takeIf { it.isNotBlank() } ?: APIClient.DEFAULT_BASE_URL
    }

    suspend fun getThemeMode(): ThemeMode = themeModeFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PREF_THEME_MODE] = mode.id
        }
    }

    suspend fun getApiBaseUrl(): String = apiBaseUrlFlow.first()

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_API_BASE_URL] = APIClient.normalizeBaseUrl(url)
        }
    }

    suspend fun getLastUnreadNotifications(): Int =
        context.dataStore.data.first()[PREF_LAST_NOTIFICATIONS] ?: 0

    suspend fun getLastUnreadMessages(): Int =
        context.dataStore.data.first()[PREF_LAST_MESSAGES] ?: 0

    suspend fun saveLastUnreadCounts(notifications: Int, messages: Int) {
        context.dataStore.edit { prefs ->
            prefs[PREF_LAST_NOTIFICATIONS] = notifications
            prefs[PREF_LAST_MESSAGES] = messages
        }
    }
}
