\# VXPlayer



Android video player app (original design, not affiliated with any other player).



\## Stack

\- Kotlin, Jetpack Compose, Material 3

\- Media3 (ExoPlayer) for playback

\- MVVM architecture, Room for local data

\- Min SDK 24



\## Rules

\- All branding, icons, and strings are original.

\- Make small, focused changes. Do not touch unrelated files.

\- After each change, make sure the project builds:

&#x20; .\\gradlew.bat assembleDebug

\- Explain which files you changed.



\## Planned features

1\. Video library (folders + thumbnails) via MediaStore

2\. Player screen with Media3

3\. Gestures: brightness/volume swipe, double-tap seek, pinch zoom

4\. Playback speed, audio/subtitle tracks, external .srt

5\. Resume position, history, favorites

6\. Picture-in-picture, background audio, lock controls

7\. Network stream from URL

8\. Dark theme, settings

