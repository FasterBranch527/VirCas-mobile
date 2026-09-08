package com.vircas.mobile.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val RouletteBrass = Color(0xFFDAB97A)
private val RouletteIvory = Color(0xFFF5EBD6)

/** High-frequency frame state is read in Canvas, not by the betting-table composition. */
@Composable
internal fun RouletteWheelPanel(
    motion: State<RouletteMotionFrame>,
    result: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val phase by remember(motion) { derivedStateOf { motion.value.phase } }
    val label = rouletteWheelPhaseLabel(phase, result)
    val resultVisible = phase == RouletteSpinPhase.SETTLED || phase == RouletteSpinPhase.IDLE
    Surface(
        modifier = modifier.testTag("roulette-wheel-panel").semantics {
            contentDescription = "European roulette. $label"
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0B1413),
        border = BorderStroke(1.dp, RouletteBrass.copy(alpha = .20f))
    ) {
        Box(Modifier.fillMaxSize().padding(if (compact) 6.dp else 12.dp)) {
            RealRouletteWheel(motion, if (resultVisible) result else null, Modifier.fillMaxSize().padding(bottom = 28.dp))
            Text(
                text = label,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 4.dp),
                color = if (resultVisible && result == 0) Color(0xFF91E2B8) else RouletteIvory,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// Preserve the previous call surface for other previews/presentations.
@Composable
internal fun RouletteWheelPanel(
    wheelRotation: Float, ballRotation: Float, ballRadius: Float, ballHop: Float,
    ballDrop: Float, phase: RouletteSpinPhase, result: Int?,
    modifier: Modifier = Modifier, compact: Boolean = false
) {
    val motion = rememberUpdatedState(RouletteMotionFrame(wheelRotation, ballRotation, ballRadius, ballHop, ballDrop, phase = phase))
    RouletteWheelPanel(motion, result, modifier, compact)
}

private fun rouletteWheelPhaseLabel(phase: RouletteSpinPhase, result: Int?): String = when (phase) {
    RouletteSpinPhase.WHEEL_AND_BALL -> "WHEEL IN MOTION"
    RouletteSpinPhase.BALL_COAST -> "WHEEL STOPPED · BALL COASTING"
    RouletteSpinPhase.POCKET_BOUNCE -> "BALL FINDING ITS POCKET"
    RouletteSpinPhase.BALL_DROP -> "BALL SETTLING"
    RouletteSpinPhase.SETTLED, RouletteSpinPhase.IDLE -> result?.let { "$it · ${RouletteEngine.colorOf(it).name}" } ?: "PLACE YOUR BETS"
}

@Composable
private fun RealRouletteWheel(motion: State<RouletteMotionFrame>, result: Int?, modifier: Modifier) {
    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(250, 243, 222)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
    }
    val labels = remember { rouletteWheelOrder.map(Int::toString) }
    val deflector = remember { Path() }
    Canvas(modifier.testTag("roulette-wheel-canvas")) {
        if (size.minDimension <= 0f) return@Canvas
        val frame = motion.value
        val c = center
        val r = size.minDimension * .465f
        val sweep = 360f / rouletteWheelOrder.size
        fun point(degrees: Float, radial: Float) = roulettePoint(c, r * radial, degrees)
        val light = c - Offset(r * .42f, r * .55f)

        // A fixed bowl, separate from the rotating numbered rotor.
        drawCircle(Brush.radialGradient(listOf(Color(0x99000000), Color.Transparent), c, r * 1.075f), r * 1.075f, c)
        drawCircle(Brush.linearGradient(listOf(Color(0xFFFAE2AA), Color(0xFF725235), Color(0xFFE0BA79), Color(0xFF3D2B1D)), c - Offset(r, r), c + Offset(r, r)), r * 1.015f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF713F28), Color(0xFF261B17), Color(0xFF513321)), light, r * 1.75f), r * .985f, c)
        repeat(5) { i ->
            drawCircle(Color(0xFFBF8351).copy(alpha = .08f), r * (.945f + i * .007f), c, style = Stroke(r * .002f))
        }
        drawCircle(Color(0xFFDCBD86), r * .935f, c, style = Stroke(r * .010f))
        drawCircle(Brush.radialGradient(0f to Color(0xFF15221F), .83f to Color(0xFF101918), .92f to Color(0xFF293330), 1f to Color(0xFF080E0D), center = c, radius = r * .926f), r * .926f, c)
        drawCircle(Color(0xFF889B90).copy(alpha = .32f), r * .908f, c, style = Stroke(r * .006f))
        drawCircle(Color.Black.copy(alpha = .65f), r * .818f, c, style = Stroke(r * .018f))
        // Diamond deflectors stay fixed to the bowl while the ball and rotor counter-rotate.
        repeat(8) { index ->
            val p = point(index * 45f + 22.5f, .850f)
            rotate(index * 45f + 22.5f, p) {
                deflector.reset()
                deflector.moveTo(p.x - r * .025f, p.y)
                deflector.lineTo(p.x, p.y - r * .012f)
                deflector.lineTo(p.x + r * .025f, p.y)
                deflector.lineTo(p.x, p.y + r * .012f)
                deflector.close()
                drawPath(deflector, Color(0xFFE2C899))
                drawLine(Color(0xFF766446), p - Offset(r * .024f, 0f), p + Offset(r * .024f, 0f), r * .003f)
            }
        }

        rotate(frame.wheelRotation % 360f, c) {
            drawCircle(Color(0xFF090E0D), r * .807f, c)
            rouletteWheelOrder.forEachIndexed { index, number ->
                val start = -90f + index * sweep
                val color = when (RouletteEngine.colorOf(number)) {
                    RouletteColor.RED -> Color(0xFFAE303E)
                    RouletteColor.BLACK -> Color(0xFF182422)
                    RouletteColor.GREEN -> Color(0xFF228462)
                }
                // Number ring and recessed pockets are distinct: the ball never covers a numeral.
                rouletteRingSegment(c, r, .651f, .793f, start + .25f, sweep - .5f, color)
                rouletteRingSegment(c, r, .487f, .640f, start + .6f, sweep - 1.2f, color.copy(alpha = .75f))
                rouletteRingSegment(c, r, .487f, .525f, start + .6f, sweep - 1.2f, Color.Black.copy(alpha = .35f))
                val dividerAngle = start
                drawLine(Color(0xFFCAA875), point(dividerAngle, .488f), point(dividerAngle, .641f), r * .006f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = .22f), point(dividerAngle + .35f, .500f), point(dividerAngle + .35f, .633f), r * .002f)
                if (number == result) {
                    rouletteRingSegment(c, r, .489f, .790f, start + .8f, sweep - 1.6f, RouletteBrass.copy(alpha = .22f))
                    drawCircle(RouletteBrass.copy(alpha = .35f), r * .035f, point(start + sweep / 2f, .561f), style = Stroke(r * .005f))
                }
            }
            drawCircle(RouletteBrass.copy(alpha = .75f), r * .797f, c, style = Stroke(r * .009f))
            drawCircle(RouletteBrass.copy(alpha = .80f), r * .646f, c, style = Stroke(r * .007f))
            drawCircle(Color(0xFFF0D5A2), r * .483f, c, style = Stroke(r * .010f))
            numberPaint.textSize = r * .079f
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                labels.forEachIndexed { index, label ->
                    val angle = -90f + (index + .5f) * sweep
                    val p = point(angle, .727f)
                    native.save()
                    native.rotate(angle + 90f, p.x, p.y)
                    native.drawText(label, p.x, p.y - (numberPaint.ascent() + numberPaint.descent()) / 2f, numberPaint)
                    native.restore()
                }
            }
            // Polished cone and spindle turn with the rotor, not with the fixed bowl.
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB38855), Color(0xFF513D28), Color(0xFF202822)), c, r * .476f), r * .476f, c)
            repeat(3) { i -> drawCircle(Color(0xFFE1C89C).copy(alpha = .12f), r * (.22f + i * .083f), c, style = Stroke(r * .003f)) }
            repeat(4) { index ->
                val end = point(index * 90f, .265f)
                drawLine(Color.Black.copy(alpha = .4f), c + Offset(0f, r * .017f), end + Offset(0f, r * .017f), r * .046f, StrokeCap.Round)
                drawLine(Color(0xFFAA8555), c, end, r * .034f, StrokeCap.Round)
                drawLine(Color(0xFFF4DCAA), c - Offset(0f, r * .007f), end - Offset(0f, r * .007f), r * .010f, StrokeCap.Round)
                drawCircle(Color(0xFFE2C58D), r * .023f, end)
            }
            drawCircle(Color(0xFF282C24), r * .105f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFEAC1), Color(0xFFBF975D), Color(0xFF6B583D)), c - Offset(r * .03f, r * .035f), r * .135f), r * .085f, c)
        }
        // Fixed soft reflections establish material without rotating the light with the wheel.
        drawArc(Color(0xFFFCE8BB).copy(alpha = .40f), 208f, 67f, false, c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(r * .008f, cap = StrokeCap.Round))
        val blur = (abs(frame.wheelSpeed) / 1000f).coerceIn(0f, 1f)
        if (blur > .01f) drawCircle(RouletteBrass.copy(alpha = blur * .10f), r * .794f, c, style = Stroke(r * .023f))

        val radial = frame.ballRadius + frame.ballHop * .018f
        val ground = point(frame.ballRotation, radial)
        val ball = ground - Offset(0f, frame.ballHop * r * .045f)
        val ballSize = r * .028f * (1f + frame.ballHop * .10f - frame.ballDrop * .05f)
        // Velocity-scaled shutter streak, not a string of equally opaque duplicate balls.
        val trail = (abs(frame.ballSpeed) * .018f).coerceAtMost(19f)
        if (trail > .5f) {
            repeat(5) { i ->
                val a = (i + 1) / 5f
                drawCircle(RouletteIvory.copy(alpha = (1f - a) * .14f), ballSize * (1f - a * .6f), point(frame.ballRotation + a * trail, radial))
            }
        }
        val shadow = ground + Offset(r * .012f, r * .013f)
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .65f - frame.ballHop * .25f), Color.Transparent), shadow, ballSize * (1.65f + frame.ballHop)), ballSize * (1.65f + frame.ballHop), shadow)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF8EFD9), Color(0xFFADA18B)), ball - Offset(ballSize * .33f, ballSize * .4f), ballSize * 1.65f), ballSize, ball)
        drawCircle(Color.White.copy(alpha = .95f), ballSize * .22f, ball - Offset(ballSize * .3f, ballSize * .36f))
    }
}

private fun roulettePoint(center: Offset, radius: Float, degrees: Float): Offset {
    val angle = degrees.toDouble() * PI / 180.0
    return center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
}

private fun DrawScope.rouletteRingSegment(center: Offset, radius: Float, inner: Float, outer: Float, start: Float, sweep: Float, color: Color) {
    val middle = radius * (inner + outer) / 2f
    drawArc(color, start, sweep, false, center - Offset(middle, middle), Size(middle * 2f, middle * 2f), style = Stroke(radius * (outer - inner)))
}
