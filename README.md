# VXPlayer

An original Android video player using Kotlin, Compose Material 3, Media3 ExoPlayer, MVVM, and Room. Min SDK 24, compile/target SDK 35. VXPlayer is not affiliated with MX Player; its branding, icon, colors, and UI text are original.

## Build and verification

Use JDK 17 and Android SDK 35. Set `JAVA_HOME` and configure `sdk.dir` in untracked `local.properties` or set `ANDROID_HOME`.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

To use this workspace's ignored local tools:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$PWD/.tools/gradle-home"
$env:ANDROID_USER_HOME = "$PWD/.tools/android-user"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

Outputs:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

Release minification and shrinking are disabled. Release distribution needs your own signing key. App version is `1.1.0` (code `2`). Each redesign part is built, unit-tested, and committed separately.

## Latest five-part theme, GUI, audio, and enhance update

This pass follows the final five-part plan in the request. The earlier 13-feature advanced wishlist remains separate; this update does not add URL streaming, casting, private folders, backups, bookmarks, or the other standalone wishlist items. No network permissions were added. The only additional app permission is the normal `MODIFY_AUDIO_SETTINGS` permission for session audio effects.

- **Theme:** System is the default for new settings; saved choices are retained. Light and Dark use full blue/neutral Material 3 palettes with contrast-tested text roles. Android 12+ has a separate Material You dynamic-color switch; earlier devices use the static palette. The player always uses black, white controls, and a dark palette for its sheets. System-bar icon contrast follows the active screen.
- **GUI:** shared typography, 4/8/12/16/24 dp spacing, 12–16 dp cards, consistent Material Icons, 48 dp minimum icon targets, press feedback, tab/navigation fades, larger thumbnails, resume card, gradient player scrims, and playback/track/audio/enhance bottom sheets. Existing playback and file-action behavior remains available.
- **Volume:** 0–100% maps to the actual discrete system media-volume steps; above 100% applies session-bound `LoudnessEnhancer` gain up to the configured 150% or 200% cap. The conversion is `2000 * log10(percent / 100)` millibels, so 200% requests approximately +6.02 dB amplitude gain. The percentage describes the requested processing level, not a measured sound-pressure or perceived-loudness guarantee. Platform compression and hardware can limit the audible result.
- **Volume safety/lifecycle:** levels above 100% have warning-colored indicators. The first request above 150% requires explicit acknowledgement before that gain is applied. Boost is process-session state, resets above-100% gain on a different video by default, and can optionally be retained between videos. Hardware volume changes remove active gain and resynchronize the indicator. Effects are released with the player and recreated when its audio session changes. Failed effect operations are checked, disabled, and released rather than reporting unsupported gain as active.
- **Audio enhance:** BassBoost is exposed only when supported. Android 9+ Night mode uses supported DynamicsProcessing compression/limiting to reduce loud peaks without makeup gain. Unsupported effects show an unavailable state instead of failing playback. Night mode is not hearing protection; boosted playback can still distort or be unsafe.
- **Video enhance:** a single Media3 1.6.1 `GlEffect` applies brightness, contrast, saturation, convolution-style sharpness/softening, and warmth. Presets are Original, Vivid, Cinema, Soft, Sharp, and Night, plus custom sliders and per-slider resets. Master off compares the original without discarding the chosen values. A Remember enhance settings preference controls persistence.
- **Enhance boundaries:** Original playback keeps the ordinary ExoPlayer output path. Initial activation may briefly rebuild the player while retaining position, paused state, speed, and track state; subsequent live slider changes update shader uniforms without repeatedly rebuilding the pipeline. Paused preview redraw is debounced. Enhancement is bypassed in PiP/audio-only mode and restored afterward. HDR, protected video, and unsupported GPU/decoder paths bypass enhancement. A processing failure hides enhancement for that video path and attempts normal playback recovery; a fault during hidden background playback pauses safely rather than starting a new hidden foreground-service session. All GL resources belong to Media3 and are released with its pipeline.

### Latest Part 1 — themes

- [ ] On every library tab, folder screen, Settings, dialogs, search field, file menu, and permission/empty/error state, switch System/Light/Dark and check readable text/icons/surfaces immediately without restarting.
- [ ] On Android 12+, enable dynamic color under both light and dark system appearance. Change wallpaper and reopen/resume the app; inspect menus, cards, progress lines, and switch/slider contrast. On older Android, confirm the static fallback and no unsupported switch.
- [ ] Change system light/dark appearance while System is selected. Check status/navigation icon contrast outside the player and white icons on black when the player reveals system bars.
- [ ] Open the player in every theme: video background must stay black, controls white, and sheets readable. Exit to a light library and verify bar icons restore correctly. Restart and verify settings persist.

