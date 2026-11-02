package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crystal_sugar_prefs")

class AppPreferences(val context: Context) {

    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language") // "en" or "fa"
        val KEY_THEME = stringPreferencesKey("theme") // "SYSTEM", "LIGHT", "DARK"
        val KEY_NORMALIZATION = stringPreferencesKey("normalization") // "STANDARD_SCALER", "MIN_MAX_SCALER"
        val KEY_SIMILARITY_THRESHOLD = doublePreferencesKey("similarity_threshold")
        val KEY_DEV_MODE = booleanPreferencesKey("dev_mode")
        val KEY_SECURITY_PIN = stringPreferencesKey("security_pin")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_SESSION_TIMEOUT = intPreferencesKey("session_timeout")
        val KEY_SCREENSHOT_PROTECTED = booleanPreferencesKey("screenshot_protected")

        val KEY_SUB_ACTIVATED = booleanPreferencesKey("sub_activated")
        val KEY_SUB_START_TIME = longPreferencesKey("sub_start_time")
        val KEY_SUB_EXPIRATION_TIME = longPreferencesKey("sub_expiration_time")
        val KEY_SUB_DURATION_DAYS = intPreferencesKey("sub_duration_days")
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "fa" // Default to Persian as requested by user or English fallback
    }

    val subActivatedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUB_ACTIVATED] ?: false
    }

    val subStartTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUB_START_TIME] ?: 0L
    }

    val subExpirationTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUB_EXPIRATION_TIME] ?: 0L
    }

    val subDurationDaysFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUB_DURATION_DAYS] ?: 30
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "DARK"
    }

    val normalizationFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NORMALIZATION] ?: "STANDARD_SCALER"
    }

    val similarityThresholdFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[KEY_SIMILARITY_THRESHOLD] ?: 65.0
    }

    val developerModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEV_MODE] ?: false
    }

    val securityPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SECURITY_PIN] ?: "Asrtkl__4GHsaGT01011"
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: false
    }

    val screenshotProtectedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCREENSHOT_PROTECTED] ?: false
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = lang }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = theme }
    }

    suspend fun setNormalization(method: String) {
        context.dataStore.edit { prefs -> prefs[KEY_NORMALIZATION] = method }
    }

    suspend fun setSimilarityThreshold(threshold: Double) {
        context.dataStore.edit { prefs -> prefs[KEY_SIMILARITY_THRESHOLD] = threshold }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DEV_MODE] = enabled }
    }

    suspend fun setSecurityPin(pin: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SECURITY_PIN] = pin }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setScreenshotProtected(protected: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SCREENSHOT_PROTECTED] = protected }
    }

    suspend fun setSubscriptionData(activated: Boolean, startTime: Long, expirationTime: Long, durationDays: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUB_ACTIVATED] = activated
            prefs[KEY_SUB_START_TIME] = startTime
            prefs[KEY_SUB_EXPIRATION_TIME] = expirationTime
            prefs[KEY_SUB_DURATION_DAYS] = durationDays
        }
    }
}
