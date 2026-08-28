package com.vircas.mobile.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRaceResult
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.SlotSpinResult
import com.vircas.mobile.game.engines.SlotSymbol
import com.vircas.mobile.game.engines.SlotsEngine
import com.vircas.mobile.game.engines.WheelEngine
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UltraWheelGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val sectors = remember { WheelEngine(viewModel.randomProvider()).sectors }
    var stake by remember { mutableStateOf("1000") }
    var active by remember { mutableStateOf<ActiveWager?>(null) }
    var pending by remember { mutableStateOf<GameOutcome?>(null) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Spin the multiplier wheel.") }
    var landedLabel by remember { mutableStateOf("×?") }
    val rotation = remember { Animatable(0f) }
    val pointerKick = remember { Animatable(0f) }

    fun outcomeName(outcome: GameOutcome): String = if (outcome is GameOutcome.Win) outcome.label else "0x"

    fun leave() {
        val wager = active
        val outcome = pending
        active = null
        pending = null
        if (wager != null && outcome != null) viewModel.settleWager(wager, outcome.multiplier, outcomeName(outcome))
        else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::leave)

    LaunchedEffect(trigger) {
        val outcome = pending ?: return@LaunchedEffect
        val sectorAngle = 360f / sectors.size
        val current = rotation.value
        val normalized = ((current % 360f) + 360f) % 360f
        val desired = ((-targetIndex * sectorAngle) % 360f + 360f) % 360f
        var delta = desired - normalized
        if (delta < 0f) delta += 360f
        pointerKick.snapTo(0f)
        rotation.animateTo(current + 7f * 360f + delta, tween(4700, easing = FastOutSlowInEasing))
        pointerKick.animateTo(1f, tween(90))
        pointerKick.animateTo(0f, tween(230))
        delay(140)
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, outcome.multiplier, outcomeName(outcome))
        landedLabel = outcomeName(outcome)
        message = "LANDED $landedLabel · ${if (outcome.multiplier > 0.0) "WIN" else "NO PAYOUT"}"
        active = null
        pending = null
    }

    PremiumGameFrame("Wheel", "9 sectors · weighted visual reveal · up to 25x", balance, ShellPurple, if (active != null) ::leave else onBack) { compact, landscape ->
        val wheel: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UltraMultiplierWheel(
                    rotation = rotation.value,
                    sectors = sectors,
                    pointerKick = pointerKick.value,
                    modifier = Modifier.size(if (compact) 252.dp else 316.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0B0D14),
                    border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.30f)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("RESULT", fontSize = 7.sp, color = Color.White.copy(alpha = 0.34f), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(landedLabel, fontSize = if (compact) 24.sp else 30.sp, color = ShellGold, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = ShellPanel.copy(alpha = 0.78f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        sectors.forEach { value ->
                            Text(
                                if (value % 1.0 == 0.0) "${value.toInt()}×" else "${value}×",
                                fontSize = 8.sp,
                                color = if (value >= 5.0) ShellGold else Color.White.copy(alpha = 0.52f),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                QuickStakeRow(active == null, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, active == null, ShellPurple, Modifier.weight(1f)) { stake = it }
                    PremiumActionButton(if (active == null) "SPIN" else "SPINNING…", ShellPurple, active == null, Modifier.weight(0.72f)) {
                        viewModel.beginWager("Wheel", premiumStake(stake)) { started ->
                            if (started == null) message = "Could not start: check stake and balance."
                            else {
                                val outcome = WheelEngine(viewModel.randomProvider()).spin()
                                active = started
                                pending = outcome
                                targetIndex = sectors.indexOf(outcome.multiplier).coerceAtLeast(0)
                                trigger++
                                message = "Wheel accelerating…"
                            }
                        }
                    }
                }
                PremiumMessageCard(message, ShellPurple)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.15f)) { wheel() }
                Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { wheel() }
                controls()
            }
        }
    }
}

