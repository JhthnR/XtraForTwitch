# Rewind Fullscreen Button Plus Catch-Up Speed Reset

## Goal

Give fullscreen live viewers a discoverable rewind entry point beside volume, and never strand recording playback at a stuck fast speed after returning to live.

## Non-goals

- No DVR buffer or second playback service.
- No restart-button behavior/icon change.
- No VoD speed default change.
- No broad upstream sync or release work.

## Current context

Base is `origin/xtra-ports/4-rewind` on branch `fix/rewind-button-speed`.
`showLiveRewindOptions()` is already live-only. Live controls hide normal
rewind/progress and expose bottom-left `restart` plus optional `seekLive`.
Recording playback exposes `seekLive` as Go live. Speed is persisted in
`C.PLAYER_SPEED` and shown by `updatePlaybackSpeedUi()`.

## Files likely involved

- `app/src/main/res/layout/player_layout.xml`
- `app/src/main/res/values/strings_live_rewind.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/LiveRewind.kt`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/LiveRewindTest.kt`
- New unit test for fullscreen visibility and speed reset

## Risks

- New control could crowd bottom row or resemble restart.
- Mitigation: history icon, live-fullscreen-only visibility, and placement before volume.
- Speed reset must not alter ordinary VoD behavior; scope it to live-rewind recordings.

Risk level: medium

## Human approval

Required before implementation: no

Reason: narrow reversible player UI plus tightly scoped recording speed handling.

## Implementation steps

1. Add live-only fullscreen rewind button before volume, opening existing rewind options.
2. Use the ⏪ rewind icon, distinct from the restart arrow.
3. Reset recording speed to 1x at live-edge catch-up, with a visible pill; never
   save speed changes made during a rewind.
4. Add unit tests for visibility, speed-reset triggers and unsaved rewind speed.
5. Run assembleDebug, test, lintDebug.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`
- [ ] `./gradlew assembleRelease`, if release-risk

Human QA required:

- [ ] live, VoD, switching, minimize/restore, close/reopen, orientation, PiP/background, speed/quality, gestures, floating chat
- [ ] fullscreen rewind button only on live fullscreen near volume and opens rewind options
- [ ] recording at 4x catches up, drops to 1x with the "Caught up" pill before
      stalling, with controls shown and hidden
- [ ] Go live and later VoDs keep the VoD speed saved before the rewind
- [ ] after minimize and restore in landscape, the ⏪ button is still there

Human QA completed:

- [ ] none yet

## Progress log

- 2026-10-11: Created plan.
- 2026-10-11: Review fixes after owner feedback and code review. The reset no
  longer writes `C.PLAYER_SPEED` (streams never read it, and it erased the saved
  VoD speed); instead, speed changes during a rewind are not saved. The edge check
  runs every 500 ms from a lifecycle-bound loop, because `updateProgress()` only
  reschedules while controls are visible, and its window scales with speed. The
  reset shows a "Caught up · back to 1x" pill. The button uses ⏪
  (`exo_icon_rewind`), since the history icon read as the restart arrow, and is
  refreshed on `maximize()` as well as layout.

## Decisions

- Decision: place button before volume and use the ⏪ rewind icon.
  Reason: near volume as requested; ⏪ pairs with the ⏩ Go live button and does
  not share the restart button's circular arrow.
  Alternatives considered: history icon (too close to restart); top-right row
  (farther from volume and crowded).
- Decision: don't save speed changes during a rewind instead of resetting the
  saved speed. Reason: the recording starts at the saved VoD speed, so writing 1x
  erased the viewer's VoD preference.

## Final PR summary draft

Summary: live fullscreen rewind control plus live-edge 1x reset.
Tests: assembleDebug, test, lintDebug.
Human QA: player matrix plus rewind/speed checks.
Risks: control crowding and TV focus order.
