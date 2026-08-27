package com.vircas.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.random.SecureRandomProvider
import com.vircas.mobile.game.engines.CasesEngine
import com.vircas.mobile.game.engines.SportsBettingEngine

private val mvvmGames = listOf(
    "dice", "coinflip", "mines", "wheel", "roulette", "blackjack", "hilo",
    "towers", "ladder", "slots", "crash", "plinko", "horse"
)

@Composable
fun HomeHubScreen(viewModel: AppViewModel, onGame: (String) -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val missions = remember(progress) {
        // Definitions are derived from persisted daily counters in the repository.
        listOf(
            Triple("Play 5 rounds", progress.dailyGames, 5),
            Triple("Win 3 rounds", progress.dailyWins, 3),
            Triple("Open 2 cases", progress.dailyCases, 2),
            Triple("Place 5 virtual bets", progress.dailyBets, 5),
            Triple("Play 3 different games", progress.dailyDistinctGames.size, 3)
        )
    }
    var rewardText by remember { mutableStateOf("Claim daily reward") }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIRCAS // PLAYER", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NightShift", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Level ${progress.level} · ${progress.levelXp}/1000 XP", color = Color(0xFF94A3B8))
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF171E2E)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("BALANCE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("$balance VC", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(190.dp).clickable { onGame("mines") }.background(
                    Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF0E7490), Color(0xFF111827))),
                    RoundedCornerShape(28.dp)
                ).padding(24.dp)
            ) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("FEATURED ORIGINAL", color = Color(0xFFBAE6FD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NEON MINES", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("25 tiles · local-only simulation", color = Color(0xFFE2E8F0))
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.claimDailyReward { claim ->
                        rewardText = if (claim == null) "Already claimed today" else "Day ${claim.day}: +${claim.amount} VC"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(rewardText) }
        }
        item { HubSectionTitle("Popular Games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(mvvmGames.take(7)) { id -> HubGameCard(gameTitle(id)) { onGame(id) } }
            }
        }
        item { HubSectionTitle("Daily Missions") }
        items(missions) { (title, current, target) ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${current.coerceAtMost(target)}/$target", color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (current.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun GamesHubScreen(onGame: (String) -> Unit, onCases: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Games", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("Originals · Casino · Cards · Arcade", color = Color(0xFF94A3B8)) }
        items(mvvmGames) { id ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onGame(id) },
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF111827)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(14.dp))
                    Text(gameTitle(id), Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCases),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF111827)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text("Cases", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
fun CasesHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var result by remember { mutableStateOf("Choose a fictional cache.") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HubBackTitle("Cases", onBack) }
        item { Text("Balance: $balance VC", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
        items(CasesEngine.All) { definition ->
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(definition.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${definition.cost} VC · fictional weapon skins", color = Color(0xFF94A3B8))
                    Button(onClick = {
                        viewModel.openCase(definition) { opened ->
                            result = opened?.item?.let { "Dropped ${it.name} · ${it.rarity.name} · value ${it.marketValue} VC" }
                                ?: "Could not open case. Check balance."
                        }
                    }) { Text("Open ${definition.cost} VC") }
                }
            }
        }
        item { Text(result, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun BetsHubScreen(viewModel: AppViewModel) {
    val balance by viewModel.balance.collectAsState()
    val events = remember { SportsBettingEngine(SecureRandomProvider()).generateEvents() }
    var message by remember { mutableStateOf("Pick any fictional outcome. Stake: 1,000 VC") }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Virtual Betting", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("$balance VC · generated teams and odds only", color = MaterialTheme.colorScheme.secondary) }
        item { Text(message, color = Color(0xFF94A3B8)) }
        items(events) { event ->
            val selections = remember(event.id) { SportsBettingEngine(SecureRandomProvider()).selections(event) }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.sport.name, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${event.home} vs ${event.away}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    selections.forEach { selection ->
                        Button(
                            onClick = {
                                viewModel.placeVirtualBet(event, selection, 1_000L) { settled ->
                                    message = settled?.let { (receipt, simulation) ->
                                        "${event.home} ${simulation.homeScore}:${simulation.awayScore} ${event.away} · ${selection.label} · ${if (receipt.payout > 0) "WIN ${receipt.payout} VC" else "LOSS"}"
                                    } ?: "Bet could not be placed. Check balance."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${selection.label} · ${selection.odds}") }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryHubScreen(viewModel: AppViewModel) {
    val inventory by viewModel.inventory.collectAsState()
    var status by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Inventory", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.secondary) }
        if (inventory.isEmpty()) item { Text("Open a case to collect fictional items.", color = Color(0xFF94A3B8)) }
        items(inventory, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${item.weaponCategory} · ${item.rarity} · ${item.marketValue} VC", color = Color(0xFF94A3B8))
                    }
                    Button(onClick = {
                        viewModel.sellInventoryItem(item.id) { value -> status = value?.let { "Sold for $it VC" } ?: "Item already removed" }
                    }) { Text("Sell") }
                }
            }
        }
    }
}

@Composable
fun ProfileHubScreen(
    viewModel: AppViewModel,
    onHistory: () -> Unit,
    onFairness: () -> Unit,
    onSettings: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("NightShift · Level ${progress.level}", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(listOf(
            "Virtual balance" to "$balance VC",
            "Games played" to "${progress.gamesPlayed}",
            "Wins / losses" to "${progress.totalWins} / ${progress.totalLosses}",
            "Win rate" to "${"%.1f".format(progress.winRate)}%",
            "Biggest win" to "${progress.biggestWin} VC",
            "Total wagered" to "${progress.totalWagered} VC",
            "Favorite game" to progress.favoriteGame,
            "Daily streak" to "${progress.dailyStreak}"
        )) { (label, value) -> HubStatRow(label, value) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("History") }
                Button(onClick = onFairness, modifier = Modifier.weight(1f)) { Text("Fairness") }
            }
        }
        item { Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") } }
    }
}

@Composable
fun HistoryHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HubBackTitle("Game History", onBack) }
        if (history.isEmpty()) item { Text("No rounds yet.", color = Color(0xFF94A3B8)) }
        items(history, key = { it.id }) { row ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row {
                        Text(row.game, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(if (row.won) "+${row.profitLoss}" else "${row.profitLoss}", color = if (row.won) MaterialTheme.colorScheme.tertiary else Color(0xFFF87171))
                    }
                    Text("${row.result} · stake ${row.stake} · payout ${row.payout}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FairnessHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val rounds by viewModel.fairness.collectAsState()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HubBackTitle("Fairness", onBack) }
        item { Text("Local seed diagnostics for a virtual simulator; not a real-money cryptographic gambling system.", color = Color(0xFF94A3B8)) }
        if (rounds.isEmpty()) item { Text("Play a round to create a record.") }
        items(rounds, key = { it.roundId }) { round ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(round.game, fontWeight = FontWeight.Bold)
                    Text("Round ${round.roundId}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    Text("Seed ${round.generatedSeed.take(18)}… · client ${round.clientSeed.take(18)}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("Result ${round.result}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var clientSeed by remember(settings.clientSeed) { mutableStateOf(settings.clientSeed) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HubBackTitle("Settings", onBack) }
        item { HubSettingSwitch("Sound", settings.sound, viewModel::setSound) }
        item { HubSettingSwitch("Vibration", settings.vibration, viewModel::setVibration) }
        item { HubSettingSwitch("Animations", settings.animations, viewModel::setAnimations) }
        item { HubSettingSwitch("Reduced motion", settings.reducedMotion, viewModel::setReducedMotion) }
        item { HubSettingSwitch("Dark mode", settings.darkMode, viewModel::setDarkMode) }
        item { HubSettingSwitch("Secure RNG", settings.secureRng, viewModel::setSecureRng) }
        item { HubSettingSwitch("Developer diagnostics", settings.developerDiagnostics, viewModel::setDeveloperDiagnostics) }
        item {
            OutlinedTextField(
                value = clientSeed,
                onValueChange = { clientSeed = it; viewModel.setClientSeed(it) },
                label = { Text("Client seed") },
                supportingText = { Text(if (settings.secureRng) "Recorded with fairness rounds" else "Debug seed mode is deterministic") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("RNG diagnostics", fontWeight = FontWeight.Bold)
                    Text("Mode: ${if (settings.secureRng) "SecureRandom" else "Deterministic"} · debug seed ${settings.debugSeed}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
        item {
            Button(onClick = { viewModel.resetLocalAccount() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset local account")
            }
        }
    }
}

@Composable
private fun HubSettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable private fun HubSectionTitle(title: String) = Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

@Composable
private fun HubGameCard(name: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.width(150.dp).height(110.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), color = Color(0xFF151D2D)) {
        Box(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.align(Alignment.BottomStart), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HubStatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(label, Modifier.weight(1f), color = Color(0xFF94A3B8)); Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HubBackTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}