@Composable
private fun UltraMultiplierWheel(
    rotation: Float,
    sectors: List<Double>,
    pointerKick: Float,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFF7C3AED), Color(0xFF0891B2), Color(0xFF059669), Color(0xFFD6A92B),
        Color(0xFFD24A63), Color(0xFF4E63D8), Color(0xFF0D9488), Color(0xFFE67E22), Color(0xFF9333EA)
    )
    Canvas(modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.43f
        val sweep = 360f / sectors.size

        drawCircle(ShellPurple.copy(alpha = 0.05f), radius * 1.24f, center)
        drawCircle(ShellPurple.copy(alpha = 0.10f), radius * 1.12f, center)
        drawCircle(Color(0xFF05070C), radius * 1.075f, center)
        drawCircle(Color.White.copy(alpha = 0.10f), radius * 1.035f, center, style = Stroke(width = 2.2f))

        repeat(36) { tick ->
            val angle = Math.toRadians((tick * 10.0 - 90.0))
            val longTick = tick % 4 == 0
            val r1 = radius * if (longTick) 1.015f else 1.03f
            val r2 = radius * 1.075f
            val p1 = Offset(center.x + cos(angle).toFloat() * r1, center.y + sin(angle).toFloat() * r1)
            val p2 = Offset(center.x + cos(angle).toFloat() * r2, center.y + sin(angle).toFloat() * r2)
            drawLine(if (longTick) ShellGold.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.16f), p1, p2, strokeWidth = if (longTick) 2f else 1f)
        }

        rotate(rotation, center) {
            sectors.indices.forEach { i ->
                val sectorColor = colors[i % colors.size]
                drawArc(
                    brush = Brush.radialGradient(
                        listOf(sectorColor.copy(alpha = 0.96f), sectorColor.copy(alpha = 0.58f)),
                        center = center,
                        radius = radius
                    ),
                    startAngle = -90f - sweep / 2f + i * sweep,
                    sweepAngle = sweep - 0.9f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
                val angle = Math.toRadians((-90f + i * sweep).toDouble())
                val labelRadius = radius * 0.72f
                val x = center.x + cos(angle).toFloat() * labelRadius
                val y = center.y + sin(angle).toFloat() * labelRadius
                val label = if (sectors[i] % 1.0 == 0.0) "${sectors[i].toInt()}x" else "${sectors[i]}x"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    y + radius * 0.035f,
                    AndroidPaint().apply {
                        color = android.graphics.Color.WHITE
                        textAlign = AndroidPaint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = radius * 0.105f
                        setShadowLayer(radius * 0.018f, 0f, radius * 0.01f, android.graphics.Color.argb(130, 0, 0, 0))
                    }
                )
            }
            drawCircle(Color.White.copy(alpha = 0.08f), radius * 0.98f, center, style = Stroke(width = 2f))
        }

        drawCircle(Color(0xFF090B11), radius * 0.30f, center)
        drawCircle(ShellGold.copy(alpha = 0.22f), radius * 0.29f, center, style = Stroke(width = 2.5f))
        drawCircle(Color.White.copy(alpha = 0.07f), radius * 0.20f, center, style = Stroke(width = 1.2f))

        val pointerY = center.y - radius * (1.10f - pointerKick * 0.035f)
        val pointer = Path().apply {
            moveTo(center.x, pointerY + 20f)
            lineTo(center.x - 14f, pointerY - 7f)
            lineTo(center.x + 14f, pointerY - 7f)
            close()
        }
        drawPath(pointer, Color.Black.copy(alpha = 0.42f))
        drawPath(pointer, ShellGold)
        drawCircle(Color.White.copy(alpha = 0.65f), 2.3f, Offset(center.x - 3f, pointerY - 1f))
    }
}

