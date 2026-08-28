package com.vircas.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.CoinflipEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val LuxePanel = Color(0xFF08100E)
private val LuxePanelHi = Color(0xFF101D18)
private val LuxeMint = Color(0xFF5BF0AA)
private val LuxeRed = Color(0xFFFF5E72)
private val LuxeGold = Color(0xFFFFDC82)
private val LuxeGoldMid = Color(0xFFD6A23F)
private val LuxeGoldDark = Color(0xFF5A3910)
private val LuxeViolet = Color(0xFFCBB9FF)
private val LuxeVioletMid = Color(0xFF8064D8)
private val LuxeVioletDark = Color(0xFF34216B)
private val LuxeEdge = Color(0xFFB77A2C)

private data class LuxeLockedFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean
)

private data class LuxeLanding(
    val xNorm: Float = 0f,
    val yNorm: Float = .195f,
    val depthNorm: Float = .115f,
    val yawDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val curve: Float = 0f,
    val swerve: Float = 0f
)

private data class LV3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: LV3) = LV3(x + other.x, y + other.y, z + other.z)
    operator fun times(scale: Float) = LV3(x * scale, y * scale, z * scale)
}

private data class LuxePose(
    val center: LV3,
    val radiusPx: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float
)

private data class LuxeCamera(val center: Offset, val focal: Float) {
    fun project(point: LV3): Offset {
        val denominator = (focal - point.z).coerceAtLeast(focal * .16f)
        val scale = focal / denominator
        return Offset(center.x + point.x * scale, center.y + point.y * scale)
    }
}

private data class LuxeQuad(val points: List<Offset>, val depth: Float, val color: Color)

