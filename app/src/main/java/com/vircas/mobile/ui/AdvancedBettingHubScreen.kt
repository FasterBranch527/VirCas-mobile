package com.vircas.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.EsportsBettingEngine
import com.vircas.mobile.game.engines.EsportsEvent
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent

private enum class BetHubTab { SPORTS, ESPORTS, HORSES }

@Composable
fun AdvancedBettingHubScreen(viewModel: AppViewModel, onHorseRace: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val sportsEngine = remember { SportsBettingEngine(viewModel.randomProvider()) }
    val esportsEngine = remember { EsportsBettingEngine(viewModel.randomProvider()) }
    val horseEngine = remember { HorseRacingEngine(viewModel.randomProvider()) }
    val sportsEvents = remember { sportsEngine.generateEvents() }
    val esportsEvents = remember { esportsEngine.generate() }
    var horseRace by remember { mutableStateOf(horseEngine.generateRace(id = "bet_horses", count = 8)) }
    var tab by remember { mutableStateOf(BetHubTab.SPORTS) }
    var selected by remember { mutableStateOf<Map<String, MarketSelection>>(emptyMap()) }
    var stake by remember { mutableStateOf("1000") }
    var status by remember { mutableStateOf("Build a single bet or accumulator. Every event is fictional and local.") }

    val selectedItems = selected.values.toList()
    val combinedOdds = selectedItems.fold(1.0) { acc, item -> acc * item.odds }
    val stakeAmount = stake.toLongOrNull() ?: 0L
    val possiblePayout = (stakeAmount.toDouble() * combinedOdds).toLong().coerceAtLeast(0L)

    fun toggle(selection: MarketSelection) {
        selected = if (selected[selection.eventId]?.id == selection.id) {
            selected - selection.eventId
        } else {
            selected + (selection.eventId to selection)
        }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Virtual Bets", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Sports · Esports · Horses", color = Color(0xFF94A3B8))
                }
                Text("%,d VC".format(balance), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BetHubTab.entries.forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.name) })
                }
            }
        }

        when (tab) {
            BetHubTab.SPORTS -> items(sportsEvents, key = { it.id }) { event ->
                SportsEventCard(event, sportsEngine.selections(event), selected[event.id], ::toggle)
            }
            BetHubTab.ESPORTS -> items(esportsEvents, key = { it.id }) { event ->
                EsportsEventCard(event, esportsEngine.selections(event), selected[event.id], ::toggle)
            }
            BetHubTab.HORSES -> {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Virtual horse market", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("Use these selections in the shared bet slip, or open the animated race mode.", color = Color(0xFF94A3B8))
                            OutlinedButton(onClick = onHorseRace, modifier = Modifier.fillMaxWidth()) { Text("OPEN ANIMATED HORSE RACING") }
                            OutlinedButton(onClick = {
                                selected = selected - horseRace.id
                                horseRace = horseEngine.generateRace(id = "bet_horses_${System.currentTimeMillis()}", count = 8)
                            }, modifier = Modifier.fillMaxWidth()) { Text("GENERATE NEW FIELD") }
                        }
                    }
                }
                items(horseEngine.selections(horseRace), key = { it.id }) { selection ->
                    SelectionButton(selection, selected[horseRace.id]?.id == selection.id) { toggle(selection) }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF151D2D)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BET SLIP", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    if (selectedItems.isEmpty()) {
                        Text("No selections yet", color = Color(0xFF94A3B8))
                    } else {
                        selectedItems.forEach { selection ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(selection.label, fontWeight = FontWeight.SemiBold)
                                    Text(selection.eventId, fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                                Text("${selection.odds}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = stake,
                        onValueChange = { next -> if (next.all(Char::isDigit)) stake = next.take(9) },
                        label = { Text("Stake (VC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        Text("Combined odds", Modifier.weight(1f), color = Color(0xFF94A3B8))
                        Text("${"%.2f".format(combinedOdds)}x", fontWeight = FontWeight.Bold)
                    }
                    Row {
                        Text("Possible payout", Modifier.weight(1f), color = Color(0xFF94A3B8))
                        Text("%,d VC".format(possiblePayout), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        enabled = selectedItems.isNotEmpty() && stakeAmount > 0,
                        onClick = {
                            viewModel.placeMixedBetSlip(
                                sportsEvents = sportsEvents,
                                esportsEvents = esportsEvents,
                                horseRace = horseRace,
                                selections = selectedItems,
                                stake = stakeAmount
                            ) { receipt ->
                                status = receipt?.let {
                                    "${it.result} · ${"%.2f".format(it.multiplier)}x · payout ${it.payout} VC"
                                } ?: "Could not place bet. Check balance and selections."
                                if (receipt != null) selected = emptyMap()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selectedItems.size > 1) "PLACE ACCUMULATOR" else "PLACE SINGLE BET") }
                    Text(status, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SportsEventCard(
    event: VirtualEvent,
    selections: List<MarketSelection>,
    selected: MarketSelection?,
    onToggle: (MarketSelection) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.sport.name, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${event.home} vs ${event.away}", fontWeight = FontWeight.Black, fontSize = 18.sp)
            selections.forEach { selection -> SelectionButton(selection, selected?.id == selection.id) { onToggle(selection) } }
        }
    }
}

@Composable
private fun EsportsEventCard(
    event: EsportsEvent,
    selections: List<MarketSelection>,
    selected: MarketSelection?,
    onToggle: (MarketSelection) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.discipline, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${event.teamA} vs ${event.teamB}", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("Match · Map 1 · Total maps · Handicap", color = Color(0xFF94A3B8), fontSize = 12.sp)
            selections.forEach { selection -> SelectionButton(selection, selected?.id == selection.id) { onToggle(selection) } }
        }
    }
}

@Composable
private fun SelectionButton(selection: MarketSelection, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(if (selected) "✓" else "+")
        Spacer(Modifier.width(8.dp))
        Text(selection.label, Modifier.weight(1f))
        Text("${selection.odds}", fontWeight = FontWeight.Bold)
    }
}