@Composable
fun UltraSlotsGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var themeIndex by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<ActiveWager?>(null) }
    var pending by remember { mutableStateOf<SlotSpinResult?>(null) }
    var displayed by remember { mutableStateOf(List(3) { List(3) { SlotsEngine.Themes[0].symbols.first() } }) }
    var spinning by remember { mutableStateOf(false) }
    var trigger by remember { mutableIntStateOf(0) }
    var autoRemaining by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Pick a machine and spin.") }
    var winningCells by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    val paylineSource = remember { SlotsEngine(viewModel.randomProvider()).paylines }

    fun leave() {
        val wager = active
        val result = pending
        active = null
        pending = null
        spinning = false
        autoRemaining = 0
        if (wager != null && result != null) {
            viewModel.settleWager(wager, result.payoutMultiplier, if (result.payoutMultiplier > 0) "WIN" else "NO WIN", "${result.lineWins.size} paylines · ${result.bonusCount} bonus")
        } else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::leave)

    fun startSpin() {
        if (spinning) return
        val theme = SlotsEngine.Themes[themeIndex]
        viewModel.beginWager("Slots · ${theme.title}", premiumStake(stake)) { started ->
            if (started == null) {
                message = "Could not start: check stake and balance."
                autoRemaining = 0
            } else {
                active = started
                pending = SlotsEngine(viewModel.randomProvider()).spin(theme)
                spinning = true
                winningCells = emptySet()
                trigger++
                message = "Reels spinning…"
            }
        }
    }

    LaunchedEffect(trigger) {
        val result = pending ?: return@LaunchedEffect
        val theme = SlotsEngine.Themes[themeIndex]
        repeat(38) { tick ->
            displayed = List(3) { row ->
                List(3) { col ->
                    if (tick > 25 + col * 4) result.grid[row][col]
                    else theme.symbols[(tick + row * 2 + col * 3) % theme.symbols.size]
                }
            }
            delay((38L + tick * 2L).coerceAtMost(98L))
        }
        displayed = result.grid
        val win = buildSet {
            result.lineWins.forEach { line ->
                paylineSource.getOrNull(line.lineIndex)?.forEach { add(it) }
            }
        }
        winningCells = win
        delay(260)
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, result.payoutMultiplier, if (result.payoutMultiplier > 0) "WIN" else "NO WIN", "${result.lineWins.size} paylines · ${result.bonusCount} bonus")
        message = when {
            result.payoutMultiplier > 0.0 -> "WIN ${"%.2f".format(result.payoutMultiplier)}x · ${result.lineWins.size} line(s)"
            result.bonusCount >= 3 -> "BONUS ${"%.2f".format(result.payoutMultiplier)}x"
            else -> "No win"
        }
        active = null
        pending = null
        spinning = false
        if (autoRemaining > 0) autoRemaining--
    }

    LaunchedEffect(autoRemaining, spinning, themeIndex) {
        if (autoRemaining > 0 && !spinning) {
            delay(320)
            startSpin()
        }
    }

    PremiumGameFrame("Slots", "3 machines · animated reels · 5 paylines", balance, ShellPurple, if (active != null) ::leave else onBack) { compact, landscape ->
        val machine: @Composable () -> Unit = {
            val glow = rememberInfiniteTransition(label = "slot-win-glow")
            val pulse by glow.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(tween(620), repeatMode = RepeatMode.Reverse),
                label = "slot-pulse"
            )
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF0B0D15),
                border = BorderStroke(1.dp, ShellPurple.copy(alpha = 0.30f)),
                shadowElevation = 12.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = ShellPurple.copy(alpha = 0.09f), border = BorderStroke(1.dp, ShellPurple.copy(alpha = 0.18f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SlotsEngine.Themes[themeIndex].title.uppercase(), color = ShellPurple, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.8.sp)
                            Text(if (spinning) "REELS LIVE" else if (winningCells.isNotEmpty()) "PAYLINE HIT" else "READY", color = if (winningCells.isNotEmpty()) ShellGold else Color.White.copy(alpha = 0.42f), fontWeight = FontWeight.Black, fontSize = 8.sp)
                        }
                    }
                    displayed.forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { colIndex, symbol ->
                                val winning = (rowIndex to colIndex) in winningCells
                                val accent = when {
                                    winning -> ShellGold
                                    symbol.bonus -> ShellGold
                                    else -> listOf(ShellCyan, ShellPurple, ShellGreen)[colIndex]
                                }
                                Surface(
                                    Modifier.weight(1f).height(if (compact) 70.dp else 88.dp),
                                    shape = RoundedCornerShape(17.dp),
                                    color = if (winning) ShellGold.copy(alpha = 0.10f + 0.05f * pulse) else ShellPanel2,
                                    border = BorderStroke(1.dp, accent.copy(alpha = if (winning) 0.55f + 0.30f * pulse else if (spinning) 0.20f else 0.34f)),
                                    shadowElevation = if (winning) 7.dp else 1.dp
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        SlotSymbolArt(symbol, accent, spinning && colIndex >= 1, Modifier.size(if (compact) 45.dp else 56.dp))
                                        Text(
                                            symbol.id,
                                            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                                            fontSize = 6.sp,
                                            color = Color.White.copy(alpha = 0.30f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5 PAYLINES", color = Color.White.copy(alpha = 0.28f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                        Text(if (winningCells.isEmpty()) "—  ╲  ─  ╱  —" else "✦ WIN LINE ✦", color = if (winningCells.isEmpty()) Color.White.copy(alpha = 0.24f) else ShellGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("99% LOGIC", color = Color.White.copy(alpha = 0.28f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlotsEngine.Themes.forEachIndexed { index, theme ->
                        FilterChip(
                            selected = themeIndex == index,
                            enabled = !spinning && autoRemaining == 0,
                            onClick = {
                                themeIndex = index
                                displayed = List(3) { List(3) { theme.symbols.first() } }
                                winningCells = emptySet()
                            },
                            label = { Text(theme.title, fontSize = 8.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                QuickStakeRow(!spinning && autoRemaining == 0, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, !spinning && autoRemaining == 0, ShellPurple, Modifier.weight(1f)) { stake = it }
                    PremiumActionButton(if (spinning) "SPINNING…" else "SPIN", ShellPurple, !spinning && autoRemaining == 0, Modifier.weight(0.72f), ::startSpin)
                }
                OutlinedButton(onClick = { autoRemaining = if (autoRemaining > 0) 0 else 10 }, enabled = !spinning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (autoRemaining > 0) "STOP AUTO · $autoRemaining LEFT" else "AUTO-SPIN ×10", fontWeight = FontWeight.Black)
                }
                PremiumMessageCard(message, ShellPurple)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.2f)) { machine() }
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { machine() }
                controls()
            }
        }
    }
}

