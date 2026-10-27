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

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Device checks:

- Android 24–32: grant or deny storage access; verify recovery through app settings.
- Android 33+: grant or deny video access.
- Android 34+: select limited videos, then use **Choose videos** to change the selection.
- Verify folders, thumbnails, durations (including hour-long videos), file sizes, and an empty library.
- Rotate the device and return from settings after revoking access; stale videos should disappear.
- Add or remove a video, then refresh or leave and return to the app.
- Tap a video: verify fullscreen playback, title, play/pause, seeking, and Back returning to the library.
- Rotate while playing and while paused: verify the title, position, and play/pause state survive.
- Double-tap the video background on the left/right to seek backward/forward 10 seconds, including near the start/end.
- Swipe up/down on the left half to adjust brightness and on the right half to adjust media volume; verify the indicator disappears after release.
- Verify single taps still toggle controls, play/pause and seek-bar dragging still work, and horizontal swipes do not change brightness or volume.
- Rotate after a brightness adjustment, then return to the library: verify player brightness survives rotation and library brightness returns to its original setting.
- Press Home or lock the device: verify playback stops; return and verify the saved position resumes.
- Open an unavailable or unsupported video: verify an error appears and Back remains available.

The screen lists MediaStore-indexed videos accessible to the app. Playback uses Media3 ExoPlayer; its resources are released on stop and screen exit. Rotation preserves position and play/pause state without retaining an Activity or video surface.
