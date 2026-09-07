package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import kotlinx.coroutines.Job

private val PhysicsCoinMint = Color(0xFF5BF0AA)
private val PhysicsCoinCopper = Color(0xFFD88A50)
private val PhysicsCoinPanel = Color(0xFF09110F)

private data class PhysicsPennyLockedFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean,
    val motion: RealPennyMotion,
    val payoutMultiplier: Double,
    val streakAfter: Int,
    val settlementResult: String,
    val details: String,
    val checkpoint: Job
)

@Composable
fun PhysicsPennyCoinflipGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by rememberRound(viewModel, "physicsPenny.stake") { mutableStateOf("1000") }
    var pick by rememberRound(viewModel, "physicsPenny.pick") { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by rememberRound(viewModel, "physicsPenny.seriesMode") { mutableStateOf(false) }
    var activeSeries by rememberRound(viewModel, "physicsPenny.activeSeries") { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by rememberRound(viewModel, "physicsPenny.seriesMultiplier") { mutableDoubleStateOf(1.0) }
    var streak by rememberRound(viewModel, "physicsPenny.streak") { mutableIntStateOf(0) }

    var pending by rememberRound(viewModel, "physicsPenny.pending") { mutableStateOf<PhysicsPennyLockedFlip?>(null) }
    var currentMotion by rememberRound(viewModel, "physicsPenny.currentMotion") { mutableStateOf(RealPennyMotion.Idle) }
    var flipToken by rememberRound(viewModel, "physicsPenny.flipToken") { mutableIntStateOf(0) }
    var flipping by rememberRound(viewModel, "physicsPenny.flipping") { mutableStateOf(false) }
    var starting by rememberRound(viewModel, "physicsPenny.starting") { mutableStateOf(false) }
    var revealedSide by rememberRound(viewModel, "physicsPenny.revealedSide") { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by rememberRound(viewModel, "physicsPenny.lastWon") { mutableStateOf<Boolean?>(null) }
    var message by rememberRound(viewModel, "physicsPenny.message") {
        mutableStateOf("Pick a side. The penny lands wherever its random impulse takes it.")
    }

    val toss = remember { Animatable(0f) }

    fun parsedStake(): Long = stake.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun finishFlip(current: PhysicsPennyLockedFlip, cashOut: Boolean = false) {
        if (pending !== current) return
        val won = current.result.side == current.picked
        revealedSide = current.result.side
        lastWon = won

        if (current.series && won && !cashOut) {
            // Apply the locked bank once; a recreated animation must not multiply it again.
            seriesMultiplier = current.payoutMultiplier
            streak = current.streakAfter
            message = "${current.result.side.name} · WIN · bank ${"%.2f".format(seriesMultiplier)}x"
        } else {
            viewModel.settleWager(
                current.wager,
                current.payoutMultiplier,
                current.settlementResult,
                current.details
            )
            activeSeries = null
            seriesMultiplier = 1.0
            streak = 0
            message = when {
                current.series && won ->
                    "Cashed out ${"%.2f".format(current.payoutMultiplier)}x · streak ${current.streakAfter}."
                current.series -> "${current.result.side.name} · series lost"
                won -> "${current.result.side.name} · WIN · 1.98x"
                else -> "${current.result.side.name} · LOSS"
            }
        }
        pending = null
        flipping = false
    }

    fun leave() {
        val current = pending
        if (current != null) {
            // Back consumes the already-known result, including the next series win or loss.
            finishFlip(current, cashOut = true)
        } else {
            activeSeries?.let { active ->
                if (streak > 0) {
                    viewModel.settleWager(
                        active,
                        seriesMultiplier,
                        "Coinflip series cash out",
                        "Streak $streak"
                    )
                    message = "Cashed out ${"%.2f".format(seriesMultiplier)}x · streak $streak."
                } else {
                    // No flip has resolved: only an untouched stake can be refunded.
                    viewModel.cancelWager(active)
                }
            }
        }
        pending = null
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
        flipping = false
        starting = false
        flipToken++ // Invalidate a late begin callback or an old animation continuation.
        onBack()
    }

    BackHandler(onBack = ::leave)

    fun queueFlip(wager: ActiveWager, isSeries: Boolean) {
        if (flipping || starting || pending != null) return

        // Game RNG is resolved first and remains completely separate from visual physics.
        val picked = pick
        val result = CoinflipEngine(viewModel.randomProvider(wager)).flip(picked)
        val won = result.side == picked
        val multiplier = when {
            !isSeries -> result.outcome.multiplier
            won -> seriesMultiplier * result.outcome.multiplier
            else -> 0.0
        }
        val streakAfter = if (isSeries && won) streak + 1 else 0
        val settlementResult = if (isSeries && won) "Coinflip series cash out" else result.side.name
        val details = when {
            !isSeries -> "Picked ${picked.name}"
            won -> "Streak $streakAfter"
            else -> "Series lost after $streak win${if (streak == 1) "" else "s"}"
        }
        val checkpoint = viewModel.checkpointWager(
            wager = wager,
            multiplier = multiplier,
            result = settlementResult,
            details = details,
            terminal = !isSeries || !won
        )
        // The visual simulation samples initial velocity/spin/material properties only. It does
        // not sample a landing coordinate; the final position emerges from integration.
        val motion = randomRealPennyMotion(result.side)
        currentMotion = motion
        pending = PhysicsPennyLockedFlip(
            wager = wager,
            result = result,
            picked = picked,
            series = isSeries,
            motion = motion,
            payoutMultiplier = multiplier,
            streakAfter = streakAfter,
            settlementResult = settlementResult,
            details = details,
            checkpoint = checkpoint
        )
        revealedSide = null
        lastWon = null
        flipping = true
        flipToken++
    }

    fun startSingle() {
        if (flipping || starting || activeSeries != null) return
        starting = true
        val request = ++flipToken
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (flipToken != request) {
                started?.let { viewModel.cancelWager(it) }
                return@beginWager
            }
            starting = false
            if (started == null) message = "Could not start · check stake and balance."
            else queueFlip(started, false)
        }
    }

    fun startSeriesAndFlip() {
        if (flipping || starting || activeSeries != null) return
        starting = true
        val request = ++flipToken
        viewModel.beginWager("Coinflip Series", parsedStake()) { started ->
            if (flipToken != request) {
                started?.let { viewModel.cancelWager(it) }
                return@beginWager
            }
            starting = false
            if (started == null) {
                message = "Could not start · check stake and balance."
            } else {
                activeSeries = started
                seriesMultiplier = 1.0
                streak = 0
                queueFlip(started, true)
            }
        }
    }

    fun cashOutSeries() {
        val active = activeSeries ?: return
        if (flipping || starting || pending != null || streak <= 0) return
        viewModel.settleWager(
            active,
            seriesMultiplier,
            "Coinflip series cash out",
            "Streak $streak"
        )
        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x · streak $streak."
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
    }

    LaunchedEffect(flipToken) {
        val current = pending ?: return@LaunchedEffect
        current.checkpoint.join()
        if (!flipping || pending !== current) return@LaunchedEffect
        message = "RESULT LOCKED · random impulse applied…"
        toss.snapTo(0f)
        toss.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = current.motion.durationMillis,
                easing = LinearEasing
            )
        )

        finishFlip(current)
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "INTERNET LINCOLN PENNY · IMPULSE PHYSICS",
        balance = balance,
        accent = PhysicsCoinCopper,
        onBack = ::leave
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            PhysicsPennyCoinStage(
                modifier = modifier,
                motion = currentMotion,
                tossProgress = toss.value,
                flipping = flipping,
                revealedSide = revealedSide,
                lastWon = lastWon,
                seriesMultiplier = activeSeries?.let { seriesMultiplier },
                streak = streak
            )
        }
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            PhysicsPennyControls(
                modifier = modifier,
                stake = stake,
                onStake = { if (!flipping && !starting && activeSeries == null) stake = it },
                pick = pick,
                onPick = { if (!flipping && !starting) pick = it },
                seriesMode = seriesMode,
                onSeriesMode = { if (!flipping && !starting && activeSeries == null) seriesMode = it },
                flipping = flipping || starting,
                seriesActive = activeSeries != null,
                streak = streak,
                seriesMultiplier = seriesMultiplier,
                message = message,
                onFlip = {
                    val active = activeSeries
                    when {
                        active != null -> queueFlip(active, true)
                        seriesMode -> startSeriesAndFlip()
                        else -> startSingle()
                    }
                },
                onCashOut = ::cashOutSeries
            )
        }

        if (landscape) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stage(Modifier.weight(1.24f).fillMaxSize())
                controls(Modifier.weight(.76f))
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
            ) {
                stage(Modifier.weight(1f).fillMaxWidth())
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PhysicsPennyControls(
    modifier: Modifier,
    stake: String,
    onStake: (String) -> Unit,
    pick: CoinflipEngine.Side,
    onPick: (CoinflipEngine.Side) -> Unit,
    seriesMode: Boolean,
    onSeriesMode: (Boolean) -> Unit,
    flipping: Boolean,
    seriesActive: Boolean,
    streak: Int,
    seriesMultiplier: Double,
    message: String,
    onFlip: () -> Unit,
    onCashOut: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PhysicsPennySideChoice(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.HEADS) },
                modifier = Modifier.weight(1f)
            )
            PhysicsPennySideChoice(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.TAILS) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PhysicsPennyModePill(
                label = "SINGLE",
                selected = !seriesMode && !seriesActive,
                enabled = !flipping && !seriesActive,
                modifier = Modifier.weight(1f),
                onClick = { onSeriesMode(false) }
            )
            PhysicsPennyModePill(
                label = "DOUBLE OR NOTHING",
                selected = seriesMode || seriesActive,
                enabled = !flipping && !seriesActive,
                modifier = Modifier.weight(1f),
                onClick = { onSeriesMode(true) }
            )
        }

        QuickStakeRow(
            enabled = !flipping && !seriesActive,
            current = stake,
            onPick = onStake
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumStakeField(
                value = stake,
                enabled = !flipping && !seriesActive,
                accent = PhysicsCoinCopper,
                modifier = Modifier.weight(1f),
                onValueChange = onStake
            )
            PremiumActionButton(
                text = when {
                    flipping -> "IN FLIGHT…"
                    seriesActive -> "FLIP AGAIN"
                    seriesMode -> "START + FLIP"
                    else -> "TOSS · 1.98x"
                },
                accent = PhysicsCoinMint,
                enabled = !flipping,
                modifier = Modifier.weight(.82f),
                onClick = onFlip
            )
        }

        if (seriesActive) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = PhysicsCoinPanel,
                border = BorderStroke(1.dp, PhysicsCoinCopper.copy(alpha = .26f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "SERIES BANK",
                            fontSize = 7.sp,
                            color = Color.White.copy(alpha = .34f),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "${"%.2f".format(seriesMultiplier)}x",
                            color = PhysicsCoinCopper,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        "STREAK $streak",
                        color = Color.White.copy(alpha = .50f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    PremiumActionButton(
                        text = "CASH OUT",
                        accent = PhysicsCoinCopper,
                        enabled = !flipping && streak > 0,
                        onClick = onCashOut
                    )
                }
            }
        }

        PremiumMessageCard(message, PhysicsCoinCopper)
    }
}

