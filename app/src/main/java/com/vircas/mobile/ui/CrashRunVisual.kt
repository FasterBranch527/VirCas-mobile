package com.vircas.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val CrashLive = Color(0xFF5CF2A5)
private val CrashLiveBright = Color(0xFFC7FFE1)
private val CrashLiveDeep = Color(0xFF16B96E)
private val CrashRed = Color(0xFFFF4F67)
private val CrashRedBright = Color(0xFFFFA1AE)
private val CrashGrid = Color(0xFF93A9A1)
private val RunnerSkin = Color(0xFFF0C8A6)
private val RunnerJersey = Color(0xFFF3F7F6)
private val RunnerJerseyShade = Color(0xFFBFD4CD)
private val RunnerShorts = Color(0xFF111B1A)
private val RunnerShoe = Color(0xFF8CFFD0)
private val RunnerOutline = Color(0xFF07100D)

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
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF091B16), Color(0xFF06110E), Color(0xFF030807))
            ),
            RoundedCornerShape(28.dp)
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val geometry = buildCrashGeometry(elapsedSeconds, multiplier)
            drawCrashAtmosphere(elapsedSeconds, geometry, crashed)
            drawCrashGrid(elapsedSeconds, multiplier)
            drawCrashTrail(geometry, accent, crashed)
            drawTrackParticles(geometry, elapsedSeconds, accent)
            drawCrashRunner(
                point = geometry.end,
                tangentDegrees = geometry.angleDegrees,
                elapsedSeconds = elapsedSeconds,
                running = running,
                crashed = crashed,
                crashProgress = crashProgress
            )
            if (crashed) drawCrashBurst(geometry.end, crashProgress)
        }

        Column(
            Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 15.dp)
        ) {
            Text(
                text = "${"%.2f".format(multiplier)}x",
                color = accent,
                fontSize = 45.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = accent) {}
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        crashed -> "CRASHED"
                        running && cashoutAt != null -> "ROUND LIVE · PAYOUT LOCKED"
                        running -> "RUNNING THE CURVE"
                        else -> "READY"
                    },
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
        }

        if (cashoutAt != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
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
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 17.dp, bottom = 12.dp),
            color = Color.White.copy(alpha = .28f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )

        if (crashed) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 13.dp, bottom = 11.dp),
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
    val previous: Offset,
    val angleDegrees: Float,
    val visibleStartTime: Float,
    val currentTime: Float
)

private fun DrawScope.buildCrashGeometry(elapsedSeconds: Float, multiplier: Double): CrashGeometry {
    val left = size.width * .045f
    val top = size.height * .10f
    val bottom = size.height * .895f
    val runnerX = size.width * .79f
    val earlyProgress = (elapsedSeconds / 4.2f).coerceIn(0f, 1f)
    val endX = left + (runnerX - left) * easeOutCubic(earlyProgress)

    val visibleDuration = 6.6f
    val visibleStart = max(0f, elapsedSeconds - visibleDuration)
    val windowDuration = max(4.2f, elapsedSeconds - visibleStart)
    val displayMax = max(2.0, multiplier * 1.28)
    val logMax = ln(displayMax).coerceAtLeast(.1)
    val sampleCount = 110
    val path = Path()
    val fill = Path()

    var first = Offset(left, bottom)
    var previous = first
    var end = first

    for (index in 0..sampleCount) {
        val fraction = index / sampleCount.toFloat()
        val t = visibleStart + (elapsedSeconds - visibleStart) * fraction
        val localTime = t - visibleStart
        val xProgress = if (elapsedSeconds <= 4.2f) {
            val total = max(elapsedSeconds, .001f)
            (t / total).coerceIn(0f, 1f)
        } else {
            (localTime / windowDuration).coerceIn(0f, 1f)
        }
        val x = left + (endX - left) * xProgress
        val m = crashDisplayMultiplier(t)
        val yProgress = (ln(m.coerceAtLeast(1.0)) / logMax).toFloat().coerceIn(0f, .965f)
        val curveLift = 0.028f * sin(fraction * PI).toFloat()
        val y = bottom - (bottom - top) * (.035f + .89f * yProgress + curveLift)
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

    val angle = Math.toDegrees(
        atan2((end.y - previous.y).toDouble(), (end.x - previous.x).toDouble())
    ).toFloat().coerceIn(-54f, -3f)

    return CrashGeometry(path, fill, first, end, previous, angle, visibleStart, elapsedSeconds)
}

private fun DrawScope.drawCrashAtmosphere(
    elapsedSeconds: Float,
    geometry: CrashGeometry,
    crashed: Boolean
) {
    val speed = (elapsedSeconds / 5f).coerceIn(0f, 1f)
    val horizonY = size.height * .26f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = .018f),
                Color.Transparent,
                (if (crashed) CrashRed else CrashLive).copy(alpha = .018f)
            ),
            startY = 0f,
            endY = size.height
        )
    )

    repeat(9) { index ->
        val lane = index / 8f
        val drift = ((elapsedSeconds * (42f + index * 5f)) % (size.width * 1.4f))
        val x = size.width * 1.1f - drift
        val y = horizonY + lane * size.height * .58f
        val length = size.width * (.07f + speed * .08f)
        drawLine(
            color = Color.White.copy(alpha = .018f + speed * .018f),
            start = Offset(x, y),
            end = Offset(x - length, y + length * .11f),
            strokeWidth = 1.2f + speed * .8f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = (if (crashed) CrashRed else CrashLive).copy(alpha = .045f),
        radius = size.minDimension * .20f,
        center = geometry.end
    )
}

