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
PiP chat is `feat/10-pip-chat` at `3f3024909`;
recovery is `feat/11-stream-recovery` at `535b99574`. Current branch:
`feat/12-tv-device-login`.

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

Recovery progress (2026-10-22): production service listener and 24 focused API
23/28 cases passed. Final debug assembly and the full suite passed 939 tests,
117 suites, no failures/errors/skips. Final lint passed (0 errors / 331 warnings).
API 36.1 rendered real live video and retained a frame during a 33s interruption.
Retries ran after connectivity returned, then repeated behind-live-window errors
exhausted the budget. Clean sustained native reconnection remains unverified;
do not present this as a successful reconnection. Physical QA remains required.
Full XML/log/screenshots/APK evidence saved under ignored step11 artifacts.

## Decisions

- TV adaptation: retain existing D-pad/player work and expose the existing device
  grant through a TV entry with its browser-feature limitation explained first.
  Use ZXing core 3.5.4 for local QR encoding, with a canonical Twitch activation
  URL and a visible code fallback. Reuse the existing token validation/storage
  path and scopes. Explicit checks respect the server interval; no token polling.
  Expiry, generation checks, single requests, lifecycle clock suspension and
  teardown prevent stale completions. Preserve browser/Helix sign-in routing.
  Verify the QR round trip, layout/focus and controller failure/cancellation paths.
  Regenerate profiles and assemble unsigned release after the dependency change.

- Live recovery belongs to a service-owned listener on the active player. Retry
  network/timeouts, HTTP 408/429/5xx and behind-live-window failures at 1s/2.5s/5s,
  with a 60s validated-network wait and a 15s buffering watchdog. Reset only after
  30s actual continuous playback or an explicit source command. Keep the source,
  tracks and play intent; never clear/rebuild it during retries. Stop buffering
  before prepare because prepare alone is ineffective outside IDLE. Cancel for
  pause, stop, source change, Cast, close and teardown. Permanent/auth/offline
  errors stay visible; preserve the existing single one-way custom-proxy fallback.
- The default Media3 backend receives this recovery change. The optional legacy
  ExoPlayer backend is unchanged. No donor's ad/proxy/service rewrite is imported.
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

## Review handoff

TV progress (2026-10-22): code flow compiled and the original 12 focused cases
passed. Native phone smoke caught a missing chain constraint; corrected it and
confirmed actual full-height WebView bounds and Twitch login rendering. Added a
viewport regression fixture (detached Robolectric WebView has zero height) and
retained-view adaptation for rotation. A removed wide layout remained in AGP's
compiled merge output; deleted that exact generated stale file, preserving source
and gates. Final full checks now use the adaptive layout. A disposable anonymous
Twitch device request returned valid metadata (1800s expiry / 5s interval); codes
were not logged/stored and no account was authorized.

Final debug assembly, full suite (952 tests / 119 suites, no failures/errors/skips)
and lint (0 errors / 331 warnings) passed after the source/resource corrections.
Strict startup/Stats profile capture passed: two journeys, no failures, two
timing-only tests filtered out. Generated baseline/startup profiles have 19,140 /
16,608 rules and no stale application-class references. Unsigned release assembly
passed. The 34,262,994-byte APK retains `com.tzii.thysttv`, version code 14 /
name 1.4.0 and minSdk 23. Packaged baseline.prof / baseline.profm are 6,532 / 528
bytes. SHA-256: `f5907a41d8f5acb0a650268c4a65582571e5889c8a3d5506a089a956ba648c34`.
APK signature verification confirms the expected unsigned output. No credentials,
release signing, version bump or publication was used.
`docs/FORK_FEATURE_STACK_REVIEW.md`
records branch boundaries, risks and human QA for the later Claude Opus 5.5 review.
