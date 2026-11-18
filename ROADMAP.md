# Roadmap

Each phase ends in something runnable. A phase is done when its checkboxes are ticked and the app builds with no new warnings.

## v1.0: "Plays my library"

### Phase 0: Scaffold
- [x] Android project: Kotlin, Compose, Material 3, Gradle version catalog
- [x] `minSdk 26`, `compileSdk`/`targetSdk` = latest stable
- [x] Package `io.github.gativrek.momenta`, app label "Momenta Excitantia et Aura"
- [x] Package structure per KICKOFF.md, `AppContainer` for manual DI
- [x] ktlint (or detekt) and a GitHub Actions workflow running `./gradlew lint test assembleDebug`
- [x] App icon placeholder, light/dark theme with dynamic color on Android 12+

### Phase 1: Library
- [x] Runtime permission flow (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`) with a rationale screen and a denied state
- [x] `MediaStoreSource`: query `IS_MUSIC != 0` audio and map rows to a `Track` domain model
- [x] Folder path via `RELATIVE_PATH` (API 29+) and the `DATA` fallback (API 26–28)
- [x] `ContentObserver` to refresh the library when MediaStore changes
- [x] Album art loading via Coil (`loadThumbnail` on API 29+, album-art URI below)
- [x] Songs list screen (flat, sorted by title)

### Phase 2: Playback core
- [x] `PlaybackService : MediaSessionService` holding one ExoPlayer
- [x] Foreground service type `mediaPlayback` declared in the manifest
- [x] Audio attributes `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`, with `handleAudioFocus = true`
- [x] `setHandleAudioBecomingNoisy(true)`, wake mode `C.WAKE_MODE_LOCAL`
- [x] Media notification + lock-screen controls (Media3 default notification provider)
- [x] UI connects through `MediaController` (`SessionToken`)
- [x] Tap a song to play it, with the rest of the list as the queue
- [x] Mini-player bar + full Now Playing screen (art, title/artist, seek bar, play/pause/next/prev)
- [ ] Verify: success criteria 1–8 (emulator run passed 1 [90 s under forced Doze], 2, 6, 7 and 8; still needs a real device for all of 1–8, especially 3, 4, 5 and real Bluetooth)

### Phase 3: Browse
- [x] Albums grid → album detail (in track order, disc-aware)
- [x] Artists list → artist detail (albums + songs)
- [x] Folders tree → folder contents
- [x] "Play all" / "Shuffle all" on every collection

### Phase 4: Queue & playback modes
- [ ] Shuffle toggle, repeat off/all/one (Media3 player modes)
- [ ] Queue screen: reorder by drag, swipe to remove, tap to jump
- [ ] "Play next" / "Add to queue" from any track
- [ ] Gapless playback verified (criterion 10)
- [ ] Sleep timer: presets plus custom, with an option to finish the current track (criterion 11)
- [ ] Persist queue, index, position, shuffle and repeat to DataStore; restore paused on launch (criterion 9)

### Phase 5: Playlists
- [ ] Room schema: `Playlist`, `PlaylistEntry` (ordered), with track identity per KICKOFF.md
- [ ] Create / rename / delete playlists
- [ ] Add to playlist (from a track, album or the queue); reorder and remove entries
- [ ] Re-match entries after a rescan; show unresolved entries as "missing" instead of dropping them (criterion 14)

### Phase 6: Polish → v1.0
- [ ] Search across songs, albums and artists
- [ ] Empty states and error states (no music, permission revoked, file deleted mid-play)
- [ ] Performance pass on a 5k-track library (criterion 12)
- [ ] Run the full manual checklist in KICKOFF.md on a real device
- [ ] Signed release APK attached to a GitHub Release, `v1.0.0` tag

## Post-v1 (unordered, pick by mood)

- **M3U import/export:** playlists portable to other players and a PC
- **Home-screen widget** (Glance)
- **Android Auto:** `MediaLibraryService` browse tree; Media3 makes this mostly additive
- **Equalizer / bass boost:** system `Equalizer` effect on the audio session
- **ReplayGain / volume normalization**
- **Crossfade**
- **Lyrics:** embedded tags and `.lrc` sidecar files, synced view
- **Smart playlists:** recently added, most played, never played
- **Play counts and history** (local only)
- **Tag editing**
- **Theming from album art** (palette-based Now Playing)
- **Store distribution** (F-Droid fits the no-network ethos better than Play)
