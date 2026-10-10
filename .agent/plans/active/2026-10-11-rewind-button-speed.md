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
2. Use history icon distinct from restart replay icon.
3. Reset recording speed to 1x on Go-live and live-edge catch-up, including UI plus persisted pref.
4. Add unit tests for visibility and speed-reset triggers.
5. Run assembleDebug, test, lintDebug.

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew assembleRelease`, if release-risk

Human QA required:

- [ ] live, VoD, switching, minimize/restore, close/reopen, orientation, PiP/background, speed/quality, gestures, floating chat
- [ ] fullscreen rewind button only on live fullscreen near volume and opens rewind options
- [ ] recording at 4x returns to live at 1x with updated UI and persisted pref
- [ ] automatic catch-up to live edge also resets to 1x

Human QA completed:

- [ ] none yet

## Progress log

- 2026-10-11: Created plan.

## Decisions

- Decision: place button before volume and use history icon.
  Reason: near volume without colliding with restart replay metaphor.
  Alternatives considered: top-right row; farther from volume and crowded.

## Final PR summary draft

Summary: live fullscreen rewind control plus live-edge 1x reset.
Tests: assembleDebug, test, lintDebug.
Human QA: player matrix plus rewind/speed checks.
Risks: control crowding and TV focus order.
