package com.vircas.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

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
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1320)) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = { nav.navigate(item.route) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true } },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen() }
            composable("games") { GamesScreen() }
            composable("bets") { ComingSoonScreen("Virtual Betting", "Fictional sports & esports simulation") }
            composable("inventory") { ComingSoonScreen("Inventory", "Your fictional case drops will live here") }
            composable("profile") { ProfileScreen() }
        }
    }
}

@Composable
private fun HomeScreen() {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIRCAS // PLAYER", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NightShift", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Level 1 · 0 XP", color = Color(0xFF94A3B8))
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF171E2E)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("BALANCE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("100,000 VC", fontWeight = FontWeight.ExtraBold, color = Color(0xFFF8FAFC))
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF0E7490), Color(0xFF111827))),
                    RoundedCornerShape(28.dp)
                ).padding(24.dp)
            ) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("FEATURED ORIGINAL", color = Color(0xFFBAE6FD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NEON MINES", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("25 tiles. One choice at a time.", color = Color(0xFFE2E8F0))
                }
            }
        }
        item { SectionTitle("Popular Games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf("Mines", "Dice", "Coinflip", "Wheel", "Roulette")) { game -> GameCard(game) }
            }
        }
        item { SectionTitle("Daily Missions") }
        items(listOf("Play 5 rounds", "Win any original", "Try 3 different games")) { mission ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp)); Text(mission, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("+XP", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String) = Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

@Composable
private fun GameCard(name: String) {
    Surface(modifier = Modifier.width(150.dp).height(110.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF151D2D)) {
        Box(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.align(Alignment.BottomStart), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GamesScreen() {
    val categories = listOf("Originals", "Casino", "Cards", "Betting", "Cases", "Arcade")
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Games", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        items(categories) { category ->
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(14.dp)); Text(category, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun ComingSoonScreen(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Construction, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp)); Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun ProfileScreen() {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("NightShift", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(listOf("Games played" to "0", "Win rate" to "—", "Biggest win" to "0 VC", "Favorite game" to "—")) { (label, value) ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(18.dp)) { Text(label, Modifier.weight(1f), color = Color(0xFF94A3B8)); Text(value, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
