package com.vircas.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.game.engines.SportsBettingEngine

private val dashboardGames = listOf("mines", "blackjack", "roulette", "crash", "slots", "plinko", "dice", "coinflip")

@Composable
fun HomeDashboardScreen(
    viewModel: AppViewModel,
    onGame: (String) -> Unit,
    onBets: () -> Unit,
    onCases: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val history by viewModel.history.collectAsState()
    val animatedBalance by animateFloatAsState(targetValue = balance.toFloat(), label = "virtual-balance")
    val missions = remember(progress) { viewModel.dailyMissions(progress) }
    val sports = remember { SportsBettingEngine(viewModel.randomProvider()).generateEvents().take(4) }
    var rewardMessage by remember { mutableStateOf<String?>(null) }
    var missionMessage by remember { mutableStateOf<String?>(null) }

    val continueGame = remember(history) {
        history.firstNotNullOfOrNull { row -> gameIdFromHistory(row.game) } ?: "mines"
    }
    val recentWins = remember(history) { history.filter { it.won }.take(8) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("NightShift", fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Level ${progress.level} · ${progress.levelXp}/1000 XP", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("VIRTUAL BALANCE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
                        Text("%,d VC".format(animatedBalance.toLong()), fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item {
            LinearProgressIndicator(
                progress = { progress.levelXp / 1000f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onGame("mines") },
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(24.dp)
                ) {
                    Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("FEATURED ORIGINAL", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("NEON MINES", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Text("5×5 board · live multiplier · cash out", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.align(Alignment.TopEnd), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily Reward", fontWeight = FontWeight.Black)
                        Text("Streak ${progress.dailyStreak} · 7-day cycle", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f), fontSize = 12.sp)
                        rewardMessage?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                    }
                    Button(onClick = {
                        viewModel.claimDailyReward { claim ->
                            rewardMessage = claim?.let { "Day ${it.day}: +${it.amount} VC" } ?: "Already claimed today"
                        }
                    }) { Text("CLAIM") }
                }
            }
        }

        item { DashboardSectionTitle("Continue Playing") }
        item {
            DashboardActionCard(
                icon = Icons.Rounded.Casino,
                title = gameTitle(continueGame),
                subtitle = if (history.isEmpty()) "Start your first round" else "Resume from recent activity",
                onClick = { onGame(continueGame) }
            )
        }

        item { DashboardSectionTitle("Popular Games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(dashboardGames) { id ->
                    Surface(
                        modifier = Modifier.width(150.dp).height(108.dp).clickable { onGame(id) },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.Casino, null, tint = MaterialTheme.colorScheme.primary)
                            Text(gameTitle(id), fontWeight = FontWeight.Black, fontSize = 17.sp)
                        }
                    }
                }
            }
        }

        item { DashboardSectionTitle("Daily Missions") }
        missionMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) } }
        items(missions, key = { it.id }) { mission ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Text(mission.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${mission.progress.coerceAtMost(mission.target)}/${mission.target}", color = MaterialTheme.colorScheme.secondary)
                    }
                    LinearProgressIndicator(progress = { (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (mission.complete) {
                        Button(
                            onClick = {
                                viewModel.claimMission(mission) { coins ->
                                    missionMessage = if (coins != null) "+$coins VC · +${mission.xpReward} XP" else "Reward already claimed"
                                }
                            },
                            enabled = !mission.claimed,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (mission.claimed) "CLAIMED" else "CLAIM +${mission.coinReward} VC") }
                    }
                }
            }
        }

        item { DashboardSectionTitle("Sports Today") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sports, key = { it.id }) { event ->
                    Surface(
                        modifier = Modifier.width(220.dp).clickable(onClick = onBets),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(event.sport.name, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${event.home} vs ${event.away}", fontWeight = FontWeight.Black)
                            Text("${event.homeOdds} · ${event.drawOdds ?: "—"} · ${event.awayOdds}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { DashboardSectionTitle("Case Drop") }
        item {
            DashboardActionCard(
                icon = Icons.Rounded.Inventory2,
                title = "Open fictional skin cases",
                subtitle = "Precomputed reel · persistent inventory · virtual resale",
                onClick = onCases
            )
        }

        item { DashboardSectionTitle("Recent Wins") }
        if (recentWins.isEmpty()) {
            item { Text("Winning rounds will appear here.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentWins, key = { it.id }) { row ->
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.width(180.dp).padding(14.dp)) {
                                Text(row.game, fontWeight = FontWeight.Bold)
                                Text("+%,d VC".format(row.profitLoss), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Black)
                                Text(row.result, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

private enum class HistoryResultFilter { ALL, WINS, LOSSES }

@Composable
fun FilterableHistoryScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var resultFilter by remember { mutableStateOf(HistoryResultFilter.ALL) }
    var gameFilter by remember { mutableStateOf<String?>(null) }
    val games = remember(history) { history.map { it.game }.distinct().sorted() }
    val visible = remember(history, resultFilter, gameFilter) {
        history.filter { row ->
            val resultMatches = when (resultFilter) {
                HistoryResultFilter.ALL -> true
                HistoryResultFilter.WINS -> row.won
                HistoryResultFilter.LOSSES -> !row.won
            }
            resultMatches && (gameFilter == null || row.game == gameFilter)
        }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Game History", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("${visible.size} of ${history.size} local rounds", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), fontSize = 12.sp)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(HistoryResultFilter.entries) { filter ->
                    FilterChip(
                        selected = resultFilter == filter,
                        onClick = { resultFilter = filter },
                        label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        if (games.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        FilterChip(selected = gameFilter == null, onClick = { gameFilter = null }, label = { Text("All games") })
                    }
                    items(games) { game ->
                        FilterChip(selected = gameFilter == game, onClick = { gameFilter = game }, label = { Text(game) })
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item { Text("No rounds match these filters.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)) }
        }

        items(visible, key = { it.id }) { row -> HistoryRow(row) }
    }
}

@Composable
private fun HistoryRow(row: GameHistoryEntity) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.game, Modifier.weight(1f), fontWeight = FontWeight.Black)
                Text(
                    if (row.profitLoss >= 0) "+%,d VC".format(row.profitLoss) else "%,d VC".format(row.profitLoss),
                    color = if (row.won) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(row.result, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
            Row {
                Text("Stake %,d".format(row.stake), Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f))
                Text("Payout %,d · ${"%.2f".format(row.multiplier)}x".format(row.payout), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f))
            }
            if (row.details.isNotBlank()) Text(row.details, maxLines = 2, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DashboardSectionTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun DashboardActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

private fun gameIdFromHistory(name: String): String? = when (name.lowercase()) {
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
