package com.vircas.mobile.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vircas.mobile.ui.theme.VirCasTheme

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "Home", Icons.Rounded.Home),
    NavItem("games", "Games", Icons.Rounded.Casino),
    NavItem("bets", "Bets", Icons.Rounded.ReceiptLong),
    NavItem("inventory", "Inventory", Icons.Rounded.Inventory2),
    NavItem("profile", "Profile", Icons.Rounded.Person)
)

@Composable
fun VirCasApp(appViewModel: AppViewModel = viewModel()) {
    val settings by appViewModel.settings.collectAsState()
    VirCasTheme(darkTheme = settings.darkMode) {
        if (!settings.onboardingComplete) {
            OnboardingScreen(onComplete = appViewModel::completeOnboarding)
        } else {
            VirCasNavigation(appViewModel)
        }
    }
}

@Composable
private fun VirCasNavigation(appViewModel: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = route in navItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    launchSingleTop = true
                                    popUpTo("home") { saveState = true }
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(if (showBottomBar) padding else PaddingValues(0.dp))
        ) {
            composable("home") {
                HomeHubScreen(appViewModel, onGame = { nav.navigate("game/$it") })
            }
            composable("games") {
                GamesHubScreen(
                    onGame = { nav.navigate("game/$it") },
                    onCases = { nav.navigate("cases") }
                )
            }
            composable("bets") { BetsHubScreen(appViewModel) }
            composable("inventory") { InventoryHubScreen(appViewModel) }
            composable("profile") {
                ProfileHubScreen(
                    viewModel = appViewModel,
                    onHistory = { nav.navigate("history") },
                    onFairness = { nav.navigate("fairness") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("game/{gameId}") { entry ->
                GamePlayScreen(
                    gameId = entry.arguments?.getString("gameId") ?: "dice",
                    viewModel = appViewModel,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("cases") { CasesHubScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("history") { HistoryHubScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("fairness") { FairnessHubScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsHubScreen(appViewModel, onBack = { nav.popBackStack() }) }
        }
    }
}
