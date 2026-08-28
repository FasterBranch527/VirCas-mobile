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

private val True3dPanel = Color(0xFF09110F)
private val True3dPanelHi = Color(0xFF101D18)
private val True3dMint = Color(0xFF5BF0AA)
private val True3dRed = Color(0xFFFF5E72)
private val True3dGold = Color(0xFFFFD978)
private val True3dGoldMid = Color(0xFFD49B35)
private val True3dGoldDark = Color(0xFF664414)
private val True3dViolet = Color(0xFFC9B7FF)
private val True3dVioletMid = Color(0xFF8063D7)
private val True3dVioletDark = Color(0xFF35236F)
private val True3dEdge = Color(0xFFB47A28)

private data class True3dLockedFlip(
    val wager: ActiveWager,
    val result: CoinflipEngine.Result,
    val picked: CoinflipEngine.Side,
    val series: Boolean
)

private data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: V3) = V3(x + other.x, y + other.y, z + other.z)
    operator fun times(scale: Float) = V3(x * scale, y * scale, z * scale)
}

private data class TrueCoinPose(
    val center: V3,
    val radiusPx: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float
)

private data class TrueCoinCamera(
    val screenCenter: Offset,
    val focalPx: Float
) {
    fun project(point: V3): Offset {
        val denominator = (focalPx - point.z).coerceAtLeast(focalPx * .16f)
        val scale = focalPx / denominator
        return Offset(
            screenCenter.x + point.x * scale,
            screenCenter.y + point.y * scale
        )
    }
}

private data class SideQuad(
    val points: List<Offset>,
    val depth: Float,
    val color: Color
)

