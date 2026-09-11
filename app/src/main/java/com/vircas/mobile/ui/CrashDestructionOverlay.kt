package com.vircas.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
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
private val BlastFire = Color(0xFFFF8538)
private val BlastWhite = Color(0xFFFFF1D2)

/** Fullscreen sibling, anchored to the fuel chamber. Never owns or resets the wager. */
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
    val jet = remember { Path() }
    val anchor = remember(flight) { BlastAnchor() }
    // Stable vectors: no frame-time Random, no access to the game's random provider.
    val particles = remember {
        List(72) { i ->
            val angle = i * 2.3999632297
            BlastParticle(
                Offset(cos(angle).toFloat(), sin(angle).toFloat()),
                130f + (i * 67 % 300), .58 + (i % 11) * .077,
                if (i % 9 == 0) 2.1f else .7f + (i % 3) * .3f
            )
        }
    }
    val fragments = remember {
        listOf(
            ShellFragment(Rect(-6f, -19f, 13f, -6f), Offset(-130f, -235f), -310f),
            ShellFragment(Rect(-6f, 6f, 13f, 19f), Offset(-150f, 175f), 260f),
            ShellFragment(Rect(-6f, -6f, 10f, 6f), Offset(-270f, 30f), -180f),
            ShellFragment(Rect(10f, -14f, 22f, 0f), Offset(-50f, -210f), 430f),
            ShellFragment(Rect(10f, 0f, 22f, 14f), Offset(-30f, 225f), -370f),
            ShellFragment(Rect(22f, -14f, 33f, 14f), Offset(125f, -155f), 285f),
            ShellFragment(Rect(33f, -14f, 49f, 14f), Offset(290f, 75f), -230f)
        )
    }
    Box(
        modifier.onGloballyPositioned { windowOrigin.value = it.positionInWindow() }
            .clipToBounds()
            .graphicsLayer { alpha = CrashBlast.overlayAlpha(state.revealStartedAtNanos, frameTime.value) }
            .pointerInput(flight) {
                // Preserve tap/scroll protection and the controller's confirmed-reset gate.
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
            if (bounds.width <= 0f || bounds.height <= 0f || size.minDimension <= 0f) return@Canvas
            val age = CrashBlast.age(flight, frameTime.value)
            val rocketScale = density * (bounds.width / density / 360f).coerceIn(.72f, 1.08f)
            val core = CrashBlast.bodyCenter(flight, bounds.width.toDouble(), bounds.height.toDouble(), rocketScale.toDouble())
            val measured = bounds.topLeft - windowOrigin.value + Offset(core.x.toFloat(), core.y.toFloat())
            val origin = anchor.resolve(measured, size, bounds.size)
            val heading = CrashTrajectory.headingRadians(CrashFlightMath.travel(flight.durationSeconds), bounds.width.toDouble(), bounds.height.toDouble())
            val coverage = CrashBlast.coveringRadius(origin.x.toDouble(), origin.y.toDouble(), size.width.toDouble(), size.height.toDouble()).toFloat()
            if (reducedMotion) {
                // One gentle fade. No shake, flash, flying shards or pressure ripples.
                drawRect(BlastInk.copy(alpha = CrashBlast.smooth(age / .55).toFloat()))
            } else {
                drawErasingWave(origin, coverage, age, waveEdge)
                if (age < 1.55) {
                    drawPressureFront(origin, age)
                    drawBlastJets(origin, heading, age, jet)
                    drawVolumetricBlast(origin, age)
                    drawShellFragments(artwork, fragments, origin, heading, rocketScale, age)
                    drawBlastParticles(particles, origin, age)
                    drawIgnition(origin, age)
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).graphicsLayer {
                alpha = CrashBlast.smooth((CrashBlast.age(flight, frameTime.value) - .82) / .42).toFloat()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SIGNAL LOST", color = BlastWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, textAlign = TextAlign.Center)
            Text(crashMultiplierText(flight.crashPoint), color = Color(0xFFFFB67D), fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(if (state.settling) "SAVING ROUND…" else "CLEARING FLIGHT PATH", color = Color(0xFFB8ADA2), fontSize = 14.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp)
        }
    }
}

private fun DrawScope.drawErasingWave(origin: Offset, coverage: Float, age: Double, edge: Path) {
    val radius = coverage * CrashBlast.waveProgress(age).toFloat()
    if (radius < .01f) return
    val heat = (1.0 - CrashBlast.smooth((age - .60) / .90)).toFloat()
    // Original opaque-radius contract: every corner is hidden before the controller resets.
    drawCircle(
        Brush.radialGradient(
            0f to BlastInk, CrashBlast.OPAQUE_RADIUS_FRACTION.toFloat() to BlastInk,
            .91f to Color(0xFF1C1210), .959f to Color(0xFF572713).copy(alpha = .42f),
            .985f to BlastFire.copy(alpha = .22f * heat), 1f to Color.Transparent,
            center = origin, radius = radius
        ), radius, origin
    )
    if (age >= CrashBlast.COVER_SECONDS) return
    edge.reset()
    repeat(97) { i ->
        val angle = i * PI * 2.0 / 96.0
        val turbulence = sin(angle * 7.0 + age * 2.4) * .014 + sin(angle * 17.0 - age * 2.0) * .006
        val local = radius * (.963 + turbulence).toFloat()
        val p = origin + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * local
        if (i == 0) edge.moveTo(p.x, p.y) else edge.lineTo(p.x, p.y)
    }
    edge.close()
    drawPath(edge, BlastFire.copy(alpha = .06f * heat), style = Stroke(18.dp.toPx()))
    drawPath(edge, BlastWhite.copy(alpha = .18f * heat), style = Stroke(1.4.dp.toPx()))
}

private fun DrawScope.drawPressureFront(origin: Offset, age: Double) {
    repeat(2) { i ->
        val t = (age - .045 - i * .095).toFloat()
        if (t <= 0f || t >= .8f) return@repeat
        val progress = t / .8f
        val radius = (22f + (1f - exp(-t * 5.5f)) * 330f).dp.toPx()
        val alpha = (1f - progress) * (1f - progress) * (if (i == 0) .18f else .08f)
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .86f to Color.Transparent, .97f to BlastFire.copy(alpha = alpha * .3f), 1f to Color.Transparent, center = origin, radius = radius), radius, origin)
        drawCircle(BlastWhite.copy(alpha = alpha), radius * .966f, origin, style = Stroke((.7f + (1f - progress) * 1.1f).dp.toPx()))
    }
}

