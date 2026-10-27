# Roadmap

## Release candidate: 1.5.1 / code 16

- Live casting fixed for Chromecast with Google TV (#62); VoDs stay on the phone.
- "Stop casting and play here" when a VoD is opened while casting (#64).
- VoDs and downloads resume at their saved position again (#63).

Live casting, clips, live switching and disconnect were checked on a Chromecast
with Google TV. The stop-casting offer has not been checked on a receiver. See
`release-notes/1.5.1.md`; signed-candidate verification, independent reviews and
exact-APK device approval are required before publication. Version 1.5.0 remains
the published release below.

## Current release: 1.5.0 / code 15 (published)

- VoD seek previews, portrait live rewind and chat/performance improvements.
- Media3 1.11.1, bounded live recovery and background VoD/clip retries.
- Optional portrait PiP chat and TV sign-in from a phone.
- Experimental Google Cast, off by default, with a separate default-off option
  to keep receiver playback after the app closes.
- Enlarged-text navigation fixes and corrected VoD replay chat.

Version 1.5.0/code 15 is published. See `release-notes/1.5.0.md` for changes,
upgrade guidance and limitations. Physical Cast/TV and authenticated TV sign-in
remain unverified; Cast is experimental and off by default. Follow
`RELEASE_PROCESS.md` when preparing the next release.

## Previous release: 1.4.0 (published October 13, 2026)

- Selective Xtra feature ports: live notification reliability, Channel Points
  rewards, exact quality variants with a metered Wi-Fi preset, and
  recording-backed live rewind (#30-#33).
- Free pinch zoom between Fit and Fill (#37) and an opt-in spoiler-free mode for
  VoDs (#38, issue #34).
- Fixes for settings drag order (#35), the gesture guide in PiP (#36) and a
  closed-player memory leak (#39).
- A half-folded layout for foldables (#43) and spoiler-free VoDs hiding the
  length in the games and download dialogs too (#42).

Version 1.4.0/code 14 is published. See `release-notes/1.4.0.md` for its scope,
upgrade guidance and limitations, and `RELEASE_PROCESS.md` for the release flow.

## Previous target: 1.3.1 (published September 30, 2026)

- Larger brightness/volume gesture feedback on large video surfaces.
- Compatible upstream correctness and localization changes with a ledger.
- D-pad navigation and visible focus for the existing Android TV interface
  (issue #24).

## Previous 1.2 priorities (historical)

### P0
- show current speed in the player speed button
- make VoD seek scrubbing significantly more responsive
- fix minimized-player black bars / ugly surface background behavior
- refresh the adaptive icon background so the mark does not blend into the background
- add polished screenshots to the README and site
- create stronger repo visual branding / social preview

### P1
- verify large-screen layouts more rigorously
- improve split-screen / resized-window behavior
- strengthen release process and repo health files
- clean up leftover branches and docs

## Guiding priorities
1. stability first
2. player UX second
3. visual polish third
4. new features after confidence is restored
