# Momenta Excitantia et Aura

*Hype moments and aura.* An offline Android music player for audio files already on your phone.

No streaming, no accounts, no network permission. It finds the music on your device and plays it reliably: in the background, with the screen off, over Bluetooth, and it gets out of the way when something else needs the speaker.

> **Status:** in development toward v1.0. Playback, library, playlists, themes and tag editing are done; the real-device checklist is next.

## Features (v1 target)

- **Local library:** reads audio from the device's MediaStore (MP3, FLAC, M4A/AAC, OGG/Opus, WAV)
- **Browse** by song, album, artist and folder
- **Sorting:** order songs and playlists by name, artist, album, date added, length, file size, time listened, times played, last played, Moments or Moments heard, either way round. Each list remembers its own order
- **Library folders:** choose which folders count as music. Messaging apps (WhatsApp, Telegram and the like), voice recorders and ringtones start out left out
- **Search** across songs, albums and artists (ignores case and accents)
- **Background playback** through a foreground media service, with lock-screen and notification controls
- **Bluetooth and headset buttons**, with track metadata on car and headset displays
- **Audio focus:** pauses for calls and other apps, ducks for navigation prompts
- **Pauses when headphones are unplugged** or Bluetooth disconnects
- **Playlists:** create, rename, reorder and delete
- **Smart playlists,** kept up to date from what you listen to: On repeat, Most played, Recently added, Forgotten favourites and Never played. Save any of them as a normal playlist
- **Queue:** shuffle, repeat (off/all/one), reorder and remove upcoming tracks
- **A proper entrance:** the logo glides from the launch screen to its place beside the tab name while your songs fly up into the list
- **A cover that follows the queue:** on Now Playing the cover slides out as the next song slides in (and back the other way). Drag it right to left to skip, left to right to go back
- **Sharp covers:** large covers are read straight from the picture embedded in the file, at full size
- **Home-screen widget:** the cover, the song, previous, play/pause, next and the Moment bolt, and it starts playback even with the app closed
- **Gapless playback**
- **Sleep timer**
- **Restores your queue and position** after the app is killed or the phone reboots
- **Themes:** ten built-in looks, each with its own vector pattern, and every color, pattern, glow and corner style editable live. Text stays readable whatever colors you pick
- **Tag editing:** fix a song's title, artist, album or album artist, or rename a whole album at once. Changes are written into the files (MP3, FLAC, M4A, OGG/Opus), so other apps and your PC see them too
- **Moments:** tap the bolt when a song hits and Momenta saves that part. Fine-tune each Moment to the tenth of a second, give it a name and a run-up, and an optional volume boost of up to +50%. While a Moment plays the cover rises into a heavenly light, the title catches fire and the theme's pattern lights up. Jump straight to a song's Moment from any list. Chill mode turns it all off for studying
- **Your Moments and the reel:** every Moment in one list, and a reel that plays only the Moments, one after another, fading from each to the next. Replay the same part twice and Momenta offers to make it a Moment
- **Moment clips:** give a Moment a video from your phone and it plays silently while the Moment hits, in the cover (which takes the clip's shape, wide or tall) or behind the whole app
- **Album themes:** pin a theme to an album and the whole app wears it while that album plays
- **Liner notes:** a private note on a song or album, shown softly on Now Playing
- **Listening history,** kept on the phone and nowhere else
- **Your Aura:** what your listening says about you, worked out on the phone: the era you're in (the album that took over your month), time in hype inside your Moments, your most played songs and artists, past eras, and what played on this day in earlier years. Share it as a card drawn in your own theme, through the share sheet, with no internet involved

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3, Lifecycle ViewModel |
| Navigation | Navigation Compose |
| Playback | Jetpack Media3 (ExoPlayer + MediaSessionService) |
| Library source | Android MediaStore |
| Persistence | Room (playlists, listening history, Moments), DataStore (settings, sort orders and playback state) |
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