@Composable
fun LuxuryCoinflip3DGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<LuxeLockedFlip?>(null) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("Pick a side. Every toss gets its own landing spot.") }
    var landing by remember { mutableStateOf(LuxeLanding()) }

    val toss = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }

    fun parsedStake(): Long = stake.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun leave() {
        val pendingWager = pending?.wager
        if (pendingWager != null) viewModel.cancelWager(pendingWager)
        activeSeries?.takeIf { it != pendingWager }?.let(viewModel::cancelWager)
        pending = null
        activeSeries = null
        onBack()
    }

    BackHandler(enabled = flipping || activeSeries != null) {
        if (!flipping) leave()
    }

    fun queueFlip(wager: ActiveWager, isSeries: Boolean) {
        if (flipping) return
        val result = CoinflipEngine(viewModel.randomProvider()).flip(pick)
        landing = randomLuxeLanding()
        pending = LuxeLockedFlip(wager, result, pick, isSeries)
        revealedSide = null
        lastWon = null
        flipping = true
        flipToken++
    }

    fun startSingle() {
        if (flipping) return
        viewModel.beginWager("Coinflip", parsedStake()) { started ->
            if (started == null) message = "Could not start · check stake and balance."
            else queueFlip(started, false)
        }
    }

    fun startSeriesAndFlip() {
        if (flipping || activeSeries != null) return
        viewModel.beginWager("Coinflip Series", parsedStake()) { started ->
            if (started == null) {
                message = "Could not start · check stake and balance."
            } else {
                activeSeries = started
                seriesMultiplier = 1.0
                streak = 0
                queueFlip(started, true)
            }
        }
    }

    fun cashOutSeries() {
        val active = activeSeries ?: return
        if (flipping || streak <= 0) return
        viewModel.settleWager(active, seriesMultiplier, "Coinflip series cash out", "Streak $streak")
        message = "Cashed out ${"%.2f".format(seriesMultiplier)}x · streak $streak."
        activeSeries = null
        seriesMultiplier = 1.0
        streak = 0
    }

    LaunchedEffect(flipToken) {
        val current = pending ?: return@LaunchedEffect
        message = "RESULT LOCKED · randomized 3D toss…"
        toss.snapTo(0f)
        impact.snapTo(0f)

        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - normalized) + 360f) % 360f
        val visualTurns = 7 + (flipToken % 4)
        val targetRotation = rotation.value + visualTurns * 360f + delta

        coroutineScope {
            launch { toss.animateTo(1f, tween(2140, easing = LinearEasing)) }
            launch { rotation.animateTo(targetRotation, tween(2140, easing = LinearOutSlowInEasing)) }
        }

        rotation.snapTo(desired)
        impact.snapTo(1f)
        impact.animateTo(
            0f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )

        revealedSide = current.result.side
        val won = current.result.side == current.picked
        lastWon = won
        if (current.series) {
            if (won) {
                seriesMultiplier *= current.result.outcome.multiplier
                streak++
                message = "${current.result.side.name} · WIN · bank ${"%.2f".format(seriesMultiplier)}x"
            } else {
                viewModel.settleWager(current.wager, 0.0, current.result.side.name, "Series lost after $streak wins")
                activeSeries = null
                seriesMultiplier = 1.0
                streak = 0
                message = "${current.result.side.name} · series lost"
            }
        } else {
            viewModel.settleWager(current.wager, current.result.outcome.multiplier, current.result.side.name, "Picked ${current.picked.name}")
            message = if (won) "${current.result.side.name} · WIN · 1.98x" else "${current.result.side.name} · LOSS"
        }
        pending = null
        flipping = false
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "BEVELED 3D COIN · RANDOMIZED LANDING",
        balance = balance,
        accent = LuxeMint,
        onBack = { if (!flipping) leave() }
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            LuxuryCoinStage(
                modifier = modifier,
                rotationDegrees = rotation.value,
                tossProgress = toss.value,
                impact = impact.value,
                landing = landing,
                flipping = flipping,
                revealedSide = revealedSide,
                lastWon = lastWon,
                seriesMultiplier = activeSeries?.let { seriesMultiplier },
                streak = streak
            )
        }
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            LuxuryCoinControls(
                modifier = modifier,
                stake = stake,
                onStake = { stake = it },
                pick = pick,
                onPick = { if (!flipping) pick = it },
                seriesMode = seriesMode,
                onSeriesMode = { if (!flipping && activeSeries == null) seriesMode = it },
                flipping = flipping,
                seriesActive = activeSeries != null,
                streak = streak,
                seriesMultiplier = seriesMultiplier,
                message = message,
                onFlip = {
                    val active = activeSeries
                    when {
                        active != null -> queueFlip(active, true)
                        seriesMode -> startSeriesAndFlip()
                        else -> startSingle()
                    }
                },
                onCashOut = ::cashOutSeries
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                stage(Modifier.weight(1.22f).fillMaxSize())
                controls(Modifier.weight(.78f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)) {
                stage(Modifier.weight(1f).fillMaxWidth())
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LuxuryCoinStage(
    modifier: Modifier,
    rotationDegrees: Float,
    tossProgress: Float,
    impact: Float,
    landing: LuxeLanding,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(LuxePanelHi, LuxePanel, Color(0xFF040705))), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = when {
                flipping -> tossProgress.coerceIn(0f, 1f)
                revealedSide != null -> 1f
                else -> 0f
            }
            val focal = max(size.minDimension * 2.35f, 650f)
            val camera = LuxeCamera(Offset(w * .5f, h * .5f), focal)
            val split = .58f
            val center: LV3
            val proximity: Float
            val falling: Float

            if (p <= split) {
                val q = (p / split).coerceIn(0f, 1f)
                val e = luxeEaseOutCubic(q)
                proximity = e
                falling = 0f
                center = LV3(
                    x = landing.curve * w * .045f * sin(q * PI.toFloat()),
                    y = luxeLerp(h * .075f, h * .008f, luxeSmoothStep(q)),
                    z = focal * .54f * e
                )
            } else {
                val q = ((p - split) / (1f - split)).coerceIn(0f, 1f)
                val s = luxeSmoothStep(q)
                proximity = 1f - s * .78f
                falling = s
                val startX = landing.curve * w * .045f
                val targetX = landing.xNorm * w
                center = LV3(
                    x = luxeLerp(startX, targetX, s) + sin(q * PI.toFloat()) * landing.swerve * w * .028f,
                    y = luxeLerp(h * .008f, h * landing.yNorm, q.pow(1.62f)),
                    z = focal * luxeLerp(.54f, landing.depthNorm, s)
                )
            }

            val baseRadius = size.minDimension * if (h < w * .82f) .158f else .178f
            val impactLift = abs(impact) * h * .010f
            val settle = impact * 8.5f
            val pose = LuxePose(
                center = center.copy(y = center.y - impactLift),
                radiusPx = baseRadius,
                rotX = rotationDegrees * LUXE_DEG,
                rotY = (sin(p * PI.toFloat() * 2f) * 11f + landing.yawDeg * falling + settle) * LUXE_DEG,
                rotZ = (sin(p * PI.toFloat()) * 8f + landing.rollDeg * falling + sin(p * PI.toFloat() * 2f) * 2.2f) * LUXE_DEG
            )

            drawLuxuryEnvironment(w, h, proximity, falling, landing)

            val projectedCenter = camera.project(pose.center)
            val finalCenter = camera.project(LV3(landing.xNorm * w, h * landing.yNorm, focal * landing.depthNorm))
            val radiusProbe = camera.project(pose.center + LV3(baseRadius, 0f, 0f))
            val screenRadius = max(baseRadius * .35f, luxeDistance(projectedCenter, radiusProbe))
            val shadowX = luxeLerp(w * .5f, finalCenter.x, falling)
            val shadowY = luxeLerp(h * .79f, finalCenter.y + screenRadius * .42f, falling)
            val shadowAlpha = (.10f + falling * .20f).coerceIn(.07f, .30f)
            repeat(5) { layer ->
                val grow = 1f + layer * .14f
                drawOval(
                    Color.Black.copy(alpha = shadowAlpha * (1f - layer * .15f)),
                    Offset(shadowX - screenRadius * 1.05f * grow, shadowY - screenRadius * .095f * grow),
                    androidx.compose.ui.geometry.Size(screenRadius * 2.10f * grow, screenRadius * .19f * grow)
                )
            }

            if (flipping && p in .16f.. .80f) {
                repeat(3) { i ->
                    drawCircle(
                        LuxeGold.copy(alpha = .060f / (i + 1)),
                        screenRadius * (.14f + i * .035f),
                        Offset(projectedCenter.x - (i + 1) * w * .015f, projectedCenter.y + (i + 1) * h * .008f),
                        style = Stroke(max(1f, screenRadius * .025f))
                    )
                }
            }

            drawLuxuryCoinMesh(camera, pose)

            if (!flipping && revealedSide != null && lastWon == true) {
                repeat(12) { i ->
                    val a = i / 12f * LUXE_TWO_PI
                    val r = screenRadius * (1.22f + (i % 3) * .12f)
                    drawCircle(LuxeMint.copy(alpha = .34f), max(2f, screenRadius * .018f), Offset(projectedCenter.x + cos(a) * r, projectedCenter.y + sin(a) * r * .58f))
                }
            }
        }

        Column(Modifier.align(Alignment.TopCenter).padding(top = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (seriesMultiplier != null) {
                Text("DOUBLE OR NOTHING", color = Color.White.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${"%.2f".format(seriesMultiplier)}x", color = LuxeMint, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("STREAK $streak", color = Color.White.copy(alpha = .36f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            } else {
                Text(
                    when {
                        flipping && tossProgress < .58f -> "COMING AT YOU"
                        flipping -> "FALLING TO A NEW SPOT"
                        else -> "50 / 50 · 1.98x"
                    },
                    color = Color.White.copy(alpha = .46f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
        }

        AnimatedVisibility(visible = revealedSide != null && !flipping, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)) {
            val won = lastWon == true
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (won) LuxeMint else LuxeRed).copy(alpha = .10f),
                border = BorderStroke(1.dp, (if (won) LuxeMint else LuxeRed).copy(alpha = .36f))
            ) {
                Text(
                    "${revealedSide?.name ?: ""} · ${if (won) "WIN" else "LOSS"}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (won) LuxeMint else LuxeRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .6.sp
                )
            }
        }
    }
}

private fun DrawScope.drawLuxuryEnvironment(width: Float, height: Float, proximity: Float, falling: Float, landing: LuxeLanding) {
    val horizon = height * .47f
    drawRect(
        Brush.radialGradient(
            listOf(LuxeMint.copy(alpha = .045f + proximity * .055f), Color.Transparent),
            center = Offset(width * .5f, height * .44f),
            radius = size.minDimension * (.58f + proximity * .28f)
        )
    )
    drawOval(
        Brush.radialGradient(listOf(Color(0xFF183126).copy(alpha = .72f), Color(0xFF07100C).copy(alpha = .92f))),
        Offset(width * .06f, height * .47f),
        androidx.compose.ui.geometry.Size(width * .88f, height * .48f)
    )
    drawOval(Color.White.copy(alpha = .045f), Offset(width * .06f, height * .47f), androidx.compose.ui.geometry.Size(width * .88f, height * .48f), style = Stroke(1.2f))

    repeat(9) { i ->
        val x = i / 8f
        val bottomX = width * (.05f + x * .90f)
        val horizonX = width * (.5f + (x - .5f) * .14f)
        drawLine(LuxeMint.copy(alpha = .024f), Offset(horizonX, horizon), Offset(bottomX, height * .95f), 1f)
    }
    repeat(7) { i ->
        val q = (i + 1) / 8f
        val perspective = q * q
        val y = horizon + (height * .46f) * perspective
        val half = width * (.06f + .42f * perspective)
        drawLine(Color.White.copy(alpha = .020f + falling * .008f), Offset(width * .5f - half, y), Offset(width * .5f + half, y), 1f)
    }

    if (falling > .10f) {
        val landingX = width * (.5f + landing.xNorm * 1.05f)
        val landingY = height * (.71f + (landing.yNorm - .16f) * .50f)
        drawCircle(LuxeMint.copy(alpha = .05f * falling), width * .055f, Offset(landingX, landingY), style = Stroke(1.2f))
        drawCircle(LuxeMint.copy(alpha = .025f * falling), width * .085f, Offset(landingX, landingY), style = Stroke(1f))
    }
}

private fun DrawScope.drawLuxuryCoinMesh(camera: LuxeCamera, pose: LuxePose) {
    val segments = 96
    val faceRadius = .915f
    val faceZ = .145f
    val shoulderZ = .085f
    val lightDir = luxeNormalized(LV3(-.44f, -.57f, .69f))
    val quads = ArrayList<LuxeQuad>(segments * 3)

    fun addStrip(r0: Float, z0: Float, r1: Float, z1: Float, normalZ: Float, base: Color, ridged: Boolean) {
        repeat(segments) { i ->
            val a0 = i / segments.toFloat() * LUXE_TWO_PI
            val a1 = (i + 1) / segments.toFloat() * LUXE_TWO_PI
            val mid = (a0 + a1) * .5f
            val localNormal = luxeNormalized(LV3(cos(mid), sin(mid), normalZ))
            val worldNormal = luxeRotate(localNormal, pose.rotX, pose.rotY, pose.rotZ)
            if (worldNormal.z <= -.10f) return@repeat
            val local = listOf(
                LV3(cos(a0) * r0, sin(a0) * r0, z0),
                LV3(cos(a1) * r0, sin(a1) * r0, z0),
                LV3(cos(a1) * r1, sin(a1) * r1, z1),
                LV3(cos(a0) * r1, sin(a0) * r1, z1)
            )
            val world = local.map { luxeTransform(it, pose) }
            val diffuse = (.25f + .75f * max(0f, luxeDot(worldNormal, lightDir))).coerceIn(.18f, 1f)
            val micro = if (ridged) {
                when (i % 4) {
                    0 -> 1.16f
                    1 -> .78f
                    2 -> 1.02f
                    else -> .68f
                }
            } else 1f
            quads += LuxeQuad(
                points = world.map(camera::project),
                depth = world.sumOf { it.z.toDouble() }.toFloat() / 4f,
                color = luxeShade(base, (diffuse * micro).coerceIn(.20f, 1.12f))
            )
        }
    }

    addStrip(faceRadius, faceZ, 1f, shoulderZ, .90f, LuxeGoldMid, false)
    addStrip(1f, shoulderZ, 1f, -shoulderZ, 0f, LuxeEdge, true)
    addStrip(1f, -shoulderZ, faceRadius, -faceZ, -.90f, LuxeGoldDark, false)

    quads.sortedBy { it.depth }.forEach { quad ->
        val path = luxePolygon(quad.points)
        drawPath(path, quad.color)
        drawPath(path, Color.Black.copy(alpha = .09f), style = Stroke(max(1f, pose.radiusPx * .0045f)))
    }

    val frontNormal = luxeRotate(LV3(0f, 0f, 1f), pose.rotX, pose.rotY, pose.rotZ)
    val backNormal = frontNormal * -1f
    if (frontNormal.z > .018f) drawLuxuryFace(camera, pose, faceZ, faceRadius, true, frontNormal)
    else if (backNormal.z > .018f) drawLuxuryFace(camera, pose, -faceZ, faceRadius, false, backNormal)
}

private fun DrawScope.drawLuxuryFace(camera: LuxeCamera, pose: LuxePose, z: Float, radiusLocal: Float, heads: Boolean, normal: LV3) {
    val segments = 84
    val ring = (0 until segments).map { i ->
        val a = i / segments.toFloat() * LUXE_TWO_PI
        luxeProjectLocal(camera, pose, LV3(cos(a) * radiusLocal, sin(a) * radiusLocal, z))
    }
    val path = luxePolygon(ring)
    val bounds = luxeBounds(ring)
    val highlight = luxeProjectLocal(camera, pose, LV3(-.28f, -.30f, z))
    val projectedRadius = max(bounds.second.x - bounds.first.x, bounds.second.y - bounds.first.y) * .65f
    val light = if (heads) LuxeGold else LuxeViolet
    val mid = if (heads) LuxeGoldMid else LuxeVioletMid
    val dark = if (heads) LuxeGoldDark else LuxeVioletDark
    val facing = normal.z.coerceIn(0f, 1f)

    drawPath(path, Brush.radialGradient(listOf(Color.White.copy(alpha = .94f), light, mid, dark), highlight, max(projectedRadius, 24f)))
    drawPath(path, Color.Black.copy(alpha = .34f), style = Stroke(max(1.5f, pose.radiusPx * .020f)))

    luxeRing(camera, pose, z, .82f, Color.White.copy(alpha = .27f * facing), pose.radiusPx * .010f)
    luxeRing(camera, pose, z, .72f, Color.Black.copy(alpha = .20f), pose.radiusPx * .008f)
    luxeRing(camera, pose, z, .43f, Color.White.copy(alpha = .10f * facing), pose.radiusPx * .006f)

    repeat(28) { i ->
        val a = i / 28f * LUXE_TWO_PI
        val p0 = luxeProjectLocal(camera, pose, LV3(cos(a) * .85f, sin(a) * .85f, z))
        val p1 = luxeProjectLocal(camera, pose, LV3(cos(a) * .91f, sin(a) * .91f, z))
        drawLine(Color.Black.copy(alpha = .17f), p0, p1, max(1f, pose.radiusPx * .005f), StrokeCap.Round)
    }

    if (heads) drawLuxuryHeadsEmblem(camera, pose, z) else drawLuxuryTailsEmblem(camera, pose, z)

    val spec = luxeProjectLocal(camera, pose, LV3(-.27f, -.25f, z))
    val specEdge = luxeProjectLocal(camera, pose, LV3(-.12f, -.25f, z))
    drawCircle(Color.White.copy(alpha = .18f * facing), max(2f, luxeDistance(spec, specEdge)), spec)
}

private fun DrawScope.drawLuxuryHeadsEmblem(camera: LuxeCamera, pose: LuxePose, z: Float) {
    val ink = Color(0xFF3B260D).copy(alpha = .84f)
    val goldHi = Color.White.copy(alpha = .17f)
    val outer = (0 until 16).map { i ->
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 8f
        val r = if (i % 2 == 0) .31f else .19f
        luxeProjectLocal(camera, pose, LV3(cos(a) * r, sin(a) * r, z))
    }
    drawPath(luxePolygon(outer), ink)
    luxeRing(camera, pose, z, .13f, goldHi, max(1f, pose.radiusPx * .012f))
    repeat(6) { i ->
        val y = -.32f + i * .13f
        val p0 = luxeProjectLocal(camera, pose, LV3(-.43f, y, z))
        val p1 = luxeProjectLocal(camera, pose, LV3(-.35f, y + .04f, z))
        val p2 = luxeProjectLocal(camera, pose, LV3(.43f, y, z))
        val p3 = luxeProjectLocal(camera, pose, LV3(.35f, y + .04f, z))
        drawLine(ink.copy(alpha = .55f), p0, p1, max(1f, pose.radiusPx * .011f), StrokeCap.Round)
        drawLine(ink.copy(alpha = .55f), p2, p3, max(1f, pose.radiusPx * .011f), StrokeCap.Round)
    }
}

private fun DrawScope.drawLuxuryTailsEmblem(camera: LuxeCamera, pose: LuxePose, z: Float) {
    val ink = Color(0xFF291651).copy(alpha = .86f)
    val hex = (0 until 6).map { i ->
        val a = PI.toFloat() / 6f + i * LUXE_TWO_PI / 6f
        luxeProjectLocal(camera, pose, LV3(cos(a) * .34f, sin(a) * .34f, z))
    }
    drawPath(luxePolygon(hex), ink.copy(alpha = .24f))
    drawPath(luxePolygon(hex), ink, style = Stroke(max(1.5f, pose.radiusPx * .025f)))
    val bolt = listOf(
        LV3(.05f, -.31f, z), LV3(-.15f, .01f, z), LV3(-.02f, .01f, z),
        LV3(-.10f, .31f, z), LV3(.18f, -.05f, z), LV3(.04f, -.05f, z)
    ).map { luxeProjectLocal(camera, pose, it) }
    drawPath(luxePolygon(bolt), ink)
    repeat(8) { i ->
        val a = i * LUXE_TWO_PI / 8f
        val p0 = luxeProjectLocal(camera, pose, LV3(cos(a) * .47f, sin(a) * .47f, z))
        val p1 = luxeProjectLocal(camera, pose, LV3(cos(a) * .55f, sin(a) * .55f, z))
        drawLine(ink.copy(alpha = .48f), p0, p1, max(1f, pose.radiusPx * .010f), StrokeCap.Round)
    }
}

private fun DrawScope.luxeRing(camera: LuxeCamera, pose: LuxePose, z: Float, radius: Float, color: Color, width: Float) {
    val pts = (0 until 64).map { i ->
        val a = i / 64f * LUXE_TWO_PI
        luxeProjectLocal(camera, pose, LV3(cos(a) * radius, sin(a) * radius, z))
    }
    drawPath(luxePolygon(pts), color, style = Stroke(max(1f, width)))
}

private fun randomLuxeLanding(): LuxeLanding = LuxeLanding(
    xNorm = Random.nextFloat() * .34f - .17f,
    yNorm = .165f + Random.nextFloat() * .065f,
    depthNorm = .085f + Random.nextFloat() * .075f,
    yawDeg = Random.nextFloat() * 18f - 9f,
    rollDeg = Random.nextFloat() * 20f - 10f,
    curve = Random.nextFloat() * 2f - 1f,
    swerve = Random.nextFloat() * 2f - 1f
)

private fun luxeTransform(local: LV3, pose: LuxePose): LV3 = luxeRotate(local * pose.radiusPx, pose.rotX, pose.rotY, pose.rotZ) + pose.center
private fun luxeProjectLocal(camera: LuxeCamera, pose: LuxePose, local: LV3): Offset = camera.project(luxeTransform(local, pose))

private fun luxeRotate(v: LV3, rx: Float, ry: Float, rz: Float): LV3 {
    val cx = cos(rx); val sx = sin(rx)
    val cy = cos(ry); val sy = sin(ry)
    val cz = cos(rz); val sz = sin(rz)
    val x1 = v.x
    val y1 = v.y * cx - v.z * sx
    val z1 = v.y * sx + v.z * cx
    val x2 = x1 * cy + z1 * sy
    val y2 = y1
    val z2 = -x1 * sy + z1 * cy
    return LV3(x2 * cz - y2 * sz, x2 * sz + y2 * cz, z2)
}

private fun luxePolygon(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

private fun luxeBounds(points: List<Offset>): Pair<Offset, Offset> {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    points.forEach {
        minX = min(minX, it.x); minY = min(minY, it.y)
        maxX = max(maxX, it.x); maxY = max(maxY, it.y)
    }
    return Offset(minX, minY) to Offset(maxX, maxY)
}

private fun luxeDot(a: LV3, b: LV3): Float = a.x * b.x + a.y * b.y + a.z * b.z
private fun luxeNormalized(v: LV3): LV3 {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(.0001f)
    return LV3(v.x / len, v.y / len, v.z / len)
}
private fun luxeShade(color: Color, factor: Float): Color = Color(
    (color.red * factor).coerceIn(0f, 1f),
    (color.green * factor).coerceIn(0f, 1f),
    (color.blue * factor).coerceIn(0f, 1f),
    color.alpha
)
private fun luxeDistance(a: Offset, b: Offset): Float = sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))
private fun luxeEaseOutCubic(t: Float): Float = 1f - (1f - t).pow(3f)
private fun luxeSmoothStep(t: Float): Float = t * t * (3f - 2f * t)
private fun luxeLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private const val LUXE_DEG = (PI / 180.0).toFloat()
private const val LUXE_TWO_PI = (PI * 2.0).toFloat()

