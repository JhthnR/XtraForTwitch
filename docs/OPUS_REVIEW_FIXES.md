# Opus review corrections — local feature stack

This is the historical pre-publication correction record. The stack was later
pushed as PR #53, with the background retry and Cast exit follow-ups in #54/#55.
Current candidate scope and QA limits are in `release-notes/1.5.0.md`; the
validation results below apply to the specific older commits named here.

The original Claude Opus 5.5/max review targeted `cf2127566` and requested changes.
Corrections are on local branch `fix/13-opus-review`, based on that reviewed SHA.
At the time of this correction pass, the original feature branches and main
checkout were preserved, nothing had been pushed, published, versioned or signed
for production, and the fixes had not received a second independent review.

## Corrected behavior

| Finding | Correction |
| --- | --- |
| P1 | PiP uses the existing attached-window observer helper; detach removes the callback before fragment view destruction. |
| P2 | Video-only PiP explicitly restores 16:9 and uses the actual video frame hint, including wide/sidebar layouts. |
| P3 | Leaving PiP chat clears stale scroll-up/touch flags and resumes normal newest-message following; drafts remain intact. |
| P4 | Cache feature support and observe only supported devices; retain the explicit API 26 guard for minSdk 23. No measured speedup is claimed. |
| C1 | Re-read Cast settings on explicit source starts; replace/release the stopped route behind a stable session player. UI uses actual service capability. Explicit close ends an existing SDK session even if the bridge was local-only. |
| C2 | Local return honors keep-screen-on-when-paused while buffering; remote playback does not hold the phone screen awake. |
| C3 | Source changes retain receiver volume/speed instead of applying stored phone defaults. |
| C4 | Reject expanded/mapped loopback and wildcard IP literals without resolving hostnames. |
| R1 | Corrected live-edge recovery and budget ownership; final short and exhausted-budget long outages resumed actual video on the task emulator. Physical outage/handover QA remains required. |
| R2 | Proxied HTTP failures bypass coordinator retries and reach the guarded one-way direct fallback. |
| R3 | One observed offline-to-validated return can start one additional finite episode after exhaustion; repeated flaps cannot continuously rearm it. |
| R4 | Non-retried and receiver errors use accurate stopped/retry-manually feedback. |
| R5 | Exhaustion is separate from pending recovery; later 404/auth/proxy errors stay visible. Explicit failed/exhausted retry can rearm the budget. |
| T1 | Distinguish denied, invalid, expired and validation-failed responses; retain completion with a previously validated Helix account. |
| T2 | Activation instructions work for a phone browser or another device. |
| Existing factory gap | Manual quality/audio MediaItem changes retain the active HTTP/HLS/parser factory; replacement and downloads reset the delegate appropriately. |

Two additional self-review corrections protect the fixes: routine quality/track
Prepare calls and metadata refreshes cannot reset recovery limits, and route
replacement transfers/clears the current video output. Connectivity callbacks
sample the active network during handover, include VPNs on API 23 and reject
queued/late notifications after teardown.

## Commit boundaries

| Commit | Scope |
| --- | --- |
| `4c935317f` | PiP observer, legacy shape and chat following, with focused tests. |
| `3acfb2add` | Cast configuration, capability, receiver controls, screen and literal guards. |
| `99fad4fc2` | Recovery error visibility, proxy fallback, explicit retry and bounded network return. |
| `8bb6bbb83` | Prevent routine Prepare calls from resetting recovery limits. |
| `f6525eab6` | Login failure classification and held-Helix completion. |
| `3d4e8c2a3` | Configured factory retention through quality/audio item changes. |
| `fd627d867` | Stable recovery source identity, route video-output transfer/cleanup, connectivity teardown and explicit PiP API guard. |
| `a0acbb2de` | Validated-network notifications and live chat reconnection with captured cleanup ownership. |

Final lifecycle guards and validation evidence are recorded in the
[completed correction plan](../.agent/plans/completed/2026-10-22-opus-review-corrections.md)
and branch history.

## Validation and evidence

