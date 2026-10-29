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
- Release without local signing config: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release with local signing config: `app/build/outputs/apk/release/app-release.apk`

Release minification and shrinking are disabled. App version is `1.1.0` (code `2`). Each implementation part is built, unit-tested, and committed separately.

For signed releases, create a local `keystore.properties` in the repository root, using Java properties syntax. Supply the keys `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; `storeType` is optional. The keystore path is resolved relative to the repository root, or may be absolute. Use forward slashes in Windows paths (or escape backslashes). Keep the actual values local. With no properties file, release builds stay unsigned; an incomplete file fails with a field-name-only error rather than silently switching keys. The properties file and common keystore extensions are gitignored. Do not force-add credentials or keystores. Distribution requires your own release key; see [Android signing guidance](https://developer.android.com/studio/publish/app-signing).

## Current update: seek, folder playback, subtitle delay, and Private

### Smart Enhance one-tap update

Smart Enhance is directly in the main player row: a tap toggles the last mode (Auto by default), long press or the chevron opens modes, and A/B holds the original without changing saved settings. The shader is prepared with neutral values so toggles update uniforms rather than rebuilding playback. Supported session-bound Virtualizer, BassBoost, Equalizer, and Night compression accompany the mode; unsupported effects are skipped independently. Manual boost/audio settings remain separate. Auto analyzes a 32×18 frame locally every 1.5 seconds on a worker thread, smooths within safe limits, and falls back to gentle fixed values when sampling is unavailable. Sampling and Smart profiles are suspended in PiP/background audio and restored afterwards. Actual effect latency and hardware support require a device.

- [ ] Tap once: ON in Auto/last mode, accented filled icon and mode chip; tap again: OFF/Original, outlined icon. No sheet or dialog opens.
- [ ] Long press and chevron both open the sheet. Try Auto, Vivid, Cinema, Sports/Action, Anime/Cartoon, Night, Original without closing the sheet; use Close. Compare video and audio through speakers and headphones; unsupported audio effects silently skip.
- [ ] Hold A/B and release/cancel: original video/audio only while held; enhanced values return without changing the remembered mode. Expand Advanced, adjust/reset sliders, and compare paused/playing.
- [ ] Test dark/bright/colorful scenes in Auto for stable, bounded changes. Test unavailable sampling/HDR/protected video and devices without audio effects: original playback remains usable; unsupported video must not disable supported audio.
- [ ] With Remember enhance settings enabled/disabled, change videos and restart. Test ON during rotation, Home/PiP, background audio, subtitle loading, private playback, and repeated decoder recreation. No audiofx or capture objects may leak.

Each part below was built and unit-tested with `.\.tools\check.ps1` before its commit. Device checks remain unchecked because `adb devices` reports no connected phone or emulator.

- **Part 1 — swipe seek:** `PlaybackMath.kt`, `GesturePlayerView.kt`, `PlayerScreen.kt`, and the settings/backup files replace duration-based seeking with a dp-based fixed time scale. Settings > Gestures > Seek swipe sensitivity offers Low (30 s), Medium (60 s, default), and High (120 s) per screen width. A 16 dp dead zone is removed from the effective distance; diagonal movement must clearly favor an axis before it locks. Preview updates while dragging, release seeks, cancellation discards the target. Double-tap and the seek bar retain their existing behavior.
- **Part 2 — folder playback:** `PlaylistModes.kt`, `PlayerViewModel.kt`, player controls/menu, and settings add Off, Repeat one, Repeat folder, and Shuffle. Both player buttons cycle the remembered mode. Off follows the existing Auto-play next setting and stops at the last item. Repeat one repeats on completion; manual Next/Previous still select adjacent videos. Repeat folder wraps in both directions. Shuffle plays every folder item before beginning another cycle and Previous retraces its history (up to 1,000 entries). Navigation history survives player recreation. Sleep-at-end and the existing current-video background-audio stop behavior retain priority over folder repetition.
- **Part 3 — subtitle delay:** `TrackMenu.kt` and `PlayerViewModel.kt` reuse `SrtTiming.kt`/`ShiftedSubtitleCache.kt`, adding signed delay values and ±1 second controls alongside ±100 ms and Reset. Delay is saved per video, bounded to ±60 seconds. Reload retains position and play/pause state. Controls are hidden when embedded text is selected; only external SRT can be shifted. Malformed cues are rejected without modifying the source file.
- **Part 4 — Private:** `privatefiles/` adds a separate non-exported activity with AndroidX Biometric/device-credential authentication, a 30-second background lock, and `FLAG_SECURE`. Library menu > Private lists files; a video's overflow menu > Move to Private copies into `filesDir/private`, verifies its size, then requests removal of the public original. Android 11+ uses MediaStore deletion consent, Android 10 uses recoverable permission consent, and older devices request storage write permission. Durable records distinguish incomplete copies, pending consent, completed moves, and interrupted restores. A private copy cannot be restored/deleted while its original's deletion request is pending; use Finish move to resolve it. Restore writes and verifies a MediaStore video in Movies/VXPlayer before deleting the private copy. Transfers show bytes copied and support cancellation. Private resume positions are stored separately, carried across move/restore, and excluded from public Recent/search/Resume and JSON backup. Android backup/transfer rules exclude the private directory and its resume preferences.

Private videos are **hidden, NOT encrypted**. Uninstalling VXPlayer or clearing its data deletes them; Restore first. The first-use dialog explains this. A cancelled move can leave the gallery original visible until Finish move succeeds; the UI explicitly marks that state. Private playback uses the normal video controls, gestures, tracks, enhancement, rotation, and resume, but pauses when hidden; PiP and background audio are unavailable in Private. Ordinary library playback retains its existing PiP and background audio behavior. Force-stopping during a transfer can require reopening Private and retrying Finish move or Restore. Moves need enough free internal storage for a full copy; restores need space for a full Movies copy.

### Device checklist — Part 1

- [ ] On a **2-hour video**, at Medium sensitivity, a normal swipe of roughly one quarter of the screen moves about **10–20 seconds**, matching a 5-minute video. Check portrait, landscape, and phones with different densities.
- [ ] Verify Low/Medium/High scale to approximately 30/60/120 s for the full width (minus the 16 dp dead zone). Changes take effect without restarting playback; restart the app and verify persistence.
- [ ] Small movements below 16 dp and diagonal swipes do not seek. Once horizontal seeking starts, vertical drift does not change brightness/volume. Verify reverse-direction dragging and clamps at zero/end.
- [ ] Observe position and signed delta during a swipe; playback position changes only on release. Cancel via a second finger, pinch, or interrupted touch and verify no pending seek is applied. Double-tap still seeks ±10 s; seek bar and vertical volume/brightness gestures work as before.

### Device checklist — Part 2

- [ ] Cycle Off → Repeat one → Repeat folder → Shuffle from both the control row and menu. Verify the icon/label and persistence in Settings and after app restart.
- [ ] With a folder containing at least four videos, test manual Next/Previous and completion in each mode. Off honors Auto-play next; Repeat one repeats the current video on completion; Repeat folder wraps at both ends.
- [ ] Shuffle visits every item before repeating the folder, without an immediate duplicate across cycles when alternatives exist. Previous retraces played videos and Next follows that forward history before drawing another item.
- [ ] Test a single-video folder, a missing/deleted file, and empty library. Open from a folder and from Recent; ensure the intended folder playlist is used. Verify per-video resume and shuffle history across rotation/player recreation.
- [ ] Sleep-at-end stops even in a repeat mode. Normal current-video background audio still stops at its end as before; no unexpected hidden autoplay is introduced.

### Device checklist — Part 3

- [ ] Load an external SRT. Use −100/+100 ms, −1/+1 s, and Reset; verify a signed value such as +300 ms. Positive values show captions later. Test ±60 s limits and rapid taps.
- [ ] Change delay while playing and paused: playback position, play/pause, speed, audio track, and subtitle Off remain correct. Rotate, reopen the same video, and open a different video to verify independent persistence.
- [ ] Select embedded subtitles while an SRT is attached: delay buttons disappear and the external-only message remains. Switch back to the SRT and verify the controls return.
- [ ] Test UTF-8/UTF-16 SRT, cues crossing zero/hour boundaries, cues shifted entirely before zero, malformed timing, oversize files, and revoked document access. Failures preserve the original subtitle and ordinary playback.

### Device checklist — Part 4

- [ ] First open shows the hidden/not-encrypted, uninstall/data-clear warning. Cancel keeps the screen closed; accept once and verify it is remembered. Without a configured device lock, Private remains locked with setup guidance.
- [ ] Unlock with a biometric and separately with device PIN/pattern/password. Cancel/fail authentication, rotate during the prompt, and restart the process: no private titles/video frames appear before authentication.
- [ ] Leave Private for under 30 seconds, then for over 30 seconds. Verify the longer absence requires reauthentication. Rotate during private playback without losing state. Use Lock to lock immediately. Screenshots, recordings, and the recent-app preview must not expose private content.
- [ ] Move a large public video while watching transfer progress. Approve deletion consent and verify the original disappears from gallery/library, including public Recent/search/Resume. Reopen Private and play using normal controls, seek, rotate, change tracks/subtitles, and verify saved position. Public PiP/background audio still work; Private pauses when hidden.
- [ ] Cancel the copy, deny storage permission, cancel Android deletion consent, fill internal storage, or revoke source access. The original must remain available unless removal was approved after a verified private copy existed. Unfinished moves remain clearly labelled; retry Finish move to complete them.
- [ ] Kill/restart during copy, after copy but before consent, and after approved source deletion. Recover completed copies and retry unfinished moves. A pending deletion request must prevent deleting/restoring its only verified private copy until resolved.
- [ ] Restore to Movies/VXPlayer, verify file size/playback/resume in the public library, then confirm the private copy is removed. Cancel or interrupt Restore and retry; preserve the private source and avoid duplicate published copies after recovery. Check Android 24–28 permission handling, Android 29 recoverable consent, and Android 30+ delete requests separately.
- [ ] Delete a private video with confirmation; cancel once. Deleting an unfinished private copy must not delete its remaining gallery original. Inspect JSON export and Android backup rules: private bytes and private resume positions are excluded. Use disposable media if testing uninstall/data-clear deletion.

### Final verification — Part 5

- `.\.tools\check.ps1` runs assembleDebug and the unit suite. Current suite: **57 tests, zero failures/errors**, including the earlier pending stream-page detection tests. New coverage includes fixed seek mapping/dead zone, folder modes/shuffle history, SRT boundary/malformed timing, and private path/copy/recovery/lock policy.
- `.\gradlew.bat assembleRelease` also **passes**, including release vital lint. This workspace has no local signing configuration, so the output is `app/build/outputs/apk/release/app-release-unsigned.apk`; a production signing certificate and installation are separate device checks.
- [ ] Install debug and locally signed release builds. Recheck folder browsing, fullscreen/cutouts, control auto-hide/lock, all gestures, rotation/orientation lock, audio/subtitle tracks, external SRT, resume, settings/backup, themes, volume boost, enhancement, normal PiP, background audio, folder playlist navigation, and repeated Home/Back/rotate cycles. Confirm decoder, surfaces, audio effects, and service release correctly.
- No implementation part was skipped. Runtime authentication, MediaStore consent and file operations, subtitle rendering, playback/decoding, gesture feel, rotation/PiP, screenshot protection, and real release installation remain **unverified without a connected device**.

## Earlier five-part theme, GUI, audio, and enhance update

The earlier five-part pass added themes, GUI, audio boost, and video enhancement. The continuation below adds external SRT delay, streaming (including INTERNET permission), JSON backup/restore, and optional release signing. Casting, private folders, bookmarks, and other standalone wishlist items are not supplied by that five-part pass.

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
- External SRT delay uses a custom loading path that shifts cue timings before passing a cached SRT to Media3. Embedded subtitle delay remains unsupported: the pinned Media3 1.6.1 has no public subtitle timestamp offset setter. See the [pinned Media3 source](https://github.com/androidx/media/blob/1.6.1/libraries/common/src/main/java/androidx/media3/common/MediaItem.java).
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

- Feature 8: Library menu > Open URL supports HTTP/HTTPS files, HLS, and DASH. Select the stream type for extensionless playlists. Recent URLs are limited to 20 and can be cleared. INTERNET is requested only for streaming; HTTP cleartext is enabled for user-supplied HTTP links. DRM licensing, browser logins, and custom request headers are unsupported.
- Feature 9 was deferred in this earlier continuation. The current update above supplies authenticated private storage, consent-aware removal, durable transfer records, and resume migration. Private storage is not encrypted.
- Feature 10: Settings > Backup and restore exports portable playback defaults, appearance/audio/enhance preferences, library layout/sort, external SRT delays, and Room resume positions. Restore validates the whole versioned JSON first, then asks for confirmation and merges by key/URI. The Room merge is transactional and ordered with pending saves; preferences are a separate storage operation. Existing history/track choices remain. No videos, document grants, recent URL list, or warning acknowledgements are backed up. Resume positions may contain complete stream URLs/tokens. Files are unencrypted, limited to 4 MiB, 10,000 positions and 2,048 settings. A URI must still refer to the same accessible video on the target device.
- Feature 11: release signing is optional via local, ignored keystore.properties. Missing config produces an unsigned APK; incomplete config fails clearly. No release key or real signing credential is supplied by this repository.

### Continuation device-check checklist

Features 1–5 were left outside this continuation as requested. This checkout started from the earlier theme/enhancement commits; it did not contain commits for the new thumbnail-preview/bookmark/screenshot/shuffle/equalizer features. This continuation does not claim to implement those features.

- [ ] **6 — SRT delay:** load an external UTF-8 SRT, then UTF-16 with BOM; use +/-100 ms and Reset. Positive delay must show cues later. Test a cue crossing an hour boundary, a negative shift crossing zero, and a file with all cues shifted before zero. Embedded tracks must remain unchanged and the limitation must be visible.
- [ ] **6 — playback regression:** adjust delay while paused/playing; position, speed, selected audio, and subtitle Off must remain correct. Seek, rotate, PiP, toggle audio-only mode, change folder videos, and reopen each video. Verify per-URI delay persists. Test malformed/oversized SRT, revoked grant, replacement file, rapid taps, and picker return during player recreation; failures must not crash playback or change the original file.
- [ ] **7 — deferred:** verify existing current-video background audio still plays on Home with notification controls and stops on Back as documented. A library mini player is not available until service ownership is refactored.
- [ ] **8 — URLs:** with and without library media permission, open HTTPS MP4, HTTP MP4, HLS .m3u8 and DASH .mpd, including query parameters. Test extensionless playlists with explicit HLS/DASH. Verify fullscreen, audio/subtitle tracks, rotation, PiP, background audio, speed, seek, and VOD resume. Check live streams without a seekable timeline.
- [ ] **8 — failures/history:** try malformed links, unsupported schemes, expired links/HTTP errors, unsupported media, offline connection, and Retry after connectivity returns. Cancel the sheet. Open over 20 distinct links; verify deduplication, recent ordering, persistence, selection, and Clear. Verify the HTTP/token-storage explanation. Browser logins, DRM license configuration, and custom headers are unsupported.
- [ ] **9 — now implemented:** use the current Part 4 checklist above for authentication, verified move/restore/delete, interruption recovery, consent, and lock checks. Also recheck existing public MediaStore rename/delete and folder browsing.
- [ ] **10 — backup:** change theme/defaults/audio/enhance/sort/layout and save positions in multiple videos. Export through local and cloud document providers; cancel once and verify the app remains usable. Inspect JSON for settings/resume only; it must contain no media bytes or signing credentials. Treat file paths and URL tokens as private.
- [ ] **10 — restore:** validate, cancel, then confirm import. Settings apply immediately, matching positions overwrite, other positions/history remain, and resume works when URIs/grants match. Try missing videos, a new device, malformed/version-mismatched/oversized/duplicate-field/out-of-range files and denied provider access. Invalid input must make no changes; test low-storage failure and the partial-restore message. Rotate during picker, validation, and confirmation.
- [ ] **11 — release:** with no keystore.properties, build assembleRelease and confirm unsigned output. With a valid local release key/config, build again and verify the APK certificate with apksigner, then install/upgrade using the same real key. Test missing fields/wrong key path gracefully fail. Confirm git ignores all local signing files and no secrets are staged.
- [ ] **Full regression:** install debug and locally signed release builds; repeat fullscreen/cutout, folder playlist, gestures, tracks, resume, theme contrast, 200% boost warning, enhancement presets, rotation, PiP, background audio, and lifecycle release checks from the earlier sections.

Automated checks cover SRT timing, URL validation/history policy, strict JSON round trips and rejection, and persistence import ordering/failure recovery. Runtime decoding/network connections, subtitle rendering, document providers, audio hardware, GPU effects, PiP/rotation, and installation with a real release key still require a device/emulator.

### Files changed in this continuation

- Player subtitle loading/controls: `player/SrtTiming.kt`, `player/ShiftedSubtitleCache.kt`, `player/TrackMenu.kt`, and `player/PlayerViewModel.kt`. PlayerViewModel/PlayerScreen also supply readable stream errors and Retry.
- Library streaming: `library/StreamUrls.kt`, `library/OpenUrlSheet.kt`, and `library/LibraryScreen.kt` implement validation, bounded recent links, and the Open URL action without changing folder navigation.
- Backup: `settings/BackupCodec.kt`, `settings/BackupControls.kt`, `settings/SettingsScreen.kt`, `settings/AppSettings.kt`, and `player/PlaybackPositionStore.kt` add strict JSON, picker/confirmation UI, instant defaults reload, and serialized transactional resume import. No Room schema change is needed.
- Build/security: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, and `.gitignore` add pinned HLS/DASH/Gson dependencies, streaming access/HTTP policy, and optional local release signing.
- Tests: `SrtTimingTest.kt`, `StreamUrlsTest.kt`, `BackupCodecTest.kt`, and `PlaybackPositionStoreTest.kt` cover timing, validation/history, JSON, and import ordering/error recovery. This README records limitations and device checks.

### Automated verification for this continuation

- `.\.tools\check.ps1`: assembleDebug and 40 unit tests pass (zero failures).
- `.\gradlew.bat assembleRelease`: passes with minification/shrinking off and no local signing configuration.
- Optional signing branch: smoke-built with the existing local debug test certificate and verified using apksigner (APK v2 signature). The temporary configuration/test-signed APK was removed, and unsigned release output rebuilt. This is not verification with a real distribution key; no release key was created or committed.
- `adb devices`: no connected device. All unchecked runtime/device items above remain unverified.
