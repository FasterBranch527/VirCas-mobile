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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val CrashLive = Color(0xFF55EDA1)
private val CrashLiveBright = Color(0xFFD2FFE8)
private val CrashLiveDeep = Color(0xFF16B96E)
private val CrashRed = Color(0xFFFF4F67)
private val CrashRedBright = Color(0xFFFFA1AE)
private val CrashGrid = Color(0xFF93A9A1)

// Friendly, faceless runner palette. The character is intentionally stylized instead of
// trying to fake a realistic face from Canvas primitives.
private val RunnerSuit = Color(0xFFE9FFF5)
private val RunnerSuitShade = Color(0xFF9AC9B7)
private val RunnerLeggings = Color(0xFF0D1714)
private val RunnerLeggingsShade = Color(0xFF263934)
private val RunnerHelmet = Color(0xFF111B18)
private val RunnerVisor = Color(0xFF9EFFE0)
private val RunnerShoe = Color(0xFF70F9C6)
private val RunnerOutline = Color(0xFF020706)

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
                listOf(Color(0xFF081712), Color(0xFF05100D), Color(0xFF020706))
            ),
            RoundedCornerShape(28.dp)
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val geometry = buildCrashGeometry(elapsedSeconds)
            drawCrashAtmosphere(elapsedSeconds, geometry, crashed)
            drawCrashGrid(elapsedSeconds)
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
            color = Color.White.copy(alpha = .27f),
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
    val angleDegrees: Float
)

private fun DrawScope.buildCrashGeometry(elapsedSeconds: Float): CrashGeometry {
    val tNow = elapsedSeconds.coerceAtLeast(0f)
    val left = size.width * .045f
    val baseline = size.height * .86f
    val xSpeed = size.width * .155f

    fun worldLift(t: Float): Float = size.height * (.043f * t + .0108f * t * t)

    val rawEndX = left + tNow * xSpeed
    val rawEndY = baseline - worldLift(tNow)
    val cameraX = max(0f, rawEndX - size.width * .72f)
    val cameraY = max(0f, size.height * .22f - rawEndY)

    val samples = max(36, min(150, (tNow * 18f).toInt() + 36))
    val path = Path()
    val fill = Path()
    var first = Offset(left - cameraX, baseline + cameraY)
    var previous = first
    var end = first

    for (index in 0..samples) {
        val fraction = index / samples.toFloat()
        val t = tNow * fraction
        val point = Offset(
            x = left + t * xSpeed - cameraX,
            y = baseline - worldLift(t) + cameraY
        )
        if (index == 0) {
            path.moveTo(point.x, point.y)
            fill.moveTo(point.x, size.height * .94f)
            fill.lineTo(point.x, point.y)
            first = point
        } else {
            path.lineTo(point.x, point.y)
            fill.lineTo(point.x, point.y)
        }
        previous = end
        end = point
    }

    fill.lineTo(end.x, size.height * .94f)
    fill.lineTo(first.x, size.height * .94f)
    fill.close()

    val angle = if (tNow < .04f) {
        -3f
    } else {
        Math.toDegrees(
            atan2((end.y - previous.y).toDouble(), (end.x - previous.x).toDouble())
        ).toFloat().coerceIn(-47f, -2f)
    }

    return CrashGeometry(path, fill, first, end, previous, angle)
}

private fun DrawScope.drawCrashAtmosphere(
    elapsedSeconds: Float,
    geometry: CrashGeometry,
    crashed: Boolean
) {
    val speed = (elapsedSeconds / 4.5f).coerceIn(0f, 1f)
    val accent = if (crashed) CrashRed else CrashLive

    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = .015f), Color.Transparent, accent.copy(alpha = .014f)),
            startY = 0f,
            endY = size.height
        )
    )

    repeat(10) { index ->
        val drift = (elapsedSeconds * (52f + index * 4.7f)) % (size.width * 1.35f)
        val x = size.width * 1.15f - drift
        val y = size.height * (.22f + (index % 7) * .095f)
        val length = size.width * (.055f + speed * .07f)
        drawLine(
            color = Color.White.copy(alpha = .015f + speed * .018f),
            start = Offset(x, y),
            end = Offset(x - length, y + length * .06f),
            strokeWidth = 1.2f + speed * .5f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = .065f), accent.copy(alpha = .014f), Color.Transparent),
            center = geometry.end,
            radius = size.minDimension * .065f
        ),
        radius = size.minDimension * .065f,
        center = geometry.end
    )
}

