package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun RealisticRouletteGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val wheelRotation = remember { Animatable(0f) }
    val ballRotation = remember { Animatable(-72f) }

    var selectedChip by remember { mutableStateOf(100L) }
    var bets by remember { mutableStateOf<Map<RouletteBet, List<Long>>>(emptyMap()) }
    var previousBets by remember { mutableStateOf<Map<RouletteBet, List<Long>>>(emptyMap()) }
    var pending by remember { mutableStateOf<RoulettePendingRound?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var spinSerial by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<Int?>(null) }
    var status by remember {
        mutableStateOf("Tap a cell to place a chip · hold it to remove the last chip")
    }

    val totalBet = bets.values.sumOf { it.sum() }

    fun addChip(bet: RouletteBet) {
        if (spinning) return
        if (totalBet + selectedChip > balance) {
            status = "Not enough virtual balance for another ${formatRouletteVc(selectedChip)} chip."
            return
        }
        val updated = bets.toMutableMap()
        updated[bet] = updated[bet].orEmpty() + selectedChip
        bets = updated
        status = "${rouletteBetName(bet)} · ${formatRouletteVc(updated[bet].orEmpty().sum())}"
    }

    fun removeChip(bet: RouletteBet) {
        if (spinning) return
        val stack = bets[bet].orEmpty()
        if (stack.isEmpty()) return
        val updated = bets.toMutableMap()
        val next = stack.dropLast(1)
        if (next.isEmpty()) updated.remove(bet) else updated[bet] = next
        bets = updated
        status = if (next.isEmpty()) {
            "Removed ${rouletteBetName(bet)}"
        } else {
            "${rouletteBetName(bet)} · ${formatRouletteVc(next.sum())}"
        }
    }

    fun settleAndLeave() {
        val round = pending
        if (round != null) {
            pending = null
            spinning = false
            viewModel.settleWager(
                wager = round.wager,
                multiplier = round.payoutMultiplier,
                result = "${round.number} ${round.color.name}",
                details = rouletteBetSummary(round.bets)
            )
        }
        onBack()
    }

    BackHandler(enabled = spinning, onBack = ::settleAndLeave)

    LaunchedEffect(spinSerial) {
        val round = pending ?: return@LaunchedEffect
        if (!spinning) return@LaunchedEffect

        val index = rouletteWheelOrder.indexOf(round.number).coerceAtLeast(0)
        val sweep = 360f / rouletteWheelOrder.size
        val duration = if (!settings.animations || settings.reducedMotion) 650 else 4_400
        val finalWheel = if (duration < 1_000) 360f else 2_020f + ((round.number * 17) % 130)
        val pocketAngle = -90f + index * sweep + sweep / 2f + (finalWheel % 360f)
        val finalBall = pocketAngle - if (duration < 1_000) 360f else 2_520f

        wheelRotation.snapTo(0f)
        ballRotation.snapTo(-62f)
        coroutineScope {
            launch {
                wheelRotation.animateTo(
                    targetValue = finalWheel,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                )
            }
            launch {
                ballRotation.animateTo(
                    targetValue = finalBall,
                    animationSpec = tween(durationMillis = duration, easing = LinearOutSlowInEasing)
                )
            }
        }

        if (pending?.wager?.id == round.wager.id) {
            lastResult = round.number
            status = if (round.payout > 0L) {
                "${round.number} ${round.color.name} · WIN ${formatRouletteVc(round.payout)}"
            } else {
                "${round.number} ${round.color.name} · no winning chips"
            }
            pending = null
            spinning = false
            viewModel.settleWager(
                wager = round.wager,
                multiplier = round.payoutMultiplier,
                result = "${round.number} ${round.color.name}",
                details = rouletteBetSummary(round.bets)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B09), Color(0xFF10251A), Color(0xFF07110C))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RouletteTopBar(
            balance = balance,
            totalBet = totalBet,
            spinning = spinning,
            onBack = if (spinning) ::settleAndLeave else onBack
        )

        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val availableWidth = maxWidth
            if (availableWidth >= 760.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RouletteWheelPanel(
                        wheelRotation = wheelRotation.value,
                        ballRotation = ballRotation.value,
                        spinning = spinning,
                        result = lastResult,
                        modifier = Modifier.width(330.dp)
                    )
                    RouletteBettingTable(
                        bets = bets,
                        result = lastResult,
                        enabled = !spinning,
                        onAdd = ::addChip,
                        onRemove = ::removeChip,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                val wheelWidth = (availableWidth - 16.dp).coerceAtMost(330.dp).coerceAtLeast(240.dp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RouletteWheelPanel(
                        wheelRotation = wheelRotation.value,
                        ballRotation = ballRotation.value,
                        spinning = spinning,
                        result = lastResult,
                        modifier = Modifier.width(wheelWidth)
                    )
                    RouletteBettingTable(
                        bets = bets,
                        result = lastResult,
                        enabled = !spinning,
                        onAdd = ::addChip,
                        onRemove = ::removeChip,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        RouletteControls(
            balance = balance,
            selectedChip = selectedChip,
            bets = bets,
            previousBets = previousBets,
            spinning = spinning,
            onChipSelected = { selectedChip = it },
            onClear = {
                bets = emptyMap()
                status = "Table cleared."
            },
            onRepeat = {
                val previousTotal = previousBets.values.sumOf { it.sum() }
                if (previousTotal <= balance) {
                    bets = previousBets
                    status = "Repeated ${formatRouletteVc(previousTotal)} layout."
                } else {
                    status = "Previous layout is larger than the current balance."
                }
            },
            onSpin = {
                val frozenBets = bets.mapValues { (_, stack) -> stack.toList() }
                val stake = frozenBets.values.sumOf { it.sum() }
                if (stake <= 0L) {
                    status = "Place at least one chip first."
                    return@RouletteControls
                }
                if (stake > balance) {
                    status = "Total chips exceed your virtual balance."
                    return@RouletteControls
                }

                status = "NO MORE BETS · result locked before animation"
                lastResult = null
                viewModel.beginWager("Roulette", stake) { wager ->
                    if (wager == null) {
                        status = "Could not start the spin: check the virtual balance."
                    } else {
                        val random = viewModel.randomProvider()
                        val engine = RouletteEngine(random)
                        val number = random.nextInt(0, 37)
                        val color = RouletteEngine.colorOf(number)
                        val payout = frozenBets.entries.sumOf { (bet, chips) ->
                            val amount = chips.sum()
                            val resolved = engine.resolve(number, bet)
                            if (resolved.won) {
                                (amount * resolved.payoutMultiplier).toLong()
                            } else {
                                0L
                            }
                        }
                        val multiplier = if (payout == 0L) {
                            0.0
                        } else {
                            Math.nextUp(payout.toDouble() / stake.toDouble())
                        }

                        previousBets = frozenBets
                        pending = RoulettePendingRound(
                            wager = wager,
                            number = number,
                            color = color,
                            payout = payout,
                            payoutMultiplier = multiplier,
                            bets = frozenBets
                        )
                        spinning = true
                        spinSerial++
                    }
                }
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xAA0B1510),
            border = BorderStroke(1.dp, Color(0xFF2D4B39))
        ) {
            Text(
                status,
                Modifier.padding(14.dp),
                color = Color(0xFFD8E6DC),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RouletteTopBar(
    balance: Long,
    totalBet: Long,
    spinning: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color(0xFFF3D8A1))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "EUROPEAN ROULETTE",
                color = Color(0xFFFFE4AF),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Text(
                if (spinning) "no more bets · ball in motion" else "0–36 · local virtual table",
                color = Color(0xFF92A99B),
                fontSize = 11.sp
            )
        }
        RouletteHeaderStat("BALANCE", formatRouletteVc(balance))
        Spacer(Modifier.width(8.dp))
        RouletteHeaderStat("ON TABLE", formatRouletteVc(totalBet))
    }
}

@Composable
private fun RouletteHeaderStat(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151C17),
        border = BorderStroke(1.dp, Color(0xFF35463A))
    ) {
        Column(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(label, color = Color(0xFF829489), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFFFFE3AA), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RouletteControls(
    balance: Long,
    selectedChip: Long,
    bets: Map<RouletteBet, List<Long>>,
    previousBets: Map<RouletteBet, List<Long>>,
    spinning: Boolean,
    onChipSelected: (Long) -> Unit,
    onClear: () -> Unit,
    onRepeat: () -> Unit,
    onSpin: () -> Unit
) {
    val totalBet = bets.values.sumOf { it.sum() }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF17120D),
        border = BorderStroke(1.dp, Color(0xFF6F542D))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CHIP VALUE", color = Color(0xFFBDAA88), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rouletteChipValues.forEach { value ->
                    RouletteChipToken(
                        value = value,
                        selected = selectedChip == value,
                        modifier = Modifier.size(if (selectedChip == value) 58.dp else 52.dp),
                        onClick = { if (!spinning) onChipSelected(value) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = !spinning && bets.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CLEAR")
                }
                OutlinedButton(
                    onClick = onRepeat,
                    enabled = !spinning && previousBets.isNotEmpty() && previousBets.values.sumOf { it.sum() } <= balance,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("REPEAT")
                }
                Button(
                    onClick = onSpin,
                    enabled = !spinning && totalBet > 0L && totalBet <= balance,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2D7C3E),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1.25f)
                ) {
                    Text(if (spinning) "SPINNING…" else "SPIN", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
