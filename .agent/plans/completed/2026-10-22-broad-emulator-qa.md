# Broader emulator regression pass after Opus corrections

## Goal

Exercise as much of the corrected app as practical on the Android emulator,
record actual results and evidence, and fix confirmed regressions in scope.

## Non-goals

No release, signing/version changes, push, PR or new reviewers. No real accounts,
outbound user chat, purchases or claim of physical-device/receiver certification.

## Current context

Work only in .private/worktrees/perf, fix/13-opus-review at f5dedd58d. The final
code a0acbb2de passed 1,005 tests, debug/unsigned release builds and lint (0 errors,
331 existing warnings). The exact debug APK hash is
aadee7cb4eeb08ba750775468a871212eaa080392f589596d29ac70ef2cdac1f.
Prior emulator checks covered short/long video/chat outage recovery and wide PiP.
The user explicitly requested a broader emulator test pass.

## Files likely involved

- docs/EMULATOR_QA_2026_10_22.md, TESTING.md, MANUAL_QA.md, OPUS_REVIEW_FIXES.md.
- Player/chat/settings code and scoped regression tests only if a defect reproduces.
- Ignored raw evidence under .private/artifacts/broad-emulator-qa-2026-10-22/.

## Risks

Testing risk: medium, restricted to the dedicated signed-out emulator-5568 and
ADB server 5038. Preserve its data. Do not touch other emulators or global ADB.
Any player changes are high risk and require the normal full Gradle checks,
targeted native retest and updated human QA. Emulator timing is not a benchmark.

## Human approval

Required before implementation: no further approval for the scoped local tests
and fixes. The user explicitly authorized emulator testing; the prior task
authorized fixing issues. Physical/account-dependent cases remain documented gaps.

## Implementation steps

1. Start the existing dedicated task device and verify/install the exact APK.
2. Test live/VoD, speed/quality, seeking, player lifecycle, PiP and chat controls.
3. Test adaptive geometry, accessibility text, Stats, settings and signed-out flows.
4. Fix reproducible scoped issues; verify changed behavior and required gates.
5. Save a coverage matrix with pass/fail/limited/not-tested results and evidence;
   restore settings/network/orientation, stop only the task emulator and hand off.

## Verification

Native evidence uses actual screenshots/UI, media-session state and sanitized
recovery traces. Log observations per scenario without equating service PLAYING
with visibly decoded video. Keep raw APKs/logs/screenshots ignored. Reuse the
passing Gradle results when source is unchanged; rerun relevant checks for fixes.

Human QA remains required for real Cast discovery/decoding/handoff, physical TV
remote/QR/authentication, audio overlap and hardware decoding/performance, plus
the player/chat matrix in MANUAL_QA.md. Human QA completed: none.

## Progress log

- 2026-10-22: Started a broader pass on the final correction branch. Loaded project
  testing/player guidance; located existing phone/foldable AVDs. No new agents.
- Native pass covers live playback, quality/audio/chat-only, system pause/resume,
  minimize/browse/restore, close/reopen, source replacement, ongoing VoD large
  seeking, speed/quality retention, paused seek, PiP/system actions and landscape
  brightness/volume gestures. Raw results distinguish buffering and settled frames.
- Found P20: VoD replay chat stays empty after PiP/stop-return while video keeps
  playing, until seeking emits another READY event. resumeReplay creates a fresh
  manager but does not arm loading. Add a narrow start when position is available;
  defer unknown-position reconnection to the existing READY hook. Cover actual
  comment delivery/current offset, late READY idempotence and asynchronous local data.
- 2026-10-23: P20 was fixed in `6718ad20c` and confirmed on decoded VoD replay.
  P30 Saved fixed with adaptive tab mode, P31 Search with scrollable tabs; a
  preliminary forced-scrollable Saved setting broke a TV remote-focus test and
  was reverted before the successful full-suite rerun.
- 2026-10-23: P32 landscape sidebar chat successfully hidden/restored on decoded
  live video. P33 reproduced a 200% font-scale Watch live/tab-row overlap on
  channel profiles; font-scale-dependent header clearance fixes this on the
  emulator, and the 100% layout remains unchanged. The private evidence index
  records 33 latest case passes, with physical/audio/Cast/account limits.
- The final 33-case matrix and remaining human QA are documented in
  `docs/EMULATOR_QA_2026_10_22.md`; do not publish ignored private artifacts.
- Final Gradle gates passed (`assembleDebug`, `test`, `lintDebug`, `assembleRelease`):
  1,012 tests / 128 suites / no failures, lint 0 errors and 331 warnings.
  The dedicated AVD was returned to baseline system settings and stopped;
  its data was preserved. No release, push or account/receiver action occurred.

## Decisions

- Use existing Android SDK/ADB tools and the dedicated private task data directory.
- Preserve prior accepted outage evidence; prioritize the untested regression areas.
