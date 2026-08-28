package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CrashEngine
import com.vircas.mobile.game.engines.CrashRound

private enum class CrashScreenPhase { READY, RUNNING, CRASHED }
private val CrashGreen = Color(0xFF5CF2A5)
private val CrashDanger = Color(0xFFFF4F67)

@Composable
fun CinematicCrashGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stakeText by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<CrashEngine?>(null) }
    var round by remember { mutableStateOf<CrashRound?>(null) }
    var phase by remember { mutableStateOf(CrashScreenPhase.READY) }
    var multiplier by remember { mutableDoubleStateOf(1.0) }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    var cashoutAt by remember { mutableStateOf<Double?>(null) }
    var message by remember { mutableStateOf("RESULT IS LOCKED BEFORE THE RUN") }
    var runToken by remember { mutableIntStateOf(0) }
    val crashFall = remember { Animatable(0f) }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        runToken += 1
        onBack()
    }
    BackHandler(onBack = ::leave)

    LaunchedEffect(runToken) {
        if (runToken == 0 || phase != CrashScreenPhase.RUNNING) return@LaunchedEffect
        val token = runToken
        val activeRound = round ?: return@LaunchedEffect
        crashFall.snapTo(0f)
        var startNanos = 0L
        withFrameNanos { startNanos = it }

        while (runToken == token && phase == CrashScreenPhase.RUNNING) {
            val now = withFrameNanos { it }
            elapsedSeconds = ((now - startNanos) / 1_000_000_000.0).toFloat().coerceAtLeast(0f)
            val animated = crashDisplayMultiplier(elapsedSeconds)
            if (animated >= activeRound.crashPoint) {
                multiplier = activeRound.crashPoint
                phase = CrashScreenPhase.CRASHED
                val openWager = wager
                wager = null
                message = if (cashoutAt == null) {
                    "CRASH @ ${"%.2f".format(activeRound.crashPoint)}x"
                } else {
                    "COLLECTED ${"%.2f".format(cashoutAt)}x · CRASH ${"%.2f".format(activeRound.crashPoint)}x"
                }
                if (openWager != null) {
                    viewModel.settleWager(
                        openWager,
                        0.0,
                        "Crash @ ${"%.2f".format(activeRound.crashPoint)}x",
                        "Runner crashed before collect"
                    )
                }
                crashFall.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                break
            } else {
                multiplier = animated
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07100E), Color(0xFF080D0C), Color(0xFF050807))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 680.dp

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            CrashHeader(balance = balance, onBack = ::leave)

            if (landscape) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CrashRunScene(
                        multiplier = multiplier,
                        elapsedSeconds = elapsedSeconds,
                        running = phase == CrashScreenPhase.RUNNING,
                        crashed = phase == CrashScreenPhase.CRASHED,
                        crashProgress = crashFall.value,
                        cashoutAt = cashoutAt,
                        modifier = Modifier.weight(1.55f).fillMaxSize()
                    )
                    CrashControlColumn(
                        phase = phase,
                        stakeText = stakeText,
                        balance = balance,
                        multiplier = multiplier,
                        cashoutAt = cashoutAt,
                        message = message,
                        compact = true,
                        modifier = Modifier.weight(.8f),
                        onStake = { stakeText = it },
                        onStart = start@{
                            val stake = stakeText.toLongOrNull()?.takeIf { it > 0L } ?: return@start
                            if (stake > balance) {
                                message = "NOT ENOUGH VIRTUAL COINS"
                                return@start
                            }
                            message = "RUN STARTING"
                            viewModel.beginWager("Crash", stake) { started ->
                                if (started == null) {
                                    message = "COULD NOT START · CHECK STAKE"
                                } else {
                                    val createdEngine = CrashEngine(viewModel.randomProvider())
                                    wager = started
                                    engine = createdEngine
                                    round = createdEngine.newRound()
                                    multiplier = 1.0
                                    elapsedSeconds = 0f
                                    cashoutAt = null
                                    phase = CrashScreenPhase.RUNNING
                                    message = "RUN LIVE · COLLECT BEFORE THE CRASH"
                                    runToken += 1
                                }
                            }
                        },
                        onCollect = collect@{
                            val active = wager ?: return@collect
                            val activeRound = round ?: return@collect
                            val activeEngine = engine ?: return@collect
                            val result = activeEngine.cashOut(activeRound, multiplier)
                            if (!result.won) return@collect
                            val locked = multiplier
                            wager = null
                            cashoutAt = locked
                            message = "PAYOUT LOCKED @ ${"%.2f".format(locked)}x · RUN CONTINUES"
                            viewModel.settleWager(
                                active,
                                result.payoutMultiplier,
                                "Collected @ ${"%.2f".format(locked)}x",
                                "Crash round continues to predetermined result"
                            )
                        }
                    )
                }
            } else {
                CrashRunScene(
                    multiplier = multiplier,
                    elapsedSeconds = elapsedSeconds,
                    running = phase == CrashScreenPhase.RUNNING,
                    crashed = phase == CrashScreenPhase.CRASHED,
                    crashProgress = crashFall.value,
                    cashoutAt = cashoutAt,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                CrashControlColumn(
                    phase = phase,
                    stakeText = stakeText,
                    balance = balance,
                    multiplier = multiplier,
                    cashoutAt = cashoutAt,
                    message = message,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth(),
                    onStake = { stakeText = it },
                    onStart = start@{
                        val stake = stakeText.toLongOrNull()?.takeIf { it > 0L } ?: return@start
                        if (stake > balance) {
                            message = "NOT ENOUGH VIRTUAL COINS"
                            return@start
                        }
                        message = "RUN STARTING"
                        viewModel.beginWager("Crash", stake) { started ->
                            if (started == null) {
                                message = "COULD NOT START · CHECK STAKE"
                            } else {
                                val createdEngine = CrashEngine(viewModel.randomProvider())
                                wager = started
                                engine = createdEngine
                                round = createdEngine.newRound()
                                multiplier = 1.0
                                elapsedSeconds = 0f
                                cashoutAt = null
                                phase = CrashScreenPhase.RUNNING
                                message = "RUN LIVE · COLLECT BEFORE THE CRASH"
                                runToken += 1
                            }
                        }
                    },
                    onCollect = collect@{
                        val active = wager ?: return@collect
                        val activeRound = round ?: return@collect
                        val activeEngine = engine ?: return@collect
                        val result = activeEngine.cashOut(activeRound, multiplier)
                        if (!result.won) return@collect
                        val locked = multiplier
                        wager = null
                        cashoutAt = locked
                        message = "PAYOUT LOCKED @ ${"%.2f".format(locked)}x · RUN CONTINUES"
                        viewModel.settleWager(
                            active,
                            result.payoutMultiplier,
                            "Collected @ ${"%.2f".format(locked)}x",
                            "Crash round continues to predetermined result"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CrashHeader(balance: Long, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("CRASH RUN", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = CrashGreen, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text("PRECOMPUTED FAIR ROUND", color = Color.White.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = Color.White.copy(alpha = .045f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                Text("BALANCE", color = CrashGreen, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text(formatShellVc(balance), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CrashControlColumn(
    phase: CrashScreenPhase,
    stakeText: String,
    balance: Long,
    multiplier: Double,
    cashoutAt: Double?,
    message: String,
    compact: Boolean,
    modifier: Modifier,
    onStake: (String) -> Unit,
    onStart: () -> Unit,
    onCollect: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = .035f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .055f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    null,
                    tint = when (phase) {
                        CrashScreenPhase.CRASHED -> CrashDanger
                        CrashScreenPhase.RUNNING -> CrashGreen
                        CrashScreenPhase.READY -> ShellGold
                    },
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    message,
                    color = Color.White.copy(alpha = .66f),
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (phase == CrashScreenPhase.RUNNING) {
            if (cashoutAt == null) {
                Button(
                    onClick = onCollect,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrashGreen, contentColor = Color(0xFF04140C)),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("COLLECT ${"%.2f".format(multiplier)}x", fontWeight = FontWeight.Black, fontSize = if (compact) 13.sp else 15.sp)
                        Text(formatShellVc(safeCrashPayout(stakeText.toLongOrNull() ?: 0L, multiplier)), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = CrashGreen.copy(alpha = .10f),
                    border = BorderStroke(1.dp, CrashGreen.copy(alpha = .28f))
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("PAYOUT LOCKED", color = CrashGreen, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${"%.2f".format(cashoutAt)}x · WATCH THE RUN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        } else {
            CrashBetControls(
                stakeText = stakeText,
                balance = balance,
                compact = compact,
                buttonLabel = if (phase == CrashScreenPhase.CRASHED) "RUN AGAIN" else "START RUN",
                onStake = onStake,
                onStart = onStart
            )
        }
    }
}

@Composable
private fun CrashBetControls(
    stakeText: String,
    balance: Long,
    compact: Boolean,
    buttonLabel: String,
    onStake: (String) -> Unit,
    onStart: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE50A1210),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 8.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(500L, 1_000L, 5_000L, 10_000L, 100_000L).forEach { amount ->
                    val selected = stakeText == amount.toString()
                    Surface(
                        onClick = { if (amount <= balance) onStake(amount.toString()) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        color = if (selected) CrashGreen.copy(alpha = .14f) else Color.White.copy(alpha = .035f),
                        border = BorderStroke(1.dp, if (selected) CrashGreen.copy(alpha = .70f) else Color.White.copy(alpha = .06f))
                    ) {
                        Text(
                            crashCompactAmount(amount),
                            Modifier.padding(vertical = if (compact) 5.dp else 7.dp),
                            textAlign = TextAlign.Center,
                            color = if (selected) CrashGreen else Color.White.copy(alpha = .62f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { next -> if (next.all(Char::isDigit)) onStake(next.take(15)) },
                    singleLine = true,
                    label = { Text("Stake VC") },
                    modifier = Modifier.weight(.82f)
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1.18f).height(if (compact) 47.dp else 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrashGreen, contentColor = Color(0xFF04140C))
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun safeCrashPayout(stake: Long, multiplier: Double): Long {
    if (stake <= 0L || multiplier <= 0.0) return 0L
    val value = stake.toDouble() * multiplier
    return if (value >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else value.toLong()
}

private fun crashCompactAmount(value: Long): String = when {
    value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
    value >= 1_000_000L -> "${value / 1_000_000L}M"
    value >= 1_000L -> "${value / 1_000L}K"
    else -> value.toString()
}