@Composable
private fun LuxuryCoinControls(
    modifier: Modifier,
    stake: String,
    onStake: (String) -> Unit,
    pick: CoinflipEngine.Side,
    onPick: (CoinflipEngine.Side) -> Unit,
    seriesMode: Boolean,
    onSeriesMode: (Boolean) -> Unit,
    flipping: Boolean,
    seriesActive: Boolean,
    streak: Int,
    seriesMultiplier: Double,
    message: String,
    onFlip: () -> Unit,
    onCashOut: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LuxurySideChoice(CoinflipEngine.Side.HEADS, pick == CoinflipEngine.Side.HEADS, !flipping, { onPick(CoinflipEngine.Side.HEADS) }, Modifier.weight(1f))
            LuxurySideChoice(CoinflipEngine.Side.TAILS, pick == CoinflipEngine.Side.TAILS, !flipping, { onPick(CoinflipEngine.Side.TAILS) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LuxuryModePill("SINGLE", !seriesMode && !seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) { onSeriesMode(false) }
            LuxuryModePill("DOUBLE OR NOTHING", seriesMode || seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) { onSeriesMode(true) }
        }
        QuickStakeRow(enabled = !flipping && !seriesActive, current = stake, onPick = onStake)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumStakeField(stake, !flipping && !seriesActive, LuxeMint, Modifier.weight(1f), onStake)
            PremiumActionButton(
                text = when {
                    flipping -> "IN FLIGHT…"
                    seriesActive -> "FLIP AGAIN"
                    seriesMode -> "START + FLIP"
                    else -> "TOSS · 1.98x"
                },
                accent = LuxeMint,
                enabled = !flipping,
                modifier = Modifier.weight(.82f),
                onClick = onFlip
            )
        }
        if (seriesActive) {
            Surface(shape = RoundedCornerShape(15.dp), color = LuxePanelHi, border = BorderStroke(1.dp, LuxeGold.copy(alpha = .18f))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SERIES BANK", fontSize = 7.sp, color = Color.White.copy(alpha = .34f), fontWeight = FontWeight.Black)
                        Text("${"%.2f".format(seriesMultiplier)}x", color = LuxeGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text("STREAK $streak", color = Color.White.copy(alpha = .50f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    PremiumActionButton("CASH OUT", LuxeGold, !flipping && streak > 0, onClick = onCashOut)
                }
            }
        }
        PremiumMessageCard(message, LuxeMint)
    }
}

@Composable
private fun LuxurySideChoice(side: CoinflipEngine.Side, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = if (side == CoinflipEngine.Side.HEADS) LuxeGold else LuxeViolet
    Surface(
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .13f) else LuxePanel,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .58f) else Color.White.copy(alpha = .06f))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(29.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .72f), accent, accent.copy(alpha = .50f))), size.minDimension * .48f, c)
                drawCircle(Color.Black.copy(alpha = .24f), size.minDimension * .40f, c, style = Stroke(1.5f))
                val ink = if (side == CoinflipEngine.Side.HEADS) Color(0xFF3A260D) else Color(0xFF28164F)
                if (side == CoinflipEngine.Side.HEADS) {
                    drawCircle(ink, size.minDimension * .13f, c)
                    drawCircle(ink.copy(alpha = .35f), size.minDimension * .25f, c, style = Stroke(1.6f))
                } else {
                    drawLine(ink, Offset(c.x - 5f, c.y + 6f), Offset(c.x + 5f, c.y - 6f), 3f, StrokeCap.Round)
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(side.name, color = if (selected) accent else Color.White.copy(alpha = .68f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("1.98x payout", color = Color.White.copy(alpha = .30f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LuxuryModePill(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .085f) else LuxePanel,
        border = BorderStroke(1.dp, if (selected) LuxeMint.copy(alpha = .34f) else Color.White.copy(alpha = .05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = selected, label = "luxury-coin-mode") { active ->
                Text(label, color = if (active) Color.White.copy(alpha = .84f) else Color.White.copy(alpha = .38f), fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        }
    }
}
