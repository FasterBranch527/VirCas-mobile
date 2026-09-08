package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteEngine
import kotlinx.coroutines.delay

@Composable
fun RealisticRouletteGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val motion = remember { mutableStateOf(RouletteMotionFrame()) }

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
        val trajectory = RouletteSpinMotion(
            start = motion.value,
            winningIndex = rouletteWheelOrder.indexOf(round.number),
            pocketCount = rouletteWheelOrder.size,
            variation = spinSerial
        )
        val reduced = !settings.animations || settings.reducedMotion
        status = "NO MORE BETS · wheel and ball in motion"
        if (reduced) {
            // A brief quiet pause, then the exact same final pose. No token full revolutions.
            delay(180)
        } else {
            val startedAt = withFrameNanos { it }
            var elapsed = 0.0
            var previousPhase = RouletteSpinPhase.IDLE
            while (elapsed < RouletteSpinMotion.DURATION_SECONDS && pending?.wager?.id == round.wager.id) {
                elapsed = withFrameNanos { (it - startedAt).coerceAtLeast(0L) / 1_000_000_000.0 }
                val frame = trajectory.sample(elapsed)
                motion.value = frame
                if (frame.phase != previousPhase) {
                    previousPhase = frame.phase
                    status = when (frame.phase) {
                        RouletteSpinPhase.BALL_COAST -> "WHEEL STOPPED · ball still rolling"
                        RouletteSpinPhase.POCKET_BOUNCE -> "BALL IN THE POCKETS"
                        RouletteSpinPhase.BALL_DROP -> "BALL SETTLING"
                        else -> "NO MORE BETS · wheel and ball in motion"
                    }
                }
            }
        }

        if (pending?.wager?.id == round.wager.id) {
            motion.value = trajectory.settled()
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

    fun spin() {
        val frozenBets = bets.mapValues { (_, stack) -> stack.toList() }
        val stake = frozenBets.values.sumOf { it.sum() }
        if (stake <= 0L) {
            status = "Place at least one chip first."
            return
        }
        if (stake > balance) {
            status = "Total chips exceed your virtual balance."
            return
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
                    if (resolved.won) (amount * resolved.payoutMultiplier).toLong() else 0L
                }
                val multiplier = if (payout == 0L) 0.0 else Math.nextUp(payout.toDouble() / stake.toDouble())

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B09), Color(0xFF10251A), Color(0xFF07110C))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        val landscape = maxWidth > maxHeight * 1.15f

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RouletteTopBar(
                balance = balance,
                totalBet = totalBet,
                spinning = spinning,
                onBack = if (spinning) ::settleAndLeave else onBack
            )

            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().weight(0.88f),
                        contentAlignment = Alignment.Center
                    ) {
                        val wheelSize = minOf(maxWidth, maxHeight).coerceAtMost(380.dp)
                        RouletteWheelPanel(motion = motion, result = lastResult, modifier = Modifier.size(wheelSize), compact = true)
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().weight(1.12f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RouletteBettingTable(
                            bets = bets,
                            result = lastResult,
                            enabled = !spinning,
                            onAdd = ::addChip,
                            onRemove = ::removeChip,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true
                        )
                        Spacer(Modifier.weight(1f))
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
                            onSpin = ::spin
                        )
                        RouletteStatusBar(status)
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val wheelSize = minOf(maxWidth * .98f, maxHeight).coerceAtMost(380.dp)
                    RouletteWheelPanel(motion = motion, result = lastResult, modifier = Modifier.size(wheelSize), compact = true)
                }

                RouletteBettingTable(
                    bets = bets,
                    result = lastResult,
                    enabled = !spinning,
                    onAdd = ::addChip,
                    onRemove = ::removeChip,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true
                )

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
                    onSpin = ::spin
                )
                RouletteStatusBar(status)
            }
        }
    }
}

@Composable
private fun RouletteTopBar(balance: Long, totalBet: Long, spinning: Boolean, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color(0xFFF3D8A1))
        }
        Column(Modifier.weight(1f)) {
            Text("EUROPEAN ROULETTE", color = Color(0xFFFFE4AF), fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
            Text(if (spinning) "no more bets · ball in motion" else "0–36 · virtual table", color = Color(0xFF92A99B), fontSize = 8.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("BAL ${formatRouletteVc(balance)}", color = Color(0xFFFFE3AA), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text("BET ${formatRouletteVc(totalBet)}", color = Color(0xFF9CB1A3), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RouletteControls(
    balance: Long, selectedChip: Long, bets: Map<RouletteBet, List<Long>>,
    previousBets: Map<RouletteBet, List<Long>>, spinning: Boolean,
    onChipSelected: (Long) -> Unit, onClear: () -> Unit, onRepeat: () -> Unit, onSpin: () -> Unit
) {
    val totalBet = bets.values.sumOf { it.sum() }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF17120D), border = BorderStroke(1.dp, Color(0xFF6F542D))) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chipSize = when { maxWidth < 300.dp -> 34.dp; maxWidth < 360.dp -> 38.dp; else -> 41.dp }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    rouletteChipValues.forEach { value ->
                        RouletteChipToken(value = value, selected = selectedChip == value, modifier = Modifier.size(chipSize), onClick = { if (!spinning) onChipSelected(value) })
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onClear, enabled = !spinning && bets.isNotEmpty(), modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("CLEAR", fontSize = 9.sp)
                }
                OutlinedButton(onClick = onRepeat, enabled = !spinning && previousBets.isNotEmpty() && previousBets.values.sumOf { it.sum() } <= balance, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("REPEAT", fontSize = 9.sp)
                }
                Button(onClick = onSpin, enabled = !spinning && totalBet > 0L && totalBet <= balance,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D7C3E), contentColor = Color.White), modifier = Modifier.weight(1.25f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(if (spinning) "SPIN…" else "SPIN", fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun RouletteStatusBar(status: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color(0xAA0B1510), border = BorderStroke(1.dp, Color(0xFF2D4B39))) {
        Text(text = status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color(0xFFD8E6DC), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
