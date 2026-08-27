package com.vircas.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.BlackjackEngine
import com.vircas.mobile.game.engines.BlackjackRound
import com.vircas.mobile.game.engines.BlackjackStatus
import com.vircas.mobile.game.engines.CasesEngine
import com.vircas.mobile.game.engines.CoinflipEngine
import com.vircas.mobile.game.engines.CrashEngine
import com.vircas.mobile.game.engines.CrashRound
import com.vircas.mobile.game.engines.DiceEngine
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.HiLoEngine
import com.vircas.mobile.game.engines.HiLoGuess
import com.vircas.mobile.game.engines.HiLoRound
import com.vircas.mobile.game.engines.LadderEngine
import com.vircas.mobile.game.engines.MinesEngine
import com.vircas.mobile.game.engines.PathRound
import com.vircas.mobile.game.engines.PlinkoEngine
import com.vircas.mobile.game.engines.PlinkoRisk
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import com.vircas.mobile.game.engines.SlotsEngine
import com.vircas.mobile.game.engines.TowersEngine
import com.vircas.mobile.game.engines.WheelEngine
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
private fun GameShell(title: String, balance: Long, onBack: () -> Unit, content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            BalancePill(balance)
        }
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BalancePill(balance: Long) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF171E2E)) {
        Text("%,d VC".format(balance), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StakeField(stakeText: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(
        value = stakeText,
        onValueChange = { value -> if (value.all(Char::isDigit)) onChange(value.take(9)) },
        enabled = enabled,
        label = { Text("Stake (VC)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun stakeOf(value: String): Long = value.toLongOrNull()?.coerceAtLeast(1L) ?: 0L

private fun outcomePlay(outcome: GameOutcome): ResolvedPlay = when (outcome) {
    is GameOutcome.Win -> ResolvedPlay(outcome.multiplier, outcome.label)
    is GameOutcome.Loss -> ResolvedPlay(0.0, outcome.label)
}

@Composable
fun DiceScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var threshold by remember { mutableFloatStateOf(50f) }
    var under by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("Choose the line and roll") }
    GameShell("Dice", balance, onBack) {
        StakeField(stake, { stake = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = under, onClick = { under = true }, label = { Text("Roll Under") })
            FilterChip(selected = !under, onClick = { under = false }, label = { Text("Roll Over") })
        }
        Text("Target ${threshold.toInt()} · chance ${if (under) threshold.toInt() else 100 - threshold.toInt()}%", fontWeight = FontWeight.Bold)
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 5f..95f)
        Button(onClick = {
            vm.playResolved("Dice", stakeOf(stake), resolver = { random ->
                val rolled = DiceEngine(random).roll(threshold.toDouble(), under)
                val play = outcomePlay(rolled.outcome)
                play.copy(details = "Roll ${"%.2f".format(rolled.roll)} · threshold ${threshold.toInt()}")
            }) { receipt -> result = receipt?.let { "${it.result} · ${"%.2f".format(it.multiplier)}x · payout ${it.payout} VC" } ?: "Insufficient balance" }
        }, modifier = Modifier.fillMaxWidth()) { Text("ROLL") }
        ResultCard(result)
    }
}

@Composable
fun CoinflipScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var result by remember { mutableStateOf("Pick a side") }
    GameShell("Coinflip", balance, onBack) {
        StakeField(stake, { stake = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoinflipEngine.Side.entries.forEach { side ->
                FilterChip(selected = pick == side, onClick = { pick = side }, label = { Text(side.name) })
            }
        }
        Button(onClick = {
            vm.playResolved("Coinflip", stakeOf(stake), resolver = { random -> outcomePlay(CoinflipEngine(random).flip(pick).outcome) }) { receipt ->
                result = receipt?.let { "${it.result} · ${if (it.payout > 0) "WIN" else "LOSS"} · ${it.payout} VC" } ?: "Insufficient balance"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("FLIP") }
        ResultCard(result)
    }
}

@Composable
fun WheelScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var result by remember { mutableStateOf("Spin the multiplier wheel") }
    GameShell("Wheel", balance, onBack) {
        StakeField(stake, { stake = it })
        Text("0x · 0.5x · 1x · 1.5x · 2x · 3x · 5x · 10x · 25x", color = Color(0xFF94A3B8))
        Button(onClick = {
            vm.playResolved("Wheel", stakeOf(stake), resolver = { random -> outcomePlay(WheelEngine(random).spin()) }) { receipt ->
                result = receipt?.let { "Landed ${it.result} · payout ${it.payout} VC" } ?: "Insufficient balance"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("SPIN") }
        ResultCard(result)
    }
}

@Composable
fun RouletteScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var bet by remember { mutableStateOf<RouletteBet>(RouletteBet.Color(RouletteColor.RED)) }
    var result by remember { mutableStateOf("European wheel · 0–36") }
    GameShell("Roulette", balance, onBack) {
        StakeField(stake, { stake = it })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                "RED" to RouletteBet.Color(RouletteColor.RED),
                "BLACK" to RouletteBet.Color(RouletteColor.BLACK),
                "ODD" to RouletteBet.Odd,
                "EVEN" to RouletteBet.Even
            ).forEach { (label, value) ->
                FilterChip(selected = bet == value, onClick = { bet = value }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("1–18" to RouletteBet.Low, "19–36" to RouletteBet.High, "1st 12" to RouletteBet.Dozen(1)).forEach { (label, value) ->
                FilterChip(selected = bet == value, onClick = { bet = value }, label = { Text(label) })
            }
        }
        Button(onClick = {
            vm.playResolved("Roulette", stakeOf(stake), resolver = { random ->
                val spin = RouletteEngine(random).spin(bet)
                ResolvedPlay(spin.payoutMultiplier, "${spin.number} ${spin.color.name}", bet.toString())
            }) { receipt -> result = receipt?.let { "${it.result} · ${if (it.payout > 0) "WIN" else "LOSS"} · payout ${it.payout} VC" } ?: "Insufficient balance" }
        }, modifier = Modifier.fillMaxWidth()) { Text("SPIN WHEEL") }
        ResultCard(result)
    }
}

@Composable
fun SlotsScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var themeIndex by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf("Pick a machine and spin") }
    GameShell("Slots", balance, onBack) {
        StakeField(stake, { stake = it })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SlotsEngine.Themes.forEachIndexed { index, theme -> FilterChip(selected = themeIndex == index, onClick = { themeIndex = index }, label = { Text(theme.title) }) }
        }
        Button(onClick = {
            val theme = SlotsEngine.Themes[themeIndex]
            vm.playResolved("Slots · ${theme.title}", stakeOf(stake), resolver = { random ->
                val spin = SlotsEngine(random).spin(theme)
                val grid = spin.grid.joinToString(" / ") { row -> row.joinToString("-") { it.id } }
                ResolvedPlay(spin.payoutMultiplier, if (spin.payoutMultiplier > 0) "WIN" else "NO WIN", grid)
            }) { receipt -> result = receipt?.let { "${it.result} · ${"%.2f".format(it.multiplier)}x · ${it.payout} VC" } ?: "Insufficient balance" }
        }, modifier = Modifier.fillMaxWidth()) { Text("SPIN") }
        ResultCard(result)
    }
}

@Composable
fun PlinkoScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var risk by remember { mutableStateOf(PlinkoRisk.MEDIUM) }
    var result by remember { mutableStateOf("Drop a ball through 12 rows") }
    GameShell("Plinko", balance, onBack) {
        StakeField(stake, { stake = it })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlinkoRisk.entries.forEach { value -> FilterChip(selected = risk == value, onClick = { risk = value }, label = { Text(value.name) }) }
        }
        Button(onClick = {
            vm.playResolved("Plinko", stakeOf(stake), resolver = { random ->
                val drop = PlinkoEngine(random).drop(risk)
                ResolvedPlay(drop.multiplier, "Bucket ${drop.bucket}", drop.path.joinToString("") { if (it) "R" else "L" })
            }) { receipt -> result = receipt?.let { "${it.result} · ${"%.2f".format(it.multiplier)}x · ${it.payout} VC" } ?: "Insufficient balance" }
        }, modifier = Modifier.fillMaxWidth()) { Text("DROP") }
        ResultCard(result)
    }
}

