# VXPlayer

An original Android video and local music player using Kotlin, Compose Material 3, Media3 ExoPlayer, MVVM, and Room. Min SDK 24, compile/target SDK 35. VXPlayer is not affiliated with MX Player; its branding, icon, colors, and UI text are original.

**No ads or tracking SDKs in VXPlayer.** Library data, settings, and frame analysis stay local. Streams and explicitly opened web pages contact their servers and embedded resources; Web Shield reduces unwanted requests but cannot guarantee that websites do not track you. Explicit exports can use your chosen document provider. VXPlayer sends no analytics or crash reports. Automatic Android cloud backup and device transfer are disabled; manual JSON export/restore remains available.

The dependency/source/manifest audit found no ad, analytics, tracking, or crash-reporting SDKs. Native playback supports direct MP4/MKV/WEBM, HLS, and DASH. HTML remains rejected by Media3, with an explicit Open web player action. At the user's later request, a separate Android WebView player now supports website video pages. INTERNET is used by explicitly opened media sessions and web pages. Android's manifest permits cleartext for user-entered HTTP hosts; the native scoped data-source factory denies network access for local playback and unregistered links. HTTP is unencrypted; HTTPS web pages cannot load mixed HTTP resources. Native connect/read timeouts are 8/12 seconds.

## Latest update — Settings, seeking, Open URL and local music

Started from a clean working tree at `05d6361`. Each implementation part has its own commit and passed `.\.tools\check.ps1` before committing. The current unit suite has **99 tests**. Final verification also guards large-queue selection indices and surfaces music errors/Retry in Now Playing. Queues use a maximum of 5,000 tracks around the selected song and disclose that limit when reached. This section supersedes the older navigation/checklists below.

### Changes

- **Part 1:** `SettingsParser.kt`, `AppSettings.kt` and Settings choice handlers tolerate missing, unknown, wrong-type and non-finite stored values. Enhance Soft/Sharp names migrate safely. `AboutCard.kt` now renders the simple launcher foreground vector instead of the adaptive mipmap, a concrete Compose crash path on Android 26+. Glyph detection has a fallback. Appearance supports Orange/Blue/Teal/Green/Purple; dynamic color overrides the accent. `diagnostics/` installs a local uncaught-exception handler, retains three redacted reports with app/Android/model/thread and a route template without arguments, calls the previous handler, shows the next-start Copy/Dismiss dialog and adds About → Last crash report. Report reads run on IO. DEBUG-only StrictMode logs disk/network access. Native crashes, force-stop and low-memory kills may not reach the Java handler.
- **Part 2:** `SeekRequests.kt`, `PlaybackTimeline.kt`, player view/model and frame sampler keep drag previews, debounce release requests, accumulate relative taps against the pending target, cancel replaced work and use CLOSEST_SYNC for swipe/double-tap; timeline release uses EXACT. Only the visible timeline polls position (300 ms), without clock state in the whole screen. Auto sampling pauses during drag/seek/buffering; PixelCopy callbacks/analysis use a worker. Forced 1 ms decoder seeks for paused enhancement repaint were removed. A five-second effects buffering stall can rebuild once without effects; an eight-second seek stall retries sync seeking once, then shows Retry/error UI. Seek saves are debounced and Room writes use IO. The remaining 120 ms delay debounces subtitle changes; it is not polling. There is no seekbar thumbnail extractor to pause.
- **Part 3:** `OpenUrlSheet.kt` and reusable network content open from the FIRST library menu item. The Network navigation tab is removed and Room link data is retained. The width-limited draggable sheet includes validation, clipboard/Paste/Clear, IME Go, formats/name/SRT, loading/Retry, favourites/recents, swipe delete/Undo and confirmed Clear all. Native streaming retains scoped HTTP access, normal User-Agent, redirects/timeouts, HLS/DASH and HTML rejection. The final clarification explicitly asked for supplied Morencius pages to play in the app's web player, so existing web playback/Shield is retained despite the contradictory direct-only sentence. Known page routes use web mode; direct media stays native. No new scraping resolver was added.
- **Part 4:** `music/` adds an all-volume local audio library, permission on first opening, search/sorts/refresh, short-track setting, exclusion of system sounds/recordings, Home/Songs/Albums/Artists/Playlists, artwork caching/placeholders and worker Palette colors, details, favourites/counts/history, playlist CRUD/drag reorder, mini player, animated Now Playing, queue drag/remove/play-next/history, release-only seeking, shuffle/repeat, share/info, supported audio profiles/bass/night compression and sleep timer. A separate MediaSessionService owns music playback, audio focus/noisy handling, notification/media controls and the persisted queue. JSON/Room writes use a serial IO actor; queue/index/position/modes restore paused. Room **5→6** adds music tables without replacing existing tables. Only READ_MEDIA_AUDIO was added. `PlaybackCoordinator.kt` pauses music for native video and every registered native video owner for music, including background video. Companion LRC highlighting/scrolling is best-effort when Android exposes the neighbouring text file through MediaStore.
- **Navigation:** Videos, Folders, Music, Recent, More, with an accent indicator and saved tab state. The mini player sits above navigation; Resume FAB clears both. More lists Settings, Private, Backup/Restore and About. The music notification opens Music/Now Playing. Local music does not download files or use an online catalogue.

Music audio-session attachment starts from the current system volume, so connecting the music service does not assume 100% volume. Check this at low volume as part of the device audio tests.

### Device checklist — Part 1

