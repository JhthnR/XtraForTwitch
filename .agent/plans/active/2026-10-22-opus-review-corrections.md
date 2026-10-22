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
reconnection failure is not yet explained; do not mask it by relaxing retry gates.

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
  separate scoped correction if its direct impact on quality-switch recovery is
  confirmed. User authorization includes the issues identified by the review.
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