@Composable
fun PerspectiveCoinflip3DGameScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val balance by viewModel.balance.collectAsState()
    var stake by remember { mutableStateOf("1000") }
    var pick by remember { mutableStateOf(CoinflipEngine.Side.HEADS) }
    var seriesMode by remember { mutableStateOf(false) }
    var activeSeries by remember { mutableStateOf<ActiveWager?>(null) }
    var seriesMultiplier by remember { mutableDoubleStateOf(1.0) }
    var streak by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<True3dLockedFlip?>(null) }
    var flipToken by remember { mutableIntStateOf(0) }
    var flipping by remember { mutableStateOf(false) }
    var revealedSide by remember { mutableStateOf<CoinflipEngine.Side?>(null) }
    var lastWon by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("Pick a side. The result locks before the 3D toss begins.") }

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
        pending = True3dLockedFlip(wager, result, pick, isSeries)
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
                message = "Series live · first toss locked."
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
        message = "RESULT LOCKED · coin coming at you…"
        toss.snapTo(0f)
        impact.snapTo(0f)

        val normalized = ((rotation.value % 360f) + 360f) % 360f
        val desired = if (current.result.side == CoinflipEngine.Side.HEADS) 0f else 180f
        val delta = ((desired - normalized) + 360f) % 360f
        val visualTurns = 7 + (flipToken % 3)
        val targetRotation = rotation.value + visualTurns * 360f + delta

        coroutineScope {
            launch { toss.animateTo(1f, tween(2050, easing = LinearEasing)) }
            launch { rotation.animateTo(targetRotation, tween(2050, easing = LinearOutSlowInEasing)) }
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
                viewModel.settleWager(
                    current.wager,
                    0.0,
                    current.result.side.name,
                    "Series lost after $streak win${if (streak == 1) "" else "s"}"
                )
                activeSeries = null
                seriesMultiplier = 1.0
                streak = 0
                message = "${current.result.side.name} · series lost"
            }
        } else {
            viewModel.settleWager(
                current.wager,
                current.result.outcome.multiplier,
                current.result.side.name,
                "Picked ${current.picked.name}"
            )
            message = if (won) "${current.result.side.name} · WIN · 1.98x" else "${current.result.side.name} · LOSS"
        }

        pending = null
        flipping = false
    }

    PremiumGameFrame(
        title = "Coin Flip",
        subtitle = "TRUE 3D CYLINDER · PERSPECTIVE CAMERA",
        balance = balance,
        accent = True3dMint,
        onBack = { if (!flipping) leave() }
    ) { compact, landscape ->
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            TruePerspectiveCoinStage(
                modifier = modifier,
                rotationDegrees = rotation.value,
                tossProgress = toss.value,
                impact = impact.value,
                flipping = flipping,
                revealedSide = revealedSide,
                lastWon = lastWon,
                seriesMultiplier = activeSeries?.let { seriesMultiplier },
                streak = streak
            )
        }
        val controls: @Composable (Modifier) -> Unit = { modifier ->
            TrueCoinControls(
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
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stage(Modifier.weight(1.20f).fillMaxSize())
                controls(Modifier.weight(.80f))
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
            ) {
                stage(Modifier.weight(1f).fillMaxWidth())
                controls(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TruePerspectiveCoinStage(
    modifier: Modifier,
    rotationDegrees: Float,
    tossProgress: Float,
    impact: Float,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(True3dPanelHi, True3dPanel, Color(0xFF050806))),
            RoundedCornerShape(28.dp)
        ),
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
            val focal = max(size.minDimension * 2.25f, 620f)
            val camera = TrueCoinCamera(Offset(w * .5f, h * .5f), focal)
            val approachSplit = .60f

            val center: V3
            val proximity: Float
            val falling: Float
            if (p <= approachSplit) {
                val q = (p / approachSplit).coerceIn(0f, 1f)
                val e = easeOutCubic(q)
                proximity = e
                falling = 0f
                center = V3(
                    x = sin(q * PI.toFloat()) * w * .024f,
                    y = lerp(h * .085f, h * .010f, smoothStep(q)),
                    z = focal * .50f * e
                )
            } else {
                val q = ((p - approachSplit) / (1f - approachSplit)).coerceIn(0f, 1f)
                val s = smoothStep(q)
                proximity = 1f - s * .76f
                falling = s
                center = V3(
                    x = sin((1f - q) * PI.toFloat() * .75f) * w * .020f * (1f - q),
                    y = lerp(h * .010f, h * .245f, q.pow(1.65f)),
                    z = focal * lerp(.50f, .12f, s)
                )
            }

            val baseRadius = size.minDimension * if (h < w * .82f) .155f else .175f
            val impactTilt = impact * 11.5f
            val impactLift = abs(impact) * h * .012f
            val pose = TrueCoinPose(
                center = center.copy(y = center.y - impactLift),
                radiusPx = baseRadius,
                rotX = rotationDegrees * DEG,
                rotY = (sin(p * PI.toFloat() * 2f) * 10f + impactTilt) * DEG,
                rotZ = (sin(p * PI.toFloat()) * 7.5f + sin(p * PI.toFloat() * 2f) * 2f) * DEG
            )

            drawTrue3dEnvironment(
                width = w,
                height = h,
                proximity = proximity,
                falling = falling
            )

            val floorY = h * .775f
            val projectedCenter = camera.project(pose.center)
            val shadowScale = if (p <= approachSplit) 1f - proximity * .38f else .62f + falling * .54f
            val shadowWidth = baseRadius * 1.75f * shadowScale
            val shadowAlpha = if (p <= approachSplit) .26f - proximity * .15f else .11f + falling * .18f
            repeat(5) { layer ->
                val grow = 1f + layer * .15f
                drawOval(
                    Color.Black.copy(alpha = (shadowAlpha * (1f - layer * .15f)).coerceIn(.02f, .30f)),
                    topLeft = Offset(w * .5f - shadowWidth * grow, floorY - baseRadius * .075f * grow),
                    size = androidx.compose.ui.geometry.Size(shadowWidth * grow * 2f, baseRadius * .15f * grow)
                )
            }

            if (flipping && p in .14f.. .82f) {
                val trailAlpha = (.09f * (1f - abs(.52f - p))).coerceAtLeast(.025f)
                repeat(3) { i ->
                    drawCircle(
                        True3dGold.copy(alpha = trailAlpha / (i + 1)),
                        radius = baseRadius * (.14f + i * .04f),
                        center = Offset(
                            projectedCenter.x - (i + 1) * w * .018f,
                            projectedCenter.y + (i + 1) * h * .010f
                        ),
                        style = Stroke(baseRadius * .025f)
                    )
                }
            }

            drawTrueCoinMesh(camera, pose)

            if (!flipping && revealedSide != null && lastWon == true) {
                repeat(12) { i ->
                    val a = i / 12f * PI.toFloat() * 2f
                    val r = baseRadius * (1.50f + (i % 3) * .18f)
                    drawCircle(
                        True3dMint.copy(alpha = .35f),
                        baseRadius * .025f,
                        Offset(projectedCenter.x + cos(a) * r, projectedCenter.y + sin(a) * r * .68f)
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (seriesMultiplier != null) {
                Text("DOUBLE OR NOTHING", color = Color.White.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${"%.2f".format(seriesMultiplier)}x", color = True3dMint, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("STREAK $streak", color = Color.White.copy(alpha = .36f), fontSize = 8.sp, fontWeight = FontWeight.Black)
            } else {
                Text(
                    when {
                        flipping && tossProgress < .60f -> "COMING AT YOU"
                        flipping -> "DROPPING TO TABLE"
                        else -> "50 / 50 · 1.98x"
                    },
                    color = Color.White.copy(alpha = .46f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
        }

        AnimatedVisibility(
            visible = revealedSide != null && !flipping,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)
        ) {
            val won = lastWon == true
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (won) True3dMint else True3dRed).copy(alpha = .10f),
                border = BorderStroke(1.dp, (if (won) True3dMint else True3dRed).copy(alpha = .36f))
            ) {
                Text(
                    "${revealedSide?.name ?: ""} · ${if (won) "WIN" else "LOSS"}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (won) True3dMint else True3dRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .6.sp
                )
            }
        }
    }
}

private fun DrawScope.drawTrue3dEnvironment(
    width: Float,
    height: Float,
    proximity: Float,
    falling: Float
) {
    val horizon = height * .49f
    drawRect(
        Brush.radialGradient(
            listOf(True3dMint.copy(alpha = .055f + proximity * .045f), Color.Transparent),
            center = Offset(width * .5f, height * .46f),
            radius = size.minDimension * (.55f + proximity * .28f)
        )
    )

    drawLine(
        Color.White.copy(alpha = .05f),
        Offset(width * .08f, horizon),
        Offset(width * .92f, horizon),
        1.2f
    )

    repeat(9) { i ->
        val x = i / 8f
        val bottomX = width * (.04f + x * .92f)
        val horizonX = width * (.5f + (x - .5f) * .15f)
        drawLine(
            True3dMint.copy(alpha = .028f),
            Offset(horizonX, horizon),
            Offset(bottomX, height * .96f),
            1f
        )
    }

    repeat(7) { i ->
        val q = (i + 1) / 8f
        val perspective = q * q
        val y = horizon + (height * .47f) * perspective
        val half = width * (.07f + .43f * perspective)
        drawLine(
            Color.White.copy(alpha = .022f + falling * .006f),
            Offset(width * .5f - half, y),
            Offset(width * .5f + half, y),
            1f
        )
    }

    drawOval(
        True3dMint.copy(alpha = .025f + falling * .018f),
        Offset(width * .20f, height * .69f),
        androidx.compose.ui.geometry.Size(width * .60f, height * .17f)
    )
}

private fun DrawScope.drawTrueCoinMesh(camera: TrueCoinCamera, pose: TrueCoinPose) {
    val segments = 48
    val halfThickness = .135f
    val frontNormal = rotateVector(V3(0f, 0f, 1f), pose.rotX, pose.rotY, pose.rotZ)
    val backNormal = frontNormal * -1f
    val lightDir = normalized(V3(-.42f, -.58f, .70f))

    val sideQuads = ArrayList<SideQuad>(segments)
    repeat(segments) { i ->
        val a0 = i / segments.toFloat() * TWO_PI
        val a1 = (i + 1) / segments.toFloat() * TWO_PI
        val mid = (a0 + a1) * .5f
        val localNormal = V3(cos(mid), sin(mid), 0f)
        val worldNormal = rotateVector(localNormal, pose.rotX, pose.rotY, pose.rotZ)
        if (worldNormal.z <= -.06f) return@repeat

        val local = listOf(
            V3(cos(a0), sin(a0), -halfThickness),
            V3(cos(a1), sin(a1), -halfThickness),
            V3(cos(a1), sin(a1), halfThickness),
            V3(cos(a0), sin(a0), halfThickness)
        )
        val world = local.map { transformCoinPoint(it, pose) }
        val points = world.map(camera::project)
        val depth = world.sumOf { it.z.toDouble() }.toFloat() / world.size
        val diffuse = (.30f + .70f * max(0f, dot(worldNormal, lightDir))).coerceIn(.25f, 1f)
        val ridge = if (i % 2 == 0) 1.08f else .82f
        val color = shade(True3dEdge, (diffuse * ridge).coerceIn(.22f, 1.08f))
        sideQuads += SideQuad(points, depth, color)
    }

    sideQuads.sortedBy { it.depth }.forEach { quad ->
        val path = polygonPath(quad.points)
        drawPath(path, quad.color)
        drawPath(path, Color.Black.copy(alpha = .10f), style = Stroke(max(1f, pose.radiusPx * .006f)))
    }

    if (frontNormal.z > .015f) {
        drawCoinFace(camera, pose, sideZ = halfThickness, heads = true, normal = frontNormal)
    } else if (backNormal.z > .015f) {
        drawCoinFace(camera, pose, sideZ = -halfThickness, heads = false, normal = backNormal)
    }
}

private fun DrawScope.drawCoinFace(
    camera: TrueCoinCamera,
    pose: TrueCoinPose,
    sideZ: Float,
    heads: Boolean,
    normal: V3
) {
    val segments = 56
    val ringWorld = (0 until segments).map { i ->
        val a = i / segments.toFloat() * TWO_PI
        transformCoinPoint(V3(cos(a), sin(a), sideZ), pose)
    }
    val ring = ringWorld.map(camera::project)
    val facePath = polygonPath(ring)
    val bounds = projectedBounds(ring)
    val faceCenter = camera.project(transformCoinPoint(V3(0f, 0f, sideZ), pose))
    val highlight = camera.project(transformCoinPoint(V3(-.25f, -.28f, sideZ + if (sideZ > 0f) .003f else -.003f), pose))
    val radius = max(bounds.second.x - bounds.first.x, bounds.second.y - bounds.first.y) * .62f
    val light = if (heads) True3dGold else True3dViolet
    val mid = if (heads) True3dGoldMid else True3dVioletMid
    val dark = if (heads) True3dGoldDark else True3dVioletDark
    val facing = normal.z.coerceIn(0f, 1f)

    drawPath(
        facePath,
        Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = .96f),
                light,
                mid,
                dark
            ),
            center = highlight,
            radius = max(radius, 24f)
        )
    )
    drawPath(facePath, Color.Black.copy(alpha = .30f), style = Stroke(max(1.5f, pose.radiusPx * .026f)))

    drawProjectedRing(camera, pose, sideZ, .84f, Color.White.copy(alpha = .24f * facing), pose.radiusPx * .012f)
    drawProjectedRing(camera, pose, sideZ, .72f, Color.Black.copy(alpha = .18f), pose.radiusPx * .008f)

    repeat(18) { i ->
        val a = i / 18f * TWO_PI
        val inner = projectLocal(camera, pose, V3(cos(a) * .88f, sin(a) * .88f, sideZ))
        val outer = projectLocal(camera, pose, V3(cos(a) * .94f, sin(a) * .94f, sideZ))
        drawLine(
            Color.Black.copy(alpha = .16f),
            inner,
            outer,
            max(1f, pose.radiusPx * .006f),
            StrokeCap.Round
        )
    }

    if (heads) drawHeadsMonogram(camera, pose, sideZ) else drawTailsMonogram(camera, pose, sideZ)

    val specCenter = projectLocal(camera, pose, V3(-.25f, -.24f, sideZ))
    val specEdge = projectLocal(camera, pose, V3(-.08f, -.24f, sideZ))
    val specRadius = max(2f, distance(specCenter, specEdge))
    drawCircle(Color.White.copy(alpha = .16f * facing), specRadius, specCenter)
}

