# Momenta Excitantia et Aura

*Hype moments and aura.* An offline Android music player for audio files already on your phone.

No streaming, no accounts, no network permission. It finds the music on your device and plays it reliably: in the background, with the screen off, over Bluetooth, and it gets out of the way when something else needs the speaker.

> **Status:** pre-development. See [ROADMAP.md](ROADMAP.md).

## Features (v1 target)

- **Local library:** reads audio from the device's MediaStore (MP3, FLAC, M4A/AAC, OGG/Opus, WAV)
- **Browse** by song, album, artist and folder
- **Background playback** through a foreground media service, with lock-screen and notification controls
- **Bluetooth and headset buttons**, with track metadata on car and headset displays
- **Audio focus:** pauses for calls and other apps, ducks for navigation prompts
- **Pauses when headphones are unplugged** or Bluetooth disconnects
- **Playlists:** create, rename, reorder and delete
- **Queue:** shuffle, repeat (off/all/one), reorder and remove upcoming tracks
- **Gapless playback**
- **Sleep timer**
- **Restores your queue and position** after the app is killed or the phone reboots

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Playback | Jetpack Media3 (ExoPlayer + MediaSessionService) |
| Library source | Android MediaStore |
| Persistence | Room (playlists), DataStore (settings and playback state) |
| Async | Kotlin Coroutines + Flow |
| Images | Coil |
| DI | Manual (constructor injection via an `AppContainer`) |
| Build | Gradle (Kotlin DSL) + version catalog, ktlint, Android lint |

## Requirements

- Android 8.0 (API 26) or newer
- To build: Android Studio (latest stable), JDK 17+

## Building

```bash
git clone https://github.com/Gativrek/momenta-excitantia-et-aura.git
cd momenta-excitantia-et-aura
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (12 and below) | Read your music library |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background |
| `POST_NOTIFICATIONS` (Android 13+) | Playback notification (optional; media controls still work without it) |
| `WAKE_LOCK` | Keep playing with the screen off |

There is no `INTERNET` permission. The app never leaves your phone.

## Docs

- [GOALS.md](GOALS.md): what this app is and isn't
- [ROADMAP.md](ROADMAP.md): phased plan
- [KICKOFF.md](KICKOFF.md): architecture, conventions and build instructions for development (human or agent)

## License

TBD.
