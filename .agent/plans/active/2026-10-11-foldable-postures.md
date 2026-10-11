# Half-folded layout for foldables

## Goal

On a half-open foldable, the maximized player stops drawing video across the
hinge. In tabletop posture (hinge across the screen, like a laptop) video sits
above the hinge and chat below it. In book posture (hinge down the screen) video
and chat sit side by side while chat is showing. A player setting turns it off.

## Non-goals

- No change to phones without a hinge, Android TV, the mini-player or PiP.
- No new player controls on the lower pane (YouTube puts controls there; that
  would mean moving the controller out of the video view).
- No change to playback, player sessions or the chat fragment itself.
- No dual-screen or span-across-displays support.

## Current context

- `PlayerLayout`/`ChatLayout` with `isPortrait` force a 16:9 video with chat below
  in portrait; landscape uses a full-height video with `marginEnd =
  chatWidthLandscape` and chat at gravity END, or floating chat.
- Half-opening does not change the configuration, so only Jetpack Window
  Manager's `WindowInfoTracker.windowLayoutInfo` reports the posture
  (`FoldingFeature`, `HALF_OPENED`, orientation HORIZONTAL = tabletop).
- Researched apps: YouTube and Google Meet use tabletop (video above, controls
  or participants below); Samsung Flex mode does the same; the guidance is to
  keep content off the hinge and use the two halves as panes.
- The maintainer tests on a Honor Magic V5 (book-style foldable, Android 16).

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFoldLayout.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/C.kt`
- `app/src/main/res/xml/player_preferences.xml`, `app/src/main/res/values/strings.xml`
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (`androidx.window:window`)
- tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- `docs/PLAYER.md`, `docs/MANUAL_QA.md`, `CHANGELOG.md`

## Risks

- Player geometry is shared with chat modes, the keyboard, pinch zoom, minimize,
  PiP and rotation; a missed restore path could leave a half-height video.
- Docking floating chat in tabletop changes the chat mode the viewer chose.
- Hinge events arrive asynchronously and before layout; stale bounds could pick
  the wrong pane sizes.
- New dependency (`androidx.window` 1.5.0).

Risk level: medium (player layout, no lifecycle or playback change)

## Human approval

Required before implementation: no

Reason: the owner asked for the best half-folded features and left the design to
the agent on 2026-10-11. The change is behind a setting, reversible, and does not
touch player sessions. It ships after 1.4.0 and is not merged without the owner.

## Implementation steps

1. Pure `PlayerFoldLayoutPolicy.resolve(hinge, container, minPane)` returning
   Tabletop or Book pane sizes, or null for split screen and tiny panes.
2. `PlayerFragment.watchFoldPosture()` collects the first `HALF_OPENED` feature in
   `repeatOnLifecycle(STARTED)` and re-runs `updateFoldLayout()` on change.
3. `updateFoldLayout()` applies the split only while maximized, outside PiP and
   with the setting on; Book only while chat is visible; Tabletop docks floating
   chat. A null result restores the normal geometry once.
4. Re-run it after every chat transition, orientation init, minimize/maximize,
   PiP change, keyboard show/hide and sliding layout size change. The keyboard's
   landscape chat widening is skipped while split.
5. Setting "Half-folded layout" (default on), docs, QA checklist, changelog.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` (809 tests)
- [x] `./gradlew lintDebug` (0 errors, no new warnings in the changed code)
- [ ] `./gradlew assembleRelease`, not release-risk

Human QA required:

- [ ] Tabletop in landscape and portrait with side, floating and hidden chat
- [ ] Book posture with chat shown, hidden and floating; RTL puts chat on the left
- [ ] Keyboard in tabletop keeps the chat input above it
- [ ] While half-folded: minimize/restore, PiP, rotate, close/reopen, stream switch,
      pinch zoom, double-tap chat, popups
- [ ] Setting off keeps the normal layout; phones and TV unchanged

Human QA completed:

- [ ] none yet (emulator results in the progress log)

## Progress log

- 2026-10-11: Created plan and implemented the policy, fragment wiring, setting
  and docs. `PlayerFoldLayoutPolicyTest` (7) and `PlayerFoldLayoutTest` (9,
  Robolectric with a simulated hinge) pass; `PlayerPinchChatTest` still passes.
- 2026-10-11: Docked floating chat now floats again once the hinge goes away.
  Replaced the API 24 `UNSPECIFIED_GRAVITY` constant with the equivalent
  `TOP or START` and moved to `androidx.window` 1.5.1 (lint).
- 2026-10-11: Emulator `Pixel_9_Pro_Fold_API_36.1` (API 36, inner screen
  2076x2152, logged out), live stream on the default backend, hinge driven with
  `adb emu sensor set hinge-angle0 90|180`. Book posture in portrait split video
  and chat at the hinge; unfolding restored the 16:9 stack. Landscape tabletop
  put video and controls above the hinge and chat below; the chat button went
  docked → hidden (empty lower pane) → docked. Floating chat docked on folding
  and floated again in its old place on unfolding. Minimize/restore, PiP and
  back, rotation to portrait (book) and back, and opening another stream while
  folded all kept or restored the right split. With the setting off, folding
  kept the normal layout. Found and fixed: after a rotation round trip the
  docked chat came back as a sidebar, because a rotation reports the new hinge
  before the configuration change (now restored only when the hinge is gone;
  covered by a test). Not covered: keyboard (logged out), RTL, VoDs, other
  backends, pinch zoom while folded, a physical hinge.

## Decisions

- Decision: tabletop puts chat in the lower pane rather than player controls.
  Reason: chat is ThystTV's second surface and already has a pane layout; moving
  the controller out of the video view would be a much riskier player change.
  Alternatives considered: YouTube-style controls below the hinge; an empty lower
  pane with the video only.
- Decision: tabletop docks floating chat and the chat cycle skips floating there.
  Reason: a floating window would sit on the small video pane. Unfolding floats
  chat again, because the viewer chose floating; a chat button cycle or double
  tap while docked is a new choice and keeps the sidebar.
  Alternatives considered: keep floating chat over the video pane; leave chat
  docked after unfolding.
- Decision: book posture splits only while chat is visible.
  Reason: with no chat the second pane would be empty and the video would shrink
  to half the screen for nothing.
  Alternatives considered: always split.
- Decision: no split below 160dp per pane or when the hinge does not cross the
  whole window.
  Reason: split screen and odd window sizes keep the tested layout.

## Final PR summary draft

Summary: half-folded layout for foldables (tabletop: video above, chat below;
book: side by side), behind a default-on player setting.
Tests: policy and Robolectric fragment tests; full `test lintDebug assembleDebug`.
Human QA: the "Half-folded layout" section of `docs/MANUAL_QA.md`.
Risks: player geometry restore paths; docking floating chat in tabletop.
