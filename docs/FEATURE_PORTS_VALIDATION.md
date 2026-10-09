# Feature-port validation report

Date: 2026-10-09 (first pass 2026-10-04). Baseline: `e8cbd77cb9f875ed2e4b777684065278bc766060`.
Scope and provenance: [feature guide](FEATURE_PORTS_2026_10.md) and
[upstream ledger](UPSTREAM_SYNC_LEDGER.md).

## Status

The ports are split into four stacked branches, each reviewable and mergeable in
order:

| Layer | Branch | Contents |
| --- | --- | --- |
| 1 | `xtra-ports/1-notifications` | Live notification polling, cache and dedup; series docs |
| 2 | `xtra-ports/2-rewards` | Channel Points custom rewards |
| 3 | `xtra-ports/3-quality` | Exact quality variants, metered Wi-Fi preset, Room 35→36 |
| 4 | `xtra-ports/4-rewind` | Recording-backed live rewind |

The top layer passed the full local Android aggregate:

```bash
./gradlew assembleDebug test lintDebug assembleRelease
```

Final result: exit 0. This establishes the configured automated checks, not real
Twitch endpoint success, physical-device behavior or approval to publish a release.
Hosted CI must be checked against the exact proposed commits and is reported
separately from this local pass. No merge or release is part of this change.

## Android checks

| Check | Layer 1 | Layer 2 | Layer 3 | Layer 4 (top) |
| --- | --- | --- | --- | --- |
| Debug APK assembly | Passed | Passed | Passed | Passed |
| Unit suite (0 failures, errors or skips) | 624 tests, 72 classes | 662 tests, 77 classes | 708 tests, 82 classes | 735 tests, 87 classes |
| Debug lint errors | Not run | 0 | 0 | 0 |
| Release compilation, R8 and unsigned APK | Not run | Not run | Not run | Passed |
| Room schema export | n/a | n/a | Genuine KSP-generated `36.json` | Same |

Layer 4's tree is byte-identical to the review branch that passed the aggregate.
Documentation-only edits followed that run.

The configured `test` task ran the debug unit suite. This report does not claim a
separate release-unit suite ran; the task catalog contains no `testReleaseUnitTest`.
Tests cover quality selection/labels/parser/network preferences, migration and
download resume, notification policy/state/repository/worker, rewards, and rewind
discovery, handoff, lifecycle and MediaPlayer failure recovery.

Toolchain: JDK 21.0.12.1, Gradle 9.4.0, Android platform 36 and build-tools 36.0.0.
The app remains minSdk 23, targetSdk 36, version 1.3.1/code 13 and Media3 1.9.3.
Gradle did not auto-detect the installed JDK 21 on this machine; checks passed it
explicitly with `-Porg.gradle.java.installations.paths`. No project setting changed.

## Lint and localization boundary

Debug lint reports 0 errors and 347 warnings on the top layer (313 on October 4).
Most warnings are dependency-version checks (`NewerVersionAvailable`,
`GradleDependency`, `UseTomlInstead`) whose count follows what has been published
upstream on the day lint runs. Warnings anchored on added lines are three
SharedPreferences KTX style suggestions and one composed localized label. The
direct `commit()` calls deliberately inspect the durable write result; the label
combines already-localized text with a newline.

The new English feature strings deliberately use English fallback until
translations are vetted, following the repository's existing scoped convention.
Only those strings carry `MissingTranslation` annotations. Other lint checks stay
enabled. Media3 helper usage explicitly opts into its existing unstable API;
the reward dialog's lifecycle issue was fixed rather than suppressed.

## Database verification

Generated schema 36 adds exactly two nullable `videos` columns
(`quality_codecs`, `quality_bitrate`). All other fields, keys, indices, foreign
keys and views across the 18 tables are unchanged, and no table is added.

- The real Room/Robolectric migration test runs on API 23 and 28: legacy
  download and notification rows survive, and the exact codec/bitrate download
  identity persists across reopen.
- Room identity hash: `cde255e41570f6cc24723aa93b410910`.

Test upgrades with backed-up data. Installing an older app afterward is not an
automatic database rollback.

## October 9 review changes

A review of the October 4 port led to these changes, each with regression tests:

- Quality fallback no longer drops a rung on a higher-bitrate channel; codec
  fallback compares codec families and never raises resolution.
- Rewind: only the initial handoff returns to live; later errors use VoD recovery.
  The rewind offset is measured from the recording's media playlist.
- A network-default audio-only choice returns to video on Wi-Fi.
- Notifications poll live follows in one request instead of paging every follow,
  skip incomplete entries instead of failing a batch, drop the
  `notification_receipts` table, and cap WorkManager retries.
- Downloads no longer persist a session-specific variant path (`quality_path`).
- Channel Points refusals before execution are definite rejections.

## Independent review and remaining QA

Correctness/lifecycle reviews resolved quality metadata/fallback issues,
notification account and baseline races, reward web authentication/logging,
recording ownership/readiness/recovery, and async test completion.

No physical device/emulator decoding, live Twitch notification delivery, real
reward spending, or complete Hilt/DialogFragment recreation scenario was tested.
Notification posting and durable acknowledgment cannot be strictly atomic across
hard process death. Rewind depends on an available server recording and uses the
ordinary VoD timeline, not local DVR.

The required device/authenticated-flow matrix remains in
[the feature guide](FEATURE_PORTS_2026_10.md). Manual QA and release/version review are required before shipping.
