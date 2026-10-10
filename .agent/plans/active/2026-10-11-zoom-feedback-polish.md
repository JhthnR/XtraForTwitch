# Zoom feedback polish: stable pill, smoother updates, Fill edge flash

## Goal

The landscape pinch-zoom feedback pill stays a fixed size while showing Fit,
Zoom N% or Fill, updates without visible choppiness, and gives a subtle
YouTube-style white edge-rectangle flash when the pinch sticks to Fill.

## Non-goals

- No change to pinch zoom math, persistence, More menu, or renderer behavior.
- No change to portrait, mini-player, PiP, TV, Stretch, or pre-N toggle behavior.
- No change to brightness/volume/seek/speed feedback geometry.
- No new settings or strings beyond the existing Fit/Fill/Zoom labels.

## Current context

- Base branch: `origin/feat/pinch-free-zoom` (plan
  `.agent/plans/active/2026-10-10-pinch-free-zoom.md`).
- Free zoom pill path: `PlayerFragment.applyPinchZoom()` calls
  `showPinchZoomFeedback(target, zoom, fraction)`, which builds labels from
  `R.string.display_mode_fit`, `R.string.display_mode_fill`, and
  `R.string.display_mode_zoom` (`Zoom %d%%`) and a position bar through
  `PlayerGestureFeedbackState.pinchZoomPresentation()`.
- `PlayerSurfacePolicy.presentFeedback()` rewrites placement, icon size,
  progress, and text on every pinch update, and sets
  `LinearProgressIndicator.progress` directly (no animation).
- Pill text is `wrap_content` (`layout_player_gesture_feedback.xml`), so
  `Fit` vs `Zoom 134%` vs `Fill` relayout the pill every update.
- No Fill-stick edge flash exists yet.

Relevant files, docs, current behavior, and known constraints.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackState.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PinchZoomController.kt` (read-only reference for detent/trigger rules)
- `app/src/main/res/layout/layout_player_gesture_feedback.xml`
- `app/src/main/res/layout/fragment_player.xml` (only if the edge flash needs a view)
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackStateTest.kt`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackRenderTest.kt`
- `docs/GESTURE_SYSTEM.md`, `docs/MANUAL_QA.md` (only if behavior/QA text changes)

## Risks

- Fixed-width pill text could clip in large-font or translated builds.
- Throttling feedback updates could make the bar feel laggy if too aggressive.
- Edge flash overlay could leak visibility or intercept touches.
- Reduced-motion handling could be missed on some API levels.

Risk level: low (player overlay presentation only, no lifecycle or renderer change)

## Human approval

Required before implementation: no

Reason: narrow, reversible player-overlay polish on top of an already-approved
pinch-zoom approach; no lifecycle, persistence, or release behavior changes.

## Implementation steps

1. Stabilize pill dimensions: give the pinch feedback text a fixed minimum
   width sized for the widest expected label (`Zoom 200%`-class), monospace or
   tabular digits if cheap, centered text, so Fit/Fill/Zoom N% swaps do not
   relayout the pill. Keep other gesture kinds unchanged.
2. Smooth updates: throttle pinch feedback presentation updates (time and/or
   minimum label/level delta), skip redundant text/progress writes, and use the
   animated `LinearProgressIndicator` progress path so the bar glides instead of
   jumping. Keep the final committed label/bar exact.
3. Fill-stick edge flash: add a subtle white edge-rectangle flash on the player
   surface when free zoom enters Fill (`PinchZoomController.Frame.reachedEnd`
   with target FILL), YouTube-style, quick smooth fade; respect reduced motion
   by shortening/skipping the flash when animations are disabled.
4. Unit tests: label width/format stability across Fit/Zoom/Fill labels plus the
   Fill-stick trigger (entering Fill fires once; staying there or leaving does
   not refire).
5. Run `.\gradlew.bat assembleDebug`, `test`, `lintDebug`; update docs/QA text
   only if behavior expectations changed.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug` (via test task compile)
- [x] `./gradlew test` (target classes pass; full suite to run pre-PR)
- [x] `./gradlew lintDebug` (0 errors, matches baseline)
- [ ] `./gradlew assembleRelease`, if release-risk

Human QA required:

- [ ] Near-square foldable inner screen in landscape: pinch slowly through Fit,
      intermediate zooms, and Fill; pill stays fixed-size and smooth, Fill stick
      shows a brief white edge flash.
- [ ] Phone landscape: pinch to Fill and back to Fit still easy; haptic ticks.
- [ ] Large fonts / RTL: pinch labels do not clip or overflow the pill.
- [ ] Reduced motion enabled: Fill flash is skipped or instant, no lingering overlay.
- [ ] Rotate, fold/unfold, open/close side chat, multi-window resize while zoomed.
- [ ] Minimize/restore, PiP, close/reopen while zoomed; portrait stays Fit.

Human QA completed:

- [ ] none yet

## Progress log

- 2026-10-11: Created plan.
- 2026-10-11: Implemented fixed ems label slot, throttled feedback writes,
  animated bar, Fill edge flash with reduced-motion path, plus unit/render
  tests. Fixed a `setMinEms`-overwrites-`setMinWidth` slot bug found by the
  render test (ems is now the sole mechanism).

## Decisions

- Decision: fixed pill slot via `setMinEms(9)` with monospace, not pixel
  minWidth. `setMinEms` and `setMinWidth` share one field, so only one can
  win; ems scales with text size for large fonts.
  Reason: render test showed `getMinWidth()` returning -1 after both calls.
  Alternatives considered: pixel minWidth only (breaks large fonts).

## Final PR summary draft

Summary:
Tests:
Human QA:
Risks:
