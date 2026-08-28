package com.vircas.mobile.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val Coin3dPanel = Color(0xFF0A1210)
private val Coin3dPanelHi = Color(0xFF101D18)
private val Coin3dMint = Color(0xFF5BF0AA)
private val Coin3dRed = Color(0xFFFF5E72)
private val Coin3dGold = Color(0xFFFFD36D)
private val Coin3dGoldMid = Color(0xFFD29A36)
private val Coin3dGoldDark = Color(0xFF795018)
private val Coin3dViolet = Color(0xFFC2ACFF)
private val Coin3dVioletMid = Color(0xFF8165DD)
private val Coin3dVioletDark = Color(0xFF3E2B82)
private val Coin3dEdgeDark = Color(0xFF4B3717)

private data class LockedCoin3dFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean
)

@Composable
fun CinematicCoinflip3DGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<LockedCoin3dFlip?>(null) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("Choose a side. Result locks before the coin leaves the table.") }

    val toss = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }

    fun parsedStake(): Long = stake.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun leave() {
        activeSeries?.let(viewModel::cancelWager)
        activeSeries = null
        pending = null
        onBack()
    }

    BackHandler(enabled = flipping || activeSeries != null) {
        if (!flipping) leave()
    }

    fun queueFlip(wager: ActiveWager, isSeries: Boolean) {
        if (flipping) return
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        pending = LockedCoin3dFlip(wager, result, pick, isSeries)
        revealedSide = null
        lastWon = null
        flipping = true
        flipToken++
    }

    fun startSingle() {
        if (flipping) return
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (started == null) message = "Could not start · check stake and balance."
            else queueFlip(started, false)
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
                message = "Series live · first toss locked."
                queueFlip(started, true)
            }
        }
    }

    fun cashOutSeries() {
        val active = activeSeries ?: return
        if (flipping || streak <= 0) return
        viewModel.settleWager(active, seriesMultiplier, "Coinflip series cash out", "Streak $streak")
        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x · streak $streak."
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
    }

    LaunchedEffect(flipToken) {
        val current = pending ?: return@LaunchedEffect
        message = "RESULT LOCKED · physical toss in progress…"
        toss.snapTo(0f)
        impact.snapTo(0f)

        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - normalized) + 360f) % 360f
        val visualTurns = 8 + (flipToken % 3)
        val targetRotation = rotation.value + visualTurns * 360f + delta

        coroutineScope {
            launch {
                toss.animateTo(1f, tween(2280, easing = LinearEasing))
            }
            launch {
                rotation.animateTo(targetRotation, tween(2280, easing = LinearOutSlowInEasing))
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
            message = if (won) "${current.result.side.name} · WIN · 1.98x" else "${current.result.side.name} · LOSS"
        }

        pending = null
        flipping = false
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "PHYSICAL 3D TOSS · RESULT PRECOMPUTED",
        balance = balance,
        accent = Coin3dMint,
        onBack = if (activeSeries != null) ::leave else onBack
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            PhysicalCoinStage(
                modifier = modifier,
                rotationDegrees = rotation.value,
                tossProgress = toss.value,
                impact = impact.value,
                flipping = flipping,
                revealedSide = revealedSide,
                lastWon = lastWon,
                seriesMultiplier = activeSeries?.let { seriesMultiplier },
                streak = streak
            )
        }

        val controls: @Composable (Modifier) -> Unit = { modifier ->
            Coin3dControls(
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
                stage(Modifier.weight(1.18f).fillMaxSize())
                controls(Modifier.weight(.82f))
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
private fun PhysicalCoinStage(
    modifier: Modifier,
    rotationDegrees: Float,
    tossProgress: Float,
    impact: Float,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Coin3dPanelHi, Coin3dPanel, Color(0xFF050907))
                ),
                RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = tossProgress.coerceIn(0f, 1f)
            val air = if (flipping) (4f * p * (1f - p)).coerceAtLeast(0f) else 0f
            val floorY = h * .75f
            val diameter = size.minDimension * if (h < w * .85f) .41f else .46f
            val jump = h * .42f
            val landingBounce = if (impact > 0f) {
                sin((1f - impact.coerceIn(0f, 1f)) * PI.toFloat()) * impact * diameter * .045f
            } else 0f
            val driftX = if (flipping) {
                sin(p * PI.toFloat()) * w * .035f + sin(p * PI.toFloat() * 2f) * w * .012f
            } else 0f
            val center = Offset(
                w * .5f + driftX,
                floorY - diameter * .54f - jump * air - landingBounce
            )

            drawRect(
                Brush.radialGradient(
                    listOf(Coin3dMint.copy(alpha = .065f), Color.Transparent),
                    center = Offset(w * .5f, h * .48f),
                    radius = size.minDimension * .62f
                )
            )

            repeat(4) { ring ->
                drawCircle(
                    Color.White.copy(alpha = .022f - ring * .0035f),
                    size.minDimension * (.22f + ring * .095f),
                    Offset(w * .5f, h * .50f),
                    style = Stroke(1.1f)
                )
            }

            val horizon = floorY + diameter * .08f
            drawLine(
                Color.White.copy(alpha = .045f),
                Offset(w * .08f, horizon),
                Offset(w * .92f, horizon),
                strokeWidth = 1.2f
            )

            val shadowWidth = diameter * (.72f + air * .34f)
            val shadowHeight = diameter * (.095f + air * .035f)
            val shadowAlpha = (.30f * (1f - air * .72f)).coerceIn(.07f, .30f)
            repeat(5) { layer ->
                val grow = 1f + layer * .13f
                drawOval(
                    Color.Black.copy(alpha = shadowAlpha * (1f - layer * .15f)),
                    topLeft = Offset(w * .5f - shadowWidth * grow / 2f, floorY - shadowHeight * grow / 2f),
                    size = Size(shadowWidth * grow, shadowHeight * grow)
                )
            }

            if (flipping && air > .18f) {
                repeat(3) { trail ->
                    val trailY = center.y + diameter * (.08f + trail * .07f)
                    drawOval(
                        Coin3dGold.copy(alpha = .035f - trail * .008f),
                        Offset(center.x - diameter * .38f, trailY - diameter * .025f),
                        Size(diameter * .76f, diameter * .05f)
                    )
                }
            }

            val landingWobble = if (impact > 0f) {
                sin((1f - impact.coerceIn(0f, 1f)) * PI.toFloat() * 4f) * impact * 4.5f
            } else 0f
            val rollZ = if (flipping) {
                sin(p * PI.toFloat()) * 5f + sin(p * PI.toFloat() * 2f) * 1.8f
            } else 0f

            rotate(rollZ, center) {
                drawPhysicalCoin3d(
                    center = center,
                    diameter = diameter,
                    rotationDegrees = rotationDegrees + landingWobble
                )
            }

            if (!flipping && revealedSide != null && lastWon == true) {
                repeat(10) { i ->
                    val a = i / 10f * PI.toFloat() * 2f
                    val r = diameter * (.64f + (i % 3) * .055f)
                    val sparkle = Offset(center.x + cos(a) * r, center.y + sin(a) * r * .62f)
                    drawCircle(Coin3dMint.copy(alpha = .36f), diameter * .012f, sparkle)
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (seriesMultiplier != null) {
                Text("DOUBLE OR NOTHING", color = Color.White.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${"%.2f".format(seriesMultiplier)}x", color = Coin3dMint, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("STREAK $streak", color = Color.White.copy(alpha = .36f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            } else {
                Text(
                    if (flipping) "COIN IN FLIGHT" else "50 / 50 · 1.98x",
                    color = Color.White.copy(alpha = .44f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
        }

        AnimatedVisibility(
            visible = revealedSide != null && !flipping,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)
        ) {
            val won = lastWon == true
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (won) Coin3dMint else Coin3dRed).copy(alpha = .10f),
                border = BorderStroke(1.dp, (if (won) Coin3dMint else Coin3dRed).copy(alpha = .36f))
            ) {
                Text(
                    "${revealedSide?.name ?: ""} · ${if (won) "WIN" else "LOSS"}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (won) Coin3dMint else Coin3dRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .6.sp
                )
            }
        }
    }
}

private fun DrawScope.drawPhysicalCoin3d(
    center: Offset,
    diameter: Float,
    rotationDegrees: Float
) {
    val rad = rotationDegrees / 180f * PI.toFloat()
    val faceCos = cos(rad)
    val faceFactor = abs(faceCos).coerceAtLeast(.025f)
    val edgeFactor = abs(sin(rad))
    val headsVisible = faceCos >= 0f
    val projectedHeight = max(diameter * faceFactor, diameter * .022f)
    val thickness = diameter * .105f
    val projectedDepth = thickness * (.22f + edgeFactor * .78f)
    val direction = if (sin(rad) >= 0f) 1f else -1f
    val frontCenter = Offset(center.x, center.y - direction * projectedDepth * .48f)
    val backCenter = Offset(center.x, center.y + direction * projectedDepth * .48f)

    val edgeLayers = 14
    repeat(edgeLayers + 1) { layer ->
        val t = layer / edgeLayers.toFloat()
        val y = backCenter.y + (frontCenter.y - backCenter.y) * t
        val lightBand = layer % 3 == 0
        val edgeColor = when {
            lightBand -> Color(0xFFD8A94C).copy(alpha = .90f)
            layer % 2 == 0 -> Color(0xFF9A6B25)
            else -> Coin3dEdgeDark
        }
        drawOval(
            color = edgeColor,
            topLeft = Offset(center.x - diameter / 2f, y - projectedHeight / 2f),
            size = Size(diameter, projectedHeight)
        )
    }

    if (faceFactor < .16f) {
        val edgeH = max(projectedDepth, diameter * .055f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Coin3dEdgeDark,
                    Color(0xFFE0B65A),
                    Color(0xFF9B6A24),
                    Color(0xFFF1C968),
                    Coin3dEdgeDark
                )
            ),
            topLeft = Offset(center.x - diameter * .50f, center.y - edgeH / 2f),
            size = Size(diameter, edgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(edgeH / 2f, edgeH / 2f)
        )
        repeat(22) { ridge ->
            val x = center.x - diameter * .44f + ridge / 21f * diameter * .88f
            drawLine(
                Color.Black.copy(alpha = .28f),
                Offset(x, center.y - edgeH * .34f),
                Offset(x, center.y + edgeH * .34f),
                strokeWidth = max(1f, diameter * .006f),
                cap = StrokeCap.Round
            )
        }
        drawLine(
            Color.White.copy(alpha = .35f),
            Offset(center.x - diameter * .43f, center.y - edgeH * .24f),
            Offset(center.x + diameter * .43f, center.y - edgeH * .24f),
            strokeWidth = max(1f, diameter * .007f),
            cap = StrokeCap.Round
        )
        return
    }

    val light = if (headsVisible) Coin3dGold else Coin3dViolet
    val mid = if (headsVisible) Coin3dGoldMid else Coin3dVioletMid
    val dark = if (headsVisible) Coin3dGoldDark else Coin3dVioletDark
    val faceTopLeft = Offset(frontCenter.x - diameter / 2f, frontCenter.y - projectedHeight / 2f)
    val faceSize = Size(diameter, projectedHeight)

    drawOval(
        brush = Brush.horizontalGradient(
            colors = listOf(dark, mid, light, mid, dark),
            startX = frontCenter.x - diameter / 2f,
            endX = frontCenter.x + diameter / 2f
        ),
        topLeft = faceTopLeft,
        size = faceSize
    )

    drawOval(
        color = Color.Black.copy(alpha = .34f),
        topLeft = Offset(frontCenter.x - diameter * .465f, frontCenter.y - projectedHeight * .465f),
        size = Size(diameter * .93f, projectedHeight * .93f),
        style = Stroke(max(1.6f, diameter * .025f))
    )
    drawOval(
        color = Color.White.copy(alpha = .26f),
        topLeft = Offset(frontCenter.x - diameter * .405f, frontCenter.y - projectedHeight * .405f),
        size = Size(diameter * .81f, projectedHeight * .81f),
        style = Stroke(max(1f, diameter * .010f))
    )

    val grooveRx = diameter * .44f
    val grooveRy = projectedHeight * .44f
    repeat(20) { index ->
        val xNorm = -0.90f + index / 19f * 1.80f
        val x = frontCenter.x + grooveRx * xNorm
        val yFactor = sqrt(max(0f, 1f - xNorm * xNorm))
        val y = grooveRy * yFactor
        val groove = Color.Black.copy(alpha = .15f)
        drawLine(
            groove,
            Offset(x, frontCenter.y - y),
            Offset(x, frontCenter.y - y * .90f),
            strokeWidth = max(1f, diameter * .006f),
            cap = StrokeCap.Round
        )
        drawLine(
            groove,
            Offset(x, frontCenter.y + y),
            Offset(x, frontCenter.y + y * .90f),
            strokeWidth = max(1f, diameter * .006f),
            cap = StrokeCap.Round
        )
    }

    if (headsVisible) {
        val star = Path()
        val outer = diameter * .165f
        val inner = outer * .46f
        repeat(10) { i ->
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val r = if (i % 2 == 0) outer else inner
            val point = Offset(
                frontCenter.x + cos(a) * r,
                frontCenter.y + sin(a) * r * faceFactor
            )
            if (i == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
        }
        star.close()
        drawPath(star, Color(0xFF3A260D).copy(alpha = .78f))
        drawCircle(
            Color.White.copy(alpha = .20f),
            diameter * .045f,
            Offset(frontCenter.x - diameter * .10f, frontCenter.y - projectedHeight * .13f)
        )
    } else {
        val bolt = Path().apply {
            moveTo(frontCenter.x + diameter * .02f, frontCenter.y - diameter * .20f * faceFactor)
            lineTo(frontCenter.x - diameter * .12f, frontCenter.y + diameter * .01f * faceFactor)
            lineTo(frontCenter.x - diameter * .025f, frontCenter.y + diameter * .01f * faceFactor)
            lineTo(frontCenter.x - diameter * .09f, frontCenter.y + diameter * .21f * faceFactor)
            lineTo(frontCenter.x + diameter * .14f, frontCenter.y - diameter * .04f * faceFactor)
            lineTo(frontCenter.x + diameter * .045f, frontCenter.y - diameter * .04f * faceFactor)
            close()
        }
        drawPath(bolt, Color(0xFF241544).copy(alpha = .82f))
        drawCircle(
            Color.White.copy(alpha = .18f),
            diameter * .04f,
            Offset(frontCenter.x - diameter * .11f, frontCenter.y - projectedHeight * .13f)
        )
    }

    drawArc(
        Color.White.copy(alpha = .30f),
        startAngle = 202f,
        sweepAngle = 104f,
        useCenter = false,
        topLeft = Offset(frontCenter.x - diameter * .46f, frontCenter.y - projectedHeight * .46f),
        size = Size(diameter * .92f, projectedHeight * .92f),
        style = Stroke(max(1.2f, diameter * .012f), cap = StrokeCap.Round)
    )

    if (faceFactor > .30f) {
        val native = drawContext.canvas.nativeCanvas
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = diameter * .085f
            color = if (headsVisible) android.graphics.Color.rgb(58, 38, 13) else android.graphics.Color.rgb(36, 21, 68)
            alpha = 205
            letterSpacing = .08f
        }
        native.save()
        native.scale(1f, faceFactor, frontCenter.x, frontCenter.y)
        native.drawText(
            if (headsVisible) "HEADS" else "TAILS",
            frontCenter.x,
            frontCenter.y + diameter * .30f,
            paint
        )
        native.restore()
    }
}

