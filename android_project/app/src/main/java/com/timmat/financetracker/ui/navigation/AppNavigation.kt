package com.timmat.financetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.timmat.financetracker.ui.auth.AuthViewModel
import com.timmat.financetracker.ui.auth.LoginScreen
import com.timmat.financetracker.ui.budgets.BudgetsScreen
import com.timmat.financetracker.ui.dashboard.DashboardScreen
import com.timmat.financetracker.ui.family.FamilyManagementScreen
import com.timmat.financetracker.ui.onboarding.OnboardingScreen
import com.timmat.financetracker.ui.settings.SettingsScreen
import com.timmat.financetracker.ui.transactions.AddTransactionScreen
import com.timmat.financetracker.ui.transactions.TransactionsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.state.collectAsState()

    val startDestination = if (authState.user != null) Routes.ONBOARDING else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onSignedIn = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFamilyReady = { familyId ->
                    navController.navigate("${Routes.DASHBOARD}/$familyId") {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0)
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable("${Routes.DASHBOARD}/{familyId}") { entry ->
            val familyId = entry.arguments?.getString("familyId").orEmpty()
            DashboardScreen(
                familyId = familyId,
                onOpenTransactions = { navController.navigate("${Routes.TRANSACTIONS}/$familyId") },
                onAddTransaction = { navController.navigate("${Routes.ADD_TRANSACTION}/$familyId") },
                onOpenBudgets = { navController.navigate("${Routes.BUDGETS}/$familyId") },
                onOpenFamily = { navController.navigate("${Routes.FAMILY}/$familyId") },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSignOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                },
            )
        }
        composable("${Routes.TRANSACTIONS}/{familyId}") { entry ->
            val familyId = entry.arguments?.getString("familyId").orEmpty()
            TransactionsScreen(
                familyId = familyId,
                onAdd = { navController.navigate("${Routes.ADD_TRANSACTION}/$familyId") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("${Routes.ADD_TRANSACTION}/{familyId}") { entry ->
            val familyId = entry.arguments?.getString("familyId").orEmpty()
            AddTransactionScreen(
                familyId = familyId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable("${Routes.BUDGETS}/{familyId}") { entry ->
            val familyId = entry.arguments?.getString("familyId").orEmpty()
            BudgetsScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("${Routes.FAMILY}/{familyId}") { entry ->
            val familyId = entry.arguments?.getString("familyId").orEmpty()
            FamilyManagementScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