### Latest Part 2 — GUI

- [ ] Inspect Folders/Videos/Recent, search and overflow actions, folder aggregates, rounded list/grid thumbnails, duration badges, resume progress, watched labels, and Resume last video behavior.
- [ ] Verify Resume prefers the most recent unfinished checkpoint, falls back to replaying the latest item if all are complete, and is absent with no history.
- [ ] Test 320 dp phones, tablets, portrait/landscape, RTL, and large system fonts. Text must wrap/ellipsize appropriately; icon targets must be at least 48 dp, and overflowed player actions remain reachable by scrolling.
- [ ] Check press feedback, tab/navigation fades, controls fading, gradient scrims, large play/pause control, thick seek track, and rounded thumb. Lock and PiP must immediately remove touch targets.
- [ ] Open speed, tracks, audio, and enhance sheets while playing/paused. Scroll long sheets at large fonts. Load/cancel subtitles and notification permission, then return; hidden controls must not discard the result callback.
- [ ] Verify polished permission, loading, error, empty, and no-search-result states provide the appropriate action.

### Latest Part 3 — boost and audio effects

- [ ] At a low listening level, drag volume through 0–100% and check the system volume steps and displayed percentage agree. Then test 100–150% and confirm a warning-colored indicator.
- [ ] Without prior acknowledgement, request 151–200%: verify the warning appears before applying above-150% gain. Cancel once; then accept. Repeat after rotation/reopening and confirm the one-time acknowledgement behavior.
- [ ] Disable Allow volume boost, set the cap to 150%, then 200%, and test both swipe and Audio enhance slider. Reset audio. No control may exceed the selected policy or show unavailable gain as active.
- [ ] Boost a video and move to another: above-100% gain resets by default. Enable Keep boost between videos and test again. Restart the process and confirm boost does not silently restore above 100%.
- [ ] Change volume with hardware keys and notification/remote controls while boosted, including background-audio playback. Verify gain is removed and the indicator resynchronizes.
- [ ] Switch embedded audio tracks/routes (speaker, wired/Bluetooth), rotate, enter/leave PiP, background, return, and exit. Check effect recreation, no duplicate audio, and no retained effects after player release.
- [ ] Test unsupported/fixed-volume outputs and denied effect control: controls must report unavailability instead of crashing or displaying fictitious boost.
- [ ] On supported devices, test bass strength and Night compression. On Android 7–8 and devices without DSP support, Night must be disabled with an explanation. Test their saved state on the next player/audio session.

### Latest Part 4 — video enhancement

- [ ] Test all six presets and each slider live while playing and paused; check brightness, contrast, saturation, detail/softening, and warmth. Reset individual sliders and reset to Original.
- [ ] Compare master on/off without losing the selected values. Check the Enhance on indicator, presets/custom label, and Remember enhance settings on/off after reopening or changing videos.
- [ ] Rotate landscape/portrait with enhancement on. Cycle Fit/Fill/Zoom/100%, pinch, seek, change speed, select tracks/subtitles, and switch playlist videos; resume state and subtitles must remain correct.
- [ ] Enter PiP with enhancement active, resize/expand it, then return. Enable/disable audio-only mode. Verify enhancement bypasses those modes and restores its previous settings afterward.
- [ ] Test HDR/protected/unsupported decoder paths and a device with limited GL support. The Enhance button must be hidden or disabled with a short reason, and normal playback must remain available.
- [ ] Exercise processing failure recovery if reproducible: retain time/paused state, do not loop recovery, do not launch hidden audio after a failure, and release the old player/GL resources.

### Latest Part 5 — regression/release

- [ ] Run `.\.tools\check.ps1` and `.\gradlew.bat assembleRelease` with JDK 17. Install a locally signed release build and repeat theme, boost, enhancement, fullscreen, PiP, and background-audio checks.
- [ ] Check existing folder/file-consent, playlist, resume, speed, sleep timer, A–B, subtitles/tracks, and history-clearing behavior. Existing regression checklist follows below.

The automated checks do not exercise Android audio hardware, compile/run GLSL on a device GPU, validate real wallpaper palettes, or inspect screen layouts. Those remain device/emulator checks. No device was connected during this implementation.

## Behavior and boundaries