private fun DrawScope.drawCrashGrid(elapsedSeconds: Float, multiplier: Double) {
    val spacingX = max(42f, size.width / 8.5f)
    val spacingY = spacingX * .72f
    val driftX = (elapsedSeconds * 34f) % spacingX
    val driftY = (elapsedSeconds * 8f + ln(multiplier.coerceAtLeast(1.0)).toFloat() * 15f) % spacingY

    var x = -spacingX + driftX
    while (x < size.width + spacingX) {
        drawLine(
            CrashGrid.copy(alpha = .055f),
            Offset(x, size.height * .08f),
            Offset(x - size.width * .10f, size.height),
            strokeWidth = 1f
        )
        x += spacingX
    }

    var y = size.height * .12f + driftY
    while (y < size.height) {
        drawLine(
            CrashGrid.copy(alpha = .055f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacingY
    }
}

private fun DrawScope.drawCrashTrail(geometry: CrashGeometry, accent: Color, crashed: Boolean) {
    val hot = if (crashed) CrashRedBright else CrashLiveBright
    drawPath(
        geometry.fillPath,
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = .22f), accent.copy(alpha = .06f), Color.Transparent),
            startY = geometry.end.y,
            endY = size.height * .93f
        )
    )
    drawPath(geometry.path, Color.Black.copy(alpha = .26f), style = Stroke(width = 23f, cap = StrokeCap.Round))
    drawPath(geometry.path, accent.copy(alpha = .08f), style = Stroke(width = 18f, cap = StrokeCap.Round))
    drawPath(geometry.path, accent.copy(alpha = .25f), style = Stroke(width = 10f, cap = StrokeCap.Round))
    drawPath(
        geometry.path,
        brush = Brush.linearGradient(
            listOf(accent.copy(alpha = .34f), accent, hot),
            start = geometry.start,
            end = geometry.end
        ),
        style = Stroke(width = 4.4f, cap = StrokeCap.Round)
    )

    drawCircle(accent.copy(alpha = .08f), radius = 27f, center = geometry.end)
    drawCircle(accent.copy(alpha = .18f), radius = 16f, center = geometry.end)
    drawCircle(hot, radius = 4.8f, center = geometry.end)
}

private fun DrawScope.drawTrackParticles(
    geometry: CrashGeometry,
    elapsedSeconds: Float,
    accent: Color
) {
    val tangent = normalize(geometry.end - geometry.previous)
    val normal = Offset(-tangent.y, tangent.x)
    repeat(8) { index ->
        val phase = ((elapsedSeconds * (1.0f + index * .07f) + index * .83f) % 1.7f) / 1.7f
        val distance = 15f + phase * 105f
        val spread = sin(index * 2.13f + elapsedSeconds * 2f) * 8f
        val p = geometry.end - tangent * distance + normal * spread
        val alpha = (1f - phase) * .26f
        drawCircle(accent.copy(alpha = alpha), radius = 1.5f + (1f - phase) * 2f, center = p)
    }
}

