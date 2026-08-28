package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.DiceEngine
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.PlinkoEngine
import com.vircas.mobile.game.engines.PlinkoResult
import com.vircas.mobile.game.engines.PlinkoRisk
import com.vircas.mobile.game.engines.SlotSpinResult
import com.vircas.mobile.game.engines.SlotSymbol
import com.vircas.mobile.game.engines.SlotTheme
import com.vircas.mobile.game.engines.SlotsEngine
import com.vircas.mobile.game.engines.WheelEngine
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun PremiumDiceGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var threshold by remember { mutableStateOf(60f) }
    var under by remember { mutableStateOf(true) }
    var active by remember { mutableStateOf<ActiveWager?>(null) }
    var pending by remember { mutableStateOf<DiceEngine.Result?>(null) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Set your target, then roll.") }
    val display = remember { Animatable(50f) }
    val chance = if (under) threshold.toDouble() / 100.0 else (100.0 - threshold) / 100.0
    val potential = 0.99 / chance

    fun settleAndLeave() {
        val wager = active
        val result = pending
        active = null
        pending = null
        if (wager != null && result != null) {
            viewModel.settleWager(
                wager,
                result.outcome.multiplier,
                "Rolled ${"%.2f".format(result.roll)}",
                "${if (under) "Under" else "Over"} ${threshold.roundToInt()}"
            )
        } else if (wager != null) {
            viewModel.cancelWager(wager)
        }
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::settleAndLeave)

    LaunchedEffect(trigger) {
        val result = pending ?: return@LaunchedEffect
        display.snapTo((50f + (result.roll.toFloat() - 50f) * 0.12f).coerceIn(0f, 100f))
        display.animateTo(result.roll.toFloat(), tween(1050, easing = FastOutSlowInEasing))
        delay(180)
        val wager = active ?: return@LaunchedEffect
        val won = result.outcome.multiplier > 0.0
        viewModel.settleWager(
            wager,
            result.outcome.multiplier,
            "Rolled ${"%.2f".format(result.roll)}",
            "${if (under) "Under" else "Over"} ${threshold.roundToInt()}"
        )
        message = "${"%.2f".format(result.roll)} · ${if (won) "WIN ${"%.2f".format(result.outcome.multiplier)}x" else "MISS"}"
        active = null
        pending = null
    }

    PremiumGameFrame("Dice", "0.00–99.99 · 99% RTP", balance, ShellGreen, if (active != null) ::settleAndLeave else onBack) { compact, landscape ->
        val gamePanel: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, ShellGreen.copy(alpha = 0.22f))
            ) {
                Column(
                    Modifier.padding(if (compact) 14.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${"%.2f".format(display.value)}", fontSize = if (compact) 44.sp else 58.sp, fontWeight = FontWeight.Black, color = ShellGreen)
                    DiceMeter(display.value, threshold, under, Modifier.fillMaxWidth().height(if (compact) 44.dp else 56.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0.00", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f))
                        Text("TARGET ${threshold.roundToInt()}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = ShellGold)
                        Text("99.99", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f))
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = under, enabled = active == null, onClick = { under = true }, label = { Text("ROLL UNDER") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = !under, enabled = active == null, onClick = { under = false }, label = { Text("ROLL OVER") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chance ${"%.1f".format(chance * 100)}%", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    Text("${"%.2f".format(potential)}x", fontWeight = FontWeight.Black, color = ShellGold)
                }
                Slider(value = threshold, onValueChange = { if (active == null) threshold = it }, valueRange = 5f..95f, enabled = active == null)
                QuickStakeRow(active == null, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, active == null, ShellGreen, Modifier.weight(1f)) { stake = it }
                    PremiumActionButton(if (active == null) "ROLL" else "ROLLING…", ShellGreen, active == null, Modifier.weight(0.75f)) {
                        viewModel.beginWager("Dice", premiumStake(stake)) { started ->
                            if (started == null) message = "Could not start: check stake and balance."
                            else {
                                val result = DiceEngine(viewModel.randomProvider()).roll(threshold.toDouble(), under)
                                active = started
                                pending = result
                                trigger++
                                message = "Rolling…"
                            }
                        }
                    }
                }
                PremiumMessageCard(message, ShellGreen)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.15f)) { gamePanel() }
                Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { gamePanel() }
                controls()
            }
        }
    }
}

