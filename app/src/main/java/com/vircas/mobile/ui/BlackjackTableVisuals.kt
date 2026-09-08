package com.vircas.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.*

internal val BlackjackInk = Color(0xFF091713)
internal val BlackjackGold = Color(0xFFE1C38D)
internal val BlackjackIvory = Color(0xFFF4F0E5)
internal val BlackjackMuted = Color(0xFFB0C5BA)
internal val BlackjackMint = Color(0xFF91E2BF)
internal val LocalBlackjackReducedMotion = staticCompositionLocalOf { false }

@Composable
fun BlackjackTableBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF235848), Color(0xFF123A30), Color(0xFF0D2923)), Offset(size.width * .5f, size.height * .35f), size.maxDimension * .9f))
        // Fine woven felt, not a wallpaper image or an animated noise field.
        val step = 6.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = .018f), Offset(0f, y), Offset(size.width, y + size.width * .25f), .5.dp.toPx())
            y += step
        }
        val inset = 10.dp.toPx()
        drawRoundRect(BlackjackGold.copy(alpha = .22f), Offset(inset, inset), Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)), androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = Stroke(.8.dp.toPx()))
        drawArc(BlackjackGold.copy(alpha = .13f), 202f, 136f, false, Offset(size.width * .07f, size.height * .18f), Size(size.width * .86f, size.height * .64f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun BlackjackTableHeader(balance: Long, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .06f)) {
            IconButton(onBack, Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "Back", tint = BlackjackIvory) }
        }
        Column(Modifier.weight(1f)) {
            Text("BLACKJACK", color = BlackjackIvory, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = .6.sp, maxLines = 1)
            Text("VIRCAS · CLASSIC", color = BlackjackMuted, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Column(Modifier.weight(.8f), horizontalAlignment = Alignment.End) {
            Text("VIRTUAL BALANCE", color = BlackjackMuted, fontSize = 11.sp, maxLines = 1)
            Text(formatShellVc(balance), color = BlackjackGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = "Virtual balance: $balance VC" })
        }
    }
}

@Composable
fun BlackjackShoe(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp, 34.dp), contentAlignment = Alignment.Center) {
        repeat(3) { i ->
            Box(Modifier.offset((i * 2).dp, (-i * 2).dp).size(30.dp, 23.dp).graphicsLayer { rotationZ = -12f }) {
                BlackjackCardBack()
            }
        }
    }
}

@Composable
fun BlackjackDealerArea(cards: List<PlayingCard>, shownCount: Int, holeRevealed: Boolean, cardWidth: Dp, cardHeight: Dp, compact: Boolean) {
    val visible = cards.take(shownCount.coerceIn(0, cards.size))
    val knownCards = if (!holeRevealed) visible.take(1) else visible
    val score = if (knownCards.isEmpty()) "—" else BlackjackEngine.score(knownCards).let {
        "${it.total}${if (!holeRevealed && visible.size > 1) " + ?" else if (it.soft) " soft" else ""}"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DEALER", color = BlackjackMuted, fontSize = 13.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text(score, color = BlackjackIvory, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            BlackjackShoe()
        }
        BlackjackCardFan(visible, if (!holeRevealed && visible.size > 1) 1 else -1, cardWidth, cardHeight)
    }
}

