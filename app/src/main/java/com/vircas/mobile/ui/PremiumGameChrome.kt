package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PremiumGameFrame(
    title: String,
    subtitle: String,
    balance: Long,
    accent: Color,
    onBack: () -> Unit,
    content: @Composable (compact: Boolean, landscape: Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ShellDeep,
                        accent.copy(alpha = 0.055f),
                        ShellDeep
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        val landscape = maxWidth > maxHeight * 1.18f
        val compact = maxHeight < 700.dp || landscape
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(
                        title.uppercase(),
                        fontSize = if (compact) 21.sp else 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp
                    )
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        fontSize = if (compact) 9.sp else 10.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                ) {
                    Text(
                        shortShellAmount(balance) + " VC",
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content(compact, landscape) }
        }
    }
}

@Composable
internal fun PremiumStakeField(
    value: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.all(Char::isDigit)) onValueChange(next.take(12)) },
        enabled = enabled,
        singleLine = true,
        label = { Text("STAKE") },
        suffix = { Text("VC", fontWeight = FontWeight.Bold) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
internal fun PremiumMessageCard(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ShellPanel.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun PremiumActionButton(
    text: String,
    accent: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color(0xFF090C12),
            disabledContainerColor = accent.copy(alpha = 0.18f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
internal fun QuickStakeRow(
    enabled: Boolean,
    current: String,
    onPick: (String) -> Unit
) {
    val values = listOf("500", "1000", "5000", "10000")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            Surface(
                onClick = { if (enabled) onPick(value) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (current == value) ShellGold.copy(alpha = 0.18f) else ShellPanel,
                border = BorderStroke(1.dp, if (current == value) ShellGold.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f))
            ) {
                Text(
                    if (value.toLong() >= 1000L) "${value.toLong() / 1000}K" else value,
                    Modifier.padding(vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    color = if (current == value) ShellGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

internal fun premiumStake(value: String): Long = value.toLongOrNull()?.takeIf { it > 0L } ?: 0L
