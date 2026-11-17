# Goals

## Purpose

A music player I trust for my own library: open it, hit play, lock the phone, and forget about it. It's built for me first and published on GitHub with no support promise. It's also a vehicle for practicing modern Android (Compose, Media3) and spec-driven development with agents.

## Goals

1. **Playback never surprises me.** It keeps playing with the screen off, stops for calls, resumes when appropriate, and pauses when headphones disconnect. The app is never the reason the music stopped or didn't stop.
2. **Local and private.** It plays only audio files on the device. No network permission, no analytics, no accounts.
3. **Fast on a real library.** It opens and is usable quickly, even with thousands of tracks.
4. **Plays well with the system.** It is a good Android media citizen: notification, lock screen, Bluetooth/AVRCP, car displays, audio focus.
5. **Playlists that survive.** Playlists aren't lost when the library is rescanned or files move around a bit.
6. **Readable codebase.** It is small enough to understand in an afternoon, with clear layers and no framework for framework's sake.

## Non-goals (v1)

- Streaming, cloud sync, or any online service (Spotify, YouTube, scrobbling)
- Tag/metadata editing or file management (delete, rename, move)
- Equalizer or audio effects
- Lyrics
- Android Auto, Wear OS or home-screen widgets
- Crossfade
- Tablet- or landscape-optimized layouts
- Play Store or F-Droid distribution
- Supporting Android versions below 8.0 (API 26)

Non-goals are not "never." Several appear in later [ROADMAP.md](ROADMAP.md) phases. They are explicitly out of v1.

## Success criteria for v1.0

All of these must pass on a real device (manual checklist in KICKOFF.md):

| # | Scenario | Expected |
|---|---|---|
| 1 | Play an album, lock the phone, wait 60 min | Plays through without stopping |
| 2 | Incoming phone call during playback | Pauses, then resumes after the call ends |
| 3 | Start a video in another app | Our playback pauses and doesn't auto-resume |
| 4 | Navigation voice prompt | Our volume ducks, then restores |
| 5 | Unplug wired headphones / turn off BT headset | Pauses immediately and does not blast through the speaker |
| 6 | BT headset play/pause/next/prev buttons | All work, including with the screen locked |
| 7 | Car/headset display | Shows title, artist and album |
| 8 | Swipe the app away from recents while playing | Keeps playing; notification still controls it |
| 9 | Force-stop or reboot, then reopen | Same queue, track and position restored (paused) |
| 10 | Gapless album (live album or continuous mix) | No audible gap between tracks |
| 11 | Sleep timer 15 min, "finish current track" on | Stops at the end of the track after the timer |
| 12 | Library of ~5,000 tracks | Song list is scrollable within ~1 s of opening |
| 13 | Deny the audio permission | Clear explanation screen and a way to grant it; no crash |
| 14 | Add files via USB, reopen the app | New tracks appear; existing playlists are intact |