@Composable
fun BlackjackPlayerArea(hands: List<BlackjackTableHand>, shownCounts: List<Int>, activeIndex: Int, baseStake: Long, cardWidth: Dp, cardHeight: Dp, compact: Boolean) {
    // Two readable columns, never four tiny hands squeezed into a single phone-width row.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        hands.indices.toList().chunked(if (hands.size == 1) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                row.forEach { index ->
                    key(index) {
                        BlackjackPlayerHand(hands[index], index, hands.size, shownCounts.getOrElse(index) { 0 }, index == activeIndex, baseStake, cardWidth, cardHeight, Modifier.weight(1f))
                    }
                }
                if (row.size == 1 && hands.size > 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BlackjackPlayerHand(hand: BlackjackTableHand, index: Int, handCount: Int, shownCount: Int, active: Boolean, baseStake: Long, cardWidth: Dp, cardHeight: Dp, modifier: Modifier) {
    val shown = shownCount.coerceIn(0, hand.cards.size)
    val cards = hand.cards.take(shown)
    val complete = shown == hand.cards.size
    val state = if (complete) hand.state else BlackjackHandState.ACTIVE
    val accent = handStateColor(state, active)
    val score = cards.takeIf { it.isNotEmpty() }?.let(BlackjackEngine::score)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    Surface(
        modifier.bringIntoViewRequester(requester).testTag("blackjack-hand-$index"),
        shape = RoundedCornerShape(16.dp),
        color = if (active) Color(0xFF254D3C) else Color(0xFF102E26),
        border = BorderStroke(if (active) 1.5.dp else 1.dp, accent.copy(alpha = if (active) .9f else .28f))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (handCount == 1) "YOUR HAND" else "HAND ${index + 1}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(score?.total?.toString() ?: "—", color = BlackjackIvory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            BlackjackCardFan(cards, -1, cardWidth, cardHeight)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (hand.betUnits > 1) "2× BET" else "BET", color = BlackjackMuted, fontSize = 12.sp)
                Text(compactAmount(safeStakeAmount(baseStake, hand.betUnits)), color = BlackjackGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                when {
                    active -> if (score?.soft == true) "YOUR MOVE · SOFT" else "YOUR MOVE"
                    !complete -> "DEALING"
                    state == BlackjackHandState.STOOD -> "STANDING"
                    state == BlackjackHandState.ACTIVE -> "WAITING"
                    else -> state.name
                },
                color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp).semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
fun BlackjackRoundStatus(message: String, committedStake: Long, handCount: Int, compact: Boolean) {
    val accent = when {
        message.startsWith("YOU WIN") || message.startsWith("BLACKJACK ·") || message.startsWith("TABLE WIN") -> BlackjackMint
        message == "BUST" || message == "DEALER WINS" || message == "TABLE LOSS" -> Color(0xFFFFB0A3)
        else -> BlackjackGold
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = accent, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("blackjack-status").semantics { liveRegion = LiveRegionMode.Polite })
        Text("${if (handCount > 1) "$handCount hands" else "Table stake"}  ·  ${formatShellVc(committedStake)}", color = BlackjackMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun BlackjackBetControls(stakeText: String, balance: Long, compact: Boolean, onStake: (String) -> Unit, onDeal: () -> Unit) {
    val stake = stakeText.toLongOrNull()
    val valid = stake != null && stake > 0L && stake <= balance
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF12251E), border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CHOOSE YOUR STAKE", color = BlackjackMuted, fontSize = 12.sp, letterSpacing = .8.sp)
                Text("VC ONLY", color = BlackjackMuted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(500L, 1_000L, 5_000L, 10_000L, 50_000L).forEach { amount ->
                    BlackjackStakeChip(amount, stake == amount, amount <= balance) { onStake(amount.toString()) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stakeText, onValueChange = { next -> if (next.all { it in '0'..'9' }) onStake(next.take(15)) },
                    label = { Text("Stake", fontSize = 14.sp) }, suffix = { Text("VC", fontSize = 14.sp, color = BlackjackMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = BlackjackIvory, unfocusedTextColor = BlackjackIvory, focusedBorderColor = BlackjackGold, unfocusedBorderColor = BlackjackMuted.copy(alpha = .45f), focusedLabelColor = BlackjackGold, unfocusedLabelColor = BlackjackMuted, cursorColor = BlackjackGold),
                    modifier = Modifier.weight(1f).testTag("blackjack-stake")
                )
                Button(onClick = onDeal, enabled = valid, modifier = Modifier.weight(1f).height(56.dp).testTag("blackjack-deal"), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlackjackGold, contentColor = BlackjackInk, disabledContainerColor = Color(0xFF33473B), disabledContentColor = BlackjackMuted)) {
                    Text("Deal cards", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            if (!valid && stakeText.isNotEmpty()) Text(if (stake != null && stake > balance) "Stake exceeds your virtual balance" else "Enter a positive stake", color = Color(0xFFFFB0A3), fontSize = 14.sp)
        }
    }
}

@Composable
private fun BlackjackStakeChip(amount: Long, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = when (amount) { 500L -> Color(0xFF3C6984); 1_000L -> Color(0xFF326D52); 5_000L -> Color(0xFF94504A); 10_000L -> Color(0xFF725285); else -> Color(0xFF806637) }
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = "$amount virtual coins${if (selected) ", selected" else ""}" }, shape = CircleShape,
        color = if (enabled) color else color.copy(alpha = .32f), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BlackjackGold else Color.White.copy(alpha = .28f))) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                drawCircle(BlackjackIvory.copy(alpha = if (enabled) .65f else .15f), size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
            }
            Text(compactAmount(amount), color = if (enabled) BlackjackIvory else BlackjackMuted.copy(alpha = .55f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BlackjackActionControls(canDouble: Boolean, canSplit: Boolean, compact: Boolean, onHit: () -> Unit, onStand: () -> Unit, onDouble: () -> Unit, onSplit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlackjackActionButton("Hit", Icons.Rounded.Add, BlackjackMint, true, true, Modifier.weight(1f), onHit)
            BlackjackActionButton("Stand", Icons.Rounded.Stop, BlackjackIvory, true, true, Modifier.weight(1f), onStand)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlackjackActionButton("Double", Icons.Rounded.Layers, BlackjackGold, canDouble, false, Modifier.weight(1f), onDouble)
            BlackjackActionButton("Split", Icons.Rounded.ContentCopy, BlackjackGold, canSplit, false, Modifier.weight(1f), onSplit)
        }
    }
}

@Composable
private fun BlackjackActionButton(label: String, icon: ImageVector, accent: Color, enabled: Boolean, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = modifier.height(48.dp).testTag("blackjack-${label.lowercase()}"), shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp), border = if (filled) null else BorderStroke(1.dp, accent.copy(alpha = if (enabled) .5f else .15f)),
        colors = ButtonDefaults.buttonColors(containerColor = if (filled) accent else Color(0xFF142820), contentColor = if (filled) BlackjackInk else accent, disabledContainerColor = Color(0xFF14201A), disabledContentColor = Color(0xFF748B7D))) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
internal fun BlackjackBusyControls(dealerTurn: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF12251E), border = BorderStroke(1.dp, BlackjackGold.copy(alpha = .18f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (!LocalBlackjackReducedMotion.current) CircularProgressIndicator(Modifier.size(18.dp), color = BlackjackGold, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(if (dealerTurn) "Dealer is playing" else "Dealing your cards", color = BlackjackIvory, fontSize = 16.sp)
        }
    }
}

@Composable
internal fun BlackjackEmptySeat() {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TAKE YOUR SEAT", color = BlackjackIvory, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
        Text("Choose a stake. Make your next move.", color = BlackjackMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { Box(Modifier.size(64.dp, 92.dp).graphicsLayer { alpha = .36f }) { BlackjackCardBack() } }
        }
    }
}

@Composable
private fun BlackjackCardFan(cards: List<PlayingCard>, hiddenIndex: Int, cardWidth: Dp, cardHeight: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
        if (maxWidth <= 0.dp) return@BoxWithConstraints
        val width = minOf(cardWidth, maxWidth)
        val capacity = (1 + ((maxWidth - width).value / (width.value * .42f)).toInt()).coerceIn(1, 5)
        val rows = if (cards.isEmpty()) listOf(emptyList()) else cards.chunked(capacity)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                val step = width * .42f
                Box(Modifier.width(if (row.isEmpty()) width else width + step * (row.size - 1)).height(cardHeight + 10.dp)) {
                    if (row.isEmpty()) {
                        Surface(Modifier.size(width, cardHeight).align(Alignment.Center), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = .07f), border = BorderStroke(1.dp, BlackjackGold.copy(alpha = .14f))) {}
                    }
                    row.forEachIndexed { column, card ->
                        val index = rowIndex * capacity + column
                        key(card.rank, card.suit, index) {
                            BlackjackAnimatedCard(card, index == hiddenIndex, Modifier.offset(x = step * column, y = 4.dp), width, cardHeight,
                                if (row.size > 1) (column - (row.size - 1) / 2f) * 2f else 0f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlackjackAnimatedCard(card: PlayingCard, hidden: Boolean, modifier: Modifier, width: Dp, height: Dp, fanAngle: Float) {
    val reduced = LocalBlackjackReducedMotion.current
    val density = LocalDensity.current.density
    val entrance = remember(card) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(card, reduced) {
        if (reduced) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val flip by animateFloatAsState(if (hidden) 0f else 180f, tween(if (reduced) 0 else 480, easing = FastOutSlowInEasing), label = "blackjack-hole-card")
    Box(modifier.size(width, height).clearAndSetSemantics {
        contentDescription = if (hidden) "Face-down card" else "${card.rank.name.lowercase()} of ${card.suit.name.lowercase()}"
    }.graphicsLayer {
        val p = entrance.value
        translationX = 64f * density * (1f - p)
        translationY = -52f * density * (1f - p)
        rotationZ = fanAngle + 10f * (1f - p)
        rotationY = flip
        cameraDistance = 18f * density
        scaleX = .90f + .10f * p
        scaleY = .90f + .10f * p
        alpha = p
    }) {
        if (flip < 90f) BlackjackCardBack()
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { BlackjackCardFace(card) }
    }
}

@Composable
private fun BlackjackCardBack() {
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(7.dp), color = Color(0xFF0F2B2B), border = BorderStroke(1.dp, BlackjackGold.copy(alpha = .85f)), shadowElevation = 3.dp) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF244D4A), Color(0xFF102B2C), Color(0xFF1B3936))))
            val step = 8.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(BlackjackGold.copy(alpha = .13f), Offset(x, 0f), Offset(x + size.height, size.height), .6.dp.toPx())
                drawLine(BlackjackGold.copy(alpha = .13f), Offset(x + size.height, 0f), Offset(x, size.height), .6.dp.toPx())
                x += step
            }
            drawRect(BlackjackGold.copy(alpha = .5f), style = Stroke(.7.dp.toPx()))
            drawCircle(Color(0xFF102B2C), size.minDimension * .27f, center)
            drawCircle(BlackjackGold.copy(alpha = .7f), size.minDimension * .27f, center, style = Stroke(.8.dp.toPx()))
        }
        Box(contentAlignment = Alignment.Center) { Text("V", color = BlackjackGold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

@Composable
private fun BlackjackCardFace(card: PlayingCard) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("serif", Typeface.BOLD) } }
    val pips = remember(card.rank) { blackjackPipPositions(card.rank) }
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(7.dp), color = BlackjackIvory, border = BorderStroke(.6.dp, Color(0xFFE5DCCC)), shadowElevation = 5.dp) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color(0xFFFFFEF7), Color(0xFFF1EBDC))))
            val w = size.width
            val h = size.height
            val glyph = suitLabel(card.suit)
            paint.color = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) android.graphics.Color.rgb(172, 42, 54) else android.graphics.Color.rgb(23, 40, 36)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                repeat(2) { corner ->
                    native.save()
                    if (corner == 1) native.rotate(180f, w / 2f, h / 2f)
                    paint.textSize = w * .23f
                    native.drawText(rankLabel(card.rank), w * .155f, h * .175f, paint)
                    paint.textSize = w * .19f
                    native.drawText(glyph, w * .155f, h * .30f, paint)
                    native.restore()
                }
                if (pips.isEmpty()) {
                    // Deliberately typographic court cards; no tiny, illegible pseudo-illustrations.
                    paint.textSize = w * .47f
                    native.drawText(rankLabel(card.rank), w * .56f, h * .54f, paint)
                    paint.textSize = w * .25f
                    native.drawText(glyph, w * .56f, h * .74f, paint)
                } else {
                    paint.textSize = if (card.rank == Rank.ACE) w * .50f else w * .22f
                    pips.forEach { pip ->
                        val x = w * pip.x
                        val y = h * pip.y
                        native.save()
                        if (pip.y > .5f && card.rank != Rank.ACE) native.rotate(180f, x, y)
                        native.drawText(glyph, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
                        native.restore()
                    }
                }
            }
        }
    }
}

