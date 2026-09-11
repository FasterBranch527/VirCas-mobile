package com.vircas.mobile.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.PlinkoEngine
import com.vircas.mobile.game.engines.PlinkoResult
import com.vircas.mobile.game.engines.PlinkoRisk

private const val MaxLivePlinkoBalls = 8

private data class LivePlinkoBall(
    val id: Int,
    val wager: ActiveWager,
    val result: PlinkoResult,
    val risk: PlinkoRisk,
    val physics: PlinkoPhysicsState,
    val trail: List<PlinkoPoint> = emptyList()
)

private data class PlinkoLanding(
    val bucket: Int,
    val multiplier: Double,
    val won: Boolean
)

@Composable
fun PhysicsPlinkoGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var risk by remember { mutableStateOf(PlinkoRisk.MEDIUM) }
    var balls by remember { mutableStateOf<List<LivePlinkoBall>>(emptyList()) }
    var recent by remember { mutableStateOf<List<PlinkoLanding>>(emptyList()) }
    var queuedDrops by remember { mutableIntStateOf(0) }
    var reservationInFlight by remember { mutableStateOf(false) }
    var nextBallId by remember { mutableIntStateOf(1) }
    var leaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Drop one ball or launch a five-ball burst.") }

    val multiplierTable = remember(risk) {
        PlinkoEngine(viewModel.randomProvider()).multipliers(risk)
    }
    val occupiedSlots = balls.size + queuedDrops + if (reservationInFlight) 1 else 0

    fun queueDrops(requested: Int) {
        val available = (MaxLivePlinkoBalls - occupiedSlots).coerceAtLeast(0)
        val accepted = requested.coerceAtMost(available)
        if (accepted == 0) {
            message = "Board is full · wait for a landing."
        } else {
            queuedDrops += accepted
            message = if (accepted == 1) "Preparing ball…" else "Queued $accepted balls…"
        }
    }

    fun leave() {
        if (leaving) return
        leaving = true
        queuedDrops = 0
        val activeBalls = balls
        balls = emptyList()
        activeBalls.forEach { viewModel.cancelWager(it.wager) }
        onBack()
    }

    BackHandler(
        enabled = balls.isNotEmpty() || reservationInFlight || queuedDrops > 0,
        onBack = ::leave
    )

    // Reservations are intentionally serialized. Balls still overlap in flight, while the wallet
    // never receives a burst of competing reservations for the same game in a single frame.
    LaunchedEffect(queuedDrops, reservationInFlight, leaving, risk, stake) {
        if (leaving || reservationInFlight || queuedDrops <= 0) return@LaunchedEffect
        reservationInFlight = true
        val launchRisk = risk
        val launchStake = premiumStake(stake)
        viewModel.beginWager("Plinko", launchStake) { started ->
            reservationInFlight = false
            queuedDrops = (queuedDrops - 1).coerceAtLeast(0)
            when {
                started == null -> {
                    queuedDrops = 0
                    message = "Could not drop: check stake, balance, or local save status."
                }
                leaving -> viewModel.cancelWager(started)
                else -> {
                    val result = PlinkoEngine(viewModel.randomProvider(started)).drop(launchRisk)
                    val id = nextBallId++
                    balls = balls + LivePlinkoBall(
                        id = id,
                        wager = started,
                        result = result,
                        risk = launchRisk,
                        physics = PlinkoPhysics.launch(result.path)
                    )
                    message = "${balls.size} ball${if (balls.size == 1) "" else "s"} in play"
                }
            }
        }
    }

    // One fixed-step frame loop advances every active ball, so eight simultaneous balls cost one
    // Compose effect and remain stable through uneven display frame times.
    LaunchedEffect(balls.isNotEmpty(), leaving) {
        if (leaving || balls.isEmpty()) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (!leaving && balls.isNotEmpty()) {
            val now = withFrameNanos { it }
            val frameSeconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previous = now

            val advanced = balls.map { ball ->
                val state = PlinkoPhysics.step(ball.physics, ball.result.path, frameSeconds)
                val shouldSample = ball.trail.lastOrNull()?.let { previousPoint ->
                    val dx = state.position.x - previousPoint.x
                    val dy = state.position.y - previousPoint.y
                    dx * dx + dy * dy > 0.00018f
                } ?: true
                ball.copy(
                    physics = state,
                    trail = if (shouldSample) (ball.trail + state.position).takeLast(12) else ball.trail
                )
            }
            val landed = advanced.filter { it.physics.landed }
            if (landed.isEmpty()) {
                balls = advanced
            } else {
                val landedIds = landed.mapTo(hashSetOf()) { it.id }
                balls = advanced.filterNot { it.id in landedIds }
                landed.forEach { ball ->
                    viewModel.settleWager(
                        ball.wager,
                        ball.result.multiplier,
                        "Bucket ${ball.result.bucket}",
                        ball.result.path.joinToString("") { if (it) "R" else "L" }
                    )
                    recent = (listOf(
                        PlinkoLanding(
                            bucket = ball.result.bucket,
                            multiplier = ball.result.multiplier,
                            won = ball.result.multiplier >= 1.0
                        )
                    ) + recent).take(5)
                    message = "Bucket ${ball.result.bucket} · ${formatPlinkoMultiplier(ball.result.multiplier)}"
                }
            }
        }
    }

    PremiumGameFrame(
        title = "Plinko",
        subtitle = "12 rows · fixed-step physics · up to 8 live balls",
        balance = balance,
        accent = ShellCyan,
        onBack = ::leave
    ) { compact, landscape ->
        val board: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF080D16),
                border = BorderStroke(1.dp, ShellCyan.copy(alpha = 0.24f)),
                shadowElevation = 12.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(if (compact) 7.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LIVE PHYSICS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = ShellCyan, letterSpacing = 1.sp)
                        Text("${balls.size} / $MaxLivePlinkoBalls BALLS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.46f))
                    }
                    PlinkoPhysicsBoard(
                        balls = balls,
                        multipliers = multiplierTable,
                        selectedRisk = risk,
                        modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 1.12f else 0.94f)
                    )
                }
            }
        }

        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlinkoRisk.entries.forEach { item ->
                        FilterChip(
                            selected = risk == item,
                            enabled = !leaving,
                            onClick = { risk = item },
                            label = { Text(item.name, fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ShellPanel2.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.055f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EDGE ${formatPlinkoMultiplier(multiplierTable.first())}", color = ShellGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("CENTER ${formatPlinkoMultiplier(multiplierTable[6])}", color = Color.White.copy(alpha = 0.44f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("RTP PATH COMMITTED", color = ShellGreen, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
                QuickStakeRow(!leaving, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, !leaving, ShellCyan, Modifier.weight(1f)) { stake = it }
                    PremiumActionButton(
                        text = if (reservationInFlight && queuedDrops == 0) "SAVING…" else "DROP",
                        accent = ShellCyan,
                        enabled = !leaving && occupiedSlots < MaxLivePlinkoBalls,
                        modifier = Modifier.weight(0.72f)
                    ) { queueDrops(1) }
                }
                PremiumActionButton(
                    text = "BURST ×5",
                    accent = ShellPurple,
                    enabled = !leaving && occupiedSlots < MaxLivePlinkoBalls,
                    modifier = Modifier.fillMaxWidth()
                ) { queueDrops(5) }
                if (recent.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        recent.forEach { landing ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (landing.won) ShellGreen.copy(alpha = 0.10f) else ShellRed.copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, if (landing.won) ShellGreen.copy(alpha = 0.25f) else ShellRed.copy(alpha = 0.20f))
                            ) {
                                Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("B${landing.bucket}", fontSize = 7.sp, color = Color.White.copy(alpha = 0.38f), fontWeight = FontWeight.Black)
                                    Text(formatPlinkoMultiplier(landing.multiplier), fontSize = 9.sp, color = if (landing.won) ShellGreen else ShellRed, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                PremiumMessageCard(message, ShellCyan)
            }
        }

        if (landscape) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1.22f).fillMaxHeight(), contentAlignment = Alignment.Center) { board() }
                Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { board() }
                controls()
            }
        }
    }
}

