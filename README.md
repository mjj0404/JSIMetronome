# JSI Metronome

A clean, minimal metronome app for Android built with Jetpack Compose and Material 3 dynamic color.

## Features

- **Precise metronome** — drift-free timing using AudioTrack with Handler on a dedicated thread
- **Dial control** — intuitive circular BPM dial with sweep gradient arc and tick marks
- **Pendulum animation** — smooth sine-mapped swing with natural deceleration at extremes
- **Tap tempo** — tap to detect BPM
- **Pitch pipe** — sine wave tone generator with clean fade in/out
- **Foreground service** — audio continues when the app is backgrounded
- **Material 3 dynamic color** — adapts to your wallpaper with a warm coral accent

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + StateFlow
- Foreground Service + AudioTrack (MODE_STATIC)
- DataStore Preferences
- Navigation Compose (type-safe routes with kotlinx-serialization)
- Min SDK 33, Target SDK 35

## Building

Open the project in Android Studio and run on a device or emulator with API 33+.

```bash
./gradlew assembleDebug
```

## License

[MIT](LICENSE)