@Composable
private fun SlotSymbolArt(symbol: SlotSymbol, accent: Color, motion: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f - size.height * 0.04f)
        val s = size.minDimension
        if (motion) {
            repeat(4) { i ->
                val x = size.width * (0.18f + i * 0.20f)
                drawLine(accent.copy(alpha = 0.08f), Offset(x, size.height * 0.10f), Offset(x, size.height * 0.90f), strokeWidth = s * 0.045f)
            }
        }
        when (symbol.id) {
            "CHERRY" -> {
                drawCircle(Color(0xFFFF496A), s * 0.15f, Offset(c.x - s * 0.13f, c.y + s * 0.10f))
                drawCircle(Color(0xFFFF6B7A), s * 0.15f, Offset(c.x + s * 0.14f, c.y + s * 0.12f))
                drawLine(ShellGreen, Offset(c.x - s * 0.08f, c.y - s * 0.03f), Offset(c.x + s * 0.02f, c.y - s * 0.27f), s * 0.045f)
                drawLine(ShellGreen, Offset(c.x + s * 0.15f, c.y - s * 0.01f), Offset(c.x + s * 0.02f, c.y - s * 0.27f), s * 0.045f)
            }
            "LEMON" -> {
                drawOval(Color(0xFFFFD84D), Offset(c.x - s * 0.25f, c.y - s * 0.16f), Size(s * 0.50f, s * 0.32f))
                drawLine(Color.White.copy(alpha = 0.45f), Offset(c.x - s * 0.10f, c.y - s * 0.10f), Offset(c.x + s * 0.06f, c.y - s * 0.11f), s * 0.025f)
            }
            "GRAPE" -> {
                listOf(-0.12f to -0.05f, 0.10f to -0.05f, -0.22f to 0.12f, 0f to 0.15f, 0.22f to 0.12f, -0.10f to 0.30f, 0.10f to 0.30f).forEach { (dx, dy) ->
                    drawCircle(Color(0xFFA855F7), s * 0.105f, Offset(c.x + s * dx, c.y + s * dy))
                }
                drawLine(ShellGreen, Offset(c.x, c.y - s * 0.12f), Offset(c.x + s * 0.08f, c.y - s * 0.30f), s * 0.045f)
            }
            "SEVEN" -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.24f, c.y - s * 0.25f)
                    lineTo(c.x + s * 0.26f, c.y - s * 0.25f)
                    lineTo(c.x - s * 0.02f, c.y + s * 0.30f)
                    lineTo(c.x - s * 0.17f, c.y + s * 0.30f)
                    lineTo(c.x + s * 0.08f, c.y - s * 0.10f)
                    lineTo(c.x - s * 0.24f, c.y - s * 0.10f)
                    close()
                }
                drawPath(p, Color(0xFFFF5B6E))
            }
            "NOVA", "SUN" -> drawStar(c, s * 0.29f, if (symbol.id == "SUN") Color(0xFFFFC84A) else ShellCyan)
            "BONUS" -> {
                drawCircle(ShellGold.copy(alpha = 0.22f), s * 0.31f, c)
                drawCircle(ShellGold, s * 0.25f, c, style = Stroke(width = s * 0.06f))
                drawStar(c, s * 0.14f, ShellGold)
            }
            "SCARAB" -> {
                drawOval(Color(0xFF38BDF8), Offset(c.x - s * 0.12f, c.y - s * 0.22f), Size(s * 0.24f, s * 0.44f))
                drawArc(ShellGold, 205f, 130f, false, Offset(c.x - s * 0.30f, c.y - s * 0.12f), Size(s * 0.28f, s * 0.32f), style = Stroke(s * 0.055f))
                drawArc(ShellGold, -155f, 130f, false, Offset(c.x + s * 0.02f, c.y - s * 0.12f), Size(s * 0.28f, s * 0.32f), style = Stroke(s * 0.055f))
            }
            "ANKH" -> {
                drawCircle(ShellGold, s * 0.12f, Offset(c.x, c.y - s * 0.16f), style = Stroke(s * 0.055f))
                drawLine(ShellGold, Offset(c.x, c.y - s * 0.04f), Offset(c.x, c.y + s * 0.29f), s * 0.06f)
                drawLine(ShellGold, Offset(c.x - s * 0.18f, c.y + s * 0.08f), Offset(c.x + s * 0.18f, c.y + s * 0.08f), s * 0.06f)
            }
            "COBRA" -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.18f, c.y + s * 0.29f)
                    cubicTo(c.x + s * 0.25f, c.y + s * 0.15f, c.x - s * 0.20f, c.y - s * 0.02f, c.x + s * 0.10f, c.y - s * 0.15f)
                    cubicTo(c.x + s * 0.28f, c.y - s * 0.23f, c.x + s * 0.22f, c.y - s * 0.34f, c.x + s * 0.03f, c.y - s * 0.26f)
                }
                drawPath(p, ShellGreen, style = Stroke(width = s * 0.08f))
                drawCircle(ShellGold, s * 0.025f, Offset(c.x + s * 0.09f, c.y - s * 0.25f))
            }
            "PHARAOH" -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.26f, c.y + s * 0.20f)
                    lineTo(c.x - s * 0.18f, c.y - s * 0.22f)
                    lineTo(c.x, c.y - s * 0.31f)
                    lineTo(c.x + s * 0.18f, c.y - s * 0.22f)
                    lineTo(c.x + s * 0.26f, c.y + s * 0.20f)
                    close()
                }
                drawPath(p, ShellGold)
                drawLine(ShellCyan, Offset(c.x - s * 0.16f, c.y - s * 0.10f), Offset(c.x + s * 0.16f, c.y - s * 0.10f), s * 0.045f)
            }
            "CHIP" -> {
                drawRoundRect(ShellCyan.copy(alpha = 0.22f), Offset(c.x - s * 0.23f, c.y - s * 0.23f), Size(s * 0.46f, s * 0.46f), CornerRadius(s * 0.06f))
                drawRoundRect(ShellCyan, Offset(c.x - s * 0.15f, c.y - s * 0.15f), Size(s * 0.30f, s * 0.30f), CornerRadius(s * 0.04f), style = Stroke(s * 0.045f))
                repeat(4) { i ->
                    val d = -0.18f + i * 0.12f
                    drawLine(ShellCyan, Offset(c.x + s * d, c.y - s * 0.31f), Offset(c.x + s * d, c.y - s * 0.23f), s * 0.025f)
                    drawLine(ShellCyan, Offset(c.x + s * d, c.y + s * 0.23f), Offset(c.x + s * d, c.y + s * 0.31f), s * 0.025f)
                }
            }
            "CORE" -> {
                drawCircle(ShellPurple.copy(alpha = 0.20f), s * 0.29f, c)
                drawCircle(ShellPurple, s * 0.22f, c, style = Stroke(s * 0.055f))
                drawCircle(ShellCyan, s * 0.08f, c)
                drawCircle(Color.White.copy(alpha = 0.65f), s * 0.025f, Offset(c.x - s * 0.02f, c.y - s * 0.02f))
            }
            "DRONE" -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - s * 0.20f); lineTo(c.x + s * 0.18f, c.y); lineTo(c.x, c.y + s * 0.20f); lineTo(c.x - s * 0.18f, c.y); close()
                }
                drawPath(p, ShellCyan.copy(alpha = 0.25f))
                drawPath(p, ShellCyan, style = Stroke(s * 0.045f))
                drawLine(ShellPurple, Offset(c.x - s * 0.31f, c.y - s * 0.16f), Offset(c.x + s * 0.31f, c.y + s * 0.16f), s * 0.035f)
                drawCircle(ShellGold, s * 0.045f, c)
            }
            "VAULT" -> {
                drawRoundRect(Color(0xFF65738A), Offset(c.x - s * 0.27f, c.y - s * 0.25f), Size(s * 0.54f, s * 0.50f), CornerRadius(s * 0.06f))
                drawCircle(Color(0xFF111827), s * 0.15f, c)
                drawCircle(ShellGold, s * 0.12f, c, style = Stroke(s * 0.04f))
                repeat(4) { i ->
                    val a = i * PI / 2.0
                    drawLine(ShellGold, c, Offset(c.x + cos(a).toFloat() * s * 0.11f, c.y + sin(a).toFloat() * s * 0.11f), s * 0.025f)
                }
            }
            "GLITCH" -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.28f, c.y - s * 0.16f)
                    lineTo(c.x - s * 0.03f, c.y - s * 0.16f)
                    lineTo(c.x - s * 0.14f, c.y + s * 0.02f)
                    lineTo(c.x + s * 0.22f, c.y + s * 0.02f)
                    lineTo(c.x + s * 0.06f, c.y + s * 0.25f)
                    lineTo(c.x + s * 0.29f, c.y + s * 0.25f)
                }
                drawPath(p, Color(0xFFFF4FD8), style = Stroke(s * 0.07f))
                drawLine(ShellCyan, Offset(c.x - s * 0.20f, c.y + s * 0.17f), Offset(c.x + s * 0.12f, c.y + s * 0.17f), s * 0.035f)
            }
            else -> drawCircle(accent, s * 0.22f, c)
        }
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    repeat(10) { i ->
        val angle = -PI / 2.0 + i * PI / 5.0
        val r = if (i % 2 == 0) radius else radius * 0.42f
        val p = Offset(center.x + cos(angle).toFloat() * r, center.y + sin(angle).toFloat() * r)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
fun UltraHorseRacingGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val catalogEngine = remember { HorseRacingEngine(viewModel.randomProvider()) }
    var race by remember { mutableStateOf(catalogEngine.generateRace(count = 8)) }
    var selectedId by remember { mutableStateOf(race.horses.first().id) }
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var result by remember { mutableStateOf<HorseRaceResult?>(null) }
    var running by remember { mutableStateOf(false) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Pick a runner and start the race.") }
    val raceProgress = remember { Animatable(0f) }

    fun selectedHorse(): Horse = race.horses.first { it.id == selectedId }

    fun settle(reason: String = "Race finished") {
        val active = wager ?: return
        val final = result ?: return
        val picked = selectedHorse()
        val won = final.winnerId == selectedId
        wager = null
        running = false
        viewModel.settleWager(
            active,
            if (won) picked.odds else 0.0,
            if (won) "${picked.name} won" else "${picked.name} lost",
            "$reason · ${final.finishOrder.joinToString()}"
        )
        val winner = race.horses.first { it.id == final.winnerId }
        message = if (won) "${picked.name.uppercase()} WINS · ${picked.odds}x" else "Winner: ${winner.name} · your pick #${final.finishOrder.indexOf(selectedId) + 1}"
    }

    fun leave() {
        if (wager != null && result != null) settle("Settled on exit") else wager?.let(viewModel::cancelWager)
        wager = null
        running = false
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    LaunchedEffect(trigger) {
        if (!running || result == null) return@LaunchedEffect
        raceProgress.snapTo(0f)
        raceProgress.animateTo(1f, tween(6800, easing = LinearEasing))
        delay(220)
        if (running) settle()
    }

    PremiumGameFrame("Horse Racing", "8 runners · live overtakes · result fixed before start", balance, ShellGold, if (wager != null) ::leave else onBack) { compact, landscape ->
        val track: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0A1510),
                border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.22f)),
                shadowElevation = 10.dp
            ) {
                UltraHorseTrack(
                    race = race,
                    result = result,
                    progress = raceProgress.value,
                    selectedId = selectedId,
                    running = running,
                    modifier = Modifier.fillMaxSize().padding(if (compact) 5.dp else 8.dp)
                )
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!running && wager == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        race.horses.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                pair.forEach { horse ->
                                    val selected = horse.id == selectedId
                                    Surface(
                                        onClick = { selectedId = horse.id },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) ShellGold.copy(alpha = 0.14f) else ShellPanel.copy(alpha = 0.80f),
                                        border = BorderStroke(1.dp, if (selected) ShellGold.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.055f)),
                                        shadowElevation = if (selected) 4.dp else 0.dp
                                    ) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("#${race.horses.indexOf(horse) + 1}", color = if (selected) ShellGold else Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.Black, fontSize = 8.sp)
                                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                                Text(horse.name, maxLines = 1, fontWeight = FontWeight.Black, fontSize = 9.sp)
                                                Text("SPD ${horse.speed} · STA ${horse.stamina}", fontSize = 6.sp, color = Color.White.copy(alpha = 0.30f))
                                            }
                                            Text("${horse.odds}x", color = ShellCyan, fontWeight = FontWeight.Black, fontSize = 8.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    QuickStakeRow(true, stake) { stake = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PremiumStakeField(stake, true, ShellGold, Modifier.weight(1f)) { stake = it }
                        PremiumActionButton("RACE", ShellGold, true, Modifier.weight(0.72f)) {
                            viewModel.beginWager("Horse Racing", premiumStake(stake)) { started ->
                                if (started == null) message = "Could not start: check stake and balance."
                                else {
                                    wager = started
                                    result = HorseRacingEngine(viewModel.randomProvider()).simulate(race)
                                    running = true
                                    trigger++
                                    message = "RACE LIVE · ${selectedHorse().name} @ ${selectedHorse().odds}x"
                                }
                            }
                        }
                    }
                } else if (!running && result != null && wager == null) {
                    OutlinedButton(
                        onClick = {
                            race = catalogEngine.generateRace(id = "race_${System.currentTimeMillis()}", count = 8)
                            selectedId = race.horses.first().id
                            result = null
                            raceProgress.snapTo(0f)
                            message = "New field generated. Pick a runner."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("NEW RACE", fontWeight = FontWeight.Black) }
                } else {
                    val picked = selectedHorse()
                    Surface(shape = RoundedCornerShape(16.dp), color = ShellPanel2.copy(alpha = 0.85f), border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.14f))) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("YOUR PICK", fontSize = 8.sp, color = Color.White.copy(alpha = 0.34f), fontWeight = FontWeight.Black)
                            Text(picked.name, fontWeight = FontWeight.Black, color = ShellGold)
                            Text("${picked.odds}x", fontWeight = FontWeight.Black, color = ShellCyan)
                        }
                    }
                }
                PremiumMessageCard(message, ShellGold)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1.35f).fillMaxSize()) { track() }
                Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.20f)) { track() }
                Box(Modifier.weight(0.80f), contentAlignment = Alignment.BottomCenter) { controls() }
            }
        }
    }
}

