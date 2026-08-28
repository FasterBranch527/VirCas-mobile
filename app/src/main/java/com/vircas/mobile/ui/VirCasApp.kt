package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                PremiumBottomBar(
                    route = route,
                    onNavigate = { target ->
                        nav.navigate(target) {
                            launchSingleTop = true
                            popUpTo("home") { saveState = true }
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(if (showBottomBar) padding else PaddingValues(0.dp))
        ) {
            composable("home") {
                PremiumHomeScreen(
                    viewModel = appViewModel,
                    onGame = { nav.navigate("game/$it") },
                    onBets = { nav.navigate("bets") },
                    onCases = { nav.navigate("cases") }
                )
            }
            composable("games") {
                PremiumGamesScreen(
                    onGame = { nav.navigate("game/$it") },
                    onCases = { nav.navigate("cases") }
                )
            }
            composable("bets") { BettingCenterScreen(appViewModel) }
            composable("inventory") { PremiumInventoryScreen(appViewModel) }
            composable("profile") {
                PremiumProfileScreen(
                    viewModel = appViewModel,
                    onHistory = { nav.navigate("history") },
                    onFairness = { nav.navigate("fairness") },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable("game/{gameId}") { entry ->
                val gameId = entry.arguments?.getString("gameId") ?: "dice"
                val back = { nav.popBackStack(); Unit }
                when (gameId) {
                    "dice" -> PremiumDiceGameScreen(appViewModel, back)
                    "coinflip" -> CinematicCoinflip3DGameScreen(appViewModel, back)
                    "wheel" -> UltraWheelGameScreen(appViewModel, back)
                    "roulette" -> RealisticRouletteGameScreen(appViewModel, back)
                    "slots" -> UltraSlotsGameScreen(appViewModel, back)
                    "plinko" -> PremiumPlinkoGameScreen(appViewModel, back)
                    "mines" -> PremiumMinesGameScreen(appViewModel, back)
                    "crash" -> CinematicCrashGameScreen(appViewModel, back)
                    "blackjack" -> FullBlackjackGameScreen(appViewModel, back)
                    "hilo" -> PremiumHiLoGameScreen(appViewModel, back)
                    "towers" -> PremiumTowersGameScreen(appViewModel, back)
                    "ladder" -> PremiumLadderGameScreen(appViewModel, back)
                    "horse" -> UltraHorseRacingGameScreen(appViewModel, back)
                    else -> GamePlayScreen(gameId = gameId, viewModel = appViewModel, onBack = back)
                }
            }
            composable("cases") { AnimatedCasesHubScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("history") { PremiumHistoryScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("fairness") { PremiumFairnessScreen(appViewModel, onBack = { nav.popBackStack() }) }
            composable("settings") { PremiumSettingsScreen(appViewModel, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun PremiumBottomBar(route: String?, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        shape = RoundedCornerShape(26.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        shadowElevation = 16.dp
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            navItems.forEach { item ->
                val selected = route == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = {
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF181107),
                        selectedTextColor = ShellGold,
                        indicatorColor = ShellGold,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    )
                )
            }
        }
    }
}
