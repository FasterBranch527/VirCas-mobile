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
import kotlin.math.abs
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
    var landed by remember { mutableStateOf("×?") }
    val rotation = remember { Animatable(0f) }
    val pointerKick = remember { Animatable(0f) }

    fun label(outcome: GameOutcome): String = if (outcome is GameOutcome.Win) outcome.label else "0x"
    fun leave() {
        val wager = active
        val outcome = pending
        active = null
        pending = null
        if (wager != null && outcome != null) viewModel.settleWager(wager, outcome.multiplier, label(outcome))
        else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::leave)

    LaunchedEffect(trigger) {
        val outcome = pending ?: return@LaunchedEffect
        val step = 360f / sectors.size
        val current = rotation.value
        val normalized = ((current % 360f) + 360f) % 360f
        val desired = ((-targetIndex * step) % 360f + 360f) % 360f
        var delta = desired - normalized
        if (delta < 0f) delta += 360f
        rotation.animateTo(current + 7f * 360f + delta, tween(4700, easing = FastOutSlowInEasing))
        pointerKick.snapTo(0f)
        pointerKick.animateTo(1f, tween(75))
        pointerKick.animateTo(0f, tween(220))
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, outcome.multiplier, label(outcome))
        landed = label(outcome)
        message = "LANDED $landed · ${if (outcome.multiplier > 0.0) "WIN" else "NO PAYOUT"}"
        active = null
        pending = null
    }

    PremiumGameFrame("Wheel", "9 sectors · up to 25x", balance, ShellPurple, if (active != null) ::leave else onBack) { compact, landscape ->
        val visual: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UltraWheelVisual(rotation.value, sectors, pointerKick.value, Modifier.size(if (compact) 250.dp else 312.dp))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF090B11),
                    border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.32f)),
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.padding(horizontal = 19.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RESULT", fontSize = 7.sp, color = Color.White.copy(alpha = 0.34f), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(landed, fontSize = if (compact) 23.sp else 29.sp, color = ShellGold, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = ShellPanel.copy(alpha = 0.80f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        sectors.forEach { value ->
                            Text(formatMultiplier(value), fontSize = 8.sp, color = if (value >= 5.0) ShellGold else Color.White.copy(alpha = 0.52f), fontWeight = FontWeight.Black)
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
                Box(Modifier.weight(1.15f)) { visual() }
                Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { visual() }
                controls()
            }
        }
    }
}

@Composable
private fun UltraWheelVisual(rotation: Float, sectors: List<Double>, pointerKick: Float, modifier: Modifier = Modifier) {
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
            val a = Math.toRadians(tick * 10.0 - 90.0)
            val major = tick % 4 == 0
            val r1 = radius * if (major) 1.012f else 1.030f
            val r2 = radius * 1.075f
            drawLine(
                if (major) ShellGold.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.15f),
                Offset(center.x + cos(a).toFloat() * r1, center.y + sin(a).toFloat() * r1),
                Offset(center.x + cos(a).toFloat() * r2, center.y + sin(a).toFloat() * r2),
                strokeWidth = if (major) 2f else 1f
            )
        }
        rotate(rotation, center) {
            sectors.indices.forEach { i ->
                val sectorColor = colors[i % colors.size]
                drawArc(
                    brush = Brush.radialGradient(listOf(sectorColor.copy(alpha = 0.96f), sectorColor.copy(alpha = 0.60f)), center, radius),
                    startAngle = -90f - sweep / 2f + i * sweep,
                    sweepAngle = sweep - 0.9f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
                val a = Math.toRadians((-90f + i * sweep).toDouble())
                val rr = radius * 0.72f
                drawContext.canvas.nativeCanvas.drawText(
                    formatMultiplier(sectors[i]),
                    center.x + cos(a).toFloat() * rr,
                    center.y + sin(a).toFloat() * rr + radius * 0.035f,
                    AndroidPaint().apply {
                        color = android.graphics.Color.WHITE
                        textAlign = AndroidPaint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = radius * 0.10f
                        setShadowLayer(radius * 0.018f, 0f, radius * 0.01f, android.graphics.Color.argb(130, 0, 0, 0))
                    }
                )
            }
            drawCircle(Color.White.copy(alpha = 0.08f), radius * 0.98f, center, style = Stroke(width = 2f))
        }
        drawCircle(Color(0xFF090B11), radius * 0.30f, center)
        drawCircle(ShellGold.copy(alpha = 0.20f), radius * 0.29f, center, style = Stroke(width = 2.4f))
        val py = center.y - radius * (1.10f - pointerKick * 0.035f)
        val pointer = Path().apply {
            moveTo(center.x, py + 20f)
            lineTo(center.x - 14f, py - 7f)
            lineTo(center.x + 14f, py - 7f)
            close()
        }
        drawPath(pointer, ShellGold)
        drawCircle(Color.White.copy(alpha = 0.68f), 2.2f, Offset(center.x - 3f, py - 1f))
    }
}

