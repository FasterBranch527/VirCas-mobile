package com.vircas.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Purple = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)
private val Gold = Color(0xFFF4C95D)
private val Green = Color(0xFF42D392)
private val Red = Color(0xFFFF6B7A)
private val Deep = Color(0xFF080B12)
private val Panel = Color(0xFF111827)
private val Panel2 = Color(0xFF182033)

private enum class Page { HOME, GAMES, HISTORY, SETTINGS }

fun main() = application {
    val windowState = rememberWindowState(width = 1180.dp, height = 760.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "VirCas",
        state = windowState
    ) {
        val state = remember { DesktopState() }
        VirCasDesktop(state)
    }
}

@Composable
private fun VirCasDesktop(state: DesktopState) {
    val colors = if (state.darkMode) {
        darkColorScheme(
            primary = Gold,
            secondary = Cyan,
            tertiary = Purple,
            background = Deep,
            surface = Panel,
            surfaceVariant = Panel2
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6D4BC3),
            secondary = Color(0xFF007A8A),
            tertiary = Color(0xFF7A5A00)
        )
    }

    MaterialTheme(colorScheme = colors) {
        if (!state.onboardingComplete) {
            Onboarding(state)
        } else {
            DesktopShell(state)
        }
    }
}

@Composable
private fun Onboarding(state: DesktopState) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Deep, Color(0xFF12152A), Color(0xFF102328)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).padding(32.dp),
            shape = RoundedCornerShape(32.dp),
            color = Panel.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shadowElevation = 24.dp
        ) {
            Column(
                Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(shape = CircleShape, color = Gold.copy(alpha = 0.13f)) {
                    Text("VC", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = Gold, fontWeight = FontWeight.Black, fontSize = 26.sp)
                }
                Text("VirCas for macOS", fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text(
                    "Offline-only virtual gaming hub. Coins, odds and rewards exist only inside VirCas and have no monetary value.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    fontSize = 15.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tag("13 games", Purple)
                    Tag("Local saves", Cyan)
                    Tag("No account", Green)
                }
                Button(
                    onClick = state::completeOnboarding,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF171006)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("ENTER VIRCAS", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DesktopShell(state: DesktopState) {
    var page by remember { mutableStateOf(Page.HOME) }
    var selectedGame by remember { mutableStateOf<DesktopGame?>(null) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(
            page = page,
            balance = state.balance,
            onPage = {
                selectedGame = null
                page = it
            }
        )
        VerticalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val game = selectedGame
            if (game != null) {
                GamePage(game, state, onBack = { selectedGame = null })
            } else {
                when (page) {
                    Page.HOME -> HomePage(state, onGame = { selectedGame = it }, onAllGames = { page = Page.GAMES })
                    Page.GAMES -> GamesPage(onGame = { selectedGame = it })
                    Page.HISTORY -> HistoryPage(state)
                    Page.SETTINGS -> SettingsPage(state)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(page: Page, balance: Long, onPage: (Page) -> Unit) {
    Column(
        Modifier.width(230.dp).fillMaxHeight().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = Gold.copy(alpha = 0.14f)) {
                Text("VC", Modifier.padding(10.dp), color = Gold, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("VirCas", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("macOS", fontSize = 10.sp, color = Cyan, fontWeight = FontWeight.Bold)
            }
        }

        NavButton("⌂", "Home", page == Page.HOME) { onPage(Page.HOME) }
        NavButton("◆", "Games", page == Page.GAMES) { onPage(Page.GAMES) }
        NavButton("↺", "History", page == Page.HISTORY) { onPage(Page.HISTORY) }
        NavButton("⚙", "Settings", page == Page.SETTINGS) { onPage(Page.SETTINGS) }

        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Gold.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.18f))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("VIRTUAL WALLET", fontSize = 9.sp, color = Gold, fontWeight = FontWeight.Black)
                Text(formatVc(balance), fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Local-only currency", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
            }
        }
    }
}

@Composable
private fun NavButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Gold.copy(alpha = 0.12f) else Color.Transparent
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 17.sp, color = if (selected) Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.width(11.dp))
            Text(label, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
        }
    }
}

@Composable
private fun HomePage(state: DesktopState, onGame: (DesktopGame) -> Unit, onAllGames: () -> Unit) {
    var showFunds by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Header("VIRCAS DESKTOP", "Casino floor", "Native desktop layout for macOS")
        WalletCard(state.balance) { showFunds = true }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Featured games", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Same virtual-only play, rebuilt for a desktop viewport", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
            }
            TextButton(onClick = onAllGames) { Text("ALL GAMES") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            desktopGames.take(4).forEach { game ->
                GameCard(game, Modifier.weight(1f), onClick = { onGame(game) })
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Green.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Green.copy(alpha = 0.18f))
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Green, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Offline and local", fontWeight = FontWeight.Black)
                    Text("Wallet, settings and game history are stored only on this Mac.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }
    }

    if (showFunds) FundsDialog(
        balance = state.balance,
        onDismiss = { showFunds = false },
        onAdd = { state.addFunds(it); showFunds = false }
    )
}

@Composable
private fun GamesPage(onGame: (DesktopGame) -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Header("PLAY", "All games", "13 local games adapted to mouse and desktop")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(210.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(desktopGames, key = { it.id }) { game ->
                GameCard(game, Modifier.fillMaxWidth(), onClick = { onGame(game) })
            }
        }
    }
}

@Composable
private fun GameCard(game: DesktopGame, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Panel,
        border = BorderStroke(1.dp, gameColor(game.id).copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(game.emoji, fontSize = 27.sp, color = gameColor(game.id))
            Spacer(Modifier.weight(1f))
            Text(game.title, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(game.subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GamePage(game: DesktopGame, state: DesktopState, onBack: () -> Unit) {
    var stakeText by remember(game.id) { mutableStateOf("1000") }
    var last by remember(game.id) { mutableStateOf<HistoryEntry?>(null) }
    val stake = stakeText.toLongOrNull() ?: 0L

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("← BACK TO GAMES") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(24.dp), color = gameColor(game.id).copy(alpha = 0.12f)) {
                Text(game.emoji, Modifier.padding(20.dp), fontSize = 38.sp, color = gameColor(game.id))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(game.title, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(game.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f))
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Panel,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("PLAY ROUND", color = gameColor(game.id), fontSize = 10.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { next -> if (next.all(Char::isDigit) && next.length <= 18) stakeText = next },
                    label = { Text("Stake") },
                    suffix = { Text("VC") },
                    singleLine = true,
                    modifier = Modifier.width(300.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100L, 1_000L, 10_000L, 50_000L).forEach { preset ->
                        AssistChip(onClick = { stakeText = preset.toString() }, label = { Text(formatVc(preset)) })
                    }
                }
                Button(
                    onClick = {
                        last = state.play(game.title, stake) { random -> resolveDesktopGame(game.id, random) }
                    },
                    enabled = stake > 0L && stake <= state.balance,
                    colors = ButtonDefaults.buttonColors(containerColor = gameColor(game.id), contentColor = Color(0xFF0A0B10)),
                    modifier = Modifier.width(300.dp).height(50.dp)
                ) {
                    Text("PLAY", fontWeight = FontWeight.Black)
                }
                Text("Balance: ${formatVc(state.balance)}", fontWeight = FontWeight.Bold)
            }
        }

        last?.let { entry ->
            val profit = entry.payout - entry.stake
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (profit >= 0) Green.copy(alpha = 0.09f) else Red.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, if (profit >= 0) Green.copy(alpha = 0.22f) else Red.copy(alpha = 0.22f))
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.result, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (profit >= 0) "+${formatVc(profit)}" else "-${formatVc(-profit)}",
                        color = if (profit >= 0) Green else Red,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("Payout: ${formatVc(entry.payout)}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }

        Text(
            "Desktop note: Android-only SceneView visuals are replaced by native Compose desktop presentation; the round result is resolved before it is displayed.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        )
    }
}

@Composable
private fun HistoryPage(state: DesktopState) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Header("LOCAL LEDGER", "History", "Latest rounds saved on this Mac")
        if (state.history.isEmpty()) {
            EmptyCard("No rounds yet", "Play a game and completed rounds will appear here.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { "${it.timestamp}-${it.game}" }) { item ->
                    HistoryRow(item)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryEntry) {
    val delta = item.payout - item.stake
    Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.game, fontWeight = FontWeight.Black)
                Text(item.result, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (delta >= 0) "+${formatVc(delta)}" else "-${formatVc(-delta)}",
                    color = if (delta >= 0) Green else Red,
                    fontWeight = FontWeight.Black
                )
                Text(formatTime(item.timestamp), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        }
    }
}

@Composable
private fun SettingsPage(state: DesktopState) {
    var debugSeedText by remember { mutableStateOf(state.debugSeed.toString()) }
    var clientSeedText by remember { mutableStateOf(state.clientSeed) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header("PREFERENCES", "Settings", "Desktop-local configuration")
        SettingSwitch("Dark mode", "Use the dark VirCas desktop theme", state.darkMode, state::setDarkMode)
        SettingSwitch("Reduced motion", "Keep motion restrained on desktop", state.reducedMotion, state::setReducedMotion)
        SettingSwitch("Secure RNG", "Use SecureRandom for normal play", state.secureRng, state::setSecureRng)

        Surface(shape = RoundedCornerShape(20.dp), color = Panel) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DEBUG FAIRNESS", fontSize = 10.sp, color = Cyan, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = debugSeedText,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '-' }) debugSeedText = it },
                    label = { Text("Debug seed") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientSeedText,
                    onValueChange = { clientSeedText = it.take(80) },
                    label = { Text("Client seed") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    debugSeedText.toLongOrNull()?.let(state::setDebugSeed)
                    state.setClientSeed(clientSeedText)
                }) { Text("SAVE SEEDS") }
            }
        }

        OutlinedButton(onClick = { confirmReset = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) {
            Text("RESET LOCAL ACCOUNT")
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset local VirCas data?") },
            text = { Text("Wallet, history and desktop settings will be reset on this Mac.") },
            confirmButton = {
                Button(
                    onClick = { state.resetLocalAccount(); confirmReset = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("RESET") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Panel) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun Header(eyebrow: String, title: String, subtitle: String) {
    Column {
        Text(eyebrow, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), fontSize = 11.sp)
    }
}

@Composable
private fun WalletCard(balance: Long, onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f))
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF241A3B), Color(0xFF102B34), Color(0xFF17251D)))
            ).padding(22.dp)
        ) {
            Column {
                Text("VIRTUAL WALLET", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(formatVc(balance), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Local-only play currency", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp)
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF171006))
            ) { Text("+ ADD FUNDS", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun FundsDialog(balance: Long, onDismiss: () -> Unit, onAdd: (Long) -> Unit) {
    var text by remember { mutableStateOf("10000") }
    val amount = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add virtual funds") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current: ${formatVc(balance)}")
                Text("No purchase or deposit is involved.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 18) text = it },
                    label = { Text("Amount") },
                    suffix = { Text("VC") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { amount?.takeIf { it > 0 }?.let(onAdd) }, enabled = amount != null && amount > 0) {
                Text("ADD")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
        }
    }
}

private fun gameColor(id: String): Color = when (id) {
    "roulette", "blackjack" -> Gold
    "mines", "crash", "plinko" -> Cyan
    "slots", "wheel" -> Purple
    "dice", "coinflip", "hilo" -> Green
    else -> Color(0xFF7C8CFF)
}

private fun formatVc(value: Long): String = "%,d VC".format(value)

private fun formatTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("dd MMM · HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
