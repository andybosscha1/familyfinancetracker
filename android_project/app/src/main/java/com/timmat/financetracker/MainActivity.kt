package com.timmat.financetracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import android.view.WindowManager
import androidx.core.os.LocaleListCompat
import com.timmat.financetracker.common.AppLockController
import com.timmat.financetracker.data.model.AppTheme
import com.timmat.financetracker.data.repository.SettingsRepository
import com.timmat.financetracker.ui.navigation.AppNavigation
import com.timmat.financetracker.ui.theme.FinanceTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appLockController: AppLockController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent the app content from appearing in screenshots and the recent-apps
        // preview. Important for a finance app that shows balances, transactions,
        // and the PIN entry screen.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Apply saved language before drawing so stringResource() picks correct locale.
        val tag = settingsRepository.language.tag
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.isEmpty || current.toLanguageTags() != tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

        appLockController.maybeLockOnStart()
        enableEdgeToEdge()
        setContent {
            // Observe settings so theme changes take effect immediately.
            var themeChoice by remember { mutableStateOf(settingsRepository.theme) }
            LaunchedEffect(Unit) {
                settingsRepository.observe().collect {
                    themeChoice = settingsRepository.theme
                }
            }

            FinanceTrackerTheme(theme = themeChoice) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        appLockController.onPause()
    }

    override fun onResume() {
        super.onResume()
        appLockController.onResume()
    }
}
