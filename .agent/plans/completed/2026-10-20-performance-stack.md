# Performance and viewing polish stack

## Goal

Improve chat responsiveness, APK size, VoD seeking and startup, with an isolated
Media3 upgrade evaluated last. Preserve ThystTV playback and overlay behavior.

## Non-goals

No release, pushing or PR creation. The user chose local stacked branches and
"Core first, then ask" before larger fork features (Cast, PiP chat, recovery, TV).
Do not activate Superpowers or delegate this task.

## Current context

Base: `ae3b1c6c7`. Continue in the existing isolated performance worktree. Its
location was moved into the writable project during continuation, preserving the
unfinished changes and branches. The main checkout and unrelated files remain
untouched. Machine-specific paths and raw logs belong in ignored local evidence.

Completed before the continuation:

- `perf/1-upstream-oct14`: `bdf483b72`, status-bar detach guard and current points query.
- `perf/2-remove-glide`: `16e47f475`, remove unused dependency/setting. Debug APK
  reduced from 50,577,941 to about 48,450,000 bytes; no release-size claim.
- `perf/3-chat-pipeline`: `295228963`, `6bda4f5dc`, `d94f7c5b7`, `1fda11ab0`:
  batched list ownership, keyed lookups and guarded/coalesced image updates.
  Earlier assembleDebug/test/lint and an emulator chat smoke passed.
- Step 4 thumbnail resizing was investigated and skipped: actual default cards
  can span the phone width and Coil already decodes to the view dimensions.
  Do not force smaller artwork without measured bandwidth/quality evidence.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- `app/src/main/java/com/github/andreyasadchy/xtra/model/VideoStoryboard.kt`
- `app/src/main/res/layout/player_layout.xml`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- Gradle configuration, baseline profile module and generated profile
- `docs/PLAYER.md`, `docs/TESTING.md`, `docs/MANUAL_QA.md`, `CHANGELOG.md`

## Risks

Seek overlay and compact control geometry: medium. Build/profile tooling: medium.
Media3: high because upstream reported Media3-service audio without video and
ThystTV previously needed an HLS rollback. Keep the upgrade on its own final branch.

## Human approval

Required before implementation: no additional approval.

Reason: the user selected local stacked commits for these core steps, including
portrait rewind and the isolated dependency evaluation. No publication or broad
lifecycle rewrite is authorized or needed.

## Implementation steps

1. Finish `perf/5-vod-scrub-previews`; see its dedicated plan.
2. `perf/6-portrait-rewind`: expose the existing action in portrait and verify
   optional controls, stream stats, RTL and wide layouts do not collide.
3. `perf/7-baseline-profile`: replace stale handwritten rules with a reproducible
   generation module, generate a real profile and verify release packaging.
   Supply startup benchmarks; require physical hardware for performance claims.
4. `perf/8-media3`: inspect 1.11.1 migration needs, update narrowly, run all checks
   and emulator playback smoke. Document unresolved device/backend soak QA.

## Verification

Each code step: assembleDebug, test, lintDebug and diff review. Build/toolchain and
Media3 changes also require assembleRelease. Inspect actual native UI renders.
Do not describe emulator observations as human/device certification.

Human QA required: live and VoD; stream switching; minimize/restore; close/reopen;
rotation/resizing/folding; PiP/background; speed/quality; gestures/floating chat;
startup on physical hardware; all supported playback backends.

Human QA completed: none in this continuation.

## Progress log

- 2026-10-20: Recovered prior progress and selected scope from durable thread history.
  Finished seek-preview hooks and added frame/placement/cancellation tests. Build running.
- 2026-10-20: Step 5 passed assembleDebug, all 857 unit tests, lint (0 errors / 336
  warnings), focused native-render recheck and diff review. Device QA is pending.
- 2026-10-20: Step 5 saved as `4f6210d87`; dedicated plan is under completed/.
  Started portrait rewind: retain existing discovery/handoff, expose its existing
  button for maximized portrait live playback, and put stats below the action row
  to avoid collision. Compact/native/RTL/200% text tests added.
- 2026-10-20: Step 6 passed assembleDebug, full test and lint (0 errors / 336
  warnings). Reviewed native portrait, RTL, 200% text and landscape renders;
  action and stats bounds stay separate. Existing discovery and player handoff
  remain unchanged. Human playback/gesture/floating-chat QA remains pending.
- 2026-10-20: Step 6 saved as `319cf27f9`, with 860 tests in 109 classes passing.
  Step 7 adds the official 1.5.0 Baseline Profile plugin/producer and ProfileInstaller
  1.4.1, isolated `.benchmark` packages, startup/local-Stats capture, and physical
  cold-start comparisons. Building capture APKs before emulator generation.
- 2026-10-20: Producer/capture APK builds passed and metadata confirms the isolated
  `.benchmark` target. Release guards: 75 passed / 2 platform skips. Generation's
  first invocation needed PowerShell quoting (documented); the existing AVD then
  refused installation with only 320 MB free. Restarted the task emulator on a
  fresh data image inside ignored `.private/emulator-perf`, preserving original
  emulator app data. Profile generation is still pending.
- 2026-10-20: Fresh-image installation worked and startup capture passed. Stats
  capture found a retained UiAutomator accessibility node becoming stale after a
  range update/swipe. Changed the generator to wait for idle and resolve current
  list bounds per gesture, keeping navigation/filter/gesture checks intact.