private fun DrawScope.drawCrashRunner(
    point: Offset,
    tangentDegrees: Float,
    elapsedSeconds: Float,
    running: Boolean,
    crashed: Boolean,
    crashProgress: Float
) {
    val p = crashProgress.coerceIn(0f, 1f)
    val fallX = if (crashed) 70f * p else 0f
    val fallY = if (crashed) 138f * p.pow(1.55f) else 0f
    val fade = if (crashed) (1f - p * .48f) else 1f
    val tumble = if (crashed) 410f * p else 0f
    val runnerPoint = point + Offset(fallX, fallY)

    val speed = if (running) (8.8f + min(4.8f, elapsedSeconds * .35f)) else 0f
    val cycle = elapsedSeconds * speed
    val gait = sin(cycle)
    val gaitCos = cos(cycle)
    val bob = if (running) abs(gaitCos) * 2.2f else 0f
    val breath = sin(elapsedSeconds * 2.4f) * .7f

    withTransform({
        translate(runnerPoint.x, runnerPoint.y)
        rotate(tangentDegrees + tumble)
    }) {
        // Motion ghosts make the runner feel fast without turning him into a blur.
        if (running && !crashed) {
            repeat(3) { ghost ->
                val shift = 8f + ghost * 8f
                val alpha = .055f - ghost * .012f
                drawRunnerSilhouette(
                    origin = Offset(-shift, 0f),
                    cycle = cycle - ghost * .45f,
                    bob = bob,
                    alpha = alpha.coerceAtLeast(.015f)
                )
            }
        }

        // Contact shadow sits on the actual graph line.
        drawOval(
            color = Color.Black.copy(alpha = .32f * fade),
            topLeft = Offset(-21f, -2.4f),
            size = Size(42f, 6.2f)
        )
        drawOval(
            color = CrashLive.copy(alpha = .12f * fade),
            topLeft = Offset(-15f, -1.5f),
            size = Size(30f, 3.4f)
        )

        drawDetailedRunner(
            cycle = cycle,
            bob = bob + breath,
            fade = fade,
            crashed = crashed
        )
    }
}

private fun DrawScope.drawRunnerSilhouette(origin: Offset, cycle: Float, bob: Float, alpha: Float) {
    val pose = runnerPose(cycle, bob)
    withTransform({ translate(origin.x, origin.y) }) {
        val color = CrashLive.copy(alpha = alpha)
        drawLine(color, pose.hip, pose.kneeA, 8f, cap = StrokeCap.Round)
        drawLine(color, pose.kneeA, pose.footA, 6f, cap = StrokeCap.Round)
        drawLine(color, pose.hip, pose.kneeB, 8f, cap = StrokeCap.Round)
        drawLine(color, pose.kneeB, pose.footB, 6f, cap = StrokeCap.Round)
        drawLine(color, pose.hip, pose.shoulder, 14f, cap = StrokeCap.Round)
        drawCircle(color, 8f, pose.head)
    }
}

