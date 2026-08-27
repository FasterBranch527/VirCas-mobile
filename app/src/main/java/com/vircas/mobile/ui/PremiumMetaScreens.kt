package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.data.GameHistoryEntity

@Composable
fun PremiumInventoryScreen(viewModel: AppViewModel) {
    val inventory by viewModel.inventory.collectAsState()
    val totalValue = inventory.sumOf { it.marketValue }
    var status by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShellHeader(
                "VIRCAS // COLLECTION",
                "Inventory",
                "${inventory.size} items · ${formatShellVc(totalValue)} value",
                Icons.Rounded.Inventory2
            )
        }
        status?.let { item { Text(it, color = ShellGreen, fontWeight = FontWeight.Bold) } }
        if (inventory.isEmpty()) {
            item {
                EmptyShellCard(
                    icon = Icons.Rounded.Inventory2,
                    title = "Inventory is empty",
                    subtitle = "Open a fictional case and your drops will live here."
                )
            }
        }
        items(inventory, key = { it.id }) { item ->
            val accent = rarityAccent(item.rarity)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.14f)) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(13.dp), tint = accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${item.weaponCategory} · ${item.rarity}",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatShellVc(item.marketValue),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(onClick = {
                        viewModel.sellInventoryItem(item.id) { value ->
                            status = value?.let { "Sold ${item.name} for ${formatShellVc(it)}" } ?: "Item already removed"
                        }
                    }) { Text("SELL") }
                }
            }
        }
    }
}

@Composable
fun PremiumProfileScreen(
    viewModel: AppViewModel,
    onHistory: () -> Unit,
    onFairness: () -> Unit,
    onSettings: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val achievements = remember(progress) { viewModel.achievements(progress) }
    var showFunds by remember { mutableStateOf(false) }
    var achievementText by remember { mutableStateOf<String?>(null) }

    if (showFunds) {
        VirtualFundsDialog(balance, { showFunds = false }) { amount ->
            viewModel.addVirtualFunds(amount) { if (it) showFunds = false }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ShellHeader(
                "VIRCAS // PROFILE",
                "NightShift",
                "Level ${progress.level} · ${progress.xp} total XP",
                Icons.Rounded.Person
            )
        }
        item { VirtualWalletCard(balance, { showFunds = true }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("ROUNDS", progress.gamesPlayed.toString(), ShellCyan, Modifier.weight(1f))
                StatTile("WIN RATE", "%.1f%%".format(progress.winRate), ShellGreen, Modifier.weight(1f))
                StatTile("STREAK", progress.winStreak.toString(), ShellGold, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAction(Icons.Rounded.History, "History", ShellCyan, Modifier.weight(1f), onHistory)
                ProfileAction(Icons.Rounded.Shield, "Fairness", ShellGreen, Modifier.weight(1f), onFairness)
                ProfileAction(Icons.Rounded.Settings, "Settings", ShellPurple, Modifier.weight(1f), onSettings)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    ProfileMetric("Biggest win", formatShellVc(progress.biggestWin))
                    ProfileMetric("Total wagered", formatShellVc(progress.totalWagered))
                    ProfileMetric("Total won", formatShellVc(progress.totalWon))
                    ProfileMetric("Favorite game", progress.favoriteGame)
                    ProfileMetric("Daily streak", "${progress.dailyStreak} days")
                }
            }
        }
        item { SectionHeader("Achievements", "Permanent local progression") }
        achievementText?.let { item { Text(it, color = ShellGreen, fontWeight = FontWeight.Bold) } }
        items(achievements, key = { it.id }) { achievement ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ShellPanel,
                border = BorderStroke(
                    1.dp,
                    if (achievement.unlocked) ShellGold.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.04f)
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            achievement.claimed -> Icons.Rounded.CheckCircle
                            achievement.unlocked -> Icons.Rounded.EmojiEvents
                            else -> Icons.Rounded.Lock
                        },
                        null,
                        tint = if (achievement.unlocked) ShellGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(achievement.title, fontWeight = FontWeight.Bold)
                        Text(
                            achievement.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (achievement.unlocked && !achievement.claimed) {
                        OutlinedButton(onClick = {
                            viewModel.claimAchievement(achievement) { success ->
                                achievementText = if (success) "+${achievement.xpReward} XP claimed" else "Already claimed"
                            }
                        }) { Text("+${achievement.xpReward} XP") }
                    }
                }
            }
        }
    }
}

private enum class PremiumHistoryFilter { ALL, WINS, LOSSES, PUSHES }

@Composable
fun PremiumHistoryScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var filter by remember { mutableStateOf(PremiumHistoryFilter.ALL) }
    val visible = remember(history, filter) {
        history.filter {
            when (filter) {
                PremiumHistoryFilter.ALL -> true
                PremiumHistoryFilter.WINS -> it.won
                PremiumHistoryFilter.LOSSES -> it.lost
                PremiumHistoryFilter.PUSHES -> !it.won && !it.lost
            }
        }
    }
    val net = history.sumOf { it.profitLoss }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ShellBackHeader("History", "${history.size} local rounds · net ${signedVc(net)}", onBack) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PremiumHistoryFilter.entries) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyShellCard(Icons.Rounded.History, "Nothing here yet", "Play a round or change the active filter.") }
        }
        items(visible, key = { it.id }) { HistoryPremiumRow(it) }
    }
}

@Composable
fun PremiumFairnessScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val rounds by viewModel.fairness.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ShellBackHeader("Fairness", "Local seed trail for every settled round", onBack) }
        item { FairnessInfoCard() }
        if (rounds.isEmpty()) {
            item { EmptyShellCard(Icons.Rounded.Shield, "No fairness records", "Settle a game round to create one.") }
        }
        items(rounds, key = { it.roundId }) { round ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(round.game, Modifier.weight(1f), fontWeight = FontWeight.Black)
                        Text(round.roundId.takeLast(8), color = ShellCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("RESULT  ${round.result}", color = ShellGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "SERVER  ${round.generatedSeed.take(28)}…",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        fontSize = 10.sp
                    )
                    Text(
                        "CLIENT  ${round.clientSeed.ifBlank { "<empty>" }.take(28)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumSettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val balance by viewModel.balance.collectAsState()
    var showFunds by remember { mutableStateOf(false) }
    var resetArmed by remember { mutableStateOf(false) }
    var clientSeed by remember(settings.clientSeed) { mutableStateOf(settings.clientSeed) }
    var debugSeed by remember(settings.debugSeed) { mutableStateOf(settings.debugSeed.toString()) }

    if (showFunds) {
        VirtualFundsDialog(balance, { showFunds = false }) { amount ->
            viewModel.addVirtualFunds(amount) { if (it) showFunds = false }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))
        ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ShellBackHeader("Settings", "Local controls and simulator diagnostics", onBack) }
        item { SectionHeader("Virtual wallet", "No payments. No real money.") }
        item { VirtualWalletCard(balance, { showFunds = true }) }
        item { SectionHeader("Experience", "Sound, motion and appearance") }
        item { PremiumSettingSwitch("Sound", "Interface and game sound effects", settings.sound, viewModel::setSound) }
        item { PremiumSettingSwitch("Vibration", "Haptic feedback on supported devices", settings.vibration, viewModel::setVibration) }
        item { PremiumSettingSwitch("Animations", "Full visual motion and transitions", settings.animations, viewModel::setAnimations) }
        item { PremiumSettingSwitch("Reduced motion", "Shorter, calmer animations", settings.reducedMotion, viewModel::setReducedMotion) }
        item { PremiumSettingSwitch("Dark mode", "Midnight VirCas theme", settings.darkMode, viewModel::setDarkMode) }
        item { SectionHeader("Fairness & developer", "Local RNG configuration") }
        item { PremiumSettingSwitch("Secure RNG", "Generate a fresh seed for every round", settings.secureRng, viewModel::setSecureRng) }
        item { PremiumSettingSwitch("Developer diagnostics", "Show extra local diagnostics", settings.developerDiagnostics, viewModel::setDeveloperDiagnostics) }
        item {
            OutlinedTextField(
                value = clientSeed,
                onValueChange = {
                    clientSeed = it.take(64)
                    viewModel.setClientSeed(clientSeed)
                },
                label = { Text("Client seed") },
                supportingText = { Text("Mixed with every generated round seed") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = debugSeed,
                onValueChange = { next ->
                    if (next.all(Char::isDigit) && next.length <= 18) {
                        debugSeed = next
                        next.toLongOrNull()?.let(viewModel::setDebugSeed)
                    }
                },
                label = { Text("Deterministic debug seed") },
                enabled = !settings.secureRng,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { SectionHeader("Danger zone", "Reset only affects this device") }
        item {
            if (!resetArmed) {
                OutlinedButton(onClick = { resetArmed = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("RESET LOCAL ACCOUNT")
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ShellRed.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, ShellRed.copy(alpha = 0.30f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "This clears balance, progression, inventory, history and fairness.",
                            color = ShellRed,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.resetLocalAccount(); resetArmed = false },
                            colors = ButtonDefaults.buttonColors(containerColor = ShellRed),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("CONFIRM RESET") }
                        OutlinedButton(onClick = { resetArmed = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("CANCEL")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}

@Composable
private fun ProfileAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            Modifier.padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, null, tint = accent)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryPremiumRow(row: GameHistoryEntity) {
    val accent = when {
        row.won -> ShellGreen
        row.lost -> ShellRed
        else -> ShellGold
    }
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.game, Modifier.weight(1f), fontWeight = FontWeight.Black)
                Text(signedVc(row.profitLoss), color = accent, fontWeight = FontWeight.Black)
            }
            Text(row.result, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), fontSize = 12.sp)
            Text(
                "Stake ${formatShellVc(row.stake)} · payout ${formatShellVc(row.payout)} · ${"%.2f".format(row.multiplier)}x",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PremiumSettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f), fontSize = 10.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
