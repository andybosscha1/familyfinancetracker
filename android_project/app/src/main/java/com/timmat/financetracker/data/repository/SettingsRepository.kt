package com.timmat.financetracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.AppLanguage
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.model.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))
        set(value) { prefs.edit().putString(KEY_LANGUAGE, value.tag).apply() }

    var currency: AppCurrency
        get() = AppCurrency.fromCode(prefs.getString(KEY_CURRENCY, null))
        set(value) { prefs.edit().putString(KEY_CURRENCY, value.code).apply() }

    var theme: AppTheme
        get() = AppTheme.fromName(prefs.getString(KEY_THEME, null))
        set(value) { prefs.edit().putString(KEY_THEME, value.name).apply() }

    var appLock: AppLockMode
        get() = AppLockMode.fromName(prefs.getString(KEY_APP_LOCK, null))
        set(value) { prefs.edit().putString(KEY_APP_LOCK, value.name).apply() }

    var appLockPromptShown: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_PROMPT_SHOWN, false)
        set(value) { prefs.edit().putBoolean(KEY_APP_LOCK_PROMPT_SHOWN, value).apply() }

    var incomeExpanded: Boolean
        get() = prefs.getBoolean(KEY_INCOME_EXPANDED, true)
        set(value) { prefs.edit().putBoolean(KEY_INCOME_EXPANDED, value).apply() }

    var expensesExpanded: Boolean
        get() = prefs.getBoolean(KEY_EXPENSES_EXPANDED, true)
        set(value) { prefs.edit().putBoolean(KEY_EXPENSES_EXPANDED, value).apply() }

    /** Local per-device flag so one-off cleanup runs at most once per calendar month. */
    var lastCleanupMonthKey: String
        get() = prefs.getString(KEY_LAST_CLEANUP, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_CLEANUP, value).apply() }

    /** Per-family marker so auto-reset-on-rollover fires exactly once per cycle boundary. */
    fun lastResetCycleKey(familyId: String): String =
        prefs.getString("${KEY_LAST_RESET_CYCLE_PREFIX}$familyId", "") ?: ""
    fun setLastResetCycleKey(familyId: String, value: String) {
        prefs.edit().putString("${KEY_LAST_RESET_CYCLE_PREFIX}$familyId", value).apply()
    }

    /** Emits on every preference change so UI recomposes when user picks a new value. */
    fun observe(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(Unit) }

    private companion object {
        const val PREFS_NAME = "finance_tracker_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_CURRENCY = "currency"
        const val KEY_THEME = "theme"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_APP_LOCK_PROMPT_SHOWN = "app_lock_prompt_shown"
        const val KEY_INCOME_EXPANDED = "income_expanded"
        const val KEY_EXPENSES_EXPANDED = "expenses_expanded"
        const val KEY_LAST_CLEANUP = "last_cleanup_month"
        const val KEY_LAST_RESET_CYCLE_PREFIX = "last_reset_cycle_"
    }
}
