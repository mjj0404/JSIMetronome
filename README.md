# JSI Metronome

A clean, minimal metronome app does the job, built for Android with Jetpack Compose and Material 3 dynamic color.

<img width="178" height="384" alt="Screenshot 2026-04-01 at 22 37 22" src="https://github.com/user-attachments/assets/82dccc05-f310-4ba5-ad29-1195a6bfe8cc" />
<img width="188" height="390" alt="Screenshot 2026-04-01 at 22 35 35" src="https://github.com/user-attachments/assets/a15805a7-c767-49a8-a6c0-aaddd0ac729c" />
<img width="182" height="381" alt="Screenshot 2026-04-01 at 22 36 59" src="https://github.com/user-attachments/assets/6dcaf0b4-257a-4c0e-b69b-f76e6f039474" />
<img width="178" height="383" alt="Screenshot 2026-04-01 at 22 37 12" src="https://github.com/user-attachments/assets/bf3e4f11-ae46-447a-b2c1-96550e4630cb" />
<img width="181" height="393" alt="Screenshot 2026-04-01 at 22 38 43" src="https://github.com/user-attachments/assets/4c436b37-bd30-4637-8829-b9785dc54efd" />


## Features

- **Precise Metronome** — drift-free timing using AudioTrack with Handler on a dedicated thread
- **Dial control** — intuitive circular BPM dial with sweep gradient arc and tick marks
- **Mechanical Metronome Animation** — smooth sine-mapped swing with natural deceleration at extremes
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

## License

[MIT](LICENSE)
