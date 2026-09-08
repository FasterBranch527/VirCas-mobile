package com.vircas.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.CrashFlight
import com.vircas.mobile.core.game.CrashFlightMath
import com.vircas.mobile.core.game.CrashTrajectory
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal val CrashMint = Color(0xFF6AF4CF)
internal val CrashCoral = Color(0xFFFF667C)
internal val CrashPanel = Color(0xFF121B2C)
internal val CrashMuted = Color(0xFF93A3BA)
internal val CrashInk = Color(0xFF07111C)

// Kept as the original helper for source compatibility; growth and the 1000x cap are unchanged.
internal fun crashDisplayMultiplier(seconds: Float): Double = CrashFlightMath.multiplier(seconds.toDouble())

@Composable
internal fun CrashRocketScene(
    state: CrashUiState,
    frameTime: State<Long>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    val artwork = remember { RocketArtwork() }
    val trace = remember { Path() }
    val fill = remember { Path() }
    val flame = remember { Path() }
    val stars = remember {
        List(46) { index ->
            Star(((index * 73 + 19) % 997) / 997f, ((index * 127 + 61) % 991) / 991f, .55f + index % 3 * .25f, .12f + index % 4 * .04f)
        }
    }
    BoxWithConstraints(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF111D32), Color(0xFF090F1F))))
            .border(1.dp, Color.White.copy(alpha = .07f), shape)
            .testTag("crash-rocket-scene")
            .semantics { contentDescription = "Rocket following the Crash flight curve" }
    ) {
        val compact = maxHeight < 290.dp
        Canvas(Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            // Read the high-frequency clock in the draw phase, NOT in the screen composition.
            val now = frameTime.value
            val flight = state.flight
            val seconds = flight?.visibleSeconds(now) ?: 0.0
            val crashed = flight?.hasCrashed(now) == true
            val flying = flight != null && !crashed
            val burst = flight?.burstProgress(now) ?: 0f
            val accent = if (crashed) CrashCoral else CrashMint
            val p = CrashFlightMath.travel(seconds)
            val normalized = CrashTrajectory.point(p)
            val prefix = CrashTrajectory.prefixControl(p)
            val start = Offset((CrashTrajectory.start.x * size.width).toFloat(), (CrashTrajectory.start.y * size.height).toFloat())
            val endpoint = Offset((normalized.x * size.width).toFloat(), (normalized.y * size.height).toFloat())
            val control = Offset((prefix.x * size.width).toFloat(), (prefix.y * size.height).toFloat())
            val angle = (CrashTrajectory.headingRadians(p, size.width.toDouble(), size.height.toDouble()) * 180.0 / PI).toFloat()

            drawSpace(stars)
            drawFlightGrid(start.y)
            trace.reset()
            trace.moveTo(start.x, start.y)
            trace.quadraticBezierTo(control.x, control.y, endpoint.x, endpoint.y)
            fill.reset()
            fill.moveTo(start.x, start.y)
            fill.quadraticBezierTo(control.x, control.y, endpoint.x, endpoint.y)
            fill.lineTo(endpoint.x, start.y)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .14f), accent.copy(alpha = .012f)), 0f, start.y))
            drawPath(trace, accent.copy(alpha = .045f), style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
            drawPath(trace, accent.copy(alpha = .12f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawPath(trace, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent.copy(alpha = .28f), 4.dp.toPx(), start)
            drawCircle(accent, 1.7.dp.toPx(), start)

            val rocketScale = density * (size.width / density / 360f).coerceIn(.72f, 1.08f)
            val rocketAlpha = if (crashed) (1f - burst * 2.8f).coerceAtLeast(0f) else 1f
            if (rocketAlpha > 0f) {
                withTransform({
                    translate(endpoint.x, endpoint.y)
                    rotate(angle, Offset.Zero)
                    scale(rocketScale, rocketScale, Offset.Zero)
                }) {
                    drawRocket(artwork, flame, seconds, flying, reducedMotion, rocketAlpha)
                }
            }
            if (crashed) {
                if (!reducedMotion && burst < 1f) drawRocketBurst(endpoint, burst)
                drawCircle(CrashCoral.copy(alpha = .18f), 7.dp.toPx(), endpoint)
                drawCircle(CrashCoral, 2.8.dp.toPx(), endpoint)
            }
        }

        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val badge = when (state.phase) {
                CrashPhase.READY -> "READY TO FLY"
                CrashPhase.PREPARING -> "PREPARING"
                CrashPhase.FLYING -> "● LIVE FLIGHT"
                CrashPhase.CRASHED -> "SIGNAL LOST"
            }
            Text(badge, color = if (state.phase == CrashPhase.CRASHED) CrashCoral else CrashMint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("VIRTUAL COINS", color = CrashMuted.copy(alpha = .75f), fontSize = 8.sp, letterSpacing = 1.sp)
        }
        CrashMultiplierReadout(
            state = state, frameTime = frameTime,
            fontSize = minOf(if (compact) 42f else 56f, maxWidth.value * .14f).sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, end = 18.dp, top = if (compact) 40.dp else 52.dp)
        )
        FlightTimeReadout(state.flight, frameTime, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp))
    }
}

