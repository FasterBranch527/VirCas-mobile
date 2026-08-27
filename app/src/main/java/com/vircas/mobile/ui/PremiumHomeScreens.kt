package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumHomeScreen(
    viewModel: AppViewModel,
    onGame: (String) -> Unit,
    onBets: () -> Unit,
    onCases: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val history by viewModel.history.collectAsState()
    val missions = remember(progress) { viewModel.dailyMissions(progress) }
    val continueGame = remember(history) { history.firstNotNullOfOrNull { shellGameIdFromHistory(it.game) } ?: "roulette" }
    var showFunds by remember { mutableStateOf(false) }
    var rewardText by remember { mutableStateOf<String?>(null) }
    var missionText by remember { mutableStateOf<String?>(null) }

    if (showFunds) {
        VirtualFundsDialog(
            balance = balance,
            onDismiss = { showFunds = false },
            onAdd = { amount -> viewModel.addVirtualFunds(amount) { if (it) showFunds = false } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ShellHeader(
                eyebrow = "VIRCAS // PLAYER HUB",
                title = "NightShift",
                subtitle = "Level ${progress.level} · ${progress.levelXp}/1000 XP",
                icon = Icons.Rounded.Person
            )
        }
        item { VirtualWalletCard(balance = balance, onAddFunds = { showFunds = true }) }
        item {
            LinearProgressIndicator(
                progress = { (progress.levelXp / 1000f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = ShellCyan,
                trackColor = ShellPanel2
            )
        }
        item {
            FeaturedHubCard(
                onRoulette = { onGame("roulette") },
                onMines = { onGame("mines") },
                onBets = onBets
            )
        }
        item { SectionHeader("Jump back in", "Your last table is one tap away") }
        item {
            ShellActionCard(
                icon = Icons.Rounded.Casino,
                title = gameTitle(continueGame),
                subtitle = if (history.isEmpty()) "Start your first local round" else "Continue from recent activity",
                accent = ShellPurple,
                onClick = { onGame(continueGame) }
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = ShellGold.copy(alpha = 0.15f)) {
                        Icon(Icons.Rounded.Stars, null, Modifier.padding(11.dp), tint = ShellGold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Daily drop", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(
                            "Streak ${progress.dailyStreak} · virtual coins + XP",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            fontSize = 12.sp
                        )
                        rewardText?.let { Text(it, color = ShellGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Button(onClick = {
                        viewModel.claimDailyReward { claim ->
                            rewardText = claim?.let { "Day ${it.day}: +${formatShellVc(it.amount)}" } ?: "Already claimed today"
                        }
                    }) { Text("CLAIM", fontWeight = FontWeight.Black) }
                }
            }
        }
        item { SectionHeader("Popular tables", "Casino, originals and arcade") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(premiumGameIds.take(8)) { id -> MiniGameCard(id = id, onClick = { onGame(id) }) }
            }
        }
        item { SectionHeader("Daily missions", "Small goals, persistent rewards") }
        missionText?.let { message -> item { Text(message, color = ShellGreen, fontWeight = FontWeight.Bold) } }
        items(missions.take(3), key = { it.id }) { mission ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (mission.claimed) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt,
                            null,
                            tint = if (mission.claimed) ShellGreen else ShellGold
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, fontWeight = FontWeight.Bold)
                            Text(
                                "+${formatShellVc(mission.coinReward)} · +${mission.xpReward} XP",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "${mission.progress.coerceAtMost(mission.target)}/${mission.target}",
                            color = ShellCyan,
                            fontWeight = FontWeight.Black
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ShellPurple,
                        trackColor = ShellPanel2
                    )
                    if (mission.complete) {
                        OutlinedButton(
                            enabled = !mission.claimed,
                            onClick = {
                                viewModel.claimMission(mission) { coins ->
                                    missionText = coins?.let { "+${formatShellVc(it)} claimed" } ?: "Reward already claimed"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (mission.claimed) "CLAIMED" else "CLAIM REWARD") }
                    }
                }
            }
        }
        item { SectionHeader("More", "Cases and recent activity") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShellActionCard(
                    icon = Icons.Rounded.Inventory2,
                    title = "Case Drop",
                    subtitle = "Open fictional skins",
                    accent = ShellGold,
                    modifier = Modifier.weight(1f),
                    onClick = onCases
                )
                ShellActionCard(
                    icon = Icons.Rounded.History,
                    title = "Last result",
                    subtitle = history.firstOrNull()?.let { "${it.game}: ${it.result}" } ?: "No rounds yet",
                    accent = ShellCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { onGame(continueGame) }
                )
            }
        }
    }
}

