# Clear Cast restoration when the player explicitly closes

## Goal

An explicit player close must stay closed when an asynchronous Cast return arrives after a rejected download or phone-local source transfer.

## Non-goals

No release metadata/version change, receiver behavior redesign, upstream sync, dependency change or VoD restart cleanup.

## Current context

During final 1.5.0 release verification, a focused real-ExoPlayer diagnostic reproduced a stale rejected snapshot being restored after the explicit close sequence. CastPlaybackHandoff holds that snapshot until source replacement, successful transfer or clear. PlayerCastBridge.endSession previously ended the SDK session without clearing it. Frozen candidate 0d717b8 / run40022378707 is therefore superseded for publication; no stable tag exists.

## Files likely involved

- app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerCastBridge.kt
- app/src/main/java/com/github/andreyasadchy/xtra/ui/player/CastPlaybackHandoff.kt
- app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerCastBridgeTest.kt
- app/src/test/java/com/github/andreyasadchy/xtra/ui/player/CastPlaybackHandoffTest.kt

## Risks

Risk level: high (player/release). Keep normal rejection fallback and subsequent sessions working. Invalidate state before both synchronous and delayed disconnect callbacks.

## Human approval

Required before implementation: yes; received through the maintainer's instruction to finish publication, including necessary regression fixes. The maintainer tested the prior exact APK successfully; that result does not approve later signed bytes. New exact-byte approval remains a release gate.

## Implementation steps

1. Reproduce close-after-rejection on the frozen source in an isolated worktree; preserve failure outside source.
2. On explicit endSession, cancel queued rejection work and clear handoff state before ending the SDK session. Preserve the bridge for future sources.
3. Test rejected local download/proxy returns, synchronous disconnect and subsequent valid fallback.
4. Run focused and full Android/repository gates, open a narrow follow-up PR, merge green CI, freeze/build/verify a new signed RC and repeat required reviews.

## Verification

Automated checks:

- [x] Original diagnostic fails on source tree c42bfa85 with closed media restored.
- [x] Focused Cast/session tests on API23/API28:64 tests, no failures/errors/skips.
- [x] gradlew test:1082 tests, no failures/errors/skips.
- [x] gradlew lintDebug assembleDebug:0 lint errors/331 existing warnings; debug passed.
- [x] gradlew assembleRelease:unsigned release passed.
- [x] Node site/workflow/release suites:79 passed,2 platform skips.

Human QA required:

- [ ] Exact newly signed APK official upgrade/data, offline Saved and restoration, updater.
- [ ] Live/VoD, switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures and floating chat.
- [ ] Cast reject file/proxy and close before disconnect finishes; no restored local media/audio; normal rejection fallback and later sources still work.
- [ ] Physical receiver/TV/non-GMS limitations remain explicitly unverified where hardware unavailable.

Human QA completed: prior exact-candidate phone smoke reported successful; superseded by this correction. No new signed-byte QA claimed.

## Progress log

- 2026-10-24: Reproduced old snapshot restoration with actual PlayerCastBridge/ExoPlayer and an injected SDK transfer callback. Preserved failing source/log/XML privately. Added state invalidation and regression coverage before/during SDK disconnect callbacks.
- 2026-10-24: Final GPT/high review independently confirmed the close-state defect and localhost hostname forms bypassing rejection. Added DNS-free rejection of absolute localhost and .localhost subdomains, with outgoing transfer and already-remote preservation tests. Node suites passed 79/81 (two platform skips) with Git Bash first on PATH; the first attempt selected the Windows WSL launcher and failed a shell fixture, without a source defect.
- 2026-10-25: All four PR58 CI checks passed on 3bf9b61d3. Local full suite passed1082 tests; lint0 errors/331 existing warnings and debug assembly passed. Isolated worktree initially lacked the existing ignored debug keystore; supplied it and resumed only unfinished build gates. Unsigned release assembly is in its final optimization/packaging steps. No source changes after this correction are planned; final signed-run/review/device evidence will remain outside source after freezing master.
- 2026-10-25: Resumed build gates completed successfully, including unsigned release assembly. No gate was disabled. Final documentation-only progress update records these results before the new freeze.

## Decisions

- Clear state only on explicit session end; automatic rejected-transfer teardown calls endCurrentSession directly, preserving its intended local fallback.
- Do not retire the bridge generation on explicit disconnect: later sources must remain usable.
- Retain 1.5.0/code15 because unpublished; build a new signed candidate after merge rather than altering or republishing old bytes.

## Final PR summary draft

Explicitly closing a player during a rejected Cast transfer could restore its old media when disconnect completed. Clear the pending handoff before ending the SDK session and cancel queued rejection callbacks. Regression tests cover delayed/synchronous returns and a later fallback.

Tests: focused64 and full1082 pass, lint0 errors, debug/unsigned release pass, Node79 pass/2 skips. Human QA: exact new signed APK and receiver timing remain required. Risks: Cast callback timing; no source is restored after explicit end.