@Composable
private fun CrashMultiplierReadout(state: CrashUiState, frameTime: State<Long>, fontSize: TextUnit, modifier: Modifier) {
    val value by remember(state.flight, frameTime) {
        derivedStateOf {
            val raw = state.flight?.visibleMultiplier(frameTime.value) ?: 1.0
            // Do not round a live 1.999x up to a displayed 2.00x before that time exists.
            floor(raw * 100.0 + 1e-8) / 100.0
        }
    }
    val accent = if (state.phase == CrashPhase.CRASHED) CrashCoral else CrashMint
    // Reserve the right side for the rocket, including long 1000.00x flights on narrow phones.
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            crashMultiplierText(value), color = accent,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (-2).sp, maxLines = 1,
            modifier = Modifier.testTag("crash-multiplier")
        )
        Text(
            when {
                state.settling && state.pendingCashout != null -> "CONFIRMING CASHOUT…"
                state.collectedAt != null -> "COLLECTED AT ${crashMultiplierText(state.collectedAt)}"
                state.phase == CrashPhase.CRASHED -> "FLIGHT ENDED"
                state.phase == CrashPhase.FLYING -> "FOLLOW THE FLIGHT"
                else -> "YOUR NEXT FLIGHT STARTS HERE"
            },
            color = if (state.collectedAt != null) CrashMint else CrashMuted,
            fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = .6.sp
        )
    }
}

