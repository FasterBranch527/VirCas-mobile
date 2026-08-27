package com.vircas.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val rouletteWheelOrder = listOf(
    0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
    5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
)

private val rouletteChipValues = listOf(10L, 50L, 100L, 500L, 1_000L, 5_000L)

private data class RoulettePendingRound(
    val wager: ActiveWager,
    val number: Int,
    val color: RouletteColor,
    val totalStake: Long,
    val payout: Long,
    val payoutMultiplier: Double,
    val bets: Map<RouletteBet, List<Long>>
)

@Composable
fun RealisticRouletteGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val wheelRotation = remember { Animatable(0f) }
    val ballRotation = remember { Animatable(-72f) }

    var selectedChip by remember { mutableStateOf(100L) }
    var bets by remember { mutableStateOf<Map<RouletteBet, List<Long>>>(emptyMap()) }
    var previousBets by remember { mutableStateOf<Map<RouletteBet, List<Long>>>(emptyMap()) }
    var pending by remember { mutableStateOf<RoulettePendingRound?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var spinSerial by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf("Tap a cell to place a chip · hold it to remove the last chip") }

    val totalBet = bets.values.sumOf { it.sum() }

    fun addChip(bet: RouletteBet) {
        if (spinning) return
        if (totalBet + selectedChip > balance) {
            status = "Not enough virtual balance for another ${formatVc(selectedChip)} chip."
            return
        }
        val updated = bets.toMutableMap()
        updated[bet] = updated[bet].orEmpty() + selectedChip
        bets = updated
        status = "${formatBetName(bet)} · ${formatVc(updated[bet].orEmpty().sum())}"
    }

    fun removeChip(bet: RouletteBet) {
        if (spinning) return
        val stack = bets[bet].orEmpty()
        if (stack.isEmpty()) return
        val updated = bets.toMutableMap()
        val next = stack.dropLast(1)
        if (next.isEmpty()) updated.remove(bet) else updated[bet] = next
        bets = updated
        status = if (next.isEmpty()) "Removed ${formatBetName(bet)}" else "${formatBetName(bet)} · ${formatVc(next.sum())}"
    }

    fun settleAndLeave() {
        val round = pending
        if (round != null) {
            pending = null
            spinning = false
            viewModel.settleWager(
                wager = round.wager,
                multiplier = round.payoutMultiplier,
                result = "${round.number} ${round.color.name}",
                details = rouletteBetSummary(round.bets)
            )
        }
        onBack()
    }

    BackHandler(enabled = spinning, onBack = ::settleAndLeave)

    LaunchedEffect(spinSerial) {
        val round = pending ?: return@LaunchedEffect
        if (!spinning) return@LaunchedEffect

        val index = rouletteWheelOrder.indexOf(round.number).coerceAtLeast(0)
        val sweep = 360f / rouletteWheelOrder.size
        val duration = if (!settings.animations || settings.reducedMotion) 650 else 4_400
        val finalWheel = if (duration < 1_000) 360f else 2_020f + ((round.number * 17) % 130)
        val pocketAngle = -90f + index * sweep + sweep / 2f + (finalWheel % 360f)
        val finalBall = pocketAngle - if (duration < 1_000) 360f else 2_520f

        wheelRotation.snapTo(0f)
        ballRotation.snapTo(-62f)
        coroutineScope {
            launch {
                wheelRotation.animateTo(
                    targetValue = finalWheel,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                )
            }
            launch {
                ballRotation.animateTo(
                    targetValue = finalBall,
                    animationSpec = tween(durationMillis = duration, easing = LinearOutSlowInEasing)
                )
            }
        }

        if (pending?.wager?.id == round.wager.id) {
            lastResult = round.number
            status = if (round.payout > 0L) {
                "${round.number} ${round.color.name} · WIN ${formatVc(round.payout)}"
            } else {
                "${round.number} ${round.color.name} · no winning chips"
            }
            pending = null
            spinning = false
            viewModel.settleWager(
                wager = round.wager,
                multiplier = round.payoutMultiplier,
                result = "${round.number} ${round.color.name}",
                details = rouletteBetSummary(round.bets)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B09), Color(0xFF10251A), Color(0xFF07110C))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = if (spinning) ::settleAndLeave else onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color(0xFFF3D8A1))
            }
            Column(Modifier.weight(1f)) {
                Text("EUROPEAN ROULETTE", color = Color(0xFFFFE4AF), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("0–36 · local virtual table", color = Color(0xFF92A99B), fontSize = 11.sp)
            }
            RouletteHeaderStat("BALANCE", formatVc(balance))
            Spacer(Modifier.width(8.dp))
            RouletteHeaderStat("ON TABLE", formatVc(totalBet))
        }

        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            if (maxWidth >= 760.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    RouletteWheelPanel(
                        wheelRotation = wheelRotation.value,
                        ballRotation = ballRotation.value,
                        spinning = spinning,
                        result = lastResult,
                        modifier = Modifier.width(330.dp)
                    )
                    RouletteBettingTable(
                        bets = bets,
                        result = lastResult,
                        enabled = !spinning,
                        onAdd = ::addChip,
                        onRemove = ::removeChip,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RouletteWheelPanel(
                        wheelRotation = wheelRotation.value,
                        ballRotation = ballRotation.value,
                        spinning = spinning,
                        result = lastResult,
                        modifier = Modifier.width(min(maxWidth.value - 16f, 330f).dp)
                    )
                    RouletteBettingTable(
                        bets = bets,
                        result = lastResult,
                        enabled = !spinning,
                        onAdd = ::addChip,
                        onRemove = ::removeChip,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF17120D),
            border = BorderStroke(1.dp, Color(0xFF6F542D))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CHIP VALUE", color = Color(0xFFBDAA88), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rouletteChipValues.forEach { value ->
                        ChipToken(
                            value = value,
                            selected = selectedChip == value,
                            modifier = Modifier.size(if (selectedChip == value) 58.dp else 52.dp),
                            onClick = { if (!spinning) selectedChip = value }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (!spinning) {
                                bets = emptyMap()
                                status = "Table cleared."
                            }
                        },
                        enabled = !spinning && bets.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("CLEAR") }

                    OutlinedButton(
                        onClick = {
                            if (!spinning && previousBets.isNotEmpty()) {
                                val previousTotal = previousBets.values.sumOf { it.sum() }
                                if (previousTotal <= balance) {
                                    bets = previousBets
                                    status = "Repeated ${formatVc(previousTotal)} layout."
                                } else status = "Previous layout is larger than the current balance."
                            }
                        },
                        enabled = !spinning && previousBets.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("REPEAT") }

                    Button(
                        onClick = {
                            if (spinning) return@Button
                            val frozenBets = bets.mapValues { (_, stack) -> stack.toList() }
                            val stake = frozenBets.values.sumOf { it.sum() }
                            if (stake <= 0L) {
                                status = "Place at least one chip first."
                                return@Button
                            }
                            if (stake > balance) {
                                status = "Total chips exceed your virtual balance."
                                return@Button
                            }

                            status = "NO MORE BETS · result locked before animation"
                            lastResult = null
                            viewModel.beginWager("Roulette", stake) { wager ->
                                if (wager == null) {
                                    status = "Could not start the spin: check the virtual balance."
                                    return@beginWager
                                }

                                val random = viewModel.randomProvider()
                                val engine = RouletteEngine(random)
                                val number = random.nextInt(0, 37)
                                val color = RouletteEngine.colorOf(number)
                                val payout = frozenBets.entries.sumOf { (bet, chips) ->
                                    val amount = chips.sum()
                                    val resolved = engine.resolve(number, bet)
                                    if (resolved.won) (amount * resolved.payoutMultiplier).toLong() else 0L
                                }
                                val multiplier = if (payout == 0L) 0.0 else Math.nextUp(payout.toDouble() / stake.toDouble())

                                previousBets = frozenBets
                                pending = RoulettePendingRound(
                                    wager = wager,
                                    number = number,
                                    color = color,
                                    totalStake = stake,
                                    payout = payout,
                                    payoutMultiplier = multiplier,
                                    bets = frozenBets
                                )
                                spinning = true
                                spinSerial++
                            }
                        },
                        enabled = !spinning && totalBet > 0L,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D7C3E), contentColor = Color.White),
                        modifier = Modifier.weight(1.25f)
                    ) { Text(if (spinning) "SPINNING…" else "SPIN", fontWeight = FontWeight.Black) }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xAA0B1510),
            border = BorderStroke(1.dp, Color(0xFF2D4B39))
        ) {
            Text(status, Modifier.padding(14.dp), color = Color(0xFFD8E6DC), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RouletteHeaderStat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF151C17), border = BorderStroke(1.dp, Color(0xFF35463A))) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
            Text(label, color = Color(0xFF829489), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFFFFE3AA), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RouletteWheelPanel(
    wheelRotation: Float,
    ballRotation: Float,
    spinning: Boolean,
    result: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF24160C),
        border = BorderStroke(2.dp, Color(0xFF7D5B2B))
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RealRouletteWheel(
                wheelRotation = wheelRotation,
                ballRotation = ballRotation,
                spinning = spinning,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF0D2317), border = BorderStroke(1.dp, Color(0xFF42684F))) {
                Text(
                    when {
                        spinning -> "BALL IN MOTION"
                        result == null -> "PLACE YOUR BETS"
                        else -> "$result · ${RouletteEngine.colorOf(result).name}"
                    },
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = if (result == 0) Color(0xFF5AD27A) else Color(0xFFF4DFB1),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun RealRouletteWheel(
    wheelRotation: Float,
    ballRotation: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.47f
        val pocketRadius = radius * 0.78f
        val innerRadius = radius * 0.51f
        val sweep = 360f / rouletteWheelOrder.size

        drawCircle(Color(0xFF110C08), radius = radius * 1.04f, center = center)
        drawCircle(Color(0xFFD1A64A), radius = radius, center = center, style = Stroke(radius * 0.035f))
        drawCircle(Color(0xFF6D3518), radius = radius * 0.95f, center = center)
        drawCircle(Color(0xFF2C160D), radius = radius * 0.87f, center = center)

        rotate(wheelRotation, center) {
            rouletteWheelOrder.forEachIndexed { index, number ->
                val start = -90f + index * sweep
                val pocketColor = when (RouletteEngine.colorOf(number)) {
                    RouletteColor.RED -> Color(0xFFC52B27)
                    RouletteColor.BLACK -> Color(0xFF151816)
                    RouletteColor.GREEN -> Color(0xFF16843C)
                }
                drawArc(
                    color = pocketColor,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - pocketRadius, center.y - pocketRadius),
                    size = Size(pocketRadius * 2f, pocketRadius * 2f)
                )
                drawArc(
                    color = Color(0xFFD8C18A),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - pocketRadius, center.y - pocketRadius),
                    size = Size(pocketRadius * 2f, pocketRadius * 2f),
                    style = Stroke(width = 1.2f)
                )
            }

            drawCircle(Color(0xFF4C2513), radius = innerRadius, center = center)
            drawCircle(Color(0xFFBE8732), radius = innerRadius, center = center, style = Stroke(radius * 0.025f))
            drawCircle(Color(0xFF0B4F2A), radius = radius * 0.28f, center = center)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = radius * 0.075f
            }
            drawIntoCanvas { canvas ->
                rouletteWheelOrder.forEachIndexed { index, number ->
                    val angle = (-90f + index * sweep + sweep / 2f) * PI.toFloat() / 180f
                    val x = center.x + cos(angle) * radius * 0.69f
                    val y = center.y + sin(angle) * radius * 0.69f - (paint.ascent() + paint.descent()) / 2f
                    canvas.nativeCanvas.drawText(number.toString(), x, y, paint)
                }
            }
        }

        drawCircle(Color(0xFFE0B65A), radius = radius * 0.105f, center = center)
        drawCircle(Color(0xFF6F4517), radius = radius * 0.065f, center = center)
        repeat(4) { index ->
            val a = index * 90f * PI.toFloat() / 180f
            val end = Offset(center.x + cos(a) * radius * 0.31f, center.y + sin(a) * radius * 0.31f)
            drawLine(Color(0xFFDDB45B), center, end, strokeWidth = radius * 0.026f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFE7C36F), radius * 0.032f, end)
        }

        fun ballOffset(angleDegrees: Float, radial: Float): Offset {
            val a = angleDegrees * PI.toFloat() / 180f
            return Offset(center.x + cos(a) * radial, center.y + sin(a) * radial)
        }

        if (spinning) {
            listOf(18f to 0.16f, 11f to 0.28f, 6f to 0.42f).forEach { (trail, alpha) ->
                drawCircle(Color.White.copy(alpha = alpha), radius * 0.027f, ballOffset(ballRotation + trail, radius * 0.88f))
            }
        }
        val ball = ballOffset(ballRotation, radius * 0.88f)
        drawCircle(Color(0x55000000), radius * 0.037f, ball + Offset(radius * 0.012f, radius * 0.012f))
        drawCircle(Color(0xFFF5F0E4), radius * 0.031f, ball)
        drawCircle(Color.White, radius * 0.015f, ball + Offset(-radius * 0.008f, -radius * 0.008f))
    }
}

