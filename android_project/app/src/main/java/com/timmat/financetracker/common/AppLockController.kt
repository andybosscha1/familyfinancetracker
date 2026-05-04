package com.timmat.financetracker.common

import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.repository.AppLockRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether the app-lock screen should be displayed.
 *
 * Called from [com.timmat.financetracker.MainActivity] lifecycle hooks:
 *   - on start: [maybeLockOnStart]
 *   - on pause: [onPause]  (remembers timestamp)
 *   - on resume: [onResume] (re-locks if PIN/biometric configured and > [RELOCK_AFTER_MS])
 */
@Singleton
class AppLockController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var lastPausedAt: Long = 0L

    fun maybeLockOnStart() {
        if (settingsRepository.appLock != AppLockMode.None && appLockRepository.isPinSet()) {
            _locked.value = true
        }
    }

    fun onPause() { lastPausedAt = System.currentTimeMillis() }

    fun onResume() {
        if (settingsRepository.appLock == AppLockMode.None) return
        if (!appLockRepository.isPinSet()) return
        if (lastPausedAt == 0L) return
        if (System.currentTimeMillis() - lastPausedAt > RELOCK_AFTER_MS) {
            _locked.value = true
        }
    }

    /** Used by a “Lock now” action in Settings. */
    fun lockNow() {
        if (settingsRepository.appLock != AppLockMode.None && appLockRepository.isPinSet()) {
            _locked.value = true
        }
    }

    fun unlock() { _locked.value = false }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L // 1 minute
    }
}