Final checks on the source at `a0acbb2de` passed:

- `assembleDebug`, `test`, `lintDebug`, `assembleRelease`.
- **1,005 tests / 127 suites, zero failures, errors or skips.**
- **Lint: zero errors / 331 existing warnings.** No baseline or gate was weakened.
- Release ID `com.tzii.thysttv`, version 1.4.0/code 14 and minSdk 23 unchanged.
- Unsigned release: 34,264,858 bytes; SHA-256
  `1f5a5182f954bb5604768991dc5ccc0a2d1f649ea055af73029cb9ae23c1328b`.
- Packaged `baseline.prof` is 6,541 bytes and `baseline.profm` is 528 bytes.
  Existing profiles were consumed; these corrections change no dependency or
  profiled startup/navigation journey, so profiles were not regenerated.
- `apksigner verify` confirms the release is unsigned (expected exit 1, missing
  `META-INF/MANIFEST.MF`). This is not a signed release candidate.

Earlier focused passes covered PiP, Cast/session/screen, recovery/error UI, login
and configured factories. A prior full pass exposed five lint API-guard errors;
the explicit guard was restored. Final connectivity tests use public SDK APIs
after correcting a fixture compile error. Raw intermediate results are retained.

Ignored evidence is under `.private/artifacts/review-corrections/`, including
`final-acceptance/` (raw test XML, lint, Gradle log, APKs and build metadata) and
`native/` (frames, sanitized recovery traces, outage times and PiP state). The original
attributed Opus report and parent adjudication remain unchanged under
`.private/artifacts/opus-5.5-review-cf2127566/`.

## Native recovery status and remaining human QA

The original Rubius outage ended in repeated behind-live-window failures. It is
retained as failed evidence. A later Robb576 live run resumed after a 33.9-second
outage, but debug traces exposed redundant Prepare calls resetting the budget;
that loophole was corrected and must be excluded from final success claims.
Robb576 ended its broadcast while the corrected build was being prepared.

Earlier corrected builds restored video but left chat disconnected after a long
outage. The activity now requests network checks after validation changes, and
visible live chat cancels/closes its captured current jobs on confirmed loss so
validated return can reconnect. Tests preserve active connections, manual
disconnect and replay exclusion. The first final-build interruption began while
video was buffering and is retained as a startup-interruption check; chat then
reconnected with new messages. It is excluded from active-playback acceptance.

Final strict-budget checks used the exact final debug APK (SHA-256
`aadee7cb4eeb08ba750775468a871212eaa080392f589596d29ac70ef2cdac1f`), a fresh
active public broadcast on Android 16/API 36.1, x86_64, emulator-5568, signed out:

- **33.5 seconds offline:** one recovery attempt after validated return, then
  continuous healthy playback beyond 30 seconds and new chat messages.
- **95.6 seconds offline:** the initial wait exhausted while offline; validated
  return opened one additional bounded episode, whose first attempt sought live
  and restored video and new chat messages. Continuous healthy playback exceeded
  30 seconds before the next test began.
- Sanitized traces report `PLAYING`/`isPlaying` and advancing live windows;
  screenshots show actual changed video frames after recovery. No manual retry
  or source replacement was used. Both began from actual playing video, with
  continuous health established before the second episode. These sample two
  outages, not every network.
- Wide/sidebar video-only PiP produced a 505×284 window with live video and
  restored the app. Framework bounds and screenshots are retained.

This is emulator correctness evidence, not a physical-device performance
measurement. Recovery, PiP and chat behavior across the full device matrix remain
required human checks.

Required human QA remains in [MANUAL_QA.md](MANUAL_QA.md): live/VoD, switching,
minimize/restore, close/reopen, PiP/background, speed/quality, gestures and floating
chat; real receiver discovery/handoff/disconnect/network/volume; physical phone
outage/handover; physical TV/OEM remote, QR camera and authenticated grant. No real
account, receiver, TV, camera scan, physical benchmark or audible overlap test is
claimed. Do not interpret a source fix or passing unit suite as hardware approval.
