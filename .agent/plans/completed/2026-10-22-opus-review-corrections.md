# Correct the Opus feature-stack review findings

## Goal

Fix the verified findings against cf2127566 in scoped local commits and provide
regression coverage, native evidence and a precise remaining-risk handoff.

## Current context

Work in .private/worktrees/perf on fix/13-opus-review. The original feature
branches and main checkout are preserved. Raw attributed review and parent
verification are ignored artifacts under .private/artifacts/opus-5.5-review-cf2127566.
The user explicitly authorized fixing the findings after the Opus review.

## Files likely involved

- PlayerFragment / PlayerPipPolicy / ChatFragment and PiP tests.
- PlaybackService / PlayerCastBridge / CastPlaybackHandoff / Media3Fragment.
- LivePlaybackRecovery, source fallback and recovery tests.
- DeviceCodeLoginController / LoginActivity and auth tests.
- PLAYER, TESTING, MANUAL_QA and feature review handoff documentation.

## Risks

Risk level: high. Preserve one active player, configured sources, cancellation,
pause intent, drafts, independent feature boundaries and finite recovery. Actual
reconnection must be demonstrated without relaxing retry gates. Preserve original
failed and intermediate evidence alongside the corrected-build observations.

## Human approval

Required before implementation: no further approval.
Reason: the user explicitly requested these fixes. No agents, pushes, PRs,
publication, version changes, signing or real accounts are authorized here.

## Implementation steps

1. PiP: attached-window listener cleanup, cached feature support, legacy 16:9
   video-only shape and scroll-follow restoration, with focused regressions.
2. Cast: service/current-setting agreement, explicit disconnect ownership,
   keep-screen-on policy, receiver volume preservation and loopback literal guard.
3. Recovery: restore proxy fallback, separate exhaustion from handled errors,
   accurate feedback, bounded recovery after explicit retry/network return and
   investigate the native behind-live-window failure without unbounded resets.
4. Login: distinguish denied/invalid/expired responses and preserve held-Helix
   completion; make the phone instructions accurate.
5. Full checks, unsigned release/profile packaging if affected, native player/
   outage checks, update docs and review the final diff.

## Verification

Focused tests at each commit; final assembleDebug, test, lintDebug and
assembleRelease. Regenerate profiles if dependency/startup paths warrant it.
Never weaken checks. Save raw results in ignored artifacts.

Human QA required: live/VoD, switching, minimize/restore, close/reopen,
PiP/background, speed/quality, gestures, floating chat, Cast receiver/session/
volume/network changes, physical outage/handover and TV/authentication.
Human QA completed: none.

## Decisions

- Retain the reviewed SHA and raw report; record corrected/qualified findings.
- Source-factory behavior predates the feature stack; change it only if a
  separate scoped correction confirms its direct impact on quality-switch recovery.
  User authorization includes the issues identified by the review.
- A 30s healthy budget reset protects against retry loops. Any new recovery
  episode must have a bounded trigger and preserve explicit pause/source identity.

## Progress log

- 2026-10-22: Reconfirmed clean reviewed HEAD, loaded review verification and
  project guidance, created the local correction branch. Begin PiP corrections.
- PiP: P1/P2/P3/P4 corrected. Use the existing detach-safe window observer,
  register/cache support only on compatible devices, restore explicit 16:9 for
  video-only PiP and clear stale chat follow flags. Focused Android tests passed;
  listener test removes the attached root before dispatching the window observer.
- Cast: C1/C2/C3/C4 corrected with a stable forwarding player and fresh idle route
  on explicit source commands. Stop/clear/release the prior route, invalidate old
  transfer callbacks, report actual service capability, clean existing SDK sessions
  without initializing default-off Cast, retain receiver controls and local wake
  policy, reject expanded/mapped loopback literals without hostname resolution.
  Final 44 focused cases passed, including SDK-unavailable fallback. No physical
  receiver is available; native single-player and setting changes remain to check.
- Recovery: R2/R3/R4/R5 corrected. Proxy HTTP failures reach the guarded direct
  fallback; exhaustion no longer masks later errors; explicit Prepare rearms the
  budget; a single observed network-return episode remains bounded against flaps.
  Confirmed offline return seeks live. Added sanitized debug-only traces for R1.
  Debug and 32 coordinator cases passed, then 40 coordinator/fragment integration
  cases passed. A fixture compile error was corrected without weakening checks.
  Task emulator restarted on dedicated port 5568 / ADB 5038 for native R1 work.
- Native Robb576 live playback resumed after a 33.9s outage, with one automatic
  attempt and live-edge seek. Trace exposed redundant quality/track Prepare calls
  resetting the budget while already buffering. Tightened the rearm guard to
  failed/exhausted states and added a no-reset regression; rerun before claiming
  final reconnection success. Original Rubius channel was offline at test time.
