package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.game.engines.BlackjackEngine
import com.vircas.mobile.game.engines.BlackjackRound
import com.vircas.mobile.game.engines.BlackjackStatus
import com.vircas.mobile.game.engines.CrashEngine
import com.vircas.mobile.game.engines.CrashRound
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.HiLoEngine
import com.vircas.mobile.game.engines.HiLoGuess
import com.vircas.mobile.game.engines.HiLoRound
import com.vircas.mobile.game.engines.LadderEngine
import com.vircas.mobile.game.engines.MinesEngine
import com.vircas.mobile.game.engines.PathRound
import com.vircas.mobile.game.engines.PathStep
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.TowersEngine
import kotlinx.coroutines.delay

@Composable
private fun MultiStepShell(
    title: String,
    balance: Long,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Offline virtual round", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("%,d VC".format(balance), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StakeInput(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.all(Char::isDigit)) onValueChange(next.take(9)) },
        enabled = enabled,
        singleLine = true,
        label = { Text("Stake (VC)") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RoundMessage(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.SemiBold)
    }
}

private fun parseStake(value: String): Long = value.toLongOrNull()?.takeIf { it > 0L } ?: 0L

@Composable
fun MinesGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsStateCompat()
    var stake by remember { mutableStateOf("1000") }
    var mineCount by remember { mutableIntStateOf(3) }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<MinesEngine?>(null) }
    var round by remember { mutableStateOf<MinesEngine.Round?>(null) }
    var multiplier by remember { mutableDoubleStateOf(1.0) }
    var message by remember { mutableStateOf("Choose 1–10 mines, then start the round.") }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    MultiStepShell("Mines", balance, ::leave) {
        StakeInput(stake, wager == null) { stake = it }
        Text("Mines: $mineCount", fontWeight = FontWeight.Bold)
        Slider(
            value = mineCount.toFloat(),
            onValueChange = { if (wager == null) mineCount = it.toInt() },
            valueRange = 1f..10f,
            steps = 8
        )

        if (wager == null) {
            Button(
                onClick = {
                    viewModel.beginWager("Mines", parseStake(stake)) { started ->
                        if (started == null) {
                            message = "Could not start: check stake and balance."
                        } else {
                            val createdEngine = MinesEngine(viewModel.randomProvider())
                            wager = started
                            engine = createdEngine
                            round = createdEngine.newRound(mineCount)
                            multiplier = 1.0
                            message = "Round live. Reveal a tile."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("START ROUND") }
        } else {
            Button(
                onClick = {
                    val active = wager ?: return@Button
                    viewModel.settleWager(active, multiplier, "Cash out", "${round?.opened?.size ?: 0} safe tiles")
                    message = "Cashed out at ${"%.2f".format(multiplier)}x."
                    wager = null
                    engine = null
                    round = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("CASH OUT ${"%.2f".format(multiplier)}x") }
        }

        val currentRound = round
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { columnIndex ->
                        val tile = rowIndex * 5 + columnIndex
                        val opened = currentRound?.opened?.contains(tile) == true
                        OutlinedButton(
                            onClick = {
                                val active = wager ?: return@OutlinedButton
                                val current = round ?: return@OutlinedButton
                                val currentEngine = engine ?: return@OutlinedButton
                                val (next, outcome) = currentEngine.reveal(current, tile)
                                when (outcome) {
                                    is GameOutcome.Loss -> {
                                        viewModel.settleWager(active, 0.0, "Mine", "$mineCount mines")
                                        message = "Mine hit. Round lost."
                                        wager = null
                                        engine = null
                                        round = null
                                        multiplier = 1.0
                                    }
                                    is GameOutcome.Win -> {
                                        round = next
                                        multiplier = outcome.multiplier
                                        message = "Safe tile · ${"%.2f".format(multiplier)}x"
                                        if (next.opened.size == 25 - mineCount) {
                                            viewModel.settleWager(active, multiplier, "Board cleared", "$mineCount mines")
                                            message = "Board cleared · ${"%.2f".format(multiplier)}x"
                                            wager = null
                                            engine = null
                                            round = null
                                        }
                                    }
                                    null -> Unit
                                }
                            },
                            enabled = wager != null && !opened,
                            modifier = Modifier.size(58.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(if (opened) "SAFE" else "?") }
                    }
                }
            }
        }
        RoundMessage(message)
    }
}

@Composable
fun CrashGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsStateCompat()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var round by remember { mutableStateOf<CrashRound?>(null) }
    var running by remember { mutableStateOf(false) }
    var multiplier by remember { mutableDoubleStateOf(1.0) }
    var message by remember { mutableStateOf("The crash point is fixed before the round starts.") }

    fun leave() {
        running = false
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    LaunchedEffect(running, round?.crashPoint) {
        val activeRound = round ?: return@LaunchedEffect
        if (!running) return@LaunchedEffect
        while (running && multiplier < activeRound.crashPoint) {
            delay(50)
            multiplier = (multiplier + 0.02 + multiplier * 0.006).coerceAtMost(activeRound.crashPoint)
        }
        if (running && multiplier >= activeRound.crashPoint) {
            val active = wager
            running = false
            wager = null
            message = "CRASH @ ${"%.2f".format(activeRound.crashPoint)}x"
            if (active != null) viewModel.settleWager(active, 0.0, message)
        }
    }

    MultiStepShell("Crash", balance, ::leave) {
        StakeInput(stake, !running) { stake = it }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.2f".format(multiplier)}x", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(if (running) "LIVE" else "READY", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
            }
        }
        if (!running) {
            Button(
                onClick = {
                    viewModel.beginWager("Crash", parseStake(stake)) { started ->
                        if (started == null) {
                            message = "Could not start: check stake and balance."
                        } else {
                            wager = started
                            round = CrashEngine(viewModel.randomProvider()).newRound()
                            multiplier = 1.0
                            running = true
                            message = "Round live. Collect before the crash."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("START") }
        } else {
            Button(
                onClick = {
                    val active = wager ?: return@Button
                    val activeRound = round ?: return@Button
                    val cash = CrashEngine(viewModel.randomProvider()).cashOut(activeRound, multiplier)
                    if (cash.won) {
                        running = false
                        wager = null
                        viewModel.settleWager(active, cash.payoutMultiplier, "Collected @ ${"%.2f".format(multiplier)}x")
                        message = "Collected @ ${"%.2f".format(multiplier)}x"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("COLLECT ${"%.2f".format(multiplier)}x") }
        }
        RoundMessage(message)
    }
}

@Composable
fun BlackjackGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsStateCompat()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<BlackjackEngine?>(null) }
    var round by remember { mutableStateOf<BlackjackRound?>(null) }
    var message by remember { mutableStateOf("Blackjack pays 3:2. Dealer stands on 17.") }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    fun finish(active: ActiveWager, finished: BlackjackRound) {
        val playerScore = BlackjackEngine.score(finished.player).total
        val dealerScore = BlackjackEngine.score(finished.dealer).total
        viewModel.settleWager(active, finished.payoutMultiplier, finished.status.name, "Player $playerScore · Dealer $dealerScore")
        message = "${finished.status.name.replace('_', ' ')} · $playerScore vs $dealerScore"
        wager = null
        engine = null
        round = finished
    }

    MultiStepShell("Blackjack", balance, ::leave) {
        StakeInput(stake, wager == null) { stake = it }
        round?.let { current ->
            Text("Dealer", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
            PlayingCards(current.dealer)
            Text("Player · ${BlackjackEngine.score(current.player).total}", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
            PlayingCards(current.player)
        }

        if (wager == null) {
            Button(
                onClick = {
                    viewModel.beginWager("Blackjack", parseStake(stake)) { started ->
                        if (started == null) {
                            message = "Could not start: check stake and balance."
                        } else {
                            val created = BlackjackEngine(viewModel.randomProvider())
                            val initial = created.newRound()
                            wager = started
                            engine = created
                            round = initial
                            if (initial.status != BlackjackStatus.PLAYER_TURN) finish(started, initial)
                            else message = "Your move: Hit, Stand, or Double."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("NEW ROUND") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val active = wager ?: return@Button
                        val current = round ?: return@Button
                        val currentEngine = engine ?: return@Button
                        val next = currentEngine.hit(current)
                        round = next
                        if (next.status != BlackjackStatus.PLAYER_TURN) finish(active, next)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("HIT") }
                Button(
                    onClick = {
                        val active = wager ?: return@Button
                        val current = round ?: return@Button
                        val currentEngine = engine ?: return@Button
                        finish(active, currentEngine.stand(current))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("STAND") }
                OutlinedButton(
                    onClick = {
                        val active = wager ?: return@OutlinedButton
                        val current = round ?: return@OutlinedButton
                        val currentEngine = engine ?: return@OutlinedButton
                        viewModel.increaseWager(active, active.stake) { doubled ->
                            if (doubled == null) {
                                message = "Not enough balance to Double."
                            } else {
                                wager = doubled
                                finish(doubled, currentEngine.double(current))
                            }
                        }
                    },
                    enabled = round?.player?.size == 2,
                    modifier = Modifier.weight(1f)
                ) { Text("DOUBLE") }
            }
        }
        RoundMessage(message)
    }
}

@Composable
private fun PlayingCards(cards: List<PlayingCard>) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        cards.forEach { card ->
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFE2E8F0)) {
                Text(
                    "${card.rank.name.take(1)}${card.suit.name.take(1)}",
                    Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HiLoGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsStateCompat()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<HiLoEngine?>(null) }
    var round by remember { mutableStateOf<HiLoRound?>(null) }
    var message by remember { mutableStateOf("Guess whether the next card is higher or lower.") }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    MultiStepShell("Hi-Lo", balance, ::leave) {
        StakeInput(stake, wager == null) { stake = it }
        round?.let { current ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.current.rank.name, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(current.current.suit.name, color = Color(0xFF94A3B8))
                    Text("${"%.2f".format(current.multiplier)}x · streak ${current.streak}", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (wager == null) {
            Button(
                onClick = {
                    viewModel.beginWager("Hi-Lo", parseStake(stake)) { started ->
                        if (started == null) message = "Could not start: check stake and balance."
                        else {
                            val created = HiLoEngine(viewModel.randomProvider())
                            wager = started
                            engine = created
                            round = created.newRound()
                            message = "Choose Higher or Lower."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("START") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HiLoGuess.entries.forEach { guess ->
                    Button(
                        onClick = {
                            val active = wager ?: return@Button
                            val current = round ?: return@Button
                            val currentEngine = engine ?: return@Button
                            val next = currentEngine.guess(current, guess)
                            if (!next.won) {
                                viewModel.settleWager(active, 0.0, "Wrong guess", "Next ${next.next.rank.name}")
                                message = "Wrong. Next card: ${next.next.rank.name}."
                                wager = null
                                engine = null
                                round = null
                            } else {
                                round = next.round
                                message = "Correct · ${"%.2f".format(next.payoutMultiplier)}x"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(guess.name) }
                }
            }
            OutlinedButton(
                onClick = {
                    val active = wager ?: return@OutlinedButton
                    val current = round ?: return@OutlinedButton
                    viewModel.settleWager(active, current.multiplier, "Cash out", "Streak ${current.streak}")
                    message = "Cashed out at ${"%.2f".format(current.multiplier)}x."
                    wager = null
                    engine = null
                    round = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("CASH OUT") }
        }
        RoundMessage(message)
    }
}

@Composable
fun TowersGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    PathGameScreen(
        viewModel = viewModel,
        title = "Towers",
        cellCount = 3,
        onBack = onBack,
        newRound = { random -> TowersEngine(random).newRound() },
        choose = { round, cell -> TowersEngine(viewModel.randomProvider()).choose(round, cell) }
    )
}

@Composable
fun LadderGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    PathGameScreen(
        viewModel = viewModel,
        title = "Ladder",
        cellCount = 4,
        onBack = onBack,
        newRound = { random -> LadderEngine(random).newRound() },
        choose = { round, cell -> LadderEngine(viewModel.randomProvider()).choose(round, cell) }
    )
}

@Composable
private fun PathGameScreen(
    viewModel: AppViewModel,
    title: String,
    cellCount: Int,
    onBack: () -> Unit,
    newRound: (RandomProvider) -> PathRound,
    choose: (PathRound, Int) -> PathStep
) {
    val balance by viewModel.balance.collectAsStateCompat()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var round by remember { mutableStateOf<PathRound?>(null) }
    var message by remember { mutableStateOf("Climb safely and cash out whenever you want.") }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    MultiStepShell(title, balance, ::leave) {
        StakeInput(stake, wager == null) { stake = it }
        round?.let { current ->
            Text("Level ${current.level + 1} · ${"%.2f".format(current.multiplier)}x", fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        if (wager == null) {
            Button(
                onClick = {
                    viewModel.beginWager(title, parseStake(stake)) { started ->
                        if (started == null) message = "Could not start: check stake and balance."
                        else {
                            wager = started
                            round = newRound(viewModel.randomProvider())
                            message = "Round live. Pick a tile."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("START") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(cellCount) { cell ->
                    Button(
                        onClick = {
                            val active = wager ?: return@Button
                            val current = round ?: return@Button
                            val step = choose(current, cell)
                            if (!step.won) {
                                viewModel.settleWager(active, 0.0, "Failed", "Level ${current.level + 1}")
                                message = "Failed at level ${current.level + 1}."
                                wager = null
                                round = null
                            } else {
                                round = step.round
                                message = "Safe · ${"%.2f".format(step.payoutMultiplier)}x"
                                if (step.round.finished) {
                                    viewModel.settleWager(active, step.payoutMultiplier, "Completed", "$title completed")
                                    message = "Completed · ${"%.2f".format(step.payoutMultiplier)}x"
                                    wager = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("${cell + 1}") }
                }
            }
            OutlinedButton(
                onClick = {
                    val active = wager ?: return@OutlinedButton
                    val current = round ?: return@OutlinedButton
                    viewModel.settleWager(active, current.multiplier, "Cash out", "Level ${current.level}")
                    message = "Cashed out at ${"%.2f".format(current.multiplier)}x."
                    wager = null
                    round = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("CASH OUT") }
        }
        RoundMessage(message)
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    androidx.compose.runtime.collectAsState(this)
