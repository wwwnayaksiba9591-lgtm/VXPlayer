# VXPlayer

Android video library built with Kotlin, Compose, and a ViewModel. Requires JDK 17 and Android SDK 35. Set `JAVA_HOME` to JDK 17 and configure the SDK using `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.

Build on Windows:

```powershell
.\gradlew.bat assembleDebug
```

This workspace has development tools under the ignored `.tools` directory. To use them from PowerShell:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$PWD/.tools/gradle-home"
$env:ANDROID_USER_HOME = "$PWD/.tools/android-user"
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. VXPlayer uses version name `1.1.0` and version code `2`.

Run unit tests and build the release APK with the same environment:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

Release minification and resource shrinking are disabled. The release output is `app/build/outputs/apk/release/app-release-unsigned.apk`; distribution requires your own release signing key.

Device checks:

- Android 24–32: grant or deny storage access; verify recovery through app settings.
- Android 33+: grant or deny video access.
- Android 34+: select limited videos, then use **Choose videos** to change the selection.
- Verify folders, thumbnails, durations (including hour-long videos), file sizes, and an empty library.
- Rotate the device and return from settings after revoking access; stale videos should disappear.
- Add or remove a video, then refresh or leave and return to the app.
- Tap a video: verify fullscreen playback, title, play/pause, seeking, and Back returning to the library.
- Rotate while playing and while paused: verify the title, position, and play/pause state survive.
- Use the speed button to select 0.5x, 0.75x, 1x, 1.25x, 1.5x, or 2x; verify the chosen rate survives rotation and background/foreground transitions.
- Seek in two different videos, leave each, then reopen: verify each resumes at its own saved position, including after restarting the app.
- Let a video finish, then reopen it: verify it starts at the beginning. Repeat after rotation or backgrounding on the end screen.
- Double-tap the video background on the left/right to seek backward/forward 10 seconds, including near the start/end.
- Swipe up/down on the left half to adjust brightness and on the right half to adjust media volume; verify the indicator disappears after release.
- Verify single taps still toggle controls, play/pause and seek-bar dragging still work, and horizontal swipes do not change brightness or volume.
- Rotate after a brightness adjustment, then return to the library: verify player brightness survives rotation and library brightness returns to its original setting.
- On devices without PiP support or with PiP disabled in system settings, press Home: verify playback stops and returns to its saved position. Lock the device and verify no hidden playback continues after the activity stops.
- Open an unavailable or unsupported video: verify an error appears and Back remains available.

New-feature device checklist (not yet verified on hardware):

- Use a video with several embedded audio/subtitle tracks. In **Tracks**, switch audio languages, select subtitles, then choose **Off**. Verify unsupported tracks are disabled and videos without embedded tracks remain playable.
- Select **Load subtitle file** and pick a readable `.srt` document. Verify captions appear at the current position, rotation and background/foreground preserve the attachment, and **Off** hides it. Test cancelling the picker, picking a different file type, loading after subtitles were turned off, and loading another SRT. Imported subtitles apply only to the current video.
- Use **Lock rotation**, physically rotate the device, then unlock. Use **Portrait / landscape** in both directions. Verify playback position, paused state, speed, and track selections survive; returning to the library restores the original orientation policy. Some large-screen devices may ignore orientation requests.
- During playback, tap the video background: verify controls and system bars appear. Tap again or wait for controls to hide: verify system bars hide. Verify Back still works and system bars return in the library.
- On Android 26+ devices supporting PiP, press Home during playback and separately tap **PiP**. Verify video/audio continue, app controls disappear from the small window, and expanding the window restores title, controls, speed, selected tracks, and position. Test dismissing PiP, pausing before Home, disabling PiP in system settings, and opening the subtitle picker without unintended PiP entry. Android 24–25 and devices without PiP retain normal stop/resume behavior.
- Open the middle video in a folder. Use **Previous** and **Next**, verifying folder order and per-video resume positions. Finish a video: verify automatic advance is on by default. At the first/last video, verify the unavailable direction is disabled and playback stops at the end rather than looping. Test advancing in PiP and after rotation; external subtitles must not carry over to another video.
- Play at least 21 different accessible videos and verify **Recently played** appears above folders, newest first, with at most 20 distinct videos. Replay an older video and check it moves to the top without duplication. Restart the app and verify history persists. Remove a video or revoke access and verify it disappears from the visible list.
- Upgrade an installation with the previous Room schema: verify existing resume positions survive while the history table is added. Actual database migration remains a device check; unit tests cover persistence-command ordering and playlist boundaries.
- Check the original VXPlayer icon on Android 24–25 and on launchers using adaptive round/square masks. Verify the app name and version, and install a separately signed release build for a playback smoke test.

The library lists accessible MediaStore-indexed videos. Playback resources are released when the player screen exits or the activity is stopped, while visible PiP continues playing. Orientation changes resize the Compose UI and video surface without recreating the activity; saved state also preserves the active playlist video, position, play/pause state, speed, and track choices if the activity is recreated. Room stores resume positions by URI and the last 20 played videos; completion clears a video's resume position. Folder playlists follow the library's title ordering and automatically advance without wrapping.
