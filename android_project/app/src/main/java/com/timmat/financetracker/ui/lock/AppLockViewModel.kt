package com.timmat.financetracker.ui.lock

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.R
import com.timmat.financetracker.common.AppLockController
import com.timmat.financetracker.common.UiMessage
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.repository.AppLockRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val mode: AppLockMode = AppLockMode.None,
    val pinSet: Boolean = false,
    val pinLength: Int = 4,
    val biometricAvailable: Boolean = false,
    val error: UiMessage? = null,
    val unlocked: Boolean = false,
    /** Seconds remaining of rate-limit lockout; 0 when not locked out. */
    val lockoutSecondsLeft: Int = 0,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
    private val appLockController: AppLockController,
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> = _state.asStateFlow()

    private var tickerStarted = false

    fun refresh(activity: Activity?) {
        val bioAvail = activity?.let { biometricAvailable(it) } ?: false
        _state.update {
            it.copy(
                mode = settingsRepository.appLock,
                pinSet = appLockRepository.isPinSet(),
                pinLength = appLockRepository.savedPinLength().coerceAtLeast(4),
                biometricAvailable = bioAvail,
                lockoutSecondsLeft = computeLockoutSecondsLeft(),
            )
        }
        startTicker()
    }

    fun setupPin(pin: String, useBiometric: Boolean) {
        runCatching { appLockRepository.setPin(pin) }
            .onSuccess {
                settingsRepository.appLock =
                    if (useBiometric) AppLockMode.PinAndBiometric else AppLockMode.Pin
                settingsRepository.appLockPromptShown = true
                _state.update {
                    it.copy(
                        mode = settingsRepository.appLock,
                        pinSet = true,
                        pinLength = pin.length,
                        error = null,
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(error = UiMessage.Raw(e.message ?: "")) } }
    }

    fun disable() {
        appLockRepository.clear()
        settingsRepository.appLock = AppLockMode.None
        appLockController.unlock()
        _state.update { it.copy(mode = AppLockMode.None, pinSet = false) }
    }

    fun skipPrompt() { settingsRepository.appLockPromptShown = true }

    /**
     * Attempt to unlock using [pin]. If currently in a rate-limit lockout, returns
     * false without even hashing. UI should never submit while `lockoutSecondsLeft > 0`.
     */
    fun verifyPin(pin: String): Boolean {
        if (computeLockoutSecondsLeft() > 0) {
            _state.update { it.copy(error = UiMessage.Res(R.string.app_lock_locked_out, it.lockoutSecondsLeft)) }
            return false
        }
        val ok = appLockRepository.verifyPin(pin)
        if (ok) {
            appLockController.unlock()
            _state.update { it.copy(unlocked = true, error = null, lockoutSecondsLeft = 0) }
        } else {
            val sec = computeLockoutSecondsLeft()
            _state.update {
                it.copy(
                    error = if (sec > 0) UiMessage.Res(R.string.app_lock_locked_out, sec)
                            else UiMessage.Res(R.string.app_lock_incorrect_pin),
                    lockoutSecondsLeft = sec,
                )
            }
        }
        return ok
    }

    fun onBiometricSuccess() {
        appLockController.unlock()
        _state.update { it.copy(unlocked = true, error = null) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    private fun computeLockoutSecondsLeft(): Int {
        val until = appLockRepository.lockedUntilMs()
        val diff = until - System.currentTimeMillis()
        return if (diff <= 0L) 0 else ((diff + 999L) / 1000L).toInt()
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val sec = computeLockoutSecondsLeft()
                _state.update { s ->
                    if (s.lockoutSecondsLeft == sec) s
                    else s.copy(lockoutSecondsLeft = sec,
                                error = if (sec == 0 && s.error is UiMessage.Res &&
                                            s.error.id == R.string.app_lock_locked_out) null else s.error)
                }
            }
        }
    }

    companion object {
        fun biometricAvailable(activity: Activity): Boolean {
            val result = BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

        fun launchBiometricPrompt(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            cancelLabel: String,
            onSuccess: () -> Unit,
            onFail: (String) -> Unit,
        ) {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) onFail(errString.toString())
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title).setSubtitle(subtitle).setNegativeButtonText(cancelLabel)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        }
    }
}
