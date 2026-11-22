package com.arnit.imagex.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.formats.ImageFormatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "arnit_imagex_settings")

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultFormat: ImageFormatId = ImageFormatId.PNG,
    val defaultQuality: QualityMode = QualityMode.HIGH,
    val historyEnabled: Boolean = true,
    val autoCleanup: Boolean = true,
    val billingTestMode: Boolean = false
)

/** Small preference store backed by DataStore. Contains no image data ever. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val defaultFormat = stringPreferencesKey("default_format")
        val defaultQuality = stringPreferencesKey("default_quality")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val autoCleanup = booleanPreferencesKey("auto_cleanup")
        val billingTestMode = booleanPreferencesKey("billing_test_mode")
        val premiumPlan = stringPreferencesKey("premium_plan")
        val premiumTest = booleanPreferencesKey("premium_test_state")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            defaultFormat = prefs[Keys.defaultFormat]?.let { runCatching { ImageFormatId.valueOf(it) }.getOrNull() }
                ?: ImageFormatId.PNG,
            defaultQuality = prefs[Keys.defaultQuality]?.let { runCatching { QualityMode.valueOf(it) }.getOrNull() }
                ?: QualityMode.HIGH,
            historyEnabled = prefs[Keys.historyEnabled] ?: true,
            autoCleanup = prefs[Keys.autoCleanup] ?: true,
            billingTestMode = prefs[Keys.billingTestMode] ?: false
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDefaultFormat(format: ImageFormatId) = context.dataStore.edit { it[Keys.defaultFormat] = format.name }
    suspend fun setDefaultQuality(mode: QualityMode) = context.dataStore.edit { it[Keys.defaultQuality] = mode.name }
    suspend fun setHistoryEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.historyEnabled] = enabled }
    suspend fun setAutoCleanup(enabled: Boolean) = context.dataStore.edit { it[Keys.autoCleanup] = enabled }
    suspend fun setBillingTestMode(enabled: Boolean) = context.dataStore.edit { it[Keys.billingTestMode] = enabled }

    suspend fun premiumPlan(): String = context.dataStore.data.map { it[Keys.premiumPlan] ?: "" }.first()

    suspend fun setPremiumPlan(planName: String?, testMode: Boolean) {
        context.dataStore.edit { prefs ->
            if (planName == null) prefs.remove(Keys.premiumPlan) else prefs[Keys.premiumPlan] = planName
            prefs[Keys.premiumTest] = testMode
        }
    }
}