@Composable
fun CasesScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf("Open a fictional skin cache") }
    GameShell("Cases", balance, onBack) {
        CasesEngine.All.forEachIndexed { index, definition ->
            Surface(shape = RoundedCornerShape(18.dp), color = if (selected == index) Color(0xFF1E293B) else Color(0xFF111827), onClick = { selected = index }) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(definition.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("${definition.cost} VC")
                }
            }
        }
        Button(onClick = {
            val definition = CasesEngine.All[selected]
            vm.openCase(definition) { opening ->
                result = opening?.let { "${it.item.name} · ${it.item.rarity.name} · value ${it.item.marketValue} VC" } ?: "Insufficient balance"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("OPEN CASE") }
        ResultCard(result)
    }
}

@Composable
fun MinesScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var mineCount by remember { mutableIntStateOf(3) }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<MinesEngine?>(null) }
    var round by remember { mutableStateOf<MinesEngine.Round?>(null) }
    var multiplier by remember { mutableDoubleStateOf(1.0) }
    var result by remember { mutableStateOf("Choose mines and start") }
    GameShell("Mines", balance, onBack) {
        StakeField(stake, { stake = it }, enabled = wager == null)
        Text("Mines: $mineCount")
        Slider(value = mineCount.toFloat(), onValueChange = { if (wager == null) mineCount = it.toInt() }, valueRange = 1f..10f, steps = 8)
        if (wager == null) {
            Button(onClick = {
                vm.beginWager("Mines", stakeOf(stake)) { started ->
                    if (started != null) {
                        val created = MinesEngine(vm.randomProvider())
                        engine = created
                        round = created.newRound(mineCount)
                        wager = started
                        multiplier = 1.0
                        result = "Round live"
                    } else result = "Insufficient balance"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START ROUND") }
        } else {
            Button(onClick = {
                val current = wager ?: return@Button
                vm.settleWager(current, multiplier, "Cash out", "${round?.opened?.size ?: 0} safe tiles")
                wager = null; round = null; engine = null; result = "Cashed out ${"%.2f".format(multiplier)}x"
            }, modifier = Modifier.fillMaxWidth()) { Text("CASH OUT ${"%.2f".format(multiplier)}x") }
        }
        val activeRound = round
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(5) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(5) { colIndex ->
                        val index = rowIndex * 5 + colIndex
                        val opened = activeRound?.opened?.contains(index) == true
                        OutlinedButton(
                            enabled = wager != null && !opened,
                            onClick = {
                                val currentEngine = engine ?: return@OutlinedButton
                                val currentRound = round ?: return@OutlinedButton
                                val currentWager = wager ?: return@OutlinedButton
                                val (next, outcome) = currentEngine.reveal(currentRound, index)
                                when (outcome) {
                                    is GameOutcome.Loss -> {
                                        vm.settleWager(currentWager, 0.0, "Mine", "Mines $mineCount")
                                        result = "BOOM · lost ${currentWager.stake} VC"
                                        wager = null; round = null; engine = null; multiplier = 1.0
                                    }
                                    is GameOutcome.Win -> {
                                        round = next
                                        multiplier = outcome.multiplier
                                        result = "Safe · ${"%.2f".format(multiplier)}x"
                                        if (next.opened.size == 25 - mineCount) {
                                            vm.settleWager(currentWager, multiplier, "Board cleared", "Mines $mineCount")
                                            wager = null; round = null; engine = null
                                        }
                                    }
                                    null -> Unit
                                }
                            },
                            modifier = Modifier.size(58.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) { Text(if (opened) "✓" else "?") }
                    }
                }
            }
        }
        ResultCard(result)
    }
}

