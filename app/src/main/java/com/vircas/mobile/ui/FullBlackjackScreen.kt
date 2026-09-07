package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.BlackjackHandState
import com.vircas.mobile.game.engines.BlackjackTableEngine
import com.vircas.mobile.game.engines.BlackjackTableRound
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FullBlackjackPhase { READY, DEALING, PLAYER_TURN, DEALER_TURN, COMPLETE }

@Composable
fun FullBlackjackGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val scope = viewModel.roundTaskScope("blackjack")

    var stakeText by rememberRound(viewModel, "blackjack:stakeText") { mutableStateOf("1000") }
    var baseStake by rememberRound(viewModel, "blackjack:baseStake") { mutableLongStateOf(1_000L) }
    var committedStake by rememberRound(viewModel, "blackjack:committedStake") { mutableLongStateOf(1_000L) }
    var wager by rememberRound(viewModel, "blackjack:wager") { mutableStateOf<ActiveWager?>(null) }
    var engine by rememberRound(viewModel, "blackjack:engine") { mutableStateOf<BlackjackTableEngine?>(null) }
    var round by rememberRound(viewModel, "blackjack:round") { mutableStateOf<BlackjackTableRound?>(null) }
    var phase by rememberRound(viewModel, "blackjack:phase") { mutableStateOf(FullBlackjackPhase.READY) }
    var message by rememberRound(viewModel, "blackjack:message") { mutableStateOf("BLACKJACK PAYS 3:2 · DEALER STANDS SOFT 17") }
    var shownCounts by rememberRound(viewModel, "blackjack:shownCounts") { mutableStateOf<List<Int>>(emptyList()) }
    var dealerShown by rememberRound(viewModel, "blackjack:dealerShown") { mutableIntStateOf(0) }
    var holeRevealed by rememberRound(viewModel, "blackjack:holeRevealed") { mutableStateOf(false) }

    fun leave() {
        // Navigation must not turn a visible hand into a refund. Resume this hand on return.
        onBack()
    }
    BackHandler(onBack = ::leave)

    suspend fun checkpoint(finished: BlackjackTableRound) {
        if (!finished.isComplete) return
        val active = wager ?: return
        viewModel.checkpointWager(
            active,
            finished.payoutMultiplier,
            blackjackResultLabel(finished),
            blackjackResultDetails(finished)
        ).join()
    }

    suspend fun settle(finished: BlackjackTableRound) {
        // Read the retained wager here, after any split/double revision and animation.
        val active = wager ?: return
        checkpoint(finished)
        val resultLabel = blackjackResultLabel(finished)
        round = finished
        shownCounts = finished.hands.map { it.cards.size }
        dealerShown = finished.dealer.size
        holeRevealed = true
        message = resultLabel
        viewModel.settleWager(
            active, finished.payoutMultiplier, resultLabel, blackjackResultDetails(finished)
        ).join()
        wager = null
        engine = null
        phase = FullBlackjackPhase.COMPLETE
    }

    suspend fun dealerTurn(from: BlackjackTableRound) {
        if (wager == null) return
        val currentEngine = engine ?: return
        val finished = currentEngine.playDealer(from)
        // Persist the complete result before revealing the hole card or any dealer draws.
        checkpoint(finished)
        phase = FullBlackjackPhase.DEALER_TURN
        message = "DEALER REVEALS"
        delay(260)
        holeRevealed = true
        delay(560)

        // Expose the dealer's final cards immediately, but gate their visibility one by one.
        round = finished.copy(hands = from.hands)
        var shown = dealerShown.coerceAtLeast(2)
        while (shown < finished.dealer.size) {
            shown += 1
            dealerShown = shown
            message = "DEALER DRAWS · ${com.vircas.mobile.game.engines.BlackjackEngine.score(finished.dealer.take(shown)).total}"
            delay(560)
        }
        delay(430)
        settle(finished)
    }

    suspend fun continueAfterAction(next: BlackjackTableRound, activeText: String = "YOUR MOVE") {
        if (next.isPlayerTurn) {
            phase = FullBlackjackPhase.PLAYER_TURN
            val hand = next.hands[next.activeHandIndex]
            message = "$activeText · HAND ${next.activeHandIndex + 1} · ${hand.score.total}"
        } else {
            dealerTurn(next)
        }
    }

    LaunchedEffect(Unit) {
        // A retained task also covers pending begin/increase callbacks, not just card delays.
        if (viewModel.hasRoundTask("blackjack")) return@LaunchedEffect
        val retained = round ?: return@LaunchedEffect
        if (wager == null) {
            if (phase == FullBlackjackPhase.COMPLETE) {
                shownCounts = retained.hands.map { it.cards.size }
                dealerShown = retained.dealer.size
                holeRevealed = true
            }
        } else if (retained.isPlayerTurn) {
            shownCounts = retained.hands.map { it.cards.size }
            dealerShown = retained.dealer.size
            holeRevealed = false
            phase = FullBlackjackPhase.PLAYER_TURN
            message = "YOUR MOVE · HAND ${retained.activeHandIndex + 1} · ${retained.hands[retained.activeHandIndex].score.total}"
        } else {
            scope.launch {
                if (retained.isComplete) {
                    settle(retained)
                } else {
                    shownCounts = retained.hands.map { it.cards.size }
                    dealerShown = retained.dealer.size
                    dealerTurn(retained)
                }
            }
        }
    }

    fun deal() {
        if (wager != null || viewModel.hasRoundTask("blackjack") ||
            (phase != FullBlackjackPhase.READY && phase != FullBlackjackPhase.COMPLETE)) return
        val stake = stakeText.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        if (stake <= 0L || stake > balance) {
            message = "CHECK YOUR VIRTUAL STAKE"
            return
        }
        phase = FullBlackjackPhase.DEALING
        message = "SHUFFLING…"
        shownCounts = emptyList()
        dealerShown = 0
        holeRevealed = false

        scope.launch {
            var startedWager: ActiveWager? = null
            viewModel.beginWager("Blackjack", stake) { startedWager = it }.join()
            val started = startedWager
            if (started == null) {
                phase = FullBlackjackPhase.READY
                message = "COULD NOT START · CHECK BALANCE"
                return@launch
            }
            val created = BlackjackTableEngine(viewModel.randomProvider())
            val initial = created.newRound()
            baseStake = stake
            committedStake = started.stake
            wager = started
            engine = created
            round = initial
            shownCounts = listOf(0)
            checkpoint(initial)

            message = "DEALING"
            delay(150)
            shownCounts = listOf(1)
            delay(235)
            dealerShown = 1
            delay(235)
            shownCounts = listOf(2)
            delay(235)
            dealerShown = 2
            delay(430)
            if (initial.isPlayerTurn) {
                phase = FullBlackjackPhase.PLAYER_TURN
                message = "YOUR MOVE · ${initial.hands.first().score.total}"
            } else {
                dealerTurn(initial)
            }
        }
    }

    fun hit() {
        val current = round ?: return
        val currentEngine = engine ?: return
        if (!current.isPlayerTurn || phase != FullBlackjackPhase.PLAYER_TURN) return
        val index = current.activeHandIndex
        val next = currentEngine.hit(current)
        round = next
        phase = FullBlackjackPhase.DEALING
        message = "HIT"
        scope.launch {
            checkpoint(next)
            delay(110)
            shownCounts = shownCounts.withCount(index, next.hands[index].cards.size, next.hands.size)
            delay(440)
            continueAfterAction(next)
        }
    }

    fun stand() {
        val current = round ?: return
        val currentEngine = engine ?: return
        if (!current.isPlayerTurn || phase != FullBlackjackPhase.PLAYER_TURN) return
        val next = currentEngine.stand(current)
        round = next
        phase = FullBlackjackPhase.DEALING
        message = "STAND"
        scope.launch {
            delay(250)
            continueAfterAction(next)
        }
    }

    fun doubleDown() {
        val active = wager ?: return
        val current = round ?: return
        val currentEngine = engine ?: return
        if (!currentEngine.canDouble(current) || balance < baseStake || phase != FullBlackjackPhase.PLAYER_TURN) return
        val index = current.activeHandIndex
        phase = FullBlackjackPhase.DEALING
        message = "DOUBLE DOWN"
        scope.launch {
            var increasedWager: ActiveWager? = null
            viewModel.increaseWager(active, baseStake) { increasedWager = it }.join()
            val increased = increasedWager
            if (increased == null) {
                phase = FullBlackjackPhase.PLAYER_TURN
                message = "NOT ENOUGH BALANCE TO DOUBLE"
                return@launch
            }
            wager = increased
            committedStake = increased.stake
            val next = currentEngine.double(current)
            round = next
            checkpoint(next)
            delay(120)
            shownCounts = shownCounts.withCount(index, next.hands[index].cards.size, next.hands.size)
            delay(480)
            continueAfterAction(next, "NEXT HAND")
        }
    }

    fun split() {
        val active = wager ?: return
        val current = round ?: return
        val currentEngine = engine ?: return
        if (!currentEngine.canSplit(current) || balance < baseStake || phase != FullBlackjackPhase.PLAYER_TURN) return
        val index = current.activeHandIndex
        phase = FullBlackjackPhase.DEALING
        message = "SPLITTING HAND"
        scope.launch {
            var increasedWager: ActiveWager? = null
            viewModel.increaseWager(active, baseStake) { increasedWager = it }.join()
            val increased = increasedWager
            if (increased == null) {
                phase = FullBlackjackPhase.PLAYER_TURN
                message = "NOT ENOUGH BALANCE TO SPLIT"
                return@launch
            }
            wager = increased
            committedStake = increased.stake
            val next = currentEngine.split(current)
            round = next
            shownCounts = shownCounts.afterSplit(index, next.hands.size)
            checkpoint(next)
            delay(190)
            shownCounts = shownCounts.withCount(index, 2, next.hands.size)
            delay(280)
            shownCounts = shownCounts.withCount(index + 1, 2, next.hands.size)
            delay(420)
            if (next.hands[index].cards.first().rank == com.vircas.mobile.game.engines.Rank.ACE) {
                message = "SPLIT ACES · ONE CARD EACH"
                delay(350)
            }
            continueAfterAction(next)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF18734B), Color(0xFF0B422E), Color(0xFF041C16)),
                    radius = 1450f
                )
            )
    ) {
        val currentRound = round
        val handCount = currentRound?.hands?.size ?: 1
        val compact = maxHeight < 710.dp || maxWidth < 360.dp
        val dealerCardWidth = if (compact) 54.dp else 64.dp
        val dealerCardHeight = if (compact) 78.dp else 94.dp
        val playerCardWidth = when {
            handCount >= 4 -> 34.dp
            handCount == 3 -> 40.dp
            handCount == 2 -> if (compact) 48.dp else 54.dp
            compact -> 56.dp
            else -> 66.dp
        }
        val playerCardHeight = playerCardWidth * 1.46f

        BlackjackTableBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
        ) {
            BlackjackTableHeader(balance, ::leave)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BlackjackDealerArea(
                        cards = currentRound?.dealer.orEmpty(),
                        shownCount = dealerShown,
                        holeRevealed = holeRevealed,
                        cardWidth = dealerCardWidth,
                        cardHeight = dealerCardHeight,
                        compact = compact
                    )

                    Spacer(Modifier.weight(.55f))
                    BlackjackRoundStatus(
                        message = message,
                        committedStake = committedStake,
                        handCount = handCount,
                        compact = compact
                    )
                    Spacer(Modifier.weight(.45f))

                    if (currentRound != null) {
                        BlackjackPlayerArea(
                            hands = currentRound.hands,
                            shownCounts = shownCounts,
                            activeIndex = if (phase == FullBlackjackPhase.PLAYER_TURN) currentRound.activeHandIndex else -1,
                            baseStake = baseStake,
                            cardWidth = playerCardWidth,
                            cardHeight = playerCardHeight,
                            compact = compact
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color.Black.copy(alpha = .12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .05f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = if (compact) 18.dp else 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.Casino, null, tint = ShellGold)
                                Spacer(Modifier.height(6.dp))
                                Text("PLACE YOUR BET", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                Text("Split pairs · Double after split · 3:2 natural", color = Color.White.copy(alpha = .45f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            when (phase) {
                FullBlackjackPhase.READY, FullBlackjackPhase.COMPLETE -> BlackjackBetControls(
                    stakeText = stakeText,
                    balance = balance,
                    compact = compact,
                    onStake = { stakeText = it },
                    onDeal = ::deal
                )

                FullBlackjackPhase.PLAYER_TURN -> {
                    val liveRound = round
                    val liveEngine = engine
                    BlackjackActionControls(
                        canDouble = liveRound != null && liveEngine?.canDouble(liveRound) == true && balance >= baseStake,
                        canSplit = liveRound != null && liveEngine?.canSplit(liveRound) == true && balance >= baseStake,
                        compact = compact,
                        onHit = ::hit,
                        onStand = ::stand,
                        onDouble = ::doubleDown,
                        onSplit = ::split
                    )
                }

                FullBlackjackPhase.DEALING, FullBlackjackPhase.DEALER_TURN -> Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xE80A1211),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Casino, null, tint = ShellGold)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            if (phase == FullBlackjackPhase.DEALER_TURN) "DEALER IS PLAYING" else "CARDS IN MOTION",
                            color = Color.White.copy(alpha = .74f),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun blackjackResultDetails(round: BlackjackTableRound): String = buildString {
    append("Dealer ").append(com.vircas.mobile.game.engines.BlackjackEngine.score(round.dealer).total)
    round.hands.forEachIndexed { index, hand ->
        append(" · H").append(index + 1)
            .append(' ').append(hand.score.total)
            .append(' ').append(hand.state.name)
            .append(" x").append(hand.betUnits)
    }
}

private fun blackjackResultLabel(round: BlackjackTableRound): String {
    val blackjacks = round.hands.count { it.state == BlackjackHandState.BLACKJACK }
    val wins = round.hands.count { it.state == BlackjackHandState.WIN || it.state == BlackjackHandState.BLACKJACK }
    val losses = round.hands.count { it.state == BlackjackHandState.LOSS || it.state == BlackjackHandState.BUST }
    val pushes = round.hands.count { it.state == BlackjackHandState.PUSH }
    return when {
        round.hands.size == 1 && blackjacks == 1 -> "BLACKJACK · 3:2"
        round.hands.size == 1 && wins == 1 -> "YOU WIN"
        round.hands.size == 1 && losses == 1 -> if (round.hands.first().state == BlackjackHandState.BUST) "BUST" else "DEALER WINS"
        round.hands.size == 1 && pushes == 1 -> "PUSH"
        wins > 0 && losses == 0 -> "TABLE WIN · $wins/${round.hands.size}"
        losses > 0 && wins == 0 && pushes == 0 -> "TABLE LOSS"
        else -> "SPLIT RESULT · $wins W / $losses L / $pushes P"
    }
}

private fun List<Int>.withCount(index: Int, value: Int, targetSize: Int): List<Int> {
    val out = MutableList(targetSize) { position -> getOrElse(position) { 0 } }
    if (index in out.indices) out[index] = value
    return out
}

private fun List<Int>.afterSplit(index: Int, targetSize: Int): List<Int> {
    val out = toMutableList()
    while (out.size < index + 1) out += 0
    if (index in out.indices) out.removeAt(index)
    out.add(index, 1)
    out.add(index + 1, 1)
    while (out.size < targetSize) out += 0
    while (out.size > targetSize) out.removeAt(out.lastIndex)
    return out
}