private fun DrawScope.drawCrashGrid(elapsedSeconds: Float) {
    val spacingX = max(44f, size.width / 8.5f)
    val spacingY = spacingX * .76f
    val driftX = (elapsedSeconds * 31f) % spacingX
    val driftY = (elapsedSeconds * 7f) % spacingY

    var x = -spacingX + driftX
    while (x < size.width + spacingX) {
        drawLine(
            CrashGrid.copy(alpha = .045f),
            Offset(x, size.height * .08f),
            Offset(x - size.width * .075f, size.height),
            strokeWidth = 1f
        )
        x += spacingX
    }

    var y = size.height * .13f + driftY
    while (y < size.height) {
        drawLine(
            CrashGrid.copy(alpha = .045f),
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
            listOf(accent.copy(alpha = .075f), accent.copy(alpha = .018f), Color.Transparent),
            startY = geometry.end.y,
            endY = size.height * .94f
        )
    )
    drawPath(geometry.path, Color.Black.copy(alpha = .30f), style = Stroke(16f, cap = StrokeCap.Round))
    drawPath(geometry.path, accent.copy(alpha = .10f), style = Stroke(12f, cap = StrokeCap.Round))
    drawPath(geometry.path, accent.copy(alpha = .28f), style = Stroke(7f, cap = StrokeCap.Round))
    drawPath(
        geometry.path,
        brush = Brush.linearGradient(
            listOf(CrashLiveDeep.copy(alpha = .52f), accent, hot),
            start = geometry.start,
            end = geometry.end
        ),
        style = Stroke(3.6f, cap = StrokeCap.Round)
    )

    drawCircle(accent.copy(alpha = .17f), radius = 7f, center = geometry.end)
    drawCircle(hot, radius = 2.5f, center = geometry.end)
}

private fun DrawScope.drawTrackParticles(
    geometry: CrashGeometry,
    elapsedSeconds: Float,
    accent: Color
) {
    val tangent = normalize(geometry.end - geometry.previous)
    val up = Offset(tangent.y, -tangent.x)
    repeat(7) { index ->
        val phase = ((elapsedSeconds * (1.0f + index * .09f) + index * .61f) % 1.4f) / 1.4f
        val distance = 8f + phase * 70f
        val lift = sin(index * 1.91f + elapsedSeconds * 3.1f) * 5f + phase * 9f
        val particle = geometry.end - tangent * distance + up * lift
        drawCircle(
            accent.copy(alpha = (1f - phase) * .20f),
            radius = 1.0f + (1f - phase) * 1.6f,
            center = particle
        )
    }
}

private data class RunnerPose(
    val ground: Offset,
    val up: Offset,
    val bodyUp: Offset,
    val bodyRight: Offset,
    val hip: Offset,
    val chest: Offset,
    val head: Offset,
    val kneeFront: Offset,
    val kneeBack: Offset,
    val footFront: Offset,
    val footBack: Offset,
    val elbowFront: Offset,
    val elbowBack: Offset,
    val handFront: Offset,
    val handBack: Offset
)

