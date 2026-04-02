package com.jsi.metronome.ui.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private const val SAMPLE_RATE = 44100
private const val BUFFER_SIZE_SAMPLES = 4096

private data class TunerResult(
    val frequency: Float,
    val noteName: String,
    val octave: Int,
    val centsOff: Float,
)

private fun detectPitch(buffer: FloatArray, sampleRate: Int): Float? {
    val halfLen = buffer.size / 2
    val yinBuffer = FloatArray(halfLen)

    // Difference function
    for (tau in 0 until halfLen) {
        var sum = 0f
        for (i in 0 until halfLen) {
            val diff = buffer[i] - buffer[i + tau]
            sum += diff * diff
        }
        yinBuffer[tau] = sum
    }

    // Cumulative mean normalized difference
    yinBuffer[0] = 1f
    var runningSum = 0f
    for (tau in 1 until halfLen) {
        runningSum += yinBuffer[tau]
        yinBuffer[tau] = yinBuffer[tau] * tau / runningSum
    }

    // Absolute threshold
    val threshold = 0.15f
    var tauEstimate = -1
    for (tau in 2 until halfLen) {
        if (yinBuffer[tau] < threshold) {
            // Walk to local minimum
            var t = tau
            while (t + 1 < halfLen && yinBuffer[t + 1] < yinBuffer[t]) t++
            tauEstimate = t
            break
        }
    }

    if (tauEstimate == -1) return null

    // Parabolic interpolation
    val s0 = if (tauEstimate > 0) yinBuffer[tauEstimate - 1] else yinBuffer[tauEstimate]
    val s1 = yinBuffer[tauEstimate]
    val s2 = if (tauEstimate + 1 < halfLen) yinBuffer[tauEstimate + 1] else yinBuffer[tauEstimate]

    val betterTau = if (s0 != s2) {
        tauEstimate + (s0 - s2) / (2f * (s0 - 2f * s1 + s2))
    } else {
        tauEstimate.toFloat()
    }

    val freq = sampleRate.toFloat() / betterTau
    return if (freq in 30f..2200f) freq else null
}

private fun frequencyToTunerResult(freq: Float): TunerResult {
    val midiNote = 69f + 12f * log2(freq / 440f)
    val nearestMidi = midiNote.roundToInt()
    val cents = (midiNote - nearestMidi) * 100f
    val noteName = NOTE_NAMES[((nearestMidi % 12) + 12) % 12]
    val octave = nearestMidi / 12 - 1
    return TunerResult(freq, noteName, octave, cents)
}

@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Microphone access is needed for the tuner",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant Permission")
            }
        }
        return
    }

    TunerContent(modifier = modifier)
}

@Composable
private fun TunerContent(modifier: Modifier = Modifier) {
    var tunerResult by remember { mutableStateOf<TunerResult?>(null) }
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { isListening = false }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            val bufferSize = maxOf(minBuf, BUFFER_SIZE_SAMPLES * 4)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize,
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext
            }

            recorder.startRecording()
            isListening = true

            val readBuffer = FloatArray(BUFFER_SIZE_SAMPLES)

            try {
                while (isActive) {
                    val read = recorder.read(
                        readBuffer, 0, BUFFER_SIZE_SAMPLES, AudioRecord.READ_BLOCKING,
                    )
                    if (read > 0) {
                        var rms = 0f
                        for (i in 0 until read) rms += readBuffer[i] * readBuffer[i]
                        rms = kotlin.math.sqrt(rms / read)

                        tunerResult = if (rms > 0.01f) {
                            detectPitch(readBuffer.copyOf(read), SAMPLE_RATE)
                                ?.let { frequencyToTunerResult(it) }
                        } else {
                            null
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
                isListening = false
            }
        }
    }

    TunerDisplay(
        result = tunerResult,
        isListening = isListening,
        modifier = modifier,
    )
}

@Composable
private fun TunerDisplay(
    result: TunerResult?,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val inTuneColor = Color(0xFF4CAF50)

    val cents = result?.centsOff ?: 0f
    val isInTune = abs(cents) < 5f && result != null

    val animatedCents by animateFloatAsState(
        targetValue = cents,
        animationSpec = tween(150),
        label = "cents",
    )

    val indicatorColor by animateColorAsState(
        targetValue = when {
            result == null -> surfaceVariant
            isInTune -> inTuneColor
            else -> accentColor
        },
        label = "indicator_color",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(0.3f))

        // Note name
        Text(
            text = result?.let { "${it.noteName}${it.octave}" } ?: "—",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
            color = if (isInTune) inTuneColor else onSurface,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Frequency
        Text(
            text = result?.let { "${String.format("%.1f", it.frequency)} Hz" }
                ?: if (isListening) "Listening..." else "Starting...",
            style = MaterialTheme.typography.bodyLarge,
            color = onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Cents meter
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
        ) {
            val meterWidth = size.width
            val meterHeight = 8.dp.toPx()
            val centerY = size.height / 2f
            val meterTop = centerY - meterHeight / 2f

            // Background track
            drawRoundRect(
                color = surfaceVariant,
                topLeft = Offset(0f, meterTop),
                size = Size(meterWidth, meterHeight),
                cornerRadius = CornerRadius(meterHeight / 2f),
            )

            // Center mark
            val centerX = meterWidth / 2f
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.5f),
                start = Offset(centerX, centerY - 16.dp.toPx()),
                end = Offset(centerX, centerY + 16.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Tick marks
            for (c in listOf(-50f, -25f, 25f, 50f)) {
                val tickX = centerX + (c / 50f) * (meterWidth / 2f - 16.dp.toPx())
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.3f),
                    start = Offset(tickX, centerY - 8.dp.toPx()),
                    end = Offset(tickX, centerY + 8.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Indicator
            if (result != null) {
                val clampedCents = animatedCents.coerceIn(-50f, 50f)
                val indicatorX = centerX + (clampedCents / 50f) * (meterWidth / 2f - 16.dp.toPx())

                drawCircle(
                    color = indicatorColor.copy(alpha = 0.25f),
                    radius = 14.dp.toPx(),
                    center = Offset(indicatorX, centerY),
                )
                drawCircle(
                    color = indicatorColor,
                    radius = 8.dp.toPx(),
                    center = Offset(indicatorX, centerY),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = 3.dp.toPx(),
                    center = Offset(indicatorX - 1.5f.dp.toPx(), centerY - 1.5f.dp.toPx()),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cents labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("−50¢", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
            Text(
                text = if (result != null) {
                    val sign = if (cents >= 0) "+" else ""
                    "$sign${cents.roundToInt()}¢"
                } else "",
                style = MaterialTheme.typography.labelMedium,
                color = indicatorColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text("+50¢", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Status
        Text(
            text = when {
                result == null -> "Play a note"
                isInTune -> "In tune"
                cents > 0 -> "Sharp — tune down"
                else -> "Flat — tune up"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (isInTune) inTuneColor else onSurfaceVariant,
            fontWeight = if (isInTune) FontWeight.SemiBold else FontWeight.Normal,
        )

        Spacer(modifier = Modifier.weight(0.5f))
    }
}
