package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.BlackjackTableHand
import com.vircas.mobile.game.engines.BlackjackTableRound
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.Rank
import com.vircas.mobile.game.engines.Suit

/** Layout only. FullBlackjackScreen's retained controller still owns every game action. */
@Composable
internal fun BlackjackTableScene(
    balance: Long,
    round: BlackjackTableRound?,
    dealerShown: Int,
    holeRevealed: Boolean,
    shownCounts: List<Int>,
    activeIndex: Int,
    baseStake: Long,
    committedStake: Long,
    message: String,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    controls: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalBlackjackReducedMotion provides reducedMotion) {
        BoxWithConstraints(Modifier.fillMaxSize().background(BlackjackInk).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val landscape = maxWidth > maxHeight * 1.25f && maxWidth >= 600.dp
            val compact = maxHeight < 660.dp || maxWidth < 360.dp
            // Capture constraints before entering ColumnScope: Compose DSL receivers are isolated.
            val controlsMaxHeight = (maxHeight * .48f).coerceAtLeast(120.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlackjackTableHeader(balance, onBack)
                Text("Blackjack pays 3:2  ·  Dealer stands on 17", color = BlackjackMuted, fontSize = 14.sp)
                if (landscape) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BlackjackFeltTable(round, dealerShown, holeRevealed, shownCounts, activeIndex, baseStake, committedStake, message, true, Modifier.weight(1.65f).fillMaxHeight(), landscape = true)
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BlackjackRoundStatus(message, committedStake, round?.hands?.size?.coerceAtLeast(1) ?: 1, true)
                            controls()
                            Text("Offline play · virtual coins only", color = BlackjackMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    BlackjackFeltTable(round, dealerShown, holeRevealed, shownCounts, activeIndex, baseStake, committedStake, message, compact, Modifier.weight(1f).fillMaxWidth())
                    // Long split hands scroll above pinned controls; keyboard-height controls can scroll too.
                    Box(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState())) { controls() }
                }
            }
        }
    }
}

@Composable
private fun BlackjackFeltTable(
    round: BlackjackTableRound?, dealerShown: Int, holeRevealed: Boolean, shownCounts: List<Int>,
    activeIndex: Int, baseStake: Long, committedStake: Long, message: String, compact: Boolean,
    modifier: Modifier, landscape: Boolean = false
) {
    val hands = round?.hands.orEmpty()
    val dealerWidth = if (landscape) 56.dp else if (compact) 62.dp else 70.dp
    val playerWidth = if (landscape || hands.size > 1) 60.dp else if (compact) 72.dp else 80.dp
    Surface(modifier.testTag("blackjack-felt"), shape = RoundedCornerShape(28.dp), color = BlackjackInk, border = BorderStroke(1.dp, BlackjackGold.copy(alpha = .32f))) {
        BoxWithConstraints {
            val fillHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
            BlackjackTableBackdrop()
            if (landscape && hands.size <= 1) {
                // Both dealer and player stay visible on a short, wide display.
                Row(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).heightIn(min = fillHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        BlackjackDealerArea(round?.dealer.orEmpty(), dealerShown, holeRevealed, dealerWidth, dealerWidth * 1.46f, true)
                    }
                    Box(Modifier.weight(1f)) {
                        if (round == null) BlackjackEmptySeat()
                        else BlackjackPlayerArea(hands, shownCounts, activeIndex, baseStake, playerWidth, playerWidth * 1.46f, true)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).heightIn(min = fillHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    BlackjackDealerArea(round?.dealer.orEmpty(), dealerShown, holeRevealed, dealerWidth, dealerWidth * 1.46f, compact)
                    if (!landscape) Box(Modifier.fillMaxWidth().padding(vertical = 16.dp)) { BlackjackRoundStatus(message, committedStake, hands.size.coerceAtLeast(1), compact) }
                    else Spacer(Modifier.height(16.dp))
                    if (round == null) BlackjackEmptySeat()
                    else BlackjackPlayerArea(hands, shownCounts, activeIndex, baseStake, playerWidth, playerWidth * 1.46f, compact)
                }
            }
        }
    }
}

// Native previews without a wallet or database. Sample cards never enter a production round.
@Preview(name = "Blackjack ready", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BlackjackReadyPreview() {
    MaterialTheme {
        BlackjackTableScene(48_500L, null, 0, false, emptyList(), -1, 1_000L, 1_000L, "WELCOME TO THE TABLE", true, {}) {
            BlackjackBetControls("1000", 48_500L, false, {}, {})
        }
    }
}

@Preview(name = "Four split hands", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Blackjack landscape", widthDp = 844, heightDp = 390, showBackground = true)
@Composable
private fun BlackjackSplitPreview() {
    val hand = BlackjackTableHand(listOf(PlayingCard(Rank.EIGHT, Suit.SPADES), PlayingCard(Rank.KING, Suit.HEARTS)), fromSplit = true)
    val round = BlackjackTableRound(List(4) { hand }, listOf(PlayingCard(Rank.SIX, Suit.CLUBS), PlayingCard(Rank.TEN, Suit.DIAMONDS)), emptyList(), 2)
    MaterialTheme {
        BlackjackTableScene(44_500L, round, 2, false, List(4) { 2 }, 2, 1_000L, 4_000L, "YOUR MOVE · HAND 3 · 18", true, {}) {
            BlackjackActionControls(true, false, false, {}, {}, {}, {})
        }
    }
}
