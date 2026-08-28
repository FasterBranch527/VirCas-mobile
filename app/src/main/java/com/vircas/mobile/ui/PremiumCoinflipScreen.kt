package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CoinBg = Color(0xFF050907)
private val CoinPanel = Color(0xFF0C1411)
private val CoinPanelHi = Color(0xFF111E19)
private val HeadsGold = Color(0xFFFFCA66)
private val HeadsGoldDeep = Color(0xFFC68125)
private val TailsViolet = Color(0xFFB69BFF)
private val TailsVioletDeep = Color(0xFF7250D8)
private val CoinMint = Color(0xFF5BF0AA)
private val CoinRed = Color(0xFFFF5E72)

private data class PendingCoinFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean
)

@Composable
fun PremiumCoinflipGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<PendingCoinFlip?>(null) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("Choose a side. The result is locked before the toss.") }

    val rotation = remember { Animatable(0f) }
    val flight = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }

    fun parsedStake(): Long = stake.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun leave() {
        activeSeries?.let(viewModel::cancelWager)
        activeSeries = null
        onBack()
    }

    BackHandler(enabled = flipping || activeSeries != null) {
        if (!flipping) leave()
    }

    fun queueFlip(wager: ActiveWager, isSeries: Boolean) {
        if (flipping) return
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        pending = PendingCoinFlip(wager, result, pick, isSeries)
        revealedSide = null
        lastWon = null
        flipToken++
    }

    fun startSingleFlip() {
        if (flipping) return
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (started == null) {
                message = "Could not start · check stake and balance."
            } else {
                queueFlip(started, false)
            }
        }
    }

    fun startSeries() {
        if (flipping || activeSeries != null) return
        viewModel.beginWager("Coinflip Series", parsedStake()) { started ->
            if (started == null) {
                message = "Could not start · check stake and balance."
            } else {
                activeSeries = started
                seriesMultiplier = 1.0
                streak = 0
                revealedSide = null
                lastWon = null
                message = "Series live · win a flip, then cash out or push your luck."
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
        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x · $streak win${if (streak == 1) "" else "s"}."
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
    }

    LaunchedEffect(flipToken) {
        val current = pending ?: return@LaunchedEffect
        flipping = true
        message = "RESULT LOCKED · tossing…"
        flight.snapTo(0f)
        landing.snapTo(0f)

        val start = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - start) + 360f) % 360f
        val target = rotation.value + 6f * 360f + delta

        coroutineScope {
            launch {
                rotation.animateTo(target, tween(1950, easing = FastOutSlowInEasing))
            }
            launch {
                flight.animateTo(1f, tween(1950, easing = LinearOutSlowInEasing))
            }
        }
        landing.animateTo(1f, tween(95))
        landing.animateTo(0f, tween(180, easing = LinearOutSlowInEasing))

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

    val angle = rotation.value
    val visibleHeads = cos(angle / 180f * PI).toFloat() >= 0f
    val density = LocalDensity.current
    val jumpPx = with(density) { 155.dp.toPx() }
    val arc = sin((flight.value * PI).toFloat()).coerceAtLeast(0f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07100C), CoinBg, Color.Black)
                )
            )
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        val landscape = maxWidth > maxHeight * 1.16f

        if (!landscape) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CoinflipHeader(balance, onBack = if (activeSeries != null) ::leave else onBack)
                CoinStage(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    visibleHeads = visibleHeads,
                    rotation = angle,
                    translationY = -jumpPx * arc,
                    landing = landing.value,
                    flipping = flipping,
                    revealedSide = revealedSide,
                    lastWon = lastWon,
                    seriesMultiplier = if (activeSeries != null) seriesMultiplier else null,
                    streak = streak
                )
                CoinControls(
                    stake = stake,
                    onStake = { next -> if (next.all(Char::isDigit)) stake = next.take(12) },
                    pick = pick,
                    onPick = { if (!flipping) pick = it },
                    seriesMode = seriesMode,
                    onSeriesMode = { if (activeSeries == null && !flipping) seriesMode = it },
                    enabled = !flipping,
                    stakeEnabled = !flipping && activeSeries == null,
                    seriesActive = activeSeries != null,
                    streak = streak,
                    seriesMultiplier = seriesMultiplier,
                    message = message,
                    onFlip = {
                        if (activeSeries != null) queueFlip(requireNotNull(activeSeries), true)
                        else if (seriesMode) startSeries()
                        else startSingleFlip()
                    },
                    onCashOut = ::cashOutSeries
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CoinflipHeader(balance, onBack = if (activeSeries != null) ::leave else onBack)
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoinStage(
                        modifier = Modifier.weight(1.02f).fillMaxSize(),
                        visibleHeads = visibleHeads,
                        rotation = angle,
                        translationY = -jumpPx * arc * .72f,
                        landing = landing.value,
                        flipping = flipping,
                        revealedSide = revealedSide,
                        lastWon = lastWon,
                        seriesMultiplier = if (activeSeries != null) seriesMultiplier else null,
                        streak = streak
                    )
                    CoinControls(
                        modifier = Modifier.weight(.98f),
                        stake = stake,
                        onStake = { next -> if (next.all(Char::isDigit)) stake = next.take(12) },
                        pick = pick,
                        onPick = { if (!flipping) pick = it },
                        seriesMode = seriesMode,
                        onSeriesMode = { if (activeSeries == null && !flipping) seriesMode = it },
                        enabled = !flipping,
                        stakeEnabled = !flipping && activeSeries == null,
                        seriesActive = activeSeries != null,
                        streak = streak,
                        seriesMultiplier = seriesMultiplier,
                        message = message,
                        onFlip = {
                            if (activeSeries != null) queueFlip(requireNotNull(activeSeries), true)
                            else if (seriesMode) startSeries()
                            else startSingleFlip()
                        },
                        onCashOut = ::cashOutSeries
                    )
                }
            }
        }
    }
}

