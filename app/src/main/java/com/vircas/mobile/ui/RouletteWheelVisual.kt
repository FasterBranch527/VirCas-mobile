package com.vircas.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun RouletteWheelPanel(
    wheelRotation: Float,
    ballRotation: Float,
    spinning: Boolean,
    result: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF24160C),
        border = BorderStroke(2.dp, Color(0xFF7D5B2B))
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealRouletteWheel(
                wheelRotation = wheelRotation,
                ballRotation = ballRotation,
                spinning = spinning,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color(0xFF0D2317),
                border = BorderStroke(1.dp, Color(0xFF42684F))
            ) {
                Text(
                    text = when {
                        spinning -> "BALL IN MOTION"
                        result == null -> "PLACE YOUR BETS"
                        else -> "$result · ${RouletteEngine.colorOf(result).name}"
                    },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = if (result == 0) Color(0xFF5AD27A) else Color(0xFFF4DFB1),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun RealRouletteWheel(
    wheelRotation: Float,
    ballRotation: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val centerPoint = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.47f
        val pocketRadius = radius * 0.78f
        val innerRadius = radius * 0.51f
        val sweep = 360f / rouletteWheelOrder.size

        drawCircle(Color(0xFF110C08), radius = radius * 1.04f, center = centerPoint)
        drawCircle(Color(0xFFD1A64A), radius = radius, center = centerPoint, style = Stroke(radius * 0.035f))
        drawCircle(Color(0xFF6D3518), radius = radius * 0.95f, center = centerPoint)
        drawCircle(Color(0xFF2C160D), radius = radius * 0.87f, center = centerPoint)

        rotate(wheelRotation, centerPoint) {
            rouletteWheelOrder.forEachIndexed { index, number ->
                val start = -90f + index * sweep
                val pocketColor = when (RouletteEngine.colorOf(number)) {
                    RouletteColor.RED -> Color(0xFFC52B27)
                    RouletteColor.BLACK -> Color(0xFF151816)
                    RouletteColor.GREEN -> Color(0xFF16843C)
                }
                drawArc(
                    color = pocketColor,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(centerPoint.x - pocketRadius, centerPoint.y - pocketRadius),
                    size = Size(pocketRadius * 2f, pocketRadius * 2f)
                )
                drawArc(
                    color = Color(0xFFD8C18A),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(centerPoint.x - pocketRadius, centerPoint.y - pocketRadius),
                    size = Size(pocketRadius * 2f, pocketRadius * 2f),
                    style = Stroke(width = 1.2f)
                )
            }

            drawCircle(Color(0xFF4C2513), radius = innerRadius, center = centerPoint)
            drawCircle(Color(0xFFBE8732), radius = innerRadius, center = centerPoint, style = Stroke(radius * 0.025f))
            drawCircle(Color(0xFF0B4F2A), radius = radius * 0.28f, center = centerPoint)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = radius * 0.075f
            }
            drawIntoCanvas { canvas ->
                rouletteWheelOrder.forEachIndexed { index, number ->
                    val angle = (-90f + index * sweep + sweep / 2f) * PI.toFloat() / 180f
                    val x = centerPoint.x + cos(angle) * radius * 0.69f
                    val y = centerPoint.y + sin(angle) * radius * 0.69f - (paint.ascent() + paint.descent()) / 2f
                    canvas.nativeCanvas.drawText(number.toString(), x, y, paint)
                }
            }
        }

        drawCircle(Color(0xFFE0B65A), radius = radius * 0.105f, center = centerPoint)
        drawCircle(Color(0xFF6F4517), radius = radius * 0.065f, center = centerPoint)
        repeat(4) { index ->
            val angle = index * 90f * PI.toFloat() / 180f
            val end = Offset(
                centerPoint.x + cos(angle) * radius * 0.31f,
                centerPoint.y + sin(angle) * radius * 0.31f
            )
            drawLine(
                color = Color(0xFFDDB45B),
                start = centerPoint,
                end = end,
                strokeWidth = radius * 0.026f,
                cap = StrokeCap.Round
            )
            drawCircle(Color(0xFFE7C36F), radius * 0.032f, end)
        }

        fun ballOffset(angleDegrees: Float, radial: Float): Offset {
            val angle = angleDegrees * PI.toFloat() / 180f
            return Offset(
                centerPoint.x + cos(angle) * radial,
                centerPoint.y + sin(angle) * radial
            )
        }

        if (spinning) {
            listOf(18f to 0.16f, 11f to 0.28f, 6f to 0.42f).forEach { (trail, alpha) ->
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    radius * 0.027f,
                    ballOffset(ballRotation + trail, radius * 0.88f)
                )
            }
        }

        val ball = ballOffset(ballRotation, radius * 0.88f)
        drawCircle(
            Color(0x55000000),
            radius * 0.037f,
            ball + Offset(radius * 0.012f, radius * 0.012f)
        )
        drawCircle(Color(0xFFF5F0E4), radius * 0.031f, ball)
        drawCircle(
            Color.White,
            radius * 0.015f,
            ball + Offset(-radius * 0.008f, -radius * 0.008f)
        )
    }
}
