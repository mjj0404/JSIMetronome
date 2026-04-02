package com.jsi.metronome

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jsi.metronome.service.MetronomeService
import com.jsi.metronome.ui.metronome.MetronomeScreen
import com.jsi.metronome.ui.navigation.Screen
import com.jsi.metronome.ui.pitchpipe.PitchPipeScreen
import com.jsi.metronome.ui.settings.SettingsScreen
import com.jsi.metronome.ui.tuner.TunerScreen
import com.jsi.metronome.data.ThemeMode
import com.jsi.metronome.ui.theme.JSIMetronomeTheme
import com.jsi.metronome.viewmodel.MetronomeViewModel
import com.jsi.metronome.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val metronomeViewModel: MetronomeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var metronomeService: MetronomeService? = null
    private var serviceBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            metronomeService = (binder as MetronomeService.LocalBinder).service
            metronomeService?.onTick = { metronomeViewModel.onTick() }
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            metronomeService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }

            JSIMetronomeTheme(darkTheme = darkTheme) {
                val isPlaying by metronomeViewModel.isPlaying.collectAsStateWithLifecycle()
                val bpm by metronomeViewModel.bpm.collectAsStateWithLifecycle()
                val subdivision by metronomeViewModel.subdivision.collectAsStateWithLifecycle()
                val backgroundPlayback by settingsViewModel.backgroundPlayback.collectAsStateWithLifecycle()

                // Start/stop service when play state changes (waits for service binding)
                LaunchedEffect(isPlaying, serviceBound) {
                    if (isPlaying) {
                        startAndBindService()
                        if (serviceBound) {
                            metronomeService?.let { svc ->
                                if (!svc.getIsPlaying()) svc.startMetronome(bpm, subdivision)
                            }
                        }
                    } else {
                        metronomeService?.stopMetronome()
                    }
                }

                // Update BPM in service when it changes while playing
                LaunchedEffect(bpm) {
                    if (isPlaying && serviceBound) {
                        metronomeService?.updateBpm(bpm)
                    }
                }

                // Update subdivision in service when it changes while playing
                LaunchedEffect(subdivision) {
                    if (isPlaying && serviceBound) {
                        metronomeService?.updateSubdivision(subdivision)
                    }
                }

                // Handle background playback setting
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, backgroundPlayback) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && !backgroundPlayback && isPlaying) {
                            metronomeViewModel.setPlaying(false)
                            metronomeService?.stopMetronome()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                JSIMetronomeApp(
                    metronomeViewModel = metronomeViewModel,
                    settingsViewModel = settingsViewModel,
                    onPlayToggle = {
                        metronomeViewModel.togglePlayback()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, MetronomeService::class.java)
        startForegroundService(intent)
        bindToService()
    }

    private fun bindToService() {
        if (!serviceBound) {
            val intent = Intent(this, MetronomeService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
}

private enum class Tab(
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val screen: Screen,
) {
    Metronome("Metronome", Icons.Filled.MusicNote, Icons.Outlined.MusicNote, Screen.Metronome),
    PitchPipe("Pitch Pipe", Icons.Filled.Tune, Icons.Outlined.Tune, Screen.PitchPipe),
    Tuner("Tuner", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, Screen.Tuner),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, Screen.Settings),
}

@Composable
private fun JSIMetronomeApp(
    metronomeViewModel: MetronomeViewModel,
    settingsViewModel: SettingsViewModel,
    onPlayToggle: () -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Metronome) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "tab_content",
        ) { tab ->
            when (tab) {
                Tab.Metronome -> MetronomeScreen(
                    viewModel = metronomeViewModel,
                    onPlayToggle = onPlayToggle,
                    modifier = Modifier.padding(innerPadding),
                )
                Tab.PitchPipe -> {
                    val duration by settingsViewModel.pitchPipeDuration.collectAsStateWithLifecycle()
                    PitchPipeScreen(
                        durationSeconds = duration,
                        modifier = Modifier.padding(innerPadding),
                        onPlayNote = {
                            if (metronomeViewModel.isPlaying.value) {
                                metronomeViewModel.setPlaying(false)
                            }
                        },
                    )
                }
                Tab.Tuner -> TunerScreen(
                    modifier = Modifier.padding(innerPadding),
                )
                Tab.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
