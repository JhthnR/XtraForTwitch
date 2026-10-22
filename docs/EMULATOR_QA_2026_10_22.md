# Android emulator regression QA — October 22–23, 2026

## Scope and environment

This is the follow-up to the independent Opus review corrections, run on
`fix/13-opus-review` in the isolated `.private/worktrees/perf` worktree.
The base under test includes `6718ad20c` (VoD replay resume); P30/P31
were rechecked after the accessibility XML changes described below.
The main/master checkout was not touched, and the app remained signed out.

- Device: Android API 36.1 x86-64 AVD `emulator-5568`, ADB server port `5038`,
  1080×2400 portrait; also landscape and 1.0/2.0 Android font scales.
- Installation: `app/build/outputs/apk/debug/app-debug.apk`, via `adb install -r`,
  retaining emulator app data. No physical casting receiver, camera, TV remote
  or logged-in Twitch account was available.
- Evidence: ignored private folder
  `.private/artifacts/broad-emulator-qa-2026-10-22/` contains the indexed
  `results.jsonl`, screenshots, selected UI hierarchies and media-session
  snapshots. It is intentionally not included in Git.
- These are functional smoke tests, **not** FPS/performance benchmarks.
  A media-session PLAYING state alone is not proof of decoded frames; cases
  asserting video used screenshots after settling.

## Case matrix

Statuses below are the **latest** result for each case. Earlier failed and
pending observations remain in `results.jsonl` as an audit trail.

| Case | Result | Checked behavior | Evidence file(s) |
| --- | --- | --- | --- |
| P01 | Pass | Signed-out launch and public channel profile | `launch.png`, `live-profile.xml` |
| P02 | Pass | Live decoded video and changing chat | `live-playing.png`, `live-playing-state.txt` |
| P03 | Pass | Pause then explicit resume | `live-paused.png`, `live-resumed-state.txt` |
| P04 | Pass | 360p live quality selection | `live-360.png` |
| P05 | Pass | Player More menu readability/reachability | `live-more.png`, `live-more.xml` |
| P06 | Pass¹ | Audio-only state and quality menu; not audible audio | `audio-mode.png`, `audio-options.png` |
| P07 | Pass | Chat-only then return to video | `chat-only.png`, `chat-return-video.png` |
| P08 | Pass | Mini-player while browsing Stats, then restore | `minimized.png`, `restored-real.png` |
| P09 | Pass | Close/miniplayer dismissal and session cleanup | `closed-player.png`, `closed-player-state.txt` |
| P10 | Pass | Reopen on another live stream | `shroud-playing.png`, `shroud-restored.png` |
| P11 | Pass | Live stream switching, settled decoder and one session | `switch-settled.png`, `switch-sessions.txt` |
| P12 | Pass | Active public VoD with replay chat | `vod-controls.png` |
| P13 | Pass | Large VoD seek and replay-chat position | `vod-seek-back.png` |
| P14 | Pass | 1.5× speed label and quality menu | `vod-quality.png` |
| P15 | Pass | Quality switch retains 1.5× and position | `vod-quality-retained.png` |
| P16 | Pass | Seek while paused, then resume at new location | `vod-paused-seek.png`, `vod-paused-seek-state.txt` |
| P17 | Pass | Real video-only PiP with 16:9 geometry | `vod-pip.png` |
| P18 | Pass | PiP pause/play actions | `pip-paused-state.txt` |
| P19 | Pass | Android media notification pause/play | `notification-later.png`, `notification-paused.png` |
| P20 | Pass² | VoD replay chat resumes after PiP/return | `fixed-vod-pip-return.png`, `fixed-vod-controls.png` |
| P21 | Pass | Landscape decoded video, sidebar, gesture guide | `landscape-real.png`, `landscape-chat.png` |
| P22 | Pass¹ | Landscape brightness/volume gesture overlays | `gesture-brightness.png`, `gesture-volume.png` |
| P23 | Pass | Lock/wake: VoD position, speed, replay chat | `fixed-lock-return.png` |
| P24 | Pass | Rapid seeks settle to new video/replay position | `fixed-rapid-seek.png` |
| P25 | Pass | User-paused VoD remains paused through outage | `confirmed-paused-after-network-state.txt` |
| P26 | Pass¹ | Cast setting while service alive exposes chooser; can dismiss | `cast-chooser.png`, `cast-dismissed.png` |
| P27 | Pass | Portrait PiP with live video/chat, then return | `active-live-pip-chat.png`, `active-live-pip-return.png` |
| P28 | Pass | Stats time-range selection, portrait/landscape 200% text | `stats-30.png`, `stats-landscape-font200.png` |
| P29 | Pass | Signed-out Following and Games at 200% | `following-signed-out.png`, `games-font200.png` |
| P30 | Pass² | Saved: full Bookmarks/Downloads/Filters labels at 200% | `saved-font200-tab-auto.png`, `saved-downloads-font200-auto.png` |
| P31 | Pass² | Search: one-line tab labels, horizontally reachable at 200% | `search-font200-final.png`, `search-font200-scroll-left.png` |
| P32 | Pass | Landscape video with sidebar chat toggled off/on by double tap | `landscape-floating-recheck.png`, `floating-cycle-on.png`, `floating-cycle-restored.png` |
| P33 | Pass² | Channel profile: Watch live remains clear of the tab strip at 200%, with normal 100% layout retained | `p33-proposed-fix.png`, `p33-font100-recheck.png` |