- Tightened rearm guard and controller Play hook passed debug assembly and all
  42 coordinator/fragment cases, including buffering/READY Prepare flaps. Save
  this candidate and rerun short/long outages before finalizing R1 evidence.
- Login: T1/T2 corrected with distinct denial/invalidity/expiry/validation states,
  held-Helix completion and accurate cross-device instructions. Nineteen focused
  controller/production-approval/native-view cases passed without real accounts.
- Native Robb576 broadcast ended during the rebuilt-candidate checks (profile now
  shows a 33:54 past broadcast). Select another live source; do not conflate this
  external stream ending with a player recovery failure.
- Corrected the review's pre-existing source-factory gap in a separate change:
  a delegating ExoPlayer media-source factory follows the active HLS/HTTP/parser
  factory through manual quality/audio item changes and resets for downloads.
  Real local ExoPlayer tests verify retained and replaced delegates; combined
  Cast/recovery/source tests and debug assembly passed. No new dependency.
- Self-review: source ownership compares URI/media ID so metadata refreshes do
  not reset pending attempts or the healthy timer. Route replacement explicitly
  transfers and clears the video output; stale output listeners are removed.
- A full 995-test pass found five lint NewApi errors after support caching. Kept
  the explicit SDK guard; the subsequent complete pass passed 997 tests,
  debug/lint/unsigned release. No lint baseline or quality gate was changed.
- Final connectivity lifecycle coverage includes API 23 VPN requests, active
  network sampling during handover and late/queued callback rejection at teardown.
  Added numeric-only window/healthy debug traces; final full checks are running.
- Native wide/sidebar live layout entered a 505x284 video-only PiP window and
  restored; raw framework bounds and screenshots saved. No timing/physical claim.
- Final source at fd627d867 passed 999 tests / 125 suites, debug/lint/unsigned
  release (lint 0 errors / 331 existing warnings). Final live-video outages of
  33.7s and 95.8s recovered and sustained healthy playback; the long run exhausted
  its first wait and used the single permitted network-return episode.
- The long run also left chat disconnected. The activity checks network state
  at onAvailable, which may precede validation, but did not request another check
  onCapabilitiesChanged. Add that notification to the existing fragment flow,
  test the actual callback and recheck native chat/video recovery before handoff.
- Activity notification cases passed API 23/28, then a full 1001-test pass and
  debug/lint/unsigned release completed. A repeated 95.9s outage restored video
  but still left chat disconnected. Preserve this intermediate negative result.
- Chat's existing restoration skips an active retry/connect job. On confirmed
  loss, cancel current jobs and close their captured connections; validated return
  can reconnect through the existing autoReconnect guard. Capture all cleanup
  references before dispatch so a delayed old close cannot target a new session.
  Test live fragment loss/restoration, manual disconnect and replay exclusion.
- Final source a0acbb2de passed all required gates: 1,005 tests / 127 suites, no
  failures/errors/skips; debug and unsigned release assembly; lint 0 errors /
  331 existing warnings. Final raw results/APKs/profile metadata are archived in
  .private/artifacts/review-corrections/final-acceptance. No more code changes.
- Final debug APK aadee7cb4eeb08ba750775468a871212eaa080392f589596d29ac70ef2cdac1f
  is installed on the task emulator. An initial startup-buffering interruption
  recovered video and chat, excluded from active-playback acceptance. After real
  continuous health, a 95.6s outage exhausted the first offline wait, then one
  bounded return attempt restored video and new chat messages; healthy >30s.
- Final 33.5s active-playback outage also restored video and new chat messages
  with one attempt, then >30s continuous health and a PLAYING media session.
  Verified installed APK hash exactly matches the final automated debug artifact.
  Final acceptance JSON distinguishes original/intermediate failures and the
  startup interruption. Restored task-device networking/orientation and stopped
  only emulator-5568; no AVD data deletion or global ADB changes.

## Outcomes

All verified Opus findings are corrected in eight scoped local code commits,
4c935317f through a0acbb2de. The pre-existing configured-factory gap and native
chat-restoration gaps have separate corrections and regression coverage. Required
Gradle checks and exact-final-APK native video/chat outage observations passed.
The original report remains unchanged; no second independent review was run.

See docs/OPUS_REVIEW_FIXES.md for finding-to-fix mapping, commit boundaries,
1,005-test/lint/build results, exact unsigned APK/profile metadata and native
observations. Ignored final-verification.json and final-acceptance/ retain the
raw evidence. No dependency, version, signing, push, PR, publication or real-account
change was made. Main remains master at f146bbeaf with no tracked changes.

Required human QA remains listed above and in docs/MANUAL_QA.md. Hardware Cast,
TV/auth/camera, physical outage/handover, audible overlap, full live/VoD/controls/
gestures/floating-chat and authenticated/background chat transports are not
certified by these emulator/unit results. Human QA completed: none.
