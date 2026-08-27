package com.vircas.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class RouletteSpinPhase {
    IDLE,
    WHEEL_AND_BALL,
    BALL_COAST,
    POCKET_BOUNCE,
    BALL_DROP,
    SETTLED
}

@Composable
internal fun RouletteWheelPanel(
    wheelRotation: Float,
    ballRotation: Float,
    ballRadius: Float,
    ballHop: Float,
    ballDrop: Float,
    phase: RouletteSpinPhase,
    result: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val label = rouletteWheelPhaseLabel(phase, result)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 20.dp else 34.dp),
        color = Color(0xFF24160C),
        border = BorderStroke(if (compact) 1.dp else 2.dp, Color(0xFF7D5B2B))
    ) {
        if (compact) {
            Box(Modifier.fillMaxSize().padding(4.dp)) {
                RealRouletteWheel(
                    wheelRotation = wheelRotation,
                    ballRotation = ballRotation,
                    ballRadius = ballRadius,
                    ballHop = ballHop,
                    ballDrop = ballDrop,
                    phase = phase,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xDD0D2317),
                    border = BorderStroke(0.5.dp, Color(0xFF42684F))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = if (result == 0) Color(0xFF5AD27A) else Color(0xFFF4DFB1),
                        fontWeight = FontWeight.Black,
                        fontSize = 7.sp,
                        maxLines = 1
                    )
                }
            }
        } else {
            Column(
                Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RealRouletteWheel(
                    wheelRotation = wheelRotation,
                    ballRotation = ballRotation,
                    ballRadius = ballRadius,
                    ballHop = ballHop,
                    ballDrop = ballDrop,
                    phase = phase,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xFF0D2317),
                    border = BorderStroke(1.dp, Color(0xFF42684F))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        color = if (result == 0) Color(0xFF5AD27A) else Color(0xFFF4DFB1),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

private fun rouletteWheelPhaseLabel(phase: RouletteSpinPhase, result: Int?): String = when (phase) {
    RouletteSpinPhase.WHEEL_AND_BALL -> "WHEEL SPINNING"
    RouletteSpinPhase.BALL_COAST -> "WHEEL STOPPED · BALL COASTING"
    RouletteSpinPhase.POCKET_BOUNCE -> "BALL IN THE POCKETS"
    RouletteSpinPhase.BALL_DROP -> "BALL DROPPING"
    RouletteSpinPhase.SETTLED -> result?.let { "$it · ${RouletteEngine.colorOf(it).name}" } ?: "RESULT"
    RouletteSpinPhase.IDLE -> result?.let { "$it · ${RouletteEngine.colorOf(it).name}" } ?: "PLACE YOUR BETS"
}

@Composable
private fun RealRouletteWheel(
    wheelRotation: Float,
    ballRotation: Float,
    ballRadius: Float,
    ballHop: Float,
    ballDrop: Float,
    phase: RouletteSpinPhase,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val centerPoint = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.47f
        val pocketRadius = radius * 0.78f
        val innerRadius = radius * 0.51f
        val sweep = 360f / rouletteWheelOrder.size

        drawCircle(Color(0xFF0C0907), radius = radius * 1.07f, center = centerPoint)
        drawCircle(Color(0xFFD1A64A), radius = radius * 1.01f, center = centerPoint, style = Stroke(radius * 0.035f))
        drawCircle(Color(0xFF6D3518), radius = radius * 0.96f, center = centerPoint)
        drawCircle(Color(0xFF2C160D), radius = radius * 0.88f, center = centerPoint)
        drawCircle(Color(0xFF160E09), radius = radius * 0.82f, center = centerPoint, style = Stroke(radius * 0.018f))

        repeat(8) { index ->
            val angle = (index * 45f + 22.5f) * PI.toFloat() / 180f
            val point = Offset(
                centerPoint.x + cos(angle) * radius * 0.885f,
                centerPoint.y + sin(angle) * radius * 0.885f
            )
            drawCircle(Color(0xFFD7AE55), radius * 0.018f, point)
            drawCircle(Color(0xFF7C5522), radius * 0.009f, point)
        }

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

            drawCircle(
                Color(0xFFB58C49),
                radius = radius * 0.79f,
                center = centerPoint,
                style = Stroke(radius * 0.012f)
            )
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

        val effectiveBallRadius = radius * ballRadius + radius * 0.035f * ballHop
        val showLongTrail = phase == RouletteSpinPhase.WHEEL_AND_BALL || phase == RouletteSpinPhase.BALL_COAST
        val showShortTrail = phase == RouletteSpinPhase.POCKET_BOUNCE
        if (showLongTrail) {
            listOf(22f to 0.10f, 14f to 0.20f, 8f to 0.34f, 4f to 0.48f).forEach { (trail, alpha) ->
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    radius * 0.024f,
                    ballOffset(ballRotation + trail, effectiveBallRadius)
                )
            }
        } else if (showShortTrail) {
            listOf(7f to 0.12f, 3f to 0.25f).forEach { (trail, alpha) ->
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    radius * 0.022f,
                    ballOffset(ballRotation + trail, effectiveBallRadius)
                )
            }
        }

        val ball = ballOffset(ballRotation, effectiveBallRadius)
        val ballScale = 1f + ballHop * 0.14f - ballDrop * 0.08f
        val ballSize = radius * 0.031f * ballScale
        val shadowOffset = radius * (0.012f - ballDrop * 0.004f)
        drawCircle(
            Color(0x66000000),
            ballSize * 1.22f,
            ball + Offset(shadowOffset, shadowOffset)
        )
        drawCircle(Color(0xFFF1ECE0), ballSize, ball)
        drawCircle(
            Color.White,
            ballSize * 0.48f,
            ball + Offset(-ballSize * 0.25f, -ballSize * 0.25f)
        )
        if (phase == RouletteSpinPhase.BALL_DROP || phase == RouletteSpinPhase.SETTLED) {
            drawCircle(
                Color.White.copy(alpha = 0.24f * ballDrop),
                ballSize * 1.55f,
                ball,
                style = Stroke(width = radius * 0.006f)
            )
        }
    }
}
