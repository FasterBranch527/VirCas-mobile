package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRaceResult
import com.vircas.mobile.game.engines.HorseRacingEngine
import kotlinx.coroutines.delay

@Composable
fun HorseRacingGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val catalogEngine = remember { HorseRacingEngine(viewModel.randomProvider()) }
    var race by remember { mutableStateOf(catalogEngine.generateRace(count = 8)) }
    var selectedId by remember { mutableStateOf(race.horses.first().id) }
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var result by remember { mutableStateOf<HorseRaceResult?>(null) }
    var running by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Pick a fictional horse. Result is fixed before the race animation.") }

    fun settleCurrent(reason: String = "Race finished") {
        val active = wager ?: return
        val final = result ?: return
        val picked = race.horses.firstOrNull { it.id == selectedId } ?: return
        val won = final.winnerId == selectedId
        wager = null
        running = false
        viewModel.settleWager(
            active,
            if (won) picked.odds else 0.0,
            if (won) "${picked.name} won" else "${picked.name} lost",
            "$reason · ${final.finishOrder.joinToString()}"
        )
        message = if (won) "${picked.name} WINS · ${picked.odds}x" else "Winner: ${race.horses.first { it.id == final.winnerId }.name}"
    }

    fun leave() {
        if (wager != null && result != null) settleCurrent("Settled on exit")
        onBack()
    }
    BackHandler(onBack = ::leave)

    LaunchedEffect(running, result?.raceId) {
        if (!running || result == null) return@LaunchedEffect
        repeat(80) { step ->
            if (!running) return@LaunchedEffect
            frame = step
            delay(35)
        }
        if (running) settleCurrent()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::leave) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Horse Racing", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("6–10 horse local simulation", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Text("%,d VC".format(balance), fontWeight = FontWeight.Bold)
        }

        race.horses.forEach { horse ->
            HorseRaceRow(
                horse = horse,
                selected = horse.id == selectedId,
                running = running,
                frame = frame,
                finishPosition = result?.finishOrder?.indexOf(horse.id) ?: -1,
                onSelect = { if (!running) selectedId = horse.id }
            )
        }

        OutlinedTextField(
            value = stake,
            onValueChange = { next -> if (next.all(Char::isDigit)) stake = next.take(9) },
            enabled = !running && wager == null,
            singleLine = true,
            label = { Text("Stake (VC)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (!running && wager == null) {
            Button(onClick = {
                val amount = stake.toLongOrNull()?.takeIf { it > 0 } ?: 0L
                viewModel.beginWager("Horse Racing", amount) { started ->
                    if (started == null) message = "Could not start: check stake and balance."
                    else {
                        wager = started
                        // beginWager has already installed the provider derived from the round seed.
                        result = HorseRacingEngine(viewModel.randomProvider()).simulate(race)
                        frame = 0
                        running = true
                        message = "RACE LIVE"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("START RACE") }
        }

        if (!running && result != null && wager == null) {
            OutlinedButton(onClick = {
                race = catalogEngine.generateRace(id = "race_${System.currentTimeMillis()}", count = 8)
                selectedId = race.horses.first().id
                result = null
                frame = 0
                message = "New field generated. Pick a horse."
            }, modifier = Modifier.fillMaxWidth()) { Text("NEW RACE") }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(message, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HorseRaceRow(
    horse: Horse,
    selected: Boolean,
    running: Boolean,
    frame: Int,
    finishPosition: Int,
    onSelect: () -> Unit
) {
    val timeProgress = (frame / 79f).coerceIn(0f, 1f)
    val finishBias = if (finishPosition < 0) 0f else (finishPosition * 0.012f).coerceAtMost(0.08f)
    val progress = if (running) (timeProgress * (1f - finishBias)).coerceIn(0f, 1f) else 0f
    Surface(
        onClick = onSelect,
        enabled = !running,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF1D2B3F) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(horse.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${horse.odds}x", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Text(
                "Rating ${horse.rating} · Form ${horse.form} · Speed ${horse.speed} · Stamina ${horse.stamina}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            if (running) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
