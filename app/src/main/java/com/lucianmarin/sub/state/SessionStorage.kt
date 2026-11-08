package com.lucianmarin.sub.state

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.network.APIClient
import com.lucianmarin.sub.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subdroid_settings")

class SessionStorage(private val context: Context) : APIClient.EndpointCacheStore {

    companion object {
        private const val SECURE_PREFS_NAME = "subdroid_secure_session"
        private const val CACHE_PREFS_NAME = "subdroid_endpoint_cache"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_SAVED_USER_JSON = "saved_user_json"
        private const val KEY_LAST_MESSAGES_SUBTAB = "last_messages_subtab"
        private const val KEY_LAST_TRENDING_SUBTAB = "last_trending_subtab"

        private val PREF_THEME_MODE = stringPreferencesKey("theme_mode")
        private val PREF_API_BASE_URL = stringPreferencesKey("api_base_url")
        private val PREF_LAST_NOTIFICATIONS = intPreferencesKey("last_unread_notifications")
        private val PREF_LAST_MESSAGES = intPreferencesKey("last_unread_messages")
    }

    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cachePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun readCache(endpointKey: String): String? {
        val key = APIClient.normalizeEndpointKey(endpointKey)
        return cachePrefs.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    override fun writeCache(endpointKey: String, rawJson: String) {
        if (rawJson.isBlank()) return
        val key = APIClient.normalizeEndpointKey(endpointKey)
        cachePrefs.edit().putString(key, rawJson).apply()
    }

    override fun clearCache() {
        cachePrefs.edit().clear().apply()
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

    fun getSavedUser(): SubUser? {
        val raw = securePrefs.getString(KEY_SAVED_USER_JSON, null) ?: return null
        return try {
            APIClient.json.decodeFromString(SubUser.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    fun setSavedUser(user: SubUser?) {
        val editor = securePrefs.edit()
        if (user == null) {
            editor.remove(KEY_SAVED_USER_JSON)
        } else {
            try {
                val encoded = APIClient.json.encodeToString(SubUser.serializer(), user)
                editor.putString(KEY_SAVED_USER_JSON, encoded)
            } catch (_: Exception) {
            }
        }
        editor.apply()
    }

    fun getLastMessagesSubtab(): SidebarSection {
        val key = securePrefs.getString(KEY_LAST_MESSAGES_SUBTAB, null)
        val section = SidebarSection.fromKey(key)
        return if (section != null && section in SidebarSection.messagesSubtabs) {
            section
        } else {
            SidebarSection.MESSAGES
        }
    }

    fun setLastMessagesSubtab(section: SidebarSection) {
        if (section in SidebarSection.messagesSubtabs) {
            securePrefs.edit().putString(KEY_LAST_MESSAGES_SUBTAB, section.key).apply()
        }
    }

    fun getLastTrendingSubtab(): SidebarSection {
        val key = securePrefs.getString(KEY_LAST_TRENDING_SUBTAB, null)
        val section = SidebarSection.fromKey(key)
        return if (section != null && section in SidebarSection.trendingSubtabs) {
            section
        } else {
            SidebarSection.TRENDING
        }
    }

    fun setLastTrendingSubtab(section: SidebarSection) {
        if (section in SidebarSection.trendingSubtabs) {
            securePrefs.edit().putString(KEY_LAST_TRENDING_SUBTAB, section.key).apply()
        }
    }

    suspend fun getThemeMode(): ThemeMode {
        val id = context.dataStore.data.first()[PREF_THEME_MODE] ?: ThemeMode.SYSTEM.id
        return ThemeMode.fromId(id)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PREF_THEME_MODE] = mode.id
        }
    }

    suspend fun getApiBaseUrl(): String =
        context.dataStore.data.first()[PREF_API_BASE_URL]?.takeIf { it.isNotBlank() }
            ?: APIClient.DEFAULT_BASE_URL

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
