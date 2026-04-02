package com.jsi.metronome.ui.metronome

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_BPM = 30
private const val MAX_BPM = 250
private const val START_ANGLE = 135f // 7:30 position
private const val SWEEP_ANGLE = 270f // to 4:30 position
private const val NUM_BALLS = 10

@Composable
fun DialView(
    bpm: Int,
    isPlaying: Boolean,
    tickTimeMs: Long,
    tickCount: Long,
    subdivision: Int,
    onBpmChange: (Int) -> Unit,
    onSubdivisionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var lastAngle by remember { mutableFloatStateOf(0f) }

    val periodMs = 60_000f / bpm

    // Bouncing ball animation state
    var activeBallIndex by remember { mutableIntStateOf(0) }
    val currentTickTimeMs by rememberUpdatedState(tickTimeMs)
    val currentTickCount by rememberUpdatedState(tickCount)
    val currentPeriodMs by rememberUpdatedState(periodMs)

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            activeBallIndex = 0
            return@LaunchedEffect
        }
        while (true) {
            withInfiniteAnimationFrameMillis { _ ->
                val lastTick = currentTickTimeMs
                if (lastTick > 0L) {
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastTick).toFloat()
                    val phase = (elapsed / currentPeriodMs).coerceIn(0f, 1f)
                    // Linear motion — constant speed, instant bounce off walls
                    val t = phase
                    // Alternate direction each tick
                    val goingRight = currentTickCount % 2 == 1L
                    val fraction = if (goingRight) t else 1f - t
                    activeBallIndex = (fraction * (NUM_BALLS - 1))
                        .roundToInt()
                        .coerceIn(0, NUM_BALLS - 1)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Bouncing ball track (above dial, full width) ---
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(40.dp),
        ) {
            if (isPlaying && tickTimeMs > 0L) {
                val ballRadius = 6.dp.toPx()
                val trackY = size.height / 2f
                val trackPadding = ballRadius * 3f
                val trackWidth = size.width - trackPadding * 2f
                val wallHeight = 16.dp.toPx()
                val wallWidth = 2.dp.toPx()

                // Left wall
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.4f),
                    start = Offset(trackPadding - ballRadius * 1.5f, trackY - wallHeight),
                    end = Offset(trackPadding - ballRadius * 1.5f, trackY + wallHeight),
                    strokeWidth = wallWidth,
                    cap = StrokeCap.Round,
                )

                // Right wall
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.4f),
                    start = Offset(trackPadding + trackWidth + ballRadius * 1.5f, trackY - wallHeight),
                    end = Offset(trackPadding + trackWidth + ballRadius * 1.5f, trackY + wallHeight),
                    strokeWidth = wallWidth,
                    cap = StrokeCap.Round,
                )

                // Ball positions and active ball
                for (i in 0 until NUM_BALLS) {
                    val frac = i.toFloat() / (NUM_BALLS - 1)
                    val bx = trackPadding + trackWidth * frac

                    if (i == activeBallIndex) {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.25f),
                            radius = ballRadius * 2.2f,
                            center = Offset(bx, trackY),
                        )
                        drawCircle(
                            color = accentColor,
                            radius = ballRadius,
                            center = Offset(bx, trackY),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = ballRadius * 0.35f,
                            center = Offset(bx - ballRadius * 0.2f, trackY - ballRadius * 0.25f),
                        )
                    } else {
                        drawCircle(
                            color = onSurfaceVariant.copy(alpha = 0.12f),
                            radius = ballRadius * 0.5f,
                            center = Offset(bx, trackY),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Dial ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                lastAngle = angleDeg(
                                    size.width / 2f, size.height / 2f,
                                    offset.x, offset.y,
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentAngle = angleDeg(
                                    size.width / 2f, size.height / 2f,
                                    change.position.x, change.position.y,
                                )
                                val normalized = normalizeAngle(currentAngle)
                                val fraction = normalized / SWEEP_ANGLE
                                val newBpm = (MIN_BPM + fraction * (MAX_BPM - MIN_BPM))
                                    .roundToInt()
                                    .coerceIn(MIN_BPM, MAX_BPM)
                                onBpmChange(newBpm)
                                lastAngle = currentAngle
                            }
                        )
                    }
            ) {
                val strokeWidth = 12.dp.toPx()
                val padding = 32.dp.toPx()
                val radius = (size.minDimension / 2f) - padding
                val center = Offset(size.width / 2f, size.height / 2f)

                // Track arc
                drawArc(
                    color = trackColor,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Progress arc
                val progress = (bpm - MIN_BPM).toFloat() / (MAX_BPM - MIN_BPM)
                val progressSweep = progress * SWEEP_ANGLE
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(accentColor.copy(alpha = 0.6f), accentColor),
                        center = center,
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = progressSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Tick marks every 10 BPM
                for (tickBpm in MIN_BPM..MAX_BPM step 10) {
                    val tickProgress = (tickBpm - MIN_BPM).toFloat() / (MAX_BPM - MIN_BPM)
                    val tickAngle = START_ANGLE + tickProgress * SWEEP_ANGLE
                    val angleRad = Math.toRadians(tickAngle.toDouble())
                    val isMajor = tickBpm % 50 == 0
                    val tickLength = if (isMajor) 14.dp.toPx() else 8.dp.toPx()
                    val innerR = radius - strokeWidth / 2 - 6.dp.toPx() - tickLength
                    val outerR = radius - strokeWidth / 2 - 6.dp.toPx()

                    drawLine(
                        color = if (tickBpm <= bpm) accentColor.copy(alpha = 0.8f)
                        else onSurfaceVariant.copy(alpha = 0.3f),
                        start = Offset(
                            center.x + innerR * cos(angleRad).toFloat(),
                            center.y + innerR * sin(angleRad).toFloat(),
                        ),
                        end = Offset(
                            center.x + outerR * cos(angleRad).toFloat(),
                            center.y + outerR * sin(angleRad).toFloat(),
                        ),
                        strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }

                // Thumb with glow
                val thumbAngle = Math.toRadians((START_ANGLE + progressSweep).toDouble())
                val thumbCenter = Offset(
                    center.x + radius * cos(thumbAngle).toFloat(),
                    center.y + radius * sin(thumbAngle).toFloat(),
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = 18.dp.toPx(),
                    center = thumbCenter,
                )
                drawCircle(
                    color = accentColor,
                    radius = 10.dp.toPx(),
                    center = thumbCenter,
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = thumbCenter,
                )
            }

            // Center BPM display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subdivision selector
        SubdivisionSelector(
            subdivision = subdivision,
            accentColor = accentColor,
            onSubdivisionChange = onSubdivisionChange,
        )
    }
}