private fun blackjackPipPositions(rank: Rank): List<Offset> {
    fun pair(y: Float) = listOf(Offset(.40f, y), Offset(.70f, y))
    val middle = Offset(.55f, .50f)
    return when (rank) {
        Rank.ACE -> listOf(middle)
        Rank.TWO -> listOf(Offset(.55f, .27f), Offset(.55f, .73f))
        Rank.THREE -> listOf(Offset(.55f, .27f), middle, Offset(.55f, .73f))
        Rank.FOUR -> pair(.27f) + pair(.73f)
        Rank.FIVE -> pair(.27f) + middle + pair(.73f)
        Rank.SIX -> pair(.25f) + pair(.50f) + pair(.75f)
        Rank.SEVEN -> pair(.25f) + pair(.50f) + pair(.75f) + Offset(.55f, .375f)
        Rank.EIGHT -> pair(.25f) + pair(.50f) + pair(.75f) + listOf(Offset(.55f, .375f), Offset(.55f, .625f))
        Rank.NINE -> pair(.22f) + pair(.40f) + pair(.60f) + pair(.78f) + middle
        Rank.TEN -> pair(.22f) + pair(.40f) + pair(.60f) + pair(.78f) + listOf(Offset(.55f, .31f), Offset(.55f, .69f))
        else -> emptyList()
    }
}

