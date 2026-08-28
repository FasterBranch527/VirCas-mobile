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
private val RunnerSkin = Color(0xFFF0C8A6)
private val RunnerSkinShade = Color(0xFFC89578)
private val RunnerJersey = Color(0xFFF1F7F4)
private val RunnerJerseyShade = Color(0xFF9FC7B8)
private val RunnerShorts = Color(0xFF111A19)
private val RunnerHair = Color(0xFF171D1C)
private val RunnerShoe = Color(0xFF7DFFD0)
private val RunnerOutline = Color(0xFF030806)

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

    // Small local runner glow only. No giant bubble around the graph endpoint.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = .075f), accent.copy(alpha = .018f), Color.Transparent),
            center = geometry.end,
            radius = size.minDimension * .075f
        ),
        radius = size.minDimension * .075f,
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

    // The curve terminates at the runner's stride. Keep this tiny so it reads as contact, not a target marker.
    drawCircle(accent.copy(alpha = .20f), radius = 8f, center = geometry.end)
    drawCircle(hot, radius = 2.7f, center = geometry.end)
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
            accent.copy(alpha = (1f - phase) * .22f),
            radius = 1.1f + (1f - phase) * 1.8f,
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
    val shoulder: Offset,
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
    val scale = (size.minDimension / 420f).coerceIn(1.35f, 2.15f)
    val p = crashProgress.coerceIn(0f, 1f)
    val baseAngle = tangentDegrees * (PI / 180.0).toFloat()
    val baseGround = normalize(Offset(cos(baseAngle), sin(baseAngle)))
    val baseSlopeUp = normalize(Offset(baseGround.y, -baseGround.x))

    val fallPoint = if (crashed) {
        point + Offset(68f * scale * p, 118f * scale * p.pow(1.55f))
    } else {
        point
    }
    val tumbleRadians = if (crashed) (p * 300f) * (PI / 180.0).toFloat() else 0f
    val ground = rotateVector(baseGround, tumbleRadians)
    val slopeUp = rotateVector(baseSlopeUp, tumbleRadians)

    val speed = if (running && !crashed) (9.4f + min(4.5f, elapsedSeconds * .42f)) else 0f
    val cycle = if (running && !crashed) elapsedSeconds * speed else elapsedSeconds * 9.4f
    val stride = sin(cycle)
    val bodyUpBase = normalize(
        Offset(
            baseSlopeUp.x * .18f,
            -0.82f + baseSlopeUp.y * .18f
        )
    )
    val bodyUp = rotateVector(bodyUpBase, tumbleRadians)
    val bodyRight = normalize(Offset(-bodyUp.y, bodyUp.x))
    val maxForward = abs(stride) * 18f * scale
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
    val fade = if (crashed) 1f - p * .48f else 1f

    if (running && !crashed) {
        // Two faint body echoes, close to the runner instead of a long blur trail.
        repeat(2) { ghost ->
            val shift = ground * ((11f + ghost * 9f) * scale)
            drawRunnerGhost(pose, shift, scale, .045f - ghost * .014f)
        }
    }

    drawCircle(
        brush = Brush.radialGradient(
            listOf(CrashLive.copy(alpha = .11f * fade), Color.Transparent),
            center = pose.hip,
            radius = 34f * scale
        ),
        radius = 34f * scale,
        center = pose.hip
    )

    // Shadow follows the actual local slope under the athlete.
    val shadowCenter = anchor - slopeUp * (1.5f * scale)
    val shadowLeft = shadowCenter - ground * (17f * scale)
    val shadowRight = shadowCenter + ground * (17f * scale)
    drawLine(Color.Black.copy(alpha = .36f * fade), shadowLeft, shadowRight, 6f * scale, cap = StrokeCap.Round)
    drawLine(CrashLive.copy(alpha = .10f * fade), shadowLeft, shadowRight, 2.2f * scale, cap = StrokeCap.Round)

    drawDetailedRunner(pose, scale, fade, crashed)

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
    val stride = if (running) sin(cycle) else .28f
    val lift = if (running) cos(cycle) else -.2f
    val liftFront = max(0f, -lift)
    val liftBack = max(0f, lift)
    val bob = if (running) abs(cos(cycle * 2f)) * 1.6f * scale else 0f

    val footFront = anchor + ground * (stride * 18f * scale) + slopeUp * (liftFront * 10f * scale)
    val footBack = anchor - ground * (stride * 18f * scale) + slopeUp * (liftBack * 10f * scale)
    val hip = anchor + bodyUp * (40f * scale) + slopeUp * bob
    val shoulder = hip + bodyUp * (29f * scale) + ground * (6.5f * scale)
    val head = shoulder + bodyUp * (16.5f * scale) + ground * (3.5f * scale)

    val kneeFront = lerpOffset(hip, footFront, .53f) + ground * ((5f + 5f * max(0f, stride)) * scale) + slopeUp * (4f * scale)
    val kneeBack = lerpOffset(hip, footBack, .53f) + ground * ((4f - 5f * min(0f, stride)) * scale) + slopeUp * (3f * scale)

    val armSwing = -stride
    val elbowFront = shoulder - bodyUp * (10f * scale) + ground * (armSwing * 13f * scale)
    val handFront = elbowFront - bodyUp * (10f * scale) + ground * (armSwing * 8f * scale)
    val elbowBack = shoulder - bodyUp * (10f * scale) - ground * (armSwing * 12f * scale)
    val handBack = elbowBack - bodyUp * (9f * scale) - ground * (armSwing * 8f * scale)

    return RunnerPose(
        ground = ground,
        up = slopeUp,
        bodyUp = bodyUp,
        bodyRight = bodyRight,
        hip = hip,
        shoulder = shoulder,
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

private fun DrawScope.drawRunnerGhost(pose: RunnerPose, shift: Offset, scale: Float, alpha: Float) {
    val c = CrashLive.copy(alpha = alpha)
    fun shifted(p: Offset) = p - shift
    drawLine(c, shifted(pose.hip), shifted(pose.kneeBack), 7f * scale, cap = StrokeCap.Round)
    drawLine(c, shifted(pose.kneeBack), shifted(pose.footBack), 5f * scale, cap = StrokeCap.Round)
    drawLine(c, shifted(pose.hip), shifted(pose.kneeFront), 7f * scale, cap = StrokeCap.Round)
    drawLine(c, shifted(pose.kneeFront), shifted(pose.footFront), 5f * scale, cap = StrokeCap.Round)
    drawLine(c, shifted(pose.hip), shifted(pose.shoulder), 10f * scale, cap = StrokeCap.Round)
    drawCircle(c, 6.8f * scale, shifted(pose.head))
}

private fun DrawScope.drawDetailedRunner(pose: RunnerPose, scale: Float, fade: Float, crashed: Boolean) {
    val outline = RunnerOutline.copy(alpha = .95f * fade)
    val skin = RunnerSkin.copy(alpha = fade)
    val skinShade = RunnerSkinShade.copy(alpha = fade)
    val jersey = RunnerJersey.copy(alpha = fade)
    val jerseyShade = RunnerJerseyShade.copy(alpha = fade)
    val shorts = RunnerShorts.copy(alpha = fade)
    val shoe = (if (crashed) CrashRedBright else RunnerShoe).copy(alpha = fade)

    // Back leg and arm are darker so the side-view pose has depth.
    drawLeg(pose, pose.kneeBack, pose.footBack, scale, outline, shorts, skinShade, .72f)
    drawShoe(pose.footBack, pose.ground, scale, shoe.copy(alpha = .72f), outline.copy(alpha = .72f))
    drawArm(
        shoulder = pose.shoulder - pose.bodyRight * (1.5f * scale),
        elbow = pose.elbowBack,
        hand = pose.handBack,
        scale = scale,
        outline = outline.copy(alpha = .72f),
        sleeve = jerseyShade.copy(alpha = .72f),
        skin = skinShade.copy(alpha = .72f)
    )

    // Tapered torso, mostly upright even on a steep graph. It only leans into the climb.
    val shoulderLeft = pose.shoulder - pose.bodyRight * (7.4f * scale)
    val shoulderRight = pose.shoulder + pose.bodyRight * (7.4f * scale)
    val hipLeft = pose.hip - pose.bodyRight * (5.1f * scale)
    val hipRight = pose.hip + pose.bodyRight * (5.1f * scale)
    val torso = Path().apply {
        moveTo(shoulderLeft.x, shoulderLeft.y)
        lineTo(shoulderRight.x, shoulderRight.y)
        lineTo(hipRight.x, hipRight.y)
        lineTo(hipLeft.x, hipLeft.y)
        close()
    }
    drawPath(torso, outline)

    val inset = 1.5f * scale
    val torsoInner = Path().apply {
        val a = shoulderLeft + pose.bodyRight * inset - pose.bodyUp * (.4f * scale)
        val b = shoulderRight - pose.bodyRight * inset
        val c = hipRight - pose.bodyRight * inset
        val d = hipLeft + pose.bodyRight * inset
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(
        torsoInner,
        brush = Brush.linearGradient(
            listOf(jersey, jerseyShade),
            start = shoulderLeft,
            end = hipRight
        )
    )

    val pelvis = Path().apply {
        val topL = pose.hip - pose.bodyRight * (5.8f * scale)
        val topR = pose.hip + pose.bodyRight * (5.8f * scale)
        val lowR = pose.hip - pose.bodyUp * (7.0f * scale) + pose.bodyRight * (5.0f * scale)
        val lowL = pose.hip - pose.bodyUp * (7.0f * scale) - pose.bodyRight * (5.0f * scale)
        moveTo(topL.x, topL.y)
        lineTo(topR.x, topR.y)
        lineTo(lowR.x, lowR.y)
        lineTo(lowL.x, lowL.y)
        close()
    }
    drawPath(pelvis, outline)
    val pelvisInner = Path().apply {
        val topL = pose.hip - pose.bodyRight * (4.4f * scale)
        val topR = pose.hip + pose.bodyRight * (4.4f * scale)
        val lowR = pose.hip - pose.bodyUp * (5.6f * scale) + pose.bodyRight * (3.7f * scale)
        val lowL = pose.hip - pose.bodyUp * (5.6f * scale) - pose.bodyRight * (3.7f * scale)
        moveTo(topL.x, topL.y)
        lineTo(topR.x, topR.y)
        lineTo(lowR.x, lowR.y)
        lineTo(lowL.x, lowL.y)
        close()
    }
    drawPath(pelvisInner, shorts)

    drawLeg(pose, pose.kneeFront, pose.footFront, scale, outline, shorts, skin, 1f)
    drawShoe(pose.footFront, pose.ground, scale, shoe, outline)

    drawArm(
        shoulder = pose.shoulder + pose.bodyRight * (1.8f * scale),
        elbow = pose.elbowFront,
        hand = pose.handFront,
        scale = scale,
        outline = outline,
        sleeve = jersey,
        skin = skin
    )

    // Neck and head.
    val neckBase = pose.shoulder + pose.bodyUp * (3.5f * scale) + pose.ground * (2f * scale)
    val neckTop = pose.head - pose.bodyUp * (7.8f * scale)
    drawLine(outline, neckBase, neckTop, 6.4f * scale, cap = StrokeCap.Round)
    drawLine(skin, neckBase, neckTop, 4.1f * scale, cap = StrokeCap.Round)
    drawCircle(outline, 8.6f * scale, pose.head)
    drawCircle(skin, 7.0f * scale, pose.head)

    val hairCenter = pose.head + pose.bodyUp * (2.6f * scale) - pose.ground * (1.5f * scale)
    drawCircle(RunnerHair.copy(alpha = fade), 6.3f * scale, hairCenter)
    drawCircle(skin, 5.7f * scale, pose.head + pose.ground * (1.7f * scale) - pose.bodyUp * (.6f * scale))
    drawCircle(Color.White.copy(alpha = .55f * fade), 1.0f * scale, pose.head + pose.ground * (4.0f * scale) + pose.bodyUp * (.4f * scale))

    // Neon stripe and bib make the athlete readable at a glance.
    drawLine(
        color = (if (crashed) CrashRedBright else CrashLiveBright).copy(alpha = .85f * fade),
        start = pose.shoulder - pose.bodyRight * (4.8f * scale),
        end = pose.hip - pose.bodyRight * (3.4f * scale),
        strokeWidth = 1.7f * scale,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = CrashLiveDeep.copy(alpha = .90f * fade),
        radius = 2.6f * scale,
        center = lerpOffset(pose.shoulder, pose.hip, .40f)
    )
}

private fun DrawScope.drawLeg(
    pose: RunnerPose,
    knee: Offset,
    foot: Offset,
    scale: Float,
    outline: Color,
    shorts: Color,
    skin: Color,
    depth: Float
) {
    val upperEnd = lerpOffset(pose.hip, knee, .40f)
    val o = outline.copy(alpha = outline.alpha * depth)
    val s = shorts.copy(alpha = shorts.alpha * depth)
    val k = skin.copy(alpha = skin.alpha * depth)

    drawLine(o, pose.hip, upperEnd, 10.5f * scale, cap = StrokeCap.Round)
    drawLine(s, pose.hip, upperEnd, 7.0f * scale, cap = StrokeCap.Round)
    drawLine(o, upperEnd, knee, 8.2f * scale, cap = StrokeCap.Round)
    drawLine(k, upperEnd, knee, 5.2f * scale, cap = StrokeCap.Round)
    drawCircle(o, 4.1f * scale, knee)
    drawCircle(k, 2.8f * scale, knee)
    drawLine(o, knee, foot - pose.up * (2.2f * scale), 7.4f * scale, cap = StrokeCap.Round)
    drawLine(k, knee, foot - pose.up * (2.2f * scale), 4.5f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawArm(
    shoulder: Offset,
    elbow: Offset,
    hand: Offset,
    scale: Float,
    outline: Color,
    sleeve: Color,
    skin: Color
) {
    val sleeveEnd = lerpOffset(shoulder, elbow, .46f)
    drawLine(outline, shoulder, sleeveEnd, 8.0f * scale, cap = StrokeCap.Round)
    drawLine(sleeve, shoulder, sleeveEnd, 5.3f * scale, cap = StrokeCap.Round)
    drawLine(outline, sleeveEnd, elbow, 6.5f * scale, cap = StrokeCap.Round)
    drawLine(skin, sleeveEnd, elbow, 4.2f * scale, cap = StrokeCap.Round)
    drawLine(outline, elbow, hand, 6.0f * scale, cap = StrokeCap.Round)
    drawLine(skin, elbow, hand, 3.9f * scale, cap = StrokeCap.Round)
    drawCircle(skin, 2.7f * scale, hand)
}

private fun DrawScope.drawShoe(
    foot: Offset,
    ground: Offset,
    scale: Float,
    color: Color,
    outline: Color
) {
    val heel = foot - ground * (3.5f * scale)
    val toe = foot + ground * (9.5f * scale)
    drawLine(outline, heel, toe, 6.2f * scale, cap = StrokeCap.Round)
    drawLine(color, heel + Offset(0f, -0.5f * scale), toe, 3.5f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.drawFootSparks(pose: RunnerPose, cycle: Float, scale: Float) {
    val c = cos(cycle)
    val contact = if (c >= 0f) pose.footFront else pose.footBack
    val intensity = abs(c).coerceIn(0f, 1f)
    repeat(4) { index ->
        val back = pose.ground * ((4f + index * 4.5f) * scale)
        val lift = pose.up * ((index % 2 + 1) * 2.2f * scale)
        drawCircle(
            CrashLiveBright.copy(alpha = (.28f - index * .045f) * intensity),
            radius = (1.5f + index * .25f) * scale,
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