@Composable
private fun RouletteBettingTable(
    bets: Map<RouletteBet, List<Long>>,
    result: Int?,
    enabled: Boolean,
    onAdd: (RouletteBet) -> Unit,
    onRemove: (RouletteBet) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val cellWidth = 58.dp
    val cellHeight = 52.dp
    val zeroWidth = 64.dp
    val columnWidth = 58.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0E4A2B),
        border = BorderStroke(2.dp, Color(0xFFBFA66C))
    ) {
        Column(
            Modifier.horizontalScroll(scroll).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                RouletteBetCell(
                    label = "0",
                    bet = RouletteBet.Number(0),
                    chips = bets[RouletteBet.Number(0)].orEmpty(),
                    baseColor = Color(0xFF137437),
                    winning = result == 0,
                    enabled = enabled,
                    width = zeroWidth,
                    height = cellHeight * 3 + 4.dp,
                    onAdd = onAdd,
                    onRemove = onRemove
                )

                Row {
                    (0 until 12).forEach { group ->
                        Column {
                            listOf(group * 3 + 3, group * 3 + 2, group * 3 + 1).forEach { number ->
                                val bet = RouletteBet.Number(number)
                                RouletteBetCell(
                                    label = number.toString(),
                                    bet = bet,
                                    chips = bets[bet].orEmpty(),
                                    baseColor = rouletteNumberColor(number),
                                    winning = result == number,
                                    enabled = enabled,
                                    width = cellWidth,
                                    height = cellHeight,
                                    onAdd = onAdd,
                                    onRemove = onRemove
                                )
                            }
                        }
                    }
                }

                Column {
                    listOf(3, 2, 1).forEach { index ->
                        val bet = RouletteBet.Column(index)
                        RouletteBetCell(
                            label = "2 TO 1",
                            bet = bet,
                            chips = bets[bet].orEmpty(),
                            baseColor = Color(0xFF135D35),
                            winning = result?.let { rouletteBetWon(it, bet) } == true,
                            enabled = enabled,
                            width = columnWidth,
                            height = cellHeight,
                            onAdd = onAdd,
                            onRemove = onRemove,
                            smallLabel = true
                        )
                    }
                }
            }

            Row {
                Spacer(Modifier.width(zeroWidth))
                (1..3).forEach { index ->
                    val bet = RouletteBet.Dozen(index)
                    RouletteBetCell(
                        label = when (index) { 1 -> "1ST 12"; 2 -> "2ND 12"; else -> "3RD 12" },
                        bet = bet,
                        chips = bets[bet].orEmpty(),
                        baseColor = Color(0xFF135D35),
                        winning = result?.let { rouletteBetWon(it, bet) } == true,
                        enabled = enabled,
                        width = cellWidth * 4,
                        height = 48.dp,
                        onAdd = onAdd,
                        onRemove = onRemove
                    )
                }
                Spacer(Modifier.width(columnWidth))
            }

            Row {
                Spacer(Modifier.width(zeroWidth))
                val outside = listOf(
                    Triple("1–18", RouletteBet.Low as RouletteBet, Color(0xFF135D35)),
                    Triple("EVEN", RouletteBet.Even as RouletteBet, Color(0xFF135D35)),
                    Triple("RED", RouletteBet.Color(RouletteColor.RED) as RouletteBet, Color(0xFFC52B27)),
                    Triple("BLACK", RouletteBet.Color(RouletteColor.BLACK) as RouletteBet, Color(0xFF171A18)),
                    Triple("ODD", RouletteBet.Odd as RouletteBet, Color(0xFF135D35)),
                    Triple("19–36", RouletteBet.High as RouletteBet, Color(0xFF135D35))
                )
                outside.forEach { (label, bet, color) ->
                    RouletteBetCell(
                        label = label,
                        bet = bet,
                        chips = bets[bet].orEmpty(),
                        baseColor = color,
                        winning = result?.let { rouletteBetWon(it, bet) } == true,
                        enabled = enabled,
                        width = cellWidth * 2,
                        height = 48.dp,
                        onAdd = onAdd,
                        onRemove = onRemove
                    )
                }
                Spacer(Modifier.width(columnWidth))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouletteBetCell(
    label: String,
    bet: RouletteBet,
    chips: List<Long>,
    baseColor: Color,
    winning: Boolean,
    enabled: Boolean,
    width: Dp,
    height: Dp,
    onAdd: (RouletteBet) -> Unit,
    onRemove: (RouletteBet) -> Unit,
    smallLabel: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(1.dp)
            .background(
                color = if (winning) Color(0xFF9A7627) else baseColor,
                shape = RoundedCornerShape(3.dp)
            )
            .combinedClickable(
                enabled = enabled,
                onClick = { onAdd(bet) },
                onLongClick = { onRemove(bet) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFFF8F0DA),
            fontSize = if (smallLabel) 8.sp else 15.sp,
            fontWeight = FontWeight.Black
        )
        if (chips.isNotEmpty()) {
            PlacedChipStack(chips, Modifier.align(Alignment.Center))
        }
        if (winning) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                shape = CircleShape,
                color = Color(0xFFF5D76E)
            ) { Spacer(Modifier.size(7.dp)) }
        }
    }
}

