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

private val StickBody = Color(0xFFF2FFF9)
private val StickBodyBack = Color(0xFF7CB8A3)
private val StickJoint = Color(0xFFBAF9DF)
private val StickHeadFill = Color(0xFF08100E)
private val StickAccent = Color(0xFF6CFFD0)

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

        Column(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 15.dp)) {
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
            listOf(accent.copy(alpha = .052f), accent.copy(alpha = .010f), Color.Transparent),
            center = geometry.end,
            radius = size.minDimension * .058f
        ),
        radius = size.minDimension * .058f,
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
            listOf(accent.copy(alpha = .070f), accent.copy(alpha = .016f), Color.Transparent),
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

    drawCircle(accent.copy(alpha = .14f), radius = 6.4f, center = geometry.end)
    drawCircle(hot, radius = 2.3f, center = geometry.end)
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
            accent.copy(alpha = (1f - phase) * .18f),
            radius = .9f + (1f - phase) * 1.5f,
            center = particle
        )
    }
}

private data class FootState(val x: Float, val lift: Float, val support: Boolean)

private data class RunnerPose(
    val ground: Offset,
    val up: Offset,
    val bodyUp: Offset,
    val hip: Offset,
    val chest: Offset,
    val neck: Offset,
    val head: Offset,
    val kneeFront: Offset,
    val kneeBack: Offset,
    val footFront: Offset,
    val footBack: Offset,
    val elbowFront: Offset,
    val elbowBack: Offset,
    val handFront: Offset,
    val handBack: Offset,
    val frontSupport: Boolean
)

