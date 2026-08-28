package com.vircas.mobile.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
internal fun PremiumGameFrame(
    title: String,
    subtitle: String,
    balance: Long,
    accent: Color,
    onBack: () -> Unit,
    content: @Composable (compact: Boolean, landscape: Boolean) -> Unit
) {
    val ambient = rememberInfiniteTransition(label = "premium-game-ambient")
    val drift by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6200), repeatMode = RepeatMode.Reverse),
        label = "ambient-drift"
    )
    val pulse by ambient.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(1800), repeatMode = RepeatMode.Reverse),
        label = "ambient-pulse"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShellDeep)
            .safeDrawingPadding()
    ) {
        val landscape = maxWidth > maxHeight * 1.18f
        val compact = maxHeight < 700.dp || landscape

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        ShellDeep,
                        accent.copy(alpha = 0.055f + 0.025f * pulse),
                        ShellDeep
                    )
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(w * (0.15f + drift * 0.20f), h * 0.16f),
                    radius = max(w, h) * 0.55f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(ShellCyan.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(w * (0.88f - drift * 0.12f), h * 0.82f),
                    radius = max(w, h) * 0.42f
                )
            )

            val gridStep = if (compact) 38f else 46f
            var x = -gridStep + (drift * gridStep)
            while (x < w + gridStep) {
                drawLine(Color.White.copy(alpha = 0.018f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += gridStep
            }
            var y = gridStep * 0.35f
            while (y < h) {
                drawLine(Color.White.copy(alpha = 0.014f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += gridStep
            }
            drawCircle(
                color = accent.copy(alpha = 0.035f * pulse),
                radius = size.minDimension * 0.38f,
                center = Offset(w * 0.5f, h * 0.48f),
                style = Stroke(width = 1.2f)
            )
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.25f), accent.copy(alpha = 0.85f), accent.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.055f))
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        title.uppercase(),
                        fontSize = if (compact) 20.sp else 25.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .graphicsLayer { alpha = pulse }
                                .background(accent, CircleShape)
                                .padding(2.dp)
                        )
                        Text(
                            subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                            fontSize = if (compact) 9.sp else 10.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ShellPanel.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.27f)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("BALANCE", color = Color.White.copy(alpha = 0.34f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Text(
                            shortShellAmount(balance) + " VC",
                            color = accent,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
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
        label = { Text("STAKE", fontWeight = FontWeight.Black, letterSpacing = 0.7.sp) },
        suffix = { Text("VC", color = accent, fontWeight = FontWeight.Black) },
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
    val border by animateColorAsState(
        targetValue = if (text.contains("WIN", ignoreCase = true) || text.contains("Cashed", ignoreCase = true) || text.contains("Safe", ignoreCase = true)) {
            ShellGreen.copy(alpha = 0.42f)
        } else if (text.contains("MISS", ignoreCase = true) || text.contains("lost", ignoreCase = true) || text.contains("Failed", ignoreCase = true) || text.contains("Mine", ignoreCase = true)) {
            ShellRed.copy(alpha = 0.42f)
        } else accent.copy(alpha = 0.20f),
        animationSpec = tween(240),
        label = "message-border"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = ShellPanel.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, border),
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(shape = CircleShape, color = border.copy(alpha = 0.65f)) {
                Box(Modifier.padding(3.dp))
            }
            Crossfade(
                targetState = text,
                modifier = Modifier.weight(1f),
                animationSpec = tween(180),
                label = "message-crossfade"
            ) { current ->
                Text(
                    current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = tween(90),
        label = "premium-action-scale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (enabled) 8.dp else 0.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color(0xFF090C12),
            disabledContainerColor = accent.copy(alpha = 0.18f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        )
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.45.sp)
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
            val selected = current == value
            Surface(
                onClick = { if (enabled) onPick(value) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) ShellGold.copy(alpha = 0.16f) else ShellPanel.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, if (selected) ShellGold.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.065f)),
                shadowElevation = if (selected) 3.dp else 0.dp
            ) {
                Column(
                    Modifier.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (value.toLong() >= 1000L) "${value.toLong() / 1000}K" else value,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = if (selected) ShellGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.67f)
                    )
                    if (selected) {
                        Box(Modifier.padding(top = 2.dp).background(ShellGold, CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

internal fun premiumStake(value: String): Long = value.toLongOrNull()?.takeIf { it > 0L } ?: 0L