@Composable
private fun UltraHorseTrack(
    race: HorseRace,
    result: HorseRaceResult?,
    progress: Float,
    selectedId: String,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val horseColors = listOf(ShellGold, ShellCyan, ShellPurple, ShellGreen, ShellRed, Color(0xFF7C8CFF), Color(0xFFFF9F43), Color(0xFF48D1CC))
    Canvas(modifier) {
        val laneHeight = size.height / race.horses.size
        val startX = size.width * 0.075f
        val finishX = size.width * 0.91f
        val trackLength = finishX - startX

        drawRect(Brush.verticalGradient(listOf(Color(0xFF153521), Color(0xFF0B2014))))
        repeat(18) { stripe ->
            val x = stripe * size.width / 18f
            drawRect(Color.White.copy(alpha = if (stripe % 2 == 0) 0.012f else 0.004f), Offset(x, 0f), Size(size.width / 18f, size.height))
        }
        for (lane in race.horses.indices) {
            val y0 = lane * laneHeight
            if (lane % 2 == 0) drawRect(Color.White.copy(alpha = 0.018f), Offset(0f, y0), Size(size.width, laneHeight))
            drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, y0 + laneHeight), Offset(size.width, y0 + laneHeight), 1.3f)
            drawContext.canvas.nativeCanvas.drawText(
                "${lane + 1}",
                size.width * 0.025f,
                y0 + laneHeight * 0.63f,
                AndroidPaint().apply {
                    color = android.graphics.Color.argb(90, 255, 255, 255)
                    textAlign = AndroidPaint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = laneHeight * 0.28f
                }
            )
        }
        repeat(4) { marker ->
            val x = startX + trackLength * (marker + 1) / 5f
            drawLine(Color.White.copy(alpha = 0.045f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        drawLine(Color.White.copy(alpha = 0.18f), Offset(startX, 0f), Offset(startX, size.height), 2f)
        repeat(14) { block ->
            val y0 = block * size.height / 14f
            drawRect(if (block % 2 == 0) Color.White.copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.72f), Offset(finishX - 3.5f, y0), Size(7f, size.height / 14f))
        }

        race.horses.forEachIndexed { index, horse ->
            val finishPos = result?.finishOrder?.indexOf(horse.id)?.takeIf { it >= 0 } ?: index
            val p = if (running || result != null) progress.coerceIn(0f, 1f) else 0f
            val wave = sin((p * PI * 4.8 + index * 0.86)).toFloat() * (1f - p) * p * 0.15f
            val statBias = ((horse.speed + horse.stamina) / 200f - 0.60f) * sin((p * PI).toFloat()) * 0.13f
            val finishPenalty = finishPos * 0.012f * p * p * p
            val normalized = if (p >= 0.999f) (1f - finishPos * 0.012f) else (p + wave + statBias - finishPenalty).coerceIn(0f, 0.99f)
            val x = startX + trackLength * normalized
            val y = laneHeight * (index + 0.56f)
            val color = horseColors[index % horseColors.size]
            val selected = horse.id == selectedId
            val phase = (p * 52f + index * 0.71f)

            if (selected) {
                drawCircle(ShellGold.copy(alpha = 0.08f), laneHeight * 0.49f, Offset(x, y - laneHeight * 0.05f))
                drawLine(ShellGold.copy(alpha = 0.12f), Offset(startX, y), Offset(x - laneHeight * 0.25f, y), strokeWidth = 2f)
            }
            if (running && p > 0.03f) {
                repeat(3) { dust ->
                    val dx = laneHeight * (0.45f + dust * 0.28f)
                    val dy = sin(phase + dust).toFloat() * laneHeight * 0.08f
                    drawCircle(Color(0xFFC9B88D).copy(alpha = 0.12f - dust * 0.025f), laneHeight * (0.10f - dust * 0.018f), Offset(x - dx, y + dy))
                }
            }
            drawGallopingHorse(Offset(x, y), laneHeight * 0.31f, color, selected, phase)

            if (result != null && p >= 0.999f) {
                drawContext.canvas.nativeCanvas.drawText(
                    "#${finishPos + 1}",
                    x,
                    y - laneHeight * 0.31f,
                    AndroidPaint().apply {
                        color = if (finishPos == 0) android.graphics.Color.rgb(244, 201, 93) else android.graphics.Color.argb(180, 255, 255, 255)
                        textAlign = AndroidPaint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = laneHeight * 0.22f
                    }
                )
            }
        }
    }
}

