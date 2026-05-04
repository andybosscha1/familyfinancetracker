package com.timmat.financetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timmat.financetracker.common.AppLockController
import com.timmat.financetracker.data.model.AppCurrency
import com.timmat.financetracker.data.model.AppLanguage
import com.timmat.financetracker.data.model.AppLockMode
import com.timmat.financetracker.data.model.AppTheme
import com.timmat.financetracker.data.repository.AppLockRepository
import com.timmat.financetracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.English,
    val currency: AppCurrency = AppCurrency.EUR,
    val theme: AppTheme = AppTheme.Light,
    val appLock: AppLockMode = AppLockMode.None,
    val pinSet: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
    private val appLockController: AppLockController,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            settingsRepository.language,
            settingsRepository.currency,
            settingsRepository.theme,
            settingsRepository.appLock,
            appLockRepository.isPinSet(),
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect {
                _state.update {
                    SettingsUiState(
                        settingsRepository.language,
                        settingsRepository.currency,
                        settingsRepository.theme,
                        settingsRepository.appLock,
                        appLockRepository.isPinSet(),
                    )
                }
            }
        }
    }

    fun setLanguage(lang: AppLanguage) { settingsRepository.language = lang }
    fun setCurrency(cur: AppCurrency) { settingsRepository.currency = cur }
    fun setTheme(theme: AppTheme) { settingsRepository.theme = theme }

    fun disableAppLock() {
        appLockRepository.clear()
        settingsRepository.appLock = AppLockMode.None
        appLockController.unlock()
    }

    fun lockNow() { appLockController.lockNow() }
}
