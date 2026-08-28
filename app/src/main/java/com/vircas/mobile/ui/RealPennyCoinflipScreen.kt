package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import java.security.SecureRandom
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val RealPennyMint = Color(0xFF5BF0AA)
private val RealPennyCopper = Color(0xFFD88A50)
private val RealPennyPanel = Color(0xFF09110F)

private data class RealPennyLockedFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean,
    val visual: RealPennyVisualThrow
)

/** Pure presentation randomness. It never participates in HEADS/TAILS selection. */
private data class RealPennyVisualThrow(
    val landingX: Float,
    val landingDepth: Float,
    val landingYawDeg: Float,
    val landingRollDeg: Float,
    val curve: Float,
    val swerve: Float,
    val turns: Int
)

private val realPennyVisualRandom = SecureRandom()

private fun randomVisualThrow(): RealPennyVisualThrow = RealPennyVisualThrow(
    // Two independent continuous values: every coordinate inside the safe table rectangle is
    // possible. There is no array/list of landing positions and no token-based pattern.
    landingX = realPennyVisualRandom.nextFloat() * 2f - 1f,
    landingDepth = realPennyVisualRandom.nextFloat(),
    landingYawDeg = realPennyVisualRandom.nextFloat() * 360f,
    landingRollDeg = realPennyVisualRandom.nextFloat() * 11f - 5.5f,
    curve = realPennyVisualRandom.nextFloat() * 2f - 1f,
    swerve = realPennyVisualRandom.nextFloat() * 2f - 1f,
    turns = 7 + realPennyVisualRandom.nextInt(5)
)

@Composable
fun RealPennyCoinflipGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }

    var pending by remember { mutableStateOf<RealPennyLockedFlip?>(null) }
    var currentVisual by remember { mutableStateOf(randomVisualThrow()) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember {
        mutableStateOf("Pick a side. Each toss gets a fresh continuous 3D landing coordinate.")
    }

    val toss = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }

    fun parsedStake(): Long = stake.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun leave() {
        val pendingWager = pending?.wager
        if (pendingWager != null) viewModel.cancelWager(pendingWager)
        activeSeries?.takeIf { it != pendingWager }?.let(viewModel::cancelWager)
        pending = null
        activeSeries = null
        onBack()
    }

    BackHandler(enabled = flipping || activeSeries != null) {
        if (!flipping) leave()
    }

    fun queueFlip(wager: ActiveWager, isSeries: Boolean) {
        if (flipping) return

        // Fair game result first.
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        // Completely separate visual stream second.
        val visual = randomVisualThrow()
        currentVisual = visual
        pending = RealPennyLockedFlip(
            wager = wager,
            result = result,
            picked = pick,
            series = isSeries,
            visual = visual
        )
        revealedSide = null
        lastWon = null
        flipping = true
        flipToken++
    }

    fun startSingle() {
        if (flipping) return
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (started == null) {
                message = "Could not start · check stake and balance."
            } else {
                queueFlip(started, false)
            }
        }
    }

    fun startSeriesAndFlip() {
        if (flipping || activeSeries != null) return
        viewModel.beginWager("Coinflip Series", parsedStake()) { started ->
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
        if (flipping || streak <= 0) return
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
        message = "RESULT LOCKED · real penny in flight…"
        toss.snapTo(0f)
        impact.snapTo(0f)

        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - normalized) + 360f) % 360f
        val targetRotation = rotation.value + current.visual.turns * 360f + delta

        coroutineScope {
            launch {
                toss.animateTo(
                    1f,
                    tween(durationMillis = 2180, easing = LinearEasing)
                )
            }
            launch {
                rotation.animateTo(
                    targetRotation,
                    tween(durationMillis = 2180, easing = LinearOutSlowInEasing)
                )
            }
        }

        rotation.snapTo(desired)
        impact.snapTo(1f)
        impact.animateTo(
            0f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )

        revealedSide = current.result.side
        val won = current.result.side == current.picked
        lastWon = won

        if (current.series) {
            if (won) {
                seriesMultiplier *= current.result.outcome.multiplier
                streak++
                message = "${current.result.side.name} · WIN · bank ${"%.2f".format(seriesMultiplier)}x"
            } else {
                viewModel.settleWager(
                    current.wager,
                    0.0,
                    current.result.side.name,
                    "Series lost after $streak win${if (streak == 1) "" else "s"}"
                )
                activeSeries = null
                seriesMultiplier = 1.0
                streak = 0
                message = "${current.result.side.name} · series lost"
            }
        } else {
            viewModel.settleWager(
                current.wager,
                current.result.outcome.multiplier,
                current.result.side.name,
                "Picked ${current.picked.name}"
            )
            message = if (won) {
                "${current.result.side.name} · WIN · 1.98x"
            } else {
                "${current.result.side.name} · LOSS"
            }
        }

        pending = null
        flipping = false
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "REAL LINCOLN PENNY · FILAMENT 3D · FREE LANDING",
        balance = balance,
        accent = RealPennyCopper,
        onBack = { if (!flipping) leave() }
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            RealPennyCoinStage(
                modifier = modifier,
                rotationDegrees = rotation.value,
                tossProgress = toss.value,
                impact = impact.value,
                landingX = currentVisual.landingX,
                landingDepth = currentVisual.landingDepth,
                landingYawDeg = currentVisual.landingYawDeg,
                landingRollDeg = currentVisual.landingRollDeg,
                curve = currentVisual.curve,
                swerve = currentVisual.swerve,
                flipping = flipping,
                revealedSide = revealedSide,
                lastWon = lastWon,
                seriesMultiplier = activeSeries?.let { seriesMultiplier },
                streak = streak
            )
        }
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            RealPennyControls(
                modifier = modifier,
                stake = stake,
                onStake = { stake = it },
                pick = pick,
                onPick = { if (!flipping) pick = it },
                seriesMode = seriesMode,
                onSeriesMode = { if (!flipping && activeSeries == null) seriesMode = it },
                flipping = flipping,
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
private fun RealPennyControls(
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
            RealPennySideChoice(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.HEADS) },
                modifier = Modifier.weight(1f)
            )
            RealPennySideChoice(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.TAILS) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RealPennyModePill(
                label = "SINGLE",
                selected = !seriesMode && !seriesActive,
                enabled = !flipping && !seriesActive,
                modifier = Modifier.weight(1f),
                onClick = { onSeriesMode(false) }
            )
            RealPennyModePill(
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
                accent = RealPennyCopper,
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
                accent = RealPennyMint,
                enabled = !flipping,
                modifier = Modifier.weight(.82f),
                onClick = onFlip
            )
        }

        if (seriesActive) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = RealPennyPanel,
                border = BorderStroke(1.dp, RealPennyCopper.copy(alpha = .26f))
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
                            color = RealPennyCopper,
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
                        accent = RealPennyCopper,
                        enabled = !flipping && streak > 0,
                        onClick = onCashOut
                    )
                }
            }
        }

        PremiumMessageCard(message, RealPennyCopper)
    }
}

