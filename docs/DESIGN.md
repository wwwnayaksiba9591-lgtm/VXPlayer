# VXPlayer design audit

## Current inconsistencies
- Theme lives in settings/VXTheme.kt; typography and shapes in ui/DesignSystem.kt. There are no Color.kt, Theme.kt or Type.kt files.
- Orange defaults compete with blue player action fills and artwork-derived music colors. Dark surfaces are blue-grey rather than near black.
- Library, music, settings and sheets mix literal spacing, corner radii and filled icons. Shared cards still use shadows.
- Library search is conditional, the title is compact, and loading uses a spinner. List/grid, refresh and fast scrolling already exist.
- Player already isolates timeline reads and fades controls; quick actions use a fixed blue active fill. Confirmations use platform toasts.
- Music already extracts artwork colors and isolates progress reads, but uses local fallback colors and uneven control spacing.
- Settings is a long ungrouped column; theme choices omit AMOLED. Sheets inherit Material shapes but use local spacing.
- Navigation uses fades; splash API exists. Complete onboarding and device accessibility/performance validation remain required.

## System to apply
- AMOLED by default: near-black canvas, tinted layered surfaces, bright readable text; clean light and system modes. Optional dynamic color stays off. Preserve existing preferences.
- Sparse electric-blue/violet brand gradient, semantic Material colors, AA text contrast. No live blur over video.
- One bundled open-license font; bold titles, readable body, tabular time numerals.
- 4/8 dp grid; 16-20 dp cards, 28 dp sheets, pill buttons, subtle 1 dp borders, 48 dp touch targets.
- Rounded Material icons, original adaptive logo and monochrome layer.
- Motion tokens 120/200/320 ms; transform/opacity animations, system motion scaling, haptic preference.
- Stable lazy keys; async target-sized cached artwork; timeline updates confined to progress/time components.

## Verification
Run .tools/check.ps1 after each step and assembleDebug at completion. Run an R8 release build. Physical-device frame timing, 500+ media scrolling, RTL, 130% font scaling, permissions and playback feature regression checks are required before claiming 60 fps or full feature parity.