private fun DrawScope.drawDetailedRunner(cycle: Float, bob: Float, fade: Float, crashed: Boolean) {
    val pose = runnerPose(cycle, bob)
    val outline = RunnerOutline.copy(alpha = .88f * fade)
    val skin = RunnerSkin.copy(alpha = fade)
    val jersey = RunnerJersey.copy(alpha = fade)
    val jerseyShade = RunnerJerseyShade.copy(alpha = fade)
    val shorts = RunnerShorts.copy(alpha = fade)
    val shoe = (if (crashed) CrashRedBright else RunnerShoe).copy(alpha = fade)

    // Back leg first for depth.
    drawLimb(pose.hip + Offset(-1f, 0f), pose.kneeB, pose.footB, outline, shorts, skin, behind = true)
    drawShoe(pose.footB, shoe.copy(alpha = .76f), fade, rear = true)

    // Back arm.
    drawLine(outline, pose.shoulder + Offset(-1f, 1f), pose.elbowB, 7.5f, cap = StrokeCap.Round)
    drawLine(jerseyShade, pose.shoulder + Offset(-1f, 1f), pose.elbowB, 5.2f, cap = StrokeCap.Round)
    drawLine(outline, pose.elbowB, pose.handB, 6.4f, cap = StrokeCap.Round)
    drawLine(skin.copy(alpha = .88f), pose.elbowB, pose.handB, 4.3f, cap = StrokeCap.Round)
    drawCircle(skin.copy(alpha = .88f), 2.7f, pose.handB)

    // Torso is a proper tapered body instead of one line.
    val torso = Path().apply {
        moveTo(pose.shoulder.x - 7.8f, pose.shoulder.y + 1f)
        lineTo(pose.shoulder.x + 7.8f, pose.shoulder.y - 1f)
        lineTo(pose.hip.x + 5.6f, pose.hip.y)
        lineTo(pose.hip.x - 5.4f, pose.hip.y + 1.5f)
        close()
    }
    val torsoInner = Path().apply {
        moveTo(pose.shoulder.x - 5.9f, pose.shoulder.y + 1f)
        lineTo(pose.shoulder.x + 5.9f, pose.shoulder.y)
        lineTo(pose.hip.x + 4.2f, pose.hip.y - .5f)
        lineTo(pose.hip.x - 4.1f, pose.hip.y + .5f)
        close()
    }
    drawPath(torso, outline)
    drawPath(
        torsoInner,
        brush = Brush.linearGradient(
            listOf(jersey, jerseyShade),
            start = pose.shoulder + Offset(-8f, 0f),
            end = pose.hip + Offset(8f, 0f)
        )
    )

    // Shorts / pelvis.
    val pelvis = Path().apply {
        moveTo(pose.hip.x - 6f, pose.hip.y - 1f)
        lineTo(pose.hip.x + 6.5f, pose.hip.y - 1.5f)
        lineTo(pose.hip.x + 5.2f, pose.hip.y + 6.2f)
        lineTo(pose.hip.x - 5.5f, pose.hip.y + 6.2f)
        close()
    }
    drawPath(pelvis, outline)
    val pelvisInner = Path().apply {
        moveTo(pose.hip.x - 4.5f, pose.hip.y)
        lineTo(pose.hip.x + 5f, pose.hip.y - .5f)
        lineTo(pose.hip.x + 3.9f, pose.hip.y + 5f)
        lineTo(pose.hip.x - 4.2f, pose.hip.y + 5f)
        close()
    }
    drawPath(pelvisInner, shorts)

    // Front leg.
    drawLimb(pose.hip + Offset(1f, 1f), pose.kneeA, pose.footA, outline, shorts, skin, behind = false)
    drawShoe(pose.footA, shoe, fade, rear = false)

    // Front arm.
    drawLine(outline, pose.shoulder + Offset(2f, 0f), pose.elbowA, 8f, cap = StrokeCap.Round)
    drawLine(jersey, pose.shoulder + Offset(2f, 0f), pose.elbowA, 5.6f, cap = StrokeCap.Round)
    drawLine(outline, pose.elbowA, pose.handA, 6.6f, cap = StrokeCap.Round)
    drawLine(skin, pose.elbowA, pose.handA, 4.5f, cap = StrokeCap.Round)
    drawCircle(skin, 2.8f, pose.handA)

    // Neck, head, hair and a tiny face highlight.
    drawLine(outline, pose.shoulder + Offset(2f, -1f), pose.neck, 7.6f, cap = StrokeCap.Round)
    drawLine(skin, pose.shoulder + Offset(2f, -1f), pose.neck, 5.1f, cap = StrokeCap.Round)
    drawCircle(outline, radius = 9.1f, center = pose.head)
    drawCircle(skin, radius = 7.4f, center = pose.head)
    val hair = Path().apply {
        moveTo(pose.head.x - 6.6f, pose.head.y - 2.8f)
        quadraticTo(pose.head.x - 2f, pose.head.y - 8.2f, pose.head.x + 5.8f, pose.head.y - 5.4f)
        lineTo(pose.head.x + 6.2f, pose.head.y - 1.3f)
        quadraticTo(pose.head.x + 1.5f, pose.head.y - 4.1f, pose.head.x - 6.6f, pose.head.y - 2.8f)
        close()
    }
    drawPath(hair, RunnerShorts.copy(alpha = fade))
    drawCircle(Color.White.copy(alpha = .42f * fade), 1.1f, pose.head + Offset(3.5f, -1.2f))

    // Jersey neon stripe ties the runner to the graph color.
    drawLine(
        color = (if (crashed) CrashRedBright else CrashLiveBright).copy(alpha = .78f * fade),
        start = pose.shoulder + Offset(-5.1f, 3.5f),
        end = pose.hip + Offset(-3.5f, -2.1f),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )
}

private data class RunnerPose(
    val hip: Offset,
    val shoulder: Offset,
    val neck: Offset,
    val head: Offset,
    val kneeA: Offset,
    val kneeB: Offset,
    val footA: Offset,
    val footB: Offset,
    val elbowA: Offset,
    val elbowB: Offset,
    val handA: Offset,
    val handB: Offset
)

