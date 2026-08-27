package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import com.vircas.mobile.game.engines.DiceEngine
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.PlinkoEngine
import com.vircas.mobile.game.engines.PlinkoRisk
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import com.vircas.mobile.game.engines.SlotTheme
import com.vircas.mobile.game.engines.SlotsEngine
import com.vircas.mobile.game.engines.WheelEngine
import kotlinx.coroutines.delay

@Composable
private fun ConfigGameShell(title: String, balance: Long, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("%,d VC".format(balance), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConfigStake(value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.all(Char::isDigit)) onChange(next.take(9)) },
        enabled = enabled,
        label = { Text("Stake (VC)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConfigResult(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.SemiBold)
    }
}

private fun configStake(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 } ?: 0L

@Composable
fun DiceGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var threshold by remember { mutableStateOf(60f) }
    var under by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Choose Roll Under or Roll Over.") }
    val chance = if (under) threshold.toDouble() / 100.0 else (100.0 - threshold) / 100.0
    val potential = 0.99 / chance

    ConfigGameShell("Dice", balance, onBack) {
        ConfigStake(stake) { stake = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = under, onClick = { under = true }, label = { Text("ROLL UNDER") })
            FilterChip(selected = !under, onClick = { under = false }, label = { Text("ROLL OVER") })
        }
        Text("Target ${threshold.toInt()} · chance ${"%.1f".format(chance * 100)}% · ${"%.2f".format(potential)}x", fontWeight = FontWeight.Bold)
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 5f..95f)
        Button(onClick = {
            viewModel.playResolved("Dice", configStake(stake), resolver = { random ->
                val roll = DiceEngine(random).roll(threshold.toDouble(), under)
                ResolvedPlay(roll.outcome.multiplier, "Rolled ${"%.2f".format(roll.roll)}", "${if (under) "Under" else "Over"} ${threshold.toInt()}")
            }) { receipt ->
                message = receipt?.let { "${it.result} · ${if (it.payout > 0) "WIN" else "LOSS"} · payout ${it.payout} VC" } ?: "Could not start: check stake and balance."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("ROLL") }
        ConfigResult(message)
    }
}

@Composable
fun RouletteGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var mode by remember { mutableStateOf("RED") }
    var number by remember { mutableStateOf("7") }
    var dozen by remember { mutableIntStateOf(1) }
    var column by remember { mutableIntStateOf(1) }
    var spinCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("European roulette · 0–36") }
    val rotation by animateFloatAsState(targetValue = spinCount * 1080f, label = "roulette-wheel")

    fun selectedBet(): RouletteBet? = when (mode) {
        "RED" -> RouletteBet.Color(RouletteColor.RED)
        "BLACK" -> RouletteBet.Color(RouletteColor.BLACK)
        "ODD" -> RouletteBet.Odd
        "EVEN" -> RouletteBet.Even
        "LOW" -> RouletteBet.Low
        "HIGH" -> RouletteBet.High
        "NUMBER" -> number.toIntOrNull()?.takeIf { it in 0..36 }?.let(RouletteBet::Number)
        "DOZEN" -> RouletteBet.Dozen(dozen)
        "COLUMN" -> RouletteBet.Column(column)
        else -> null
    }

    ConfigGameShell("Roulette", balance, onBack) {
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally).graphicsLayer(rotationZ = rotation),
            shape = CircleShape,
            color = Color(0xFF162235)
        ) {
            Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("0–36", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("EUROPEAN", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
        }
        ConfigStake(stake) { stake = it }
        listOf("RED", "BLACK", "ODD", "EVEN", "LOW", "HIGH", "NUMBER", "DOZEN", "COLUMN").chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item) }) }
            }
        }
        if (mode == "NUMBER") {
            OutlinedTextField(value = number, onValueChange = { next -> if (next.all(Char::isDigit)) number = next.take(2) }, label = { Text("Number 0–36") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (mode == "DOZEN") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { value -> FilterChip(selected = dozen == value, onClick = { dozen = value }, label = { Text("${value}×12") }) }
            }
        }
        if (mode == "COLUMN") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { value -> FilterChip(selected = column == value, onClick = { column = value }, label = { Text("Column $value") }) }
            }
        }
        Button(onClick = {
            val bet = selectedBet()
            if (bet == null) {
                message = "Invalid roulette selection."
                return@Button
            }
            spinCount++
            viewModel.playResolved("Roulette", configStake(stake), resolver = { random ->
                val result = RouletteEngine(random).spin(bet)
                ResolvedPlay(result.payoutMultiplier, "${result.number} ${result.color.name}", bet.toString())
            }) { receipt ->
                message = receipt?.let { "${it.result} · ${if (it.payout > 0) "WIN" else "LOSS"} · payout ${it.payout} VC" } ?: "Could not start: check stake and balance."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("SPIN") }
        ConfigResult(message)
    }
}

