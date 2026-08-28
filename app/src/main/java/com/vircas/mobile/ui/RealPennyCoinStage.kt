package com.vircas.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.CoinflipEngine
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

private val PennyStageMint = Color(0xFF5BF0AA)
private val PennyStageRed = Color(0xFFFF5E72)
private val PennyStageCopper = Color(0xFFD98B52)
private val PennyStagePanel = Color(0xFF07100D)

/**
 * Real Filament/glTF stage for Coin Flip.
 *
 * The penny itself is not procedurally drawn. It is the CC BY 4.0 Yanez Designs
 * "Penny (Coin)" glTF bundled under assets/models/penny at build time.
 *
 * landingX and landingDepth are continuous normalized random values generated once per toss.
 * They only affect presentation. CoinflipEngine still locks the game result before animation.
 */
@Composable
internal fun RealPennyCoinStage(
    modifier: Modifier,
    rotationDegrees: Float,
    tossProgress: Float,
    impact: Float,
    landingX: Float,
    landingDepth: Float,
    landingYawDeg: Float,
    landingRollDeg: Float,
    curve: Float,
    swerve: Float,
    flipping: Boolean,
    revealedSide: CoinflipEngine.Side?,
    lastWon: Boolean?,
    seriesMultiplier: Double?,
    streak: Int
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val coinNode = rememberNode {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(
                assetFileLocation = "models/penny/scene.gltf"
            ),
            // Keep a constant physical world size. The dramatic size change during the toss
            // comes from perspective as the coin approaches the camera, not from 2D scaling.
            scaleToUnits = 1.18f
        ).apply {
            isTouchable = false
            isEditable = false
        }
    }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0f, y = 1.72f, z = 5.15f)
        lookAt(Position(x = 0f, y = -0.62f, z = 0.02f))
    }

    val mainLight = rememberMainLightNode(engine) {
        intensity = 125_000.0f
    }

    val p = when {
        flipping -> tossProgress.coerceIn(0f, 1f)
        revealedSide != null -> 1f
        else -> 0f
    }

    val pose = pennyWorldPose(
        progress = p,
        rotationDegrees = rotationDegrees,
        impact = impact,
        landingX = landingX,
        landingDepth = landingDepth,
        landingYawDeg = landingYawDeg,
        landingRollDeg = landingRollDeg,
        curve = curve,
        swerve = swerve,
        idle = !flipping && revealedSide == null
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF10221A), PennyStagePanel, Color(0xFF030705))
            ),
            RoundedCornerShape(28.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        PennyTableBackdrop(
            modifier = Modifier.fillMaxSize(),
            landingX = landingX,
            landingDepth = landingDepth,
            falling = if (p <= PENNY_NEAR_SPLIT) 0f else ((p - PENNY_NEAR_SPLIT) / (1f - PENNY_NEAR_SPLIT)).coerceIn(0f, 1f),
            flipping = flipping
        )

        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            isOpaque = false,
            mainLightNode = mainLight,
            cameraNode = cameraNode,
            childNodes = listOf(coinNode),
            cameraManipulator = null,
            onGestureListener = null,
            onTouchEvent = { _, _ -> false },
            onFrame = {
                coinNode.position = Position(pose.x, pose.y, pose.z)
                coinNode.rotation = Rotation(
                    x = pose.rotX,
                    y = pose.rotY,
                    z = pose.rotZ
                )
                // The camera is deliberately fixed so apparent scaling is true perspective.
                cameraNode.lookAt(Position(x = 0f, y = -0.62f, z = 0.02f))
            }
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (seriesMultiplier != null) {
                Text(
                    "DOUBLE OR NOTHING",
                    color = Color.White.copy(alpha = .45f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "${"%.2f".format(seriesMultiplier)}x",
                    color = PennyStageMint,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "STREAK $streak",
                    color = Color.White.copy(alpha = .36f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                Text(
                    when {
                        flipping && p < PENNY_NEAR_SPLIT -> "COMING AT YOU"
                        flipping -> "FREE LANDING"
                        else -> "REAL PENNY · 50 / 50 · 1.98x"
                    },
                    color = Color.White.copy(alpha = .50f),
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
            val accent = if (won) PennyStageMint else PennyStageRed
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = .11f),
                border = BorderStroke(1.dp, accent.copy(alpha = .40f))
            ) {
                Text(
                    "${revealedSide?.name.orEmpty()} · ${if (won) "WIN" else "LOSS"}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .6.sp
                )
            }
        }
    }
}

private data class PennyWorldPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float
)

