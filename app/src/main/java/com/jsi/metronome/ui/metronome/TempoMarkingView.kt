package com.jsi.metronome.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class TempoMarking(val label: String, val minBpm: Int, val maxBpm: Int) {
    GRAVE("Grave", 30, 39),
    LARGO("Largo", 40, 59),
    ADAGIO("Adagio", 60, 72),
    ANDANTE("Andante", 73, 107),
    MODERATO("Moderato", 108, 119),
    ALLEGRO("Allegro", 120, 155),
    VIVACE("Vivace", 156, 175),
    PRESTO("Presto", 176, 209),
    PRESTISSIMO("Prestissimo", 210, 250);

    companion object {
        fun forBpm(bpm: Int): TempoMarking =
            entries.firstOrNull { bpm in it.minBpm..it.maxBpm } ?: PRESTISSIMO
    }
}

@Composable
fun TempoMarkingView(
    bpm: Int,
    modifier: Modifier = Modifier,
) {
    val currentMarking = TempoMarking.forBpm(bpm)
    var displayedMarking by remember { mutableStateOf(currentMarking) }
    var previousMarking by remember { mutableStateOf(currentMarking) }

    // Outgoing text animation — rotates around horizontal axis
    val outAlpha = remember { Animatable(1f) }
    val outRotationX = remember { Animatable(0f) }

    // Incoming text animation
    val inAlpha = remember { Animatable(1f) }
    val inRotationX = remember { Animatable(0f) }

    LaunchedEffect(currentMarking) {
        if (currentMarking != displayedMarking) {
            previousMarking = displayedMarking

            // Direction: going up = old rotates upward (positive rotationX), new enters from below
            val goingUp = currentMarking.ordinal > previousMarking.ordinal
            val exitRotation = if (goingUp) -70f else 70f

            // Reset incoming state — starts rotated from opposite side
            inAlpha.snapTo(0f)
            inRotationX.snapTo(-exitRotation)

            // Animate out and in concurrently
            launch {
                launch { outAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                launch { outRotationX.animateTo(exitRotation, tween(300, easing = FastOutSlowInEasing)) }
            }
            launch {
                launch { inAlpha.animateTo(1f, tween(380, delayMillis = 60, easing = FastOutSlowInEasing)) }
                launch { inRotationX.animateTo(0f, tween(380, delayMillis = 60, easing = FastOutSlowInEasing)) }
            }.join()

            displayedMarking = currentMarking
            // Reset outgoing for next transition
            outAlpha.snapTo(1f)
            outRotationX.snapTo(0f)
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outgoing text (previous marking rotating out)
        if (currentMarking != displayedMarking) {
            Text(
                text = previousMarking.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = onSurface,
                modifier = Modifier
                    .graphicsLayer {
                        rotationX = outRotationX.value
                        alpha = outAlpha.value
                        cameraDistance = 12f * density
                    },
            )
        }

        // Current/incoming text
        Text(
            text = currentMarking.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = onSurface,
            modifier = Modifier
                .graphicsLayer {
                    rotationX = if (currentMarking == displayedMarking) 0f else inRotationX.value
                    alpha = if (currentMarking == displayedMarking) 1f else inAlpha.value
                    cameraDistance = 12f * density
                },
        )
    }
}
