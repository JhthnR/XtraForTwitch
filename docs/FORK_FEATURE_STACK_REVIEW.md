# Local fork feature stack review

Prepared for the user's later Claude Opus 5.5 review. No reviewer has been
launched. These changes are local, unreleased and unsigned for production.
No push, PR, publication, version bump or signing change is part of this work.

## Checkout and boundaries

Worktree: `.private/worktrees/perf` relative to the main checkout. Main `master`
and its unrelated files were not changed. Each branch contains its predecessors.

| Layer | Local branch | Feature commit | Parent |
| --- | --- | --- | --- |
| Completed performance base | `perf/8-media3` | `5e0de159f` | core performance stack |
| Cast | `feat/9-cast` | `908b2e349` | `perf/8-media3` |
| PiP chat | `feat/10-pip-chat` | `3f3024909` | `feat/9-cast` |
| Bounded live recovery | `feat/11-stream-recovery` | `535b99574` | `feat/10-pip-chat` |
| TV device sign-in | `feat/12-tv-device-login` | `529d4de1b` | `feat/11-stream-recovery` |

Review each parent-to-feature diff independently. The completed performance work
is documented in `.agent/plans/completed/2026-10-20-performance-stack.md`.
The [completed feature ExecPlan](../.agent/plans/completed/2026-10-21-fork-feature-stack.md)
records decisions and verification history. Donor provenance and primary API
references are in `UPSTREAM_SYNC_LEDGER.md`.

## Review focus

1. **Cast:** opt-in/default-off Media3 handoff, configured HTTP/parser source
   restoration, selected-quality ownership, unsupported download/loopback media,
   local/remote pause/position/speed, explicit close versus stream replacement,
   service destruction and no independently active second player. Receivers cannot
   inherit phone-only custom request headers/proxies.
2. **PiP chat:** opt-in/default-off portrait local-live eligibility; early 9:16
   request and source hint; stable shape while entered; read-only existing chat
   list without composer/poll/overlay painting; draft/geometry restoration and
   listener cleanup. Check the legacy video-only request and fold/floating cases.
3. **Recovery:** three attempts, finite offline wait, stable-playback budget
   reset, buffering watchdog, narrow HTTP classifier, explicit pause and stale
   callback cancellation. Keep the source and selection; avoid retrying receivers.
   Review the single proxy fallback and the unchanged legacy/VoD behavior.
4. **TV login:** native adaptive panel, local QR, canonical activation route,
   provider expiry/interval, explicit checks, cancellation/generation guards,
   browser/Helix routing and existing validation/storage/scopes. No background
   token polling, external QR service or code persistence. Keep tokens on the
   existing storage path; do not add real-account test credentials.

## Verified evidence

Ignored artifacts live under `.private/artifacts/step9-cast/`,
`step10-pip-chat/`, `step11-stream-recovery/` and `step12-tv-device-login/`.
They are local evidence, not release artifacts. Do not commit private APKs/logs.

The final 952-test pass includes all four layers and supersedes the earlier
per-layer counts below. Earlier results are retained to describe each boundary.

- Cast: full 898-test pass before the final close/switch addition; final focused
  32-case pass. Debug/unsigned release assembly, lint (0 errors / 331 warnings),
  strict startup/Stats profile capture and profiled release packaging passed.
  Real emulator VoD rendered and native device chooser opened; no receiver.
- PiP: full 913-test pass before final dispatch cleanup/request tests; final 16
  focused cases and debug assembly passed. Lint 0 errors / 331 warnings. API 36.1
  rendered live video/updating chat in portrait PiP, resized and restored, used
  video-only VoD/disabled-option PiP, then minimized/restored the live player.
- Recovery: final debug and full 939 tests passed, 117 suites, no failures/errors/
  skips. Lint 0 errors / 331 warnings. Production listener timing/cancellation
  cases cover API 23/28.
- TV: final debug assembly, full 952 tests / 119 suites and lint (0 errors /
  331 warnings) passed. QR round-trip and retained-view resizing use actual
  native graphics. Native signed-out phone browser login rendered correctly.
  One disposable anonymous device request confirmed 1800s expiry / 5s interval;
  no codes were stored/logged and no account was authorized. Strict startup and
  Stats profile capture passed (two journeys, two timing-only tests filtered out,
  no failures). Generated profiles contain 19,140 baseline and 16,608 startup
  rules, with `ThystTVApp` and no stale `AmethytwApp` references. Profiled unsigned
  release assembly passed.

Final APK: 34,262,994 bytes, `com.tzii.thysttv`, version code 14 / name 1.4.0,
minSdk 23. Packaged `assets/dexopt/baseline.prof` / `baseline.profm` are 6,532 / 528
bytes. SHA-256: `f5907a41d8f5acb0a650268c4a65582571e5889c8a3d5506a089a956ba648c34`.
APK signature verification reports the expected unsigned output. This APK is
local build evidence, not a signed installable release. Exact output metadata,
badging, signature check and test counts are saved in the step12 artifact folder,
including `final-verification.json`.

## Material gaps and required human QA

The recovery native smoke is **not a clean reconnection success**: API 36.1
played a real stream, retained a frame through a 33-second network interruption,
then tried recovery. Repeated behind-live-window failures exhausted the budget.
Sustained reliable reconnection still needs physical-device validation and review.
Do not convert this observation into a successful recovery claim.

No physical Cast receiver, TV/OEM remote, phone camera scan or authenticated
device grant was tested. No physical startup speed improvement is claimed.
See `MANUAL_QA.md` for the complete required matrix. It includes live/VoD,
switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures,
floating chat, receiver handoff/disconnect/network loss and TV authentication.
Use disposable accounts: opening the existing login activity revokes its previous
tokens. Keep these feature branches out of a release until review and QA resolve
the relevant gaps.
