# Updater Material 3 Refresh

## Goal

Make the update prompt and download progress read as one Material 3 surface,
with emphasized-easing window motion and a smooth hand-over from downloading to
installing.

## Non-goals

- No change to update checking, download, verification or installer flow.
- No settings redesign (separate PR), no release or publication work.

## Current context

Base is master 71356a010. `UpdateAvailableDialog` inflates
`dialog_update_available.xml` inside a transparent window sized by
`UpdateDialogWindow`; `UpdateDialogController` renders download, permission,
installing and failure dialogs from `UpdateManager` state. The prompt mixed
hardcoded text sizes with legacy MaterialComponents text buttons (wide letter
spacing) and the windows appeared without motion.

The app themes override only `colorPrimary`, so `colorPrimaryContainer` stays
baseline purple and clashes on Light, Blue and AMOLED. Tonal fills are derived
from `colorPrimary` at 16% instead.

## Files likely involved

- `app/src/main/res/layout/dialog_update_available.xml`, `dialog_update_download.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/common/Update*.kt`
- New drawables, interpolators, window animations and `UpdateDialogMotion` style
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/common/UpdateUiTest.kt`

## Risks

Risk level: low-medium. Layout-only plus window animations; view IDs used by
the controller and tests are unchanged. Window animations must be set before
`show()` to affect entry. Large font scales must keep actions reachable.

## Human approval

Required before implementation: no. The user asked for a Material 3 updater
with smoother motion.

## Implementation steps

1. Tonal icon, HeadlineSmall title, current → new version pills in a flow row
   (wrap whole pills at large font), release notes in an inset surface, M3
   filled and text buttons.
2. Matching download layout with a gapped M3 progress track.
3. `UpdateDialogMotion`: emphasized-decelerate rise in (400 ms), emphasized-
   accelerate fade out (200 ms), applied to every updater dialog before show.
4. Fade-and-rise the download content when the phase changes.

## Verification

- [x] `UpdateUiTest` renders dark/light at 1× and 2× font; actions fit.
- [x] assembleDebug, testDebugUnitTest (817 passed), lintDebug (0 errors, 348
  existing warnings).
- [ ] Device: prompt on phone (bottom) and tablet/unfolded (centred), enter and
  exit motion, download → install hand-over, failure retry, rotate during
  download, Light/Dark/AMOLED/Blue themes, 2× font.

## Progress log

- 2026-10-12: Implemented layouts, motion and test; renders checked.
- 2026-10-12: Device feedback (HONOR, API 36): square Download button, notes
  bleeding past their panel, wrapped "Release page", header scrolling away,
  M2 buttons and underlined hashes/dates on the Changelog screen, and the
  latest release repeated in the changelog list. Reworked the prompt as an M3
  scrollable dialog (fixed icon/title, body between scroll-aware dividers,
  pinned actions; icon drops below 420dp of height; secondary actions stack
  only when a label would wrap). Rebuilt the Changelog screen with filled M3
  cards and M3 buttons, localized dates, a status pill and no duplicate latest
  release. Markdown links are web URLs only; note headings become bold
  body-size headings.

## Decisions

- One rise animation serves both bottom and centred placement, avoiding a
  placement-dependent style chosen before the window is measured.
- Tonal colours derive from `colorPrimary` rather than container roles.
- Shapes come from the theme (no hard-coded pill or 28dp corners), so the
  corner-style setting applies. The square button on device came from
  `applyTheme` reading the three-way corner list as a boolean, which always
  picks NoCorners once the setting is stored; fixed in a separate PR.
