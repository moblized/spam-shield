package app.spamshield.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.spamshield.core.Action
import app.spamshield.core.EngineConfig
import app.spamshield.core.NeighborMode
import app.spamshield.core.Rule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

data class AppSettings(
    val actions: Map<Rule, Action> = emptyMap(),
    val myNumber: String = "",
    val neighborMode: NeighborMode = NeighborMode.SAME_PREFIX,
    val ftcMinReports: Int = 2,
    val ftcDays: Int = 30,
    val communityMinVotes: Int = 3,
    val communityToken: String = "",
    val notifyOnBlock: Boolean = true,
    val logAllowed: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val ftcUpdatedAt: Long = 0L,
    val ftcError: String = "",
) {
    fun action(rule: Rule): Action = actions[rule] ?: rule.defaultAction

    fun engineConfig() = EngineConfig(
        actions = actions,
        myNumber = myNumber.ifBlank { null },
        neighborMode = neighborMode,
        ftcMinReports = ftcMinReports,
        communityMinVotes = communityMinVotes,
    )
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { read(it) }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setAction(rule: Rule, action: Action) = store.edit { it[ruleKey(rule)] = action.name }
    suspend fun setMyNumber(value: String) = store.edit { it[MY_NUMBER] = value }
    suspend fun setNeighborMode(value: NeighborMode) = store.edit { it[NEIGHBOR_MODE] = value.name }
    suspend fun setFtcMinReports(value: Int) = store.edit { it[FTC_MIN_REPORTS] = value }
    suspend fun setFtcDays(value: Int) = store.edit { it[FTC_DAYS] = value }
    suspend fun setCommunityMinVotes(value: Int) = store.edit { it[COMMUNITY_MIN_VOTES] = value }
    suspend fun setCommunityToken(value: String) = store.edit { it[COMMUNITY_TOKEN] = value.trim() }
    suspend fun setNotifyOnBlock(value: Boolean) = store.edit { it[NOTIFY_ON_BLOCK] = value }
    suspend fun setLogAllowed(value: Boolean) = store.edit { it[LOG_ALLOWED] = value }
    suspend fun setTheme(value: ThemeMode) = store.edit { it[THEME] = value.name }

    suspend fun setFtcResult(updatedAt: Long, error: String) = store.edit {
        it[FTC_UPDATED_AT] = updatedAt
        it[FTC_ERROR] = error
    }

    private fun read(p: Preferences) = AppSettings(
        actions = Rule.entries.associateWith { rule ->
            p[ruleKey(rule)]?.let { value -> runCatching { Action.valueOf(value) }.getOrNull() } ?: rule.defaultAction
        },
        myNumber = p[MY_NUMBER] ?: "",
        neighborMode = p[NEIGHBOR_MODE]?.let { runCatching { NeighborMode.valueOf(it) }.getOrNull() }
            ?: NeighborMode.SAME_PREFIX,
        ftcMinReports = p[FTC_MIN_REPORTS] ?: 2,
        ftcDays = p[FTC_DAYS] ?: 30,
        communityMinVotes = p[COMMUNITY_MIN_VOTES] ?: 3,
        communityToken = p[COMMUNITY_TOKEN] ?: "",
        notifyOnBlock = p[NOTIFY_ON_BLOCK] ?: true,
        logAllowed = p[LOG_ALLOWED] ?: true,
        theme = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        ftcUpdatedAt = p[FTC_UPDATED_AT] ?: 0L,
        ftcError = p[FTC_ERROR] ?: "",
    )

    private fun ruleKey(rule: Rule) = stringPreferencesKey("rule_${rule.name}")

    private companion object {
        val MY_NUMBER = stringPreferencesKey("my_number")
        val NEIGHBOR_MODE = stringPreferencesKey("neighbor_mode")
        val FTC_MIN_REPORTS = intPreferencesKey("ftc_min_reports")
        val FTC_DAYS = intPreferencesKey("ftc_days")
        val COMMUNITY_MIN_VOTES = intPreferencesKey("community_min_votes")
        val COMMUNITY_TOKEN = stringPreferencesKey("community_token")
        val NOTIFY_ON_BLOCK = booleanPreferencesKey("notify_on_block")
        val LOG_ALLOWED = booleanPreferencesKey("log_allowed")
        val THEME = stringPreferencesKey("theme")
        val FTC_UPDATED_AT = longPreferencesKey("ftc_updated_at")
        val FTC_ERROR = stringPreferencesKey("ftc_error")
    }
}