@Composable
private fun SubdivisionSelector(
    subdivision: Int,
    accentColor: Color,
    onSubdivisionChange: (Int) -> Unit,
) {
    val values = (1..9).toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = subdivision - 1)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Sync scroll position → subdivision value
    val currentOnChange by rememberUpdatedState(onSubdivisionChange)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            val newValue = values[centerIndex]
            if (newValue != subdivision) {
                currentOnChange(newValue)
            }
        }
    }

    // Sync external subdivision changes → scroll position
    LaunchedEffect(subdivision) {
        val targetIndex = subdivision - 1
        if (listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "÷",
            style = MaterialTheme.typography.titleMedium,
            color = onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))

        val itemWidthDp = 36.dp

        Box(
            modifier = Modifier
                .width(itemWidthDp * 5) // show 5 items at a time
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = itemWidthDp * 2),
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(values) { index, value ->
                    val isSelected = value == subdivision
                    Box(
                        modifier = Modifier.width(itemWidthDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$value",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accentColor
                                else onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

/** Returns an angle in degrees where 0° is straight right, going clockwise. */
private fun angleDeg(cx: Float, cy: Float, x: Float, y: Float): Float {
    val angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    return if (angle < 0) angle + 360f else angle
}

/** Normalize an absolute angle to the dial's 0–SWEEP_ANGLE range. */
private fun normalizeAngle(absoluteAngle: Float): Float {
    var a = absoluteAngle - START_ANGLE
    if (a < 0) a += 360f
    return a.coerceIn(0f, SWEEP_ANGLE)
}
