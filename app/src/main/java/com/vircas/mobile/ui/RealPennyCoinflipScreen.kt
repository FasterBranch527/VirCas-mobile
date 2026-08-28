package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import java.security.SecureRandom
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val PennyPanel = Color(0xFF08100E)
private val PennyPanelHi = Color(0xFF101D18)
private val PennyMint = Color(0xFF5BF0AA)
private val PennyRed = Color(0xFFFF5E72)
private val PennyCopper = Color(0xFFD88743)
private val PennyCopperHi = Color(0xFFFFC47A)
private val PennyViolet = Color(0xFFBBA8FF)

private data class RealPennyLockedFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean
)

private data class RealPennyLanding(
    val x: Float = 0f,
    val y: Float = -0.45f,
    val z: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val curve: Float = 0f,
    val swerve: Float = 0f,
    val turns: Int = 8,
    val wobble: Float = 5f
)

private val pennyVisualRandom = SecureRandom()

private fun pennyRandom(min: Float, max: Float): Float =
    min + pennyVisualRandom.nextFloat() * (max - min)

/**
 * Purely visual randomness. It never participates in HEADS/TAILS selection.
 * sqrt(U) gives a uniform distribution over the entire safe tabletop ellipse,
 * not a finite list of authored landing points.
 */
private fun newRealPennyLanding(): RealPennyLanding {
    val angle = pennyVisualRandom.nextFloat() * (PI.toFloat() * 2f)
    val radius = sqrt(pennyVisualRandom.nextFloat())
    return RealPennyLanding(
        x = cos(angle) * radius * 0.82f,
        y = -0.44f + sin(angle) * radius * 0.30f,
        z = pennyRandom(-0.30f, 0.48f),
        pitch = pennyRandom(-4.5f, 4.5f),
        yaw = pennyRandom(-10f, 10f),
        roll = pennyRandom(0f, 360f),
        curve = pennyRandom(-1f, 1f),
        swerve = pennyRandom(-1f, 1f),
        turns = 7 + pennyVisualRandom.nextInt(4),
        wobble = pennyRandom(3.5f, 7.5f)
    )
}

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
    var landing by remember { mutableStateOf(newRealPennyLanding()) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("Pick a side. This uses a real Lincoln penny 3D asset.") }

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
        // Fair result is fixed first. Landing randomness is a separate visual-only stream.
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        landing = newRealPennyLanding()
        pending = RealPennyLockedFlip(wager, result, pick, isSeries)
        revealedSide = null
        lastWon = null
        flipping = true
        flipToken++
    }

    fun startSingle() {
        if (flipping) return
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (started == null) message = "Could not start - check stake and balance."
            else queueFlip(started, false)
        }
    }

    fun startSeriesAndFlip() {
        if (flipping || activeSeries != null) return
        viewModel.beginWager("Coinflip Series", parsedStake()) { started ->
            if (started == null) {
                message = "Could not start - check stake and balance."
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
        viewModel.settleWager(active, seriesMultiplier, "Coinflip series cash out", "Streak $streak")
        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x - streak $streak."
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
    }

    LaunchedEffect(flipToken) {
        val current = pending ?: return@LaunchedEffect
        message = "RESULT LOCKED - real penny in flight..."
        toss.snapTo(0f)
        impact.snapTo(0f)

        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - normalized) + 360f) % 360f
        val targetRotation = rotation.value + landing.turns * 360f + delta

        coroutineScope {
            launch { toss.animateTo(1f, tween(2200, easing = LinearEasing)) }
            launch { rotation.animateTo(targetRotation, tween(2200, easing = LinearOutSlowInEasing)) }
        }

        rotation.snapTo(desired)
        impact.snapTo(1f)
        impact.animateTo(
            0f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )

        revealedSide = current.result.side
        val won = current.result.side == current.picked
        lastWon = won
        if (current.series) {
            if (won) {
                seriesMultiplier *= current.result.outcome.multiplier
                streak++
                message = "${current.result.side.name} - WIN - bank ${"%.2f".format(seriesMultiplier)}x"
            } else {
                viewModel.settleWager(current.wager, 0.0, current.result.side.name, "Series lost after $streak wins")
                activeSeries = null
                seriesMultiplier = 1.0
                streak = 0
                message = "${current.result.side.name} - series lost"
            }
        } else {
            viewModel.settleWager(
                current.wager,
                current.result.outcome.multiplier,
                current.result.side.name,
                "Picked ${current.picked.name}"
            )
            message = if (won) "${current.result.side.name} - WIN - 1.98x" else "${current.result.side.name} - LOSS"
        }
        pending = null
        flipping = false
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "REAL LINCOLN PENNY - FILAMENT 3D",
        balance = balance,
        accent = PennyMint,
        onBack = { if (!flipping) leave() }
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            RealPennyStage(
                modifier = modifier,
                rotationDegrees = rotation.value,
                tossProgress = toss.value,
                impact = impact.value,
                landing = landing,
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
                stage(Modifier.weight(1.22f).fillMaxSize())
                controls(Modifier.weight(.78f))
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
private fun RealPennyStage(
    modifier: Modifier,
    rotationDegrees: Float,
    tossProgress: Float,
    impact: Float,
    landing: RealPennyLanding,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val pennyNode = remember(modelLoader) {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(
                assetFileLocation = "models/penny/scene.gltf"
            ),
            scaleToUnits = 1.12f
        ).apply {
            position = Position(x = 0f, y = -0.30f, z = -0.45f)
        }
    }

    val p = when {
        flipping -> tossProgress.coerceIn(0f, 1f)
        revealedSide != null -> 1f
        else -> 0f
    }
    val split = 0.55f
    val falling: Float
    val x: Float
    val y: Float
    val z: Float
    if (p <= split) {
        val q = (p / split).coerceIn(0f, 1f)
        val e = pennyEaseOutCubic(q)
        falling = 0f
        x = landing.curve * 0.25f * sin(q * PI.toFloat())
        y = pennyLerp(-0.30f, 0.10f, pennySmoothStep(q))
        z = pennyLerp(-0.45f, 1.90f, e)
    } else {
        val q = ((p - split) / (1f - split)).coerceIn(0f, 1f)
        val s = pennySmoothStep(q)
        falling = s
        val startX = landing.curve * 0.25f
        x = pennyLerp(startX, landing.x, s) + sin(q * PI.toFloat()) * landing.swerve * 0.18f
        y = pennyLerp(0.10f, landing.y, q.pow(1.45f))
        z = pennyLerp(1.90f, landing.z, s)
    }

    SideEffect {
        pennyNode.position = Position(
            x = x,
            y = y + impact * 0.035f,
            z = z
        )
        pennyNode.rotation = Rotation(
            x = rotationDegrees + landing.pitch * falling,
            y = landing.yaw * falling + impact * landing.wobble,
            z = landing.roll * falling + sin(p * PI.toFloat()) * 8f
        )
    }

    Box(
        modifier.background(
            Brush.verticalGradient(listOf(PennyPanelHi, PennyPanel, Color(0xFF030705))),
            RoundedCornerShape(28.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * 0.48f
            drawRect(
                Brush.radialGradient(
                    listOf(PennyCopper.copy(alpha = 0.055f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.minDimension * 0.68f
                )
            )
            drawOval(
                Brush.radialGradient(
                    listOf(Color(0xFF173027).copy(alpha = 0.86f), Color(0xFF06100C).copy(alpha = 0.96f))
                ),
                Offset(size.width * 0.045f, size.height * 0.46f),
                androidx.compose.ui.geometry.Size(size.width * 0.91f, size.height * 0.51f)
            )
            repeat(8) { i ->
                val t = i / 7f
                val bottomX = size.width * (0.04f + t * 0.92f)
                val topX = size.width * (0.5f + (t - 0.5f) * 0.15f)
                drawLine(
                    PennyMint.copy(alpha = 0.022f),
                    Offset(topX, horizon),
                    Offset(bottomX, size.height * 0.96f),
                    1f
                )
            }
            repeat(7) { i ->
                val q = (i + 1) / 8f
                val d = q * q
                val yy = horizon + size.height * 0.47f * d
                val half = size.width * (0.06f + 0.43f * d)
                drawLine(
                    Color.White.copy(alpha = 0.022f),
                    Offset(size.width * 0.5f - half, yy),
                    Offset(size.width * 0.5f + half, yy),
                    1f
                )
            }
        }

        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = rememberNodes { add(pennyNode) },
            cameraNode = rememberCameraNode(engine) {
                position = Position(x = 0f, y = 0f, z = 4.0f)
            },
            mainLightNode = rememberMainLightNode(engine) {
                intensity = 110_000f
            },
            cameraManipulator = null,
            isOpaque = false
        )

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (seriesMultiplier != null) {
                Text("DOUBLE OR NOTHING", color = Color.White.copy(alpha = 0.42f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${"%.2f".format(seriesMultiplier)}x", color = PennyMint, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("STREAK $streak", color = Color.White.copy(alpha = 0.36f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            } else {
                Text(
                    when {
                        flipping && tossProgress < split -> "FLYING TOWARD CAMERA"
                        flipping -> "FALLING TO A RANDOM SPOT"
                        else -> "REAL US PENNY - 50 / 50 - 1.98x"
                    },
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp
                )
            }
        }

        Text(
            "Penny (Coin) - Anthony Yanez - CC BY 4.0",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 35.dp),
            color = Color.White.copy(alpha = 0.22f),
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold
        )

        AnimatedVisibility(
            visible = revealedSide != null && !flipping,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            val won = lastWon == true
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (won) PennyMint else PennyRed).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, (if (won) PennyMint else PennyRed).copy(alpha = 0.38f))
            ) {
                Text(
                    "${revealedSide?.name ?: ""} - ${if (won) "WIN" else "LOSS"}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (won) PennyMint else PennyRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
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
            PennySideButton(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = !flipping,
                modifier = Modifier.weight(1f),
                onClick = { onPick(CoinflipEngine.Side.HEADS) }
            )
            PennySideButton(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = !flipping,
                modifier = Modifier.weight(1f),
                onClick = { onPick(CoinflipEngine.Side.TAILS) }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PennyModeButton("SINGLE", !seriesMode && !seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) {
                onSeriesMode(false)
            }
            PennyModeButton("DOUBLE OR NOTHING", seriesMode || seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) {
                onSeriesMode(true)
            }
        }

        QuickStakeRow(enabled = !flipping && !seriesActive, current = stake, onPick = onStake)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumStakeField(
                value = stake,
                enabled = !flipping && !seriesActive,
                accent = PennyCopperHi,
                modifier = Modifier.weight(1f),
                onValueChange = onStake
            )
            PremiumActionButton(
                text = when {
                    flipping -> "IN FLIGHT..."
                    seriesActive -> "FLIP AGAIN"
                    seriesMode -> "START + FLIP"
                    else -> "TOSS - 1.98x"
                },
                accent = PennyMint,
                enabled = !flipping,
                modifier = Modifier.weight(0.82f),
                onClick = onFlip
            )
        }

        if (seriesActive) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = PennyPanelHi,
                border = BorderStroke(1.dp, PennyCopper.copy(alpha = 0.22f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SERIES BANK", fontSize = 7.sp, color = Color.White.copy(alpha = 0.34f), fontWeight = FontWeight.Black)
                        Text("${"%.2f".format(seriesMultiplier)}x", color = PennyCopperHi, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text("STREAK $streak", color = Color.White.copy(alpha = 0.50f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    PremiumActionButton(
                        text = "CASH OUT",
                        accent = PennyCopperHi,
                        enabled = !flipping && streak > 0,
                        onClick = onCashOut
                    )
                }
            }
        }

        PremiumMessageCard(message, PennyMint)
    }
}

@Composable
private fun PennySideButton(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) PennyCopperHi else PennyViolet
    Surface(
        modifier = modifier.height(50.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) accent.copy(alpha = 0.13f) else PennyPanel,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (side == CoinflipEngine.Side.HEADS) "LINCOLN - HEADS" else "ONE CENT - TAILS",
                color = if (selected) accent else Color.White.copy(alpha = 0.66f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text("1.98x payout", color = Color.White.copy(alpha = 0.30f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PennyModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(33.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = 0.085f) else PennyPanel,
        border = BorderStroke(1.dp, if (selected) PennyMint.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White.copy(alpha = 0.84f) else Color.White.copy(alpha = 0.38f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun pennyEaseOutCubic(t: Float): Float = 1f - (1f - t).pow(3f)
private fun pennySmoothStep(t: Float): Float = t * t * (3f - 2f * t)
private fun pennyLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
