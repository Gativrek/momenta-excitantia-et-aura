# Kickoff

Read this before writing code. It is the working spec for anyone building this app, human or agent. [GOALS.md](GOALS.md) says *what* and *why*, [ROADMAP.md](ROADMAP.md) says *in what order*, and this file says *how*.

## Ground rules

1. **Work one roadmap phase at a time**, in order. Don't start Phase N+1 code while Phase N has unticked boxes.
2. **Every change builds and runs.** `./gradlew lint test assembleDebug` must pass before a task is called done.
3. **Don't add dependencies outside the stack table** in README.md without flagging it and saying why.
4. **Never add the `INTERNET` permission.** If something seems to need it, it's out of scope.
5. **Prefer Media3 built-ins over hand-rolled code.** Audio focus, noisy-audio handling, notifications, shuffle and repeat are all provided. If you're writing an `AudioManager.OnAudioFocusChangeListener`, stop.
6. **Explain non-obvious Android code** in short comments. The owner is fluent in Kotlin/Java but newer to Media3 and Compose internals, and will ask.

## Architecture

```
┌──────────────── UI process (Activity) ─────────────────┐
│  Compose screens ── ViewModels ── Repositories         │
│                          │                             │
│                   PlayerConnection (MediaController)   │
└──────────────────────────┼─────────────────────────────┘
                           │ MediaSession (IPC-capable)
┌──────────────────────────┼─────────────────────────────┐
│  PlaybackService : MediaSessionService                 │
│     ExoPlayer · MediaSession · notification            │
│     SleepTimer · PlaybackStatePersister                │
└────────────────────────────────────────────────────────┘
         ▲                                ▲
   MediaStore (tracks)          Room (playlists) · DataStore (state, settings)
```

- **The service owns the player.** The UI never holds an ExoPlayer. It talks to the service only through `MediaController`, the same path Bluetooth, the lock screen and cars use. If it works from the UI, it works from a headset.
- **MediaStore is the source of truth for tracks.** Don't cache the library in Room. Query MediaStore and observe changes. Room holds only what MediaStore can't: playlists.
- **Unidirectional data flow.** Repositories expose `Flow`, ViewModels expose `StateFlow<UiState>`, and screens are stateless composables taking state + callbacks.

### Package layout

```
io.github.gativrek.momenta
├── App.kt                      // Application, creates AppContainer
├── AppContainer.kt             // manual DI: repositories, db, datastore
├── data/
│   ├── library/                // MediaStoreSource, LibraryRepository, Track/Album/Artist/Folder models
│   ├── playlist/               // Room entities, DAO, PlaylistRepository, TrackMatcher
│   └── prefs/                  // DataStore: settings, persisted playback state
├── playback/
│   ├── PlaybackService.kt
│   ├── PlayerConnection.kt     // wraps MediaController, exposes StateFlows to the UI
│   ├── SleepTimer.kt
│   └── MediaItemMapper.kt      // Track <-> MediaItem (+ MediaMetadata)
└── ui/
    ├── theme/
    ├── navigation/
    ├── components/             // MiniPlayer, TrackRow, ArtworkImage...
    └── screens/                // songs/, albums/, artists/, folders/, nowplaying/, queue/, playlists/, permission/
```

Single Gradle module (`:app`) for v1. Split into modules only if build times hurt. Packages are created in the phase that first puts code in them (Kotlin has no empty-package placeholder): `data/` in Phase 1, `playback/` in Phase 2.

## Key decisions and gotchas