@Composable
private fun Coin3dControls(
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
            Coin3dSideChoice(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.HEADS) },
                modifier = Modifier.weight(1f)
            )
            Coin3dSideChoice(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.TAILS) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Coin3dModePill(
                label = "SINGLE",
                selected = !seriesMode && !seriesActive,
                enabled = !flipping && !seriesActive,
                modifier = Modifier.weight(1f)
            ) { onSeriesMode(false) }
            Coin3dModePill(
                label = "DOUBLE OR NOTHING",
                selected = seriesMode || seriesActive,
                enabled = !flipping && !seriesActive,
                modifier = Modifier.weight(1f)
            ) { onSeriesMode(true) }
        }

        QuickStakeRow(enabled = !flipping && !seriesActive, current = stake, onPick = onStake)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumStakeField(
                value = stake,
                enabled = !flipping && !seriesActive,
                accent = Coin3dMint,
                modifier = Modifier.weight(1f),
                onValueChange = onStake
            )
            if (!seriesActive) {
                PremiumActionButton(
                    text = when {
                        flipping -> "IN AIR…"
                        seriesMode -> "START + FLIP"
                        else -> "TOSS · 1.98x"
                    },
                    accent = Coin3dMint,
                    enabled = !flipping,
                    modifier = Modifier.weight(.80f),
                    onClick = onFlip
                )
            } else {
                PremiumActionButton(
                    text = if (flipping) "IN AIR…" else "FLIP AGAIN",
                    accent = Coin3dMint,
                    enabled = !flipping,
                    modifier = Modifier.weight(.80f),
                    onClick = onFlip
                )
            }
        }

        if (seriesActive) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = Coin3dPanelHi,
                border = BorderStroke(1.dp, Coin3dGold.copy(alpha = .18f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SERIES BANK", fontSize = 7.sp, color = Color.White.copy(alpha = .34f), fontWeight = FontWeight.Black)
                        Text("${"%.2f".format(seriesMultiplier)}x", color = Coin3dGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text("STREAK $streak", color = Color.White.copy(alpha = .50f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    PremiumActionButton(
                        text = "CASH OUT",
                        accent = Coin3dGold,
                        enabled = !flipping && streak > 0,
                        onClick = onCashOut
                    )
                }
            }
        }

        PremiumMessageCard(message, Coin3dMint)
    }
}

@Composable
private fun Coin3dSideChoice(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) Coin3dGold else Coin3dViolet
    Surface(
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .13f) else Coin3dPanel,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .58f) else Color.White.copy(alpha = .06f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(29.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = .72f), accent, accent.copy(alpha = .50f))),
                    radius = size.minDimension * .48f,
                    center = c
                )
                drawCircle(Color.Black.copy(alpha = .25f), size.minDimension * .40f, c, style = Stroke(1.5f))
                if (side == CoinflipEngine.Side.HEADS) {
                    drawCircle(Color(0xFF2B1C08).copy(alpha = .78f), size.minDimension * .12f, c)
                } else {
                    drawLine(Color(0xFF241544), Offset(c.x - 4f, c.y + 5f), Offset(c.x + 4f, c.y - 5f), 3f, StrokeCap.Round)
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(side.name, color = if (selected) accent else Color.White.copy(alpha = .68f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("1.98x payout", color = Color.White.copy(alpha = .30f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Coin3dModePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .085f) else Coin3dPanel,
        border = BorderStroke(1.dp, if (selected) Coin3dMint.copy(alpha = .34f) else Color.White.copy(alpha = .05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = selected, label = "coin3d-mode") { active ->
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
