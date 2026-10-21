# Cast, PiP chat, recovery and TV feature stack

## Goal

Implement the four larger feature areas one by one, preserving ThystTV viewing UX
and leaving scoped local commits and reproducible evidence for the user's later
Claude Opus 5.5 review.

## Non-goals

No pushes, PRs, publishing, release signing or version changes. Do not launch the
future reviewer or other agents. Do not import donor service rewrites, ad-avoidance
logic, multiview or Compose migrations. Keep one active playback owner.

## Current context

Base is `perf/8-media3` at `5e0de159f`, the completed core performance stack.
Continue in its existing isolated worktree; the main checkout has unrelated files.
Create one local stacked branch per feature. Cast is `feat/9-cast` at `908b2e349`;
current branch: `feat/10-pip-chat`.

The user explicitly authorized all four feature areas on 2026-10-21 and will have
Claude Opus 5.5 review the result afterward. Earlier core-only scope is superseded
for these features; the local-only git preference remains.

## Files likely involved

- `ui/player/PlaybackService.kt`, `Media3Fragment.kt`, `PlayerFragment.kt`
- Existing player settings, popup host, PiP and TV focus helpers
- `ui/main/MainActivity.kt` and TV authentication/navigation entry points as needed
- Scoped policy/coordinator helpers with tests
- Gradle dependencies/manifest for Cast; generated profiles if dependencies change
- `docs/PLAYER.md`, `docs/TESTING.md`, `docs/MANUAL_QA.md`, upstream ledger/changelog

## Risks

Risk level: high. Remote/local handoff, PiP geometry, network retry ownership and
remote focus can regress playback. Preserve user pause/quality/position choices,
guard stale async results, and cancel player-owned callbacks on teardown.

## Human approval

Required before implementation: no additional approval.

Reason: the user explicitly requested these feature areas sequentially, to the
best of our ability, with review afterward. Work stays local and reversible.

## Implementation steps

1. Cast: inspect pinned donor implementations and official Media3 APIs. Integrate
   sender discovery and local/remote handoff into the default session backend,
   with honest availability, usable controls, safe unsupported-media handling,
   preserved VoD position and explicit close semantics.
2. PiP chat: optional readable chat beneath video within system PiP, preserve its
   aspect ratio/bounds, avoid input overlays and restore original geometry.
3. Stream recovery: bounded, cancellable recovery for transient live failures,
   preserve the last frame and explicit pause, avoid retries after close/switch.
4. TV improvements: select a useful bounded gap after auditing current remote UX
   and donor features; keep phone behavior and single-session playback intact.
5. Record each branch/commit, test results, native evidence and unverified hardware
   scenarios in a review handoff. Do not run or substitute a review model.

## Verification

Each feature: assembleDebug, test, lintDebug, diff review, relevant native UI/
emulator checks. Cast/dependency/build risk: assembleRelease and regenerate
startup profiles. Test meaningful state transitions and failure/cancellation paths.
Do not claim Cast receiver or physical-device testing without actual hardware.

Human QA required: live/VoD, switching, minimize/restore, close/reopen, PiP/
background, speed/quality, gestures/floating chat; receiver discovery/handoff/
disconnect/network loss; TV remote and applicable authentication; physical startup.
Human QA completed in this feature stack: none.

## Progress log

- 2026-10-21: Read project guidance and recovered the original fork audit. Created
  `feat/9-cast`. Inspected Spider19996/Xtra_Cast at
  `f3d0edc2c2cdbfebe200369f83b9e60f1b0d293b` and thiscallnet/Xtra Cast PR #481
  (`c45058ebe80258b90f2dfdc424c19fe95fbba4d1`). The latter uses Media3 CastPlayer;
  its large notification/service changes are outside this adaptation.
- 2026-10-21: Implemented opt-in Media3 CastPlayer with lazy route UI, retained
  configured source factories and owned-media restoration. Downloads remain
  local. Receiver playback avoids phone background-track/proxy/restart handling;
  explicit Close ends the route while stream replacement keeps it. Added API
  23/28 handoff/close tests and trusted command checks.