@Composable
private fun FlightTimeReadout(flight: CrashFlight?, frameTime: State<Long>, modifier: Modifier) {
    val tenths by remember(flight, frameTime) { derivedStateOf { ((flight?.visibleSeconds(frameTime.value) ?: 0.0) * 10.0).toInt() } }
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0s  /  FLIGHT TIMELINE", color = CrashMuted.copy(alpha = .55f), fontSize = 8.sp, letterSpacing = .8.sp)
        Text(String.format(Locale.US, "%.1fs", tenths / 10.0), color = CrashMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

private fun DrawScope.drawSpace(stars: List<Star>) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF283A68).copy(alpha = .22f), Color.Transparent), Offset(size.width * .82f, size.height * .18f), size.width * .63f),
        size.width * .63f, Offset(size.width * .82f, size.height * .18f)
    )
    stars.forEach { star -> drawCircle(Color.White.copy(alpha = star.alpha), star.radius.dp.toPx(), Offset(size.width * star.x, size.height * star.y)) }
    drawCircle(Color(0xFF647BBA).copy(alpha = .035f), size.width * .32f, Offset(size.width * 1.08f, size.height * .13f), style = Stroke(1.dp.toPx()))
    drawCircle(Color(0xFF647BBA).copy(alpha = .035f), size.width * .38f, Offset(size.width * 1.08f, size.height * .13f), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawFlightGrid(baseline: Float) {
    val grid = Color(0xFFB0BED5).copy(alpha = .055f)
    for (index in 1..5) {
        val x = size.width * index / 6f
        drawLine(grid, Offset(x, size.height * .30f), Offset(x, baseline), .7.dp.toPx())
    }
    for (index in 0..3) {
        val y = baseline - size.height * .14f * index
        drawLine(grid, Offset(size.width * .06f, y), Offset(size.width * .95f, y), .7.dp.toPx())
    }
}

/** Cached vector silhouette. Its engine nozzle is (0, 0), exactly the endpoint of the graph. */
private class RocketArtwork {
    val body = Path().apply {
        moveTo(2f, -6f)
        cubicTo(16f, -11f, 33f, -11f, 46f, 0f)
        cubicTo(33f, 11f, 16f, 11f, 2f, 6f)
        close()
    }
    val upperFin = Path().apply {
        moveTo(5f, -6f); lineTo(13f, -7f); lineTo(7f, -16f); lineTo(-3f, -17f); lineTo(-1f, -8f); close()
    }
    val lowerFin = Path().apply {
        moveTo(5f, 6f); lineTo(13f, 7f); lineTo(7f, 16f); lineTo(-3f, 17f); lineTo(-1f, 8f); close()
    }
    val bodyBrush = Brush.linearGradient(listOf(Color(0xFFF4F8FF), Color(0xFFD9E4F5), Color(0xFF8299BA)), Offset(16f, -9f), Offset(20f, 10f))
}

private fun DrawScope.drawRocket(art: RocketArtwork, flame: Path, seconds: Double, flying: Boolean, reducedMotion: Boolean, alpha: Float) {
    if (flying) {
        val pulse = if (reducedMotion) 0f else sin(seconds * 15.0).toFloat() * 2f + sin(seconds * 23.0).toFloat()
        val length = 27f + pulse
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFBA69).copy(alpha = .18f * alpha), Color.Transparent), Offset(-8f, 0f), 25f), 25f, Offset(-8f, 0f))
        flame.reset()
        flame.moveTo(1f, -4.5f)
        flame.cubicTo(-8f, -7f, -length * .75f, -3f, -length, 0f)
        flame.cubicTo(-length * .75f, 3f, -8f, 7f, 1f, 4.5f)
        flame.close()
        drawPath(flame, Brush.horizontalGradient(listOf(Color(0xFFFF6B55).copy(alpha = .12f * alpha), Color(0xFFFFAC5E).copy(alpha = alpha), Color(0xFFFFF0BF).copy(alpha = alpha)), -length, 1f))
        drawLine(Color(0xFFFFF5D2).copy(alpha = .9f * alpha), Offset(-13f, 0f), Offset(1f, 0f), 3f, StrokeCap.Round)
        if (!reducedMotion) {
            repeat(6) { index ->
                val age = ((seconds * .85 + index / 6.0) % 1.0).toFloat()
                val fade = sin(age * PI).toFloat().coerceAtLeast(0f) * .4f * alpha
                drawCircle(Color(0xFFFFC886).copy(alpha = fade), (1f - age) * 1.3f + .3f, Offset(-9f - age * 49f, sin(index * 2.4).toFloat() * age * 7f))
            }
        }
    }
    drawPath(art.upperFin, Color(0xFF7779BD).copy(alpha = alpha))
    drawPath(art.lowerFin, Color(0xFFAEA2F8).copy(alpha = alpha))
    drawRoundRect(Color(0xFF52627E).copy(alpha = alpha), Offset(-2f, -4.5f), Size(7f, 9f), CornerRadius(2f))
    drawPath(art.body, art.bodyBrush, alpha = alpha)
    drawPath(art.body, Color.White.copy(alpha = .52f * alpha), style = Stroke(.7f))
    drawLine(Color.White.copy(alpha = .75f * alpha), Offset(9f, -4f), Offset(21f, -5.5f), 1f, StrokeCap.Round)
    drawCircle(Color(0xFF657BA0).copy(alpha = alpha), 6.2f, Offset(28f, 0f))
    drawCircle(Color(0xFF172E4A).copy(alpha = alpha), 4.7f, Offset(28f, 0f))
    drawCircle(Color(0xFF6DE3F0).copy(alpha = alpha), 3.5f, Offset(28f, 0f))
    drawCircle(Color.White.copy(alpha = .82f * alpha), 1.15f, Offset(29f, -1.3f))
    drawLine(Color(0xFF8BA0C0).copy(alpha = .65f * alpha), Offset(8f, -5f), Offset(8f, 5f), .8f)
}

private fun DrawScope.drawRocketBurst(point: Offset, progress: Float) {
    val fade = (1f - progress) * (1f - progress)
    val expansion = 1f - (1f - progress) * (1f - progress)
    val radius = (5f + expansion * 49f).dp.toPx()
    drawCircle(Brush.radialGradient(listOf(CrashCoral.copy(alpha = .22f * fade), Color.Transparent), point, radius), radius, point)
    drawCircle(CrashCoral.copy(alpha = .5f * fade), radius * .8f, point, style = Stroke(1.4.dp.toPx()))
    repeat(12) { index ->
        val angle = index * PI * 2.0 / 12.0
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val distance = radius * (.66f + (index % 3) * .14f)
        val tail = point + direction * distance
        drawLine(Color(0xFFFFCAB1).copy(alpha = .85f * fade), tail, tail + direction * (3f + 6f * (1f - progress)).dp.toPx(), 1.5.dp.toPx(), StrokeCap.Round)
    }
}