@Composable
fun BlackjackScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<BlackjackEngine?>(null) }
    var round by remember { mutableStateOf<BlackjackRound?>(null) }
    var result by remember { mutableStateOf("Dealer stands on 17 · Blackjack pays 3:2") }

    fun settleFinished(currentWager: ActiveWager, currentRound: BlackjackRound) {
        vm.settleWager(currentWager, currentRound.payoutMultiplier, currentRound.status.name, "Player ${BlackjackEngine.score(currentRound.player).total} · Dealer ${BlackjackEngine.score(currentRound.dealer).total}")
        result = "${currentRound.status.name} · payout ${"%.2f".format(currentRound.payoutMultiplier)}x"
        wager = null
    }

    GameShell("Blackjack", balance, onBack) {
        StakeField(stake, { stake = it }, enabled = wager == null)
        val current = round
        if (current != null) {
            Text("Dealer", color = Color(0xFF94A3B8)); CardRow(current.dealer)
            Text("Player · ${BlackjackEngine.score(current.player).total}", color = Color(0xFF94A3B8)); CardRow(current.player)
        }
        if (wager == null) {
            Button(onClick = {
                vm.beginWager("Blackjack", stakeOf(stake)) { started ->
                    if (started == null) result = "Insufficient balance" else {
                        val created = BlackjackEngine(vm.randomProvider())
                        val newRound = created.newRound()
                        engine = created; round = newRound; wager = started
                        if (newRound.status != BlackjackStatus.PLAYER_TURN) settleFinished(started, newRound)
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("NEW ROUND") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val e = engine ?: return@Button; val r = round ?: return@Button; val w = wager ?: return@Button
                    val next = e.hit(r); round = next
                    if (next.status != BlackjackStatus.PLAYER_TURN) settleFinished(w, next)
                }, modifier = Modifier.weight(1f)) { Text("HIT") }
                Button(onClick = {
                    val e = engine ?: return@Button; val r = round ?: return@Button; val w = wager ?: return@Button
                    val next = e.stand(r); round = next; settleFinished(w, next)
                }, modifier = Modifier.weight(1f)) { Text("STAND") }
                OutlinedButton(enabled = round?.player?.size == 2, onClick = {
                    val e = engine ?: return@OutlinedButton; val r = round ?: return@OutlinedButton; val w = wager ?: return@OutlinedButton
                    vm.increaseWager(w, w.stake) { doubled ->
                        if (doubled == null) result = "Not enough balance to double" else {
                            wager = doubled
                            val next = e.double(r); round = next; settleFinished(doubled, next)
                        }
                    }
                }, modifier = Modifier.weight(1f)) { Text("DOUBLE") }
            }
        }
        ResultCard(result)
    }
}

