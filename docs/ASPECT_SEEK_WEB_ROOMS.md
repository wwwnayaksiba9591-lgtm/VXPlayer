# Aspect, seek, web and Private fixes

Nothing was committed. Existing working-tree edits were preserved.

## Files by task

Kotlin paths are under `app/src/main/java/com/vxplayer/`; tests are under `app/src/test/java/com/vxplayer/player/`.

1. Aspect: `player/AspectMode.kt`, `player/GesturePlayerView.kt`, `player/PlayerScreen.kt`, `ui/PlayerControls.kt`, `settings/SettingsScreen.kt`; `AspectModeTest.kt`.
2. Seeking: `player/PlayerViewModel.kt`, `player/SeekSessionPlayer.kt`, `player/PlaybackTimeline.kt`, `player/PlayerScreen.kt`, `music/VideoSessionHandoff.kt`; `TenSecondSeekTest.kt` and existing seek tests.
3. Web: `library/StreamUrls.kt`, `library/StreamPageProbe.kt`, `network/NetworkTab.kt` (the shared component used by `network/OpenUrlSheet.kt`), `web/WebPlayerActivity.kt`, `web/WebShield.kt`, `web/WebPageRoutes.kt`, `web/BrowserUserAgent.kt`, `player/DirectMediaDataSource.kt`, `player/PlayerViewModel.kt`, `MainActivity.kt`, `settings/SettingsScreen.kt`; `assets/web-blocklist.txt`, manifest; `UrlInputTest.kt`, `StreamProbeIntegrationTest.kt`, `WebPageRoutesTest.kt`, `WebShieldTest.kt`, `BrowserUserAgentTest.kt`.
5. Reported launcher crash: app build file and version catalog explicitly select Fragment 1.8.6 rather than the transitive 1.2.5; `ActivityResultCompatibilityTest.kt` guards against the legacy request-code validator.
6. Private Folder: `privatefiles/PrivateActivity.kt`, `PrivateScreen.kt`, `PrivateViewModel.kt`, `PrivateRepository.kt`, `PrivateFilePolicy.kt`, player view model; `PrivateFilePolicyTest.kt`.

The requested paths `library/OpenUrlSheet.kt` and `player/WebVideoActivity.kt` are named `network/OpenUrlSheet.kt` and `web/WebPlayerActivity.kt` in this checkout.

## Device checklist

- **Crash regression:** on the Infinix Android 15 device, library menu > Open video file. Open and cancel the picker repeatedly. Repeat subtitle attachment, backup/restore, notification/audio permissions and Private deletion consent. No “lower 16 bits” crash should occur.
- **Aspect:** cycle Fit, Fill, Zoom, 16:9, 4:3, Original in portrait and landscape with landscape and portrait sources. Confirm the label/icon and visible size change. Pinch, rotate through all four directions, enter/leave PiP, switch videos and restart the app. Original is source-pixel sizing, so a large source may be cropped.
- **Seek:** use +10/-10 buttons, double tap, swipe, seek bar, keyboard arrows/rewind and headset seek. Check MKV H.264, HEVC, a cue-less MKV, MP4, AVI, WEBM, TS, 3GP, MOV and a multiple-audio-track file. Test near start/end, paused, buffering and unknown duration. Check rapid repeated taps accumulate, one tap is not dropped, and a failure produces feedback rather than freezing.
- **Open URL:** direct MP4/M3U8, ordinary HTTPS video page, HTTP-only page, redirecting page, bare `example.com`, pasted descriptive text and Android Share/Open-with links. Inconclusive probes should go to the web player. Probe redirects are bounded and GET is used rather than depending on HEAD.
- **Web:** test play/fullscreen/back, third-party-cookie playback, mixed-content compatibility, desktop mode, reload, browser chooser and error/retry screens. Test SSL cancellation and explicit Proceed, an external-app link, popup and forced-redirect pages. Toggle blocking in Settings and toolbar; verify the blocked counter. Edit the asset and rebuild to add blocked hosts.
- **Native handoff:** open a non-DRM page that requests MP4/MKV/WEBM/HLS/DASH and check Play in VXPlayer appears. Start it and check Referer/User-Agent forwarding. A detected URL can be an advertisement or inaccessible/signed resource: detection does not establish that native playback will succeed. DRM identifier requests are denied with a clear message.
- **Private:** accept the warning, unlock with biometric and with device PIN/pattern, cancel unlock, leave for over 30 seconds and return. Move a large file, cancel a transfer, deny and approve Android deletion consent, play/resume, restore to Movies/VXPlayer, delete and lock. Check intact videos remain visible if a journal is damaged; damaged files are kept. No encryption is claimed, and uninstall/data-clear still deletes private storage.

## What remains device-dependent

An index-less MKV is not made seekable by constant-bitrate flags for other extractors. Media3 must expose a usable seek map; truly unseekable files now give feedback. No claim is made that every malformed container or device codec can seek without delay. Hardware/decoder stalls still need real samples and logs.

Web filtering is a small best-effort host/path blocker, not a complete browser blocker. First-party/server-inserted ads can remain, websites can change, mixed content/autoplay depends on WebView, and DRM/blob-only sources cannot be extracted as an ordinary media URL. HTTPS is preferred for bare domains; HTTP fallback is attempted for connection/timeout failures, not silently for certificate failures. Public HTTP support remains enabled for explicitly opened links.

The Private unlock changes and launcher fix were not exercised on the reported Infinix device because ADB reported no connected devices. Corrupt journal records are isolated, not reconstructed; their files are retained for recovery.
