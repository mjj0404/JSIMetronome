package com.jsi.metronome.ui.metronome

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_SWING_DEGREES = 25f
private const val MIN_BPM = 30
private const val MAX_BPM = 250

// Weight position: 0 = close to pivot (fastest), 1 = far from pivot (slowest)
private const val WEIGHT_MIN_FRACTION = 0.15f
private const val WEIGHT_MAX_FRACTION = 0.70f

@Composable
fun MechanicalMetronomeView(
    bpm: Int,
    isPlaying: Boolean,
    tickTimeMs: Long,
    tickCount: Long,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val outline = MaterialTheme.colorScheme.outline

    val periodMs = 60_000f / bpm

    // Tick-synced animation
    var swingAngle by remember { mutableFloatStateOf(0f) }
    val currentTickTimeMs by rememberUpdatedState(tickTimeMs)
    val currentTickCount by rememberUpdatedState(tickCount)
    val currentPeriodMs by rememberUpdatedState(periodMs)

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            swingAngle = 0f
            return@LaunchedEffect
        }
        while (true) {
            withInfiniteAnimationFrameMillis { _ ->
                val lastTick = currentTickTimeMs
                if (lastTick > 0L) {
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastTick).toFloat()
                    val phase = (elapsed / currentPeriodMs).coerceIn(0f, 1f)
                    // sin(phase * π) → 0 at tick (center) → 1 at midpoint (max swing) → 0 at next tick
                    val direction = if (currentTickCount % 2 == 0L) 1f else -1f
                    swingAngle = sin(phase * PI.toFloat()) * MAX_SWING_DEGREES * direction
                }
            }
        }
    }

    val weightFraction = bpmToWeightFraction(bpm)
    val currentWeightFraction by rememberUpdatedState(weightFraction)
    val currentBpm by rememberUpdatedState(bpm)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val labelSizeSp = 9.sp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // BPM display
        Text(
            text = "$bpm",
            style = MaterialTheme.typography.displayMedium,
            color = onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "BPM",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            letterSpacing = 2.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mechanical metronome canvas
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .weight(1f),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                                val armLength = size.height * 0.65f
                                val weightRange =
                                    (WEIGHT_MAX_FRACTION - WEIGHT_MIN_FRACTION) * armLength
                                val fractionDelta = -dragAccumulator / weightRange
                                val newFraction = (currentWeightFraction + fractionDelta)
                                    .coerceIn(WEIGHT_MIN_FRACTION, WEIGHT_MAX_FRACTION)
                                val newBpm = weightFractionToBpm(newFraction)
                                if (newBpm != currentBpm) {
                                    onBpmChange(newBpm)
                                    dragAccumulator = 0f
                                }
                            },
                        )
                    },
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val centerX = canvasW / 2f

                // --- Body (trapezoid: narrow top, wide bottom) ---
                val bodyTop = canvasH * 0.22f
                val bodyBottom = canvasH * 0.97f
                val bodyHeight = bodyBottom - bodyTop
                val bodyTopHalfW = canvasW * 0.19f
                val bodyBottomHalfW = canvasW * 0.40f

                val bodyPath = Path().apply {
                    moveTo(centerX - bodyTopHalfW, bodyTop)
                    lineTo(centerX + bodyTopHalfW, bodyTop)
                    lineTo(centerX + bodyBottomHalfW, bodyBottom)
                    lineTo(centerX - bodyBottomHalfW, bodyBottom)
                    close()
                }
                drawPath(bodyPath, color = surfaceVariant)
                drawPath(
                    bodyPath,
                    color = outline.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // --- Face plate (inset trapezoid) ---
                val inset = 7.dp.toPx()
                val faceTop = bodyTop + inset
                val faceBottom = bodyBottom - inset
                fun halfWidthAt(y: Float): Float {
                    val ratio = (y - bodyTop) / bodyHeight
                    return bodyTopHalfW + (bodyBottomHalfW - bodyTopHalfW) * ratio
                }

                val faceTopHalfW = halfWidthAt(faceTop) - inset
                val faceBottomHalfW = halfWidthAt(faceBottom) - inset

                val facePath = Path().apply {
                    moveTo(centerX - faceTopHalfW, faceTop)
                    lineTo(centerX + faceTopHalfW, faceTop)
                    lineTo(centerX + faceBottomHalfW, faceBottom)
                    lineTo(centerX - faceBottomHalfW, faceBottom)
                    close()
                }
                drawPath(facePath, color = surfaceContainer)

                // --- Pivot (near bottom of body) ---
                val pivotY = bodyBottom - bodyHeight * 0.13f

                // --- Arm geometry ---
                val armAboveLen = canvasH * 0.68f
                val armBelowLen = canvasH * 0.04f

                // Swing angle (tick-synced)
                val angle = if (isPlaying) swingAngle else 0f
                val angleRad = Math.toRadians(angle.toDouble())
                val sinA = sin(angleRad).toFloat()
                val cosA = cos(angleRad).toFloat()

                // Arm endpoints
                val armTopX = centerX + armAboveLen * sinA
                val armTopY = pivotY - armAboveLen * cosA
                val armBotX = centerX - armBelowLen * sinA
                val armBotY = pivotY + armBelowLen * cosA

                // --- BPM graduation marks ---
                val labelPaint = android.graphics.Paint().apply {
                    textSize = labelSizeSp.toPx()
                    color = android.graphics.Color.argb(
                        (0.55f * 255).toInt(),
                        ((onSurfaceVariant.red) * 255).toInt(),
                        ((onSurfaceVariant.green) * 255).toInt(),
                        ((onSurfaceVariant.blue) * 255).toInt(),
                    )
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.NORMAL,
                    )
                }
                for (markBpm in 40..240 step 10) {
                    val fraction = bpmToWeightFraction(markBpm)
                    val markY = pivotY - fraction * armAboveLen
                    if (markY < faceTop + inset * 1.5f || markY > pivotY - inset) continue

                    val hw = halfWidthAt(markY) - inset * 1.5f
                    val isMajor = markBpm % 20 == 0
                    val tickHalf = if (isMajor) hw * 0.38f else hw * 0.20f
                    val alpha = if (isMajor) 0.30f else 0.12f

                    drawLine(
                        color = onSurfaceVariant.copy(alpha = alpha),
                        start = Offset(centerX - tickHalf, markY),
                        end = Offset(centerX + tickHalf, markY),
                        strokeWidth = if (isMajor) 1.dp.toPx() else 0.5.dp.toPx(),
                    )

                    if (isMajor) {
                        val labelText = "$markBpm"
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            centerX + tickHalf + 4.dp.toPx(),
                            markY + labelPaint.textSize / 3f,
                            labelPaint,
                        )
                    }
                }

                // --- Arm (rod) ---
                drawLine(
                    color = onSurface.copy(alpha = 0.65f),
                    start = Offset(armBotX, armBotY),
                    end = Offset(armTopX, armTopY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // --- Weight block on arm ---
                val weightDist = weightFraction * armAboveLen
                val weightCX = centerX + weightDist * sinA
                val weightCY = pivotY - weightDist * cosA
                val weightW = 28.dp.toPx()
                val weightH = 16.dp.toPx()

                rotate(degrees = angle, pivot = Offset(weightCX, weightCY)) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(
                            weightCX - weightW / 2f,
                            weightCY - weightH / 2f,
                        ),
                        size = Size(weightW, weightH),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    // Highlight line on weight
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(weightCX - weightW * 0.3f, weightCY),
                        end = Offset(weightCX + weightW * 0.3f, weightCY),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // --- Arm tip (small decorative end) ---
                drawCircle(
                    color = onSurface.copy(alpha = 0.5f),
                    radius = 3.dp.toPx(),
                    center = Offset(armTopX, armTopY),
                )

                // --- Counterweight bob below pivot ---
                drawCircle(
                    color = accentColor.copy(alpha = 0.6f),
                    radius = 5.dp.toPx(),
                    center = Offset(armBotX, armBotY),
                )

                // --- Pivot point (drawn on top) ---
                drawCircle(
                    color = onSurfaceVariant,
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, pivotY),
                )
                drawCircle(
                    color = surfaceVariant,
                    radius = 2.5.dp.toPx(),
                    center = Offset(centerX, pivotY),
                )
            }
        }
    }
}

/** Higher BPM = weight closer to pivot = lower fraction. */
private fun bpmToWeightFraction(bpm: Int): Float {
    val t = (bpm - MIN_BPM).toFloat() / (MAX_BPM - MIN_BPM)
    return WEIGHT_MAX_FRACTION - t * (WEIGHT_MAX_FRACTION - WEIGHT_MIN_FRACTION)
}

/** Closer to pivot = higher BPM. */
private fun weightFractionToBpm(fraction: Float): Int {
    val t = (WEIGHT_MAX_FRACTION - fraction) / (WEIGHT_MAX_FRACTION - WEIGHT_MIN_FRACTION)
    return (MIN_BPM + t * (MAX_BPM - MIN_BPM)).roundToInt().coerceIn(MIN_BPM, MAX_BPM)
}