private fun DrawScope.drawGallopingHorse(center: Offset, scale: Float, color: Color, selected: Boolean, phase: Float) {
    val bodyCenter = Offset(center.x, center.y - scale * 0.10f)
    val dark = color.copy(alpha = 0.78f)
    val legSwing = sin(phase).toFloat()
    val opposite = sin(phase + PI.toFloat()).toFloat()

    drawOval(dark, Offset(bodyCenter.x - scale * 0.70f, bodyCenter.y - scale * 0.26f), Size(scale * 1.18f, scale * 0.52f))
    drawLine(color, Offset(bodyCenter.x + scale * 0.36f, bodyCenter.y - scale * 0.12f), Offset(bodyCenter.x + scale * 0.63f, bodyCenter.y - scale * 0.47f), strokeWidth = scale * 0.18f)
    drawOval(color, Offset(bodyCenter.x + scale * 0.48f, bodyCenter.y - scale * 0.63f), Size(scale * 0.42f, scale * 0.30f))
    drawLine(color.copy(alpha = 0.65f), Offset(bodyCenter.x - scale * 0.60f, bodyCenter.y - scale * 0.08f), Offset(bodyCenter.x - scale * 0.91f, bodyCenter.y - scale * 0.36f), strokeWidth = scale * 0.10f)

    val rearHip = Offset(bodyCenter.x - scale * 0.42f, bodyCenter.y + scale * 0.16f)
    val frontHip = Offset(bodyCenter.x + scale * 0.30f, bodyCenter.y + scale * 0.14f)
    fun leg(root: Offset, swing: Float, forward: Boolean) {
        val knee = Offset(root.x + scale * (0.22f * swing + if (forward) 0.04f else -0.04f), root.y + scale * 0.42f)
        val hoof = Offset(knee.x + scale * (0.25f * swing), center.y + scale * 0.55f - kotlin.math.abs(swing) * scale * 0.12f)
        drawLine(dark, root, knee, strokeWidth = scale * 0.11f)
        drawLine(color, knee, hoof, strokeWidth = scale * 0.09f)
        drawLine(color, hoof, Offset(hoof.x + scale * 0.16f, hoof.y), strokeWidth = scale * 0.07f)
    }
    leg(rearHip, legSwing, false)
    leg(Offset(rearHip.x + scale * 0.19f, rearHip.y), opposite, false)
    leg(frontHip, opposite, true)
    leg(Offset(frontHip.x + scale * 0.18f, frontHip.y), legSwing, true)

    val saddle = Offset(bodyCenter.x - scale * 0.03f, bodyCenter.y - scale * 0.28f)
    drawOval(Color(0xFF1F2937), Offset(saddle.x - scale * 0.22f, saddle.y - scale * 0.06f), Size(scale * 0.44f, scale * 0.16f))
    val riderBody = Offset(saddle.x + scale * 0.02f, saddle.y - scale * 0.28f)
    drawLine(ShellCyan, saddle, riderBody, strokeWidth = scale * 0.15f)
    drawCircle(Color(0xFFE8B58B), scale * 0.12f, Offset(riderBody.x + scale * 0.10f, riderBody.y - scale * 0.17f))
    drawArc(ShellGold, 190f, 170f, false, Offset(riderBody.x - scale * 0.02f, riderBody.y - scale * 0.31f), Size(scale * 0.25f, scale * 0.15f), style = Stroke(scale * 0.07f))
    drawLine(ShellCyan, riderBody, Offset(bodyCenter.x + scale * 0.47f, bodyCenter.y - scale * 0.25f), strokeWidth = scale * 0.075f)

    if (selected) drawCircle(ShellGold.copy(alpha = 0.75f), scale * 1.12f, center, style = Stroke(width = scale * 0.075f))
}
