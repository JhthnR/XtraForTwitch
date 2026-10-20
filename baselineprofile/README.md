# Startup profiles and measurement

This module generates real ART profiles for startup and local Stats navigation.
It also supplies cold-start benchmarks comparing no compilation with a required
packaged Baseline Profile. It does not benchmark network latency or video decoding.

The Gradle plugin creates `nonMinifiedRelease` for capture and `benchmarkRelease`
for measurement. Both install as **`com.tzii.thysttv.benchmark`**, signed with the
local debug key. The production `com.tzii.thysttv` and ordinary debug app are
separate. Check APK metadata before installing tooling builds. Do not distribute
the capture APK; normal `assembleRelease` remains conditionally release-signed or
unsigned, using the existing release rules.

Use JDK 21 and an API 33+ emulator/device (or a rooted API 28+ device). Keep one
test device connected. Use a clean benchmark install with default navigation tabs;
the generator fails if the expected home/Stats controls are absent. It uses stable
resource IDs, not translated text, and never signs in or opens a stream.

Generate from the repository root:

```powershell
.\gradlew.bat :app:generateBaselineProfile '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile' --no-daemon
```

The plugin copies `baseline-prof.txt` and `startup-prof.txt` into
`app/src/main/generated/baselineProfiles/`. Commit both. The startup profile covers
launch only; the broader baseline also covers Stats filters and scrolling. Raw
captures/results stay under `baselineprofile/build/outputs/`. Regenerate after
changes to startup/navigation or dependencies; never repair generated rules by
hand. Normal builds consume the saved files with automatic generation disabled,
so CI and release builds do not need a device.

After generation, build `assembleRelease` and inspect the APK ZIP for nonempty
`assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm`. Source rules should
include `ThystTVApp`, with no stale `AmethytwApp` entries. Preserve ordinary unsigned
release behavior when no release credentials are supplied.

Measure on a cool physical device with stable power/thermal conditions, keeping
device/settings/build fixed:

```powershell
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tzii.thysttv.baselineprofile.StartupBenchmarks' --no-daemon
```

This runs ten cold starts per mode. The profile mode requires a successfully
installed Baseline Profile; it cannot silently substitute warmup compilation.
Keep the raw JSON and Perfetto traces under `baselineprofile/build/outputs/` with
device/API/commit/thermal context. Emulator profile generation is useful for
correctness but is not evidence of a physical-device speedup. Do not suppress
benchmark validation errors to obtain a timing result.

References: [Android profile generation](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile),
[configuration](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles),
[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