@Composable
private fun CoinflipHeader(balance: Long, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("COIN FLIP", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = CoinMint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("FAIR · RESULT BEFORE ANIMATION", color = Color.White.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CoinPanelHi,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
                Text("BALANCE", color = CoinMint, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text("%,d VC".format(balance), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CoinStage(
    modifier: Modifier,
    visibleHeads: Boolean,
    rotation: Float,
    translationY: Float,
    landing: Float,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    Box(
        modifier
            .background(
                Brush.verticalGradient(listOf(CoinPanelHi, CoinPanel, Color(0xFF080D0B))),
                RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * .60f)
            val maxR = size.minDimension * .42f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CoinMint.copy(alpha = .055f), Color.Transparent),
                    center = center,
                    radius = maxR
                ),
                center = center,
                radius = maxR
            )
            repeat(3) { ring ->
                drawCircle(
                    color = Color.White.copy(alpha = .018f - ring * .004f),
                    radius = maxR * (.62f + ring * .18f),
                    center = center,
                    style = Stroke(1.2f)
                )
            }
        }

        val shadowAlpha = .30f - .20f * sin((translationY / -500f).coerceIn(0f, 1f) * PI).toFloat()
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(top = 132.dp)
                .width((92 - 28 * (if (flipping) .45f else 0f)).dp)
                .height(13.dp)
                .graphicsLayer {
                    scaleX = 1f + (-translationY / 900f).coerceIn(0f, .34f)
                    alpha = shadowAlpha.coerceIn(.08f, .34f)
                }
                .background(Color.Black, CircleShape)
        )

        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    rotationY = rotation
                    rotationZ = sin((rotation / 720f) * PI).toFloat() * 3.5f
                    this.translationY = translationY
                    scaleX = 1f + landing * .045f
                    scaleY = 1f - landing * .12f
                    shadowElevation = 20f
                    cameraDistance = 18f * density
                },
            contentAlignment = Alignment.Center
        ) {
            CoinFace(
                heads = visibleHeads,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationY = if (visibleHeads) 0f else 180f
                }
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (seriesMultiplier != null) {
                Text("DOUBLE OR NOTHING", color = Color.White.copy(alpha = .44f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${"%.2f".format(seriesMultiplier)}x", color = CoinMint, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("STREAK $streak", color = Color.White.copy(alpha = .40f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(if (flipping) "IN THE AIR" else "50 / 50", color = Color.White.copy(alpha = .42f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }

        AnimatedVisibility(
            visible = revealedSide != null && !flipping,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        ) {
            val win = lastWon == true
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (win) CoinMint else CoinRed).copy(alpha = .10f),
                border = BorderStroke(1.dp, (if (win) CoinMint else CoinRed).copy(alpha = .34f))
            ) {
                Text(
                    "${revealedSide?.name ?: ""} · ${if (win) "WIN" else "LOSS"}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (win) CoinMint else CoinRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun CoinFace(heads: Boolean, modifier: Modifier = Modifier) {
    val light = if (heads) HeadsGold else TailsViolet
    val dark = if (heads) HeadsGoldDeep else TailsVioletDeep
    Box(
        modifier
            .aspectRatio(1f)
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .95f), light, dark),
                    center = Offset(.32f, .25f),
                    radius = 1.05f
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = .24f), r - 3f, c, style = Stroke(5f))
            drawCircle(Color.White.copy(alpha = .32f), r * .80f, c, style = Stroke(2f))
            repeat(20) { index ->
                val a = index / 20.0 * PI * 2.0
                val inner = r * .86f
                val outer = r * .93f
                drawLine(
                    Color.Black.copy(alpha = .16f),
                    Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                    Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (heads) Icons.Rounded.AutoAwesome else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color(0xFF11130F).copy(alpha = .82f),
                modifier = Modifier.size(42.dp)
            )
            Text(
                if (heads) "HEADS" else "TAILS",
                color = Color(0xFF10120F).copy(alpha = .82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun CoinControls(
    stake: String,
    onStake: (String) -> Unit,
    pick: CoinflipEngine.Side,
    onPick: (CoinflipEngine.Side) -> Unit,
    seriesMode: Boolean,
    onSeriesMode: (Boolean) -> Unit,
    enabled: Boolean,
    stakeEnabled: Boolean,
    seriesActive: Boolean,
    streak: Int,
    seriesMultiplier: Double,
    message: String,
    onFlip: () -> Unit,
    onCashOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CoinSideButton(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = enabled,
                onClick = { onPick(CoinflipEngine.Side.HEADS) },
                modifier = Modifier.weight(1f)
            )
            CoinSideButton(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = enabled,
                onClick = { onPick(CoinflipEngine.Side.TAILS) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ModePill("SINGLE", !seriesMode && !seriesActive, stakeEnabled) { onSeriesMode(false) }
            ModePill("DOUBLE OR NOTHING", seriesMode || seriesActive, stakeEnabled) { onSeriesMode(true) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = stake,
                onValueChange = onStake,
                enabled = stakeEnabled,
                label = { Text("Stake VC", fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            listOf(1_000L, 5_000L, 10_000L).forEach { quick ->
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(enabled = stakeEnabled) { onStake(quick.toString()) },
                    shape = RoundedCornerShape(13.dp),
                    color = CoinPanelHi,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))
                ) {
                    Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(if (quick >= 1000) "${quick / 1000}K" else "$quick", color = Color.White.copy(alpha = if (stakeEnabled) .70f else .25f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(13.dp),
            color = CoinPanel,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .055f))
        ) {
            AnimatedContent(targetState = message, label = "coin-message") { text ->
                Text(
                    text,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        if (seriesActive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onFlip,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoinMint, contentColor = Color(0xFF06110C))
                ) {
                    Text(if (enabled) "FLIP AGAIN" else "FLIPPING…", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = onCashOut,
                    enabled = enabled && streak > 0,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("CASH ${"%.2f".format(seriesMultiplier)}x", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        } else {
            Button(
                onClick = onFlip,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoinMint, contentColor = Color(0xFF06110C))
            ) {
                Text(
                    when {
                        !enabled -> "COIN IN THE AIR…"
                        seriesMode -> "START DOUBLE OR NOTHING"
                        else -> "FLIP COIN · 1.98x"
                    },
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun CoinSideButton(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) HeadsGold else TailsViolet
    Surface(
        modifier = modifier.height(50.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .15f) else CoinPanel,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .62f) else Color.White.copy(alpha = .06f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(Modifier.size(25.dp), shape = CircleShape, color = accent) {
                Box(contentAlignment = Alignment.Center) {
                    Text(side.name.take(1), color = Color(0xFF10120F), fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(side.name, color = if (selected) accent else Color.White.copy(alpha = .68f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RowScope.ModePill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f).height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .09f) else CoinPanel,
        border = BorderStroke(1.dp, if (selected) CoinMint.copy(alpha = .34f) else Color.White.copy(alpha = .05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White.copy(alpha = if (selected) .82f else .38f), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}