private fun formatMultiplier(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}×" else "${value}×"

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
    val paylines = remember { SlotsEngine(viewModel.randomProvider()).paylines }

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
                winningCells = emptySet()
                spinning = true
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
        winningCells = buildSet {
            result.lineWins.forEach { win -> paylines.getOrNull(win.lineIndex)?.forEach { add(it) } }
        }
        delay(230)
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

    PremiumGameFrame("Slots", "3 machines · vector symbols · 5 paylines", balance, ShellPurple, if (active != null) ::leave else onBack) { compact, landscape ->
        val machine: @Composable () -> Unit = {
            val transition = rememberInfiniteTransition(label = "slot-win")
            val winPulse by transition.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(620), repeatMode = RepeatMode.Reverse), label = "slot-win-pulse")
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
                                val accent = if (winning || symbol.bonus) ShellGold else listOf(ShellCyan, ShellPurple, ShellGreen)[colIndex]
                                Surface(
                                    Modifier.weight(1f).height(if (compact) 70.dp else 88.dp),
                                    shape = RoundedCornerShape(17.dp),
                                    color = if (winning) ShellGold.copy(alpha = 0.10f + winPulse * 0.05f) else ShellPanel2,
                                    border = BorderStroke(1.dp, accent.copy(alpha = if (winning) 0.55f + winPulse * 0.30f else if (spinning) 0.20f else 0.34f)),
                                    shadowElevation = if (winning) 7.dp else 1.dp
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        SlotVectorIcon(symbol, accent, spinning && colIndex > 0, Modifier.size(if (compact) 44.dp else 56.dp))
                                        Text(symbol.id, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), fontSize = 6.sp, color = Color.White.copy(alpha = 0.30f), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5 PAYLINES", color = Color.White.copy(alpha = 0.27f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                        Text(if (winningCells.isEmpty()) "—  ╲  ─  ╱  —" else "✦ WIN LINE ✦", color = if (winningCells.isEmpty()) Color.White.copy(alpha = 0.23f) else ShellGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("LOCAL RNG", color = Color.White.copy(alpha = 0.27f), fontSize = 7.sp, fontWeight = FontWeight.Black)
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
private fun SlotVectorIcon(symbol: SlotSymbol, accent: Color, motion: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f - size.height * 0.04f)
        val s = size.minDimension
        if (motion) repeat(4) { i -> drawLine(accent.copy(alpha = 0.07f), Offset(size.width * (0.18f + i * 0.20f), size.height * 0.10f), Offset(size.width * (0.18f + i * 0.20f), size.height * 0.90f), s * 0.04f) }
        when (symbol.id) {
            "CHERRY" -> {
                drawCircle(Color(0xFFFF496A), s * 0.15f, Offset(c.x - s * 0.13f, c.y + s * 0.10f)); drawCircle(Color(0xFFFF6B7A), s * 0.15f, Offset(c.x + s * 0.14f, c.y + s * 0.12f))
                drawLine(ShellGreen, Offset(c.x - s * 0.08f, c.y - s * 0.03f), Offset(c.x + s * 0.02f, c.y - s * 0.27f), s * 0.045f); drawLine(ShellGreen, Offset(c.x + s * 0.15f, c.y - s * 0.01f), Offset(c.x + s * 0.02f, c.y - s * 0.27f), s * 0.045f)
            }
            "LEMON" -> drawOval(Color(0xFFFFD84D), Offset(c.x - s * 0.25f, c.y - s * 0.16f), Size(s * 0.50f, s * 0.32f))
            "GRAPE" -> listOf(-.12f to -.05f, .10f to -.05f, -.22f to .12f, 0f to .15f, .22f to .12f, -.10f to .30f, .10f to .30f).forEach { (dx, dy) -> drawCircle(Color(0xFFA855F7), s * .105f, Offset(c.x + s * dx, c.y + s * dy)) }
            "SEVEN" -> {
                val p = Path().apply { moveTo(c.x - s*.24f,c.y-s*.25f); lineTo(c.x+s*.26f,c.y-s*.25f); lineTo(c.x-s*.02f,c.y+s*.30f); lineTo(c.x-s*.17f,c.y+s*.30f); lineTo(c.x+s*.08f,c.y-s*.10f); lineTo(c.x-s*.24f,c.y-s*.10f); close() }
                drawPath(p, Color(0xFFFF5B6E))
            }
            "NOVA", "SUN" -> drawSimpleStar(c, s * .29f, if (symbol.id == "SUN") Color(0xFFFFC84A) else ShellCyan)
            "BONUS" -> { drawCircle(ShellGold.copy(alpha=.22f),s*.31f,c); drawCircle(ShellGold,s*.25f,c,style=Stroke(s*.055f)); drawSimpleStar(c,s*.13f,ShellGold) }
            "SCARAB" -> { drawOval(Color(0xFF38BDF8),Offset(c.x-s*.12f,c.y-s*.22f),Size(s*.24f,s*.44f)); drawCircle(ShellGold,s*.22f,c,style=Stroke(s*.045f)) }
            "ANKH" -> { drawCircle(ShellGold,s*.12f,Offset(c.x,c.y-s*.16f),style=Stroke(s*.055f)); drawLine(ShellGold,Offset(c.x,c.y-s*.04f),Offset(c.x,c.y+s*.29f),s*.06f); drawLine(ShellGold,Offset(c.x-s*.18f,c.y+s*.08f),Offset(c.x+s*.18f,c.y+s*.08f),s*.06f) }
            "COBRA" -> { val p=Path().apply { moveTo(c.x-s*.18f,c.y+s*.29f); cubicTo(c.x+s*.25f,c.y+s*.15f,c.x-s*.20f,c.y-s*.02f,c.x+s*.10f,c.y-s*.15f); cubicTo(c.x+s*.28f,c.y-s*.23f,c.x+s*.22f,c.y-s*.34f,c.x+s*.03f,c.y-s*.26f) }; drawPath(p,ShellGreen,style=Stroke(s*.08f)) }
            "PHARAOH" -> { val p=Path().apply { moveTo(c.x-s*.26f,c.y+s*.20f); lineTo(c.x-s*.18f,c.y-s*.22f); lineTo(c.x,c.y-s*.31f); lineTo(c.x+s*.18f,c.y-s*.22f); lineTo(c.x+s*.26f,c.y+s*.20f); close() }; drawPath(p,ShellGold); drawLine(ShellCyan,Offset(c.x-s*.16f,c.y-s*.10f),Offset(c.x+s*.16f,c.y-s*.10f),s*.045f) }
            "CHIP" -> { drawRoundRect(ShellCyan.copy(alpha=.22f),Offset(c.x-s*.23f,c.y-s*.23f),Size(s*.46f,s*.46f),CornerRadius(s*.06f)); drawRoundRect(ShellCyan,Offset(c.x-s*.15f,c.y-s*.15f),Size(s*.30f,s*.30f),CornerRadius(s*.04f),style=Stroke(s*.045f)) }
            "CORE" -> { drawCircle(ShellPurple.copy(alpha=.20f),s*.29f,c); drawCircle(ShellPurple,s*.22f,c,style=Stroke(s*.055f)); drawCircle(ShellCyan,s*.08f,c) }
            "DRONE" -> { val p=Path().apply { moveTo(c.x,c.y-s*.20f); lineTo(c.x+s*.18f,c.y); lineTo(c.x,c.y+s*.20f); lineTo(c.x-s*.18f,c.y); close() }; drawPath(p,ShellCyan.copy(alpha=.25f)); drawPath(p,ShellCyan,style=Stroke(s*.045f)); drawLine(ShellPurple,Offset(c.x-s*.31f,c.y-s*.16f),Offset(c.x+s*.31f,c.y+s*.16f),s*.035f) }
            "VAULT" -> { drawRoundRect(Color(0xFF65738A),Offset(c.x-s*.27f,c.y-s*.25f),Size(s*.54f,s*.50f),CornerRadius(s*.06f)); drawCircle(Color(0xFF111827),s*.15f,c); drawCircle(ShellGold,s*.12f,c,style=Stroke(s*.04f)) }
            "GLITCH" -> { val p=Path().apply { moveTo(c.x-s*.28f,c.y-s*.16f); lineTo(c.x-s*.03f,c.y-s*.16f); lineTo(c.x-s*.14f,c.y+s*.02f); lineTo(c.x+s*.22f,c.y+s*.02f); lineTo(c.x+s*.06f,c.y+s*.25f); lineTo(c.x+s*.29f,c.y+s*.25f) }; drawPath(p,Color(0xFFFF4FD8),style=Stroke(s*.07f)); drawLine(ShellCyan,Offset(c.x-s*.20f,c.y+s*.17f),Offset(c.x+s*.12f,c.y+s*.17f),s*.035f) }
            else -> drawCircle(accent, s * .22f, c)
        }
    }
}