private fun DrawScope.drawIgnition(origin: Offset, age: Double) {
    val flash = (CrashBlast.smooth(age / .025) * (1.0 - CrashBlast.smooth((age - .065) / .18))).toFloat()
    if (flash <= .001f) return
    // A single local ignition, not repeated full-screen white flashes.
    val radius = (32f + flash * 110f).dp.toPx()
    drawCircle(Brush.radialGradient(0f to BlastWhite.copy(alpha = .95f * flash), .18f to Color(0xFFFFDFA3).copy(alpha = .9f * flash), .48f to BlastFire.copy(alpha = .42f * flash), 1f to Color.Transparent, center = origin, radius = radius), radius, origin)
    drawLine(BlastWhite.copy(alpha = .8f * flash), origin - Offset(radius * .55f, 0f), origin + Offset(radius * .55f, 0f), 1.7.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawBlastJets(origin: Offset, heading: Double, age: Double, jet: Path) {
    val power = (CrashBlast.smooth(age / .025) * (1.0 - CrashBlast.smooth((age - .10) / .55))).toFloat()
    if (power <= .001f) return
    val expansion = (1.0 - exp(-age * 9.0)).toFloat()
    repeat(9) { i ->
        val angle = heading + i * 2.399963 + sin(i * 3.1) * .14
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val normal = Offset(-direction.y, direction.x)
        val reach = (88f + (i * 43 % 115)).dp.toPx() * expansion
        val width = (13f + (i % 3) * 6f).dp.toPx() * power
        val tip = origin + direction * reach
        val left = origin + normal * width * .4f
        val right = origin - normal * width * .4f
        val shoulder = origin + direction * reach * .28f
        val neck = tip - direction * reach * .23f
        jet.reset()
        jet.moveTo(left.x, left.y)
        jet.cubicTo(shoulder.x + normal.x * width, shoulder.y + normal.y * width, neck.x + normal.x * width * .23f, neck.y + normal.y * width * .23f, tip.x, tip.y)
        jet.cubicTo(neck.x - normal.x * width * .35f, neck.y - normal.y * width * .35f, shoulder.x - normal.x * width * .7f, shoulder.y - normal.y * width * .7f, right.x, right.y)
        jet.close()
        drawPath(jet, Brush.linearGradient(listOf(BlastWhite.copy(alpha = .38f * power), Color(0xFFFFD276).copy(alpha = .28f * power), BlastFire.copy(alpha = .15f * power), Color(0xFFEC4720).copy(alpha = .01f * power)), origin, tip))
    }
}

private fun DrawScope.drawVolumetricBlast(origin: Offset, age: Double) {
    val t = age.toFloat()
    val expansion = (1.0 - exp(-age * 5.0)).toFloat()
    val fade = (1.0 - CrashBlast.smooth((age - .66) / .87)).toFloat()
    val heat = (1.0 - CrashBlast.smooth((age - .10) / .84)).toFloat()
    val halo = (35f + expansion * 150f).dp.toPx()
    drawCircle(Brush.radialGradient(listOf(BlastFire.copy(alpha = .33f * fade * heat), Color(0xFFC34721).copy(alpha = .12f * fade), Color.Transparent), origin, halo), halo, origin)
    // Rear smoke, then rolling hot lobes: asymmetry, depth and cooling rather than flat circles.
    repeat(18) { i ->
        val theta = i * 2.399963 + .12 * age
        val spread = expansion * (30f + (i % 5) * 16f).dp.toPx()
        val lift = (t * t * (24f + i % 4 * 6f)).dp.toPx()
        val p = origin + Offset(cos(theta).toFloat() * spread, sin(theta).toFloat() * spread * .83f - lift)
        val radius = (13f + (i % 4) * 5f + expansion * 35f).dp.toPx()
        val smokeAlpha = CrashBlast.smooth(age / .22).toFloat() * fade
        drawCircle(Brush.radialGradient(0f to Color(0xFF3B302A).copy(alpha = .72f * smokeAlpha), .52f to Color(0xFF221E1D).copy(alpha = .67f * smokeAlpha), 1f to Color.Transparent, center = p, radius = radius * 1.25f), radius * 1.25f, p)
    }
    // Fade the colored fuel smoothly before cooling leaves visible circular lobe rims.
    val fuelFade = CrashBlast.smooth((heat.toDouble() - .06) / .24).toFloat() * fade
    if (fuelFade <= .001f) return
    repeat(15) { i ->
        val theta = i * 2.399963 - age * (.17 + (i % 3) * .06)
        val spread = expansion * (12f + (i % 5) * 13f).dp.toPx()
        val p = origin + Offset(cos(theta).toFloat() * spread, sin(theta).toFloat() * spread - t * t * 31.dp.toPx())
        val radius = (12f + (i % 3) * 7f + expansion * 25f).dp.toPx()
        val localHeat = (heat - (i % 4) * .07f).coerceAtLeast(0f)
        drawCircle(
            Brush.radialGradient(
                0f to lerp(Color(0xFF40302A), BlastWhite, localHeat).copy(alpha = .94f * fuelFade),
                .24f to lerp(Color(0xFF302420), Color(0xFFFFC25B), localHeat).copy(alpha = .90f * fuelFade),
                .52f to lerp(Color(0xFF241D1B), Color(0xFFFA6124), localHeat).copy(alpha = .83f * fuelFade),
                .76f to Color(0xFFA5331A).copy(alpha = .42f * fuelFade * heat),
                1f to Color.Transparent, center = p, radius = radius
            ), radius, p
        )
    }
}

private data class BlastParticle(val direction: Offset, val speed: Float, val lifetime: Double, val width: Float)
private data class ShellFragment(val clip: Rect, val velocity: Offset, val spin: Float)

private fun DrawScope.drawBlastParticles(particles: List<BlastParticle>, origin: Offset, age: Double) {
    val t = age.toFloat()
    val drag = ((1.0 - exp(-age * 2.2)) / 2.2).toFloat()
    particles.forEachIndexed { i, particle ->
        val life = (age / particle.lifetime).coerceIn(0.0, 1.0).toFloat()
        if (life >= 1f) return@forEachIndexed
        val point = origin + particle.direction * (particle.speed.dp.toPx() * drag) + Offset(0f, (76f * t * t).dp.toPx())
        val fade = CrashBlast.smooth(age / .035).toFloat() * (1f - life) * (1f - life)
        val length = (3f + exp(-t * 3.2f) * (11f + i % 5 * 4f)).dp.toPx()
        val tail = point - particle.direction * length
        drawLine(BlastFire.copy(alpha = .12f * fade), tail, point, (particle.width * 4f).dp.toPx(), StrokeCap.Round)
        drawLine(lerp(BlastFire, BlastWhite, 1f - life).copy(alpha = .95f * fade), tail, point, particle.width.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.drawShellFragments(art: RocketArtwork, fragments: List<ShellFragment>, origin: Offset, heading: Double, rocketScale: Float, age: Double) {
    val t = age.toFloat()
    val fade = (1.0 - CrashBlast.smooth((age - .35) / 1.1)).toFloat()
    val drag = ((1.0 - exp(-age * 1.8)) / 1.8).toFloat()
    val c = cos(heading).toFloat()
    val s = sin(heading).toFloat()
    fun orient(vector: Offset) = Offset(vector.x * c - vector.y * s, vector.x * s + vector.y * c)
    fragments.forEach { piece ->
        val local = piece.clip.center - Offset(22f, 0f)
        val position = origin + orient(local) * rocketScale + orient(piece.velocity) * (density * drag) + Offset(0f, (115f * t * t).dp.toPx())
        val glow = 15.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(BlastFire.copy(alpha = .24f * fade * exp(-t * 3f)), Color.Transparent), position, glow), glow, position)
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

private class BlastAnchor {
    private var point: Offset? = null
    private var viewport = Size.Zero
    private var chart = Size.Zero
    fun resolve(measured: Offset, viewportSize: Size, chartSize: Size): Offset {
        // Freeze while a fling settles; reproject on a real rotation/resize without restarting time.
        if (point == null || viewport != viewportSize || chart != chartSize) {
            point = measured
            viewport = viewportSize
            chart = chartSize
        }
        return requireNotNull(point)
    }
}