### Library (MediaStore)
- Query `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with `IS_MUSIC != 0`. This excludes ringtones and notification sounds.
- Don't filter by MIME type to "MP3 only." Accept anything MediaStore classes as music. ExoPlayer plays the common formats.
- **Folder path:** API 29+ uses `RELATIVE_PATH`. API 26–28 uses the deprecated `DATA` column (still readable there). Hide this behind one function.
- **Album art:** API 29+ uses `ContentResolver.loadThumbnail(uri, size, null)`. Below that, use `content://media/external/audio/albumart/{albumId}`. Wrap both in a Coil fetcher or a single `artworkModel(track)` function.
- **Permissions:** `READ_MEDIA_AUDIO` on 33+, and `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` below. Android 14's "partial access" applies to photos and video only, not audio.
- Register a `ContentObserver` on the audio URI and debounce re-queries (~500 ms).
- **As built (Phase 1):**
  - `Track` is pure Kotlin (no `Uri`), so library logic stays JVM-testable. Get the playable URI with `track.contentUri()`. Raw-column parsing (unknown tags, disc/track split, folder paths, sort order) lives in `TrackFields` and is unit-tested.
  - `LibraryRepository.tracks` loads at once on first collect and reloads after a debounced burst of changes. It is shared app-wide (`shareIn`, kept for 5 s after the last collector), so tabs don't re-query.
  - Songs sort by title, skipping leading punctuation ("... And Justice" files under A), with a `Collator` at secondary strength: case-insensitive, accents count, ties broken by ID.
  - Album art: `ArtworkFetcher` (a Coil 3 fetcher) handles both API branches. `ArtworkKeyer` caches per track on 29+ (untagged files share a folder-named album ID but each has its own cover) and per album below 29. Coil is added **without** any `coil-network-*` module.
  - Permission: `PermissionGate` wraps the whole app. It re-checks on every resume and, after two denials, switches from the system dialog to "Open settings".

### Playback (Media3)
- Manifest: the service gets `android:foregroundServiceType="mediaPlayback"` and an intent filter for `androidx.media3.session.MediaSessionService`. Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (required on 34+) and `WAKE_LOCK`.
- ExoPlayer setup:
  ```kotlin
  ExoPlayer.Builder(context)
      .setAudioAttributes(
          AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
              .build(),
          /* handleAudioFocus = */ true
      )
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .build()
  ```
- **Media IDs:** `MediaItem.mediaId` = the MediaStore `_ID` as a string, and the URI is the content URI. Fill `MediaMetadata` (title, artist, album, artwork URI). This is what Bluetooth and car displays show.
- **Swipe-away behavior:** override `onTaskRemoved`. Keep running if playing, and stop the service if paused or empty (criterion 8).
- **Gapless:** ExoPlayer handles it between consecutive playlist items when the files carry encoder delay/padding info (LAME/iTunes headers). Don't add silence trimming or a second player.
- **Sleep timer:** lives in the service, not a ViewModel, so it survives the UI dying. "Finish current track" = when time is up, set a flag and pause on the next `onMediaItemTransition`.
- **State persistence:** save the queue (media IDs), current index, position, shuffle and repeat to DataStore on pause, on track change and every ~10 s while playing. Restore **paused** on startup. Never auto-play on launch.
- `POST_NOTIFICATIONS` on 13+: request it, but playback must still work if it's denied.
- **As built (Phase 2):**
  - **No artwork URI in `MediaMetadata`** (deviation from "Media IDs" above). MediaStore has no per-track art URI, and the per-album one is wrong for untagged files (see Library). ExoPlayer extracts the embedded cover while playing, and the notification, lock screen and Bluetooth get it from there. The album ID rides in the metadata extras so the in-app UI can build its own `Artwork` model.
  - `PlaybackService.onAddMediaItems` rebuilds the content URI from `mediaId` when an item arrives without one, because Media3 strips URIs from items crossing processes (System UI, Bluetooth).
  - `PlayerConnection` lives in `AppContainer`, connects in `MainActivity.onStart` and releases in `onStop`. Commands sent before the connection is ready are queued. The position is a separate polled flow, so only seek bars tick.
  - The notification permission is requested on the first play (contextual), at most once per screen session.
  - Notification small icon: `ic_notification`, generated from `design/icons/notification.svg`.
  - Instrumented smoke test: `./gradlew connectedDebugAndroidTest` (emulator or device; not in CI).