¹ Scope-limited pass: emulator visual/interaction check only; not receiver
decoding, audio audibility or physical hardware. ² A previously failing case
was fixed and rerun; both prior/final outcomes remain in the raw log.

**Closeout: 33/33 cases have a latest `Pass` result**, with the scope
limitations noted above. This does not mean all physical-device behavior
passed, nor does it establish long-run performance or memory safety.

## Fixes and regression checks

- **P20** was fixed earlier in `6718ad20c`: after PiP/background return,
  VoD chat now starts loading again without requiring the user to seek.
- **P30:** `fragment_media_pager.xml` uses `tabMode="auto"`, keeping
  Saved's three labels on one line at 200% without unnecessarily changing
  television focus behavior.
- **P31:** Search has its own `fragment_search.xml` and needed
  `tabMode="scrollable"` and `tabGravity="start"`. In the initial
  experiment, changing Saved to forced scrollable broke
  `TvNavigationFocusTest` (D-pad-down focus of the Channels tab).
  Restoring Saved to adaptive mode fixed that regression. Search now shows
  Streams, Channels and Games without wrapping, while Videos is fully
  reachable by swiping the tab strip; this was verified on the rebuilt APK.
- Focused `TvNavigationFocusTest` and `assembleDebug`: **passed**.
  Final `test lintDebug assembleRelease`: **passed**. The intermediate
  failed suite is retained in `build-qa-tabs-final.log`; the successful
  runs are in `build-qa-tabs-focused.log` and
  `build-qa-tabs-verification.log`.
- **P33:** A further 200% font-scale check found the expanded channel
  profile's Watch live action overlapping the pinned tab row. The
  `ChannelPagerFragment` pager callback now reserves scale-dependent extra
  space above that row, leaving the 100% geometry unchanged. Native before
  and after images are retained; the fixed profile was checked again at
  200% and 100%, and a focused `ChannelPagerHeaderTest` guards the
  scale-based spacing calculation.
- **Final build gates:** `assembleDebug test lintDebug assembleRelease` completed
  successfully (`build-qa-closeout.log`). The Gradle XML results contain
  **1,012 tests across 128 suites, 0 failures, 0 errors and 0 skipped**;
  lint reports **0 errors, 331 warnings**. The release assembly is unsigned,
  so it is not a published or signed release candidate.

## Observations to investigate separately

1. A debug **LeakCanary screen listed six distinct leaks**, including
   Search-related fragment types, after reinstalling the debug app
   (`final-after-install.png`).

   **Resolved on 2026-10-23 as historical.** LeakCanary's `leaks.db` was
   last written on 2026-08-12, and its only two heap dumps date from
   August 12 as well. The six leaks
   were `LifecycleRegistry.observerMap` (the Search ViewPager2 adapter held by
   `SearchPagerFragment`'s retained lifecycle) and `pagingAdapter` leaks in
   `ChannelSearchFragment`, `GameSearchFragment`, `StreamSearchFragment`,
   `VideoSearchFragment` and `TopStreamsFragment` (adapter observers holding
   detached `RecyclerView`s). Both were fixed after that date by `672a989cd`
   (list adapters released on view teardown, 2026-09-27) and `6c4f6eaae`
   (Search pager released with its view, issue #14, 2026-10-12).

   Fresh check on `89b6bd661`, `Medium_Phone_API_36.1`: the old database was
   backed up to `.private/artifacts/leakcanary-2026-10-23/leaks-before.db` and
   cleared. Four Search sessions (each visiting all four tabs), bottom-nav
   switches across every top-level destination, and a landscape/portrait
   rotation inside Search were followed by backgrounding the app. LeakCanary
   watched 147 instances (12 of each Search pager/tab fragment) and reported
   **no retained objects**, no new `leaks.db` and no new heap dump. This does
   not cover player open/close heap growth, which remains a device QA item.

## Outstanding human/device QA

Physical Chromecast handoff/disconnect and receiver audio/volume, actual
TV D-pad and QR scanning/device-login, authenticated Twitch interactions,
hardware decoder and audio audibility, 20+ player open/close heap validation,
foldable postures and OEM PiP transitions are **not certified** by these
emulator checks. Follow `docs/MANUAL_QA.md`.

The emulator was restored to font scale 1.0, auto-rotation enabled, user
rotation 0, Wi-Fi enabled and airplane mode off. Only the dedicated
`emulator-5568` instance was shut down; no app data was wiped. The final
post-fix retest used the same AVD, accessed from its separate ADB server on
port 5038 via `127.0.0.1:5569` after the original process had exited.