- The player is a black, unpadded, edge-to-edge surface. It uses immersive transient system bars, short-edge cutout layout, and automatic orientation from Media3 video dimensions. Only overlay controls have safe-area padding. Fit intentionally retains black margins when screen and video aspect ratios differ; Fill stretches, Zoom crops, and 100% displays native pixel dimensions. Large-screen Android policies may override requested orientation.
- Folders is the default home tab. Folder details, all Videos, and Recent are separate views. Resume progress, watched markers, metadata, stable list keys, and an 8 MiB thumbnail cache support browsing. The optional folder NEW badge is not implemented.
- Playlist playback stays within the opened video's folder and follows the current library sort. Automatic next is on by default and configurable. It stops at the last video without wrapping. Search filters the browser, not the folder playlist.
- Room stores per-URI resume positions, watched markers, the latest 20 distinct played videos, and audio/subtitle choices. Migrations preserve existing data from schema versions 1 through 3 when upgrading to version 4. Completion removes the resume checkpoint while retaining a watched marker. Default settings use app preferences.
- Background/audio-only mode is a deliberately limited current-video handoff to a Media3 `MediaSessionService` with a media notification. It disables video rendering, prevents automatic PiP, and keeps audio and transport controls active when Home is pressed. It stops at the current video's end; it does not advance a background playlist. Back, closing the task, or turning the mode off ends the service handoff. It does not attempt to resurrect playback after process death. Normal foreground/PiP playback still has folder auto-next.
- Subtitle appearance is configurable. External SRT attachments and track choices are remembered per URI when the document provider grants persistent access. Unsupported or missing track choices fall back to automatic selection.
- Subtitle delay controls are deferred: the pinned Media3 1.6.1 `SubtitleConfiguration` has no public subtitle timestamp offset API. Adding a no-op control or replacing the text renderer would be misleading or invasive. A future Media3 upgrade can add this separately. See the [pinned Media3 source](https://github.com/androidx/media/blob/1.6.1/libraries/common/src/main/java/androidx/media3/common/MediaItem.java).
- Rename/delete use Android 11+ write/delete consent requests, Android 10 recoverable consent, or legacy write permission on Android 7–9. Cancellation and access failures leave the app usable. No broad all-files permission is requested.

## Existing playback regression checklist

These checks remain unverified until a device or emulator is available. Unit tests cover persistence queue ordering, clearing checkpoints, playlist boundaries, seek-preview clamping, sorting, and preferred track matching. They do not validate rendering, system consent dialogs, foreground-service notifications, or real Room migration execution.

### Part 1: fullscreen and lifecycle

- [ ] Open landscape, portrait, square, and rotated-metadata videos. Confirm automatic orientation, correct Fit geometry, and no inset padding around the video root.
- [ ] Test gesture and three-button navigation, plus a simulated/physical display cutout. Video may use the notch area; controls must avoid unsafe areas.
- [ ] Swipe from a system edge to reveal transient bars; verify they hide again without resizing the video. Back restores library bars and orientation policy.
- [ ] Lock rotation, rotate the device, unlock, and manually toggle portrait/landscape. Verify position, paused state, speed, tracks, and brightness remain correct.
- [ ] Verify the display remains awake while playing and can sleep while paused. Backgrounding without PiP/audio mode must release the player and preserve position.

### Part 2: folder-first browsing

- [ ] Verify Folders opens by default, with folder icon/name/count/aggregate size. Open a folder and check only its videos are listed.
- [ ] Scroll folders, open a folder/video, then return. Verify folder scroll position and selected folder survive navigation and rotation.
- [ ] Check Videos is flat and Recent is a separate tab, not above folders.
- [ ] Verify thumbnail, duration badge, two-line title, size, resolution, partial resume line, and watched label in list/grid views.
- [ ] Try empty folders, many folders, duplicate video titles, deleted videos, removable storage, and Android 14 limited-media access. Recent must show only currently accessible videos.

### Part 3: controls and gestures

- [ ] Tap to show/hide overlays. Playing controls hide after about three seconds; seek-bar drags and open menus must remain usable.
- [ ] Verify Back/title/Tracks/PiP/overflow at the top and time/slider/duration plus Lock/Previous/Play-Pause/Next/Aspect/Rotate at the bottom. Tap total duration to switch to remaining time.
- [ ] Lock controls: swipes, double taps, pinch, long press, and Back must not alter playback. A tap reveals only the small Unlock control.
- [ ] Cycle Fit, Fill, Zoom, and 100%; verify the label and geometry. Pinch between 1x and 4x; change aspect to reset pinch zoom.
- [ ] Double-tap left/right for -10/+10 seconds with pulse/label feedback; center double-tap toggles play/pause. Check clamping at both ends.
- [ ] Drag horizontally and inspect target time/delta before release. A cancelled gesture or switching into a pinch must not commit the seek.
- [ ] Swipe vertically on each half for bounded brightness/media volume, including fixed-volume devices. The indicator stays visible while swiping and disappears afterward.
- [ ] Hold to temporarily play at 2x, release/cancel to restore the previous speed. Test cancellation by a second finger and by backgrounding.

### Part 4: browsing tools and file consent

- [ ] Sort by name/date/size/duration in both directions in Folders, folder details, and Videos. Restart and confirm choices persist. Folder numeric sort uses aggregate size/duration and newest contained date.
- [ ] Search by video title or folder name. Toggle list/grid, restart, and confirm layout choice persists.
- [ ] For each video menu, test Play, full metadata Info, and Share to an app that needs a URI read grant.
- [ ] Rename/delete an app-owned and another app's video on Android 10 and Android 11+. Approve and deny system consent; confirm a rename is applied after approval and deletion refreshes the list.
- [ ] On Android 7–9, approve/deny legacy write permission. Test invalid names, duplicate names, read-only media, and a video removed while a consent dialog is open. Failures must show an error without crashing.
- [ ] Pull to refresh and return from another app after adding/removing a video. Permission revocation must hide inaccessible content.

### Part 5: extra playback features

- [ ] Test sleep Off/15/30/45/60 minutes and end-of-video. Expiry pauses playback; end-of-video must take precedence over auto-next.
- [ ] Set A, seek forward, set B, and verify repeat. Invalid B must show guidance; Clear and switching videos remove the loop.
- [ ] Change subtitle size/color/background while embedded and external subtitles are visible. Verify persistence across rotation and reopening.
- [ ] Switch embedded audio/subtitle tracks or turn subtitles Off. Reopen the same URI after restarting the app and confirm the choice returns, with safe fallback when the track is unavailable.
- [ ] Load/cancel an SRT picker, reject non-SRT files, load after Off, rotate, reopen, and revoke document access. Confirm current playback position is preserved and the picker does not trigger unwanted PiP.
- [ ] Enable audio-only mode, grant/deny notification permission, press Home, and test notification play/pause/seek and return-to-player. Confirm no automatic PiP or hidden video decoder, and no duplicate audio on return.
- [ ] Disable audio mode, press Back, finish the video, and swipe away the task. Confirm the notification/session and player are released as documented. Rapidly toggle the mode and test notification dismissal.
- [ ] Enter/exit PiP separately; verify controls disappear there, playback continues, returning restores controls, and dismissing PiP releases playback.

### Part 6: theme and defaults

- [ ] Verify System (default), Light, and Dark appearance, blue accent, rounded cards, readable metadata, and clean VXPlayer top bar. Inspect landscape, small screens, large fonts, and system theme mode.
- [ ] In Settings, select each speed/aspect/orientation default. Open a new video and confirm it uses the setting; temporary player changes must not overwrite defaults.
- [ ] Turn auto-next off/on and finish a middle/final folder video. Confirm correct behavior without looping or losing checkpoints.
- [ ] Test polished permission-denied, partial-access, loading, failed-scan, empty, and no-search-results states.
- [ ] Clear history/resume positions: cancel once, then confirm. Verify Recent, progress lines, and watched markers disappear after returning, while defaults and track preferences remain.

### Part 7: release regression checks

- [ ] Upgrade an older installation with resume data and exercise the complete Room migration chain. Verify metadata/history/track preferences after restart.
- [ ] Install a locally signed release APK and repeat fullscreen, playback, permission, PiP, service notification, subtitle, and file-action smoke tests.
- [ ] Verify original adaptive/fallback icon, VXPlayer name/version, and a clean debug/release build and unit-test run.

## Continuation: features 6–11

- Feature 6: external SRT delay is remembered per video, in 100 ms steps (up to +/-60 seconds). Positive values show subtitles later. Timing is shifted in a private cache on a background thread; the original document is unchanged. Embedded subtitle delay is unsupported. SRT files are limited to 4 MiB.
- Feature 7 deferred: the background service borrows the navigation-scoped player. Leaving PlayerScreen releases that engine. A reliable library mini player requires service-owned playback and controller reconnection; retaining a stale player would risk lifecycle regressions.