private fun DrawScope.drawHeadsMonogram(camera: TrueCoinCamera, pose: TrueCoinPose, z: Float) {
    val ink = Color(0xFF3A260D).copy(alpha = .82f)
    val width = max(2f, pose.radiusPx * .055f)
    fun line(ax: Float, ay: Float, bx: Float, by: Float) {
        drawLine(
            ink,
            projectLocal(camera, pose, V3(ax, ay, z)),
            projectLocal(camera, pose, V3(bx, by, z)),
            width,
            StrokeCap.Round
        )
    }
    line(-.22f, -.28f, -.22f, .28f)
    line(.22f, -.28f, .22f, .28f)
    line(-.22f, 0f, .22f, 0f)
}

private fun DrawScope.drawTailsMonogram(camera: TrueCoinCamera, pose: TrueCoinPose, z: Float) {
    val ink = Color(0xFF28164F).copy(alpha = .84f)
    val width = max(2f, pose.radiusPx * .055f)
    drawLine(
        ink,
        projectLocal(camera, pose, V3(-.28f, -.26f, z)),
        projectLocal(camera, pose, V3(.28f, -.26f, z)),
        width,
        StrokeCap.Round
    )
    drawLine(
        ink,
        projectLocal(camera, pose, V3(0f, -.26f, z)),
        projectLocal(camera, pose, V3(0f, .30f, z)),
        width,
        StrokeCap.Round
    )
}