- **As built (Phase 4):**
  - The session wraps ExoPlayer in `QueuePlayer` (a `ForwardingPlayer`). Whenever shuffle turns on, or the list is replaced while shuffled, it redraws the shuffle order **starting from the current track**. ExoPlayer's own random order could put the current track near the end, leaving only a few songs to play. This works for every controller (notification, Bluetooth), not just our UI.
  - Queue edits work in **play order** (`PlayOrder`, unit-tested), so "Play next" and drag-to-reorder mean the same thing with shuffle on or off. With shuffle on they rewrite the shuffle order (`setShuffleOrder`) instead of the item list.
  - Our UI gets extra custom session commands (`SessionProtocol`): play next, add to queue, move, set/cancel the sleep timer. They're granted only to controllers from our own package; everyone else gets the default command set. Remove and jump use standard `Player` commands.
  - Sleep timer (deviation from "pause on the next `onMediaItemTransition`" above): "finish current track" sets ExoPlayer's `pauseAtEndOfMediaItems`, which stops exactly at the end of the track instead of a few milliseconds into the next one. The timer counts `elapsedRealtime` in 1 s steps, because a long `delay()` counts uptime, which stops in deep sleep. Its state reaches the UI through session extras.
  - Saved state: Preferences DataStore file `playback_state`. It holds track IDs (item order), index, position, shuffle, repeat and the play order. Saved on queue, track, play/pause, seek and mode changes, every 10 s while playing, and when the task is removed or the service is destroyed. Nothing is saved until the restore has finished, so an empty player can't overwrite it.
  - Restore runs in `PlaybackService.onCreate`, against MediaStore: missing tracks are dropped (`SavedQueue.keepOnly`, unit-tested). The player is **not prepared**, so no notification pops up at launch. `PlayerConnection` prepares on the first play.
  - The saved queue is excluded from device transfer (`data_extraction_rules.xml`): MediaStore IDs mean nothing on another phone.

### Playlists (Room)
- **MediaStore IDs are not stable forever.** They can change after a rescan, SD card remount or reinstall of files. Store a fingerprint with each entry:
  `PlaylistEntry(playlistId, position, mediaStoreId, relativePath, displayName, durationMs, sizeBytes)`.
- `TrackMatcher` resolution order: (1) ID matches and the file name matches → use it; (2) path + name match → re-link and update the ID; (3) name + duration (±1 s) + size match → re-link; (4) otherwise mark the entry missing and keep it.
- Ordering is an explicit `position` column. Reorder in one transaction.
- **As built (Phase 5):**
  - Room 2.7 with KSP. Database `momenta.db`, version 1. The schema is exported to `app/schemas/` (commit it; later versions need migrations against it).
  - Entries also store `title` and `artist`, so a missing entry still shows what it was.
  - Rule 1 also refreshes the stored fingerprint when it has drifted. MediaStore keeps the ID when a file moves within one volume, so without the refresh the stored folder would go stale, and rules 2 and 3 couldn't find the file after a later rescan hands out a new ID.
  - `PlaylistRepository` combines the DAO flows with the library track list and runs `TrackMatcher` on every change. Re-linked fingerprints are written back in the background, and that write re-emits the flow once with nothing left to fix. Missing entries stay in place, dimmed and labelled "Missing". Play and Shuffle skip them.
  - The DAO renumbers positions `0..n-1` inside the same transaction after every move or remove, so there are never gaps (`PlaylistDaoTest`, instrumented).
  - Duplicates are allowed: the same song can appear twice in a playlist.
  - Drag to reorder and swipe to remove are shared by the queue and playlist screens (`ui/components/Reorderable.kt`).
  - "Add to playlist" is in the track menu, the album top bar and the queue top bar (the whole queue in play order). Its dialog can also create a new playlist.
  - The database is included in device-to-device transfer. MediaStore IDs differ on the new phone, but rule 2 (folder + file name) re-links the entries.