@Composable
fun PremiumGamesScreen(onGame: (String) -> Unit, onCases: () -> Unit) {
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All", "Casino", "Originals", "Cards", "Arcade")
    val visible = premiumGameIds.filter { id ->
        when (category) {
            "Casino" -> id in listOf("roulette", "blackjack", "slots")
            "Originals" -> id in listOf("mines", "crash", "dice", "coinflip", "wheel", "plinko")
            "Cards" -> id in listOf("blackjack", "hilo")
            "Arcade" -> id in listOf("towers", "ladder", "horse")
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShellHeader(
                "VIRCAS // LIBRARY",
                "Games",
                "Everything local. Everything virtual.",
                Icons.Rounded.SportsEsports
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { name ->
                    FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                }
            }
        }
        items(visible.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { id -> LargeGameCard(id, Modifier.weight(1f)) { onGame(id) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            ShellActionCard(
                icon = Icons.Rounded.Inventory2,
                title = "Cases",
                subtitle = "Animated openings · persistent inventory",
                accent = ShellGold,
                onClick = onCases
            )
        }
    }
}

@Composable
private fun FeaturedHubCard(onRoulette: () -> Unit, onMines: () -> Unit, onBets: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ShellPurple.copy(alpha = 0.32f))
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF24113E), Color(0xFF10263F), Color(0xFF092B29)))
            ).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("THE FLOOR IS OPEN", color = ShellCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(
                "Pick a table.\nChase a virtual streak.",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroAction("ROULETTE", Icons.Rounded.Casino, ShellGold, Modifier.weight(1f), onRoulette)
                HeroAction("MINES", Icons.Rounded.AutoAwesome, ShellCyan, Modifier.weight(1f), onMines)
                HeroAction("BETS", Icons.Rounded.ReceiptLong, ShellGreen, Modifier.weight(1f), onBets)
            }
        }
    }
}

@Composable
private fun HeroAction(
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = accent)
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MiniGameCard(id: String, onClick: () -> Unit) {
    val accent = gameAccent(id)
    Surface(
        modifier = Modifier.width(142.dp).height(102.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Rounded.Casino, null, tint = accent)
            Text(gameTitle(id), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LargeGameCard(id: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = gameAccent(id)
    Surface(
        modifier = modifier.height(122.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f)) {
                    Icon(Icons.Rounded.Casino, null, Modifier.padding(8.dp), tint = accent)
                }
                Spacer(Modifier.weight(1f))
            }
            Column {
                Text(gameTitle(id), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(gameTagline(id), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f), fontSize = 10.sp)
            }
        }
    }
}

private fun shellGameIdFromHistory(name: String): String? = when (name.lowercase()) {
    "dice" -> "dice"
    "coinflip" -> "coinflip"
    "mines" -> "mines"
    "wheel" -> "wheel"
    "roulette" -> "roulette"
    "blackjack" -> "blackjack"
    "hi-lo" -> "hilo"
    "towers" -> "towers"
    "ladder" -> "ladder"
    "slots" -> "slots"
    "crash" -> "crash"
    "plinko" -> "plinko"
    "horse racing" -> "horse"
    else -> null
}
