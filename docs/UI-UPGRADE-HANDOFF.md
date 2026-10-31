# UI upgrade handoff

Scope reduced at the user's request to finish within two minutes. No commits made.

## Step 0 - audit
- docs/DESIGN.md records inspected surfaces, existing capabilities, inconsistencies, design direction and validation gates.
- The original check hit a Gradle report access failure. Restarting the daemon resolved report writing. Two overlapping checks then collided in the Kotlin cache; a sequential offline check with incremental compilation disabled recovered successfully.

## Step 1 - implemented
- ui/DesignSystem.kt: shared motion, shape, touch-target and blue/violet gradient tokens; bundled Plus Jakarta Sans typography; tabular numeric labels; light haptic ticks for shared icon buttons; cards use subtle borders and no default shadow.
- settings/VXTheme.kt: deeper near-black layered dark surfaces; AMOLED canvas; haptic preference propagated through composition.
- settings/AppSettings.kt, SettingsParser.kt, ThemePolicy.kt: immutable settings model; AMOLED and blue defaults for new preferences; dynamic color stays off; existing preferences retained; haptic preference persisted.
- settings/SettingsScreen.kt: AMOLED choice and touch-feedback switch.
- res/font/plus_jakarta_sans.ttf and assets/licenses/PlusJakartaSans-OFL.txt: bundled font and open license.

## Verified
- Sequential .tools/check.ps1 passed assembleDebug and testDebugUnitTest after the design-system changes.
- Existing theme contrast and settings parsing tests passed as part of the suite.
- Final direct assembleDebug passed (33 seconds; all 38 tasks up to date). All 133 unit tests passed with zero failures, errors or skips.

## Remaining / skipped
- Steps 2-6 are not implemented: library redesign, cinematic player refinements, music presentation refinements, sheet rebuilds, onboarding, new icon, and broader motion/accessibility work.
- Gradient tokens exist but are not yet applied across the screens. Existing icons and many screen-local dimensions/colors remain.
- No R8 release verification, Baseline Profile, rendered-device review or frame-time measurement completed in this shortened pass.
- No Android device was attached to adb. A steady 60 fps and full feature parity have not been established by builds or unit tests.

## Phone checklist
- Scroll 500+ videos rapidly in list/grid; confirm thumbnails, refresh, search, resume and fast scrolling.
- Play local video, URL and web streams; verify controls, lock, gestures, subtitles, HW/SW, screenshots, Cast, background play and PiP.
- Rotate repeatedly during playback; check entering/leaving player and lock persistence.
- Exercise Music Now Playing, queue, mini player, next/previous and background playback.
- Switch Dark / AMOLED / Light / System, dynamic color and existing accents; restart to verify persistence.
- Check haptic toggle, system Remove animations, RTL, landscape and 130% font scale.
- Onboarding remains to be implemented and tested.