@Composable
private fun PhysicsPennySideChoice(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) PhysicsCoinCopper else Color(0xFFB88768)
    Surface(
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .14f) else PhysicsCoinPanel,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = .62f) else Color.White.copy(alpha = .06f)
        )
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(29.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFC89E), PhysicsCoinCopper, Color(0xFF653018))
                    ),
                    radius = size.minDimension * .48f,
                    center = c
                )
                drawCircle(
                    Color.Black.copy(alpha = .27f),
                    radius = size.minDimension * .40f,
                    center = c,
                    style = Stroke(1.4f)
                )
                val ink = Color(0xFF482313).copy(alpha = .88f)
                if (side == CoinflipEngine.Side.HEADS) {
                    drawCircle(ink, size.minDimension * .13f, Offset(c.x + 1f, c.y - 2f))
                    drawLine(
                        ink,
                        Offset(c.x - 1f, c.y + 1f),
                        Offset(c.x - 4f, c.y + 7f),
                        2.5f,
                        StrokeCap.Round
                    )
                } else {
                    drawLine(ink, Offset(c.x - 6f, c.y - 5f), Offset(c.x + 6f, c.y - 5f), 2f, StrokeCap.Round)
                    drawLine(ink, Offset(c.x - 6f, c.y - 5f), Offset(c.x, c.y + 6f), 2f, StrokeCap.Round)
                    drawLine(ink, Offset(c.x + 6f, c.y - 5f), Offset(c.x, c.y + 6f), 2f, StrokeCap.Round)
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    side.name,
                    color = if (selected) accent else Color.White.copy(alpha = .68f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (side == CoinflipEngine.Side.HEADS) "Lincoln" else "One Cent reverse",
                    color = Color.White.copy(alpha = .30f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PhysicsPennyModePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .085f) else PhysicsCoinPanel,
        border = BorderStroke(
            1.dp,
            if (selected) PhysicsCoinCopper.copy(alpha = .40f) else Color.White.copy(alpha = .05f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = selected, label = "physics-penny-mode") { active ->
                Text(
                    label,
                    color = if (active) Color.White.copy(alpha = .84f) else Color.White.copy(alpha = .38f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