private fun DrawScope.drawCrashRunner(
    point: Offset,
    tangentDegrees: Float,
    elapsedSeconds: Float,
    running: Boolean,
    crashed: Boolean,
    crashProgress: Float
) {
    val scale = (size.minDimension / 445f).coerceIn(1.25f, 1.90f)
    val p = crashProgress.coerceIn(0f, 1f)
    val baseAngle = tangentDegrees * (PI / 180.0).toFloat()
    val baseGround = normalize(Offset(cos(baseAngle), sin(baseAngle)))
    val baseSlopeUp = normalize(Offset(baseGround.y, -baseGround.x))

    val fallPoint = if (crashed) {
        point + Offset(62f * scale * p, 112f * scale * p.pow(1.55f))
    } else {
        point
    }
    val tumbleRadians = if (crashed) (p * 250f) * (PI / 180.0).toFloat() else 0f
    val ground = rotateVector(baseGround, tumbleRadians)
    val slopeUp = rotateVector(baseSlopeUp, tumbleRadians)

    val speed = if (running && !crashed) (8.7f + min(3.8f, elapsedSeconds * .34f)) else 0f
    val cycle = if (running && !crashed) elapsedSeconds * speed else elapsedSeconds * 8.7f
    val stride = sin(cycle)

    // Keep the torso visually upright. The hill affects the feet much more than the spine.
    val upright = Offset(0f, -1f)
    val bodyUpBase = normalize(upright * .88f + baseSlopeUp * .12f + baseGround * .055f)
    val bodyUp = rotateVector(bodyUpBase, tumbleRadians)
    val bodyRight = normalize(Offset(-bodyUp.y, bodyUp.x))
    val maxForward = abs(stride) * 15f * scale
    val anchor = fallPoint - ground * maxForward

    val pose = makeRunnerPose(
        anchor = anchor,
        ground = ground,
        slopeUp = slopeUp,
        bodyUp = bodyUp,
        bodyRight = bodyRight,
        cycle = cycle,
        scale = scale,
        running = running && !crashed
    )
    val fade = if (crashed) 1f - p * .44f else 1f

    val shadowCenter = anchor - slopeUp * (1.3f * scale)
    val shadowLeft = shadowCenter - ground * (16f * scale)
    val shadowRight = shadowCenter + ground * (16f * scale)
    drawLine(Color.Black.copy(alpha = .34f * fade), shadowLeft, shadowRight, 5.5f * scale, cap = StrokeCap.Round)
    drawLine(CrashLive.copy(alpha = .08f * fade), shadowLeft, shadowRight, 1.8f * scale, cap = StrokeCap.Round)

    drawMascotRunner(pose, scale, fade, crashed)

    if (running && !crashed) {
        drawFootSparks(pose, cycle, scale)
    }
}

private fun makeRunnerPose(
    anchor: Offset,
    ground: Offset,
    slopeUp: Offset,
    bodyUp: Offset,
    bodyRight: Offset,
    cycle: Float,
    scale: Float,
    running: Boolean
): RunnerPose {
    val stride = if (running) sin(cycle) else .22f
    val lift = if (running) cos(cycle) else -.15f
    val frontLift = max(0f, -lift)
    val backLift = max(0f, lift)
    val bob = if (running) abs(cos(cycle * 2f)) * 1.05f * scale else 0f

    val footFront = anchor + ground * (stride * 15f * scale) + slopeUp * (frontLift * 7.5f * scale)
    val footBack = anchor - ground * (stride * 15f * scale) + slopeUp * (backLift * 7.5f * scale)
    val hip = anchor + bodyUp * (36f * scale) + slopeUp * bob
    val chest = hip + bodyUp * (24f * scale) + ground * (4.8f * scale)
    val head = chest + bodyUp * (16f * scale) + ground * (2.8f * scale)

    val kneeFront = lerpOffset(hip, footFront, .53f) + ground * ((4.5f + 3.5f * max(0f, stride)) * scale) + slopeUp * (3.3f * scale)
    val kneeBack = lerpOffset(hip, footBack, .53f) + ground * ((3.8f - 3.5f * min(0f, stride)) * scale) + slopeUp * (2.7f * scale)

    val armSwing = -stride
    val elbowFront = chest - bodyUp * (8.5f * scale) + ground * (armSwing * 10f * scale)
    val handFront = elbowFront - bodyUp * (8.5f * scale) + ground * (armSwing * 6f * scale)
    val elbowBack = chest - bodyUp * (8.5f * scale) - ground * (armSwing * 9.5f * scale)
    val handBack = elbowBack - bodyUp * (8f * scale) - ground * (armSwing * 5.8f * scale)

    return RunnerPose(
        ground = ground,
        up = slopeUp,
        bodyUp = bodyUp,
        bodyRight = bodyRight,
        hip = hip,
        chest = chest,
        head = head,
        kneeFront = kneeFront,
        kneeBack = kneeBack,
        footFront = footFront,
        footBack = footBack,
        elbowFront = elbowFront,
        elbowBack = elbowBack,
        handFront = handFront,
        handBack = handBack
    )
}

