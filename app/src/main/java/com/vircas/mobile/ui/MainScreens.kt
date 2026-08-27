package com.vircas.mobile.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.UserSettings
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.game.engines.EsportsBettingEngine
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRaceResult
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent
import com.vircas.mobile.game.engines.VirtualSport
import kotlinx.coroutines.delay

internal data class GameSpec(val id: String, val title: String, val category: String)

internal val gameSpecs = listOf(
    GameSpec("dice", "Dice", "Originals"),
    GameSpec("coinflip", "Coinflip", "Originals"),
    GameSpec("mines", "Mines", "Originals"),
    GameSpec("wheel", "Wheel", "Originals"),
    GameSpec("crash", "Crash", "Originals"),
    GameSpec("plinko", "Plinko", "Originals"),
    GameSpec("towers", "Towers", "Originals"),
    GameSpec("ladder", "Ladder", "Originals"),
    GameSpec("roulette", "Roulette", "Casino"),
    GameSpec("slots", "Slots", "Casino"),
    GameSpec("blackjack", "Blackjack", "Cards"),
    GameSpec("hilo", "Hi-Lo", "Cards"),
    GameSpec("cases", "Cases", "Cases"),
    GameSpec("horse", "Horse Racing", "Betting")
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("Virtual Gaming Hub", "Everything is virtual", "No deposits. No withdrawals.")
    val bodies = listOf(
        "A polished offline gaming hub with casino-style simulations, originals, cases and fictional betting.",
        "Coins, items, odds, teams, horses and events exist only inside this app and have no monetary value.",
        "VirCas has no real-money deposits, purchases, cashout, crypto or bookmaker integrations."
    )
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF111827), Color(0xFF25124A)))).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0x2218FFFF)) {
                Icon(Icons.Rounded.Casino, null, modifier = Modifier.padding(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("0${page + 1} / 03", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(titles[page], fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(bodies[page], color = Color(0xFFCBD5E1), fontSize = 17.sp)
            Button(onClick = { if (page < 2) page++ else onComplete() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (page < 2) "CONTINUE" else "START PLAYING")
            }
        }
    }
}

@Composable
fun LiveHomeScreen(
    vm: AppViewModel,
    balance: Long,
    progress: UserProgress,
    history: List<GameHistoryEntity>,
    onGame: (String) -> Unit,
    onBets: () -> Unit
) {
    var dailyMessage by remember { mutableStateOf<String?>(null) }
    val missions = listOf(
        Triple("Play 5 rounds", progress.dailyGames, 5),
        Triple("Win 3 rounds", progress.dailyWins, 3),
        Triple("Open 2 cases", progress.dailyCases, 2),
        Triple("Place 5 virtual bets", progress.dailyBets, 5),
        Triple("Play 3 different games", progress.dailyDistinctGames.size, 3)
    )
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIRCAS // PLAYER", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NightShift", fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Level ${progress.level} · ${progress.levelXp}/1000 XP", color = Color(0xFF94A3B8))
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF171E2E)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("BALANCE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("%,d VC".format(balance), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            Surface(onClick = { onGame("mines") }, shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
                Box(
                    Modifier.fillMaxWidth().height(190.dp).background(
                        Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF0E7490), Color(0xFF111827))),
                        RoundedCornerShape(28.dp)
                    ).padding(24.dp)
                ) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text("FEATURED ORIGINAL", color = Color(0xFFBAE6FD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("NEON MINES", fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Text("25 tiles. Cash out before the mine.", color = Color(0xFFE2E8F0))
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reward", fontWeight = FontWeight.Bold)
                        Text("Streak ${progress.dailyStreak} days", color = Color(0xFF94A3B8))
                        dailyMessage?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                    }
                    Button(onClick = {
                        vm.claimDailyReward { claim ->
                            dailyMessage = claim?.let { "Day ${it.day}: +${it.amount} VC" } ?: "Already claimed today"
                        }
                    }) { Text("CLAIM") }
                }
            }
        }
        item { SectionHeading("Popular Games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(gameSpecs.take(8)) { game ->
                    Surface(onClick = { onGame(game.id) }, modifier = Modifier.width(150.dp).height(112.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF151D2D)) {
                        Box(Modifier.padding(16.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Text(game.title, Modifier.align(Alignment.BottomStart), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { SectionHeading("Daily Missions") }
        items(missions) { (title, value, target) ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${value.coerceAtMost(target)}/$target", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item {
            Surface(onClick = onBets, shape = RoundedCornerShape(22.dp), color = Color(0xFF0F2532)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Sports Today", fontWeight = FontWeight.Bold); Text("Fictional locally generated events", color = Color(0xFF94A3B8)) }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionHeading("Recent Games") }
            items(history.take(5)) { item -> HistoryRow(item) }
        }
    }
}

@Composable
fun LiveGamesScreen(onGame: (String) -> Unit) {
    val categories = listOf("Originals", "Casino", "Cards", "Cases", "Betting")
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Games", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        categories.forEach { category ->
            item { Text(category.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            items(gameSpecs.filter { it.category == category }) { game ->
                Surface(onClick = { onGame(game.id) }, shape = RoundedCornerShape(22.dp), color = Color(0xFF111827)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (category == "Betting") Icons.Rounded.ReceiptLong else Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(14.dp)); Text(game.title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(items: List<InventoryItemEntity>, onSell: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Inventory", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        if (items.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF111827)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp)); Text("No case drops yet", fontWeight = FontWeight.Bold)
                        Text("Open fictional cases from Games.", color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${item.weaponCategory} · ${item.rarity}", color = Color(0xFF94A3B8))
                            Text("Virtual value: ${item.marketValue} VC", color = MaterialTheme.colorScheme.secondary)
                        }
                        OutlinedButton(onClick = { onSell(item.id) }) { Icon(Icons.Rounded.Sell, null); Spacer(Modifier.width(5.dp)); Text("SELL") }
                    }
                }
            }
        }
    }
}

@Composable
fun BettingScreen(vm: AppViewModel, balance: Long, onHorseRace: () -> Unit) {
    val sportsEngine = remember { SportsBettingEngine(vm.randomProvider()) }
    val sports = remember { sportsEngine.generateEvents() }
    val esports = remember {
        EsportsBettingEngine(vm.randomProvider()).generate().map { event ->
            VirtualEvent(event.id, VirtualSport.ESPORTS, event.teamA, event.teamB, event.matchWinnerA, null, event.matchWinnerB, System.currentTimeMillis() + 1_800_000L)
        }
    }
    val events = remember { sports + esports }
    var selections by remember { mutableStateOf<Map<String, MarketSelection>>(emptyMap()) }
    var stake by remember { mutableStateOf("1000") }
    var message by remember { mutableStateOf("Build a single or accumulator bet slip") }
    val combined = selections.values.fold(1.0) { acc, item -> acc * item.odds }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Virtual Bets", fontSize = 32.sp, fontWeight = FontWeight.Black); Text("All events are fictional and simulated locally", color = Color(0xFF94A3B8)) }
                Text("%,d VC".format(balance), fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(onClick = onHorseRace, modifier = Modifier.fillMaxWidth()) { Text("OPEN HORSE RACING") }
        }
        items(events) { event ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(event.sport.name, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${event.home} vs ${event.away}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sportsEngine.selections(event).forEach { selection ->
                            FilterChip(
                                selected = selections[event.id]?.id == selection.id,
                                onClick = {
                                    selections = if (selections[event.id]?.id == selection.id) selections - event.id else selections + (event.id to selection)
                                },
                                label = { Text("${selection.label} ${selection.odds}") }
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF172033)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BET SLIP", fontWeight = FontWeight.Black)
                    Text("${selections.size} selection(s) · ${"%.2f".format(combined)}x", color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(value = stake, onValueChange = { if (it.all(Char::isDigit)) stake = it.take(9) }, label = { Text("Stake VC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Possible payout: ${(stake.toLongOrNull() ?: 0L) * combined.toLong()}+ VC", color = Color(0xFF94A3B8))
                    Button(enabled = selections.isNotEmpty(), onClick = {
                        vm.placeVirtualBetSlip(events, selections.values.toList(), stake.toLongOrNull() ?: 0L) { receipt ->
                            message = receipt?.let { "${it.result} · payout ${it.payout} VC" } ?: "Could not place bet"
                            if (receipt != null) selections = emptyMap()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("PLACE VIRTUAL BET") }
                    Text(message, color = Color(0xFFCBD5E1))
                }
            }
        }
    }
}

@Composable
fun HorseRaceScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    val engine = remember { HorseRacingEngine(vm.randomProvider()) }
    var race by remember { mutableStateOf(engine.generateRace()) }
    var selected by remember { mutableStateOf(race.horses.first().id) }
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var result by remember { mutableStateOf<HorseRaceResult?>(null) }
    var racing by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Choose a horse. The finish order is determined before the animation starts.") }

    LaunchedEffect(racing, result) {
        val final = result ?: return@LaunchedEffect
        if (!racing) return@LaunchedEffect
        repeat(60) { step -> frame = step; delay(35) }
        racing = false
        val current = wager
        wager = null
        if (current != null) {
            val horse = race.horses.first { it.id == selected }
            val won = final.winnerId == selected
            vm.settleWager(current, if (won) horse.odds else 0.0, if (won) "${horse.name} won" else "${horse.name} lost", final.finishOrder.joinToString())
            message = if (won) "${horse.name} WON · ${horse.odds}x" else "Winner: ${race.horses.first { it.id == final.winnerId }.name}"
        }
    }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = onBack) { Text("BACK") }; Spacer(Modifier.width(12.dp)); Text("Horse Racing", fontSize = 28.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("%,d VC".format(balance)) } }
        items(race.horses) { horse -> HorseRow(horse, selected == horse.id, racing, frame, result?.finishOrder?.indexOf(horse.id) ?: -1) { selected = horse.id } }
        item {
            OutlinedTextField(value = stake, onValueChange = { if (it.all(Char::isDigit)) stake = it.take(9) }, enabled = !racing, label = { Text("Stake VC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(enabled = !racing, onClick = {
                vm.beginWager("Horse Racing", stake.toLongOrNull() ?: 0L) { started ->
                    if (started == null) message = "Insufficient balance" else {
                        wager = started
                        result = engine.simulate(race)
                        frame = 0
                        racing = true
                        message = "RACE LIVE"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START RACE") }
        }
        item { Text(message, color = Color(0xFFCBD5E1)) }
        if (!racing && result != null) {
            item { OutlinedButton(onClick = { race = engine.generateRace("race_${System.currentTimeMillis()}"); selected = race.horses.first().id; result = null; frame = 0 }, modifier = Modifier.fillMaxWidth()) { Text("NEW RACE") } }
        }
    }
}

@Composable
private fun HorseRow(horse: Horse, selected: Boolean, racing: Boolean, frame: Int, finishIndex: Int, onSelect: () -> Unit) {
    val raceProgress = if (!racing) 0f else ((frame / 59f) * (1f - (finishIndex.coerceAtLeast(0) * 0.015f))).coerceIn(0f, 1f)
    Surface(onClick = { if (!racing) onSelect() }, shape = RoundedCornerShape(18.dp), color = if (selected) Color(0xFF1D2B3F) else Color(0xFF111827)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row { Text(horse.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("${horse.odds}x", color = MaterialTheme.colorScheme.secondary) }
            Text("Rating ${horse.rating} · Form ${horse.form} · Speed ${horse.speed} · Stamina ${horse.stamina}", color = Color(0xFF94A3B8), fontSize = 11.sp)
            if (racing) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF253044), RoundedCornerShape(99.dp))) {
                    Box(Modifier.fillMaxWidth(raceProgress).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp)))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    progress: UserProgress,
    settings: UserSettings,
    history: List<GameHistoryEntity>,
    fairness: List<FairnessRoundEntity>,
    vm: AppViewModel
) {
    var clientSeed by remember(settings.clientSeed) { mutableStateOf(settings.clientSeed) }
    var confirmReset by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item { Text("NightShift · Level ${progress.level}", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        items(listOf(
            "Games played" to progress.gamesPlayed.toString(),
            "Wins / Losses" to "${progress.totalWins} / ${progress.totalLosses}",
            "Win rate" to "${"%.1f".format(progress.winRate)}%",
            "Biggest win" to "${progress.biggestWin} VC",
            "Total wagered" to "${progress.totalWagered} VC",
            "Favorite game" to progress.favoriteGame
        )) { (label, value) -> StatRow(label, value) }
        item { SectionHeading("Achievements") }
        items(listOf(
            "First Win" to (progress.totalWins >= 1),
            "On a Roll" to (progress.totalWins >= 5),
            "100 Games Played" to (progress.gamesPlayed >= 100),
            "High Roller" to (progress.totalWagered >= 100_000),
            "Hot Hand" to (progress.winStreak >= 10)
        )) { (label, unlocked) -> StatRow(label, if (unlocked) "UNLOCKED" else "LOCKED") }
        item { SectionHeading("Settings") }
        item { ToggleRow("Sound", settings.sound, vm::setSound) }
        item { ToggleRow("Vibration", settings.vibration, vm::setVibration) }
        item { ToggleRow("Animations", settings.animations, vm::setAnimations) }
        item { ToggleRow("Reduced motion", settings.reducedMotion, vm::setReducedMotion) }
        item { ToggleRow("Secure RNG", settings.secureRng, vm::setSecureRng) }
        item { ToggleRow("Developer diagnostics", settings.developerDiagnostics, vm::setDeveloperDiagnostics) }
        item {
            OutlinedTextField(value = clientSeed, onValueChange = { clientSeed = it.take(64) }, label = { Text("Client seed") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item { OutlinedButton(onClick = { vm.setClientSeed(clientSeed) }, modifier = Modifier.fillMaxWidth()) { Text("SAVE CLIENT SEED") } }
        item { SectionHeading("Fairness") }
        items(fairness.take(5)) { item ->
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF111827)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp)); Text(item.game, fontWeight = FontWeight.Bold) }
                    Text(item.roundId, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text("Seed ${item.generatedSeed.take(16)}… · ${item.result}", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                }
            }
        }
        item { SectionHeading("History") }
        items(history.take(10)) { HistoryRow(it) }
        item {
            if (!confirmReset) OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("RESET LOCAL ACCOUNT") }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.resetLocalAccount(); confirmReset = false }, modifier = Modifier.weight(1f)) { Text("CONFIRM RESET") }
                OutlinedButton(onClick = { confirmReset = false }, modifier = Modifier.weight(1f)) { Text("CANCEL") }
            }
        }
    }
}

@Composable private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f)); Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable private fun StatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) { Text(label, Modifier.weight(1f), color = Color(0xFF94A3B8)); Text(value, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun HistoryRow(item: GameHistoryEntity) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF111827)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.game, fontWeight = FontWeight.Bold); Text(item.result, color = Color(0xFF94A3B8), fontSize = 12.sp) }
            Text(if (item.profitLoss >= 0) "+${item.profitLoss}" else item.profitLoss.toString(), color = if (item.profitLoss > 0) MaterialTheme.colorScheme.secondary else Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun SectionHeading(title: String) = Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
