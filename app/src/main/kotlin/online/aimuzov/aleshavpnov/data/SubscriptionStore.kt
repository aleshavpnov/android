package online.aimuzov.aleshavpnov.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import online.aimuzov.aleshavpnov.sub.SubscriptionHeaders
import online.aimuzov.aleshavpnov.sub.SubscriptionInfo

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subscription")

/** Всё, что приложение помнит между запусками: ссылка, hwid и последний удачный ответ. */
class SubscriptionStore(context: Context) {
	private val store = context.applicationContext.dataStore

	val hasUrl: Flow<Boolean> = store.data.map { !it[URL].isNullOrBlank() }

	/** Карточка подписки из последнего удачного ответа — экран без сети живёт на ней. */
	val cachedInfo: Flow<SubscriptionInfo?> =
		store.data.map { prefs -> prefs[CACHED_HEADERS]?.let { SubscriptionHeaders.parse(decodeHeaders(it)) } }

	suspend fun url(): String? = store.data.first()[URL]

	suspend fun saveUrl(url: String) {
		store.edit { it[URL] = url }
	}

	// hwid живёт столько же, сколько установка: бот по нему считает устройства.
	suspend fun hwid(): String {
		val existing = store.data.first()[HWID]
		if (existing != null) return existing
		val fresh = UUID.randomUUID().toString()
		store.edit { it[HWID] = fresh }
		return fresh
	}

	suspend fun cache(): Cached? {
		val prefs = store.data.first()
		val body = prefs[CACHED_BODY] ?: return null
		return Cached(body, decodeHeaders(prefs[CACHED_HEADERS] ?: "{}"), prefs[CACHED_AT] ?: 0L)
	}

	suspend fun saveCache(body: String, headers: Map<String, String>) {
		store.edit {
			it[CACHED_BODY] = body
			it[CACHED_HEADERS] = Json.encodeToString(HEADERS, headers)
			it[CACHED_AT] = System.currentTimeMillis()
		}
	}

	suspend fun lastUpdateCheck(): Long = store.data.first()[UPDATE_CHECKED_AT] ?: 0L

	suspend fun markUpdateCheck() {
		store.edit { it[UPDATE_CHECKED_AT] = System.currentTimeMillis() }
	}

	data class Cached(val body: String, val headers: Map<String, String>, val savedAt: Long)

	private fun decodeHeaders(json: String): Map<String, String> =
		runCatching { Json.decodeFromString(HEADERS, json) }.getOrDefault(emptyMap())

	private companion object {
		val HEADERS = MapSerializer(String.serializer(), String.serializer())
		val URL = stringPreferencesKey("url")
		val HWID = stringPreferencesKey("hwid")
		val CACHED_BODY = stringPreferencesKey("cached_body")
		val CACHED_HEADERS = stringPreferencesKey("cached_headers")
		val CACHED_AT = longPreferencesKey("cached_at")
		val UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
	}
}