private fun DrawScope.drawMascotRunner(pose: RunnerPose, scale: Float, fade: Float, crashed: Boolean) {
    val outline = RunnerOutline.copy(alpha = .94f * fade)
    val suit = RunnerSuit.copy(alpha = fade)
    val suitShade = RunnerSuitShade.copy(alpha = fade)
    val leggings = RunnerLeggings.copy(alpha = fade)
    val leggingsShade = RunnerLeggingsShade.copy(alpha = fade)
    val accent = (if (crashed) CrashRedBright else CrashLiveBright).copy(alpha = fade)
    val shoe = (if (crashed) CrashRedBright else RunnerShoe).copy(alpha = fade)

    // Back limbs first. Full leggings/gloves remove the uncanny pseudo-skin look.
    drawMascotLeg(pose, pose.kneeBack, pose.footBack, scale, outline.copy(alpha = .68f), leggingsShade.copy(alpha = .78f))
    drawMascotShoe(pose.footBack, pose.ground, pose.up, scale, shoe.copy(alpha = .68f), outline.copy(alpha = .68f))
    drawMascotArm(
        chest = pose.chest - pose.bodyRight * (1.4f * scale),
        elbow = pose.elbowBack,
        hand = pose.handBack,
        scale = scale,
        outline = outline.copy(alpha = .68f),
        sleeve = suitShade.copy(alpha = .72f),
        glove = leggingsShade.copy(alpha = .80f)
    )

    // Rounded athletic torso: broad shoulders, compact waist, slight forward lean.
    val shoulderL = pose.chest - pose.bodyRight * (7.7f * scale) + pose.bodyUp * (1.0f * scale)
    val shoulderR = pose.chest + pose.bodyRight * (7.7f * scale) - pose.bodyUp * (.2f * scale)
    val waistL = pose.hip - pose.bodyRight * (4.8f * scale)
    val waistR = pose.hip + pose.bodyRight * (4.8f * scale)
    val torso = Path().apply {
        moveTo(shoulderL.x, shoulderL.y)
        quadraticTo(
            pose.chest.x - pose.bodyRight.x * (9.2f * scale),
            pose.chest.y - pose.bodyRight.y * (9.2f * scale),
            waistL.x,
            waistL.y
        )
        quadraticTo(pose.hip.x, pose.hip.y + 1.5f * scale, waistR.x, waistR.y)
        quadraticTo(
            pose.chest.x + pose.bodyRight.x * (9.2f * scale),
            pose.chest.y + pose.bodyRight.y * (9.2f * scale),
            shoulderR.x,
            shoulderR.y
        )
        close()
    }
    drawPath(torso, outline)

    val inset = 1.35f * scale
    val innerL = shoulderL + pose.bodyRight * inset - pose.bodyUp * (.1f * scale)
    val innerR = shoulderR - pose.bodyRight * inset
    val innerWaistL = waistL + pose.bodyRight * inset
    val innerWaistR = waistR - pose.bodyRight * inset
    val torsoInner = Path().apply {
        moveTo(innerL.x, innerL.y)
        quadraticTo(
            pose.chest.x - pose.bodyRight.x * (7.4f * scale),
            pose.chest.y - pose.bodyRight.y * (7.4f * scale),
            innerWaistL.x,
            innerWaistL.y
        )
        quadraticTo(pose.hip.x, pose.hip.y, innerWaistR.x, innerWaistR.y)
        quadraticTo(
            pose.chest.x + pose.bodyRight.x * (7.4f * scale),
            pose.chest.y + pose.bodyRight.y * (7.4f * scale),
            innerR.x,
            innerR.y
        )
        close()
    }
    drawPath(
        torsoInner,
        brush = Brush.linearGradient(listOf(suit, suitShade), start = shoulderL, end = waistR)
    )

    // Compact dark running shorts over leggings.
    val shortsTopL = pose.hip - pose.bodyRight * (5.4f * scale)
    val shortsTopR = pose.hip + pose.bodyRight * (5.4f * scale)
    val shortsLow = pose.hip - pose.bodyUp * (6.2f * scale)
    val shorts = Path().apply {
        moveTo(shortsTopL.x, shortsTopL.y)
        lineTo(shortsTopR.x, shortsTopR.y)
        lineTo((shortsLow + pose.bodyRight * (4.4f * scale)).x, (shortsLow + pose.bodyRight * (4.4f * scale)).y)
        lineTo((shortsLow - pose.bodyRight * (4.4f * scale)).x, (shortsLow - pose.bodyRight * (4.4f * scale)).y)
        close()
    }
    drawPath(shorts, outline)
    val shortsInner = Path().apply {
        val a = shortsTopL + pose.bodyRight * (1.2f * scale)
        val b = shortsTopR - pose.bodyRight * (1.2f * scale)
        val c = shortsLow + pose.bodyRight * (3.2f * scale)
        val d = shortsLow - pose.bodyRight * (3.2f * scale)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(shortsInner, leggings)

    drawMascotLeg(pose, pose.kneeFront, pose.footFront, scale, outline, leggings)
    drawMascotShoe(pose.footFront, pose.ground, pose.up, scale, shoe, outline)

    drawMascotArm(
        chest = pose.chest + pose.bodyRight * (1.6f * scale),
        elbow = pose.elbowFront,
        hand = pose.handFront,
        scale = scale,
        outline = outline,
        sleeve = suit,
        glove = leggings
    )

    // Small collar/neck bridge.
    val neckBottom = pose.chest + pose.bodyUp * (4.2f * scale) + pose.ground * (1.8f * scale)
    val neckTop = pose.head - pose.bodyUp * (7.1f * scale)
    drawLine(outline, neckBottom, neckTop, 6.0f * scale, cap = StrokeCap.Round)
    drawLine(leggingsShade, neckBottom, neckTop, 3.8f * scale, cap = StrokeCap.Round)

    // Helmet instead of a fake face. A single luminous visor reads much cleaner at this size.
    drawCircle(outline, 8.1f * scale, pose.head)
    drawCircle(RunnerHelmet.copy(alpha = fade), 6.8f * scale, pose.head)
    val visorStart = pose.head - pose.ground * (1.2f * scale) + pose.bodyUp * (1.1f * scale)
    val visorEnd = pose.head + pose.ground * (5.5f * scale) + pose.bodyUp * (.2f * scale)
    drawLine(RunnerVisor.copy(alpha = .88f * fade), visorStart, visorEnd, 2.5f * scale, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .34f * fade), visorStart + pose.bodyUp * (.7f * scale), visorEnd - pose.ground * (1.8f * scale), 0.8f * scale, cap = StrokeCap.Round)

    // Clean neon identity stripe on the suit.
    val stripeStart = pose.chest - pose.bodyRight * (4.8f * scale) + pose.bodyUp * (1.4f * scale)
    val stripeEnd = pose.hip - pose.bodyRight * (3.1f * scale) + pose.bodyUp * (1.0f * scale)
    drawLine(accent.copy(alpha = .82f), stripeStart, stripeEnd, 1.7f * scale, cap = StrokeCap.Round)

    // Tiny chest badge, no facial detail.
    drawCircle(
        color = (if (crashed) CrashRed else CrashLiveDeep).copy(alpha = .88f * fade),
        radius = 2.15f * scale,
        center = lerpOffset(pose.chest, pose.hip, .34f) + pose.bodyRight * (1.2f * scale)
    )
}