@Composable
private fun DiceMeter(value: Float, threshold: Float, under: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height * 0.45f
        val h = size.height * 0.22f
        val radius = h / 2f
        drawRoundRect(Color.White.copy(alpha = 0.07f), topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(radius, radius))
        val thresholdX = size.width * threshold / 100f
        if (under) {
            drawRoundRect(ShellGreen.copy(alpha = 0.42f), topLeft = Offset(0f, y), size = Size(thresholdX, h), cornerRadius = CornerRadius(radius, radius))
        } else {
            drawRoundRect(ShellPurple.copy(alpha = 0.42f), topLeft = Offset(thresholdX, y), size = Size(size.width - thresholdX, h), cornerRadius = CornerRadius(radius, radius))
        }
        drawLine(ShellGold, Offset(thresholdX, y - 8f), Offset(thresholdX, y + h + 8f), strokeWidth = 3f)
        val markerX = size.width * value.coerceIn(0f, 100f) / 100f
        drawCircle(Color.White.copy(alpha = 0.18f), 11f, Offset(markerX, y + h / 2f))
        drawCircle(if (under) ShellGreen else ShellPurple, 6.5f, Offset(markerX, y + h / 2f))
    }
}

@Composable
fun PremiumWheelGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val sectors = remember { WheelEngine(viewModel.randomProvider()).sectors }
    var stake by remember { mutableStateOf("1000") }
    var active by remember { mutableStateOf<ActiveWager?>(null) }
    var pending by remember { mutableStateOf<GameOutcome?>(null) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Spin the 9-sector multiplier wheel.") }
    val rotation = remember { Animatable(0f) }

    fun outcomeName(outcome: GameOutcome): String = if (outcome is GameOutcome.Win) outcome.label else "0x"
    fun settleAndLeave() {
        val wager = active
        val outcome = pending
        active = null
        pending = null
        if (wager != null && outcome != null) viewModel.settleWager(wager, outcome.multiplier, outcomeName(outcome))
        else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::settleAndLeave)

    LaunchedEffect(trigger) {
        val outcome = pending ?: return@LaunchedEffect
        val sectorAngle = 360f / sectors.size
        val current = rotation.value
        val normalized = ((current % 360f) + 360f) % 360f
        val desired = ((-targetIndex * sectorAngle) % 360f + 360f) % 360f
        var delta = desired - normalized
        if (delta < 0f) delta += 360f
        rotation.animateTo(current + 6f * 360f + delta, tween(4200, easing = FastOutSlowInEasing))
        delay(180)
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, outcome.multiplier, outcomeName(outcome))
        message = "Landed ${outcomeName(outcome)} · ${if (outcome.multiplier > 0.0) "WIN" else "NO PAYOUT"}"
        active = null
        pending = null
    }

    PremiumGameFrame("Wheel", "9 sectors · up to 25x", balance, ShellPurple, if (active != null) ::settleAndLeave else onBack) { compact, landscape ->
        val wheel: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MultiplierWheel(rotation.value, sectors, Modifier.size(if (compact) 245.dp else 300.dp))
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    sectors.chunked(3).forEach { chunk ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            chunk.forEach { value ->
                                Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = ShellPanel2) {
                                    Text("${value}x", Modifier.padding(vertical = 6.dp), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (value >= 5.0) ShellGold else Color.White.copy(alpha = 0.72f))
                                }
                            }
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
                                message = "Wheel in motion…"
                            }
                        }
                    }
                }
                PremiumMessageCard(message, ShellPurple)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { wheel() }
                Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.Center) { controls() }
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
private fun MultiplierWheel(rotation: Float, sectors: List<Double>, modifier: Modifier = Modifier) {
    val colors = listOf(ShellPurple, ShellCyan, ShellGreen, ShellGold, Color(0xFFFF7A8A), Color(0xFF5F7CFF), Color(0xFF14B8A6), Color(0xFFFF9F43), Color(0xFFB36BFF))
    Canvas(modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        drawCircle(Color.Black.copy(alpha = 0.45f), radius * 1.06f, center)
        rotate(rotation, center) {
            val sweep = 360f / sectors.size
            sectors.indices.forEach { i ->
                drawArc(
                    color = colors[i % colors.size].copy(alpha = if (sectors[i] == 0.0) 0.34f else 0.82f),
                    startAngle = -90f - sweep / 2f + i * sweep,
                    sweepAngle = sweep - 1.2f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
            }
            drawCircle(ShellDeep, radius * 0.23f, center)
            drawCircle(Color.White.copy(alpha = 0.12f), radius * 0.16f, center)
        }
        drawCircle(Color.White.copy(alpha = 0.06f), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        val pointer = Path().apply {
            moveTo(center.x, center.y - radius - 3f)
            lineTo(center.x - 12f, center.y - radius - 25f)
            lineTo(center.x + 12f, center.y - radius - 25f)
            close()
        }
        drawPath(pointer, ShellGold)
    }
}

@Composable
fun PremiumSlotsGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
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

    fun glyph(symbol: SlotSymbol): String = when (symbol.id) {
        "CHERRY" -> "●●"
        "LEMON" -> "◒"
        "GRAPE" -> "●●●"
        "SEVEN" -> "7"
        "NOVA", "SUN" -> "✦"
        "BONUS" -> "B"
        "SCARAB" -> "◇"
        "ANKH" -> "†"
        "COBRA" -> "S"
        "PHARAOH" -> "♛"
        "CHIP" -> "▦"
        "CORE" -> "◎"
        "DRONE" -> "△"
        "VAULT" -> "▣"
        "GLITCH" -> "⌁"
        else -> symbol.id.take(1)
    }

    fun settleAndLeave() {
        val wager = active
        val result = pending
        active = null
        pending = null
        spinning = false
        autoRemaining = 0
        if (wager != null && result != null) viewModel.settleWager(wager, result.payoutMultiplier, if (result.payoutMultiplier > 0) "WIN" else "NO WIN", "${result.lineWins.size} paylines · ${result.bonusCount} bonus")
        else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::settleAndLeave)

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
                trigger++
                message = "Reels spinning…"
            }
        }
    }

    LaunchedEffect(trigger) {
        val result = pending ?: return@LaunchedEffect
        val theme = SlotsEngine.Themes[themeIndex]
        repeat(34) { tick ->
            displayed = List(3) { row ->
                List(3) { col ->
                    if (tick > 23 + col * 3) result.grid[row][col]
                    else theme.symbols[(tick + row * 2 + col * 3) % theme.symbols.size]
                }
            }
            delay((42 + tick * 2L).coerceAtMost(95L))
        }
        displayed = result.grid
        delay(180)
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, result.payoutMultiplier, if (result.payoutMultiplier > 0) "WIN" else "NO WIN", "${result.lineWins.size} paylines · ${result.bonusCount} bonus")
        message = if (result.payoutMultiplier > 0.0) "WIN ${"%.2f".format(result.payoutMultiplier)}x · ${result.lineWins.size} line(s)" else if (result.bonusCount >= 3) "BONUS ${"%.2f".format(result.payoutMultiplier)}x" else "No win"
        active = null
        pending = null
        spinning = false
        if (autoRemaining > 0) autoRemaining--
    }

    LaunchedEffect(autoRemaining, spinning, themeIndex) {
        if (autoRemaining > 0 && !spinning) {
            delay(300)
            startSpin()
        }
    }

    PremiumGameFrame("Slots", "3 machines · 5 paylines · bonus symbols", balance, ShellPurple, if (active != null) ::settleAndLeave else onBack) { compact, landscape ->
        val machine: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, ShellPurple.copy(alpha = 0.24f))
            ) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 11.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    displayed.forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { colIndex, symbol ->
                                val accent = if (symbol.bonus) ShellGold else listOf(ShellCyan, ShellPurple, ShellGreen)[colIndex]
                                Surface(
                                    Modifier.weight(1f).height(if (compact) 68.dp else 84.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = ShellPanel2,
                                    border = BorderStroke(1.dp, accent.copy(alpha = if (spinning) 0.24f else 0.42f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(glyph(symbol), fontSize = if (compact) 24.sp else 31.sp, fontWeight = FontWeight.Black, color = accent)
                                            Text(symbol.id, fontSize = 7.sp, color = Color.White.copy(alpha = 0.36f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("PAYLINES  —  —  ╲  ╱", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.28f), fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlotsEngine.Themes.forEachIndexed { index, theme ->
                        FilterChip(selected = themeIndex == index, enabled = !spinning && autoRemaining == 0, onClick = {
                            themeIndex = index
                            displayed = List(3) { List(3) { theme.symbols.first() } }
                        }, label = { Text(theme.title, fontSize = 9.sp) }, modifier = Modifier.weight(1f))
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
fun PremiumPlinkoGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var risk by remember { mutableStateOf(PlinkoRisk.MEDIUM) }
    var active by remember { mutableStateOf<ActiveWager?>(null) }
    var pending by remember { mutableStateOf<PlinkoResult?>(null) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Choose risk and drop the ball.") }
    val progress = remember { Animatable(0f) }
    val multiplierTable = remember(risk) { PlinkoEngine(viewModel.randomProvider()).multipliers(risk) }

    fun settleAndLeave() {
        val wager = active
        val result = pending
        active = null
        pending = null
        if (wager != null && result != null) viewModel.settleWager(wager, result.multiplier, "Bucket ${result.bucket}", result.path.joinToString("") { if (it) "R" else "L" })
        else if (wager != null) viewModel.cancelWager(wager)
        onBack()
    }
    BackHandler(enabled = active != null, onBack = ::settleAndLeave)

    LaunchedEffect(trigger) {
        val result = pending ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2700, easing = LinearOutSlowInEasing))
        delay(170)
        val wager = active ?: return@LaunchedEffect
        viewModel.settleWager(wager, result.multiplier, "Bucket ${result.bucket}", result.path.joinToString("") { if (it) "R" else "L" })
        message = "Bucket ${result.bucket} · ${"%.2f".format(result.multiplier)}x"
        active = null
        pending = null
    }

    PremiumGameFrame("Plinko", "12 rows · deterministic path before animation", balance, ShellCyan, if (active != null) ::settleAndLeave else onBack) { compact, landscape ->
        val board: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, ShellCyan.copy(alpha = 0.22f))
            ) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlinkoBoard(pending?.path, progress.value, risk, Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1.08f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("EDGE ${multiplierTable.first()}x", fontSize = 8.sp, fontWeight = FontWeight.Black, color = ShellGold)
                        Text("CENTER ${multiplierTable[6]}x", fontSize = 8.sp, color = Color.White.copy(alpha = 0.42f))
                        Text("${multiplierTable.last()}x EDGE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = ShellGold)
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlinkoRisk.entries.forEach { item ->
                        FilterChip(selected = risk == item, enabled = active == null, onClick = { risk = item }, label = { Text(item.name) }, modifier = Modifier.weight(1f))
                    }
                }
                QuickStakeRow(active == null, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, active == null, ShellCyan, Modifier.weight(1f)) { stake = it }
                    PremiumActionButton(if (active == null) "DROP" else "DROPPING…", ShellCyan, active == null, Modifier.weight(0.72f)) {
                        viewModel.beginWager("Plinko", premiumStake(stake)) { started ->
                            if (started == null) message = "Could not start: check stake and balance."
                            else {
                                val result = PlinkoEngine(viewModel.randomProvider()).drop(risk)
                                active = started
                                pending = result
                                trigger++
                                message = "Ball in play…"
                            }
                        }
                    }
                }
                PremiumMessageCard(message, ShellCyan)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) { board() }
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.Center) { controls() }
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
private fun PlinkoBoard(path: List<Boolean>?, progress: Float, risk: PlinkoRisk, modifier: Modifier = Modifier) {
    val accent = when (risk) {
        PlinkoRisk.LOW -> ShellGreen
        PlinkoRisk.MEDIUM -> ShellCyan
        PlinkoRisk.HIGH -> ShellRed
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (row in 0 until 12) {
            val count = row + 1
            val spacing = w * 0.07f
            val start = w / 2f - (count - 1) * spacing / 2f
            val y = h * (0.10f + row * 0.061f)
            repeat(count) { col ->
                val p = Offset(start + col * spacing, y)
                drawCircle(Color.White.copy(alpha = 0.11f), 5.2f, p)
                drawCircle(Color.White.copy(alpha = 0.48f), 2.1f, p)
            }
        }
        val bucketY = h * 0.87f
        repeat(13) { i ->
            val x0 = w * (0.045f + i * 0.07f)
            val color = if (i == 0 || i == 12) ShellGold else if (i in 5..7) ShellRed.copy(alpha = 0.55f) else accent.copy(alpha = 0.5f)
            drawRoundRect(color, Offset(x0, bucketY), Size(w * 0.055f, h * 0.055f), CornerRadius(5f, 5f))
        }
        if (path != null) {
            val stepFloat = (progress.coerceIn(0f, 1f) * 12f)
            val completed = floor(stepFloat).toInt().coerceIn(0, 12)
            val fraction = stepFloat - completed
            var x = 0.5f
            repeat(completed) { i -> x += if (path[i]) 0.035f else -0.035f }
            if (completed < 12) x += (if (path[completed]) 0.035f else -0.035f) * fraction
            val y = 0.06f + stepFloat / 12f * 0.79f
            val center = Offset(w * x, h * y)
            drawCircle(accent.copy(alpha = 0.16f), 18f, center)
            drawCircle(accent, 9f, center)
            drawCircle(Color.White.copy(alpha = 0.65f), 3f, Offset(center.x - 2.5f, center.y - 2.5f))
        } else {
            drawCircle(accent.copy(alpha = 0.75f), 8f, Offset(w / 2f, h * 0.055f))
        }
    }
}
