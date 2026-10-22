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
  reproducible recovery issue demonstrates that it is necessary, in a separate
  scoped correction.
- A 30s healthy budget reset protects against retry loops. Any new recovery
  episode must have a bounded trigger and preserve explicit pause/source identity.

## Progress log

- 2026-10-22: Reconfirmed clean reviewed HEAD, loaded review verification and
  project guidance, created the local correction branch. Begin PiP corrections.
- PiP: P1/P2/P3/P4 corrected. Use the existing detach-safe window observer,
  register/cache support only on compatible devices, restore explicit 16:9 for
  video-only PiP and clear stale chat follow flags. Focused Android tests passed;
  listener test removes the attached root before dispatching the window observer.
