# Playback changes and device validation

No device was connected during implementation. Build and unit-test success does not establish 60 fps, decoder compatibility, TV compatibility or absence of device-specific crashes.

## Changed files by task

All Kotlin paths below are relative to `app/src/main/java/com/vxplayer/`. Existing uncommitted work and Gradle wrapper changes were preserved; nothing was committed.

1. Compact resume: `library/LibraryScreen.kt`, `ui/ResumeFab.kt`. The existing resume-selection tests remain applicable.
2. Seeking/buffering: `player/PlaybackTuning.kt`, `player/DirectMediaDataSource.kt`, `player/PlayerViewModel.kt`, `player/PlaybackTimeline.kt`; `PlaybackTuningTest.kt` under `app/src/test/java/com/vxplayer/player/`.
3. Rotation: `player/OrientationControls.kt`, `player/PlayerScreen.kt`, `music/MusicPlayerOverlay.kt`, `ui/QuickActionPolicy.kt`, `ui/PlayerControls.kt`, `settings/SettingsScreen.kt`, `settings/SettingsParser.kt`; `RotationPolicyTest.kt`.
4. Decoder/screenshot/Cast: `player/VideoScreenshot.kt`, `player/PlaybackDiagnostics.kt`, `player/PlayerViewModel.kt`, `player/PlayerScreen.kt`, `player/PlayerExtraMenu.kt`, `cast/CastButton.kt`, `cast/CastBridge.kt`, `cast/VXCastOptions.kt`, `cast/LocalCastServer.kt`, `cast/HttpRange.kt`, `music/VideoSessionHandoff.kt`, `MainActivity.kt`, `ui/PlayerControls.kt`, `music/MusicPlayerOverlay.kt`; `HttpRangeTest.kt`; `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/branding.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.
5. Performance/release: `library/VideoThumbnailStore.kt`, `library/LibraryScreen.kt`, `library/MediaLibraryScanner.kt`, `library/VideoLibraryViewModel.kt`, `music/MusicPolicy.kt`, `ui/LibraryFastScroll.kt`, `ui/PlayerControls.kt`; `app/build.gradle.kts`, `app/proguard-rules.pro`, manifest.
6. Stability: `cast/CastBridge.kt`, `cast/LocalCastServer.kt`, `player/PlayerViewModel.kt`, `player/PlayerScreen.kt`, `player/PlayerExtraMenu.kt`; this checklist and README.

## Phone checklist

- Videos and Folders: partly watched rows have only a circular play action on the right; no extra resume detail text. Tap the icon to continue, and tap a folder elsewhere to browse. Folder shortcuts choose the latest unfinished video. Progress remains under the thumbnail.
- Seek repeatedly forward/backward in a large local two-hour video using the bar, double tap and swipe. Check long jumps use sync frames and resume promptly. Repeat with MP4, MKV, HLS and DASH streams; retry after a network failure. Unknown durations must not crash.
- Cycle Auto, Portrait, Landscape, Reverse Portrait, Reverse Landscape. Lock each direction, rotate the phone, then unlock to Auto. Check Fit/Fill/Zoom/100%, subtitles, position and paused/playing state, with no player restart.
- Toggle HW/SW at a nonzero position while playing and paused. Try a video without a suitable software decoder; confirm a single fallback message and preserved position. Unsupported HEVC/10-bit must show an explanation.
- Capture a frame during phone playback. Verify Pictures/VXPlayer in Gallery, Open and Share. Try immediately after opening a video, after seeking, with permission denied on Android 8/9, and with protected/private media. No blank successful capture should be reported for protected media.
- Cast an HTTPS MP4, HLS and DASH URL to a TV; pause, seek and disconnect. Repeat with a local seekable MP4, seek near the end, then return to phone at the same position. Reopen the player from the notification, press Back rapidly, disconnect Wi-Fi, and reconnect. Confirm local serving stops when casting ends. Test both first-time Google Cast setup and a device without Google Play services; Cast screen is the fallback.
- Scroll a large video/folder/music library quickly. Return from playback and verify the old scroll position survives a rescan. Measure release-frame timing with Perfetto/JankStats or Android Studio; inspect debug StrictMode logs. No measured 60 fps result is claimed.
- Background video/audio with the screen off: notification, lock-screen and Bluetooth controls, audio focus/phone call, headphones unplugged, and foreground return. Starting music pauses video/Cast; starting video pauses music.
- Recheck audio/subtitle track selection, external SRT loading, styling and delay, A-B repeat, sleep timer, equalizer, night mode, speed, background audio, PiP, control lock and quick-action customization. Rotate and use Back while sheets are open.
- Recheck direct URL playback, HTML-to-web-player handling, web video, Private, resume/history and music queues/playlists.

## Limits and follow-up

- Cast sends one selected item to the Default Media Receiver. TV-supported codecs and containers still apply; VXPlayer does not transcode. DRM/license handoff, authenticated request headers/cookies and casting phone-only effects or local subtitle tracks are not implemented. IPv4 Wi-Fi and a seekable local descriptor are required for local serving. The server uses a random token, binds only to Wi-Fi, handles byte ranges and closes its sockets when the Cast session ends.
- SurfaceView screenshots need Android 8+. Android 7 SurfaceView capture is unavailable and reports a failure; Android 8/9 Gallery writes require the existing storage permission. Android 10+ uses scoped MediaStore without legacy write permission. Private/DRM screenshots are rejected.
- The existing global cleartext declaration was retained to preserve user-entered HTTP streams and web playback. It is **not** a localhost-only network-security policy. The Cast server itself is Wi-Fi-bound and does not broaden file access. Restricting all outgoing HTTP would break those existing links and requires a separate product decision.
- Debug StrictMode was already enabled. Runtime violations and performance cannot be audited without a connected device. No app-specific Baseline Profile was generated because this project has no macrobenchmark/profile-generation device setup. Bundled dependency profiles are still merged by the Android build.
- Remaining disabled UI represents real unavailable capabilities: unsupported tracks/effects, missing permissions/media, unknown-duration seeking or absent previous/next items. Screenshots are not offered in the audio-only player. There are no intentionally inert action buttons.

Implementation references: [Google Cast Android setup](https://developers.google.com/cast/docs/android_sender/integrate), [Media3 LoadControl](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder).
