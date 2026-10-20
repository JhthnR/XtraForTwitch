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

## Decisions

- Keep fork feature work out of the core stack, matching the user's scope choice.
- Keep each saved step local, on its own branch, matching the user's git choice.

## Final PR summary draft

Summary: Separate scoped steps for chat, size, seeking, rewind and startup;
Media3 is independently reviewable at the stack tip.
Tests: Record actual outcomes below as each step completes.
Human QA: Remains the player matrix plus physical startup measurement.
Risks: Device-only playback and performance cannot be established by unit tests.