@Composable
private fun CardRow(cards: List<PlayingCard>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEach { card ->
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFE2E8F0)) {
                Text("${card.rank.name.take(1)}${card.suit.name.take(1)}", color = Color(0xFF0F172A), modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun HiLoScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<HiLoEngine?>(null) }
    var round by remember { mutableStateOf<HiLoRound?>(null) }
    var result by remember { mutableStateOf("Guess the next card") }
    GameShell("Hi-Lo", balance, onBack) {
        StakeField(stake, { stake = it }, enabled = wager == null)
        round?.let { Text("Current: ${it.current.rank.name} ${it.current.suit.name} · ${"%.2f".format(it.multiplier)}x", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (wager == null) {
            Button(onClick = {
                vm.beginWager("Hi-Lo", stakeOf(stake)) { started ->
                    if (started == null) result = "Insufficient balance" else {
                        val created = HiLoEngine(vm.randomProvider()); engine = created; round = created.newRound(); wager = started
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HiLoGuess.entries.forEach { guess -> Button(onClick = {
                    val e = engine ?: return@Button; val r = round ?: return@Button; val w = wager ?: return@Button
                    val next = e.guess(r, guess)
                    if (!next.won) {
                        vm.settleWager(w, 0.0, "Wrong · ${next.next.rank.name}")
                        result = "Wrong — next was ${next.next.rank.name}"; wager = null; round = null; engine = null
                    } else {
                        round = next.round; result = "Correct · ${"%.2f".format(next.payoutMultiplier)}x"
                    }
                }, modifier = Modifier.weight(1f)) { Text(guess.name) } }
            }
            OutlinedButton(onClick = {
                val w = wager ?: return@OutlinedButton; val r = round ?: return@OutlinedButton
                vm.settleWager(w, r.multiplier, "Cash out", "Streak ${r.streak}")
                result = "Cashed out ${"%.2f".format(r.multiplier)}x"; wager = null; round = null; engine = null
            }, modifier = Modifier.fillMaxWidth()) { Text("CASH OUT") }
        }
        ResultCard(result)
    }
}

@Composable
fun TowersScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) = PathGameScreen(
    vm = vm,
    balance = balance,
    title = "Towers",
    cells = 3,
    onBack = onBack,
    newEngineRound = { random -> TowersEngine(random) to TowersEngine(random).newRound() },
    choose = { engine, round, index -> (engine as TowersEngine).choose(round, index) }
)

@Composable
fun LadderScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) = PathGameScreen(
    vm = vm,
    balance = balance,
    title = "Ladder",
    cells = 4,
    onBack = onBack,
    newEngineRound = { random -> LadderEngine(random) to LadderEngine(random).newRound() },
    choose = { engine, round, index -> (engine as LadderEngine).choose(round, index) }
)

@Composable
private fun PathGameScreen(
    vm: AppViewModel,
    balance: Long,
    title: String,
    cells: Int,
    onBack: () -> Unit,
    newEngineRound: (com.vircas.mobile.core.random.RandomProvider) -> Pair<Any, PathRound>,
    choose: (Any, PathRound, Int) -> com.vircas.mobile.game.engines.PathStep
) {
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<Any?>(null) }
    var round by remember { mutableStateOf<PathRound?>(null) }
    var result by remember { mutableStateOf("Climb and cash out at any safe step") }
    GameShell(title, balance, onBack) {
        StakeField(stake, { stake = it }, enabled = wager == null)
        round?.let { Text("Level ${it.level + 1} · ${"%.2f".format(it.multiplier)}x", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (wager == null) {
            Button(onClick = {
                vm.beginWager(title, stakeOf(stake)) { started ->
                    if (started == null) result = "Insufficient balance" else {
                        val created = newEngineRound(vm.randomProvider()); engine = created.first; round = created.second; wager = started
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(cells) { index ->
                    Button(onClick = {
                        val e = engine ?: return@Button; val r = round ?: return@Button; val w = wager ?: return@Button
                        val step = choose(e, r, index)
                        if (!step.won) {
                            vm.settleWager(w, 0.0, "Failed at level ${r.level + 1}")
                            result = "Failed"; wager = null; round = null; engine = null
                        } else {
                            round = step.round; result = "Safe · ${"%.2f".format(step.payoutMultiplier)}x"
                            if (step.round.finished) {
                                vm.settleWager(w, step.payoutMultiplier, "Completed")
                                wager = null; engine = null
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("${index + 1}") }
                }
            }
            OutlinedButton(onClick = {
                val w = wager ?: return@OutlinedButton; val r = round ?: return@OutlinedButton
                vm.settleWager(w, r.multiplier, "Cash out", "Level ${r.level}")
                result = "Cashed out ${"%.2f".format(r.multiplier)}x"; wager = null; round = null; engine = null
            }, modifier = Modifier.fillMaxWidth()) { Text("CASH OUT") }
        }
        ResultCard(result)
    }
}

@Composable
fun CrashScreen(vm: AppViewModel, balance: Long, onBack: () -> Unit) {
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var crashRound by remember { mutableStateOf<CrashRound?>(null) }
    var multiplier by remember { mutableDoubleStateOf(1.0) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Crash point is generated before the animation") }

    LaunchedEffect(running, crashRound?.crashPoint) {
        val point = crashRound?.crashPoint ?: return@LaunchedEffect
        if (!running) return@LaunchedEffect
        while (running && multiplier < point) {
            delay(45)
            multiplier = min(point, multiplier + 0.02 + multiplier * 0.006)
        }
        if (running && multiplier >= point) {
            val current = wager
            running = false
            wager = null
            result = "CRASHED @ ${"%.2f".format(point)}x"
            if (current != null) vm.settleWager(current, 0.0, result)
        }
    }

    GameShell("Crash", balance, onBack) {
        StakeField(stake, { stake = it }, enabled = !running)
        CrashGraph(multiplier)
        Text("${"%.2f".format(multiplier)}x", fontSize = 40.sp, fontWeight = FontWeight.Black)
        if (!running) {
            Button(onClick = {
                vm.beginWager("Crash", stakeOf(stake)) { started ->
                    if (started == null) result = "Insufficient balance" else {
                        val created = CrashEngine(vm.randomProvider()).newRound()
                        wager = started; crashRound = created; multiplier = 1.0; running = true; result = "LIVE"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START") }
        } else {
            Button(onClick = {
                val current = wager ?: return@Button
                running = false; wager = null
                vm.settleWager(current, multiplier, "Collected @ ${"%.2f".format(multiplier)}x")
                result = "COLLECTED @ ${"%.2f".format(multiplier)}x"
            }, modifier = Modifier.fillMaxWidth()) { Text("COLLECT") }
        }
        ResultCard(result)
    }
}

@Composable
private fun CrashGraph(multiplier: Double) {
    val lineColor = MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF0B1120), modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val progress = ((multiplier - 1.0) / 9.0).coerceIn(0.02, 1.0).toFloat()
            val path = Path().apply {
                moveTo(0f, size.height)
                cubicTo(size.width * .28f, size.height * .96f, size.width * .60f, size.height * .72f, size.width * progress, size.height * (1f - progress * .82f))
            }
            drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f))
            drawCircle(lineColor, radius = 10f, center = Offset(size.width * progress, size.height * (1f - progress * .82f)))
        }
    }
}

@Composable
private fun ResultCard(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFFE2E8F0), fontWeight = FontWeight.SemiBold)
    }
}
