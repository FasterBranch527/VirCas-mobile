package com.vircas.mobile.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.CaseItemTemplate
import com.vircas.mobile.game.engines.CaseOpeningResult
import com.vircas.mobile.game.engines.CasesEngine
import com.vircas.mobile.game.engines.ItemRarity
import kotlinx.coroutines.delay

@Composable
fun AnimatedCasesHubScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    val reelState = rememberLazyListState()
    var selectedCase by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf<CaseOpeningResult?>(null) }
    var animating by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf("Choose a fictional cache.") }

    LaunchedEffect(opening) {
        val result = opening ?: return@LaunchedEffect
        animating = true
        reveal = "Opening ${CasesEngine.All[selectedCase].title}…"
        reelState.scrollToItem(0)
        delay(120)
        val target = (result.winningIndex - 1).coerceAtLeast(0)
        reelState.animateScrollToItem(target, scrollOffset = -90)
        delay(350)
        reveal = "${result.item.name} · ${result.item.rarity.name} · virtual value ${result.item.marketValue} VC"
        animating = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !animating) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Cases", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Fictional skins · local drops only", color = Color(0xFF94A3B8))
                }
                Text("%,d VC".format(balance), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CasesEngine.All.size) { index ->
                    val definition = CasesEngine.All[index]
                    Surface(
                        onClick = { if (!animating) selectedCase = index },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedCase == index) Color(0xFF263452) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.width(180.dp).padding(16.dp)) {
                            Text(definition.title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("${definition.cost} VC", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF090E18)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DROP REEL", Modifier.padding(horizontal = 18.dp), color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val reel = opening?.reel ?: CasesEngine.All[selectedCase].items.take(8)
                    LazyRow(
                        state = reelState,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = !animating
                    ) {
                        items(reel.size) { index -> CaseReelItem(reel[index], opening?.winningIndex == index && !animating) }
                    }
                }
            }
        }

        item {
            Button(
                enabled = !animating,
                onClick = {
                    val definition = CasesEngine.All[selectedCase]
                    viewModel.openCase(definition) { result ->
                        if (result == null) reveal = "Could not open case. Check balance."
                        else opening = result
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (animating) "OPENING…" else "OPEN ${CasesEngine.All[selectedCase].cost} VC") }
        }

        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(reveal, Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CaseReelItem(item: CaseItemTemplate, winner: Boolean) {
    val rarityColor = when (item.rarity) {
        ItemRarity.COMMON -> Color(0xFF94A3B8)
        ItemRarity.UNCOMMON -> Color(0xFF34D399)
        ItemRarity.RARE -> Color(0xFF38BDF8)
        ItemRarity.EPIC -> Color(0xFFA78BFA)
        ItemRarity.LEGENDARY -> Color(0xFFF59E0B)
        ItemRarity.MYTHIC -> Color(0xFFF472B6)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (winner) rarityColor.copy(alpha = 0.28f) else Color(0xFF172033)
    ) {
        Column(Modifier.width(140.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.weaponCategory.uppercase(), color = rarityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(item.name, fontWeight = FontWeight.Black)
            Text(item.rarity.name, color = rarityColor, fontSize = 11.sp)
            Text("${item.marketValue} VC", color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}
