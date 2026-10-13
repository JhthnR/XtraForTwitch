# Repository cleanup and review defaults

## Goal

Remove obsolete Superpowers and progress documents from GitHub, correct public
project copy, use GPT-6.1-sol/high and OpenCode GLM 5.3/max for requested reviews,
and deliver a fresh signed 1.4.0 candidate after the cleanup is merged.

## Non-goals

No player/app behavior changes, broad asset deletion, private local-file cleanup,
tag creation or publication.

## Current context

The maintainer explicitly requested this cleanup and model correction on October
12. PR #49 merged the accepted app changes. Its source gate passed: 829 tests,
lint 0 errors/338 warnings, debug and unsigned release builds. The source tree
matched the accepted combined QA build. Signed run 38311795467 and GPT-5.6/GLM
5.2 review attempts are superseded; the run was cancelled before completion.

## Files likely involved

- `AGENTS.md`, `docs/RELEASE_PROCESS.md`, the active release plan
- `README.md`, Fastlane descriptions and GitHub repository description
- `docs/superpowers/`, `TEMP_PROGRESS.md`, `docs/PR_ORDER.md`, redundant `.gitkeep`
- Global review preferences and installed review guidance outside the repository

## Risks

Risk level: low for these documentation changes; release verification remains
high risk. Only inspected tracked obsolete files are deleted. Original artwork,
working tools, historical release evidence and user-local files are preserved.

## Human approval

The maintainer authorized the cleanup, review models and global review defaults.
Earlier authorization to merge, build/sign and verify the candidate persists.
Tagging/publication and approval of exact signed APK bytes remain pending.

## Implementation steps

1. Stop superseded reviews/build; update global and repository review defaults.
2. Remove obsolete tracked documents and correct public copy and links.
3. Run relevant text/tooling checks; obtain the requested independent reviews.
4. Merge through normal policy, freeze the new SHA, build and independently
   verify the signed candidate, and review its final evidence with both lanes.
5. Deliver the exact APK and device checklist; record pending device-only QA.

## Verification

- [ ] Global review guidance validates; OpenCode GLM lane uses 5.3/max.
- [ ] Public links/UTF-8, site/release tooling and updater contracts pass.
- [ ] No tracked Superpowers folder, obsolete progress files or private artifacts.
- [ ] GPT-6.1-sol/high and verified OpenCode GLM 5.3/max reviews completed.
- [ ] PR CI passes; signed candidate provenance/package/version/cert/hash verified.

Android app code/resources are unchanged. Existing passing local Android results
remain evidence for those bytes; normal PR CI and signed-workflow gates run again.
Human QA is listed in `docs/MANUAL_QA.md` and the delivered `DEVICE_QA.md`, including
live/VoD, switching, minimize/restore, close/reopen, PiP/background, speed/quality,
gestures, floating chat, folding, logged-in upgrade/offline and updater flows.

## Progress log

- 2026-10-12: Created plan after the maintainer expanded the release task.
  Verified global GLM configuration already pins `zai-coding-plan/glm-5.3`, max.
  Inspected stale review requirements, Superpowers files and public descriptions.
- 2026-10-12: Fresh debug stress covered Settings navigation and populated Search
  tab switching. Heap analysis found 0 application/library leaks; targeted
  destroyed-Settings analysis also found 0 leaks. Transition animation records,
  SettingsAxisTransition instances and closed Settings fragments all counted 0.
  Raw heaps remain outside the repository; this does not certify all device flows.
- 2026-10-13: Both original review attempts were interrupted by quota; a resumed
  app-owned OpenCode attempt failed to open its provider session. Use the existing
  crew GLM 5.3/max lane, preserving the failed attempt as unavailable. GPT review
  identified a low-severity notice wording discrepancy: ordinary canceled rewind
  discovery has no return notice. Corrected the feature guide to match the actual
  completed-result-drop behavior, without changing player code.

## Decisions

- Use the maintainer's explicit current reviewer pair instead of older model
  requirements. Never silently lower reasoning or substitute another reviewer.
- Keep cleanup in its own documentation PR. Any source-tree change requires a
  new frozen signed bundle; never promote the superseded candidate.

## Final PR summary draft

Remove obsolete agent/progress documents, align review defaults and correct the
README/store/repository copy. App behavior is unchanged. Validate public links,
tooling and requested reviews, then prepare a fresh signed 1.4.0 candidate.
