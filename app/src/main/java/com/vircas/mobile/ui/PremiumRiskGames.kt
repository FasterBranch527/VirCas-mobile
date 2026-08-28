package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.game.engines.GameOutcome
import com.vircas.mobile.game.engines.HiLoEngine
import com.vircas.mobile.game.engines.HiLoGuess
import com.vircas.mobile.game.engines.HiLoResult
import com.vircas.mobile.game.engines.HiLoRound
import com.vircas.mobile.game.engines.LadderEngine
import com.vircas.mobile.game.engines.MinesEngine
import com.vircas.mobile.game.engines.PathRound
import com.vircas.mobile.game.engines.PathStep
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.Rank
import com.vircas.mobile.game.engines.Suit
import com.vircas.mobile.game.engines.TowersEngine
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PremiumMinesGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var mineCount by remember { mutableIntStateOf(3) }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<MinesEngine?>(null) }
    var round by remember { mutableStateOf<MinesEngine.Round?>(null) }
    var multiplier by remember { mutableStateOf(1.0) }
    var message by remember { mutableStateOf("Choose mine count and start the board.") }
    var revealMines by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var openedSnapshot by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var failedTile by remember { mutableStateOf<Int?>(null) }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        round = null
        engine = null
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    PremiumGameFrame("Mines", "5×5 board · 1–10 mines · live cashout", balance, ShellCyan, if (wager != null) ::leave else onBack) { compact, landscape ->
        val board: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, ShellCyan.copy(alpha = 0.18f))
            ) {
                Column(Modifier.fillMaxSize().padding(if (compact) 7.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(5) { rowIndex ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(5) { columnIndex ->
                                val tile = rowIndex * 5 + columnIndex
                                val current = round
                                val opened = current?.opened?.contains(tile) == true || tile in openedSnapshot
                                val mine = tile in revealMines
                                val failed = failedTile == tile
                                val tileColor = when {
                                    failed -> ShellRed.copy(alpha = 0.34f)
                                    mine -> ShellRed.copy(alpha = 0.18f)
                                    opened -> ShellGreen.copy(alpha = 0.18f)
                                    wager != null -> ShellPanel2
                                    else -> ShellPanel2.copy(alpha = 0.62f)
                                }
                                Surface(
                                    onClick = {
                                        val active = wager ?: return@Surface
                                        val live = round ?: return@Surface
                                        val liveEngine = engine ?: return@Surface
                                        if (tile in live.opened) return@Surface
                                        val (next, outcome) = liveEngine.reveal(live, tile)
                                        when (outcome) {
                                            is GameOutcome.Loss -> {
                                                openedSnapshot = live.opened
                                                revealMines = live.mineIndexes
                                                failedTile = tile
                                                viewModel.settleWager(active, 0.0, "Mine", "$mineCount mines")
                                                message = "BOOM · mine hit on tile ${tile + 1}"
                                                wager = null
                                                round = null
                                                engine = null
                                                multiplier = 1.0
                                            }
                                            is GameOutcome.Win -> {
                                                round = next
                                                multiplier = outcome.multiplier
                                                message = "Safe · ${next.opened.size} revealed · ${"%.2f".format(multiplier)}x"
                                                if (next.opened.size == 25 - mineCount) {
                                                    viewModel.settleWager(active, multiplier, "Board cleared", "$mineCount mines")
                                                    openedSnapshot = next.opened
                                                    message = "BOARD CLEARED · ${"%.2f".format(multiplier)}x"
                                                    wager = null
                                                    round = null
                                                    engine = null
                                                }
                                            }
                                            null -> Unit
                                        }
                                    },
                                    enabled = wager != null && !opened,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    shape = RoundedCornerShape(if (compact) 10.dp else 13.dp),
                                    color = tileColor,
                                    border = BorderStroke(1.dp, when {
                                        failed -> ShellRed.copy(alpha = 0.75f)
                                        mine -> ShellRed.copy(alpha = 0.35f)
                                        opened -> ShellGreen.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.055f)
                                    })
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            when {
                                                failed -> "✹"
                                                mine -> "✦"
                                                opened -> "◆"
                                                else -> ""
                                            },
                                            color = if (mine || failed) ShellRed else ShellGreen,
                                            fontWeight = FontWeight.Black,
                                            fontSize = if (compact) 15.sp else 19.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1, 3, 5, 7, 10).forEach { count ->
                        FilterChip(selected = mineCount == count, enabled = wager == null, onClick = { mineCount = count }, label = { Text("$count", fontWeight = FontWeight.Black) }, modifier = Modifier.weight(1f))
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = ShellPanel2) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("MINES $mineCount", color = ShellRed, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text("SAFE ${25 - mineCount}", color = ShellGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text("${"%.2f".format(multiplier)}x", color = ShellGold, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
                QuickStakeRow(wager == null, stake) { stake = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PremiumStakeField(stake, wager == null, ShellCyan, Modifier.weight(1f)) { stake = it }
                    if (wager == null) {
                        PremiumActionButton("START", ShellCyan, true, Modifier.weight(0.72f)) {
                            viewModel.beginWager("Mines", premiumStake(stake)) { started ->
                                if (started == null) message = "Could not start: check stake and balance."
                                else {
                                    val created = MinesEngine(viewModel.randomProvider())
                                    wager = started
                                    engine = created
                                    round = created.newRound(mineCount)
                                    multiplier = 1.0
                                    revealMines = emptySet()
                                    openedSnapshot = emptySet()
                                    failedTile = null
                                    message = "Board live · tap a tile."
                                }
                            }
                        }
                    } else {
                        PremiumActionButton("CASH ${"%.2f".format(multiplier)}x", ShellGold, round?.opened?.isNotEmpty() == true, Modifier.weight(0.92f)) {
                            val active = wager ?: return@PremiumActionButton
                            val live = round ?: return@PremiumActionButton
                            viewModel.settleWager(active, multiplier, "Cash out", "${live.opened.size} safe tiles")
                            openedSnapshot = live.opened
                            message = "Cashed out at ${"%.2f".format(multiplier)}x"
                            wager = null
                            round = null
                            engine = null
                        }
                    }
                }
                PremiumMessageCard(message, ShellCyan)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { Box(Modifier.aspectRatio(1f)) { board() } }
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Box(Modifier.fillMaxWidth().aspectRatio(1f)) { board() } }
                controls()
            }
        }
    }
}

@Composable
fun PremiumHiLoGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<HiLoEngine?>(null) }
    var round by remember { mutableStateOf<HiLoRound?>(null) }
    var pending by remember { mutableStateOf<HiLoResult?>(null) }
    var guessUsed by remember { mutableStateOf<HiLoGuess?>(null) }
    var trigger by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Start a run, then predict the next card.") }
    val reveal = remember { Animatable(0f) }

    fun rankValue(rank: Rank): Int = when (rank) {
        Rank.JACK -> 11
        Rank.QUEEN -> 12
        Rank.KING -> 13
        Rank.ACE -> 14
        else -> rank.pip
    }

    fun settleAndLeave() {
        val active = wager
        val current = round
        wager = null
        round = null
        engine = null
        pending = null
        if (active != null && current != null) viewModel.settleWager(active, current.multiplier, "Exit cash out", "Streak ${current.streak}")
        else if (active != null) viewModel.cancelWager(active)
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::settleAndLeave)

    LaunchedEffect(trigger) {
        val result = pending ?: return@LaunchedEffect
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(820, easing = FastOutSlowInEasing))
        delay(140)
        val active = wager ?: return@LaunchedEffect
        if (!result.won) {
            viewModel.settleWager(active, 0.0, "Wrong guess", "Next ${result.next.rank.name}")
            message = "${result.next.rank.name} ${suitGlyph(result.next.suit)} · WRONG"
            wager = null
            engine = null
            round = null
        } else {
            round = result.round
            message = "CORRECT · ${"%.2f".format(result.payoutMultiplier)}x · streak ${result.round?.streak ?: 0}"
        }
        pending = null
        guessUsed = null
        reveal.snapTo(0f)
    }

    PremiumGameFrame("Hi-Lo", "Predict higher or lower · ties lose", balance, ShellGreen, if (wager != null) ::settleAndLeave else onBack) { compact, landscape ->
        val current = round
        val value = current?.let { rankValue(it.current.rank) } ?: 8
        val higherCount = (14 - value).coerceAtLeast(0)
        val lowerCount = (value - 2).coerceAtLeast(0)
        val higherChance = higherCount / 13.0
        val lowerChance = lowerCount / 13.0
        val cardStage: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, ShellGreen.copy(alpha = 0.18f))
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (wager == null) "READY" else "CURRENT CARD", color = Color.White.copy(alpha = 0.35f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    val front = if (pending != null && reveal.value >= 0.5f) pending!!.next else current?.current
                    PremiumPlayingCard(
                        card = front,
                        hidden = pending != null && reveal.value < 0.5f,
                        modifier = Modifier.size(width = if (compact) 122.dp else 148.dp, height = if (compact) 166.dp else 200.dp).graphicsLayer {
                            rotationY = if (pending != null) reveal.value * 180f else 0f
                            cameraDistance = 18f * density
                            scaleX = if (pending != null) 0.96f + abs(reveal.value - 0.5f) * 0.08f else 1f
                        }
                    )
                    if (current != null) {
                        Text("${"%.2f".format(current.multiplier)}x · STREAK ${current.streak}", color = ShellGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (wager == null) {
                    QuickStakeRow(true, stake) { stake = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PremiumStakeField(stake, true, ShellGreen, Modifier.weight(1f)) { stake = it }
                        PremiumActionButton("START", ShellGreen, true, Modifier.weight(0.72f)) {
                            viewModel.beginWager("Hi-Lo", premiumStake(stake)) { started ->
                                if (started == null) message = "Could not start: check stake and balance."
                                else {
                                    val created = HiLoEngine(viewModel.randomProvider())
                                    wager = started
                                    engine = created
                                    round = created.newRound()
                                    message = "Choose HIGHER or LOWER."
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PremiumActionButton("↑ HIGHER\n${"%.0f".format(higherChance * 100)}%", ShellGreen, pending == null && higherCount > 0, Modifier.weight(1f)) {
                            val live = round ?: return@PremiumActionButton
                            val liveEngine = engine ?: return@PremiumActionButton
                            pending = liveEngine.guess(live, HiLoGuess.HIGHER)
                            guessUsed = HiLoGuess.HIGHER
                            trigger++
                            message = "Revealing next card…"
                        }
                        PremiumActionButton("↓ LOWER\n${"%.0f".format(lowerChance * 100)}%", ShellPurple, pending == null && lowerCount > 0, Modifier.weight(1f)) {
                            val live = round ?: return@PremiumActionButton
                            val liveEngine = engine ?: return@PremiumActionButton
                            pending = liveEngine.guess(live, HiLoGuess.LOWER)
                            guessUsed = HiLoGuess.LOWER
                            trigger++
                            message = "Revealing next card…"
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val active = wager ?: return@OutlinedButton
                            val live = round ?: return@OutlinedButton
                            viewModel.settleWager(active, live.multiplier, "Cash out", "Streak ${live.streak}")
                            message = "Cashed out at ${"%.2f".format(live.multiplier)}x"
                            wager = null
                            engine = null
                            round = null
                        },
                        enabled = pending == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CASH OUT ${"%.2f".format(current?.multiplier ?: 1.0)}x", fontWeight = FontWeight.Black) }
                }
                PremiumMessageCard(message, ShellGreen)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { cardStage() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cardStage() }
                controls()
            }
        }
    }
}

@Composable
private fun PremiumPlayingCard(card: PlayingCard?, hidden: Boolean, modifier: Modifier = Modifier) {
    val red = card?.suit == Suit.HEARTS || card?.suit == Suit.DIAMONDS
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (hidden) Color(0xFF24194A) else Color(0xFFF2F4F8),
        border = BorderStroke(2.dp, if (hidden) ShellPurple.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.6f)),
        shadowElevation = 10.dp
    ) {
        if (hidden || card == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("V", fontSize = 40.sp, fontWeight = FontWeight.Black, color = ShellPurple)
                    Text("VIRCAS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Column {
                    Text(rankGlyph(card.rank), fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (red) Color(0xFFD83A52) else Color(0xFF111827))
                    Text(suitGlyph(card.suit), fontSize = 20.sp, color = if (red) Color(0xFFD83A52) else Color(0xFF111827))
                }
                Text(suitGlyph(card.suit), Modifier.align(Alignment.Center), fontSize = 42.sp, color = if (red) Color(0xFFD83A52) else Color(0xFF111827))
                Text(rankGlyph(card.rank), Modifier.align(Alignment.BottomEnd), fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (red) Color(0xFFD83A52) else Color(0xFF111827))
            }
        }
    }
}

