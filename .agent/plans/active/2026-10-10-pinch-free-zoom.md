# Pinch free zoom between Fit and Fill

## Goal

In the landscape maximized player, pinch zooms continuously and stays centered
anywhere between Fit and Fill, instead of only toggling Fit/Fill. Releasing
between the two keeps that zoom, and it is remembered for later playback. This
is for screens whose shape differs a lot from the video's, such as a near-square
foldable inner screen in landscape, where Fill crops too much.

## Non-goals

- No zoom beyond Fill and no panning (zoom stays centered).
- No change to portrait, the mini-player, PiP, TV, or Stretch.
- No change to pre-API 24 devices, which keep the stepped Fit/Fill toggle.
- No change to gesture arbitration, double-tap chat, or other swipe gestures.

## Current context

- `PinchDisplayModeController` is a discrete Fit/Fill state machine
  (arm at 1.15 / 0.87 cumulative span, elastic dead directions).
- `PlayerFragment` previews by scaling `aspectRatioFrameLayout` in the committed
  renderer (`PlayerDisplayModePreviewer.fillToFitRatio`), commits by switching
  `resizeMode` and normalizing scale on the next layout, and owns canonical
  geometry in `finalizePinchSurface()`.
- `PlayerDisplayModeStore` persists fit/fill/stretch in
  `C.PLAYER_DISPLAY_MODE_LANDSCAPE`; the More menu (`PlayerMorePopupBinder`)
  shows and selects the mode.
- YouTube's Android player scales the video about its center between the original
  framing and zoom-to-fill, with no panning in the default experience.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PinchZoomController.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerDisplayModeStore.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackState.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerMorePopupBinder.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/C.kt`
- `app/src/main/res/values/strings.xml`
- tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- `docs/GESTURE_SYSTEM.md`, `docs/MANUAL_QA.md`

## Risks

- A persistent view scale on the video surface must be recomputed when the
  player area or video aspect changes (rotation, fold/unfold, chat side panel,
  multi-window), or the picture is cropped wrongly.
- Renderer switches between Fit and Fill must not flash the wrong geometry.
- Phones: a pinch that used to snap to Fill could now stop between modes.

Risk level: medium (player gesture and surface geometry, no lifecycle change)

## Human approval

Required before implementation: no

Reason: the owner asked for this feature and approved the approach on 2026-10-10.
It is narrow, reversible, and keeps the old controller for unsupported cases.

## Implementation steps

1. Add a pure `PinchZoomController`: zoom relative to Fit (1 = Fit, max = Fill),
   content follows the fingers (start zoom × span scale), magnetic detents near
   both ends, elastic resistance past them, release/cancel results.
2. Use it on API 24+ when the committed mode is Fit or Fill and the Fill/Fit ratio
   leaves a usable band between the detents; otherwise keep
   `PinchDisplayModeController` unchanged.
3. Persist the zoom in a new float preference next to mode `fit`, so older builds
   read plain Fit. Selecting a mode in More clears it.
4. Make `finalizePinchSurface()` apply the canonical zoom scale, switch renderers
   behind an equivalent scale, and re-apply on player-area or aspect changes.
5. Feedback pill shows Fit, Fill or "Zoom N%" with the bar at the position
   between Fit and Fill; haptic tick on reaching either end. More shows the zoom.
6. Unit tests for the controller, store and feedback; update docs and QA.

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew assembleRelease`

Human QA required:

- [ ] Near-square foldable inner screen in landscape: pinch to an intermediate
      zoom, release, reopen another stream: zoom kept and centered
- [ ] Phone landscape: pinch to Fill and back to Fit still easy; haptic ticks
- [ ] Rotate, fold/unfold, open/close side chat, multi-window resize while zoomed
- [ ] Minimize/restore, PiP, close/reopen while zoomed; portrait stays Fit
- [ ] More → Display mode shows the zoom; choosing Fit/Fill/Stretch clears it
- [ ] Live, VoD and clips; all three backends; Stretch and pre-N keep old behavior
- [ ] Double-tap chat still works when a pinch begins as a second tap

Human QA completed:

- [ ] none yet

## Progress log

- 2026-10-10: Created plan.

## Decisions

- Decision: store zoom relative to Fit, clamped to the current Fill ratio.
  Reason: the same value crops the same share of the video on every screen shape,
  so a foldable shows the partial zoom on its inner screen and plain Fill on the
  narrower outer screen.
  Alternatives considered: fraction between Fit and Fill (crop varies by screen),
  per-screen-shape storage (more state than needed).
- Decision: keep the discrete controller when the Fill/Fit ratio is below about
  1.12, for Stretch, and before API 24.
  Reason: there is no meaningful band between the detents there, and scaling the
  surface is unreliable before API 24.
  Alternatives considered: one controller for everything (degenerate ranges and a
  stepped renderer make it awkward).

## Final PR summary draft

Summary:
Tests:
Human QA:
Risks:
