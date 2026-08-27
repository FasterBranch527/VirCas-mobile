package com.vircas.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val featuredGameIds = listOf("mines", "blackjack", "roulette", "crash", "slots", "plinko", "horse")

@Composable
fun MetaHomeScreen(viewModel: AppViewModel, onGame: (String) -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val missions = remember(progress) { viewModel.dailyMissions(progress) }
    var rewardMessage by remember { mutableStateOf<String?>(null) }
    var missionMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIRCAS // PLAYER", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NightShift", fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Level ${progress.level} · ${progress.levelXp}/1000 XP", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("BALANCE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Text("%,d VC".format(balance), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onGame("mines") },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FEATURED ORIGINAL", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NEON MINES", fontSize = 34.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("A true multi-step 5×5 round with 1–10 mines and live cashout.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reward", fontWeight = FontWeight.Bold)
                        Text("Streak ${progress.dailyStreak} days", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        rewardMessage?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                    }
                    Button(onClick = {
                        viewModel.claimDailyReward { claim ->
                            rewardMessage = claim?.let { "Day ${it.day}: +${it.amount} VC · +50 XP" } ?: "Already claimed today"
                        }
                    }) { Text("CLAIM") }
                }
            }
        }

        item { Text("Popular Games", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(featuredGameIds) { id ->
                    Surface(
                        modifier = Modifier.width(150.dp).height(108.dp).clickable { onGame(id) },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(gameTitle(id), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        item { Text("Daily Missions", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        missionMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.secondary) } }
        items(missions, key = { it.id }) { mission ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (mission.claimed) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = if (mission.claimed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, fontWeight = FontWeight.SemiBold)
                            Text("+${mission.coinReward} VC · +${mission.xpReward} XP", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Text("${mission.progress.coerceAtMost(mission.target)}/${mission.target}", color = MaterialTheme.colorScheme.secondary)
                    }
                    LinearProgressIndicator(
                        progress = { (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mission.complete) {
                        Button(
                            onClick = {
                                viewModel.claimMission(mission) { coins ->
                                    missionMessage = when {
                                        coins != null -> "Mission claimed: +$coins VC and +${mission.xpReward} XP"
                                        mission.claimed -> "Mission reward already claimed"
                                        else -> "Mission is not claimable"
                                    }
                                }
                            },
                            enabled = !mission.claimed,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (mission.claimed) "CLAIMED" else "CLAIM REWARD") }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaProfileScreen(
    viewModel: AppViewModel,
    onHistory: () -> Unit,
    onFairness: () -> Unit,
    onSettings: () -> Unit
) {
    val balance by viewModel.balance.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val achievements = remember(progress) { viewModel.achievements(progress) }
    var achievementMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("NightShift", fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Level ${progress.level} · ${progress.xp} total XP", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        items(
            listOf(
                "Virtual balance" to "%,d VC".format(balance),
                "Games played" to progress.gamesPlayed.toString(),
                "Wins / losses" to "${progress.totalWins} / ${progress.totalLosses}",
                "Win rate" to "%.1f%%".format(progress.winRate),
                "Win streak" to progress.winStreak.toString(),
                "Biggest win" to "%,d VC".format(progress.biggestWin),
                "Total wagered" to "%,d VC".format(progress.totalWagered),
                "Total won" to "%,d VC".format(progress.totalWon),
                "Favorite game" to progress.favoriteGame,
                "Daily streak" to progress.dailyStreak.toString()
            )
        ) { (label, value) ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Text(value, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("HISTORY") }
                Button(onClick = onFairness, modifier = Modifier.weight(1f)) { Text("FAIRNESS") }
            }
        }
        item { OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("SETTINGS") } }

        item { Text("Achievements", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        achievementMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.secondary) } }
        items(achievements, key = { it.id }) { achievement ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                achievement.claimed -> Icons.Rounded.CheckCircle
                                achievement.unlocked -> Icons.Rounded.EmojiEvents
                                else -> Icons.Rounded.Lock
                            },
                            contentDescription = null,
                            tint = if (achievement.unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(achievement.title, fontWeight = FontWeight.Bold)
                            Text(achievement.description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Text("+${achievement.xpReward} XP", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    }
                    if (achievement.unlocked) {
                        Button(
                            onClick = {
                                viewModel.claimAchievement(achievement) { success ->
                                    achievementMessage = if (success) "Achievement claimed: +${achievement.xpReward} XP" else "Achievement reward already claimed"
                                }
                            },
                            enabled = !achievement.claimed,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (achievement.claimed) "CLAIMED" else "CLAIM XP") }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var clientSeed by remember(settings.clientSeed) { mutableStateOf(settings.clientSeed) }
    var debugSeed by remember(settings.debugSeed) { mutableStateOf(settings.debugSeed.toString()) }
    var resetArmed by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("BACK") }
                Spacer(Modifier.width(12.dp))
                Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
        item { MetaSettingSwitch("Sound", settings.sound, viewModel::setSound) }
        item { MetaSettingSwitch("Vibration", settings.vibration, viewModel::setVibration) }
        item { MetaSettingSwitch("Animations", settings.animations, viewModel::setAnimations) }
        item { MetaSettingSwitch("Reduced motion", settings.reducedMotion, viewModel::setReducedMotion) }
        item { MetaSettingSwitch("Dark mode", settings.darkMode, viewModel::setDarkMode) }
        item { MetaSettingSwitch("Secure RNG", settings.secureRng, viewModel::setSecureRng) }
        item { MetaSettingSwitch("Developer diagnostics", settings.developerDiagnostics, viewModel::setDeveloperDiagnostics) }
        item {
            OutlinedTextField(
                value = clientSeed,
                onValueChange = {
                    clientSeed = it.take(64)
                    viewModel.setClientSeed(clientSeed)
                },
                label = { Text("Client seed") },
                supportingText = { Text("Mixed with the generated seed for every recorded round") },
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RNG diagnostics", fontWeight = FontWeight.Bold)
                    Text("Mode: ${if (settings.secureRng) "secure generated round seeds" else "deterministic debug round seeds"}")
                    Text("Client seed: ${settings.clientSeed.ifBlank { "<empty>" }}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("Every settled round records generated seed + client seed + result in Fairness.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        item {
            if (!resetArmed) {
                OutlinedButton(onClick = { resetArmed = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("RESET LOCAL ACCOUNT")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This clears local balance, progression, inventory, history, fairness and settings.", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            viewModel.resetLocalAccount()
                            resetArmed = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CONFIRM RESET") }
                    OutlinedButton(onClick = { resetArmed = false }, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
                }
            }
        }
    }
}

@Composable
private fun MetaSettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