@Composable
private fun PlacedChipStack(chips: List<Long>, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        val visible = chips.takeLast(4)
        visible.forEachIndexed { index, value ->
            SmallChipToken(
                value = value,
                modifier = Modifier
                    .size(31.dp)
                    .offset(y = (-index * 3).dp)
                    .zIndex(index.toFloat())
            )
        }
        if (chips.size > 1) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).zIndex(10f),
                shape = CircleShape,
                color = Color(0xEE07100B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
            ) {
                Text("×${chips.size}", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ChipToken(value: Long, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = chipColor(value)
            drawCircle(Color(0x77000000), radius = size.minDimension * 0.47f, center = center + Offset(2f, 3f))
            drawCircle(c, radius = size.minDimension * 0.46f)
            drawCircle(Color(0xFFF2E7D1), radius = size.minDimension * 0.40f, style = Stroke(width = size.minDimension * 0.055f))
            repeat(12) { index ->
                val angle = index * 30f * PI.toFloat() / 180f
                val r1 = size.minDimension * 0.35f
                val r2 = size.minDimension * 0.44f
                drawLine(
                    Color.White.copy(alpha = 0.8f),
                    Offset(center.x + cos(angle) * r1, center.y + sin(angle) * r1),
                    Offset(center.x + cos(angle) * r2, center.y + sin(angle) * r2),
                    strokeWidth = size.minDimension * 0.045f,
                    cap = StrokeCap.Round
                )
            }
            if (selected) drawCircle(Color(0xFFFFD66A), radius = size.minDimension * 0.49f, style = Stroke(width = size.minDimension * 0.055f))
        }
        Text(shortChip(value), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun SmallChipToken(value: Long, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x99000000), radius = size.minDimension * 0.47f, center = center + Offset(1.4f, 2f))
            drawCircle(chipColor(value), radius = size.minDimension * 0.45f)
            drawCircle(Color.White.copy(alpha = 0.86f), radius = size.minDimension * 0.35f, style = Stroke(width = size.minDimension * 0.055f))
        }
        Text(shortChip(value), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
    }
}