private fun DrawScope.drawSimpleStar(center: Offset, radius: Float, color: Color) {
    val p = Path()
    repeat(10) { i ->
        val angle = -PI / 2.0 + i * PI / 5.0
        val r = if (i % 2 == 0) radius else radius * .42f
        val point = Offset(center.x + cos(angle).toFloat() * r, center.y + sin(angle).toFloat() * r)
        if (i == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
    }
    p.close(); drawPath(p, color)
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
    val progress = remember { Animatable(0f) }

    fun selectedHorse(): Horse = race.horses.first { it.id == selectedId }
    fun settle(reason: String = "Race finished") {
        val active = wager ?: return
        val final = result ?: return
        val picked = selectedHorse()
        val won = final.winnerId == selectedId
        wager = null; running = false
        viewModel.settleWager(active, if (won) picked.odds else 0.0, if (won) "${picked.name} won" else "${picked.name} lost", "$reason · ${final.finishOrder.joinToString()}")
        val winner = race.horses.first { it.id == final.winnerId }
        message = if (won) "${picked.name.uppercase()} WINS · ${picked.odds}x" else "Winner: ${winner.name} · your pick #${final.finishOrder.indexOf(selectedId) + 1}"
    }
    fun leave() {
        if (wager != null && result != null) settle("Settled on exit") else wager?.let(viewModel::cancelWager)
        wager = null; running = false; onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    LaunchedEffect(trigger) {
        if (!running || result == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(6800, easing = LinearEasing))
        delay(220)
        if (running) settle()
    }

    PremiumGameFrame("Horse Racing", "8 runners · live overtakes · precomputed finish", balance, ShellGold, if (wager != null) ::leave else onBack) { compact, landscape ->
        val track: @Composable () -> Unit = {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF0A1510), border = BorderStroke(1.dp, ShellGold.copy(alpha=.22f)), shadowElevation = 10.dp) {
                UltraHorseTrack(race, result, progress.value, selectedId, running, Modifier.fillMaxSize().padding(if (compact) 5.dp else 8.dp))
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
                                    Surface(onClick = { selectedId = horse.id }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = if (selected) ShellGold.copy(alpha=.14f) else ShellPanel.copy(alpha=.80f), border = BorderStroke(1.dp, if (selected) ShellGold.copy(alpha=.60f) else Color.White.copy(alpha=.055f)), shadowElevation = if (selected) 4.dp else 0.dp) {
                                        Row(Modifier.padding(horizontal=8.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                                            Text("#${race.horses.indexOf(horse)+1}", color=if(selected) ShellGold else Color.White.copy(alpha=.35f), fontWeight=FontWeight.Black, fontSize=8.sp)
                                            Column(Modifier.weight(1f).padding(start=6.dp)) { Text(horse.name,maxLines=1,fontWeight=FontWeight.Black,fontSize=9.sp); Text("SPD ${horse.speed} · STA ${horse.stamina}",fontSize=6.sp,color=Color.White.copy(alpha=.30f)) }
                                            Text("${horse.odds}x",color=ShellCyan,fontWeight=FontWeight.Black,fontSize=8.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    QuickStakeRow(true, stake) { stake = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PremiumStakeField(stake,true,ShellGold,Modifier.weight(1f)){stake=it}
                        PremiumActionButton("RACE",ShellGold,true,Modifier.weight(.72f)) {
                            viewModel.beginWager("Horse Racing", premiumStake(stake)) { started ->
                                if(started==null) message="Could not start: check stake and balance."
                                else { wager=started; result=HorseRacingEngine(viewModel.randomProvider()).simulate(race); running=true; trigger++; message="RACE LIVE · ${selectedHorse().name} @ ${selectedHorse().odds}x" }
                            }
                        }
                    }
                } else if (!running && result != null && wager == null) {
                    OutlinedButton(onClick = { race=catalogEngine.generateRace(id="race_${System.currentTimeMillis()}",count=8); selectedId=race.horses.first().id; result=null; message="New field generated. Pick a runner." }, modifier=Modifier.fillMaxWidth()) { Text("NEW RACE",fontWeight=FontWeight.Black) }
                } else {
                    val picked=selectedHorse()
                    Surface(shape=RoundedCornerShape(16.dp),color=ShellPanel2.copy(alpha=.85f),border=BorderStroke(1.dp,ShellGold.copy(alpha=.14f))) { Row(Modifier.fillMaxWidth().padding(11.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("YOUR PICK",fontSize=8.sp,color=Color.White.copy(alpha=.34f),fontWeight=FontWeight.Black); Text(picked.name,fontWeight=FontWeight.Black,color=ShellGold); Text("${picked.odds}x",fontWeight=FontWeight.Black,color=ShellCyan) } }
                }
                PremiumMessageCard(message,ShellGold)
            }
        }
        if(landscape) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ Box(Modifier.weight(1.35f).fillMaxSize()){track()}; Column(Modifier.weight(.85f),verticalArrangement=Arrangement.Center){controls()} }
        else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){ Box(Modifier.weight(1.20f)){track()}; Box(Modifier.weight(.80f),contentAlignment=Alignment.BottomCenter){controls()} }
    }
}

@Composable
private fun UltraHorseTrack(race: HorseRace, result: HorseRaceResult?, progress: Float, selectedId: String, running: Boolean, modifier: Modifier = Modifier) {
    val colors=listOf(ShellGold,ShellCyan,ShellPurple,ShellGreen,ShellRed,Color(0xFF7C8CFF),Color(0xFFFF9F43),Color(0xFF48D1CC))
    Canvas(modifier) {
        val laneH=size.height/race.horses.size; val startX=size.width*.075f; val finishX=size.width*.91f; val length=finishX-startX
        drawRect(Brush.verticalGradient(listOf(Color(0xFF153521),Color(0xFF0B2014))))
        repeat(18){stripe-> val x=stripe*size.width/18f; drawRect(Color.White.copy(alpha=if(stripe%2==0).012f else .004f),Offset(x,0f),Size(size.width/18f,size.height)) }
        race.horses.indices.forEach { lane ->
            val y0=lane*laneH; if(lane%2==0) drawRect(Color.White.copy(alpha=.018f),Offset(0f,y0),Size(size.width,laneH)); drawLine(Color.White.copy(alpha=.09f),Offset(0f,y0+laneH),Offset(size.width,y0+laneH),1.3f)
            drawContext.canvas.nativeCanvas.drawText("${lane+1}",size.width*.025f,y0+laneH*.63f,AndroidPaint().apply{this.color=android.graphics.Color.argb(90,255,255,255);textAlign=AndroidPaint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD;textSize=laneH*.28f})
        }
        repeat(4){m-> val x=startX+length*(m+1)/5f; drawLine(Color.White.copy(alpha=.045f),Offset(x,0f),Offset(x,size.height),1f) }
        drawLine(Color.White.copy(alpha=.18f),Offset(startX,0f),Offset(startX,size.height),2f)
        repeat(14){b-> val y=b*size.height/14f; drawRect(if(b%2==0)Color.White.copy(alpha=.9f) else Color.Black.copy(alpha=.72f),Offset(finishX-3.5f,y),Size(7f,size.height/14f)) }
        race.horses.forEachIndexed { index,horse ->
            val pos=result?.finishOrder?.indexOf(horse.id)?.takeIf{it>=0}?:index; val p=if(running||result!=null)progress.coerceIn(0f,1f) else 0f
            val wave=sin(p*PI.toFloat()*4.8f+index*.86f)*(1f-p)*p*.15f; val stat=((horse.speed+horse.stamina)/200f-.60f)*sin(p*PI.toFloat())*.13f; val penalty=pos*.012f*p*p*p
            val norm=if(p>=.999f)1f-pos*.012f else (p+wave+stat-penalty).coerceIn(0f,.99f); val x=startX+length*norm; val y=laneH*(index+.56f); val horseColor=colors[index%colors.size]; val selected=horse.id==selectedId; val phase=p*52f+index*.71f
            if(selected){drawCircle(ShellGold.copy(alpha=.08f),laneH*.49f,Offset(x,y-laneH*.05f));drawLine(ShellGold.copy(alpha=.12f),Offset(startX,y),Offset(x-laneH*.25f,y),2f)}
            if(running&&p>.03f) repeat(3){dust-> val dx=laneH*(.45f+dust*.28f); val dy=sin(phase+dust)*laneH*.08f; drawCircle(Color(0xFFC9B88D).copy(alpha=.12f-dust*.025f),laneH*(.10f-dust*.018f),Offset(x-dx,y+dy)) }
            drawRaceHorse(Offset(x,y),laneH*.31f,horseColor,selected,phase)
            if(result!=null&&p>=.999f) drawContext.canvas.nativeCanvas.drawText("#${pos+1}",x,y-laneH*.31f,AndroidPaint().apply{this.color=if(pos==0)android.graphics.Color.rgb(244,201,93) else android.graphics.Color.argb(180,255,255,255);textAlign=AndroidPaint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD;textSize=laneH*.22f})
        }
    }
}

private fun DrawScope.drawRaceHorse(center: Offset, scale: Float, color: Color, selected: Boolean, phase: Float) {
    val body=Offset(center.x,center.y-scale*.10f); val dark=color.copy(alpha=.78f); val a=sin(phase); val b=sin(phase+PI.toFloat())
    drawOval(dark,Offset(body.x-scale*.70f,body.y-scale*.26f),Size(scale*1.18f,scale*.52f)); drawLine(color,Offset(body.x+scale*.36f,body.y-scale*.12f),Offset(body.x+scale*.63f,body.y-scale*.47f),scale*.18f); drawOval(color,Offset(body.x+scale*.48f,body.y-scale*.63f),Size(scale*.42f,scale*.30f)); drawLine(color.copy(alpha=.65f),Offset(body.x-scale*.60f,body.y-scale*.08f),Offset(body.x-scale*.91f,body.y-scale*.36f),scale*.10f)
    fun leg(root:Offset,swing:Float){ val knee=Offset(root.x+scale*.22f*swing,root.y+scale*.42f); val hoof=Offset(knee.x+scale*.25f*swing,center.y+scale*.55f-abs(swing)*scale*.12f); drawLine(dark,root,knee,scale*.11f); drawLine(color,knee,hoof,scale*.09f); drawLine(color,hoof,Offset(hoof.x+scale*.15f,hoof.y),scale*.07f) }
    val rear=Offset(body.x-scale*.42f,body.y+scale*.16f); val front=Offset(body.x+scale*.30f,body.y+scale*.14f); leg(rear,a);leg(Offset(rear.x+scale*.19f,rear.y),b);leg(front,b);leg(Offset(front.x+scale*.18f,front.y),a)
    val saddle=Offset(body.x-scale*.03f,body.y-scale*.28f); drawOval(Color(0xFF1F2937),Offset(saddle.x-scale*.22f,saddle.y-scale*.06f),Size(scale*.44f,scale*.16f)); val rider=Offset(saddle.x+scale*.02f,saddle.y-scale*.28f); drawLine(ShellCyan,saddle,rider,scale*.15f); drawCircle(Color(0xFFE8B58B),scale*.12f,Offset(rider.x+scale*.10f,rider.y-scale*.17f)); drawLine(ShellCyan,rider,Offset(body.x+scale*.47f,body.y-scale*.25f),scale*.075f)
    if(selected) drawCircle(ShellGold.copy(alpha=.75f),scale*1.12f,center,style=Stroke(scale*.075f))
}
