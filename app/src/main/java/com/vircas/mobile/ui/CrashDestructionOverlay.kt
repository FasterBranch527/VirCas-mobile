package com.vircas.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.core.game.CrashBlast
import com.vircas.mobile.core.game.CrashFlightMath
import com.vircas.mobile.core.game.CrashTrajectory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private val BlastInk = Color(0xFF070A10)
private val BlastFire = Color(0xFFFF8E3C)

/** A sibling of the ENTIRE game UI: not clipped to the chart or its rounded card. */
@Composable
internal fun CrashDestructionOverlay(
    state: CrashUiState,
    frameTime: State<Long>,
    sceneBounds: State<Rect?>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val flight = state.blastFlight ?: return
    val windowOrigin = remember { mutableStateOf(Offset.Zero) }
    val artwork = remember { RocketArtwork() }
    val waveEdge = remember { Path() }
    val anchor = remember(flight) { BlastAnchor() }
    val fragments = remember {
        listOf(
            ShellFragment(Rect(-6f, -19f, 18f, 0f), Offset(-56f, -108f), -190f),
            ShellFragment(Rect(-6f, 0f, 18f, 19f), Offset(-82f, 102f), 150f),
            ShellFragment(Rect(18f, -14f, 31f, 14f), Offset(66f, -88f), 230f),
            ShellFragment(Rect(31f, -14f, 49f, 14f), Offset(151f, 41f), -135f)
        )
    }
    Box(
        modifier.onGloballyPositioned { windowOrigin.value = it.positionInWindow() }
            .clipToBounds()
            .graphicsLayer { alpha = CrashBlast.overlayAlpha(state.revealStartedAtNanos, frameTime.value) }
            .pointerInput(flight) {
                // Suppress tap-through and scrolling while the old screen is being erased.
                // The system BackHandler and the app's save-error dialog remain available.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .testTag("crash-fullscreen-explosion")
            .semantics {
                contentDescription = "Rocket exploded. Clearing the flight scene, not the wallet."
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bounds = sceneBounds.value ?: return@Canvas
            if (bounds.width <= 0f || bounds.height <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas
            val now = frameTime.value
            val age = CrashBlast.age(flight, now)
            val rocketScale = density * (bounds.width / density / 360f).coerceIn(.72f, 1.08f)
            val core = CrashBlast.bodyCenter(flight, bounds.width.toDouble(), bounds.height.toDouble(), rocketScale.toDouble())
            val measuredOrigin = bounds.topLeft - windowOrigin.value + Offset(core.x.toFloat(), core.y.toFloat())
            val origin = anchor.resolve(measuredOrigin, size, bounds.size)
            val heading = CrashTrajectory.headingRadians(CrashFlightMath.travel(flight.durationSeconds), bounds.width.toDouble(), bounds.height.toDouble())
            val coverage = CrashBlast.coveringRadius(origin.x.toDouble(), origin.y.toDouble(), size.width.toDouble(), size.height.toDouble()).toFloat()

            if (reducedMotion) {
                // No hot flash, debris or pressure ripples; keep the same safe reset timing.
                drawRect(BlastInk.copy(alpha = CrashBlast.smooth(age / .55).toFloat()))
            } else {
                drawErasingWave(origin, coverage, age, waveEdge)
                if (age < 1.25) {
                    drawFireAndSmoke(origin, age)
                    drawShellFragments(artwork, fragments, origin, heading, rocketScale, age)
                    drawBlastEmbers(origin, age)
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center).graphicsLayer {
                alpha = CrashBlast.smooth((CrashBlast.age(flight, frameTime.value) - .55) / .42).toFloat()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("SIGNAL LOST", color = Color(0xFFF1E7DC), fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(crashMultiplierText(flight.crashPoint), color = Color(0xFFA9998B), fontSize = 17.sp, fontFamily = FontFamily.Monospace)
            Text(if (state.settling) "SAVING ROUND…" else "CLEARING FLIGHT PATH", color = Color(0xFF746F6B), fontSize = 8.sp, letterSpacing = 1.8.sp)
        }
    }
}

private fun DrawScope.drawErasingWave(origin: Offset, coverage: Float, age: Double, edge: Path) {
    val radius = coverage * CrashBlast.waveProgress(age).toFloat()
    if (radius < .01f) return
    val heat = (1.0 - CrashBlast.smooth((age - .65) / .9)).toFloat()
    // An opaque burned area follows the hot rim. Its inner radius covers all four
    // screen corners BEFORE the controller clears the old graph underneath it.
    drawCircle(
        Brush.radialGradient(
            0f to BlastInk, CrashBlast.OPAQUE_RADIUS_FRACTION.toFloat() to BlastInk,
            .91f to Color(0xFF231711), .957f to Color(0xFF873B20).copy(alpha = .9f),
            .985f to BlastFire.copy(alpha = .80f * heat), 1f to Color.Transparent,
            center = origin, radius = radius
        ), radius, origin
    )
    if (age >= CrashBlast.COVER_SECONDS) return
    edge.reset()
    repeat(81) { index ->
        val angle = index * PI * 2.0 / 80.0
        val turbulence = sin(angle * 7.0 + age * 2.4) * .013 + sin(angle * 13.0 - age * 1.7) * .007
        val r = radius * (0.962 + turbulence).toFloat()
        val x = origin.x + cos(angle).toFloat() * r
        val y = origin.y + sin(angle).toFloat() * r
        if (index == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
    }
    edge.close()
    drawPath(edge, BlastFire.copy(alpha = .16f * heat), style = Stroke(12.dp.toPx()))
    drawPath(edge, Color(0xFFFFD6A4).copy(alpha = .56f * heat), style = Stroke(1.8.dp.toPx()))
    drawCircle(Color(0xFFE4D3BD).copy(alpha = .13f * heat), radius * 1.035f, origin, style = Stroke(.8.dp.toPx()))
}

private fun DrawScope.drawFireAndSmoke(origin: Offset, age: Double) {
    val t = age.toFloat()
    val ignition = (CrashBlast.smooth(age / .035) * (1.0 - CrashBlast.smooth((age - .08) / .22))).toFloat()
    val heat = (1.0 - CrashBlast.smooth(age / .78)).toFloat()
    val fade = (1.0 - CrashBlast.smooth((age - .35) / .9)).toFloat()
    val expansion = (1.0 - exp(-age * 4.2)).toFloat()
    val bloomRadius = (14f + expansion * 88f).dp.toPx()
    drawCircle(Brush.radialGradient(listOf(BlastFire.copy(alpha = .30f * fade), Color.Transparent), origin, bloomRadius), bloomRadius, origin)
    // Overlapping soft lobes produce an irregular fireball that cools into soot.
    repeat(9) { index ->
        val angle = index * 2.399963 + age * .16
        val spread = expansion * (13f + (index % 4) * 11f).dp.toPx()
        val center = origin + Offset(cos(angle).toFloat() * spread, sin(angle).toFloat() * spread - t * 19.dp.toPx())
        val radius = (10f + (index % 3) * 5f + expansion * 20f).dp.toPx()
        val localHeat = (heat - (index % 3) * .10f).coerceAtLeast(0f)
        drawCircle(
            Brush.radialGradient(
                0f to lerp(Color(0xFF332B25), Color(0xFFFFE3A5), localHeat).copy(alpha = .84f * fade),
                .34f to lerp(Color(0xFF2A2420), Color(0xFFFF9A3E), localHeat).copy(alpha = .78f * fade),
                .72f to Color(0xFF70351D).copy(alpha = .43f * fade),
                1f to Color.Transparent, center = center, radius = radius
            ), radius, center
        )
    }
    val coreRadius = (8f + ignition * 21f).dp.toPx()
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1D1).copy(alpha = .93f * ignition), BlastFire.copy(alpha = .35f * ignition), Color.Transparent), origin, coreRadius), coreRadius, origin)
}

private class BlastAnchor {
    private var point: Offset? = null
    private var viewport = Size.Zero
    private var chart = Size.Zero

    // Freeze the detonation in screen space even if an old scroll fling is still settling.
    // A real resize/rotation reprojects it onto the resized rocket instead of restarting it.
    fun resolve(measured: Offset, viewportSize: Size, chartSize: Size): Offset {
        if (point == null || viewport != viewportSize || chart != chartSize) {
            point = measured
            viewport = viewportSize
            chart = chartSize
        }
        return requireNotNull(point)
    }
}

private data class ShellFragment(val clip: Rect, val velocity: Offset, val spin: Float)

private fun DrawScope.drawShellFragments(art: RocketArtwork, fragments: List<ShellFragment>, origin: Offset, heading: Double, rocketScale: Float, age: Double) {
    val t = age.toFloat()
    val fade = (1.0 - CrashBlast.smooth(age / 1.0)).toFloat()
    val dragTime = ((1.0 - exp(-age * 1.5)) / 1.5).toFloat()
    val c = cos(heading).toFloat()
    val s = sin(heading).toFloat()
    fun orient(vector: Offset) = Offset(vector.x * c - vector.y * s, vector.x * s + vector.y * c)
    fragments.forEach { piece ->
        val localCenter = piece.clip.center - Offset(22f, 0f)
        val position = origin + orient(localCenter) * rocketScale + orient(piece.velocity) * (density * dragTime) + Offset(0f, 90.dp.toPx() * t * t)
        withTransform({
            translate(position.x, position.y)
            rotate((heading * 180.0 / PI).toFloat() + piece.spin * t, Offset.Zero)
            scale(rocketScale, rocketScale, Offset.Zero)
            translate(-piece.clip.center.x, -piece.clip.center.y)
        }) {
            clipRect(piece.clip.left, piece.clip.top, piece.clip.right, piece.clip.bottom) { drawRocketShell(art, fade) }
        }
    }
}

private fun DrawScope.drawBlastEmbers(origin: Offset, age: Double) {
    val t = age.toFloat()
    repeat(28) { index ->
        val lifetime = .52 + (index % 7) * .10
        val p = (age / lifetime).coerceIn(0.0, 1.0).toFloat()
        if (p >= 1f) return@repeat
        val angle = index * 2.399963
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val speed = (80f + ((index * 47) % 170)).dp.toPx()
        val travel = ((1.0 - exp(-age * 1.7)) / 1.7).toFloat() * speed
        val point = origin + direction * travel + Offset(0f, 62.dp.toPx() * t * t)
        val fade = sin(p * PI).toFloat().coerceAtLeast(0f) * (1f - p)
        val length = (2f + (1f - p) * 9f).dp.toPx()
        drawLine(Color(0xFFFFC07A).copy(alpha = .8f * fade), point - direction * length, point, (if (index % 5 == 0) 1.8f else .9f).dp.toPx(), StrokeCap.Round)
    }
}
