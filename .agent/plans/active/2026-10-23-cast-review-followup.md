# Cast RC adversarial-review follow-up (GLM N1/N2)

## Scope
Resolve code-level findings from independent GLM 5.3/max review of signed RC
`f8f43743f33f35aa60697f8f33401ab6d7056935`. All three previous
major/moderate F1-F3 findings were considered fixed. No feature expansion,
SDK update, version bump, tag, publication, secret or user-data changes.

## Findings and smallest fixes

- **N1 (medium)**: rejected Cast snapshot could survive a new source or later
  successful handoff, resurrecting older media on delayed return. Clear it in
  `installSource` and on accepted transfer, with separate regression cases.
- **N2 (low)**: malformed numeric host sent to `InetAddress.getByName` could
  fall back to DNS resolution on the main thread. Validate/parse IP literals
  using the already available Guava `InetAddresses` library, without DNS.
  Reject malformed numeric and noncanonical integer hosts.
- Review bundle metadata N3 is corrected outside tracked source in future
  review evidence. Physical no-GMS/Cast device checks N4 are manual QA gates;
  no unsupported claim of hardware behavior.

## Verification before PR

- [x] Focused Robolectric Cast/session tests (API 23 and 28)
- [x] Full :app:testDebugUnitTest (1,076 tests, 0 failures, errors or skips),
  lintDebug, assembleDebug, unsigned assembleRelease
- [x] Node site, workflow, signing/release contract tests (79 pass, 2 POSIX skips)
- [x] Review exact source diff, whitespace and staged file list
- [ ] Commit, push PR and require all GitHub checks to pass

## Post-merge release safety

Any code change supersedes signed RC run 39999368441 and its APK SHA
`aaa386d1b1c0eef699645dc1c34a7dd3d0e90cbeaf5c1706b1c66eb0e150c631`.
Freeze the **new** master SHA, re-dispatch signing, verify digest/certificate/
manifest binding, repeat official 1.4.0 signed upgrade emulator smoke and both
independent review lanes as available. Do not tag or publish without exact
signed candidate physical-device approval. Cast receiver, authenticated TV,
non-GMS playback, and signed-in/offline/updater smoke remain manual gates.