private fun DrawScope.drawProjectedRing(
    camera: TrueCoinCamera,
    pose: TrueCoinPose,
    z: Float,
    radius: Float,
    color: Color,
    width: Float
) {
    val points = (0 until 48).map { i ->
        val a = i / 48f * TWO_PI
        projectLocal(camera, pose, V3(cos(a) * radius, sin(a) * radius, z))
    }
    drawPath(polygonPath(points), color, style = Stroke(max(1f, width)))
}

private fun transformCoinPoint(local: V3, pose: TrueCoinPose): V3 {
    val scaled = local * pose.radiusPx
    return rotateVector(scaled, pose.rotX, pose.rotY, pose.rotZ) + pose.center
}

private fun projectLocal(camera: TrueCoinCamera, pose: TrueCoinPose, local: V3): Offset =
    camera.project(transformCoinPoint(local, pose))

private fun rotateVector(v: V3, rx: Float, ry: Float, rz: Float): V3 {
    val cx = cos(rx); val sx = sin(rx)
    val cy = cos(ry); val sy = sin(ry)
    val cz = cos(rz); val sz = sin(rz)

    val x1 = v.x
    val y1 = v.y * cx - v.z * sx
    val z1 = v.y * sx + v.z * cx

    val x2 = x1 * cy + z1 * sy
    val y2 = y1
    val z2 = -x1 * sy + z1 * cy

    return V3(
        x = x2 * cz - y2 * sz,
        y = x2 * sz + y2 * cz,
        z = z2
    )
}

