package com.arnit.imagex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.screens.AuthScreen
import com.arnit.imagex.ui.screens.BatchScreen
import com.arnit.imagex.ui.screens.ConvertScreen
import com.arnit.imagex.ui.screens.FormatsScreen
import com.arnit.imagex.ui.screens.HistoryScreen
import com.arnit.imagex.ui.screens.HomeScreen
import com.arnit.imagex.ui.screens.PremiumScreen
import com.arnit.imagex.ui.screens.SelectScreen
import com.arnit.imagex.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SELECT = "select"
    const val CONVERT = "convert"
    const val BATCH = "batch"
    const val FORMATS = "formats"
    const val HISTORY = "history"
    const val PREMIUM = "premium"
    const val SETTINGS = "settings"
    const val AUTH = "auth"
}

@Composable
fun ImageXNavHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onConvert = { navController.navigate(Routes.SELECT) },
                onBatch = { navController.navigate(Routes.BATCH) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onFormats = { navController.navigate(Routes.FORMATS) },
                onPremium = { navController.navigate(Routes.PREMIUM) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAccount = { navController.navigate(Routes.AUTH) }
            )
        }
        composable(Routes.SELECT) {
            SelectScreen(vm = vm, onBack = { navController.popBackStack() },
                onPicked = { navController.navigate(Routes.CONVERT) },
                onBatchPicked = { navController.navigate(Routes.BATCH) })
        }
        composable(Routes.CONVERT) {
            ConvertScreen(vm = vm, onBack = { navController.popBackStack() },
                onPickAnother = {
                    vm.resetConversion()
                    vm.clearSource()
                    navController.navigate(Routes.SELECT) { popUpTo(Routes.HOME) }
                }, onUpgrade = { navController.navigate(Routes.PREMIUM) })
        }
        composable(Routes.BATCH) {
            BatchScreen(vm = vm, onBack = { navController.popBackStack() },
                onAddImages = { navController.navigate(Routes.SELECT) { launchSingleTop = true } },
                onUpgrade = { navController.navigate(Routes.PREMIUM) })
        }
        composable(Routes.FORMATS) { FormatsScreen(vm = vm, onBack = { navController.popBackStack() }) }
        composable(Routes.HISTORY) { HistoryScreen(vm = vm, onBack = { navController.popBackStack() }) }
        composable(Routes.PREMIUM) { PremiumScreen(vm = vm, onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(vm = vm, onBack = { navController.popBackStack() }) }
        composable(Routes.AUTH) { AuthScreen(onBack = { navController.popBackStack() }) }
    }
}