@Composable
private fun RealPennySideChoice(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) RealPennyCopper else Color(0xFFB98A6B)
    Surface(
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .14f) else RealPennyPanel,
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
                        listOf(Color(0xFFFFC496), RealPennyCopper, Color(0xFF6B3219))
                    ),
                    radius = size.minDimension * .48f,
                    center = c
                )
                drawCircle(
                    Color.Black.copy(alpha = .28f),
                    radius = size.minDimension * .40f,
                    center = c,
                    style = Stroke(1.4f)
                )
                if (side == CoinflipEngine.Side.HEADS) {
                    drawCircle(
                        Color(0xFF4A2515).copy(alpha = .86f),
                        radius = size.minDimension * .13f,
                        center = Offset(c.x + 1f, c.y - 2f)
                    )
                    drawLine(
                        Color(0xFF4A2515).copy(alpha = .86f),
                        Offset(c.x - 1f, c.y + 1f),
                        Offset(c.x - 4f, c.y + 7f),
                        2.5f,
                        StrokeCap.Round
                    )
                } else {
                    drawLine(
                        Color(0xFF4A2515).copy(alpha = .86f),
                        Offset(c.x - 5f, c.y - 5f),
                        Offset(c.x + 5f, c.y - 5f),
                        2f,
                        StrokeCap.Round
                    )
                    drawLine(
                        Color(0xFF4A2515).copy(alpha = .86f),
                        Offset(c.x - 5f, c.y - 5f),
                        Offset(c.x, c.y + 6f),
                        2f,
                        StrokeCap.Round
                    )
                    drawLine(
                        Color(0xFF4A2515).copy(alpha = .86f),
                        Offset(c.x + 5f, c.y - 5f),
                        Offset(c.x, c.y + 6f),
                        2f,
                        StrokeCap.Round
                    )
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
                    if (side == CoinflipEngine.Side.HEADS) "Lincoln" else "Reverse",
                    color = Color.White.copy(alpha = .30f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RealPennyModePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .085f) else RealPennyPanel,
        border = BorderStroke(
            1.dp,
            if (selected) RealPennyCopper.copy(alpha = .40f) else Color.White.copy(alpha = .05f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = selected, label = "real-penny-mode") { active ->
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
