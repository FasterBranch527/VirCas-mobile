package com.vircas.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.BlackjackHandState
import com.vircas.mobile.game.engines.BlackjackTableHand
import com.vircas.mobile.game.engines.PlayingCard
import com.vircas.mobile.game.engines.Rank
import com.vircas.mobile.game.engines.Suit
import kotlin.math.min

@Composable
fun BlackjackTableBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val gold = Color(0xFFF0C86A)
        drawArc(
            color = gold.copy(alpha = .19f),
            startAngle = 197f,
            sweepAngle = 146f,
            useCenter = false,
            topLeft = Offset(size.width * .08f, size.height * .18f),
            size = androidx.compose.ui.geometry.Size(size.width * .84f, size.height * .74f),
            style = Stroke(width = 2.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        )
        drawArc(
            color = Color.White.copy(alpha = .045f),
            startAngle = 202f,
            sweepAngle = 136f,
            useCenter = false,
            topLeft = Offset(size.width * .15f, size.height * .25f),
            size = androidx.compose.ui.geometry.Size(size.width * .70f, size.height * .58f),
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
fun BlackjackTableHeader(balance: Long, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text("BLACKJACK", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Color.White)
            Text("3:2 · STAND SOFT 17 · SPLIT ×4", color = Color.White.copy(alpha = .48f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = .24f),
            border = BorderStroke(1.dp, ShellGold.copy(alpha = .34f))
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
                Text("BALANCE", color = ShellGold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text(formatShellVc(balance), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun BlackjackShoe(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 60.dp, height = 42.dp), contentAlignment = Alignment.Center) {
        repeat(4) { index ->
            Surface(
                modifier = Modifier
                    .size(width = 38.dp, height = 28.dp)
                    .offset(x = (index * 3).dp, y = (-index * 2).dp),
                shape = RoundedCornerShape(5.dp),
                color = Color(0xFF261B55),
                border = BorderStroke(1.dp, ShellGold.copy(alpha = .42f))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF362970), Color(0xFF171038), Color(0xFF362970))
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun BlackjackDealerArea(
    cards: List<PlayingCard>,
    shownCount: Int,
    holeRevealed: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    compact: Boolean
) {
    val visible = cards.take(shownCount.coerceAtMost(cards.size))
    val score = when {
        visible.isEmpty() -> "–"
        !holeRevealed && visible.size >= 2 -> "${com.vircas.mobile.game.engines.BlackjackEngine.score(listOf(visible.first())).total}+?"
        else -> com.vircas.mobile.game.engines.BlackjackEngine.score(visible).total.toString()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlackjackHandBadge("DEALER", score, ShellGold)
            Spacer(Modifier.width(10.dp))
            BlackjackShoe()
        }
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        BlackjackCardFan(
            cards = visible,
            hiddenIndex = if (!holeRevealed && visible.size >= 2) 1 else -1,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            dealFromRight = true
        )
    }
}

@Composable
fun BlackjackPlayerArea(
    hands: List<BlackjackTableHand>,
    shownCounts: List<Int>,
    activeIndex: Int,
    baseStake: Long,
    cardWidth: Dp,
    cardHeight: Dp,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (hands.size >= 3) 3.dp else 7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        hands.forEachIndexed { index, hand ->
            val active = index == activeIndex
            val accent = handStateColor(hand.state, active)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
                color = if (active) Color.Black.copy(alpha = .25f) else Color.Black.copy(alpha = .12f),
                border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (active) .95f else .30f)),
                shadowElevation = if (active) 10.dp else 0.dp
            ) {
                Column(
                    Modifier.padding(horizontal = if (hands.size >= 3) 3.dp else 7.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hands.size == 1) "PLAYER" else "HAND ${index + 1}",
                            color = accent,
                            fontSize = if (hands.size >= 3) 8.sp else 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            hand.score.total.toString(),
                            color = Color.White,
                            fontSize = if (hands.size >= 3) 11.sp else 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    BlackjackCardFan(
                        cards = hand.cards.take(shownCounts.getOrElse(index) { hand.cards.size }),
                        hiddenIndex = -1,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        dealFromRight = true
                    )
                    Spacer(Modifier.height(4.dp))
                    BlackjackChipStack(
                        amount = safeStakeAmount(baseStake, hand.betUnits),
                        units = hand.betUnits,
                        compact = hands.size >= 3
                    )
                    if (hand.state != BlackjackHandState.ACTIVE) {
                        Text(
                            hand.state.name,
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlackjackRoundStatus(message: String, committedStake: Long, handCount: Int, compact: Boolean) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xD50A1713),
        border = BorderStroke(1.dp, ShellGold.copy(alpha = .18f))
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Casino, null, tint = ShellGold, modifier = Modifier.size(17.dp))
            Column(Modifier.weight(1f)) {
                Text(message, color = Color.White, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (handCount > 1) "$handCount HANDS LIVE" else "TABLE BET",
                    color = Color.White.copy(alpha = .42f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(formatShellVc(committedStake), color = ShellGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun BlackjackBetControls(
    stakeText: String,
    balance: Long,
    compact: Boolean,
    onStake: (String) -> Unit,
    onDeal: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xE80A1211),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
        shadowElevation = 16.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 9.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(500L, 1_000L, 5_000L, 10_000L, 50_000L).forEach { amount ->
                    val selected = stakeText == amount.toString()
                    Surface(
                        onClick = { if (amount <= balance) onStake(amount.toString()) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        color = if (selected) ShellGold.copy(alpha = .18f) else Color.White.copy(alpha = .045f),
                        border = BorderStroke(1.dp, if (selected) ShellGold else Color.White.copy(alpha = .07f))
                    ) {
                        Text(
                            compactAmount(amount),
                            Modifier.padding(vertical = if (compact) 6.dp else 8.dp),
                            textAlign = TextAlign.Center,
                            color = if (selected) ShellGold else Color.White.copy(alpha = .72f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { next -> if (next.all(Char::isDigit)) onStake(next.take(15)) },
                    label = { Text("Stake VC") },
                    singleLine = true,
                    modifier = Modifier.weight(.78f)
                )
                Button(
                    onClick = onDeal,
                    modifier = Modifier.weight(1.22f).height(if (compact) 48.dp else 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = Color(0xFF171006))
                ) {
                    Text("DEAL", fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                }
            }
        }
    }
}

@Composable
fun BlackjackActionControls(
    canDouble: Boolean,
    canSplit: Boolean,
    compact: Boolean,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xEA091210),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
        shadowElevation = 18.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(if (compact) 8.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlackjackActionButton("HIT", ShellCyan, true, Modifier.weight(1f), onHit)
            BlackjackActionButton("STAND", Color(0xFFE7EAF0), true, Modifier.weight(1f), onStand)
            BlackjackActionButton("2×", ShellGold, canDouble, Modifier.weight(1f), onDouble)
            BlackjackActionButton("SPLIT", ShellPurple, canSplit, Modifier.weight(1f), onSplit)
        }
    }
}

@Composable
private fun BlackjackActionButton(label: String, accent: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) .75f else .18f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 10.sp)
    }
}

@Composable
private fun BlackjackHandBadge(label: String, score: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = Color.Black.copy(alpha = .22f),
        border = BorderStroke(1.dp, accent.copy(alpha = .24f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(7.dp))
            Text(score, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BlackjackCardFan(
    cards: List<PlayingCard>,
    hiddenIndex: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    dealFromRight: Boolean
) {
    val overlap = cardWidth * .56f
    val totalWidth = if (cards.isEmpty()) cardWidth else cardWidth + overlap * (cards.size - 1)
    Box(Modifier.width(totalWidth).height(cardHeight)) {
        cards.forEachIndexed { index, card ->
            androidx.compose.runtime.key("${card.rank}-${card.suit}-$index-${cards.size}") {
                BlackjackAnimatedCard(
                    card = card,
                    hidden = index == hiddenIndex,
                    modifier = Modifier.offset(x = overlap * index),
                    width = cardWidth,
                    height = cardHeight,
                    dealFromRight = dealFromRight
                )
            }
        }
    }
}

@Composable
private fun BlackjackAnimatedCard(
    card: PlayingCard,
    hidden: Boolean,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    dealFromRight: Boolean
) {
    val density = LocalDensity.current.density
    val entrance = remember(card, width, height) { Animatable(0f) }
    LaunchedEffect(card, width, height) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(430, easing = FastOutSlowInEasing))
    }
    val flip by animateFloatAsState(
        targetValue = if (hidden) 0f else 180f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "blackjack-card-flip"
    )
    val showBack = flip < 90f

    Box(
        modifier
            .size(width = width, height = height)
            .graphicsLayer {
                val p = entrance.value
                translationX = (if (dealFromRight) 150f else -150f) * density * (1f - p)
                translationY = -150f * density * (1f - p)
                rotationZ = 13f * (1f - p)
                scaleX = .82f + .18f * p
                scaleY = .82f + .18f * p
                rotationY = flip
                cameraDistance = 13f * density
                alpha = p.coerceIn(0f, 1f)
            }
    ) {
        if (showBack) {
            BlackjackCardBack()
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                BlackjackCardFace(card)
            }
        }
    }
}

@Composable
private fun BlackjackCardBack() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF171036),
        border = BorderStroke(1.5.dp, ShellGold.copy(alpha = .62f)),
        shadowElevation = 5.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(5.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF392A78), Color(0xFF1A1141), Color(0xFF5234A0), Color(0xFF1A1141))
                    ),
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = ShellGold.copy(alpha = .85f), fontWeight = FontWeight.Black, fontSize = 21.sp)
        }
    }
}