- [ ] Upgrade without clearing data. Open Settings in Light/Dark/System/dynamic themes, rotate and increase font size. Check every choice, About/vector icon/flag/version and accents. Use old Soft/Sharp, old/unknown seek names and wrong-type/garbage settings in a debug fixture: defaults load without closing the app.
- [ ] Trigger a controlled uncaught Kotlin/Java exception in a debug build, restart and check “VXPlayer closed unexpectedly last time”, Copy report, Dismiss and acknowledgement on later starts. About → Last crash report still opens it. Generate four crashes and confirm only three files remain. Check copied text for URL/token/media-path redaction. Release must not enable StrictMode.
- [ ] Check malformed backup rejection and valid old backup restore. Parsing/redaction are unit-tested; no Compose smoke/instrumented test ran because this project has no such setup or connected runtime.

### Device checklist — Part 2

- [ ] On a 2–3-hour video, swipe forward/back/cancel, double-tap rapidly and drag/tap the seek bar. Dragging updates only preview; release seeks once; a Medium short swipe stays around 20 s and playback continues smoothly. Test boundaries, unknown/zero duration and unseekable media.
- [ ] Hide/show controls and verify timeline polling stops/restarts. Repeat with Auto enhancement, high-resolution/HDR, SRT delay, paused playback, rotation, PiP and background audio. Sampling must pause while seeking/buffering and resume afterwards.
- [ ] Inject a post-seek stall: effects buffering recovery happens once around 5 s with position/play state retained. An ordinary 8 s stall retries sync seeking once, then shows readable Retry/error UI. Test a newer seek cancelling old work and leaving the player mid-seek. Physical stutter/driver timing needs a device and problem sample.

### Device checklist — Part 3

- [ ] Open URL is the first three-dot item; Network is absent from navigation. Drag/dismiss the sheet in portrait/landscape/tablet, both themes, large text and keyboard. Check clipboard chip/Paste/Clear, invalid schemes, IME Go, disabled/working Play, optional name/SRT and loading/Retry.
- [ ] Play direct MP4, HLS, DASH, signed extensionless media, HTTP→HTTPS and cross-host redirects. Test offline/timeout, 403/404, bad format and HTML. Native HTML must show “This link is a web page, not a direct video link”.
- [ ] Supplied Morencius `/file/` and `/embed/` links in Auto/Web mode open the in-app web player. Check page Play/fullscreen/Back/Shield. Website availability, browser/DRM checks, codecs and ad coverage remain device/site-dependent.
- [ ] Save/name/toggle favourites, reopen recent items, check newest-first de-duplication and the 20-item cap, delete/swipe/Undo, confirm Clear all and preserve old links after Room upgrade.

### Device checklist — Part 4

- [ ] First Music opening requests audio access on Android 24–32/33+. Test deny/permanent deny/settings return/restricted profile, empty/loading/error, pull refresh and resume rescan. Include primary/SD storage and Unicode tags/names. Ringtones/notifications/alarms/recordings are excluded. Toggle Hide tracks shorter than 30 s in Settings/empty state.
- [ ] Check metadata/art/gradient-note placeholders, search/sorts, each Home row, shuffle all, Albums/Artists grids/details/play/shuffle, rounded/circular artwork and Palette gradients. Test themes/accents, landscape/large text and state/scroll preservation across bottom tabs.
- [ ] Create/rename/delete playlists; add once, remove, long-press the handle and drag to reorder; verify cover collage and persistence. Check favourites, play counts and recents after manual/automatic next, repeat and restart.
- [ ] Mini player: tap/expand, swipe next/previous, play/pause/next and progress. Now Playing: expand/collapse/down swipe, artwork gradient, favourite/share/info, previous/next, seek, shuffle, repeat off/all/one, sleep off/15/30/60/end and supported equalizer/spatial/bass/night profiles. Unsupported effects fail gracefully.
- [ ] Queue: play next/add/reorder/remove/swipe/clear/history, including active/empty/single queues and shuffled Previous/Next. Kill/relaunch: queue/track/position/modes return paused. Deleted/revoked media gives a clear error and permits another selection.
- [ ] Accessible adjacent `.lrc`: Unicode, fractions, offsets, multiple tags, bad/empty lines; current line highlights/scrolls. If Android does not index/grant the companion text, lyrics remain unavailable and music keeps playing. No all-files permission is used.
- [ ] Test notification artwork/prev/play/pause/next, lock screen, Bluetooth/wired buttons, focus loss/calls/headset unplug, background playback, task removal and service cleanup. Test gapless-tagged supported files; decoder/output behavior is device-dependent.
- [ ] Start native video during music: music pauses. Start music during video or video background audio: every video owner pauses and background mode detaches. Rapidly switch, rotate, use PiP and reopen through the music notification: no overlapping audio or released-player callbacks.

### Device checklist — Part 5 and verification limits

- [ ] Regress fullscreen/cutouts, controls/gestures/lock, folders, video playlist/shuffle/repeat/resume/FAB, themes/accents, volume boost, Smart Enhance modes/A-B, tracks/SRT/delay, orientation/PiP, background controls, Private move/restore/delete/auth/secure screens, About/splash and backup/restore.
- [ ] Validate Room upgrade on an installed older app with existing video/network data and new music data. Runtime Settings/crash-dialog rendering, physical seek stutter, service/focus/notification/codec/gapless behavior and migration on a real installation are unverified without a connected device/emulator.

Final verification on 2026-10-30 with the current Gradle **9.7.1** wrapper: `.\.tools\check.ps1` passed (assembleDebug and **99 tests, zero failures/errors**); `assembleRelease` and release vital lint passed. Release output is unsigned without a local signing configuration. `adb devices` reported no connected device.