private fun pennyWorldPose(
    progress: Float,
    rotationDegrees: Float,
    impact: Float,
    landingX: Float,
    landingDepth: Float,
    landingYawDeg: Float,
    landingRollDeg: Float,
    curve: Float,
    swerve: Float,
    idle: Boolean
): PennyWorldPose {
    if (idle) {
        return PennyWorldPose(
            x = 0f,
            y = -.18f,
            z = -.38f,
            rotX = 18f,
            rotY = -9f,
            rotZ = -8f
        )
    }

    // The allowed landing area is a rectangle on the visible tabletop. Values remain continuous;
    // there are no slots, buckets or hand-authored landing points.
    val finalX = landingX.coerceIn(-1f, 1f) * 1.34f
    val finalZ = -1.00f + landingDepth.coerceIn(0f, 1f) * 1.95f
    val floorY = -.91f

    val x: Float
    val y: Float
    val z: Float
    val fall: Float

    if (progress <= PENNY_NEAR_SPLIT) {
        val q = (progress / PENNY_NEAR_SPLIT).coerceIn(0f, 1f)
        val e = 1f - (1f - q).pow(3f)
        // Start well behind the focal area, then physically move toward the camera.
        x = curve * .22f * sin(q * PI.toFloat())
        y = -.22f + sin(q * PI.toFloat()) * 1.22f
        z = -1.46f + 3.45f * e
        fall = 0f
    } else {
        val q = ((progress - PENNY_NEAR_SPLIT) / (1f - PENNY_NEAR_SPLIT)).coerceIn(0f, 1f)
        val s = q * q * (3f - 2f * q)
        val nearX = curve * .22f
        x = lerpPenny(nearX, finalX, s) + swerve * .34f * sin(q * PI.toFloat())
        // Gravity-like fall after the closest point. The impact animation adds a very small rebound.
        y = lerpPenny(1.00f, floorY, q.pow(1.58f)) + abs(impact) * .075f
        z = lerpPenny(1.99f, finalZ, s)
        fall = s
    }

    // The imported penny's disc is in its XY plane. +90 degrees lays it on the XZ tabletop.
    // rotationDegrees already terminates 180 degrees apart for HEADS vs TAILS.
    val landingTilt = landingRollDeg.coerceIn(-5.5f, 5.5f)
    val rotX = rotationDegrees + 90f + impact * 7.5f
    val rotY = landingTilt * fall + sin(progress * PI.toFloat() * 2f) * 5.5f * (1f - fall)
    val rotZ = landingYawDeg * fall + sin(progress * PI.toFloat()) * 8f

    return PennyWorldPose(x, y, z, rotX, rotY, rotZ)
}

@Composable
private fun PennyTableBackdrop(
    modifier: Modifier,
    landingX: Float,
    landingDepth: Float,
    falling: Float,
    flipping: Boolean
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * .44f

        drawRect(
            Brush.radialGradient(
                colors = listOf(PennyStageCopper.copy(alpha = .08f), Color.Transparent),
                center = Offset(w * .5f, h * .34f),
                radius = size.minDimension * .74f
            )
        )

        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF173326).copy(alpha = .88f), Color(0xFF07120D).copy(alpha = .98f)),
                startY = horizon,
                endY = h
            ),
            topLeft = Offset(w * .035f, h * .43f),
            size = androidx.compose.ui.geometry.Size(w * .93f, h * .54f)
        )
        drawOval(
            color = Color.White.copy(alpha = .055f),
            topLeft = Offset(w * .035f, h * .43f),
            size = androidx.compose.ui.geometry.Size(w * .93f, h * .54f),
            style = Stroke(1.3f)
        )

        repeat(9) { i ->
            val t = i / 8f
            drawLine(
                PennyStageMint.copy(alpha = .025f),
                start = Offset(w * (.5f + (t - .5f) * .12f), horizon),
                end = Offset(w * (.04f + t * .92f), h * .96f),
                strokeWidth = 1f
            )
        }
        repeat(7) { i ->
            val t = (i + 1) / 8f
            val d = t * t
            val y = horizon + (h * .50f) * d
            val half = w * (.055f + .43f * d)
            drawLine(
                Color.White.copy(alpha = .022f),
                start = Offset(w * .5f - half, y),
                end = Offset(w * .5f + half, y),
                strokeWidth = 1f
            )
        }

        // This marker is only a subtle shadow/landing cue. Its coordinate is calculated directly
        // from the same continuous random values as the 3D target, never from preset positions.
        if (flipping && falling > .18f) {
            val sx = w * (.5f + landingX.coerceIn(-1f, 1f) * .29f)
            val sy = h * (.69f + landingDepth.coerceIn(0f, 1f) * .15f)
            drawOval(
                color = Color.Black.copy(alpha = .10f * falling),
                topLeft = Offset(sx - w * .065f, sy - h * .015f),
                size = androidx.compose.ui.geometry.Size(w * .13f, h * .03f)
            )
        }
    }
}

private fun lerpPenny(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private const val PENNY_NEAR_SPLIT = .56f