private fun rankGlyph(rank: Rank): String = when (rank) {
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.ACE -> "A"
    else -> rank.pip.toString()
}

private fun suitGlyph(suit: Suit): String = when (suit) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}

@Composable
fun PremiumTowersGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    PremiumPathGameScreen(
        viewModel = viewModel,
        title = "Towers",
        subtitle = "8 floors · 2 safe doors on every floor",
        accent = ShellCyan,
        cellCount = 3,
        onBack = onBack,
        newRound = { random -> TowersEngine(random).newRound() },
        choose = { round, cell -> TowersEngine(viewModel.randomProvider()).choose(round, cell) }
    )
}

@Composable
fun PremiumLadderGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    PremiumPathGameScreen(
        viewModel = viewModel,
        title = "Ladder",
        subtitle = "8 rungs · one trap per level",
        accent = ShellGold,
        cellCount = 4,
        onBack = onBack,
        newRound = { random -> LadderEngine(random).newRound() },
        choose = { round, cell -> LadderEngine(viewModel.randomProvider()).choose(round, cell) }
    )
}

@Composable
private fun PremiumPathGameScreen(
    viewModel: AppViewModel,
    title: String,
    subtitle: String,
    accent: Color,
    cellCount: Int,
    onBack: () -> Unit,
    newRound: (RandomProvider) -> PathRound,
    choose: (PathRound, Int) -> PathStep
) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var round by remember { mutableStateOf<PathRound?>(null) }
    var choices by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var failedAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var message by remember { mutableStateOf("Start the climb and choose a path.") }

    fun leave() {
        val active = wager
        val live = round
        wager = null
        round = null
        if (active != null && live != null && live.level > 0) viewModel.settleWager(active, live.multiplier, "Exit cash out", "Level ${live.level}")
        else if (active != null) viewModel.cancelWager(active)
        onBack()
    }
    BackHandler(enabled = wager != null, onBack = ::leave)

    PremiumGameFrame(title, subtitle, balance, accent, if (wager != null) ::leave else onBack) { compact, landscape ->
        val board: @Composable () -> Unit = {
            PathBoard(
                title = title,
                level = round?.level ?: 0,
                cellCount = cellCount,
                choices = choices,
                failedAt = failedAt,
                active = wager != null,
                accent = accent,
                compact = compact,
                onChoose = { cell ->
                    val active = wager ?: return@PathBoard
                    val live = round ?: return@PathBoard
                    val currentLevel = live.level
                    val step = choose(live, cell)
                    if (!step.won) {
                        failedAt = currentLevel to cell
                        viewModel.settleWager(active, 0.0, "Failed", "Level ${currentLevel + 1}")
                        message = "TRAP · failed at level ${currentLevel + 1}"
                        wager = null
                        round = null
                    } else {
                        choices = choices + (currentLevel to cell)
                        round = step.round
                        message = "SAFE · level ${step.round.level} · ${"%.2f".format(step.payoutMultiplier)}x"
                        if (step.round.finished) {
                            viewModel.settleWager(active, step.payoutMultiplier, "Completed", "$title completed")
                            message = "TOP REACHED · ${"%.2f".format(step.payoutMultiplier)}x"
                            wager = null
                        }
                    }
                }
            )
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = ShellPanel2) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("LEVEL ${round?.level ?: 0}/8", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accent)
                        Text("${"%.2f".format(round?.multiplier ?: 1.0)}x", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ShellGold)
                    }
                }
                if (wager == null) {
                    QuickStakeRow(true, stake) { stake = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PremiumStakeField(stake, true, accent, Modifier.weight(1f)) { stake = it }
                        PremiumActionButton("START", accent, true, Modifier.weight(0.72f)) {
                            viewModel.beginWager(title, premiumStake(stake)) { started ->
                                if (started == null) message = "Could not start: check stake and balance."
                                else {
                                    wager = started
                                    round = newRound(viewModel.randomProvider())
                                    choices = emptyMap()
                                    failedAt = null
                                    message = "Level 1 live · choose a tile."
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val active = wager ?: return@OutlinedButton
                            val live = round ?: return@OutlinedButton
                            viewModel.settleWager(active, live.multiplier, "Cash out", "Level ${live.level}")
                            message = "Cashed out at ${"%.2f".format(live.multiplier)}x"
                            wager = null
                            round = null
                        },
                        enabled = (round?.level ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CASH OUT ${"%.2f".format(round?.multiplier ?: 1.0)}x", fontWeight = FontWeight.Black) }
                }
                PremiumMessageCard(message, accent)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1.15f).fillMaxSize()) { board() }
                Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.Center) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { board() }
                controls()
            }
        }
    }
}