@Composable
private fun PlinkoPhysicsBoard(
    balls: List<LivePlinkoBall>,
    multipliers: List<Double>,
    selectedRisk: PlinkoRisk,
    modifier: Modifier = Modifier
) {
    val riskAccent = plinkoRiskColor(selectedRisk)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sx: (Float) -> Float = { it * w }
        val sy: (Float) -> Float = { it * h }
        val scale = size.minDimension
        val ballRadius = PlinkoPhysics.BallRadius * scale
        val pegRadius = PlinkoPhysics.PegRadius * scale

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0B1624), Color(0xFF070A11))
            ),
            cornerRadius = CornerRadius(18f, 18f)
        )
        drawCircle(ShellCyan.copy(alpha = 0.045f), scale * 0.42f, Offset(w * 0.50f, h * 0.32f))

        for (row in 0 until PlinkoPhysics.Rows) {
            val count = row + 1
            val spacing = 0.068f
            val startX = 0.5f - row * spacing / 2f
            val y = 0.125f + row * 0.059f
            repeat(count) { column ->
                val center = Offset(sx(startX + column * spacing), sy(y))
                drawCircle(Color.Black.copy(alpha = 0.46f), pegRadius * 1.9f, center)
                drawCircle(Color.White.copy(alpha = 0.18f), pegRadius * 1.28f, center)
                drawCircle(Color(0xFFD6E6F5).copy(alpha = 0.92f), pegRadius, center)
                drawCircle(Color.White.copy(alpha = 0.78f), pegRadius * 0.28f, Offset(center.x - pegRadius * 0.25f, center.y - pegRadius * 0.26f))
            }
        }

        val bucketTop = sy(0.887f)
        val floor = sy(0.955f)
        val bucketWidth = 0.068f * w
        repeat(PlinkoPhysics.BucketCount) { bucket ->
            val centerX = sx(PlinkoPhysics.bucketCenter(bucket))
            val edgeDistance = kotlin.math.abs(bucket - 6) / 6f
            val color = when {
                edgeDistance > 0.82f -> ShellGold
                edgeDistance < 0.22f -> ShellRed
                else -> riskAccent
            }
            drawRoundRect(
                color = color.copy(alpha = 0.17f + edgeDistance * 0.12f),
                topLeft = Offset(centerX - bucketWidth * 0.43f, bucketTop),
                size = Size(bucketWidth * 0.86f, floor - bucketTop),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawLine(
                color = color.copy(alpha = 0.68f),
                start = Offset(centerX - bucketWidth * 0.5f, bucketTop),
                end = Offset(centerX - bucketWidth * 0.5f, floor),
                strokeWidth = (scale * 0.0032f).coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }
        drawLine(Color.White.copy(alpha = 0.14f), Offset(w * 0.055f, floor), Offset(w * 0.945f, floor), strokeWidth = 2f)

        val labelPaint = AndroidPaint().apply {
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = (bucketWidth * 0.24f).coerceIn(7f, 16f)
        }
        multipliers.forEachIndexed { bucket, multiplier ->
            val centerX = sx(PlinkoPhysics.bucketCenter(bucket))
            val edgeDistance = kotlin.math.abs(bucket - 6) / 6f
            labelPaint.color = (if (edgeDistance > 0.82f) ShellGold else Color.White.copy(alpha = 0.56f)).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                formatPlinkoMultiplier(multiplier),
                centerX,
                sy(0.982f),
                labelPaint
            )
        }

        balls.forEach { ball ->
            val color = plinkoRiskColor(ball.risk)
            ball.trail.forEachIndexed { index, point ->
                val alpha = (index + 1f) / ball.trail.size.coerceAtLeast(1) * 0.12f
                drawCircle(color.copy(alpha = alpha), ballRadius * (0.35f + index * 0.035f), Offset(sx(point.x), sy(point.y)))
            }
            val center = Offset(sx(ball.physics.position.x), sy(ball.physics.position.y))
            drawCircle(color.copy(alpha = 0.14f), ballRadius * 2.25f, center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, color, color.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.62f)),
                    center = Offset(center.x - ballRadius * 0.35f, center.y - ballRadius * 0.42f),
                    radius = ballRadius * 1.45f
                ),
                radius = ballRadius,
                center = center
            )
            drawCircle(Color.White.copy(alpha = 0.62f), ballRadius * 0.18f, Offset(center.x - ballRadius * 0.33f, center.y - ballRadius * 0.38f))
            drawCircle(Color.Black.copy(alpha = 0.32f), ballRadius, center, style = Stroke(width = (ballRadius * 0.12f).coerceAtLeast(1f)))
        }

        if (balls.isEmpty()) {
            drawCircle(riskAccent.copy(alpha = 0.13f), ballRadius * 2.3f, Offset(w * 0.5f, h * 0.035f))
            drawCircle(riskAccent.copy(alpha = 0.80f), ballRadius, Offset(w * 0.5f, h * 0.035f))
        }
    }
}

private fun plinkoRiskColor(risk: PlinkoRisk): Color = when (risk) {
    PlinkoRisk.LOW -> ShellGreen
    PlinkoRisk.MEDIUM -> ShellCyan
    PlinkoRisk.HIGH -> ShellRed
}

private fun formatPlinkoMultiplier(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()}×" else "${value}×"
