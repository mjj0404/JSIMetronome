package com.jsi.metronome.ui.metronome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jsi.metronome.viewmodel.MetronomeViewModel
import com.jsi.metronome.viewmodel.VisualStyle
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val visualStyle by viewModel.visualStyle.collectAsStateWithLifecycle()
    val tickTimeMs by viewModel.tickTimeMs.collectAsStateWithLifecycle()
    val tickCount by viewModel.tickCount.collectAsStateWithLifecycle()
    val subdivision by viewModel.subdivision.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (visualStyle) {
            VisualStyle.DIAL -> {
                DialView(
                    bpm = bpm,
                    isPlaying = isPlaying,
                    tickTimeMs = tickTimeMs,
                    tickCount = tickCount,
                    subdivision = subdivision,
                    onBpmChange = viewModel::setBpm,
                    onSubdivisionChange = viewModel::setSubdivision,
                    modifier = Modifier.weight(1f),
                )
            }
            VisualStyle.MECHANICAL -> {
                MechanicalMetronomeView(
                    bpm = bpm,
                    isPlaying = isPlaying,
                    tickTimeMs = tickTimeMs,
                    tickCount = tickCount,
                    onBpmChange = viewModel::setBpm,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TempoMarkingView(bpm = bpm)

        Spacer(modifier = Modifier.height(24.dp))

        // Play/Stop FAB
        LargeFloatingActionButton(
            onClick = onPlayToggle,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                },
                label = "play_stop",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Stop" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Inline Tap Tempo
        InlineTapTempo(
            onUseBpm = viewModel::setBpm,
            onReset = viewModel::resetCount,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InlineTapTempo(onUseBpm: (Int) -> Unit, onReset: () -> Unit) {
    val tapTimes = remember { mutableStateListOf<Long>() }
    var calculatedBpm by remember { mutableStateOf<Int?>(null) }
    var tapping by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (tapping) 0.92f else 1f,
        animationSpec = tween(80),
        label = "tap_scale",
        finishedListener = { tapping = false },
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Reset button
        ElevatedButton(
            onClick = {
                onReset()
                tapTimes.clear()
                calculatedBpm = null
            },
            modifier = Modifier.height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset count",
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Tap button
        ElevatedButton(
            onClick = {
                tapping = true
                val now = System.currentTimeMillis()
                if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) {
                    tapTimes.clear()
                    calculatedBpm = null
                }
                tapTimes.add(now)
                while (tapTimes.size > 6) tapTimes.removeAt(0)

                if (tapTimes.size >= 2) {
                    val intervals = (1 until tapTimes.size).map {
                        tapTimes[it] - tapTimes[it - 1]
                    }
                    val avgInterval = intervals.average()
                    val bpm = (60_000.0 / avgInterval)
                        .roundToInt()
                        .coerceIn(30, 250)
                    calculatedBpm = bpm
                    onUseBpm(bpm)
                }
            },
            modifier = Modifier
                .height(48.dp)
                .scale(scale),
            shape = CircleShape,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Text(
                text = "TAP",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Tap to set tempo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (calculatedBpm != null) {
                Text(
                    text = "$calculatedBpm BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
