package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val CrashLive = Color(0xFF5CF2A5)
private val CrashLiveBright = Color(0xFFB7FFD8)
private val CrashRed = Color(0xFFFF4F67)
private val CrashGrid = Color(0xFF93A9A1)

internal fun crashDisplayMultiplier(seconds: Float): Double {
    val t = seconds.coerceAtLeast(0f).toDouble()
    return exp(0.105 * t + 0.0045 * t * t).coerceIn(1.0, 1000.0)
}

@Composable
fun CrashRunScene(
    multiplier: Double,
    elapsedSeconds: Float,
    running: Boolean,
    crashed: Boolean,
    crashProgress: Float,
    cashoutAt: Double?,
    modifier: Modifier = Modifier
) {
    val accent = if (crashed) CrashRed else CrashLive
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF081813), Color(0xFF06100E), Color(0xFF040A09))
                ),
                RoundedCornerShape(28.dp)
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCrashGrid(elapsedSeconds)
            val geometry = buildCrashGeometry(elapsedSeconds, multiplier)
            drawCrashTrail(geometry, accent)
            drawCrashRunner(
                point = geometry.end,
                tangentDegrees = geometry.angleDegrees,
                elapsedSeconds = elapsedSeconds,
                crashed = crashed,
                crashProgress = crashProgress
            )
            if (crashed) drawCrashBurst(geometry.end, crashProgress)
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 15.dp)
        ) {
            Text(
                text = "${"%.2f".format(multiplier)}x",
                color = accent,
                fontSize = 43.sp,
                lineHeight = 43.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = CircleShape,
                    color = accent
                ) {}
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        crashed -> "CRASHED"
                        running && cashoutAt != null -> "ROUND LIVE · PAYOUT LOCKED"
                        running -> "RUNNING"
                        else -> "READY"
                    },
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
        }

        if (cashoutAt != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                shape = RoundedCornerShape(15.dp),
                color = CrashLive.copy(alpha = .11f),
                border = BorderStroke(1.dp, CrashLive.copy(alpha = .36f))
            ) {
                Column(
                    Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("LOCKED", color = CrashLive, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text("${"%.2f".format(cashoutAt)}x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Text(
            text = "PROVABLY FAIR · RESULT FIXED BEFORE RUN",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 17.dp, bottom = 12.dp),
            color = Color.White.copy(alpha = .28f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )

        if (crashed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 13.dp, bottom = 11.dp),
                shape = RoundedCornerShape(13.dp),
                color = CrashRed.copy(alpha = .13f),
                border = BorderStroke(1.dp, CrashRed.copy(alpha = .30f))
            ) {
                Text(
                    "RUN ENDED",
                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = CrashRed,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class CrashGeometry(
    val path: Path,
    val fillPath: Path,
    val start: Offset,
    val end: Offset,
    val angleDegrees: Float
)

private fun DrawScope.buildCrashGeometry(elapsedSeconds: Float, multiplier: Double): CrashGeometry {
    val left = size.width * .055f
    val right = size.width * .965f
    val top = size.height * .12f
    val bottom = size.height * .88f
    val usableWidth = right - left
    val usableHeight = bottom - top
    val displayMax = max(2.15, multiplier * 1.22)
    val logMax = ln(displayMax).coerceAtLeast(.1)
    val sampleCount = 84
    val path = Path()
    val fill = Path()
    var first = Offset(left, bottom)
    var previous = first
    var end = first

    for (index in 0..sampleCount) {
        val fraction = index / sampleCount.toFloat()
        val t = elapsedSeconds * fraction
        val m = crashDisplayMultiplier(t)
        val xProgress = (1.0 - exp(-t.toDouble() / 6.2)).toFloat()
        val x = left + usableWidth * (.035f + .93f * xProgress)
        val yProgress = (ln(m.coerceAtLeast(1.0)) / logMax).toFloat().coerceIn(0f, .96f)
        val y = bottom - usableHeight * (.04f + .90f * yProgress)
        val point = Offset(x, y)
        if (index == 0) {
            path.moveTo(point.x, point.y)
            fill.moveTo(point.x, bottom)
            fill.lineTo(point.x, point.y)
            first = point
        } else {
            path.lineTo(point.x, point.y)
            fill.lineTo(point.x, point.y)
        }
        previous = end
        end = point
    }
    fill.lineTo(end.x, bottom)
    fill.lineTo(first.x, bottom)
    fill.close()
    val angle = Math.toDegrees(atan2((end.y - previous.y).toDouble(), (end.x - previous.x).toDouble())).toFloat()
    return CrashGeometry(path, fill, first, end, angle.coerceIn(-58f, -2f))
}

private fun DrawScope.drawCrashGrid(elapsedSeconds: Float) {
    val spacing = max(38f, size.width / 9f)
    val drift = (elapsedSeconds * 28f) % spacing
    var x = -spacing + drift
    while (x < size.width + spacing) {
        drawLine(
            CrashGrid.copy(alpha = .065f),
            Offset(x, 0f),
            Offset(x, size.height),
            strokeWidth = 1f
        )
        x += spacing
    }
    var y = size.height * .16f
    while (y < size.height) {
        drawLine(
            CrashGrid.copy(alpha = .065f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacing * .78f
    }

    val streakOffset = (elapsedSeconds * 95f) % (size.width * .42f)
    repeat(5) { index ->
        val baseX = size.width * (.10f + index * .23f) - streakOffset
        val baseY = size.height * (.25f + (index % 3) * .19f)
        drawLine(
            Color.White.copy(alpha = .025f),
            Offset(baseX, baseY),
            Offset(baseX + size.width * .17f, baseY - size.height * .09f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawCrashTrail(geometry: CrashGeometry, accent: Color) {
    drawPath(
        geometry.fillPath,
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = .22f), accent.copy(alpha = .055f), Color.Transparent),
            startY = geometry.end.y,
            endY = size.height * .91f
        )
    )
    drawPath(geometry.path, accent.copy(alpha = .10f), style = Stroke(width = 18f))
    drawPath(geometry.path, accent.copy(alpha = .28f), style = Stroke(width = 9f))
    drawPath(
        geometry.path,
        brush = Brush.linearGradient(
            listOf(accent.copy(alpha = .45f), if (accent == CrashRed) CrashRed else CrashLiveBright),
            start = geometry.start,
            end = geometry.end
        ),
        style = Stroke(width = 4.2f)
    )
    drawCircle(accent.copy(alpha = .16f), radius = 18f, center = geometry.end)
    drawCircle(accent, radius = 5.5f, center = geometry.end)
}

private fun DrawScope.drawCrashRunner(
    point: Offset,
    tangentDegrees: Float,
    elapsedSeconds: Float,
    crashed: Boolean,
    crashProgress: Float
) {
    val gait = sin(elapsedSeconds * 10.5f)
    val p = crashProgress.coerceIn(0f, 1f)
    val fallX = if (crashed) 48f * p else 0f
    val fallY = if (crashed) 112f * p.pow(1.65f) else 0f
    val fade = if (crashed) (1f - p * .55f) else 1f
    val rotation = tangentDegrees + 8f + if (crashed) 270f * p else 0f
    val runnerPoint = point + Offset(fallX, fallY - 5f)
    val body = Color(0xFFF3F6F5).copy(alpha = fade)
    val glow = CrashLive.copy(alpha = .15f * fade)
    val stride = gait * 10f

    withTransform({
        translate(runnerPoint.x, runnerPoint.y)
        rotate(rotation)
    }) {
        drawCircle(glow, radius = 25f, center = Offset(1f, -21f))
        drawLine(glow, Offset(0f, -29f), Offset(0f, -8f), strokeWidth = 10f)
        drawLine(glow, Offset(0f, -10f), Offset(11f + stride, 1f), strokeWidth = 7f)
        drawLine(glow, Offset(0f, -10f), Offset(-9f - stride, 1f), strokeWidth = 7f)

        drawCircle(body, radius = 5.2f, center = Offset(4f, -35f))
        drawLine(body, Offset(2f, -30f), Offset(-1f, -13f), strokeWidth = 4.5f)
        drawLine(body, Offset(1f, -26f), Offset(12f - stride * .55f, -18f), strokeWidth = 3.8f)
        drawLine(body, Offset(1f, -25f), Offset(-8f + stride * .55f, -17f), strokeWidth = 3.8f)
        drawLine(body, Offset(-1f, -13f), Offset(10f + stride, 0f), strokeWidth = 4.2f)
        drawLine(body, Offset(-1f, -13f), Offset(-9f - stride, 0f), strokeWidth = 4.2f)
        drawLine(CrashLiveBright.copy(alpha = fade), Offset(8f + stride, 0f), Offset(14f + stride, 0f), strokeWidth = 3f)
        drawLine(CrashLiveBright.copy(alpha = fade), Offset(-11f - stride, 0f), Offset(-5f - stride, 0f), strokeWidth = 3f)
    }
}

private fun DrawScope.drawCrashBurst(point: Offset, crashProgress: Float) {
    val p = crashProgress.coerceIn(0f, 1f)
    val radius = 12f + 58f * p
    drawCircle(CrashRed.copy(alpha = .18f * (1f - p)), radius = radius, center = point)
    drawCircle(CrashRed.copy(alpha = .75f * (1f - p * .5f)), radius = 4f + 7f * (1f - p), center = point)
    repeat(12) { index ->
        val angle = index / 12.0 * PI * 2.0
        val startRadius = 7f + 12f * p
        val endRadius = 12f + 48f * p
        val start = Offset(
            point.x + cos(angle).toFloat() * startRadius,
            point.y + sin(angle).toFloat() * startRadius
        )
        val end = Offset(
            point.x + cos(angle).toFloat() * endRadius,
            point.y + sin(angle).toFloat() * endRadius
        )
        drawLine(
            CrashRed.copy(alpha = .75f * (1f - p)),
            start,
            end,
            strokeWidth = 2.2f
        )
    }
}