private fun chipColor(value: Long): Color = when (value) {
    10L -> Color(0xFF64748B)
    50L -> Color(0xFFD13B32)
    100L -> Color(0xFF258C48)
    500L -> Color(0xFF25282D)
    1_000L -> Color(0xFF7441A5)
    else -> Color(0xFFB17B1D)
}

private fun rouletteNumberColor(number: Int): Color = when (RouletteEngine.colorOf(number)) {
    RouletteColor.RED -> Color(0xFFC52B27)
    RouletteColor.BLACK -> Color(0xFF171A18)
    RouletteColor.GREEN -> Color(0xFF137437)
}

private fun rouletteBetWon(number: Int, bet: RouletteBet): Boolean = when (bet) {
    is RouletteBet.Number -> number == bet.number
    is RouletteBet.Color -> number != 0 && RouletteEngine.colorOf(number) == bet.color
    RouletteBet.Odd -> number != 0 && number % 2 == 1
    RouletteBet.Even -> number != 0 && number % 2 == 0
    RouletteBet.Low -> number in 1..18
    RouletteBet.High -> number in 19..36
    is RouletteBet.Dozen -> number in ((bet.index - 1) * 12 + 1)..(bet.index * 12)
    is RouletteBet.Column -> number != 0 && ((number - 1) % 3) + 1 == bet.index
}

private fun rouletteBetSummary(bets: Map<RouletteBet, List<Long>>): String = bets.entries
    .sortedByDescending { it.value.sum() }
    .joinToString(" · ") { (bet, chips) -> "${formatBetName(bet)} ${formatVc(chips.sum())}" }

private fun formatBetName(bet: RouletteBet): String = when (bet) {
    is RouletteBet.Number -> "#${bet.number}"
    is RouletteBet.Color -> bet.color.name
    RouletteBet.Odd -> "ODD"
    RouletteBet.Even -> "EVEN"
    RouletteBet.Low -> "1–18"
    RouletteBet.High -> "19–36"
    is RouletteBet.Dozen -> "${bet.index}${when (bet.index) { 1 -> "ST"; 2 -> "ND"; else -> "RD" }} 12"
    is RouletteBet.Column -> "COLUMN ${bet.index}"
}

private fun formatVc(value: Long): String = "%,d VC".format(value)
private fun shortChip(value: Long): String = if (value >= 1_000) "${value / 1_000}K" else value.toString()