private fun polygonPath(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

private fun projectedBounds(points: List<Offset>): Pair<Offset, Offset> {
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

private fun dot(a: V3, b: V3): Float = a.x * b.x + a.y * b.y + a.z * b.z

private fun normalized(v: V3): V3 {
    val length = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(.0001f)
    return V3(v.x / length, v.y / length, v.z / length)
}

private fun shade(color: Color, factor: Float): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun distance(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3f)
private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private const val DEG = (PI / 180.0).toFloat()
private const val TWO_PI = (PI * 2.0).toFloat()

@Composable
private fun TrueCoinControls(
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
            TrueCoinSideChoice(
                side = CoinflipEngine.Side.HEADS,
                selected = pick == CoinflipEngine.Side.HEADS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.HEADS) },
                modifier = Modifier.weight(1f)
            )
            TrueCoinSideChoice(
                side = CoinflipEngine.Side.TAILS,
                selected = pick == CoinflipEngine.Side.TAILS,
                enabled = !flipping,
                onClick = { onPick(CoinflipEngine.Side.TAILS) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TrueModePill("SINGLE", !seriesMode && !seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) {
                onSeriesMode(false)
            }
            TrueModePill("DOUBLE OR NOTHING", seriesMode || seriesActive, !flipping && !seriesActive, Modifier.weight(1f)) {
                onSeriesMode(true)
            }
        }

        QuickStakeRow(enabled = !flipping && !seriesActive, current = stake, onPick = onStake)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumStakeField(
                value = stake,
                enabled = !flipping && !seriesActive,
                accent = True3dMint,
                modifier = Modifier.weight(1f),
                onValueChange = onStake
            )
            PremiumActionButton(
                text = when {
                    flipping -> "IN FLIGHT…"
                    seriesActive -> "FLIP AGAIN"
                    seriesMode -> "START + FLIP"
                    else -> "TOSS · 1.98x"
                },
                accent = True3dMint,
                enabled = !flipping,
                modifier = Modifier.weight(.82f),
                onClick = onFlip
            )
        }

        if (seriesActive) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = True3dPanelHi,
                border = BorderStroke(1.dp, True3dGold.copy(alpha = .18f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SERIES BANK", fontSize = 7.sp, color = Color.White.copy(alpha = .34f), fontWeight = FontWeight.Black)
                        Text("${"%.2f".format(seriesMultiplier)}x", color = True3dGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text("STREAK $streak", color = Color.White.copy(alpha = .50f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    PremiumActionButton(
                        text = "CASH OUT",
                        accent = True3dGold,
                        enabled = !flipping && streak > 0,
                        onClick = onCashOut
                    )
                }
            }
        }

        PremiumMessageCard(message, True3dMint)
    }
}

@Composable
private fun TrueCoinSideChoice(
    side: CoinflipEngine.Side,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (side == CoinflipEngine.Side.HEADS) True3dGold else True3dViolet
    Surface(
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = .13f) else True3dPanel,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .58f) else Color.White.copy(alpha = .06f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(29.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = .72f), accent, accent.copy(alpha = .50f))),
                    radius = size.minDimension * .48f,
                    center = c
                )
                drawCircle(Color.Black.copy(alpha = .24f), size.minDimension * .40f, c, style = Stroke(1.5f))
                val ink = if (side == CoinflipEngine.Side.HEADS) Color(0xFF3A260D) else Color(0xFF28164F)
                if (side == CoinflipEngine.Side.HEADS) {
                    drawLine(ink, Offset(c.x - 4f, c.y - 6f), Offset(c.x - 4f, c.y + 6f), 2.5f, StrokeCap.Round)
                    drawLine(ink, Offset(c.x + 4f, c.y - 6f), Offset(c.x + 4f, c.y + 6f), 2.5f, StrokeCap.Round)
                    drawLine(ink, Offset(c.x - 4f, c.y), Offset(c.x + 4f, c.y), 2.5f, StrokeCap.Round)
                } else {
                    drawLine(ink, Offset(c.x - 6f, c.y - 5f), Offset(c.x + 6f, c.y - 5f), 2.5f, StrokeCap.Round)
                    drawLine(ink, Offset(c.x, c.y - 5f), Offset(c.x, c.y + 6f), 2.5f, StrokeCap.Round)
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
private fun TrueModePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = .085f) else True3dPanel,
        border = BorderStroke(1.dp, if (selected) True3dMint.copy(alpha = .34f) else Color.White.copy(alpha = .05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = selected, label = "true3d-mode") { active ->
                Text(
                    label,
                    color = if (active) Color.White.copy(alpha = .84f) else Color.White.copy(alpha = .38f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