- 2026-10-21: Full suite passed 898 tests before the final close/switch addition;
  the final focused 32 cases and debug/release assembly passed. An earlier lint
  run hit a Kotlin FIR analyzer crash in unchanged FeaturePortsMigrationTest;
  separate lint passed with 0 errors / 331 warnings. Final lint/profile capture
  pending. No quality gate was disabled.
- 2026-10-21: API 36.1 emulator rendered real VoD video with Cast enabled and
  opened the native device chooser. No receiver was available. A profile capture
  completed startup but timed out finding Stats; a debug PiP window was still
  active. Manual benchmark Stats navigation worked after stopping debug. Retry
  capture with debug stopped, preserving the strict journey and benchmark checks.
- 2026-10-22: Final Cast debug/release assembly and focused 32 tests passed;
  separate final lint passed (0 errors / 331 warnings). Retried profile capture
  with the debug app force-stopped. Preserved native screenshots and test/log
  evidence under ignored `.private/artifacts/step9-cast/`.
- 2026-10-22: Strict profile generation passed both startup and local Stats
  journeys in 4m54s with debug stopped. The timing benchmarks were excluded by the
  requested BaselineProfile filter (expected two skips). Saved 18,291 baseline
  rules and 16,222 startup rules, with ThystTVApp and no stale app-class entries.
  This supports the PiP-interference hypothesis for the earlier timeout but does
  not prove it; no emulator speedup claim. Final profiled release packaging passed:
  unsigned production ID/code 14 unchanged, 34,221,698 bytes, embedded profile
  6,299 bytes and profile metadata 460 bytes. APK/log/report artifacts are saved
  locally. Cast implementation and local checks complete; hardware QA remains.

PiP progress (2026-10-22): implemented optional portrait live chat using the
existing RecyclerView. Full suite passed 913 tests with no failures/errors/skips;
debug assembly passed. Initial lint passed with one new accessibility warning
for the root's read-only touch override; replaced it with input suppression at
dispatch, preserving ordinary click handling. Final debug assembly and 16 focused
PiP cases passed, including two added platform-request cases. Final lint passed
with 0 errors / 331 warnings. The final restored live player minimized/restored.
API 36.1 emulator showed real live video/chat in a 336×597 portrait PiP window,
continuing chat updates, native resize and repeated restoration. Switching to a
VoD produced a video-only landscape PiP window. Physical/API 26–30 and
authenticated composer QA remain required. No receiver or human checks claimed.

## Decisions

- Use existing native UI patterns and small policy/coordinator helpers.
- Keep each feature reviewable independently; preserve raw local verification.
- Cast stays disabled by default and is confined to the existing Media3 session
  backend. The SDK owns switching and stops the inactive player; do not create a
  second independently playing service. Preserve configured local source factories
  for returning from receiver-selected media and reject unowned receiver content.
- Receiver URL fetching cannot inherit the phone's custom HTTP headers/proxy.
  Document that limitation and retain physical receiver QA as an explicit gap.
- PiP chat adaptation: thiscallnet/Xtra #520 at
  `daf8eab52a280a2d8aecc6117e5ec7c427940273` uses a different canvas renderer.
  Reuse ThystTV's RecyclerView instead. An opt-in portrait live-only request uses
  9:16 bounds, freezes its shape while in PiP and restores normal geometry on
  exit. First-child-only chat containers suppress overlays/composer without
  changing their visibility or draft state; incoming messages keep following the
  newest item. Exclude remote playback, TV, hidden/unready/floating chat and hinge
  splits. Keep the normal video-only request with the setting off.
- PiP presentation must replace the existing video-only animation-hint tracker
  so it cannot overwrite the composite hint. Refresh cached bounds on layout and
  preference changes; retain system actions and disable seamless resize for chat,
  following Android's platform PiP guidance.

## Review handoff draft

Implementation and validation pending. Final handoff will list branch boundaries,
risks, observed behavior and required hardware QA for Claude Opus 5.5.
