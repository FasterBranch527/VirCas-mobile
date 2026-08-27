package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.BlackjackEngine
import com.vircas.mobile.game.engines.BlackjackRound
import com.vircas.mobile.game.engines.BlackjackStatus
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.Rank
import com.vircas.mobile.game.engines.Suit
import kotlinx.coroutines.delay

private enum class BlackjackUiPhase { READY, DEALING, PLAYER_TURN, DEALER_TURN, COMPLETE }

@Composable
fun CasinoBlackjackGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stakeText by remember { mutableStateOf("1000") }
    var wager by remember { mutableStateOf<ActiveWager?>(null) }
    var engine by remember { mutableStateOf<BlackjackEngine?>(null) }
    var round by remember { mutableStateOf<BlackjackRound?>(null) }
    var phase by remember { mutableStateOf(BlackjackUiPhase.READY) }
    var message by remember { mutableStateOf("BLACKJACK PAYS 3:2 · DEALER STANDS ON 17") }
    var playerShown by remember { mutableIntStateOf(0) }
    var dealerShown by remember { mutableIntStateOf(0) }
    var holeRevealed by remember { mutableStateOf(false) }
    var dealSequence by remember { mutableIntStateOf(0) }
    var dealerSequence by remember { mutableIntStateOf(0) }
    var hitSequence by remember { mutableIntStateOf(0) }
    var pendingDealerRound by remember { mutableStateOf<BlackjackRound?>(null) }
    var pendingHitRound by remember { mutableStateOf<BlackjackRound?>(null) }

    fun leave() {
        wager?.let(viewModel::cancelWager)
        wager = null
        onBack()
    }
    BackHandler(onBack = ::leave)

    fun settle(active: ActiveWager, finished: BlackjackRound) {
        val playerScore = BlackjackEngine.score(finished.player).total
        val dealerScore = BlackjackEngine.score(finished.dealer).total
        val title = when (finished.status) {
            BlackjackStatus.BLACKJACK -> "BLACKJACK · 3:2"
            BlackjackStatus.PLAYER_WIN -> if (finished.doubled) "DOUBLE DOWN WIN" else "YOU WIN"
            BlackjackStatus.DEALER_WIN -> if (playerScore > 21) "BUST" else "DEALER WINS"
            BlackjackStatus.PUSH -> "PUSH"
            else -> finished.status.name.replace('_', ' ')
        }
        message = "$title · $playerScore vs $dealerScore"
        viewModel.settleWager(
            active,
            finished.payoutMultiplier,
            finished.status.name,
            "Player $playerScore · Dealer $dealerScore${if (finished.doubled) " · doubled" else ""}"
        )
        wager = null
        engine = null
        phase = BlackjackUiPhase.COMPLETE
    }

    LaunchedEffect(dealSequence) {
        if (dealSequence == 0 || phase != BlackjackUiPhase.DEALING) return@LaunchedEffect
        val active = wager ?: return@LaunchedEffect
        val initial = round ?: return@LaunchedEffect
        playerShown = 0
        dealerShown = 0
        holeRevealed = false
        delay(180)
        playerShown = 1
        delay(230)
        dealerShown = 1
        delay(230)
        playerShown = 2
        delay(230)
        dealerShown = 2
        delay(420)
        if (initial.status == BlackjackStatus.PLAYER_TURN) {
            phase = BlackjackUiPhase.PLAYER_TURN
            message = "YOUR MOVE"
        } else {
            holeRevealed = true
            delay(520)
            settle(active, initial)
        }
    }

    LaunchedEffect(hitSequence) {
        if (hitSequence == 0) return@LaunchedEffect
        val active = wager ?: return@LaunchedEffect
        val next = pendingHitRound ?: return@LaunchedEffect
        phase = BlackjackUiPhase.DEALING
        delay(130)
        playerShown = next.player.size
        delay(430)
        if (next.status == BlackjackStatus.PLAYER_TURN) {
            phase = BlackjackUiPhase.PLAYER_TURN
            val score = BlackjackEngine.score(next.player).total
            message = if (score == 21) "21 · STAND OR DOUBLE IS LOCKED" else "YOUR MOVE · $score"
        } else {
            holeRevealed = true
            delay(380)
            settle(active, next)
        }
        pendingHitRound = null
    }

    LaunchedEffect(dealerSequence) {
        if (dealerSequence == 0) return@LaunchedEffect
        val active = wager ?: return@LaunchedEffect
        val finished = pendingDealerRound ?: return@LaunchedEffect
        phase = BlackjackUiPhase.DEALER_TURN
        message = "DEALER TURN"
        delay(300)
        holeRevealed = true
        delay(520)
        val target = finished.dealer.size
        var shown = dealerShown.coerceAtLeast(2)
        while (shown < target) {
            shown += 1
            dealerShown = shown
            delay(540)
        }
        delay(460)
        settle(active, finished)
        pendingDealerRound = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF176844), Color(0xFF0B3B2A), Color(0xFF041B15)),
                    radius = 1350f
                )
            )
    ) {
        val compact = maxHeight < 710.dp
        val cardWidth = if (compact) 56.dp else 68.dp
        val cardHeight = if (compact) 82.dp else 100.dp
        BlackjackFeltDecoration()

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            BlackjackHeader(balance = balance, onBack = ::leave)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val current = round
                    val dealerScore = current?.let {
                        if (!holeRevealed && it.dealer.isNotEmpty()) {
                            BlackjackEngine.score(listOf(it.dealer.first())).total.toString() + "+?"
                        } else {
                            BlackjackEngine.score(it.dealer.take(dealerShown.coerceAtLeast(1))).total.toString()
                        }
                    } ?: "–"
                    HandLabel("DEALER", dealerScore, accent = Color(0xFFF8D477), compact = compact)
                    Spacer(Modifier.height(if (compact) 3.dp else 7.dp))
                    BlackjackHand(
                        cards = current?.dealer.orEmpty(),
                        shownCount = dealerShown,
                        hideHole = !holeRevealed,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight
                    )

                    Spacer(Modifier.weight(1f))
                    BlackjackStatusPlate(
                        phase = phase,
                        message = message,
                        stake = wager?.stake ?: stakeText.toLongOrNull() ?: 0L,
                        compact = compact
                    )
                    Spacer(Modifier.weight(1f))

                    val playerScore = current?.let { BlackjackEngine.score(it.player.take(playerShown.coerceAtLeast(1))).total.toString() } ?: "–"
                    BlackjackHand(
                        cards = current?.player.orEmpty(),
                        shownCount = playerShown,
                        hideHole = false,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight
                    )
                    Spacer(Modifier.height(if (compact) 3.dp else 7.dp))
                    HandLabel("PLAYER", playerScore, accent = ShellCyan, compact = compact)
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xE6101817),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                shadowElevation = 16.dp
            ) {
                when (phase) {
                    BlackjackUiPhase.READY, BlackjackUiPhase.COMPLETE -> {
                        BlackjackBetPanel(
                            stakeText = stakeText,
                            balance = balance,
                            compact = compact,
                            onStake = { stakeText = it },
                            onDeal = {
                                val stake = stakeText.toLongOrNull()?.takeIf { it > 0L } ?: 0L
                                if (stake <= 0L || stake > balance) {
                                    message = "CHECK YOUR VIRTUAL STAKE"
                                    return@BlackjackBetPanel
                                }
                                phase = BlackjackUiPhase.DEALING
                                message = "DEALING…"
                                holeRevealed = false
                                playerShown = 0
                                dealerShown = 0
                                viewModel.beginWager("Blackjack", stake) { started ->
                                    if (started == null) {
                                        phase = BlackjackUiPhase.READY
                                        message = "COULD NOT START · CHECK BALANCE"
                                    } else {
                                        val created = BlackjackEngine(viewModel.randomProvider())
                                        wager = started
                                        engine = created
                                        round = created.newRound()
                                        dealSequence += 1
                                    }
                                }
                            }
                        )
                    }

                    BlackjackUiPhase.PLAYER_TURN -> {
                        BlackjackActionPanel(
                            canDouble = round?.player?.size == 2 && (wager?.stake ?: Long.MAX_VALUE) <= balance,
                            compact = compact,
                            onHit = {
                                val current = round ?: return@BlackjackActionPanel
                                val currentEngine = engine ?: return@BlackjackActionPanel
                                val next = currentEngine.hit(current)
                                round = next
                                pendingHitRound = next
                                hitSequence += 1
                            },
                            onStand = {
                                val current = round ?: return@BlackjackActionPanel
                                val currentEngine = engine ?: return@BlackjackActionPanel
                                val finished = currentEngine.stand(current)
                                round = finished
                                pendingDealerRound = finished
                                dealerSequence += 1
                            },
                            onDouble = {
                                val active = wager ?: return@BlackjackActionPanel
                                val current = round ?: return@BlackjackActionPanel
                                val currentEngine = engine ?: return@BlackjackActionPanel
                                phase = BlackjackUiPhase.DEALING
                                message = "DOUBLE DOWN"
                                viewModel.increaseWager(active, active.stake) { doubled ->
                                    if (doubled == null) {
                                        phase = BlackjackUiPhase.PLAYER_TURN
                                        message = "NOT ENOUGH BALANCE TO DOUBLE"
                                    } else {
                                        wager = doubled
                                        val finished = currentEngine.double(current)
                                        round = finished
                                        pendingHitRound = finished
                                        hitSequence += 1
                                        if (finished.status != BlackjackStatus.DEALER_WIN || BlackjackEngine.score(finished.player).total <= 21) {
                                            pendingDealerRound = finished
                                        }
                                    }
                                }
                            }
                        )
                    }

                    BlackjackUiPhase.DEALING, BlackjackUiPhase.DEALER_TURN -> {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Casino, null, tint = ShellGold)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                if (phase == BlackjackUiPhase.DEALER_TURN) "DEALER IS PLAYING" else "CARDS IN MOTION",
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = .75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlackjackHeader(balance: Long, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text("BLACKJACK", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Color.White)
            Text("EUROPEAN TABLE · 3:2", color = Color.White.copy(alpha = .48f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = .22f),
            border = BorderStroke(1.dp, ShellGold.copy(alpha = .30f))
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
                Text("BALANCE", color = ShellGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text(formatShellVc(balance), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun BlackjackBetPanel(
    stakeText: String,
    balance: Long,
    compact: Boolean,
    onStake: (String) -> Unit,
    onDeal: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(500L, 1_000L, 5_000L, 10_000L).forEach { amount ->
                Surface(
                    onClick = { if (amount <= balance) onStake(amount.toString()) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (stakeText == amount.toString()) ShellGold.copy(alpha = .18f) else Color.White.copy(alpha = .05f),
                    border = BorderStroke(1.dp, if (stakeText == amount.toString()) ShellGold else Color.White.copy(alpha = .08f))
                ) {
                    Text(
                        if (amount >= 1_000) "${amount / 1_000}K" else amount.toString(),
                        Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        color = if (stakeText == amount.toString()) ShellGold else Color.White.copy(alpha = .72f),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = stakeText,
                onValueChange = { next -> if (next.all(Char::isDigit)) onStake(next.take(12)) },
                label = { Text("Stake VC") },
                singleLine = true,
                modifier = Modifier.weight(.9f)
            )
            Button(
                onClick = onDeal,
                modifier = Modifier.weight(1.1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = Color(0xFF171006))
            ) { Text("DEAL", fontWeight = FontWeight.Black, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun BlackjackActionPanel(
    canDouble: Boolean,
    compact: Boolean,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onHit,
            modifier = Modifier.weight(1f).height(if (compact) 48.dp else 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShellGreen)
        ) { Text("HIT", fontWeight = FontWeight.Black) }
        Button(
            onClick = onStand,
            modifier = Modifier.weight(1f).height(if (compact) 48.dp else 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF344155))
        ) { Text("STAND", fontWeight = FontWeight.Black) }
        OutlinedButton(
            onClick = onDouble,
            enabled = canDouble,
            modifier = Modifier.weight(1f).height(if (compact) 48.dp else 54.dp),
            border = BorderStroke(1.dp, ShellGold.copy(alpha = if (canDouble) .75f else .20f))
        ) { Text("DOUBLE", fontWeight = FontWeight.Black, color = if (canDouble) ShellGold else Color.Gray) }
    }
}

@Composable
private fun HandLabel(label: String, score: String, accent: Color, compact: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = .20f),
        border = BorderStroke(1.dp, accent.copy(alpha = .20f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.width(9.dp))
            Text(score, color = Color.White, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BlackjackStatusPlate(phase: BlackjackUiPhase, message: String, stake: Long, compact: Boolean) {
    val accent = when (phase) {
        BlackjackUiPhase.PLAYER_TURN -> ShellCyan
        BlackjackUiPhase.DEALER_TURN -> ShellGold
        BlackjackUiPhase.COMPLETE -> ShellPurple
        else -> Color.White.copy(alpha = .55f)
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = .18f),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f))
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, color = accent, fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("BET ${formatShellVc(stake)}", color = Color.White.copy(alpha = .45f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlackjackHand(
    cards: List<PlayingCard>,
    shownCount: Int,
    hideHole: Boolean,
    cardWidth: Dp,
    cardHeight: Dp
) {
    val overlap = cardWidth * .57f
    val visible = cards.take(shownCount.coerceAtMost(cards.size))
    val width = if (visible.isEmpty()) cardWidth else cardWidth + overlap * (visible.size - 1)
    Box(Modifier.width(width).height(cardHeight)) {
        cards.forEachIndexed { index, card ->
            AnimatedVisibility(
                visible = index < shownCount,
                modifier = Modifier.offset(x = overlap * index),
                enter = fadeIn(tween(150)) + scaleIn(tween(220), initialScale = .86f) + slideInVertically(tween(260)) { -it / 2 }
            ) {
                CasinoPlayingCard(
                    card = card,
                    hidden = hideHole && index == 1,
                    width = cardWidth,
                    height = cardHeight
                )
            }
        }
    }
}

@Composable
private fun CasinoPlayingCard(card: PlayingCard, hidden: Boolean, width: Dp, height: Dp) {
    Surface(
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(9.dp),
        color = if (hidden) Color(0xFF122744) else Color(0xFFF8F7F2),
        border = BorderStroke(1.dp, if (hidden) ShellGold.copy(alpha = .58f) else Color.Black.copy(alpha = .18f)),
        shadowElevation = 8.dp
    ) {
        if (hidden) {
            Box(
                Modifier.fillMaxSize().padding(4.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF10223A), Color(0xFF213B61), Color(0xFF10223A)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.minDimension / 5f
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(ShellGold.copy(alpha = .17f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1.5f)
                        drawLine(ShellGold.copy(alpha = .10f), Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1.1f)
                        x += step
                    }
                    drawRoundRect(
                        ShellGold.copy(alpha = .30f),
                        style = Stroke(width = 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                    )
                }
                Text("V", color = ShellGold.copy(alpha = .68f), fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
        } else {
            val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
            val ink = if (red) Color(0xFFD62828) else Color(0xFF111827)
            Box(Modifier.fillMaxSize().padding(6.dp)) {
                Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rankLabel(card.rank), color = ink, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 14.sp)
                    Text(suitGlyph(card.suit), color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 12.sp)
                }
                Text(
                    suitGlyph(card.suit),
                    modifier = Modifier.align(Alignment.Center),
                    color = ink.copy(alpha = .90f),
                    fontWeight = FontWeight.Black,
                    fontSize = 31.sp
                )
                Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(suitGlyph(card.suit), color = ink, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = 10.sp)
                    Text(rankLabel(card.rank), color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BlackjackFeltDecoration() {
    Canvas(Modifier.fillMaxSize()) {
        val inset = size.width * .08f
        val top = size.height * .18f
        drawArc(
            color = Color.White.copy(alpha = .08f),
            startAngle = 198f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(inset, top),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height * .65f),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        )
        drawCircle(
            color = ShellGold.copy(alpha = .035f),
            radius = size.minDimension * .34f,
            center = Offset(size.width / 2f, size.height * .48f),
            style = Stroke(width = 28f)
        )
    }
}

private fun rankLabel(rank: Rank): String = when (rank) {
    Rank.TWO -> "2"
    Rank.THREE -> "3"
    Rank.FOUR -> "4"
    Rank.FIVE -> "5"
    Rank.SIX -> "6"
    Rank.SEVEN -> "7"
    Rank.EIGHT -> "8"
    Rank.NINE -> "9"
    Rank.TEN -> "10"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.ACE -> "A"
}

private fun suitGlyph(suit: Suit): String = when (suit) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}
