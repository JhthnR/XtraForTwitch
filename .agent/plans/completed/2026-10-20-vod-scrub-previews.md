# VoD Scrub Previews

## Goal

Dragging the VoD seek bar shows a small thumbnail of the frame at the drag position,
with its time underneath, so viewers can find a moment without seeking blindly.

## Non-goals

- No previews for live streams, clips or downloaded VoDs.
- No previews for swipe-to-seek gestures (follow-up).
- No new setting. The seek bar is hidden in spoiler-free mode, so previews never show there.
- No change to how seeking, the controller auto-hide or the seek bar itself behave.

## Current context

- Twitch publishes a storyboard for finished VoDs. The GQL `seekPreviewsURL` points to
  `<base>/storyboards/<id>-info.json`; `animatedPreviewURL` is `<base>/storyboards/<id>-strip-0.jpg`
  in the same folder. The player already receives `animatedPreviewURL` as
  `KEY_VIDEO_ANIMATED_PREVIEW` from GQL lists, bookmarks, clips and deep links.
- `info.json` lists a `low` (160x90 frames) and a `high` (220x124 frames) sprite set.
  For a 3 hour VoD, `high` is four 1100x1240 JPEG sheets of 5x10 frames, about 184 KB
  each, one frame every 54 seconds.
- An in-progress VoD (for example a live-rewind recording) returns 403 for `info.json`.
- `PlayerFragment` owns the `DefaultTimeBar` scrub listener for all three playback
  backends, and every backend overrides `getDuration()`.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/model/VideoStoryboard.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/SeekPreview.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/view/StoryboardFrameView.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/repository/PlayerRepository.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerViewModel.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/res/layout/player_layout.xml`
- `app/src/main/res/drawable/seek_preview_background.xml` (new)
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/VideoStoryboardTest.kt` (new)

## Risks

- Player UI: the preview sits above the seek bar inside the controls. It must not
  steal touches, keep the controls from hiding, or survive a minimize or close.
- Memory: each decoded `high` sheet is about 2.7 MB as RGB_565. Sheets load only while
  scrubbing and live in Coil's memory cache, which evicts them like any other image.
- Data: about 1 KB of JSON per VoD start, plus one sheet (about 184 KB) per 45 minutes
  of video the viewer scrubs over.
- Unknown storyboard formats or missing files must fail silently with no preview.

Risk level: medium

## Human approval

Required before implementation: no

Reason: Additive player UI with no lifecycle, playback or session changes. It is part of
the agreed performance and polish steps, kept as a local stacked commit.

## Implementation steps

1. `VideoStoryboard`: derive the `info.json` URL from the animated preview URL, parse
   it, pick the `high` set (or the widest), and map a position to a sheet and frame.
2. `PlayerRepository.loadVideoStoryboard` fetches `info.json` with the selected network
   library. `PlayerViewModel` loads it once per VoD unless spoiler-free mode hides the seek bar.
3. `StoryboardFrameView` draws one frame of a sheet. `SeekPreview` loads sheets with
   Coil, positions the preview over the drag position, and hides it when scrubbing ends.
4. Hook it into the `DefaultTimeBar` scrub listener and release it in `onDestroyView`.
5. Unit tests, CHANGELOG, `docs/PLAYER.md`, `docs/MANUAL_QA.md`, then `assembleDebug`,
   `test` and `lintDebug`.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` — 857 tests in 108 classes, no failures/errors/skips.
- [x] `./gradlew lintDebug` — 0 errors / 336 warnings.

Human QA required:

- [ ] VoD playback: drag the seek bar slowly and quickly; the preview follows the thumb,
      stays on screen at both ends and matches the video after releasing.
- [ ] Long VoD (3+ hours): drag across sheet boundaries; frames switch without showing
      a frame from the wrong sheet.
- [ ] Portrait, landscape, tablet/large screen and RTL layout: preview is readable and not clipped.
- [ ] Controller auto-hide during and after a drag; cancelling a drag hides the preview.
- [ ] Spoiler-free VoDs on: no seek bar and no preview.
- [ ] Live stream, clip, downloaded VoD and live-rewind recording: no preview, no errors.
- [ ] Minimize, restore, close and reopen during and after a drag; switching VoDs shows
      the new VoD's frames.
- [ ] Speed and quality controls, gestures and floating chat still behave as before.
- [ ] Android TV: D-pad scrubbing shows the preview.

Human QA completed:

- [ ] Not yet performed.

## Progress log

- 2026-10-20: Created plan as step 5 of the performance stack (`perf/5-vod-scrub-previews`).
- 2026-10-20: Finished player hooks, optional loading, grid validation and cancellation.
  Native tests cover sheet/cell boundaries, actual cropped pixels, same-sheet reuse,
  stale callbacks, hide/release and compact/wide/RTL placement. Reviewed native renders.
  Full Gradle checks passed; a focused render/lint rerun passed after fixing capture
  layout timing. Human playback/gesture/TV QA remains pending.

## Decisions

- Decision: Derive the storyboard URL from `animatedPreviewURL` instead of adding a GQL query.
  Reason: The player already has that URL for GQL-sourced VoDs (the default for every
  list), bookmarks and deep links, so previews cost no extra query.
  Alternatives considered: add `seekPreviewsURL` to the GQL schema and query it at VoD
  start. Revisit if Helix-sourced VoDs need previews.
- Decision: Draw a sub-rectangle of the decoded sheet in a small custom view.
  Reason: No per-frame bitmap copies while dragging, and Coil caches whole sheets.
- Decision: Use the `high` set.
  Reason: 220x124 frames upscale acceptably to a 160dp-wide preview. The `low` set is
  noticeably blurry at that size and is one larger sheet anyway.

## Final PR summary draft

Summary: Show Twitch storyboard thumbnails above the VoD seek bar while dragging.
Tests: `VideoStoryboardTest`, full `test`, `lintDebug`.
Human QA: See the list above.
Risks: Player controls overlay; preview placement on unusual screen sizes.