@Composable
fun CoinflipGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var series by remember { mutableStateOf(false) }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }
    var flipCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Pick Heads or Tails.") }
    val rotation by animateFloatAsState(targetValue = flipCount * 720f, label = "coin-flip")

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    fun flipSeries() {
        val active = wager ?: return
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        flipCount++
        if (result.side == pick) {
            seriesMultiplier *= result.outcome.multiplier
            streak++
            message = "${result.side.name} · WIN · ${"%.2f".format(seriesMultiplier)}x"
        } else {
            viewModel.settleWager(active, 0.0, result.side.name, "Series streak $streak")
            wager = null
            seriesMultiplier = 1.0
            streak = 0
            message = "${result.side.name} · series lost"
        }
    }

    ConfigGameShell("Coinflip", balance, if (wager != null) ::leave else onBack) {
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally).graphicsLayer(rotationY = rotation),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) { Text(pick.name.take(1), Modifier.padding(32.dp), fontSize = 36.sp, fontWeight = FontWeight.Black) }
        ConfigStake(stake, wager == null) { stake = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoinflipEngine.Side.entries.forEach { side -> FilterChip(selected = pick == side, onClick = { pick = side }, label = { Text(side.name) }) }
        }
        FilterChip(selected = series, enabled = wager == null, onClick = { series = !series }, label = { Text(if (series) "DOUBLE-OR-NOTHING SERIES" else "SINGLE FLIP") })
        if (!series) {
            Button(onClick = {
                flipCount++
                viewModel.playResolved("Coinflip", configStake(stake), resolver = { random ->
                    val result = CoinflipEngine(random).flip(pick)
                    ResolvedPlay(result.outcome.multiplier, result.side.name, "Picked ${pick.name}")
                }) { receipt -> message = receipt?.let { "${it.result} · ${if (it.payout > 0) "WIN" else "LOSS"} · ${it.payout} VC" } ?: "Could not start." }
            }, modifier = Modifier.fillMaxWidth()) { Text("FLIP") }
        } else if (wager == null) {
            Button(onClick = {
                viewModel.beginWager("Coinflip Series", configStake(stake)) { started ->
                    if (started == null) message = "Could not start: check balance."
                    else {
                        wager = started
                        seriesMultiplier = 1.0
                        streak = 0
                        message = "Series live. Flip or cash out after a win."
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START SERIES") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::flipSeries, modifier = Modifier.weight(1f)) { Text("FLIP") }
                OutlinedButton(
                    onClick = {
                        val active = wager ?: return@OutlinedButton
                        viewModel.settleWager(active, seriesMultiplier, "Series cash out", "Streak $streak")
                        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x"
                        wager = null
                        seriesMultiplier = 1.0
                        streak = 0
                    },
                    enabled = streak > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("CASH OUT") }
            }
        }
        ConfigResult(message)
    }
}

@Composable
fun SlotsGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var themeIndex by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(List(3) { List(3) { "—" } }) }
    var message by remember { mutableStateOf("Choose one of three slot themes.") }
    var spinning by remember { mutableStateOf(false) }
    var autoRemaining by remember { mutableIntStateOf(0) }

    fun spin(theme: SlotTheme) {
        if (spinning) return
        spinning = true
        viewModel.playResolved("Slots · ${theme.title}", configStake(stake), resolver = { random ->
            val result = SlotsEngine(random).spin(theme)
            grid = result.grid.map { row -> row.map { it.id } }
            ResolvedPlay(result.payoutMultiplier, if (result.payoutMultiplier > 0) "WIN" else "NO WIN", "${result.lineWins.size} paylines · ${result.bonusCount} bonus")
        }) { receipt ->
            message = receipt?.let { "${it.result} · ${"%.2f".format(it.multiplier)}x · payout ${it.payout} VC" } ?: "Auto-spin stopped: check balance."
            spinning = false
            if (autoRemaining > 0 && receipt != null) autoRemaining-- else autoRemaining = 0
        }
    }

    LaunchedEffect(autoRemaining, spinning, themeIndex) {
        if (autoRemaining > 0 && !spinning) {
            delay(300)
            spin(SlotsEngine.Themes[themeIndex])
        }
    }

    ConfigGameShell("Slots", balance, onBack) {
        ConfigStake(stake, !spinning && autoRemaining == 0) { stake = it }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SlotsEngine.Themes.forEachIndexed { index, theme ->
                FilterChip(selected = themeIndex == index, enabled = autoRemaining == 0, onClick = { themeIndex = index }, label = { Text(theme.title) })
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grid.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { symbol -> Text(symbol, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 12.sp) }
                    }
                }
            }
        }
        Button(onClick = { spin(SlotsEngine.Themes[themeIndex]) }, enabled = !spinning && autoRemaining == 0, modifier = Modifier.fillMaxWidth()) { Text(if (spinning) "SPINNING" else "SPIN") }
        OutlinedButton(onClick = { autoRemaining = if (autoRemaining > 0) 0 else 10 }, enabled = !spinning, modifier = Modifier.fillMaxWidth()) {
            Text(if (autoRemaining > 0) "STOP AUTO ($autoRemaining)" else "AUTO-SPIN ×10")
        }
        ConfigResult(message)
    }
}

@Composable
fun PlinkoGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var risk by remember { mutableStateOf(PlinkoRisk.MEDIUM) }
    var path by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Choose a risk level, then drop.") }

    ConfigGameShell("Plinko", balance, onBack) {
        ConfigStake(stake) { stake = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlinkoRisk.entries.forEach { value -> FilterChip(selected = risk == value, onClick = { risk = value }, label = { Text(value.name) }) }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("•  •  •  •  •", color = Color(0xFF94A3B8))
                Text(" •  •  •  • ", color = Color(0xFF94A3B8))
                Text("•  •  •  •  •", color = Color(0xFF94A3B8))
                if (path.isNotBlank()) Text(path, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            }
        }
        Button(onClick = {
            viewModel.playResolved("Plinko", configStake(stake), resolver = { random ->
                val result = PlinkoEngine(random).drop(risk)
                path = result.path.joinToString("") { if (it) "R" else "L" }
                ResolvedPlay(result.multiplier, "Bucket ${result.bucket}", path)
            }) { receipt -> message = receipt?.let { "${it.result} · ${"%.2f".format(it.multiplier)}x · payout ${it.payout} VC" } ?: "Could not start." }
        }, modifier = Modifier.fillMaxWidth()) { Text("DROP") }
        ConfigResult(message)
    }
}

@Composable
fun WheelGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var spinCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("0x · 0.5x · 1x · 1.5x · 2x · 3x · 5x · 10x · 25x") }
    val rotation by animateFloatAsState(targetValue = spinCount * 900f, label = "multiplier-wheel")

    ConfigGameShell("Wheel", balance, onBack) {
        ConfigStake(stake) { stake = it }
        Surface(modifier = Modifier.align(Alignment.CenterHorizontally).graphicsLayer(rotationZ = rotation), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Text("×?", Modifier.padding(36.dp), fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Button(onClick = {
            spinCount++
            viewModel.playResolved("Wheel", configStake(stake), resolver = { random ->
                val outcome = WheelEngine(random).spin()
                ResolvedPlay(outcome.multiplier, if (outcome is GameOutcome.Win) outcome.label else "0x")
            }) { receipt -> message = receipt?.let { "Landed ${it.result} · payout ${it.payout} VC" } ?: "Could not start." }
        }, modifier = Modifier.fillMaxWidth()) { Text("SPIN") }
        ConfigResult(message)
    }
}
