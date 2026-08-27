package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import kotlinx.coroutines.launch

internal val ShellPurple = Color(0xFF8B5CF6)
internal val ShellCyan = Color(0xFF22D3EE)
internal val ShellGold = Color(0xFFF4C95D)
internal val ShellGreen = Color(0xFF42D392)
internal val ShellRed = Color(0xFFFF6B7A)
internal val ShellDeep = Color(0xFF080B12)
internal val ShellPanel = Color(0xFF111827)
internal val ShellPanel2 = Color(0xFF182033)

internal val premiumGameIds = listOf(
    "roulette", "blackjack", "mines", "crash", "slots", "plinko", "dice",
    "coinflip", "wheel", "hilo", "towers", "ladder", "horse"
)

fun AppViewModel.addVirtualFunds(amount: Long, onResult: (Boolean) -> Unit = {}) {
    if (amount <= 0L) {
        onResult(false)
        return
    }
    viewModelScope.launch {
        val app = getApplication<VirCasApplication>()
        app.container.walletRepository.credit(amount)
        onResult(true)
    }
}

@Composable
internal fun VirtualFundsDialog(balance: Long, onDismiss: () -> Unit, onAdd: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toLongOrNull()
    val presets = listOf(10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = ShellGold) },
        title = { Text("Add virtual funds", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current balance: ${formatShellVc(balance)}", color = ShellCyan, fontWeight = FontWeight.Bold)
                Text(
                    "These coins exist only inside VirCas. There is no purchase, deposit or withdrawal.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(presets) { preset ->
                        FilterChip(
                            selected = amount == preset,
                            onClick = { text = preset.toString() },
                            label = { Text("+${shortShellAmount(preset)}") }
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        if (next.all(Char::isDigit) && next.length <= 18) text = next.trimStart('0')
                    },
                    label = { Text("Any amount") },
                    placeholder = { Text("250000000") },
                    suffix = { Text("VC") },
                    supportingText = { Text("Any positive virtual amount up to 18 digits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { amount?.takeIf { it > 0 }?.let(onAdd) },
                enabled = amount != null && amount > 0L
            ) {
                Text(amount?.let { "ADD ${shortShellAmount(it)}" } ?: "ADD", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
internal fun VirtualWalletCard(balance: Long, onAddFunds: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ShellGold.copy(alpha = 0.28f))
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF241A3B), Color(0xFF102B34), Color(0xFF17251D)))
            ).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = ShellGold)
                    Spacer(Modifier.width(8.dp))
                    Text("VIRTUAL WALLET", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(formatShellVc(balance), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                Text("Local-only play currency", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
            }
            Button(
                onClick = onAddFunds,
                modifier = Modifier.align(Alignment.BottomEnd),
                colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = Color(0xFF161006))
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("ADD FUNDS", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
internal fun ShellHeader(eyebrow: String, title: String, subtitle: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ShellPurple.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, ShellPurple.copy(alpha = 0.28f))
        ) {
            Icon(icon, null, Modifier.padding(12.dp), tint = ShellPurple)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = ShellCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ShellBackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f), fontSize = 11.sp)
    }
}

@Composable
internal fun ShellActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ShellPanel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(icon, null, Modifier.padding(10.dp), tint = accent)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(
                    subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    fontSize = 10.sp
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
        }
    }
}

@Composable
internal fun EmptyShellCard(icon: ImageVector, title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = ShellPanel, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(
            Modifier.fillMaxWidth().padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, Modifier.size(38.dp), tint = ShellPurple)
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun FairnessInfoCard() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ShellGreen.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, ShellGreen.copy(alpha = 0.25f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = ShellGreen)
            Spacer(Modifier.width(11.dp))
            Text(
                "Outcome is generated before animation. Generated seed + client seed + result are stored locally.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                fontSize = 12.sp
            )
        }
    }
}

internal fun formatShellVc(value: Long): String = "%,d VC".format(value)
internal fun signedVc(value: Long): String = when {
    value > 0L -> "+${formatShellVc(value)}"
    value < 0L -> if (value == Long.MIN_VALUE) "-$value VC" else "-${formatShellVc(-value)}"
    else -> "0 VC"
}
internal fun shortShellAmount(value: Long): String = when {
    value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
    value >= 1_000_000L -> "${value / 1_000_000L}M"
    value >= 1_000L -> "${value / 1_000L}K"
    else -> value.toString()
}
internal fun rarityAccent(rarity: String): Color = when (rarity.uppercase()) {
    "LEGENDARY", "GOLD" -> ShellGold
    "EPIC" -> ShellPurple
    "RARE" -> ShellCyan
    else -> Color(0xFF94A3B8)
}
internal fun gameAccent(id: String): Color = when (id) {
    "roulette", "blackjack" -> ShellGold
    "mines", "crash", "plinko" -> ShellCyan
    "slots", "wheel" -> ShellPurple
    "dice", "coinflip", "hilo" -> ShellGreen
    else -> Color(0xFF7C8CFF)
}
internal fun gameTagline(id: String): String = when (id) {
    "roulette" -> "European 0–36"
    "blackjack" -> "Dealer stands 17"
    "mines" -> "5×5 live cashout"
    "crash" -> "Ride the multiplier"
    "slots" -> "Three fictional themes"
    "plinko" -> "Drop through the board"
    "dice" -> "Roll under or over"
    "coinflip" -> "Heads or tails"
    "wheel" -> "Up to 25×"
    "hilo" -> "Higher or lower"
    "towers" -> "Climb for multiplier"
    "ladder" -> "Step-by-step risk"
    "horse" -> "Animated local race"
    else -> "Virtual local game"
}
