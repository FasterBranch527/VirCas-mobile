package com.vircas.mobile.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.data.UserSettings
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "Home", Icons.Rounded.Home),
    NavItem("games", "Games", Icons.Rounded.Casino),
    NavItem("bets", "Bets", Icons.Rounded.ReceiptLong),
    NavItem("inventory", "Inventory", Icons.Rounded.Inventory2),
    NavItem("profile", "Profile", Icons.Rounded.Person)
)

@Composable
fun VirCasApp() {
    val application = LocalContext.current.applicationContext as VirCasApplication
    val container = application.container
    val settings by container.settingsRepository.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()

    if (!settings.onboardingComplete) {
        OnboardingScreen(onComplete = { scope.launch { container.settingsRepository.completeOnboarding() } })
        return
    }

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = route in navItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0D1320)) {
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
            modifier = Modifier.padding(if (showBottomBar) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            composable("home") {
                HomeScreen(container, onGame = { nav.navigate("game/$it") })
            }
            composable("games") {
                GamesScreen(
                    onGame = { nav.navigate("game/$it") },
                    onCases = { nav.navigate("cases") }
                )
            }
            composable("bets") { BetsScreen(container) }
            composable("inventory") { InventoryScreen(container) }
            composable("profile") {
                ProfileScreen(
                    container = container,
                    onHistory = { nav.navigate("history") },
                    onFairness = { nav.navigate("fairness") }
                )
            }
            composable("game/{gameId}") { entry ->
                GamePlayScreen(
                    gameId = entry.arguments?.getString("gameId") ?: "dice",
                    container = container,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("cases") { CasesScreen(container, onBack = { nav.popBackStack() }) }
            composable("history") { HistoryScreen(container, onBack = { nav.popBackStack() }) }
            composable("fairness") { FairnessScreen(container, onBack = { nav.popBackStack() }) }
        }
    }
}