- 2026-10-20: Further capture diagnosis used actual emulator pixels/hierarchy:
  after the app bar collapses, the list bounds reach behind the status bar and
  the downward gesture opened system notifications. The generator now uses the
  middle half of the current list bounds, retaining all navigation assertions.
- 2026-10-20: Generation passed in 7m08s: both startup (iteration 8) and local
  navigation (iteration 11) reached the default two stable iterations. The two
  timing benchmarks were intentionally excluded by the profile-generation rule
  selector. Saved both generated source profiles and removed stale handwritten
  rules. Physical-device speedup is unmeasured. Normal build/release gates pending.
- 2026-10-20: Corrected the inherited Media3 risk assessment after reading upstream
  `9a919bfa06a2efd32bb4c5d1b72e0ec96d2f9dea` (2026-08-16): it deletes
  `Media3Fragment`/`PlaybackService` and retains direct-bound `ExoPlayerService`.
  Thus ThystTV's default path is exposed to the reported regression, not merely
  its optional direct-bound backend. Step 8 must compare default playback first;
  do not change defaults or import upstream's broad service removal.
- 2026-10-21: Step 7 passed assembleDebug, test, lintDebug and assembleRelease:
  860 tests in 109 classes, zero failures/errors/skips; lint zero errors / 335
  warnings. Release guards passed 75 tests with two platform skips. Generated
  profiles are consumed by R8 (9,174 residual rules, including ThystTVApp and
  StatsFragment). The unsigned production APK contains baseline.prof (7,496
  bytes) and baseline.profm (447 bytes), without the benchmark native library.
  No physical startup speedup is claimed. Preserve this working artifact as the
  1.9.3 control before evaluating Media3 1.11.1 on the next isolated branch.
- 2026-10-21: Saved step 7 as `7bde0aec9`; branch `perf/8-media3` evaluates
  1.11.1 with narrow HLS model compatibility. The preserved 1.9.3 control rendered
  public live and VoD video on the default session service. The first candidate
  compile required the new Variant/Rendition/master metadata signatures; the
  focused fixtures then exposed missing frame-rate JSON becoming zero, rejected
  by the newer Format builder. Preserve unknown rates as NO_VALUE and fractional
  rates as floats. Candidate verification and default-service comparison pending.
- 2026-10-21: Candidate HLS build and all 12 focused tests passed. Default-service
  playback reproduced black video while session position advanced; logs rejected
  controller commands 27/29 (surface/volume). Official 1.11.1 source shows the old
  `Callback.onConnect` default returns a placeholder, whose empty player commands
  were copied by ThystTV. Migrating only connection negotiation to `onConnectAsync`
  with controller-aware defaults; keep the service/lifecycle/default backend.
  Trusted controllers receive existing custom commands, while untrusted controllers
  retain Media3's read permissions. Callback contract tests and render retest pending.
- 2026-10-21: Callback migration passed assembleDebug and all 16 focused tests.
  Actual default-service VoD/live video now renders, with normal session actions
  and no rejected surface/volume commands. Changed a current live stream to 480p,
  verified its selected readable label, and minimized/restored visible video.
  Actual online VoD dragging showed a downloaded storyboard frame/time and then
  sought/resumed. Native screenshots and before/after logs are private artifacts.
  These are smoke checks without audible output, not the full human QA matrix.
  Reviewed the frame-rate guard against SDK API metadata and used API-23-safe
  NaN/infinity checks. Profile regeneration is running; broad gates remain.
- 2026-10-21: Profile regeneration passed in 8m47s with default stability checks;
  the final journey reached two stable iterations at iteration 12. Saved 18,686
  baseline and 16,576 startup rules. Both timing benchmarks were deliberately
  excluded by the generation selector, so no physical speedup is measured.
  Stopped only the task emulator and started assembleDebug/test/lintDebug/
  assembleRelease for the final candidate.
- 2026-10-21: Full unit suite passed: 874 tests in 111 classes, zero failures,
  errors or skips. Lint required the explicit Media3 API opt-in on the new callback;
  added the same annotation used by PlaybackService. Final assembleDebug,
  lintDebug and assembleRelease passed (lint zero errors / 331 warnings).
  Verified production package `com.tzii.thysttv`, unsigned release behavior,
  baseline.prof (7,559 bytes), baseline.profm (449 bytes), 9,367 R8 residual
  rules including current app/Stats classes, and no benchmark native library.
  Release APK: 33,258,169 bytes. Diff reviewed; all core implementation steps are
  complete. APKs, hashes, native screenshots and raw logs stay in private artifacts.

## Decisions

- Keep fork feature work out of the core stack, matching the user's scope choice.
- Keep each saved step local, on its own branch, matching the user's git choice.

## Local handoff

The local stack ends at `perf/8-media3`. Step 5 is `4f6210d87`, step 6 is
`319cf27f9`, and step 7 is `7bde0aec9`; earlier layers are listed above. Step 8
contains the isolated dependency/parser/session adaptation and regenerated profiles.
No pushes, PRs, releases, signing-key changes or broad service removal were made.

Required automated checks passed as recorded above. Human QA remains live/VoD,
switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures,
floating chat, resizing and all playback backends. Physical startup measurement
is still required before claiming a speedup. Larger fork features remain deferred
pending the user's next scope choice.