@Composable
private fun PathBoard(
    title: String,
    level: Int,
    cellCount: Int,
    choices: Map<Int, Int>,
    failedAt: Pair<Int, Int>?,
    active: Boolean,
    accent: Color,
    compact: Boolean,
    onChoose: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Box(Modifier.fillMaxSize().padding(if (compact) 8.dp else 12.dp)) {
            if (title == "Ladder") {
                Canvas(Modifier.fillMaxSize()) {
                    val left = size.width * 0.10f
                    val right = size.width * 0.90f
                    drawLine(accent.copy(alpha = 0.12f), Offset(left, 0f), Offset(left, size.height), strokeWidth = 5f)
                    drawLine(accent.copy(alpha = 0.12f), Offset(right, 0f), Offset(right, size.height), strokeWidth = 5f)
                }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (visualRow in 7 downTo 0) {
                    val passed = visualRow < level
                    val current = visualRow == level && active
                    val choice = choices[visualRow]
                    val failedCell = failedAt?.takeIf { it.first == visualRow }?.second
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${visualRow + 1}", Modifier.size(18.dp), textAlign = TextAlign.Center, fontSize = 8.sp, color = if (current) accent else Color.White.copy(alpha = 0.2f))
                        repeat(cellCount) { cell ->
                            val chosen = choice == cell
                            val failed = failedCell == cell
                            Surface(
                                onClick = { if (current) onChoose(cell) },
                                enabled = current,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                shape = RoundedCornerShape(if (title == "Towers") 8.dp else 14.dp),
                                color = when {
                                    failed -> ShellRed.copy(alpha = 0.34f)
                                    chosen -> ShellGreen.copy(alpha = 0.22f)
                                    current -> accent.copy(alpha = 0.13f)
                                    passed -> ShellPanel2.copy(alpha = 0.55f)
                                    else -> ShellPanel2.copy(alpha = 0.3f)
                                },
                                border = BorderStroke(1.dp, when {
                                    failed -> ShellRed.copy(alpha = 0.7f)
                                    chosen -> ShellGreen.copy(alpha = 0.55f)
                                    current -> accent.copy(alpha = 0.34f)
                                    else -> Color.White.copy(alpha = 0.035f)
                                })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        when {
                                            failed -> "✕"
                                            chosen -> "◆"
                                            current && title == "Towers" -> "▯"
                                            current -> "●"
                                            else -> ""
                                        },
                                        color = when {
                                            failed -> ShellRed
                                            chosen -> ShellGreen
                                            else -> accent
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = if (compact) 11.sp else 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
