package com.timmat.financetracker.ui.lock

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.timmat.financetracker.common.AppLockController
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.repository.AppLockRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AppLockUiState(
    val mode: AppLockMode = AppLockMode.None,
    val pinSet: Boolean = false,
    val biometricAvailable: Boolean = false,
    val error: String? = null,
    val unlocked: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
    private val appLockController: AppLockController,
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> = _state.asStateFlow()

    fun refresh(activity: Activity?) {
        val bioAvail = activity?.let { biometricAvailable(it) } ?: false
        _state.update {
            it.copy(
                mode = settingsRepository.appLock,
                pinSet = appLockRepository.isPinSet(),
                biometricAvailable = bioAvail,
            )
        }
    }

    fun setupPin(pin: String, useBiometric: Boolean) {
        runCatching { appLockRepository.setPin(pin) }
            .onSuccess {
                settingsRepository.appLock =
                    if (useBiometric) AppLockMode.PinAndBiometric else AppLockMode.Pin
                settingsRepository.appLockPromptShown = true
                _state.update { it.copy(mode = settingsRepository.appLock, pinSet = true, error = null) }
            }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun disable() {
        appLockRepository.clear()
        settingsRepository.appLock = AppLockMode.None
        appLockController.unlock()
        _state.update { it.copy(mode = AppLockMode.None, pinSet = false) }
    }

    fun skipPrompt() {
        settingsRepository.appLockPromptShown = true
    }

    fun verifyPin(pin: String): Boolean {
        val ok = appLockRepository.verifyPin(pin)
        if (ok) {
            appLockController.unlock()
            _state.update { it.copy(unlocked = true, error = null) }
        } else {
            _state.update { it.copy(error = "Incorrect PIN") }
        }
        return ok
    }

    fun onBiometricSuccess() {
        appLockController.unlock()
        _state.update { it.copy(unlocked = true, error = null) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    companion object {
        fun biometricAvailable(activity: Activity): Boolean {
            val result = BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

        /**
         * Launches the system biometric prompt. Must be called from a FragmentActivity.
         */
        fun launchBiometricPrompt(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            cancelLabel: String,
            onSuccess: () -> Unit,
            onFail: (String) -> Unit,
        ) {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            onFail(errString.toString())
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(cancelLabel)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        }
    }
}