@Composable
private fun BlackjackCardFace(card: PlayingCard) {
    val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val ink = if (red) Color(0xFFD72B43) else Color(0xFF111521)
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9F7F0),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = .20f)),
        shadowElevation = 5.dp
    ) {
        Box(Modifier.fillMaxSize().padding(5.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rankLabel(card.rank), color = ink, fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                Text(suitLabel(card.suit), color = ink, fontSize = 13.sp, lineHeight = 13.sp)
            }
            Text(suitLabel(card.suit), Modifier.align(Alignment.Center), color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.align(Alignment.BottomEnd).graphicsLayer { rotationZ = 180f },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(rankLabel(card.rank), color = ink, fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                Text(suitLabel(card.suit), color = ink, fontSize = 13.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun BlackjackChipStack(amount: Long, units: Int, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (compact) 25.dp else 31.dp, height = 18.dp)) {
            repeat(min(4, units + 1)) { index ->
                Surface(
                    modifier = Modifier
                        .size(if (compact) 15.dp else 18.dp)
                        .offset(x = (index * 4).dp, y = (-index).dp),
                    shape = CircleShape,
                    color = if (units > 1) Color(0xFFC14467) else Color(0xFF294B91),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .70f))
                ) {}
            }
        }
        Spacer(Modifier.width(3.dp))
        Text(compactAmount(amount), color = ShellGold, fontSize = if (compact) 8.sp else 9.sp, fontWeight = FontWeight.Black)
    }
}

private fun handStateColor(state: BlackjackHandState, active: Boolean): Color = when {
    active -> ShellGold
    state == BlackjackHandState.WIN || state == BlackjackHandState.BLACKJACK -> ShellGreen
    state == BlackjackHandState.LOSS || state == BlackjackHandState.BUST -> Color(0xFFFF647C)
    state == BlackjackHandState.PUSH -> ShellCyan
    else -> Color.White.copy(alpha = .50f)
}

private fun safeStakeAmount(base: Long, units: Int): Long =
    if (base > Long.MAX_VALUE / units.coerceAtLeast(1)) Long.MAX_VALUE else base * units

private fun compactAmount(value: Long): String = when {
    value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
    value >= 1_000_000L -> "${value / 1_000_000L}M"
    value >= 1_000L -> "${value / 1_000L}K"
    else -> value.toString()
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

private fun suitLabel(suit: Suit): String = when (suit) {
    Suit.CLUBS -> "♣"
    Suit.DIAMONDS -> "♦"
    Suit.HEARTS -> "♥"
    Suit.SPADES -> "♠"
}
