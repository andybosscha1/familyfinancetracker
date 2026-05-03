package com.timmat.financetracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.timmat.financetracker.data.model.AppLanguage
import com.timmat.financetracker.data.model.AppCurrency
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

    /** Local per-device flag so one-off cleanup runs at most once per calendar month. */
    var lastCleanupMonthKey: String
        get() = prefs.getString(KEY_LAST_CLEANUP, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_CLEANUP, value).apply() }

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
        const val KEY_LAST_CLEANUP = "last_cleanup_month"
    }
}
