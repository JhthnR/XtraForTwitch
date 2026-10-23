# Preserve Cast ownership across reopened and rejected playback starts

## Goal

Resolve GPT review F1–F3 on frozen 1.5.0 RC d5631277: a retained session must not
compete with another backend, rejected downloads must not control unrelated
receiver media, and remote source changes must honor the local-only URL policy.

## Non-goals

No SDK update, player backend rewrite, updater/site changes, foreground VoD retry
change, account operation, tag or publication. Keep both Cast switches off.

## Current context

The signed RC passed build/signature/manifest gates and limited emulator upgrade
smoke, but the independent GPT-6.1-sol/high review requested changes. GLM5.3/max
completed; its low findings were verified separately. Current source confirms:
MainActivity replaces only an attached fragment; a kept service can survive
without one; offline command failure is ignored; setSource bypasses canCast.

## Files likely involved

- MainActivity.kt, PlaybackService.kt, Media3Fragment.kt
- CastPlaybackHandoff.kt and a small service-owned session reference
- Focused ownership, command-result and handoff regression tests
- PLAYER.md, MANUAL_QA.md and CHANGELOG.md

## Risks

Risk level: high (player/session integration and release). Protect the receiver
from accidental stop/save under a rejected source, stale callbacks and old
service teardown. Keep all guards on the existing main looper. A service-owned
reference must not leak the service/player or initialize Cast for default-off
playback. A receiver returning locally must still have one owner.

## Human approval

Required before implementation: yes; received for finishing release preparation
and its required corrections. The user explicitly continued the work. This is
a focused, reversible correction within the already-authorized Cast scope.
Exact signed-byte device/publication approval remains absent and is not inferred.

## Implementation steps

1. Register the live service-owned player with weak lifetime/identity-safe
   cleanup. Consult that owner before replacing playback UI. Reject downloads
   while remote and incompatible backend starts while an unattached session
   still owns media, including after a receiver returns locally.
2. Consume source-command results. A rejected start clears its own started state,
   releases only its controller, explains the rejection and removes only its UI;
   it must not pause/stop/clear/save the retained receiver under new arguments.
3. Preflight service source commands before metadata/recovery changes; enforce
   eligibility again in both handoff setSource paths before source/factory/media
   ownership changes. A rejected remote URL preserves the old receiver item.
4. Add meaningful regression cases for every entry point/backend, no-fragment
   service ownership, route return, rejected/stale command results and loopback
   URL switches. Keep ordinary local/default-backend starts working.
5. Update behavior/QA docs, run required gates, inspect diff, open narrow PR.
6. After green CI and merge, freeze a NEW master SHA, build/verify a new signed
   bundle, repeat smoke and BOTH independent review lanes. Old RC is invalidated.

## Verification

- [x] Focused regression tests on API23/28, including actual activity entry paths
- [x] assembleDebug and test (1,072 debug unit tests; zero failures)
- [x] lintDebug and unsigned assembleRelease
- [x] Repository contract/site/release-tool checks (79 passed; 2 platform-specific skips)
- [x] Diff reviewed; no unrelated changes or private artifacts staged

Human QA required: receiver keep-on-exit/reopen, default and both alternate
backends, live/VoD/clip/download switching, rejection feedback and unchanged
receiver media, disconnect/local return, notification control, close/reopen,
minimize/restore, PiP/background, speed/quality, gestures and floating chat.
Hardware is unavailable; no physical Cast/TV pass is claimed.

## Progress log

- 2026-10-23: Verified all three source paths and created isolated branch
  fix/cast-review-ownership from d5631277. Original frozen tree remains untouched.
- 2026-10-23: Fixed test application accessors and nested Mockito stubbing,
  strengthened the alternate-backend guard even with a visible Media3 fragment,
  and clarified the RC notes. Focused tests, all 1,072 debug unit tests,
  lintDebug, assembleDebug, unsigned assembleRelease and repository contract
  checks passed. No receiver/device verification is claimed.

## Decisions

- Reject incompatible backend starts while a retained session owns media rather
  than introduce an asynchronous cross-service takeover. Explain how to stop
  the current session. Default Media3 online starts reuse the same service.
- Failed source UI is discarded without closing the receiver session. Async
  results are checked against the originating controller/current fragment.
- No change to 1.5.0/code15: it has not been published. Any source correction
  nevertheless requires a new exact signed candidate and review cycle.

## Final PR summary draft

Fix retained Cast ownership and rejected-source UI, including remote loopback
source switches. Add targeted regression coverage and explicit hardware QA.
Validation and signed-candidate successor recorded after the final gates.