private fun runnerPose(cycle: Float, bob: Float): RunnerPose {
    val stride = sin(cycle)
    val lift = cos(cycle)
    val hip = Offset(-1.5f, -34f - bob)
    val shoulder = hip + Offset(8.5f, -25.5f)
    val neck = shoulder + Offset(5f, -5.8f)
    val head = neck + Offset(3.2f, -8.3f)

    // One foot is kept on the graph in each half-cycle while the other swings above it.
    val footA = Offset(
        x = stride * 22f,
        y = -max(0f, -lift) * 10f
    )
    val footB = Offset(
        x = -stride * 22f,
        y = -max(0f, lift) * 10f
    )

    val kneeA = lerpOffset(hip, footA, .52f) + Offset(7f + abs(lift) * 5f, -5f - max(0f, -lift) * 2f)
    val kneeB = lerpOffset(hip, footB, .52f) + Offset(6f + abs(lift) * 4f, -4f - max(0f, lift) * 2f)

    val armSwing = -stride
    val elbowA = shoulder + Offset(armSwing * 14f + 7f, 9f + abs(lift) * 2f)
    val handA = elbowA + Offset(armSwing * 11f + 5f, 8f)
    val elbowB = shoulder + Offset(-armSwing * 13f - 4f, 10f)
    val handB = elbowB + Offset(-armSwing * 10f - 2f, 8f)

    return RunnerPose(
        hip = hip,
        shoulder = shoulder,
        neck = neck,
        head = head,
        kneeA = kneeA,
        kneeB = kneeB,
        footA = footA,
        footB = footB,
        elbowA = elbowA,
        elbowB = elbowB,
        handA = handA,
        handB = handB
    )
}

private fun DrawScope.drawLimb(
    hip: Offset,
    knee: Offset,
    foot: Offset,
    outline: Color,
    shorts: Color,
    skin: Color,
    behind: Boolean
) {
    val alpha = if (behind) .76f else 1f
    drawLine(outline.copy(alpha = outline.alpha * alpha), hip, knee, 9.5f, cap = StrokeCap.Round)
    drawLine(shorts.copy(alpha = shorts.alpha * alpha), hip, knee, 6.4f, cap = StrokeCap.Round)
    drawCircle(shorts.copy(alpha = shorts.alpha * alpha), 4.1f, knee)
    drawLine(outline.copy(alpha = outline.alpha * alpha), knee, foot + Offset(0f, -2f), 7.5f, cap = StrokeCap.Round)
    drawLine(skin.copy(alpha = skin.alpha * alpha), knee, foot + Offset(0f, -2f), 4.7f, cap = StrokeCap.Round)
}

private fun DrawScope.drawShoe(foot: Offset, color: Color, fade: Float, rear: Boolean) {
    val alpha = if (rear) .72f else 1f
    drawLine(
        RunnerOutline.copy(alpha = .82f * fade * alpha),
        foot + Offset(-3f, -1f),
        foot + Offset(8f, 0f),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color.copy(alpha = color.alpha * alpha),
        foot + Offset(-2.4f, -1.4f),
        foot + Offset(7f, -.2f),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawCrashBurst(point: Offset, crashProgress: Float) {
    val p = crashProgress.coerceIn(0f, 1f)
    val radius = 14f + 72f * p
    drawCircle(CrashRed.copy(alpha = .20f * (1f - p)), radius = radius, center = point)
    drawCircle(CrashRedBright.copy(alpha = .62f * (1f - p * .55f)), radius = 5f + 8f * (1f - p), center = point)

    repeat(16) { index ->
        val angle = index / 16.0 * PI * 2.0 + p * .8
        val startRadius = 8f + 13f * p
        val endRadius = 14f + 61f * p
        val start = Offset(
            point.x + cos(angle).toFloat() * startRadius,
            point.y + sin(angle).toFloat() * startRadius
        )
        val end = Offset(
            point.x + cos(angle).toFloat() * endRadius,
            point.y + sin(angle).toFloat() * endRadius
        )
        drawLine(
            CrashRedBright.copy(alpha = .72f * (1f - p)),
            start,
            end,
            strokeWidth = 1.8f + (index % 3) * .55f,
            cap = StrokeCap.Round
        )
    }

    repeat(10) { index ->
        val angle = index * 2.31 + .4
        val travel = 18f + 74f * p * ((index % 4) + 1) / 4f
        val particle = Offset(
            point.x + cos(angle).toFloat() * travel,
            point.y + sin(angle).toFloat() * travel + 32f * p.pow(1.35f)
        )
        drawCircle(
            CrashRedBright.copy(alpha = .66f * (1f - p)),
            radius = 1.5f + (index % 3),
            center = particle
        )
    }
}

private fun normalize(vector: Offset): Offset {
    val length = kotlin.math.sqrt(vector.x * vector.x + vector.y * vector.y).coerceAtLeast(.001f)
    return Offset(vector.x / length, vector.y / length)
}

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun easeOutCubic(t: Float): Float {
    val x = 1f - t.coerceIn(0f, 1f)
    return 1f - x * x * x
}
