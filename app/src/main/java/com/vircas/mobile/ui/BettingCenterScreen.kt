package com.vircas.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.betting.EsportsMarketEngine
import com.vircas.mobile.game.betting.UniversalBetSelection
import com.vircas.mobile.game.betting.UniversalBetSlip
import com.vircas.mobile.game.engines.EsportsBettingEngine
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SportsBettingEngine

private enum class BettingCategory(val label: String) {
    SPORTS("Sports"),
    ESPORTS("Esports"),
    HORSES("Horse Racing")
}

@Composable
fun BettingCenterScreen(viewModel: AppViewModel) {
    val balance by viewModel.balance.collectAsState()
    val catalogRandom = remember { viewModel.randomProvider() }
    val sportsEngine = remember { SportsBettingEngine(catalogRandom) }
    val esportsEngine = remember { EsportsBettingEngine(catalogRandom) }
    val esportsMarkets = remember { EsportsMarketEngine(catalogRandom) }
    val horseEngine = remember { HorseRacingEngine(catalogRandom) }
    val sportsEvents = remember { sportsEngine.generateEvents() }
    val esportsEvents = remember { esportsEngine.generate() }
    val horseRace = remember { horseEngine.generateRace(id = "betting_horses", count = 8) }

    var category by remember { mutableStateOf(BettingCategory.SPORTS) }
    var selections by remember { mutableStateOf<List<UniversalBetSelection>>(emptyList()) }
    var stakeText by remember { mutableStateOf("1000") }
    var status by remember { mutableStateOf("Build a single or an express from fictional events.") }
    var resultLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var placing by remember { mutableStateOf(false) }

    fun toggle(selection: UniversalBetSelection) {
        if (placing) return
        selections = if (selections.any { it.id == selection.id }) {
            selections.filterNot { it.id == selection.id }
        } else {
            selections.filterNot { it.eventId == selection.eventId } + selection
        }
    }

    val stake = stakeText.toLongOrNull()?.takeIf { it > 0L } ?: 0L
    val slip = remember(selections, stake) {
        runCatching { UniversalBetSlip(selections, stake) }.getOrNull()
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Virtual Betting", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Fictional events · offline settlement only", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("%,d VC".format(balance), Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BettingCategory.entries) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        item {
            BetSlipPanel(
                selections = selections,
                stakeText = stakeText,
                slip = slip,
                status = status,
                placing = placing,
                onStakeChange = { next -> if (next.all(Char::isDigit)) stakeText = next.take(9) },
                onRemove = { id -> selections = selections.filterNot { it.id == id } },
                onClear = { selections = emptyList() },
                onPlace = {
                    val current = slip
                    if (current == null) {
                        status = "Add at least one selection and enter a valid stake."
                        return@BetSlipPanel
                    }
                    placing = true
                    status = "Resolving ${if (current.isExpress) "express" else "single"} locally…"
                    viewModel.placeUniversalBetSlip(current.selections, current.stake) { settled ->
                        placing = false
                        if (settled == null) {
                            status = "Bet could not be placed. Check balance and selections."
                        } else {
                            val (receipt, simulation) = settled
                            resultLines = simulation.resultLines
                            status = if (simulation.won) {
                                "WIN · ${receipt.payout} VC · ${simulation.slip.combinedOdds}x"
                            } else {
                                "LOSS · results were fixed from the recorded round seed"
                            }
                            selections = emptyList()
                        }
                    }
                }
            )
        }

        if (resultLines.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Last settlement", fontWeight = FontWeight.Bold)
                        resultLines.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f), fontSize = 12.sp) }
                    }
                }
            }
        }

        when (category) {
            BettingCategory.SPORTS -> {
                items(sportsEvents, key = { it.id }) { event ->
                    BettingEventCard(
                        eyebrow = event.sport.name,
                        title = "${event.home} vs ${event.away}",
                        markets = sportsEngine.selections(event),
                        selectedIds = selections.mapTo(mutableSetOf()) { it.id },
                        onMarket = { market -> toggle(UniversalBetSelection.Sports(event, market)) }
                    )
                }
            }

            BettingCategory.ESPORTS -> {
                items(esportsEvents, key = { it.id }) { event ->
                    BettingEventCard(
                        eyebrow = event.discipline,
                        title = "${event.teamA} vs ${event.teamB}",
                        markets = esportsMarkets.selections(event),
                        selectedIds = selections.mapTo(mutableSetOf()) { it.id },
                        onMarket = { market -> toggle(UniversalBetSelection.Esports(event, market)) }
                    )
                }
            }

            BettingCategory.HORSES -> {
                item {
                    Text("${horseRace.horses.size} fictional horses · winner market", fontWeight = FontWeight.Bold)
                }
                items(horseRace.horses, key = { it.id }) { horse ->
                    val selection = UniversalBetSelection.HorseWin(horseRace, horse)
                    val selected = selections.any { it.id == selection.id }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { toggle(selection) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(horse.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Rating ${horse.rating} · Form ${horse.form} · Speed ${horse.speed} · Stamina ${horse.stamina}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                                    fontSize = 11.sp
                                )
                            }
                            Text("${horse.odds}x", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BettingEventCard(
    eyebrow: String,
    title: String,
    markets: List<MarketSelection>,
    selectedIds: Set<String>,
    onMarket: (MarketSelection) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(eyebrow, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(markets, key = { it.id }) { market ->
                    FilterChip(
                        selected = market.id in selectedIds,
                        onClick = { onMarket(market) },
                        label = { Text("${market.label} · ${market.odds}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun BetSlipPanel(
    selections: List<UniversalBetSelection>,
    stakeText: String,
    slip: UniversalBetSlip?,
    status: String,
    placing: Boolean,
    onStakeChange: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onPlace: () -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bet Slip", fontWeight = FontWeight.Black, fontSize = 19.sp)
                    Text(
                        when (selections.size) {
                            0 -> "No selections"
                            1 -> "Single"
                            else -> "Express · ${selections.size} legs"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                if (selections.isNotEmpty()) OutlinedButton(onClick = onClear, enabled = !placing) { Text("CLEAR") }
            }

            selections.forEach { selection ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selection.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${selection.odds}x", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onRemove(selection.id) }, enabled = !placing) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove")
                    }
                }
            }

            OutlinedTextField(
                value = stakeText,
                onValueChange = onStakeChange,
                enabled = !placing,
                singleLine = true,
                label = { Text("Stake (VC)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (slip != null) {
                Row {
                    Text("Combined ${slip.combinedOdds}x", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Possible %,d VC".format(slip.possiblePayout), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            }
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 12.sp)
            Button(
                onClick = onPlace,
                enabled = slip != null && !placing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (placing) "RESOLVING…" else "PLACE VIRTUAL BET") }
        }
    }
}
