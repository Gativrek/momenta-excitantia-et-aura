# Momenta Excitantia et Aura

*Hype moments and aura.* An offline Android music player for audio files already on your phone.

No streaming, no accounts, no network permission. It finds the music on your device and plays it reliably: in the background, with the screen off, over Bluetooth, and it gets out of the way when something else needs the speaker.

> **Status:** in development toward v1.0. Playback, library, playlists, themes and tag editing are done; the real-device checklist is next.

## Features (v1 target)

- **Local library:** reads audio from the device's MediaStore (MP3, FLAC, M4A/AAC, OGG/Opus, WAV)
- **Browse** by song, album, artist and folder
- **Search** across songs, albums and artists (ignores case and accents)
- **Background playback** through a foreground media service, with lock-screen and notification controls
- **Bluetooth and headset buttons**, with track metadata on car and headset displays
- **Audio focus:** pauses for calls and other apps, ducks for navigation prompts
- **Pauses when headphones are unplugged** or Bluetooth disconnects
- **Playlists:** create, rename, reorder and delete
- **Queue:** shuffle, repeat (off/all/one), reorder and remove upcoming tracks
- **Gapless playback**
- **Sleep timer**
- **Restores your queue and position** after the app is killed or the phone reboots
- **Themes:** ten built-in looks, each with its own vector pattern, and every color, pattern, glow and corner style editable live. Text stays readable whatever colors you pick
- **Tag editing:** fix a song's title, artist, album or album artist, or rename a whole album at once. Changes are written into the files (MP3, FLAC, M4A, OGG/Opus), so other apps and your PC see them too
- **Moments:** tap the bolt when a song hits and Momenta saves that part. Fine-tune each Moment to the tenth of a second, give it a name and a run-up, and an optional volume boost of up to +30%. While a Moment plays the cover rises into a heavenly light, the title catches fire and the theme's pattern lights up. Jump straight to a song's Moment from any list. Chill mode turns it all off for studying
- **Listening history,** kept on the phone and nowhere else

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3, Lifecycle ViewModel |
| Navigation | Navigation Compose |
| Playback | Jetpack Media3 (ExoPlayer + MediaSessionService) |
| Library source | Android MediaStore |
| Persistence | Room (playlists), DataStore (settings and playback state) |
| Async | Kotlin Coroutines + Flow |
| Images | Coil 3 (core only, no network module) |
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

Release builds are signed from a local `keystore.properties` in the project root (gitignored) or from environment variables of the same names: `MOMENTA_KEYSTORE`, `MOMENTA_KEYSTORE_PASSWORD`, `MOMENTA_KEY_ALIAS`, `MOMENTA_KEY_PASSWORD`. Without them, `./gradlew assembleRelease` builds an unsigned APK.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (12 and below) | Read your music library |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background |
| `POST_NOTIFICATIONS` (Android 13+) | Playback notification (optional; media controls still work without it) |
| `WAKE_LOCK` | Keep playing with the screen off |
| `WRITE_EXTERNAL_STORAGE` (Android 10 and below only) | Save tag edits into your files. On Android 11+ the system asks per edit instead |

There is no `INTERNET` permission. The app never leaves your phone.

## License

TBD.