private fun stickFootState(phaseInput: Float): FootState {
    val twoPi = (PI * 2.0).toFloat()
    var phase = phaseInput % twoPi
    if (phase < 0f) phase += twoPi
    val stride = 17f

    return if (phase < PI.toFloat()) {
        val u = phase / PI.toFloat()
        val eased = u * u * (3f - 2f * u)
        val x = -stride + stride * 2f * eased
        val lift = sin(phase).coerceAtLeast(0f).pow(1.35f) * 11f
        FootState(x, lift, false)
    } else {
        val u = (phase - PI.toFloat()) / PI.toFloat()
        val x = stride - stride * 2f * u
        FootState(x, 0f, true)
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
    val scale = (size.minDimension / 430f).coerceIn(1.30f, 2.0f)
    val p = crashProgress.coerceIn(0f, 1f)
    val baseAngle = tangentDegrees * (PI / 180.0).toFloat()
    val baseGround = normalize(Offset(cos(baseAngle), sin(baseAngle)))
    val baseSlopeUp = normalize(Offset(baseGround.y, -baseGround.x))

    val fallPoint = if (crashed) {
        point + Offset(64f * scale * p, 116f * scale * p.pow(1.55f))
    } else {
        point
    }
    val tumbleRadians = if (crashed) (p * 260f) * (PI / 180.0).toFloat() else 0f
    val ground = rotateVector(baseGround, tumbleRadians)
    val slopeUp = rotateVector(baseSlopeUp, tumbleRadians)

    val cadence = 8.2f + min(4.3f, elapsedSeconds * .34f)
    val cycle = elapsedSeconds * cadence
    val frontState = stickFootState(cycle)
    val backState = stickFootState(cycle + PI.toFloat())
    val supportX = if (frontState.support) frontState.x else backState.x

    val upright = Offset(0f, -1f)
    val climbLean = ((-tangentDegrees) / 47f).coerceIn(0f, 1f)
    val bodyUpBase = normalize(upright * .91f + baseSlopeUp * .09f + baseGround * (.045f + climbLean * .04f))
    val bodyUp = rotateVector(bodyUpBase, tumbleRadians)

    // The curve ends under the support foot. The airborne foot may reach slightly ahead,
    // which makes the runner lead the graph naturally instead of being dragged by it.
    val anchor = fallPoint - ground * (supportX * scale)
    val pose = makeStickPose(
        anchor = anchor,
        ground = ground,
        slopeUp = slopeUp,
        bodyUp = bodyUp,
        cycle = cycle,
        scale = scale,
        animated = running || crashed
    )
    val fade = if (crashed) 1f - p * .45f else 1f

    val shadowCenter = fallPoint - slopeUp * (1.0f * scale)
    drawLine(
        Color.Black.copy(alpha = .31f * fade),
        shadowCenter - ground * (15f * scale),
        shadowCenter + ground * (15f * scale),
        5.0f * scale,
        cap = StrokeCap.Round
    )

    drawStickRunner(pose, scale, fade, crashed)

    if (running && !crashed) {
        drawFootSparks(pose, cycle, scale)
    }
}

private fun makeStickPose(
    anchor: Offset,
    ground: Offset,
    slopeUp: Offset,
    bodyUp: Offset,
    cycle: Float,
    scale: Float,
    animated: Boolean
): RunnerPose {
    val front = if (animated) stickFootState(cycle) else FootState(7f, 0f, true)
    val back = if (animated) stickFootState(cycle + PI.toFloat()) else FootState(-8f, 5f, false)

    val footFront = anchor + ground * (front.x * scale) + slopeUp * (front.lift * scale)
    val footBack = anchor + ground * (back.x * scale) + slopeUp * (back.lift * scale)

    val contactBlend = abs(sin(cycle)).coerceIn(0f, 1f)
    val bob = if (animated) (1.0f - contactBlend) * 1.4f * scale else 0f
    val hip = anchor + bodyUp * (38f * scale) + slopeUp * bob
    val chest = hip + bodyUp * (25f * scale) + ground * (4.5f * scale)
    val neck = chest + bodyUp * (8f * scale) + ground * (1.0f * scale)
    val head = neck + bodyUp * (9.5f * scale) + ground * (2.2f * scale)

    fun kneeFor(foot: Offset, state: FootState, frontLeg: Boolean): Offset {
        val middle = lerpOffset(hip, foot, .53f)
        val forwardBend = if (frontLeg) 1f else .78f
        val bend = if (state.support) 4.2f else 8.6f + state.lift * .22f
        return middle + ground * (bend * forwardBend * scale) + slopeUp * (2.2f * scale)
    }

    val kneeFront = kneeFor(footFront, front, true)
    val kneeBack = kneeFor(footBack, back, false)

    val armSwing = if (animated) sin(cycle + PI.toFloat()) else -.25f
    val elbowFront = chest - bodyUp * (9f * scale) + ground * (armSwing * 12f * scale) + slopeUp * (2.2f * scale)
    val handFront = elbowFront - bodyUp * (8.5f * scale) + ground * (armSwing * 6.5f * scale)
    val elbowBack = chest - bodyUp * (9f * scale) - ground * (armSwing * 11f * scale) + slopeUp * (1.5f * scale)
    val handBack = elbowBack - bodyUp * (8f * scale) - ground * (armSwing * 6f * scale)

    return RunnerPose(
        ground = ground,
        up = slopeUp,
        bodyUp = bodyUp,
        hip = hip,
        chest = chest,
        neck = neck,
        head = head,
        kneeFront = kneeFront,
        kneeBack = kneeBack,
        footFront = footFront,
        footBack = footBack,
        elbowFront = elbowFront,
        elbowBack = elbowBack,
        handFront = handFront,
        handBack = handBack,
        frontSupport = front.support
    )
}

private fun DrawScope.drawStickRunner(
    pose: RunnerPose,
    scale: Float,
    fade: Float,
    crashed: Boolean
) {
    val front = (if (crashed) CrashRedBright else StickBody).copy(alpha = fade)
    val back = (if (crashed) CrashRed.copy(alpha = .62f) else StickBodyBack.copy(alpha = .58f * fade))
    val joint = (if (crashed) CrashRedBright else StickJoint).copy(alpha = .80f * fade)
    val accent = (if (crashed) CrashRedBright else StickAccent).copy(alpha = fade)

    // Rear limbs first: still readable, but the front side clearly owns the silhouette.
    drawStickLimb(pose.hip, pose.kneeBack, pose.footBack, back, scale, 4.0f)
    drawStickFoot(pose.footBack, pose.ground, pose.up, back, scale)
    drawStickLimb(pose.chest, pose.elbowBack, pose.handBack, back, scale, 3.4f)

    // Spine has two widths: a dark under-stroke keeps it crisp over the neon curve.
    drawLine(Color.Black.copy(alpha = .52f * fade), pose.hip, pose.chest, 7.0f * scale, cap = StrokeCap.Round)
    drawLine(front, pose.hip, pose.chest, 4.5f * scale, cap = StrokeCap.Round)
    drawLine(front, pose.chest, pose.neck, 3.8f * scale, cap = StrokeCap.Round)

    // Front limbs.
    drawStickLimb(pose.hip, pose.kneeFront, pose.footFront, front, scale, 4.4f)
    drawStickFoot(pose.footFront, pose.ground, pose.up, accent, scale)
    drawStickLimb(pose.chest, pose.elbowFront, pose.handFront, front, scale, 3.7f)

    // Subtle joints make the motion easy to read without turning the character into a puppet.
    drawCircle(joint, 2.1f * scale, pose.kneeFront)
    drawCircle(back.copy(alpha = .70f), 1.8f * scale, pose.kneeBack)
    drawCircle(joint, 1.75f * scale, pose.elbowFront)
    drawCircle(back.copy(alpha = .65f), 1.55f * scale, pose.elbowBack)

    // Classic faceless stickman head: dark center, clean luminous outline, zero facial detail.
    drawCircle(Color.Black.copy(alpha = .64f * fade), 9.2f * scale, pose.head)
    drawCircle(StickHeadFill.copy(alpha = fade), 7.6f * scale, pose.head)
    drawCircle(front, 7.6f * scale, pose.head, style = Stroke(width = 2.5f * scale))

    // Tiny shoulder and hip accents visually lock the skeleton together during fast motion.
    drawCircle(accent.copy(alpha = .66f), 2.2f * scale, pose.chest)
    drawCircle(accent.copy(alpha = .42f), 1.9f * scale, pose.hip)
}

private fun DrawScope.drawStickLimb(
    start: Offset,
    joint: Offset,
    end: Offset,
    color: Color,
    scale: Float,
    width: Float
) {
    val under = Color.Black.copy(alpha = .42f * color.alpha)
    drawLine(under, start, joint, (width + 2.2f) * scale, cap = StrokeCap.Round)
    drawLine(under, joint, end, (width + 1.8f) * scale, cap = StrokeCap.Round)
    drawLine(color, start, joint, width * scale, cap = StrokeCap.Round)
    drawLine(color, joint, end, (width - .45f) * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawStickFoot(
    foot: Offset,
    ground: Offset,
    up: Offset,
    color: Color,
    scale: Float
) {
    val heel = foot - ground * (2.5f * scale) + up * (.7f * scale)
    val toe = foot + ground * (7.2f * scale)
    drawLine(Color.Black.copy(alpha = .46f * color.alpha), heel, toe, 5.7f * scale, cap = StrokeCap.Round)
    drawLine(color, heel, toe, 3.1f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawFootSparks(pose: RunnerPose, cycle: Float, scale: Float) {
    val contact = if (pose.frontSupport) pose.footFront else pose.footBack
    val pulse = (.55f + abs(cos(cycle)) * .45f).coerceIn(0f, 1f)
    repeat(4) { index ->
        val back = pose.ground * ((4f + index * 4.4f) * scale)
        val lift = pose.up * ((index % 2 + 1) * 1.8f * scale)
        drawCircle(
            CrashLiveBright.copy(alpha = (.23f - index * .038f) * pulse),
            radius = (1.15f + index * .18f) * scale,
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
