package com.jsi.metronome.ui.pitchpipe

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private data class PitchNote(
    val name: String,
    val displayName: String,
    val frequency: Double,
    val isSharp: Boolean,
)

private val chromaticNotes: List<PitchNote> = run {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val enharmonicLabels = mapOf(
        "C#" to "C#/Db", "D#" to "D#/Eb", "F#" to "F#/Gb", "G#" to "G#/Ab", "A#" to "A#/Bb",
    )
    (36..60).map { midi ->
        val octave = midi / 12 - 1
        val noteName = noteNames[midi % 12]
        val isSharp = noteName.contains("#")
        val display = if (isSharp) {
            "${enharmonicLabels[noteName]}$octave"
        } else {
            "$noteName$octave"
        }
        val freq = 440.0 * 2.0.pow((midi - 69.0) / 12.0)
        PitchNote("$noteName$octave", display, freq, isSharp)
    }
}

private const val SAMPLE_RATE = 44100
private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

@Composable
fun PitchPipeScreen(
    durationSeconds: Int,
    modifier: Modifier = Modifier,
    onPlayNote: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val currentJobs = remember { mutableListOf<Job>() }
    val currentTracks = remember { mutableListOf<AudioTrack>() }

    var selectedIndex by remember { mutableIntStateOf(12) }
    var lastAngle by remember { mutableFloatStateOf(0f) }

    val selectedNote = chromaticNotes[selectedIndex]

    val accentColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    fun stopPlayback() {
        currentTracks.forEach { track ->
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
        currentTracks.clear()
        currentJobs.forEach { it.cancel() }
        currentJobs.clear()
    }

    fun playNote(note: PitchNote) {
        onPlayNote()
        stopPlayback()
        val job = scope.launch(Dispatchers.IO) {
            val track = playSineWave(note.frequency, durationSeconds.toDouble())
            currentTracks.add(track)
        }
        currentJobs.add(job)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentTracks.forEach { track ->
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
            currentJobs.forEach { it.cancel() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // --- Dial ---
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
        val naturalLabelStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

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
                                val newIndex = (fraction * (chromaticNotes.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, chromaticNotes.size - 1)
                                if (newIndex != selectedIndex) {
                                    selectedIndex = newIndex
                                }
                                lastAngle = currentAngle
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val tapped = angleDeg(cx, cy, offset.x, offset.y)
                            val normalized = normalizeAngle(tapped)
                            if (normalized in 0f..SWEEP_ANGLE) {
                                val fraction = normalized / SWEEP_ANGLE
                                val newIndex = (fraction * (chromaticNotes.size - 1))
                                    .roundToInt()
                                    .coerceIn(0, chromaticNotes.size - 1)
                                selectedIndex = newIndex
                            }
                        }
                    },
            ) {
                val strokeWidth = 12.dp.toPx()
                val padding = 56.dp.toPx()
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
                val progress = selectedIndex.toFloat() / (chromaticNotes.size - 1)
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

                // Tick marks + labels for each note
                for (i in chromaticNotes.indices) {
                    val note = chromaticNotes[i]
                    val tickProgress = i.toFloat() / (chromaticNotes.size - 1)
                    val tickAngle = START_ANGLE + tickProgress * SWEEP_ANGLE
                    val angleRad = Math.toRadians(tickAngle.toDouble())

                    val isNatural = !note.isSharp
                    val isC = note.name.startsWith("C") && !note.isSharp
                    val tickLength = when {
                        isC -> 16.dp.toPx()
                        isNatural -> 10.dp.toPx()
                        else -> 6.dp.toPx()
                    }
                    val innerR = radius - strokeWidth / 2 - 6.dp.toPx() - tickLength
                    val outerR = radius - strokeWidth / 2 - 6.dp.toPx()

                    val tickColor = if (i <= selectedIndex) accentColor.copy(alpha = 0.8f)
                    else onSurfaceVariant.copy(alpha = 0.3f)

                    drawLine(
                        color = tickColor,
                        start = Offset(
                            center.x + innerR * cos(angleRad).toFloat(),
                            center.y + innerR * sin(angleRad).toFloat(),
                        ),
                        end = Offset(
                            center.x + outerR * cos(angleRad).toFloat(),
                            center.y + outerR * sin(angleRad).toFloat(),
                        ),
                        strokeWidth = when {
                            isC -> 2.5f.dp.toPx()
                            isNatural -> 1.5f.dp.toPx()
                            else -> 1.dp.toPx()
                        },
                    )

                    // Draw note label outside the arc
                    val labelText = note.displayName
                    val style = if (isNatural) naturalLabelStyle else labelStyle
                    val labelColor = if (i == selectedIndex) accentColor
                    else if (i <= selectedIndex) accentColor.copy(alpha = 0.7f)
                    else onSurfaceVariant.copy(alpha = 0.5f)

                    val measuredText = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            fontSize = style.fontSize,
                            fontWeight = style.fontWeight,
                            color = labelColor,
                        ),
                    )
                    val labelR = radius + strokeWidth / 2 + 10.dp.toPx()
                    val labelCenter = Offset(
                        center.x + labelR * cos(angleRad).toFloat(),
                        center.y + labelR * sin(angleRad).toFloat(),
                    )
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(
                            labelCenter.x - measuredText.size.width / 2f,
                            labelCenter.y - measuredText.size.height / 2f,
                        ),
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

            // Center note display + play button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedNote.displayName,
                    style = MaterialTheme.typography.displayMedium,
                    color = onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledIconButton(
                    onClick = { playNote(selectedNote) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Play note",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Duration: ${durationSeconds}s",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun angleDeg(cx: Float, cy: Float, x: Float, y: Float): Float {
    val angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    return if (angle < 0) angle + 360f else angle
}

private fun normalizeAngle(absoluteAngle: Float): Float {
    var a = absoluteAngle - START_ANGLE
    if (a < 0) a += 360f
    return a.coerceIn(0f, SWEEP_ANGLE)
}

private fun playSineWave(frequency: Double, durationSec: Double): AudioTrack {
    val numSamples = (SAMPLE_RATE * durationSec).toInt()
    val samples = ShortArray(numSamples)

    for (i in 0 until numSamples) {
        val t = i.toDouble() / SAMPLE_RATE
        val fadeLen = SAMPLE_RATE * 0.02
        val envelope = when {
            i < fadeLen -> i / fadeLen
            i > numSamples - fadeLen -> (numSamples - i) / fadeLen
            else -> 1.0
        }
        val sample = (sin(2.0 * Math.PI * frequency * t) * envelope * Short.MAX_VALUE * 0.8)
        samples[i] = sample.toInt().toShort()
    }

    val bufferSize = samples.size * 2
    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    audioTrack.write(samples, 0, samples.size)
    audioTrack.play()
    return audioTrack
}
