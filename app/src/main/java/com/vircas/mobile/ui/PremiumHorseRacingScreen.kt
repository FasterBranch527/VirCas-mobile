package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRaceResult
import com.vircas.mobile.game.engines.HorseRacingEngine
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PremiumHorseRacingGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
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
        message = if (won) "${picked.name.uppercase()} WINS · ${picked.odds}x" else "Winner: ${winner.name} · your pick finished #${final.finishOrder.indexOf(selectedId) + 1}"
    }

    fun leave() {
        if (wager != null && result != null) settle("Settled on exit")
        else wager?.let(viewModel::cancelWager)
        wager = null
        running = false
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    LaunchedEffect(trigger) {
        if (!running || result == null) return@LaunchedEffect
        raceProgress.snapTo(0f)
        raceProgress.animateTo(1f, tween(6200, easing = LinearEasing))
        delay(250)
        if (running) settle()
    }

    PremiumGameFrame("Horse Racing", "8-runner local simulation · result fixed before race", balance, ShellGold, if (wager != null) ::leave else onBack) { compact, landscape ->
        val track: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF0C1713),
                border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.18f))
            ) {
                HorseTrack(
                    race = race,
                    result = result,
                    progress = raceProgress.value,
                    selectedId = selectedId,
                    running = running,
                    modifier = Modifier.fillMaxSize().padding(if (compact) 6.dp else 9.dp)
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
                                        color = if (selected) ShellGold.copy(alpha = 0.16f) else ShellPanel,
                                        border = BorderStroke(1.dp, if (selected) ShellGold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("#${race.horses.indexOf(horse) + 1}", color = if (selected) ShellGold else Color.White.copy(alpha = 0.38f), fontWeight = FontWeight.Black, fontSize = 9.sp)
                                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                                Text(horse.name, maxLines = 1, fontWeight = FontWeight.Black, fontSize = 9.sp)
                                                Text("SPD ${horse.speed} · STA ${horse.stamina}", fontSize = 7.sp, color = Color.White.copy(alpha = 0.32f))
                                            }
                                            Text("${horse.odds}x", color = ShellCyan, fontWeight = FontWeight.Black, fontSize = 9.sp)
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
                            message = "New field generated. Pick a runner."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("NEW RACE", fontWeight = FontWeight.Black) }
                } else {
                    val picked = selectedHorse()
                    Surface(shape = RoundedCornerShape(16.dp), color = ShellPanel2) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("YOUR PICK", fontSize = 9.sp, color = Color.White.copy(alpha = 0.38f))
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
                Box(Modifier.weight(1.3f).fillMaxSize()) { track() }
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.15f)) { track() }
                Box(Modifier.weight(0.85f), contentAlignment = Alignment.BottomCenter) { controls() }
            }
        }
    }
}

@Composable
private fun HorseTrack(
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
        val startX = size.width * 0.08f
        val finishX = size.width * 0.90f
        val trackLength = finishX - startX

        drawRect(Color(0xFF10231A), size = size)
        for (lane in race.horses.indices) {
            val y0 = lane * laneHeight
            if (lane % 2 == 0) drawRect(Color.White.copy(alpha = 0.018f), Offset(0f, y0), Size(size.width, laneHeight))
            drawLine(Color.White.copy(alpha = 0.075f), Offset(0f, y0 + laneHeight), Offset(size.width, y0 + laneHeight), 1.4f)
        }
        drawLine(Color.White.copy(alpha = 0.15f), Offset(startX, 0f), Offset(startX, size.height), 2f)
        repeat(10) { block ->
            val y0 = block * size.height / 10f
            drawRect(if (block % 2 == 0) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.55f), Offset(finishX - 3f, y0), Size(6f, size.height / 10f))
        }

        race.horses.forEachIndexed { index, horse ->
            val finishPos = result?.finishOrder?.indexOf(horse.id)?.takeIf { it >= 0 } ?: index
            val p = if (running || result != null) progress.coerceIn(0f, 1f) else 0f
            val wave = sin((p * PI * 4.0 + index * 0.9)).toFloat() * (1f - p) * p * 0.16f
            val statBias = ((horse.speed + horse.stamina) / 200f - 0.6f) * sin((p * PI).toFloat()) * 0.12f
            val finishPenalty = finishPos * 0.012f * p * p * p
            val normalized = if (p >= 0.999f) (1f - finishPos * 0.012f) else (p + wave + statBias - finishPenalty).coerceIn(0f, 0.99f)
            val x = startX + trackLength * normalized
            val y = laneHeight * (index + 0.5f)
            val color = horseColors[index % horseColors.size]
            val selected = horse.id == selectedId
            if (selected) drawCircle(ShellGold.copy(alpha = 0.11f), laneHeight * 0.42f, Offset(x, y))
            drawHorseMarker(Offset(x, y), laneHeight * 0.26f, color, selected)
            if (running && p > 0.05f) {
                drawLine(color.copy(alpha = 0.16f), Offset((x - laneHeight * 0.9f).coerceAtLeast(startX), y), Offset(x - laneHeight * 0.32f, y), strokeWidth = 2f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHorseMarker(
    center: Offset,
    scale: Float,
    color: Color,
    selected: Boolean
) {
    val body = Size(scale * 1.25f, scale * 0.58f)
    drawOval(color.copy(alpha = if (selected) 1f else 0.86f), Offset(center.x - body.width * 0.48f, center.y - body.height * 0.45f), body)
    drawCircle(color, scale * 0.24f, Offset(center.x + scale * 0.63f, center.y - scale * 0.30f))
    drawLine(color, Offset(center.x + scale * 0.42f, center.y - scale * 0.16f), Offset(center.x + scale * 0.58f, center.y - scale * 0.31f), strokeWidth = scale * 0.12f)
    drawLine(color, Offset(center.x - scale * 0.27f, center.y + scale * 0.14f), Offset(center.x - scale * 0.42f, center.y + scale * 0.48f), strokeWidth = scale * 0.10f)
    drawLine(color, Offset(center.x + scale * 0.22f, center.y + scale * 0.14f), Offset(center.x + scale * 0.38f, center.y + scale * 0.48f), strokeWidth = scale * 0.10f)
    drawLine(color.copy(alpha = 0.75f), Offset(center.x - scale * 0.57f, center.y - scale * 0.12f), Offset(center.x - scale * 0.86f, center.y - scale * 0.35f), strokeWidth = scale * 0.08f)
    if (selected) drawCircle(ShellGold, scale * 0.88f, center, style = Stroke(width = scale * 0.08f))
}
