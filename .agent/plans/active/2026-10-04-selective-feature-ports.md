# Selective Xtra feature ports

## Goal
Improve quality selection and metered-network presets, notification reliability,
custom Channel Points redemption, and recording-backed live rewind.

## Non-goals
No release/version/signing changes, whole-fork merge, minSdk increase, Hilt removal,
Media3 upgrade, networking rewrite, EventSub service, multiview, clips, or Drops.

## Current context
Baseline master e8cbd77cb9f875ed2e4b777684065278bc766060 (1.3.1 Android code).
Donors: crackededed/Xtra 9f8043e and thiscallnet/Xtra fbffe567. Manual adaptation
retains ThystTV's minSdk 23, Hilt, WorkManager, Media3 1.9.3 and existing UI.
The existing quality selection orders FPS incorrectly on one backend and labels
can collapse distinct variants. Donor player/service code has diverged heavily.

## Files likely involved
- model/VideoQuality, playlist parsing, player quality/network selection
- ui/main/LiveNotificationWorker and focused notification persistence helpers
- ui/chat reward dialog/ViewModel and narrow GraphQL reward transport
- ui/player recording discovery, existing live/VoD transition entrypoints
- focused regression tests and docs/UPSTREAM_SYNC_LEDGER.md

## Risks
Quality persistence across backends; failed API loads and duplicate notifications;
reward confirmation and uncertain outcomes; stale callbacks during player changes.
Risk level: high for player transitions, medium for bounded other ports.

## Human approval
Required before implementation: yes. Received explicit implementation approval
October 4, including independent adversarial review. A new GitHub branch was authorized on October 4; a draft PR may be used to
run existing CI. Master updates, merge, release and deployment remain outside scope.

## Implementation steps
1. Implement scoped independent manual ports with regression tests.
2. Reuse existing single-player live-to-VoD replacement for rewind: discover and
   validate current recording first, keep live untouched on absence, offer Go Live,
   restore live on recording startup failure. No second decoder or service rewrite.
3. Inspect adversarially for logic, lifecycle, stale state and compatibility errors.
4. Run available Gradle checks; document exact unavailable gates, never infer device QA.
5. Update source provenance and practical manual QA; produce a local commit/patch.
6. Publish only the authorized new branch, use a draft PR for CI, and verify the
   exact remote commit and terminal checks. Do not merge or release.

## Verification
Automated checks:
- [x] ./gradlew assembleDebug
- [x] ./gradlew test
- [x] ./gradlew lintDebug
- [x] ./gradlew assembleRelease (unsigned)
- [x] git diff --check and independent source reviews

Human QA required:
- [ ] Live/VoD playback, stream switching and no old audio
- [ ] Minimize/restore, close/reopen, rotation, PiP/background
- [ ] Quality variants, metered/unmetered changes, speed and gestures
- [ ] Phone/tablet/TV remote and floating chat
- [ ] Notification account/local-follow changes, denial/failure and dedup
- [ ] Authenticated reward availability/input/confirm/error/cancel
- [ ] Recording absent, old session, source failure, scrub and Go Live

Human QA completed: none.

## Progress log
- 2026-10-04: Cloned baseline and donor snapshots; scoped implementation ownership.
- 2026-10-04: Gradle 9.4 help, debug-unit-test dependency resolution and
  generateServiceApolloSources passed with isolated writable cache/proxy settings.
- 2026-10-04: System Java was a JRE (no javac); provisioned official Temurin JDK
  21.0.12.1 in the isolated build workspace. Repository Node contracts then passed:
  76 passed, 5 native-Windows-only skips, 0 failed. Static updater contracts and
  all Android resource XML syntax checks passed.
- 2026-10-04: Android SDK installation awaits explicit SDK license acceptance.
  No Android compilation, Robolectric execution, lint or APK assembly yet.
- 2026-10-04: First independent rewind review identified background READY,
  explicit-offset consumption, prior-recording mismatch, asynchronous backend
  startup error and minimized recovery cases. Fixes and regression tests added;
  follow-up independent source reviews completed with no unresolved concrete findings.
- 2026-10-04: Added Room35→36 additive migration for download identity and
  notification stream receipts. Production SQL passed SQLite preservation,
  nullable-default, identity-roundtrip and receipt uniqueness checks across all
  18 prior tables. Official Room36 schema export is configured but awaits KSP.
- 2026-10-04: Actual aggregate assembleDebug/test/lintDebug/assembleRelease attempt
  stopped before compilation: SDK location not found. SDK acceptance remains
  pending; no license accepted, no APK built, no Android tests or lint run.
- 2026-10-04: Focused production policy probes passed: rewind: 20 (model stubs),
  reward DTO/model: 21, playlist parser: 7, notification timestamp: 13 and state: 16
  (in-memory Android adapters). Committed JUnit/Robolectric suites remain unrun.

- 2026-10-04: Explicit approval received for official Android SDK installation and
  its disclosed license terms, plus publication on a new GitHub branch. Installed
  Android36 platform/build-tools36.0.0; only android-sdk-license accepted. Aggregate
  Android checks are now running; earlier blocked-check entries are historical.

- 2026-10-04: Full local aggregate passed after compile, fixture and lifecycle
  corrections: assembleDebug, test (725 tests, zero failures/errors/skips),
  lintDebug (zero errors, 313 warnings), assembleRelease (unsigned). Generated
  Room36 schema was independently checked and Room migration tests passed on
  API23/28. Hosted branch/CI verification is the next step.

- 2026-10-09: Review fixes: quality fallback/codec ordering, rewind recovery after
  playback starts, rewind offset from the media playlist, audio-only restore on
  Wi-Fi, single live-follows notification poll, skipped incomplete entries,
  removed `notification_receipts` and `quality_path`, capped worker retries, and
  definite reward rejections. Full aggregate passed again: 735 tests, zero lint
  errors. Split into four stacked branches (notifications → rewards → quality →
  rewind); each intermediate layer was built and tested on its own.

## Decisions
- Manual ports only; upstream architecture and dependency changes intentionally deferred.
- Rewind uses the established player replacement and VoD UI, preserving replay chat,
  stats and lifecycle conventions rather than introducing a second playback architecture.
- Notifications keep 1.3.1's single live-follows request per poll; a full follow-list
  sync on every poll scales badly and only fed a fallback cache.
- Stacked per-feature PRs ordered by risk: independent notification and reward work
  first, player work (quality, then rewind, which depends on it) last.

## Final PR summary draft
Summary: All five bounded feature areas implemented with preserved minSdk23,
Hilt, WorkManager and Media3 1.9.3; no version bump or publication.
Tests: full configured Android aggregate passed, including 735 tests and
zero-error lint. SDK setup blocker resolved. See
`docs/FEATURE_PORTS_VALIDATION.md` for exact passed/unrun boundaries.
Human QA: listed above; none performed.
Risks: external Twitch/device behavior remains unverified. Hosted exact-commit CI,
manual device/authenticated-flow QA and user review are distinct from local validation.
