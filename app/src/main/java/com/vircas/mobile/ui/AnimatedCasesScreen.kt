package com.vircas.mobile.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.*
import kotlinx.coroutines.delay

private const val CASE_SPIN_MS = 8200
private val CaseCardWidth = 168.dp
private val CaseGap = 8.dp
private val ReelPadding = 18.dp

@Composable
fun AnimatedCasesHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val reelState = rememberLazyListState()
    val density = LocalDensity.current
    var selected by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf<CaseOpeningResult?>(null) }
    var reveal by remember { mutableStateOf<CaseItemTemplate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose a cache. The result is locked before the reel moves.") }

    val definition = CasesEngine.All[selected]
    val displayedReel = opening?.reel ?: previewReel(definition.items)

    LaunchedEffect(opening) {
        val result = opening ?: return@LaunchedEffect
        reveal = null
        status = "ROLLING // ${(CASE_SPIN_MS / 1000f)}s cinematic reel"
        reelState.scrollToItem(0)
        delay(180)

        val viewport = reelState.layoutInfo.viewportSize.width.toFloat()
        val card = with(density) { CaseCardWidth.toPx() }
        val gap = with(density) { CaseGap.toPx() }
        val pad = with(density) { ReelPadding.toPx() }
        val bias = (((result.item.id.hashCode() and 0x7fffffff) % 31) - 15) / 100f * card
        val target = (
            pad + result.winningIndex * (card + gap) + card / 2f - viewport / 2f + bias
        ).coerceAtLeast(0f)

        // One long CS-style reel: hard initial velocity, then a very long viscous slowdown.
        reelState.animateScrollBy(
            target,
            tween(
                durationMillis = CASE_SPIN_MS,
                easing = CubicBezierEasing(0.055f, 0.76f, 0.105f, 1f)
            )
        )

        // Tiny mechanical settle keeps the final stop from looking digitally perfect.
        val settle = with(density) { 5.dp.toPx() }
        reelState.animateScrollBy(-settle, tween(120, easing = LinearEasing))
        reelState.animateScrollBy(settle, tween(260, easing = FastOutSlowInEasing))
        delay(240)

        reveal = result.item
        status = "DROP LOCKED // ${result.item.rarity.name}"
        busy = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, ShellDeep))),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ShellBackHeader("Case Drop", "Long reel · PNG items · local-only virtual inventory", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIRTUAL BALANCE", color = ShellCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(formatShellVc(balance), fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ShellGreen.copy(alpha = .10f),
                    border = BorderStroke(1.dp, ShellGreen.copy(alpha = .22f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Inventory2, null, Modifier.size(17.dp), tint = ShellGreen)
                        Spacer(Modifier.width(6.dp))
                        Text("LOCAL ITEMS", color = ShellGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item { SectionHeader("Choose cache", "Tap a case, then open it") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), userScrollEnabled = !busy) {
                items(CasesEngine.All.size) { index ->
                    val c = CasesEngine.All[index]
                    val active = selected == index
                    Surface(
                        onClick = {
                            if (!busy) {
                                selected = index
                                reveal = null
                                opening = null
                            }
                        },
                        modifier = Modifier.width(150.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (active) Color(0xFF241A3B) else ShellPanel,
                        border = BorderStroke(1.dp, if (active) ShellPurple else Color.White.copy(alpha = .06f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = if (active) ShellGold else ShellPurple)
                            Spacer(Modifier.height(4.dp))
                            Text(c.title, fontWeight = FontWeight.Black)
                            Text(formatShellVc(c.cost), color = ShellGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Drop reel", "The gold line is the winning position") }
        item { CaseReel(displayedReel, opening?.winningIndex, busy, reelState) }

        item {
            Button(
                enabled = !busy && balance >= definition.cost,
                onClick = {
                    busy = true
                    reveal = null
                    status = "LOCKING OUTCOME…"
                    viewModel.openCase(definition) { result ->
                        if (result == null) {
                            busy = false
                            status = "Could not open cache. Check virtual balance."
                        } else {
                            opening = result
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShellPurple)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (busy) "OPENING…" else "OPEN · ${formatShellVc(definition.cost)}",
                    fontWeight = FontWeight.Black
                )
            }
        }

        item {
            reveal?.let { WinnerCard(it) } ?: Surface(
                shape = RoundedCornerShape(18.dp),
                color = ShellPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = .05f))
            ) {
                Text(
                    status,
                    Modifier.fillMaxWidth().padding(15.dp),
                    color = Color.White.copy(alpha = .60f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CaseReel(
    reel: List<CaseItemTemplate>,
    winningIndex: Int?,
    busy: Boolean,
    state: androidx.compose.foundation.lazy.LazyListState
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B0F18))
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = ReelPadding),
            horizontalArrangement = Arrangement.spacedBy(CaseGap),
            userScrollEnabled = !busy,
            modifier = Modifier.fillMaxSize()
        ) {
            items(reel.size, key = { "$it-${reel[it].id}" }) { index ->
                ReelItem(reel[index], winner = winningIndex == index && !busy)
            }
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(46.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF070A10), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(46.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF070A10))))
        )

        // CS-style fixed winning marker. The item reel moves below it.
        Box(Modifier.align(Alignment.Center).fillMaxHeight().width(2.dp).background(ShellGold))
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            color = ShellGold
        ) { Spacer(Modifier.width(30.dp).height(7.dp)) }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            color = ShellGold
        ) { Spacer(Modifier.width(30.dp).height(7.dp)) }
    }
}

@Composable
private fun ReelItem(item: CaseItemTemplate, winner: Boolean) {
    val rarity = rarityColor(item.rarity)
    Surface(
        modifier = Modifier.width(CaseCardWidth).height(164.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (winner) rarity.copy(alpha = .19f) else Color(0xFF141B28),
        border = BorderStroke(if (winner) 2.dp else 1.dp, rarity.copy(alpha = if (winner) .95f else .30f))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.verticalGradient(listOf(rarity.copy(alpha = .14f), Color.Transparent)))
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                CaseItemArtwork(item.previewKey, Modifier.fillMaxWidth().height(78.dp))
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(rarity))
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(item.weaponCategory.uppercase(), color = rarity, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text(
                    item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatShellVc(item.marketValue), color = Color.White.copy(alpha = .48f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun WinnerCard(item: CaseItemTemplate) {
    val rarity = rarityColor(item.rarity)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = rarity.copy(alpha = .11f),
        border = BorderStroke(1.dp, rarity.copy(alpha = .48f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0A0F18)) {
                CaseItemArtwork(item.previewKey, Modifier.width(112.dp).height(72.dp).padding(6.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOU UNBOXED", color = rarity, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(item.name, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(
                    "${item.weaponCategory} · ${item.rarity.name}",
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 11.sp
                )
                Text(
                    "Virtual value ${formatShellVc(item.marketValue)}",
                    color = ShellGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun previewReel(items: List<CaseItemTemplate>) = List(18) { items[it % items.size] }

private fun rarityColor(rarity: ItemRarity): Color = when (rarity) {
    ItemRarity.COMMON -> Color(0xFF8D99AE)
    ItemRarity.UNCOMMON -> Color(0xFF4CC9F0)
    ItemRarity.RARE -> Color(0xFF4361EE)
    ItemRarity.EPIC -> Color(0xFF9B5DE5)
    ItemRarity.LEGENDARY -> Color(0xFFF72585)
    ItemRarity.MYTHIC -> Color(0xFFFFC857)
}