private fun DrawScope.drawMascotLeg(
    pose: RunnerPose,
    knee: Offset,
    foot: Offset,
    scale: Float,
    outline: Color,
    legColor: Color
) {
    val thighEnd = lerpOffset(pose.hip, knee, .44f)
    drawLine(outline, pose.hip, thighEnd, 9.6f * scale, cap = StrokeCap.Round)
    drawLine(legColor, pose.hip, thighEnd, 6.2f * scale, cap = StrokeCap.Round)
    drawLine(outline, thighEnd, knee, 7.7f * scale, cap = StrokeCap.Round)
    drawLine(legColor, thighEnd, knee, 4.8f * scale, cap = StrokeCap.Round)
    drawCircle(outline, 3.7f * scale, knee)
    drawCircle(legColor, 2.4f * scale, knee)
    drawLine(outline, knee, foot - pose.up * (2.0f * scale), 6.8f * scale, cap = StrokeCap.Round)
    drawLine(legColor, knee, foot - pose.up * (2.0f * scale), 4.1f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawMascotArm(
    chest: Offset,
    elbow: Offset,
    hand: Offset,
    scale: Float,
    outline: Color,
    sleeve: Color,
    glove: Color
) {
    val sleeveEnd = lerpOffset(chest, elbow, .62f)
    drawLine(outline, chest, sleeveEnd, 7.4f * scale, cap = StrokeCap.Round)
    drawLine(sleeve, chest, sleeveEnd, 4.8f * scale, cap = StrokeCap.Round)
    drawLine(outline, sleeveEnd, elbow, 6.0f * scale, cap = StrokeCap.Round)
    drawLine(glove, sleeveEnd, elbow, 3.6f * scale, cap = StrokeCap.Round)
    drawLine(outline, elbow, hand, 5.6f * scale, cap = StrokeCap.Round)
    drawLine(glove, elbow, hand, 3.4f * scale, cap = StrokeCap.Round)
    drawCircle(outline, 2.8f * scale, hand)
    drawCircle(glove, 1.9f * scale, hand)
}

private fun DrawScope.drawMascotShoe(
    foot: Offset,
    ground: Offset,
    up: Offset,
    scale: Float,
    color: Color,
    outline: Color
) {
    val heel = foot - ground * (3.0f * scale) + up * (.5f * scale)
    val toe = foot + ground * (8.5f * scale)
    drawLine(outline, heel, toe, 6.0f * scale, cap = StrokeCap.Round)
    drawLine(color, heel + up * (.4f * scale), toe, 3.4f * scale, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .34f * color.alpha), foot, toe - ground * (1.3f * scale), 0.9f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawFootSparks(pose: RunnerPose, cycle: Float, scale: Float) {
    val c = cos(cycle)
    val contact = if (c >= 0f) pose.footFront else pose.footBack
    val intensity = abs(c).coerceIn(0f, 1f)
    repeat(4) { index ->
        val back = pose.ground * ((4f + index * 4.2f) * scale)
        val lift = pose.up * ((index % 2 + 1) * 1.9f * scale)
        drawCircle(
            CrashLiveBright.copy(alpha = (.24f - index * .038f) * intensity),
            radius = (1.2f + index * .20f) * scale,
            center = contact - back + lift
        )
    }
}

private fun DrawScope.drawCrashBurst(point: Offset, crashProgress: Float) {
    val p = crashProgress.coerceIn(0f, 1f)
    val radius = 12f + 66f * p
    drawCircle(CrashRed.copy(alpha = .18f * (1f - p)), radius = radius, center = point)
    drawCircle(CrashRedBright.copy(alpha = .68f * (1f - p * .55f)), radius = 4f + 7f * (1f - p), center = point)

    repeat(14) { index ->
        val angle = index / 14.0 * PI * 2.0 + p * .7
        val startRadius = 7f + 11f * p
        val endRadius = 13f + 55f * p
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
            strokeWidth = 1.8f + (index % 3) * .45f,
            cap = StrokeCap.Round
        )
    }
}

private fun normalize(vector: Offset): Offset {
    val length = sqrt(vector.x * vector.x + vector.y * vector.y).coerceAtLeast(.001f)
    return Offset(vector.x / length, vector.y / length)
}

private fun rotateVector(vector: Offset, radians: Float): Offset {
    if (radians == 0f) return vector
    val c = cos(radians)
    val s = sin(radians)
    return Offset(vector.x * c - vector.y * s, vector.x * s + vector.y * c)
}

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
