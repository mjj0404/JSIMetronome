# JSI Metronome

## Stack
- Kotlin, Jetpack Compose, Material 3 dynamic color
- Navigation Compose (type-safe), MVVM + StateFlow
- Foreground Service + AudioTrack for audio (not SoundPool — AudioTrack gives more control for generated tones)
- DataStore (Preferences) for persistence
- Min SDK 33 (Tiramisu), target/compile SDK 35

## Architecture
- MVVM strictly: ViewModel owns all state via StateFlow
- Break UI into small reusable Composables, no monolithic files
- Foreground Service required for all audio — must survive backgrounding
- POST_NOTIFICATIONS permission must be requested before starting service
- Single-activity, three tabs: Metronome, Tools, Settings
- Service uses Handler on HandlerThread for drift-free tick timing (epoch + tickCount approach)
- AudioTrack in MODE_STATIC for low-latency click playback

## Package Structure
com.jsi.metronome/
├── ui/
│   ├── navigation/      # Screen routes (@Serializable sealed interface)
│   ├── metronome/       # MetronomeScreen, DialView, PendulumView
│   ├── tools/           # ToolsScreen, TapTempo, PitchPipe
│   ├── settings/        # SettingsScreen
│   └── theme/           # Theme, Color, Type
├── service/             # MetronomeService (Foreground, bound + started)
├── viewmodel/           # MetronomeViewModel, SettingsViewModel
├── data/                # PreferencesManager (DataStore wrapper)
└── MainActivity.kt

## Dependencies (libs.versions.toml)
- kotlinx-serialization for type-safe navigation routes
- navigation-compose 2.8.5
- datastore-preferences 1.1.1
- material-icons-extended for filled/outlined icon pairs
- lifecycle-viewmodel-compose + lifecycle-runtime-compose for collectAsStateWithLifecycle

## UI/Aesthetic
- Reference apps: Gentler Streak, Opal, Linear
- Material 3 dynamic color + one warm coral accent (tertiary slot)
- Generous padding (24dp+), breathable layouts, no clutter
- Motion: meaningful only (pendulum swing, play/stop icon fade transition)
- Typography from MaterialTheme exclusively, no hardcoded sp
- Bottom nav: 3 tabs with filled/outlined icon toggle, labels always visible, pill-shaped indicator using tertiaryContainer

## Key Decisions
- Tab navigation uses AnimatedContent with fade, not NavHost (simpler for 3 static screens with no arguments)
- Metronome click is a generated 30ms 1000Hz sine with linear decay — no raw resource files needed
- PitchPipe generates sine waves with 20ms fade in/out for clean attack/release
- Pendulum uses infiniteRepeatable with sin() mapping for natural deceleration at swing extremes
- Dial uses Canvas with sweep gradient for the progress arc and tick marks every 10 BPM

## Autonomy — IMPORTANT
- Never stub or placeholder — always write full working implementations
- Do not pause to ask questions mid-task, make bold decisions
- Chain adjacent improvements automatically
- Pick the better approach and go, don't present options
- Refactor freely, create files/packages freely, update build.gradle freely
- Update this file silently at end of session with new learnings
