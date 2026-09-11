package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val settings by viewModel.settings.collectAsState()
    val scope = viewModel.roundTaskScope("blackjack")

    var stakeText by rememberRound(viewModel, "blackjack:stakeText") { mutableStateOf("1000") }
    var baseStake by rememberRound(viewModel, "blackjack:baseStake") { mutableLongStateOf(1_000L) }
    var committedStake by rememberRound(viewModel, "blackjack:committedStake") { mutableLongStateOf(1_000L) }
    var wager by rememberRound(viewModel, "blackjack:wager") { mutableStateOf<ActiveWager?>(null) }
    var engine by rememberRound(viewModel, "blackjack:engine") { mutableStateOf<BlackjackTableEngine?>(null) }
    var round by rememberRound(viewModel, "blackjack:round") { mutableStateOf<BlackjackTableRound?>(null) }
    var phase by rememberRound(viewModel, "blackjack:phase") { mutableStateOf(FullBlackjackPhase.READY) }
    var message by rememberRound(viewModel, "blackjack:message") { mutableStateOf("WELCOME TO THE TABLE") }
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

    BlackjackTableScene(
        balance = balance,
        round = round,
        dealerShown = dealerShown,
        holeRevealed = holeRevealed,
        shownCounts = shownCounts,
        activeIndex = if (phase == FullBlackjackPhase.PLAYER_TURN) round?.activeHandIndex ?: -1 else -1,
        baseStake = baseStake,
        committedStake = if (round == null) stakeText.toLongOrNull() ?: 0L else committedStake,
        message = message,
        reducedMotion = settings.reducedMotion || !settings.animations,
        onBack = ::leave
    ) {
        when (phase) {
            FullBlackjackPhase.READY, FullBlackjackPhase.COMPLETE -> BlackjackBetControls(
                stakeText = stakeText, balance = balance, compact = false,
                onStake = { stakeText = it }, onDeal = ::deal
            )
            FullBlackjackPhase.PLAYER_TURN -> {
                val liveRound = round
                val liveEngine = engine
                BlackjackActionControls(
                    canDouble = liveRound != null && liveEngine?.canDouble(liveRound) == true && balance >= baseStake,
                    canSplit = liveRound != null && liveEngine?.canSplit(liveRound) == true && balance >= baseStake,
                    compact = false,
                    onHit = ::hit, onStand = ::stand, onDouble = ::doubleDown, onSplit = ::split
                )
            }
            FullBlackjackPhase.DEALING, FullBlackjackPhase.DEALER_TURN -> BlackjackBusyControls(phase == FullBlackjackPhase.DEALER_TURN)
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
