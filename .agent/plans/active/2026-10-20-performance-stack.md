# Performance and viewing polish stack

## Goal

Improve chat responsiveness, APK size, VoD seeking and startup, with an isolated
Media3 upgrade evaluated last. Preserve ThystTV playback and overlay behavior.

## Non-goals

No release, pushing or PR creation. The user chose local stacked branches and
"Core first, then ask" before larger fork features (Cast, PiP chat, recovery, TV).
Do not activate Superpowers or delegate this task.

## Current context

Base: `ae3b1c6c7`. The task worktree was moved from
`C:/Users/simon/Documents/Projects/ThystTV-perf` to
`C:/Users/simon/Documents/Android/ThystTV/.private/worktrees/perf` on October 20
so continuation stays inside the writable project. Originals and branches were
preserved. The main checkout and its unrelated files remain untouched.

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
Media3: high because upstream reported legacy-backend audio without video and
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

## Decisions

- Keep fork feature work out of the core stack, matching the user's scope choice.
- Keep each saved step local, on its own branch, matching the user's git choice.

## Final PR summary draft

Summary: Separate scoped steps for chat, size, seeking, rewind and startup;
Media3 is independently reviewable at the stack tip.
Tests: Record actual outcomes below as each step completes.
Human QA: Remains the player matrix plus physical startup measurement.
Risks: Device-only playback and performance cannot be established by unit tests.