private fun handStateColor(state: BlackjackHandState, active: Boolean): Color = when {
    active -> BlackjackGold
    state == BlackjackHandState.WIN || state == BlackjackHandState.BLACKJACK -> BlackjackMint
    state == BlackjackHandState.LOSS || state == BlackjackHandState.BUST -> Color(0xFFFFB0A3)
    state == BlackjackHandState.PUSH -> Color(0xFFADD4E3)
    else -> BlackjackMuted
}
private fun safeStakeAmount(base: Long, units: Int): Long = if (base > Long.MAX_VALUE / units.coerceAtLeast(1)) Long.MAX_VALUE else base * units
private fun compactAmount(value: Long): String = when {
    value >= 1_000_000L && value % 1_000_000L == 0L -> "${value / 1_000_000L}M"
    value >= 1_000L && value % 1_000L == 0L -> "${value / 1_000L}K"
    else -> "%,d".format(value)
}
private fun rankLabel(rank: Rank): String = when (rank) { Rank.ACE -> "A"; Rank.JACK -> "J"; Rank.QUEEN -> "Q"; Rank.KING -> "K"; else -> rank.pip.toString() }
private fun suitLabel(suit: Suit): String = when (suit) { Suit.CLUBS -> "♣"; Suit.DIAMONDS -> "♦"; Suit.HEARTS -> "♥"; Suit.SPADES -> "♠" }
