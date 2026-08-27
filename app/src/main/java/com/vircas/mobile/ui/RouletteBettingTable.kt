@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun RouletteBettingTable(
    bets: Map<RouletteBet, List<Long>>,
    result: Int?,
    enabled: Boolean,
    onAdd: (RouletteBet) -> Unit,
    onRemove: (RouletteBet) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val cellWidth = 58.dp
    val cellHeight = 52.dp
    val zeroWidth = 64.dp
    val columnWidth = 58.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0E4A2B),
        border = BorderStroke(2.dp, Color(0xFFBFA66C))
    ) {
        Column(
            Modifier.horizontalScroll(scroll).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                RouletteBetCell(
                    label = "0",
                    bet = RouletteBet.Number(0),
                    chips = bets[RouletteBet.Number(0)].orEmpty(),
                    baseColor = Color(0xFF137437),
                    winning = result == 0,
                    enabled = enabled,
                    width = zeroWidth,
                    height = cellHeight * 3 + 4.dp,
                    onAdd = onAdd,
                    onRemove = onRemove
                )

                Row {
                    (0 until 12).forEach { group ->
                        Column {
                            listOf(group * 3 + 3, group * 3 + 2, group * 3 + 1).forEach { number ->
                                val bet = RouletteBet.Number(number)
                                RouletteBetCell(
                                    label = number.toString(),
                                    bet = bet,
                                    chips = bets[bet].orEmpty(),
                                    baseColor = rouletteNumberColor(number),
                                    winning = result == number,
                                    enabled = enabled,
                                    width = cellWidth,
                                    height = cellHeight,
                                    onAdd = onAdd,
                                    onRemove = onRemove
                                )
                            }
                        }
                    }
                }

                Column {
                    listOf(3, 2, 1).forEach { index ->
                        val bet = RouletteBet.Column(index)
                        RouletteBetCell(
                            label = "2 TO 1",
                            bet = bet,
                            chips = bets[bet].orEmpty(),
                            baseColor = Color(0xFF135D35),
                            winning = result?.let { rouletteBetWon(it, bet) } == true,
                            enabled = enabled,
                            width = columnWidth,
                            height = cellHeight,
                            onAdd = onAdd,
                            onRemove = onRemove,
                            smallLabel = true
                        )
                    }
                }
            }

            Row {
                Spacer(Modifier.width(zeroWidth))
                (1..3).forEach { index ->
                    val bet = RouletteBet.Dozen(index)
                    RouletteBetCell(
                        label = when (index) {
                            1 -> "1ST 12"
                            2 -> "2ND 12"
                            else -> "3RD 12"
                        },
                        bet = bet,
                        chips = bets[bet].orEmpty(),
                        baseColor = Color(0xFF135D35),
                        winning = result?.let { rouletteBetWon(it, bet) } == true,
                        enabled = enabled,
                        width = cellWidth * 4,
                        height = 48.dp,
                        onAdd = onAdd,
                        onRemove = onRemove
                    )
                }
                Spacer(Modifier.width(columnWidth))
            }

            Row {
                Spacer(Modifier.width(zeroWidth))
                val outside = listOf(
                    Triple("1–18", RouletteBet.Low, Color(0xFF135D35)),
                    Triple("EVEN", RouletteBet.Even, Color(0xFF135D35)),
                    Triple("RED", RouletteBet.Color(RouletteColor.RED), Color(0xFFC52B27)),
                    Triple("BLACK", RouletteBet.Color(RouletteColor.BLACK), Color(0xFF171A18)),
                    Triple("ODD", RouletteBet.Odd, Color(0xFF135D35)),
                    Triple("19–36", RouletteBet.High, Color(0xFF135D35))
                )
                outside.forEach { (label, bet, color) ->
                    RouletteBetCell(
                        label = label,
                        bet = bet,
                        chips = bets[bet].orEmpty(),
                        baseColor = color,
                        winning = result?.let { rouletteBetWon(it, bet) } == true,
                        enabled = enabled,
                        width = cellWidth * 2,
                        height = 48.dp,
                        onAdd = onAdd,
                        onRemove = onRemove
                    )
                }
                Spacer(Modifier.width(columnWidth))
            }
        }
    }
}

@Composable
private fun RouletteBetCell(
    label: String,
    bet: RouletteBet,
    chips: List<Long>,
    baseColor: Color,
    winning: Boolean,
    enabled: Boolean,
    width: Dp,
    height: Dp,
    onAdd: (RouletteBet) -> Unit,
    onRemove: (RouletteBet) -> Unit,
    smallLabel: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(1.dp)
            .background(
                color = if (winning) Color(0xFF9A7627) else baseColor,
                shape = RoundedCornerShape(3.dp)
            )
            .combinedClickable(
                enabled = enabled,
                onClick = { onAdd(bet) },
                onLongClick = { onRemove(bet) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFF8F0DA),
            fontSize = if (smallLabel) 8.sp else 15.sp,
            fontWeight = FontWeight.Black
        )
        if (chips.isNotEmpty()) {
            PlacedChipStack(chips, Modifier.align(Alignment.Center))
        }
        if (winning) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                shape = CircleShape,
                color = Color(0xFFF5D76E)
            ) {
                Spacer(Modifier.size(7.dp))
            }
        }
    }
}

@Composable
private fun PlacedChipStack(chips: List<Long>, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        chips.takeLast(4).forEachIndexed { index, value ->
            SmallChipToken(
                value = value,
                modifier = Modifier
                    .size(31.dp)
                    .offset(y = (-index * 3).dp)
                    .zIndex(index.toFloat())
            )
        }
        if (chips.size > 1) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).zIndex(10f),
                shape = CircleShape,
                color = Color(0xEE07100B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "×${chips.size}",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
internal fun RouletteChipToken(
    value: Long,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val chipColor = rouletteChipColor(value)
            drawCircle(Color(0x77000000), radius = size.minDimension * 0.47f, center = center + Offset(2f, 3f))
            drawCircle(chipColor, radius = size.minDimension * 0.46f)
            drawCircle(
                Color(0xFFF2E7D1),
                radius = size.minDimension * 0.40f,
                style = Stroke(width = size.minDimension * 0.055f)
            )
            repeat(12) { index ->
                val angle = index * 30f * PI.toFloat() / 180f
                val r1 = size.minDimension * 0.35f
                val r2 = size.minDimension * 0.44f
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(center.x + cos(angle) * r1, center.y + sin(angle) * r1),
                    end = Offset(center.x + cos(angle) * r2, center.y + sin(angle) * r2),
                    strokeWidth = size.minDimension * 0.045f,
                    cap = StrokeCap.Round
                )
            }
            if (selected) {
                drawCircle(
                    Color(0xFFFFD66A),
                    radius = size.minDimension * 0.49f,
                    style = Stroke(width = size.minDimension * 0.055f)
                )
            }
        }
        Text(
            shortRouletteChip(value),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SmallChipToken(value: Long, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x99000000), radius = size.minDimension * 0.47f, center = center + Offset(1.4f, 2f))
            drawCircle(rouletteChipColor(value), radius = size.minDimension * 0.45f)
            drawCircle(
                Color.White.copy(alpha = 0.86f),
                radius = size.minDimension * 0.35f,
                style = Stroke(width = size.minDimension * 0.055f)
            )
        }
        Text(
            shortRouletteChip(value),
            color = Color.White,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black
        )
    }
}
