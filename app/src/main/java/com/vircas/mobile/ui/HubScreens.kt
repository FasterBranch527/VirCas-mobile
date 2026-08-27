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
import com.vircas.mobile.AppContainer
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.random.SeededRandomProvider
import com.vircas.mobile.core.random.SecureRandomProvider
import com.vircas.mobile.core.wallet.WalletRepository
import com.vircas.mobile.game.engines.*
import java.util.UUID
import kotlinx.coroutines.launch

private val playableGames = listOf(
    "dice", "coinflip", "mines", "wheel", "roulette", "blackjack", "hilo",
    "towers", "ladder", "slots", "crash", "plinko", "horse"
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("Virtual Gaming Hub", "Everything is virtual", "No deposits. No withdrawals.")
    val bodies = listOf(
        "A polished offline collection of locally simulated games.",
        "Coins, items, teams, odds, races and rewards have no monetary value.",
        "VirCas has no real-money purchase, betting, crypto, cashout or bookmaker integration."
    )
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(28.dp))
        Text(titles[page], fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(bodies[page], color = Color(0xFF94A3B8), fontSize = 17.sp)
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(progress = { (page + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (page < 2) page++ else onComplete() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (page < 2) "Continue" else "Start Playing", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun HomeScreen(container: AppContainer, onGame: (String) -> Unit) {
    val balance by container.walletRepository.balance.collectAsState(initial = WalletRepository.STARTING_BALANCE)
    val progress by container.progressionRepository.progress.collectAsState(initial = UserProgress())
    val missions = remember(progress) { container.progressionRepository.missions(progress) }
    val scope = rememberCoroutineScope()
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
                    Text("25 tiles · local deterministic rounds", color = Color(0xFFE2E8F0))
                }
            }
        }
        item {
            Button(onClick = {
                scope.launch {
                    val claim = container.progressionRepository.claimDailyReward()
                    if (claim == null) rewardText = "Already claimed today"
                    else {
                        container.walletRepository.credit(claim.amount)
                        rewardText = "Day ${claim.day}: +${claim.amount} VC"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(rewardText) }
        }
        item { SectionTitle("Popular Games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playableGames.take(7)) { id -> GameCard(gameTitle(id)) { onGame(id) } }
            }
        }
        item { SectionTitle("Daily Missions") }
        items(missions) { mission ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Text(mission.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${mission.progress.coerceAtMost(mission.target)}/${mission.target}", color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun GamesScreen(onGame: (String) -> Unit, onCases: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Games", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("Originals & Casino", color = Color(0xFF94A3B8)) }
        items(playableGames) { id ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onGame(id) },
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF111827)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(gameTitle(id), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Offline virtual engine", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
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
                    Spacer(Modifier.width(14.dp)); Text("Cases", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
fun CasesScreen(container: AppContainer, onBack: () -> Unit) {
    val balance by container.walletRepository.balance.collectAsState(initial = WalletRepository.STARTING_BALANCE)
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("Choose a fictional cache.") }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BackTitle("Cases", onBack) }
        item { Text("Balance: $balance VC", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
        items(CasesEngine.All) { case ->
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(case.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${case.cost} VC · fictional weapon skins", color = Color(0xFF94A3B8))
                    Button(onClick = {
                        scope.launch {
                            if (!container.walletRepository.debit(case.cost)) {
                                result = "Not enough VC"
                                return@launch
                            }
                            val seed = UUID.randomUUID().toString().replace("-", "")
                            val opened = CasesEngine(SeededRandomProvider(seed.hashCode().toLong())).open(case)
                            val item = opened.item
                            container.inventoryRepository.add(
                                InventoryItemEntity(
                                    id = UUID.randomUUID().toString(),
                                    templateId = item.id,
                                    name = item.name,
                                    weaponCategory = item.weaponCategory,
                                    rarity = item.rarity.name,
                                    marketValue = item.marketValue,
                                    previewKey = item.previewKey,
                                    acquiredAt = System.currentTimeMillis()
                                )
                            )
                            container.progressionRepository.recordCaseOpen()
                            container.progressionRepository.recordGame("Cases", case.cost, 0, xpReward = 35)
                            container.historyRepository.record("Cases", case.cost, 0, 0.0, "${item.rarity.name}: ${item.name}", "marketValue=${item.marketValue}")
                            container.fairnessRepository.record("Cases", seed, "local", item.id)
                            result = "Dropped ${item.name} · ${item.rarity.name} · value ${item.marketValue} VC"
                        }
                    }) { Text("Open ${case.cost} VC") }
                }
            }
        }
        item { Text(result, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun BetsScreen(container: AppContainer) {
    val balance by container.walletRepository.balance.collectAsState(initial = WalletRepository.STARTING_BALANCE)
    val events = remember { SportsBettingEngine(SecureRandomProvider()).generateEvents() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Pick a fictional home team. Stake: 1,000 VC") }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Virtual Betting", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("$balance VC · generated teams and odds only", color = MaterialTheme.colorScheme.secondary) }
        item { Text(message, color = Color(0xFF94A3B8)) }
        items(events) { event ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.sport.name, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${event.home} vs ${event.away}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val stake = 1_000L
                                if (!container.walletRepository.debit(stake)) {
                                    message = "Not enough VC"
                                    return@launch
                                }
                                val seed = UUID.randomUUID().toString().replace("-", "")
                                val simulation = SportsBettingEngine(SeededRandomProvider(seed.hashCode().toLong())).simulate(event)
                                val won = simulation.winnerSelectionId == "${event.id}:home"
                                val payout = if (won) (stake * event.homeOdds).toLong() else 0L
                                if (payout > 0) container.walletRepository.credit(payout)
                                container.progressionRepository.recordVirtualBet()
                                container.progressionRepository.recordGame("Sports Betting", stake, payout, xpReward = 25)
                                container.historyRepository.record("Sports Betting", stake, payout, if (won) event.homeOdds else 0.0, "${simulation.homeScore}:${simulation.awayScore}", event.id)
                                container.fairnessRepository.record("Sports Betting", seed, "local", simulation.winnerSelectionId)
                                message = "${event.home} ${simulation.homeScore}:${simulation.awayScore} ${event.away} · ${if (won) "WIN $payout VC" else "LOSS"}"
                            }
                        }) { Text("${event.home} ${event.homeOdds}") }
                        Text("${event.awayOdds}", modifier = Modifier.padding(top = 12.dp), color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(container: AppContainer) {
    val items by container.inventoryRepository.items.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Inventory", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        if (items.isEmpty()) item { Text("Open a case to collect fictional items.", color = Color(0xFF94A3B8)) }
        items(items, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${item.weaponCategory} · ${item.rarity} · ${item.marketValue} VC", color = Color(0xFF94A3B8))
                    }
                    Button(onClick = {
                        scope.launch {
                            val existing = container.inventoryRepository.find(item.id) ?: return@launch
                            container.inventoryRepository.remove(existing.id)
                            container.walletRepository.credit(existing.marketValue)
                        }
                    }) { Text("Sell") }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(container: AppContainer, onHistory: () -> Unit, onFairness: () -> Unit) {
    val balance by container.walletRepository.balance.collectAsState(initial = WalletRepository.STARTING_BALANCE)
    val progress by container.progressionRepository.progress.collectAsState(initial = UserProgress())
    val achievements = remember(progress) { container.progressionRepository.achievements(progress) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("NightShift · Level ${progress.level}", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        items(listOf(
            "Virtual balance" to "$balance VC",
            "Games played" to "${progress.gamesPlayed}",
            "Win rate" to "${"%.1f".format(progress.winRate)}%",
            "Biggest win" to "${progress.biggestWin} VC",
            "Total wagered" to "${progress.totalWagered} VC",
            "Favorite game" to progress.favoriteGame,
            "Daily streak" to "${progress.dailyStreak}"
        )) { (label, value) -> StatRow(label, value) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("History") }
                Button(onClick = onFairness, modifier = Modifier.weight(1f)) { Text("Fairness") }
            }
        }
        item { SectionTitle("Achievements") }
        items(achievements) { achievement ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (achievement.unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null, tint = if (achievement.unlocked) MaterialTheme.colorScheme.tertiary else Color(0xFF64748B))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(achievement.title, fontWeight = FontWeight.Bold)
                        Text(achievement.description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Text("+${achievement.xpReward} XP", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit) {
    val history by container.historyRepository.recent(100).collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackTitle("Game History", onBack) }
        if (history.isEmpty()) item { Text("No rounds yet.", color = Color(0xFF94A3B8)) }
        items(history, key = { it.id }) { row ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row { Text(row.game, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(if (row.won) "+${row.profitLoss}" else "${row.profitLoss}", color = if (row.won) MaterialTheme.colorScheme.tertiary else Color(0xFFF87171)) }
                    Text("${row.result} · stake ${row.stake} · payout ${row.payout}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FairnessScreen(container: AppContainer, onBack: () -> Unit) {
    val rounds by container.fairnessRepository.recent(100).collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackTitle("Fairness", onBack) }
        item { Text("Local deterministic seeds are recorded for transparency; this is not a real-money cryptographic gambling system.", color = Color(0xFF94A3B8)) }
        if (rounds.isEmpty()) item { Text("Play a round to create a record.") }
        items(rounds, key = { it.roundId }) { round ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(round.game, fontWeight = FontWeight.Bold)
                    Text("Round ${round.roundId}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    Text("Seed ${round.generatedSeed.take(18)}…", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("Result ${round.result}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String) = Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

@Composable
private fun GameCard(name: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.width(150.dp).height(110.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), color = Color(0xFF151D2D)) {
        Box(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.align(Alignment.BottomStart), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(label, Modifier.weight(1f), color = Color(0xFF94A3B8)); Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}
