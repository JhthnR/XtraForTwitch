# Spoiler-free VODs (issue #34)

## Goal

Opt-in setting that hides the video length and progress indicators on VODs, so
an esports VOD's duration does not reveal how many games were played. Default is
off. Requested by ykw11n (issue #34).

## Non-goals

- No change to clips lists or clip playback.
- No change to the media notification or lock-screen controls: on the default
  backend their length comes from the shared Media3 session, which the in-app
  player also reads, so hiding it there would break swipe-to-seek.
- No change to live streams (they already hide seek/length).
- No auto-detection of esports; this is a plain viewer preference.
- No translation work (English fallback, matching new-feature-copy convention).

## Current context

Base is `master`. New branch `feat/spoiler-free-vods`.

- Player: for `videoType == STREAM`, `position`, `progressBar` (DefaultTimeBar) and
  `duration` are set `View.GONE` in `PlayerFragment.onViewCreated` (~1189-1193).
  For `VIDEO` they remain visible (default from `player_layout.xml`).
- VOD thumbnails: `VideosAdapter` (Past Broadcasts) shows a `duration` overlay
  (~121-126) and a watched `progressBar` (~138-143).
- Saved list: `BookmarksAdapter` shows `duration` (~172-177) and watched
  `progressBar` (~221-226).
- Preferences live in `res/xml/player_preferences.xml`; keys in `util/C.kt`;
  copy in `res/values/strings.xml`.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/util/C.kt` (new key)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/SpoilerFreeVideos.kt` (new, pure)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt` (gate VOD seek/duration)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/common/VideosAdapter.kt` (gate duration + progress)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/BookmarksAdapter.kt` (gate duration + progress)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/downloads/DownloadsAdapter.kt` (gate duration, source range + progress)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureListener.kt` (seek pill without the length)
- `app/src/main/res/xml/player_preferences.xml` (switch)
- `app/src/main/res/values/strings.xml` (title + summary)
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/SpoilerFreeVideosTest.kt` (new)

## Risks

- Gating the player seek bar removes scrubbing on VODs when enabled; that is the
  intended trade-off, and seeking by gesture still works, only the bar is hidden.
- Bookmarks can hold clips/highlights too; hiding their length under an opt-in
  spoiler-free mode is acceptable but noted.
- Default off, so no existing behavior changes unless the user opts in.

Risk level: low (additive, opt-in visibility gating)

## Human approval

Required before implementation: no

Reason: narrow, reversible, opt-in visibility change already discussed with the
owner; matches the existing feature-request triage.

## Implementation steps

1. Add `C.PLAYER_SPOILER_FREE_VODS` key and player_preferences switch (default false).
2. Add `SpoilerFreeVideos` pure object: `hidePlayerProgress(enabled, videoType)`
   (true only for VIDEO) and `hideListSpoilers(enabled)`.
3. Gate VOD player `position`/`progressBar`/`duration` visibility.
4. Gate `VideosAdapter` and `BookmarksAdapter` duration overlay + watched progress.
5. Unit tests for the helper.
6. Run assembleDebug, test, lintDebug.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Enable setting; play a VOD: seek bar, position and duration hidden; gesture
      seek still works; reopening remembers the choice.
- [ ] Live streams unaffected; clips unaffected.
- [ ] Past Broadcasts and Saved thumbnails hide duration + watched bar when on,
      show normally when off.

Human QA completed:

- [ ] none yet

## Progress log

- 2026-10-11: Created plan.
- 2026-10-11: Review fixes. Swipe-to-seek showed `position / length`; it now shows
  only the position when the setting applies. Downloaded VoDs (`OFFLINE_VIDEO`)
  and Downloads thumbnails are included, since an esports VoD downloaded for
  later has the same problem. The Downloads duration block used `?: { }`, which
  never ran its fallback; it is now an `if`/`else`, so hidden rows really hide.

## Decisions

- Decision: scope to online and downloaded VoD playback plus Past Broadcasts,
  Saved and Downloads thumbnails; leave clips alone.
  Reason: matches the reported use case; clips are short and not tournament VoDs.
  Alternatives considered: VoD player only (leaks through the seek pill and
  downloads); also hiding the notification length (needs a playback-service
  change, see Non-goals).

## Final PR summary draft

Summary: opt-in spoiler-free VOD setting hiding length/progress on VOD playback
and VOD/Saved thumbnails (issue #34).
Tests: assembleDebug, test, lintDebug.
Human QA: VOD player + thumbnails, live/clips unaffected.
Risks: disables seek-bar scrubbing on VODs when on (intended).