No feature part was intentionally skipped. Instrumented/device checks remain pending; companion lyrics remain limited by Android text-file access. Unrelated wrapper edits to Gradle 9.7.1 appeared during this run and were preserved outside the feature commits. Intermediate checks used cached Gradle 8.11.1 via the ignored local check script's optional `VX_GRADLE_EXECUTABLE` override; final checks also passed using the current wrapper without that override.

## Web player and Web Shield (earlier implementation details)

In Network, paste an HTTP/HTTPS link and choose **Open web player · Shield ON** (or the **Web page** format chip) for an embed/watch page, or **Play** for a direct stream. In Auto, `/file/<id>` and `/embed/<id>` pages on `morencius.com` and `m4uplay.quest` are recognized and the main Play button opens the web player directly. Explicit HLS/DASH overrides stay native. Signed extensionless URLs on other hosts are not guessed to be web pages. Web openings share Recent/Favourites (including the existing 20-item cap), retain the original URL/query/origin, and do not grant native streaming/subtitle access. Tapping a saved web item returns to the web player. A native HTML playback error offers the same web-player action. Web playback uses the website's HTML5 controls, including fullscreen; native Smart Enhance, playlists, PiP and resume remain features of the native player, not web pages.

The three supplied URLs (`morencius.com/file/bbw8t89l8cls`, `morencius.com/embed/bbw8t89l8cls`, and `m4uplay.quest/file/tm187qcf23hh`) returned HTTP 200 HTML during inspection. Both hosts supplied JW Player markup, first-party ad loaders and site scripts. This confirms page availability, **not Android video playback**: no device was connected, media was not played, and site stream URLs can expire. `WebPageRoutes.kt` tests exact-host/path routing and embed-only viewport sizing; rules preserve JW Player/jQuery/caption/media assets while blocking observed ad paths, the known first-party popup loader and extra tracker/ad-SDK domains. No protected player tokens are extracted or hard-coded, and no site access restrictions are bypassed. The updated unit suite has **84 tests**.

`web/WebPlayerActivity.kt` adds a separate, non-exported web screen, fullscreen/Back handling, load errors/Retry, blocked-request count and a per-session Shield toggle. `web/WebShield.kt` applies bundled domain/path rules and conservative cosmetic hiding. New windows/popups, external-app intents, downloads, uploads, camera/microphone/geolocation and third-party cookies are blocked. Cross-host top-level navigation without a user gesture is blocked with Shield ON; clicked cross-host links require confirmation. There is no native JavaScript bridge or file/content URI access; SSL errors are cancelled. Native playback is unchanged. No online filter updater, analytics or extra dependency is added.

This is **not Brave's filtering engine** and does not promise all-ad blocking. Bundled rules need maintenance; first-party/stitched video ads, obfuscated domains, service workers and WebView interception limitations can evade filtering. Strict redirects/cookies/mixed-content rules can break some hosts; Shield OFF plus Reload relaxes ad/automatic-navigation filtering, while popup/permission/file restrictions stay enabled. DRM, browser checks and unsupported codecs may still prevent a site from playing. Scripts supplied by websites necessarily execute in the web renderer.

Device checks (no device connected here):

- [ ] Open a supplied embed/watch URL through Network and the HTML error action; tap the actual video, fullscreen, rotate, Back, Reload and retry offline/HTTP errors.
- [ ] Paste each of the three supplied URLs in Auto and tap Play: go straight to the web player without a native HTML error. Tap the site's own play icon, check picture/audio/seeking/quality/tracks, save a favourite, return via Recent/Favourites, and check embed sizing. Other websites can use the Web page chip; native direct URLs and forced HLS/DASH remain native.
- [ ] Check known ad-domain requests, popunders, target-blank links, external-app redirects, blocked count, same-host navigation and confirmed cross-host links. Confirm video frames are not hidden by cosmetic rules.
- [ ] Compare Shield ON/OFF and reload with a broken site; inspect third-party cookies, SSL errors, file/content URLs, downloads, permission prompts, background audio and process restoration. WebView restrictions do not constitute a full privacy guarantee.
- [ ] Recheck native local/network playback, controls, resume, rotation, PiP, background service and Private. Web playback compatibility and actual ad coverage require a device with an up-to-date Android System WebView.

Media3 platform diagnostics reporting is explicitly disabled with `setUsePlatformDiagnostics(false)`; see the [Android API reference](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setUsePlatformDiagnostics(boolean)).

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

## Current continuation: Resume and playback audit (Parts 3–5)

This continuation started with a **clean** working tree at `448004c`. The instruction was to continue from Part 3 and leave Parts 1 and 2 committed. Those newer Open URL/library-redesign commits are not present in this checkout's refs/worktrees, which still has the earlier four-tab library; they were not reimplemented or overwritten. The Part 1/2 checks below are integration checks to run when those commits are available. No reference screenshot was available in this session.

### What changed

- **Part 3:** `ui/ResumePolicy.kt`, `ui/ResumeFab.kt`, `library/ResumeViewModel.kt` and `LibraryScreen.kt` add a floating Resume action and shared top-bar Last played action. Selection combines local/network history, prefers the newest unfinished checkpoint, replays the latest when all eligible entries are finished, and excludes Private/inaccessible files. A replay checkpoint still wins if a video was watched previously. The FAB expands with a one-line title when idle and collapses while scrolling; long press offers Play from start and Dismiss for now. Dismissal survives tab changes until a newer playback event. Click rechecks local access and reports “Video not found” if the file disappears. Play from start serializes the resume reset before opening playback.
- **Part 4 — codecs:** `PlayerViewModel.kt`, `PlaybackSafety.kt`, `PlaybackDiagnostics.kt`, and `TrackMenu.kt` enable Media3 decoder initialization fallback, keep/choose a supported audio track, label unsupported tracks, and play silently with a banner when audio is unavailable. If a supposedly supported audio decoder fails, alternative supported audio formats are tried before muting; recovery is bounded. Unsupported video errors/report fields include MIME, codec string, profile/level, dimensions, rotation, pixel aspect, frame rate/bitrate and colour/HDR details when extraction supplies them. Unknown fields remain explicitly unknown. Media3's compatible codec/base-layer handling is retained; no proprietary codec or bitstream conversion is bundled.
- **Part 4 — effects/lifecycle:** the first preparation uses ordinary video until the format is known. HDR, Dolby Vision, detected >8-bit profiles and protected sources do not prepare a video effect pipeline. A safe SDR source can warm the neutral pipeline once, preserving instant subsequent toggles. Any playback error with effects in use triggers one original/no-audiofx recovery at the current position and requested play/pause state; a hashed per-URI flag prevents repeated effects recovery and labels Smart Enhance unavailable for that video. Audiofx, Auto sampling and GPU adjustments are bypassed for recovery. Background recovery keeps the existing service alive and swaps its player; if that service is gone, it pauses rather than starting a new hidden service. Retired player callbacks cannot overwrite the replacement's state or PiP callbacks. Media3 continues to own audio focus and headset-unplug handling; those platform events need device tests.
- **Part 4 — subtitles/seeking:** `SubtitleText.kt`, `SrtTiming.kt`, and `ShiftedSubtitleCache.kt` add strict UTF-8/BOM/UTF-16 handling, local legacy-encoding detection, Windows-1252/1250/1251 and available ISCII overrides in the subtitle menu, HTML/ASS tag removal, and tolerant mixed malformed/empty cues. Unicode Indic text is supported. Encoding guesses are best-effort, so a manual override is available; legacy font-specific encodings such as Kruti Dev/Bamini still require conversion. A failed SRT is discarded/reported without blocking video. Seek math handles unknown/zero duration and overflow, non-seekable streams do not offer a working-looking seek bar, and MKV/AVI/TS-style sources prefer nearby sync frames. A missing/broken file index cannot be reconstructed universally.
- **Part 4 — library/Open with:** `MediaLibraryScanner.kt`, `MediaMetadata.kt`, `VideoLibraryViewModel.kt`, `FolderPlaylist.kt`, the manifest and `MainActivity.kt` query available external volumes, include video MIME/known-extension rows exposed by MediaStore, retain volume-specific folders, fill missing metadata on a worker, and use bounded retriever thumbnail fallback. Primary-storage video URIs remain compatible with earlier resume keys; a matching legacy SD-card URI can migrate its checkpoint after verifying the actual volume. Open video file and Android Open with handle explicitly granted, unindexed content/file URIs. External intents cannot open app-private file paths. `.nomedia` and scoped-storage restrictions are respected: use the file picker for hidden/unindexed media; no all-files permission or filesystem-wide scan is added.
- **Part 4 — reports/error UI:** `PlaybackDiagnostics.kt`, `PlaybackProblemDialog.kt`, player menus and `PlayerScreen.kt` add a scrollable Media info / Playback problem report dialog and Copy report. Reports contain app/Android/model, local basename (or generic Network/Private label), known size/format metadata, all discovered audio/subtitle tracks, supported/selected flags, effects state, exact Media3 error code/message with redaction, and decoder candidates, actual creation attempts, initialization failures and successes. Candidates are distinguished from actual attempts. URL/token/full-path/account identifiers are redacted; no report uploads or platform diagnostics are enabled. Errors have an icon, readable explanation, Retry, Play without enhance, Copy report and Back; the error panel scrolls in short layouts. Non-square pixel geometry is respected at 100% size.
- **Part 5:** this checklist and final debug/unit/release verification. The current unit suite has **76 tests**, including resume selection, codec/effects policy, safe seek math, aspect/rotation geometry, report redaction, legacy encodings and malformed/empty SRT handling.

Character detection uses the unmodified, local-only [juniversalchardet 2.0.4](https://github.com/albfernandez/juniversalchardet) library (MPL-1.1, alternatively LGPL-2.1-or-later/GPL-2.0-or-later). The 2.0.x line preserves this app's Android 24 compatibility. It adds no ads, tracking or network calls.

### Part 1 integration checklist — Open URL

These checks require the separately completed Part 1 commit, which is absent from this checkout.

- [ ] Open the shared stream component from the first three-dot-menu item and Network. Verify full-height draggable sheet, close, scrim, rounded corners, and centered max-width layout on landscape/tablet.
- [ ] Test HTTP/HTTPS validation, invalid schemes, clipboard chip, Paste/Clear, IME Go, disabled/enabled Play, progress, Retry, optional name/SRT, format hints, Advanced Auto/HLS/DASH, favourites, recent links, swipe delete/Undo and confirmed Clear all.
- [ ] Test a direct file, playlists, redirect, HTTP 403/404, offline/timeout and HTML. HTML must show “This link is a web page, not a direct video link”; no WebView is allowed. Check both themes, large fonts, 48 dp targets and keyboard scrolling.

### Part 2 integration checklist — Library redesign

These checks require the separately completed Part 2 commit, which is absent from this checkout.

- [ ] Inspect the VXPlayer logo/title/search/Last played/menu, five bottom-navigation items, accent pill and preservation of list/grid/folder scroll state across tab switches.
- [ ] Test phone/landscape/tablet column counts, rounded 16:9 cards, resolution badges, thumbnail menu, one-line title, grey duration, watched indicator, progress, fade/placeholder/cache, folder mosaics and N videos.
- [ ] Check Orange/Blue/Teal/Green/Purple with Light/Dark and dynamic override, sort, grid/list persistence, search, refresh, permissions, limited access, empty/loading/error states. Verify the new Resume FAB sits above that bottom navigation after integration.

### Part 3 device checklist — Resume

- [ ] With no history, the FAB is absent. Play local and network videos, leave midway, and return: the newest unfinished checkpoint wins; if every video finished, the latest replays from the start. Private videos never appear.
- [ ] One tap on FAB and Last played opens the same video/checkpoint. Test a previously watched video replayed halfway. Swipe/list/grid scrolling collapses the extended label; idle restores it without hiding the play icon.
- [ ] Long press: Play from start resets before playback; Dismiss for now stays dismissed when switching tabs, and a new playback event restores it. Check the small progress indicator with known duration.
- [ ] Delete/revoke access after the button appears, then tap: “Video not found” is shown and the button hides. Test app restart, media permission denial, network history without library permission, large fonts and landscape. Verify bottom-bar clearance against the Part 2 layout when available.

### Part 4 device checklist — Playback audit

- [ ] **Audio:** use files with AC3, EAC3, DTS and TrueHD plus a supported AAC/Opus alternative. Supported selection wins; unsupported rows are grey and labelled. With no supported audio, video continues silently with a codec banner. Test a decoder that advertises support but fails initialization, and confirm bounded alternative-track/mute recovery.
- [ ] **Video:** test HEVC/Main10/HDR10/HLG/Dolby Vision, VP9, AV1 and high-resolution sources. Check actual fallback decoder attempts, compatible base-layer behavior where supported, and precise codec/profile/resolution errors when no decoder works. Unknown metadata must not be invented.
- [ ] **Effects:** inject/observe a decoder, GL, source or audiofx failure while effects are active. Recovery must happen once without effects at the same checkpoint/play-pause state, remain unavailable for that URI after reopening, and avoid loops. Verify HDR/10-bit/protected sources never begin in the GL path. Repeat while paused, in PiP, during rotation, and with an existing background-audio service; if the service disappears, recovery must pause safely.
- [ ] **Subtitles:** load UTF-8 with/without BOM, UTF-16LE/BE, Windows-1252/1250/1251, Unicode Indic and available ISCII text. Test Auto and explicit encoding choices, malformed/empty/mixed cues, HTML/ASS tags, revoked grants and oversized files. Video must open even if SRT loading fails. Check subtitle delay, track selection, position and pause state after encoding changes.
- [ ] **Seek:** test indexed and poorly indexed MKV/AVI/TS/MPEG, unknown duration, zero-length, live/unseekable media, resume, swipe preview/release/cancel, double tap and seek bar. Verify approximate sync seeks are bounded and unsupported indexing/container limitations are reported rather than crashing.
- [ ] **Library:** put Unicode-named MP4/MKV/TS/M2TS/FLV/3GP/AVI/WMV files on primary and secondary volumes, including missing duration/resolution and rotated portrait metadata. Test SD removal/access revocation, fallback thumbnails and large files. Open `.nomedia`/unindexed media through the file picker or another app's Open with, without granting the full library. Confirm old primary/SD checkpoints still resume the intended file.
- [ ] **Geometry:** inspect Fit/Fill/Zoom/100% with portrait, rotated and anamorphic/non-square-pixel samples; rotate the device and use orientation lock. Do not double-apply decoder rotation.
- [ ] **Focus/noisy:** lose permanent/transient audio focus, receive/end a call, unplug wired headphones and disconnect Bluetooth. Pause/suppression and return of focus must respect the user's prior play/pause state; playback must not restart unexpectedly.
- [ ] **Report/error UI:** from Media info and the failure panel, view and copy the report. Check app/Android/model, size/container/codecs/profile/level/resolution/frame rate/bitrate/HDR, every audio/subtitle track, effects, actual decoder attempts and sanitized error. Inspect clipboard text for no URL, tokens, full path, account/device identifier or private title. Test Retry, Play without enhance, Back, large fonts and short landscape height.
- [ ] **Full regression:** folders, Network links/favourites/SRT, fullscreen/cutouts, controls/gestures/lock, PiP, resume, all themes, volume boost, Smart Enhance modes/A-B, background notification controls, tracks/SRT delay, shuffle/repeat, Private move/restore/delete/auth/lock/screen security, About, splash and manual backup/restore.

### Remaining limits and verification

Final workspace verification: `.\.tools\check.ps1` passed (assembleDebug and **76 unit tests, zero failures/errors**); `assembleRelease` and release vital lint passed. The release APK is unsigned because no local signing configuration is supplied. `adb devices` reported no connected device. Each of Parts 3, 4 and 5 has its own commit.

No failing video sample or connected device was supplied, so hardware codec/driver failures, GL/HDR handling, physical audio focus/headset behavior, SD/provider quirks, rotation, PiP/background-service replacement and rendered UI were **not reproduced on a device**. Policy/parser/redaction fixtures are unit-tested. Unindexed/`.nomedia` access remains limited by Android's permissions; proprietary codecs need a compatible device decoder; ambiguous legacy byte/font encodings can require a manual selection or conversion. Decoder fallback cannot create a decoder that is absent or reconstruct every missing index. Parts 1/2 visual integration remains unverified because their commits are not in this checkout.

## Previous six-part update: Smart Enhance, Network, and branding

All six parts are implemented; none was skipped. Each implementation part passed `.\.tools\check.ps1` before its separate commit. No device/emulator is connected, so the runtime checklist below is still unverified.

- **1 — Faster seek:** presets now use Low 60 s, Medium 120 s (default), High 240 s per full width, independent of video duration. The saved enum names are unchanged, so existing choices migrate automatically. Settings show the seconds beside every option. The existing 16 dp dead zone, axis lock, preview, release/cancel behavior, double-tap and seek bar remain.
- **2 — One-tap Smart Enhance:** the main control row has an outlined/filled, state-tinted Enhance button, mode chip, chevron, and a separate hold-to-compare A/B control. Tap toggles without opening a sheet. Long press/chevron opens modes that apply immediately without dismissing the sheet; Advanced is collapsed initially. Auto/Vivid/Cinema/Sports/Action/Anime/Cartoon/Night/Original combine video settings and supported session-bound audio effects. Remembered mode/state respects Remember enhance settings. Frame sampling is local, low-frequency, and suspended with Smart audio in PiP/background mode. The GPU pipeline is prepared with neutral parameters, allowing taps to change uniforms without a player rebuild; unsupported video paths fall back independently of supported audio.
- **3 — Direct streaming only:** source, manifest and resolved runtime dependency audit found no ad/tracking/analytics/crash-reporting SDK. The webpage player and its manifest entry are deleted. HTML MIME types and a bounded HTML prefix check fail immediately with the explicit webpage message, including after redirects. Local playback has no HTTP capability; only a registered user-entered stream session can use the scoped Media3 HTTP factory. No site scripts or web ad blocker are present. HTTP remains platform-enabled for arbitrary user-entered hosts, with access enforced in the factory for media/playlist/segment requests.
- **4 — Network tab:** Folders / Videos / Recent / Network use the existing tab transition and themes. Network has validated URL input, clipboard paste/chip, Clear, format hints/type selection, display name, optional SRT, favourites with editable names, a recent list capped at 20, individual deletion, confirmed Clear all, empty state, and per-URL resume labels. The old Open URL menu/sheet is removed. Playback uses the ordinary Media3 controls/lifecycle with buffering, timeouts, 403/404 messages, unsupported-format handling and Retry. Room schema **5** adds `network_links`; the 4→5 migration preserves all prior tables and imports the former preference-based recent URLs in order, with types and signed query strings intact. Fresh database creation also imports legacy preferences when present.
- **5 — About/splash:** the final Settings card shows the original app icon, BuildConfig version name/code, “Made in India 🇮🇳 with ❤ by Siba” with an accent heart, and the privacy line plus streaming/export clarification. A text `(IN)` fallback replaces an unavailable flag glyph. Content wraps with large fonts. AndroidX core-splashscreen uses the existing icon and a small Siba credit during its one-second exit fade, beginning when the first app frame is ready. No timer, minimum display interval, network wait, or separate splash activity holds startup. Activity restoration skips the exit animation.
- **6 — Verification:** debug build, unit suite, release build and release vital lint are checked using the workspace tools; real-device behavior still needs the checks below. A release without local signing configuration is unsigned.

### Device checks — Part 1

- [ ] On a **2-hour video**, a normal short swipe at Medium moves about **20 seconds**, matching a 5-minute video. Use an effective distance near one sixth of screen width after the 16 dp dead zone. Compare different phone densities and portrait/landscape.
- [ ] Low/Medium/High show 60/120/240 s in Settings and retain the chosen name after an upgrade/restart. Test start/end clamps, preview, release, cancellation, diagonal rejection, double-tap ±10 s and the seek bar.

### Device checks — Part 2

- [ ] One tap ON/OFF opens no sheet/dialog; icon/tint, mode chip and short feedback match. Test every mode with both video and audio on speakers/headphones; only wholly unsupported enhancement shows the unsupported message.
- [ ] Long press and chevron open the sheet; change modes repeatedly while it stays open, expand Advanced, adjust/reset each slider and use Close. Hold/release/cancel A/B: original appears only while held, with supported Smart audio restored on release.
- [ ] Auto handles bright/dark/colourful scenes without flicker; unavailable sampling uses the gentle fallback. Test HDR, protected video and unsupported GPU/audio paths without breaking playback.
- [ ] Remember ON/OFF and the last mode across videos/restarts with Remember enhance settings both enabled and disabled. Rotate with Smart Enhance ON; enter/leave PiP and background audio and check suspension/restoration, speed, tracks, resume, private playback and released audiofx/capture objects.

### Device checks — Part 3

- [ ] Open an HTML page, an HTML response behind a redirect, and an extensionless HTML response: show exactly “This link is a web page, not a direct video link” and stop without rendering the page or executing scripts. No automatic HTML retries.
- [ ] Verify offline local playback makes no network requests. Stream chosen HTTPS and HTTP MP4/MKV/WEBM/HLS/DASH links; only those sessions and their media dependencies contact servers. There are no background analytics/crash uploads or automatic platform backups. Manual export/restore still works with a chosen document provider.

### Device checks — Part 4

- [ ] Switch all four tabs smoothly in Light/Dark/System themes, including without library permission. Paste a valid/invalid clipboard URL, use the copied-link chip, Clear, type inline errors, choose format/type, and set a custom name. Nothing autoplays merely from opening the tab or detecting the clipboard.
- [ ] Play direct MP4/MKV/WEBM, HLS and DASH, including signed query strings, live and extensionless playlists with explicit type. Seek/reopen to resume by URL. Attach an SRT before opening, change it inside the player, and verify attachment/delay on reopening.
- [ ] Play over 20 distinct links, replay an older link, and restart: deduplication/newest-first order/cap hold. Remove one recent; confirm/cancel Clear all. Favourites and resume positions remain after clearing recents.
- [ ] Save via star and long press, edit the name, replay and remove favourites; favourites remain above recent links and survive recent eviction. Upgrade from schema 4 with old recent URLs/resume/history/track choices and verify all survive the migration.
- [ ] Test buffering, offline/timeout, 403, 404, expired URLs and unsupported formats. Retry after recovery. Rotate, Home/PiP, background audio, speed/track changes and repeated Back/Home cycles must preserve the ordinary player lifecycle.

### Device checks — Part 5

- [ ] Scroll to the final About card in both themes, dynamic colour and large font/display sizes. Check icon, BuildConfig version, readable accent heart, India flag or `(IN)` fallback, centered wrapping, privacy statement and its streaming/export clarification.
- [ ] Cold start on Android 12+ and Android 24–30: original logo and small Siba credit fade out gently in about one second. The UI is ready without waiting for media scans/network/permissions. Test warm resume, restored player state, rotation and disabled system animations for no repeated or stuck splash.

### Device checks — Part 6 / full regression

- [ ] Install debug and locally signed release builds. Recheck fullscreen/cutouts, folders, controls/auto-hide/lock, all gestures, PiP, resume, settings/manual backup, themes, boost, Smart Enhance, current-video background audio, tracks/subtitles/SRT delay, folder playlist, shuffle/repeat and private move/restore/delete/authentication/lock/screenshots. Inspect decoder, surfaces, audiofx, samplers and service lifecycle under repeated rotation/Home/Back.

Files changed by this pass: `player/PlaybackMath.kt`, `VideoEnhancer.kt`, `EnhanceValues.kt`, `AudioEnhancer.kt`, `AutoFrameSampler.kt`, `SmartEnhanceControl.kt`, `EnhanceSheet.kt`, `PlayerScreen.kt`, `PlayerExtraMenu.kt`, `PlayerViewModel.kt`, `DirectMediaDataSource.kt`, `PlaybackPositionStore.kt`; `network/`; `library/LibraryScreen.kt`, `StreamPageProbe.kt`; `settings/SettingsScreen.kt`, `AboutCard.kt`, `BackupCodec.kt`; `MainActivity.kt`, `ui/BrandSplash.kt`, manifest/build/dependency/branding/backup resources, and relevant unit tests. Removed `WebVideoActivity.kt` and `OpenUrlSheet.kt`.

## Earlier update: folder playback, subtitle delay, and Private

### Smart Enhance one-tap update

Smart Enhance is directly in the main player row: a tap toggles the last mode (Auto by default), long press or the chevron opens modes, and A/B holds the original without changing saved settings. The shader is prepared with neutral values so toggles update uniforms rather than rebuilding playback. Supported session-bound Virtualizer, BassBoost, Equalizer, and Night compression accompany the mode; unsupported effects are skipped independently. Manual boost/audio settings remain separate. Auto analyzes a 32×18 frame locally every 1.5 seconds on a worker thread, smooths within safe limits, and falls back to gentle fixed values when sampling is unavailable. Sampling and Smart profiles are suspended in PiP/background audio and restored afterwards. Actual effect latency and hardware support require a device.

- [ ] Tap once: ON in Auto/last mode, accented filled icon and mode chip; tap again: OFF/Original, outlined icon. No sheet or dialog opens.
- [ ] Long press and chevron both open the sheet. Try Auto, Vivid, Cinema, Sports/Action, Anime/Cartoon, Night, Original without closing the sheet; use Close. Compare video and audio through speakers and headphones; unsupported audio effects silently skip.
- [ ] Hold A/B and release/cancel: original video/audio only while held; enhanced values return without changing the remembered mode. Expand Advanced, adjust/reset sliders, and compare paused/playing.
- [ ] Test dark/bright/colorful scenes in Auto for stable, bounded changes. Test unavailable sampling/HDR/protected video and devices without audio effects: original playback remains usable; unsupported video must not disable supported audio.
- [ ] With Remember enhance settings enabled/disabled, change videos and restart. Test ON during rotation, Home/PiP, background audio, subtitle loading, private playback, and repeated decoder recreation. No audiofx or capture objects may leak.

Each part below was built and unit-tested with `.\.tools\check.ps1` before its commit. Device checks remain unchecked because `adb devices` reports no connected phone or emulator.

- **Swipe seek:** the duration-independent dp mapping is retained; the latest pass above doubles the original presets to 60/120/240 s. A 16 dp dead zone and axis lock still apply, with release-only seek and cancellation.
- **Part 2 — folder playback:** `PlaylistModes.kt`, `PlayerViewModel.kt`, player controls/menu, and settings add Off, Repeat one, Repeat folder, and Shuffle. Both player buttons cycle the remembered mode. Off follows the existing Auto-play next setting and stops at the last item. Repeat one repeats on completion; manual Next/Previous still select adjacent videos. Repeat folder wraps in both directions. Shuffle plays every folder item before beginning another cycle and Previous retraces its history (up to 1,000 entries). Navigation history survives player recreation. Sleep-at-end and the existing current-video background-audio stop behavior retain priority over folder repetition.
- **Part 3 — subtitle delay:** `TrackMenu.kt` and `PlayerViewModel.kt` reuse `SrtTiming.kt`/`ShiftedSubtitleCache.kt`, adding signed delay values and ±1 second controls alongside ±100 ms and Reset. Delay is saved per video, bounded to ±60 seconds. Reload retains position and play/pause state. Controls are hidden when embedded text is selected; only external SRT can be shifted. Malformed cues are rejected without modifying the source file.
- **Part 4 — Private:** `privatefiles/` adds a separate non-exported activity with AndroidX Biometric/device-credential authentication, a 30-second background lock, and `FLAG_SECURE`. Library menu > Private lists files; a video's overflow menu > Move to Private copies into `filesDir/private`, verifies its size, then requests removal of the public original. Android 11+ uses MediaStore deletion consent, Android 10 uses recoverable permission consent, and older devices request storage write permission. Durable records distinguish incomplete copies, pending consent, completed moves, and interrupted restores. A private copy cannot be restored/deleted while its original's deletion request is pending; use Finish move to resolve it. Restore writes and verifies a MediaStore video in Movies/VXPlayer before deleting the private copy. Transfers show bytes copied and support cancellation. Private resume positions are stored separately, carried across move/restore, and excluded from public Recent/search/Resume and JSON backup. Android backup/transfer rules exclude the private directory and its resume preferences.

Private videos are **hidden, NOT encrypted**. Uninstalling VXPlayer or clearing its data deletes them; Restore first. The first-use dialog explains this. A cancelled move can leave the gallery original visible until Finish move succeeds; the UI explicitly marks that state. Private playback uses the normal video controls, gestures, tracks, enhancement, rotation, and resume, but pauses when hidden; PiP and background audio are unavailable in Private. Ordinary library playback retains its existing PiP and background audio behavior. Force-stopping during a transfer can require reopening Private and retrying Finish move or Restore. Moves need enough free internal storage for a full copy; restores need space for a full Movies copy.

### Device checklist — Part 1

- [ ] On a **2-hour video**, at Medium sensitivity, a normal short swipe moves about **20 seconds**, matching a 5-minute video. Check portrait, landscape, and phones with different densities.
- [ ] Verify Low/Medium/High scale to approximately 60/120/240 s for the full width (minus the 16 dp dead zone). Changes take effect without restarting playback; restart the app and verify persistence.
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

### Automated verification

- `.\.tools\check.ps1` runs assembleDebug and the unit suite. Current suite: **76 tests, zero failures/errors**. Coverage includes resume eligibility, codec/effects recovery policy, unknown-duration seek and geometry, report redaction and legacy SRT handling, plus the earlier fixed seek, Smart Enhance, network, folder, backup and private-storage tests. Room migration/runtime integration still needs a device.
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
- **Enhance boundaries:** the latest Smart Enhance pass prepares a neutral shader on supported GPUs; toggles and slider changes update uniforms without rebuilding playback. Paused preview redraw is debounced. Parameters and Smart audio are bypassed in PiP/audio-only mode and restored afterward. HDR, protected video, and unsupported GPU/decoder paths fall back to ordinary video independently of available audio effects. Video processing failures rebuild once on the ordinary path while preserving state; all GL/audiofx resources belong to the player session and are released with it.

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
- Room stores per-URI resume positions, watched markers, the latest 20 distinct played videos, audio/subtitle choices, and network recent/favourite links. Migrations preserve existing data from schema versions 1 through 4 when upgrading to version 5. Completion removes the resume checkpoint while retaining a watched marker. Default settings use app preferences.
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

- Feature 8: the **Network tab** replaces Library menu > Open URL and supports direct HTTP/HTTPS files, HLS, and DASH. Select the stream type for extensionless playlists. Room stores the latest 20 URLs and named favourites. INTERNET is used only for explicit media sessions; HTTP is guarded by the scoped media data source. Webpages, DRM licensing, browser logins, and custom request headers are unsupported.
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
- Library streaming: `library/StreamUrls.kt`, `library/StreamPageProbe.kt`, `library/LibraryScreen.kt`, `network/`, and `player/DirectMediaDataSource.kt` implement the current direct-only Network tab. The former `OpenUrlSheet.kt` is removed.
- Backup: `settings/BackupCodec.kt`, `settings/BackupControls.kt`, `settings/SettingsScreen.kt`, `settings/AppSettings.kt`, and `player/PlaybackPositionStore.kt` add strict JSON, picker/confirmation UI, instant defaults reload, and serialized transactional resume import. No Room schema change is needed.
- Build/security: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, and `.gitignore` add pinned HLS/DASH/Gson dependencies, streaming access/HTTP policy, and optional local release signing.
- Tests: `SrtTimingTest.kt`, `StreamUrlsTest.kt`, `BackupCodecTest.kt`, and `PlaybackPositionStoreTest.kt` cover timing, validation/history, JSON, and import ordering/error recovery. This README records limitations and device checks.

### Automated verification for this continuation

- `.\.tools\check.ps1`: assembleDebug and 40 unit tests pass (zero failures).
- `.\gradlew.bat assembleRelease`: passes with minification/shrinking off and no local signing configuration.
- Optional signing branch: smoke-built with the existing local debug test certificate and verified using apksigner (APK v2 signature). The temporary configuration/test-signed APK was removed, and unsigned release output rebuilt. This is not verification with a real distribution key; no release key was created or committed.
- `adb devices`: no connected device. All unchecked runtime/device items above remain unverified.
