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
import androidx.compose.runtime.remember
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
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import kotlin.math.abs

private val PhysicsPennyMint = Color(0xFF5BF0AA)
private val PhysicsPennyRed = Color(0xFFFF5E72)
private val PhysicsPennyCopper = Color(0xFFD68A50)
private val PhysicsPennyPanel = Color(0xFF06100C)

internal data class PennyPresentationPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val scaleMultiplier: Float
)

/**
 * Visual camera-rush envelope. This affects rendering only: the rigid-body simulation and its
 * random landing coordinate stay untouched. The penny accelerates toward the player, peaks close
 * to the camera, then blends back onto the real simulated trajectory before the landing phase.
 */
internal fun pennyCameraRush(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p <= .07f -> 0f
        p < .34f -> smoothCameraRush((p - .07f) / .27f)
        p < .64f -> 1f - smoothCameraRush((p - .34f) / .30f)
        else -> 0f
    }
}

internal fun pennyPresentationPose(
    physical: RealPennyMotionFrame,
    progress: Float,
    flipping: Boolean
): PennyPresentationPose {
    if (!flipping) {
        return PennyPresentationPose(
            x = physical.x,
            y = physical.y,
            z = physical.z,
            scaleMultiplier = 1f
        )
    }

    val rush = pennyCameraRush(progress)
    return PennyPresentationPose(
        // Pull the coin toward the optical center while it is closest to the player so even wide
        // random throws still read as "thrown at you", not as a sideways table hop.
        x = physical.x * (1f - .55f * rush),
        y = physical.y + .08f * rush,
        // Z points toward the viewer in SceneView/Filament. This is deliberately a visual offset;
        // the simulated body underneath continues untouched and determines the eventual landing.
        z = physical.z + 2.45f * rush,
        // Perspective already makes the penny larger. Add an explicit punch so the throw is
        // unmistakable even on small/flat displays.
        scaleMultiplier = 1f + .90f * rush
    )
}

private fun smoothCameraRush(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * SceneView/Filament stage using Anthony Yanez's internet-sourced Lincoln penny glTF.
 * The final landing position comes exclusively from RealPennyPhysics. A short presentation-only
 * camera rush makes the throw come at the player without changing where the physical toss lands.
 */
@Composable
internal fun PhysicsPennyCoinStage(
    modifier: Modifier,
    motion: RealPennyMotion,
    tossProgress: Float,
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
            scaleToUnits = .90f
        ).apply {
            isTouchable = false
            isEditable = false
        }
    }
    // scaleToUnits computes the model-specific base scale once. Keep it so the camera-rush scale
    // can be applied as a multiplier instead of replacing the imported model's fitted scale.
    val fittedCoinScale = remember(coinNode) { coinNode.scale }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0f, y = 1.68f, z = 5.05f)
        lookAt(Position(x = 0f, y = -.58f, z = -.02f))
    }
    val mainLight = rememberMainLightNode(engine) {
        intensity = 118_000.0f
    }

    val progress = when {
        flipping -> tossProgress.coerceIn(0f, 1f)
        revealedSide != null -> 1f
        else -> 0f
    }
    val pose = if (!flipping && revealedSide == null) {
        RealPennyMotion.Idle.sample(0f)
    } else {
        motion.sample(progress)
    }
    val presentation = pennyPresentationPose(pose, progress, flipping)
    val rush = if (flipping) pennyCameraRush(progress) else 0f

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF10231A), PhysicsPennyPanel, Color(0xFF020604))
            ),
            RoundedCornerShape(28.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        PhysicsPennyTableBackdrop(
            modifier = Modifier.fillMaxSize(),
            pose = pose,
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
                coinNode.position = Position(
                    presentation.x,
                    presentation.y,
                    presentation.z
                )
                coinNode.rotation = Rotation(
                    x = pose.rotX,
                    y = pose.rotY,
                    z = pose.rotZ
                )
                val scale = presentation.scaleMultiplier
                coinNode.scale = Scale(
                    fittedCoinScale.x * scale,
                    fittedCoinScale.y * scale,
                    fittedCoinScale.z * scale
                )
                cameraNode.lookAt(Position(x = 0f, y = -.58f, z = -.02f))
            }
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
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
                    color = PhysicsPennyMint,
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
                        flipping && rush > .18f -> "TOSSED AT YOU"
                        flipping && pose.y > PENNY_FLOOR_Y + .12f -> "FREE FLIGHT"
                        flipping -> "BOUNCE + FRICTION"
                        else -> "REAL LINCOLN CENT · 50 / 50 · 1.98x"
                    },
                    color = Color.White.copy(alpha = .50f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .7.sp
                )
            }
        }

        AnimatedVisibility(
            visible = revealedSide != null && !flipping,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            val won = lastWon == true
            val accent = if (won) PhysicsPennyMint else PhysicsPennyRed
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

@Composable
private fun PhysicsPennyTableBackdrop(
    modifier: Modifier,
    pose: RealPennyMotionFrame,
    flipping: Boolean
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * .43f

        drawRect(
            Brush.radialGradient(
                colors = listOf(PhysicsPennyCopper.copy(alpha = .09f), Color.Transparent),
                center = Offset(w * .5f, h * .31f),
                radius = size.minDimension * .78f
            )
        )

        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF173628), Color(0xFF06110D)),
                startY = horizon,
                endY = h
            ),
            topLeft = Offset(w * .025f, h * .42f),
            size = androidx.compose.ui.geometry.Size(w * .95f, h * .56f)
        )
        drawOval(
            color = Color.White.copy(alpha = .055f),
            topLeft = Offset(w * .025f, h * .42f),
            size = androidx.compose.ui.geometry.Size(w * .95f, h * .56f),
            style = Stroke(1.2f)
        )

        repeat(9) { i ->
            val t = i / 8f
            drawLine(
                PhysicsPennyMint.copy(alpha = .024f),
                start = Offset(w * (.5f + (t - .5f) * .12f), horizon),
                end = Offset(w * (.025f + t * .95f), h * .97f),
                strokeWidth = 1f
            )
        }
        repeat(7) { i ->
            val t = (i + 1) / 8f
            val d = t * t
            val y = horizon + h * .52f * d
            val half = w * (.05f + .45f * d)
            drawLine(
                Color.White.copy(alpha = .022f),
                start = Offset(w * .5f - half, y),
                end = Offset(w * .5f + half, y),
                strokeWidth = 1f
            )
        }

        // Shadow stays bound to the real simulated body. The presentation rush never alters the
        // random physical landing coordinate underneath it.
        if (flipping || pose.grounded) {
            val xNorm = (pose.x / 1.45f).coerceIn(-1f, 1f)
            val zNorm = ((pose.z + 1.25f) / 2.40f).coerceIn(0f, 1f)
            val sx = w * (.5f + xNorm * .31f)
            val sy = h * (.67f + zNorm * .17f)
            val height = ((pose.y - PENNY_FLOOR_Y) / 1.9f).coerceIn(0f, 1f)
            val alpha = (.22f * (1f - height) + .035f).coerceIn(.035f, .25f)
            val width = w * (.075f - height * .024f)
            drawOval(
                color = Color.Black.copy(alpha = alpha),
                topLeft = Offset(sx - width, sy - h * .012f),
                size = androidx.compose.ui.geometry.Size(width * 2f, h * .024f)
            )
        }

        if (flipping && abs(pose.x) > 1.30f) {
            drawCircle(
                PhysicsPennyCopper.copy(alpha = .05f),
                radius = w * .09f,
                center = Offset(w * (.5f + (pose.x / 1.45f) * .30f), h * .75f)
            )
        }
    }
}