### UI
- Compose + Material 3, with dynamic color on 12+ and a fixed palette below.
- Navigation: bottom bar with **Songs / Albums / Artists / Folders / Playlists**. The mini-player sits above the bottom bar and expands to Now Playing.
- Lists use `LazyColumn`/`LazyVerticalGrid` with stable `key = mediaId`.
- Portrait-first. Don't spend time on landscape in v1. (Not orientation-locked: Android 16+ ignores locks on large screens and lint flags them.)
- Navigation Compose with string routes for now. Bottom-bar tabs are the `TopLevelDestination` enum.
- **As built (Phase 3):**
  - Each tab is a nested graph (`navigation(route = tab, startDestination = "$tab/list")`), so a tab keeps its own back stack and re-selecting it restores where you were. Album detail is registered in both the Albums and Artists graphs (`"$tab/album/{albumId}"`), so opening an album from an artist stays inside the Artists tab.
  - Albums, artists and folders are derived from the track list by `LibraryGrouping`: pure functions, unit-tested, recomputed off the main thread on each library change. Nothing is cached in a database.
  - Album order: disc, then track number, then title (punctuation-skipping sort key). Album artist comes from the `ALBUM_ARTIST` tag (read only on API 30+, where the column exists), else the single track artist, else "Various artists".
  - When an album has no track numbers at all (typically untagged files MediaStore groups by folder name), rows show their own cover instead of a number.
  - Folders: one screen for every level, with the path as a URI-encoded nav argument (`""` = root). "Play all" / "Shuffle" in a folder include its subfolders.
  - "Shuffle" plays a shuffled copy of the collection with the player's shuffle mode off. The shuffle toggle arrives in Phase 4.
  - Detail top bars pass `windowInsets = WindowInsets(0)`: the root Scaffold already pads below the status bar.

### Graphics
- **Line icons only** (stroke, no fill: 24×24, stroke width 2, round caps and joins). No emojis anywhere in the UI or resources.
- **SVG is the source of truth for every graphic.** Sources live in `design/icons/*.svg`. `python tools/svg_to_vector.py` regenerates the VectorDrawables in `res/drawable/`: `<name>.svg` becomes `ic_<name>.xml` (e.g. `nav_songs.svg` → `ic_nav_songs`), and `nav_songs.svg` also produces the launcher foreground. Never hand-edit the generated XML. The script supports only `<path>` elements, so write circles as two arcs.

### Backup
- No cloud backup, ever (the data never leaves the phone). `data_extraction_rules.xml` excludes everything from cloud backup but allows direct device-to-device transfer on Android 12+. `backup_rules.xml` excludes everything on 8–11, which can't tell the two apart.

## Conventions

- Kotlin official style, enforced by ktlint (the `lint` task depends on `ktlintCheck`; `./gradlew ktlintFormat` auto-fixes). No `!!` outside tests.
- Android lint runs with `warningsAsErrors`. Only the version-bump checks (`GradleDependency`, `AndroidGradlePluginVersion`, `NewerVersionAvailable`, `OldTargetApi`) are disabled, because their results depend on how new the machine's SDK is. Upgrade deliberately.
- Coroutines: `viewModelScope` in ViewModels, and a service-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Main)` cancelled in `onDestroy`. MediaStore queries run on `Dispatchers.IO`.
- No business logic in composables, and no Android `Context` in ViewModels beyond what the repositories wrap.
- Strings in `strings.xml`. English only for v1, but no hard-coded UI text, so pt-BR can be added later.
- Commits: Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`). One roadmap checkbox ≈ one or a few commits.

## Testing

- **Unit (JVM):** `TrackMatcher`, `MediaItemMapper`, sorting and grouping (albums/artists/folders), sleep timer logic, persisted-state round trip.
- **Instrumented (minimal):** Room DAO tests, plus a smoke test that the service starts and a `MediaController` connects.
- **Emulator:** AVD `Pixel_9_API_37` (Pixel 9, API 37, Google Play image). Use it for day-to-day checks; it doesn't replace the real-device checklist.
- **Manual device checklist:** the 14 success criteria in GOALS.md. Run the playback ones (1–8) at the end of Phase 2 and the full list before tagging v1.0. Test at least one real phone on Android 13+ and, if available, one older device or emulator on API 26–28 for the `DATA`-column and album-art branches.

## Definition of done (per task)

- Builds; `lint` and `test` pass; no new warnings
- Behavior checked on a device or emulator, not just compiled
- Roadmap checkbox ticked in the same commit
- Any decision that deviates from this file is written back into this file

## First task

Phase 0 in full: scaffold the project to the layout above, wire the theme and an empty bottom-nav shell with five placeholder screens, add the CI workflow, and confirm `./gradlew lint test assembleDebug` passes. Stop there and report.
